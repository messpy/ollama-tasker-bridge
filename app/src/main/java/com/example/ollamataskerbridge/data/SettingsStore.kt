package com.example.ollamataskerbridge.data

import android.content.Context

class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("connection_settings", Context.MODE_PRIVATE)
  var endpoint: String
    get() = prefs.getString("endpoint", "http://127.0.0.1:11434") ?: "http://127.0.0.1:11434"
    set(value) { prefs.edit().putString("endpoint", value.trim()).apply() }
}
