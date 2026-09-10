package com.example.ollamataskerbridge.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.ollamataskerbridge.bridge.InferenceForegroundService
import net.dinglisch.android.tasker.TaskerPlugin

class LocaleFireReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != LocalePluginContract.ACTION_FIRE_SETTING) return
    Log.i("OllamaTaskerBridge", "FIRE_SETTING受付: ordered=" + isOrderedBroadcast + " pending=" + isOrderedBroadcast)
    if (isOrderedBroadcast) setResultCode(TaskerPlugin.Setting.RESULT_CODE_PENDING)
    Log.i("OllamaTaskerBridge", "FIRE_SETTING受付: ordered=" + isOrderedBroadcast + " pending=" + (isOrderedBroadcast))
    val serviceIntent = Intent(context, InferenceForegroundService::class.java)
      .putExtras(intent)
      .putExtra(InferenceForegroundService.EXTRA_ORIGIN, InferenceForegroundService.ORIGIN_LOCALE)
    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
  }
}
