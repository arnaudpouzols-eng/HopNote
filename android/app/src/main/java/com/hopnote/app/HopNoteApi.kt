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
    private const val NETWORK_TIMEOUT_MS = 15_000

    suspend fun authorizationUrl(session: HopNoteSession): Result<String> = runCatching {
        val token = session.token() ?: createSession().also(session::save)
        request("GET", "/v1/notion/oauth/start", token).getString("authorizationUrl")
    }
    suspend fun connected(session: HopNoteSession) = session.token()?.let { runCatching { request("GET", "/v1/notion/status", it).optBoolean("connected") }.getOrDefault(false) } ?: false
    suspend fun append(session: HopNoteSession, capture: Capture): Result<String> = runCatching {
        val token = session.token() ?: error("Notion non connecté")
        request("POST", "/v1/captures", token, JSONObject().put("text", capture.text).put("source", capture.source.name).put("createdAt", capture.createdAt)).optString("notionBlockId")
    }
    suspend fun disconnect(session: HopNoteSession): Result<Unit> = runCatching {
        val token = session.token() ?: return@runCatching
        withContext(Dispatchers.IO) {
            val connection = (URL("$BASE/v1/notion").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
            }
            try {
                if (connection.responseCode !in 200..299) error("Déconnexion impossible")
            } finally {
                connection.disconnect()
            }
        }
    }
    private suspend fun createSession() = withContext(Dispatchers.IO) {
        val c = (URL("$BASE/v1/devices").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
        }
        try {
            JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getString("sessionToken")
        } finally {
            c.disconnect()
        }
    }
    private suspend fun request(method: String, path: String, token: String, payload: JSONObject? = null) = withContext(Dispatchers.IO) {
        val c = (URL("$BASE$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            if (payload != null) {
                doOutput = true
                outputStream.bufferedWriter().use { it.write(payload.toString()) }
            }
        }
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Connexion HopNote impossible")
            JSONObject(body)
        } finally {
            c.disconnect()
        }
    }
}
