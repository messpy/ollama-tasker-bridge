package com.example.ollamataskerbridge.data

import android.content.Context

class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("connection_settings", Context.MODE_PRIVATE)
  var endpoint: String
    get() = prefs.getString("endpoint", null)
      ?.takeUnless { it.contains("100.122.68.52") || it.contains("127.0.0.1:11434") }
      ?: "https://ollama.com"
    set(value) { prefs.edit().putString("endpoint", value.trim()).apply() }
  var apiKey: String
    get() = prefs.getString("api_key", "").orEmpty()
    set(value) { prefs.edit().putString("api_key", value.trim()).apply() }
}
