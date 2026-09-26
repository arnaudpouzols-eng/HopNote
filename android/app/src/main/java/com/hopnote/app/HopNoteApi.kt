package com.hopnote.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HopNoteSession(context: Context) {
    private val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val store = EncryptedSharedPreferences.create(context, "hopnote_server", key, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun token() = store.getString("token", null)
    fun save(token: String) = store.edit().putString("token", token).apply()
    fun clear() = store.edit().clear().apply()
}

object HopNoteApi {
    private const val BASE = "https://hopnote-api.arnaud-pouzols.workers.dev"

    suspend fun authorizationUrl(session: HopNoteSession): Result<String> = runCatching {
        val token = session.token() ?: createSession().also(session::save)
        request("GET", "/v1/notion/oauth/start", token).getString("authorizationUrl")
    }
    suspend fun connected(session: HopNoteSession) = session.token()?.let { runCatching { request("GET", "/v1/notion/status", it).optBoolean("connected") }.getOrDefault(false) } ?: false
    private suspend fun createSession() = withContext(Dispatchers.IO) {
        val c = (URL("$BASE/v1/devices").openConnection() as HttpURLConnection).apply { requestMethod = "POST" }
        JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getString("sessionToken")
    }
    private suspend fun request(method: String, path: String, token: String) = withContext(Dispatchers.IO) {
        val c = (URL("$BASE$path").openConnection() as HttpURLConnection).apply { requestMethod = method; setRequestProperty("Authorization", "Bearer $token") }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("Connexion HopNote impossible")
        JSONObject(body)
    }
}
