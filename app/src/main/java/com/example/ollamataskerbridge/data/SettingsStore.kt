package com.example.ollamataskerbridge.data

import android.content.Context

class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("connection_settings", Context.MODE_PRIVATE)
  var endpoint: String
    get() = prefs.getString("endpoint", "http://100.122.68.52:42049") ?: "http://100.122.68.52:42049"
    set(value) { prefs.edit().putString("endpoint", value.trim()).apply() }
}
