package com.hopnote.app

import android.content.Context

class LocalRetention(context: Context) {
    private val store = context.getSharedPreferences("hopnote_preferences", Context.MODE_PRIVATE)

    fun automaticCleanupEnabled() = store.getBoolean("automatic_cleanup", true)
    fun setAutomaticCleanupEnabled(enabled: Boolean) = store.edit().putBoolean("automatic_cleanup", enabled).apply()
}
