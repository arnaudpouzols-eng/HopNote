package com.hopnote.app

import android.content.Context

class GoogleAccount(context: Context) {
    private val store = context.getSharedPreferences("hopnote_google", Context.MODE_PRIVATE)
    fun email() = store.getString("email", null)
    fun save(email: String?) = store.edit().putString("email", email).apply()
    fun clear() = store.edit().clear().apply()
}
