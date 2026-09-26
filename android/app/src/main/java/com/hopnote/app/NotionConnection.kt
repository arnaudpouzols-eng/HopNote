package com.hopnote.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NotionConnection(val token: String, val parentPageId: String, val hopNotePageId: String?)

class NotionPreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val store = EncryptedSharedPreferences.create(
        context,
        "hopnote_notion",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun connection(): NotionConnection? {
        val token = store.getString("token", null) ?: return null
        val parentPageId = store.getString("parent_page_id", null) ?: return null
        return NotionConnection(token, parentPageId, store.getString("hopnote_page_id", null))
    }

    fun save(token: String, parentPageId: String, hopNotePageId: String) {
        store.edit().putString("token", token).putString("parent_page_id", parentPageId).putString("hopnote_page_id", hopNotePageId).apply()
    }

    fun clear() = store.edit().clear().apply()
}

object NotionClient {
    private const val API_VERSION = "2026-03-11"

    suspend fun createOrFindHopNotePage(token: String, parentPageId: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val existing = findExistingChild(token, parentPageId)
            existing ?: createChildPage(token, parentPageId)
        }
    }

    private fun findExistingChild(token: String, parentPageId: String): String? {
        val response = request("GET", "https://api.notion.com/v1/blocks/$parentPageId/children?page_size=100", token)
        val blocks = response.getJSONArray("results")
        for (index in 0 until blocks.length()) {
            val block = blocks.getJSONObject(index)
            if (block.optString("type") == "child_page" && block.optJSONObject("child_page")?.optString("title") == "HopNote") {
                return block.getString("id")
            }
        }
        return null
    }

    private fun createChildPage(token: String, parentPageId: String): String {
        val title = JSONObject().put("title", JSONArray().put(
            JSONObject().put("type", "text").put("text", JSONObject().put("content", "HopNote"))
        ))
        val body = JSONObject()
            .put("parent", JSONObject().put("type", "page_id").put("page_id", parentPageId))
            .put("properties", title)
        return request("POST", "https://api.notion.com/v1/pages", token, body).getString("id")
    }

    private fun request(method: String, endpoint: String, token: String, body: JSONObject? = null): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Notion-Version", API_VERSION)
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout = 15_000
            if (body != null) {
                doOutput = true
                outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
        }
        val status = connection.responseCode
        val content = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IllegalStateException("Notion a répondu $status : ${JSONObject(content).optString("message", "Erreur inconnue")}")
        return JSONObject(content)
    }
}

fun pageIdFromUrl(value: String): String? {
    val id = Regex("([0-9a-fA-F]{32})(?:[?#].*)?$").find(value)?.groupValues?.get(1)
    return id?.let { "${it.substring(0, 8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.substring(20)}" }
}
