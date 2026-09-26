package com.hopnote.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class GoogleAccount(context: Context) {
    private val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val store = EncryptedSharedPreferences.create(context, "hopnote_google", key, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun email() = store.getString("email", null)
    fun save(email: String?) = store.edit().putString("email", email).apply()
    fun clear() = store.edit().clear().apply()
}
