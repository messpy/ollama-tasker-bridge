package com.example.ollamataskerbridge.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.ollamataskerbridge.bridge.InferenceForegroundService

class LocaleFireReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != LocalePluginContract.ACTION_FIRE_SETTING) return
    val serviceIntent = Intent(context, InferenceForegroundService::class.java)
      .putExtras(intent)
      .putExtra(InferenceForegroundService.EXTRA_ORIGIN, InferenceForegroundService.ORIGIN_LOCALE)
    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
  }
}
