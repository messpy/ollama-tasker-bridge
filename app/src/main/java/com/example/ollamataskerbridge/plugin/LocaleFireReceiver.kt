package com.example.ollamataskerbridge.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.ollamataskerbridge.bridge.BridgeContract
import com.example.ollamataskerbridge.bridge.LocalInferenceBridge
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocaleFireReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != LocalePluginContract.ACTION_FIRE_SETTING) return
    val pending = goAsync()
    val appContext = context.applicationContext
    val values = intent.extras?.getBundle(LocalePluginContract.EXTRA_BUNDLE)
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val model = values?.getString(LocalePluginContract.KEY_MODEL).orEmpty()
        val prompt = values?.getString(LocalePluginContract.KEY_PROMPT).orEmpty()
        val system = values?.getString(LocalePluginContract.KEY_SYSTEM)
        val mode = values?.getString(LocalePluginContract.KEY_MODE) ?: "auto"
        val result = when (mode) {
          "local" -> LocalInferenceBridge.generate(appContext, model, prompt, system)
          "cloud" -> generateCloud(appContext, model, prompt, system)
          else -> if (LocalModelStore(appContext).fileFor(model).isFile) {
            LocalInferenceBridge.generate(appContext, model, prompt, system)
          } else {
            generateCloud(appContext, model, prompt, system)
          }
        }
        finish(pending, appContext, intent, true, result, null)
      } catch (error: Exception) {
        finish(pending, appContext, intent, false, null, error.message ?: "実行に失敗しました")
      } finally {
        pending.finish()
      }
    }
  }

  private suspend fun generateCloud(context: Context, model: String, prompt: String, system: String?): String {
    val settings = SettingsStore(context)
    return OllamaClient(settings.endpoint, settings.apiKey).generate(model, prompt, system)
  }

  private fun finish(pending: PendingResult, context: Context, request: Intent, ok: Boolean, result: String?, error: String?) {
    val extras = Bundle().apply {
      putBoolean(BridgeContract.EXTRA_OK, ok)
      result?.let { putString(BridgeContract.EXTRA_RESULT, it) }
      error?.let { putString(BridgeContract.EXTRA_ERROR, it) }
    }
    pending.setResultCode(if (ok) 0 else 1)
    pending.setResultExtras(extras)
    val replyAction = request.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION)
      ?.takeIf(String::isNotBlank) ?: BridgeContract.ACTION_RESULT
    val reply = Intent(replyAction).putExtras(extras)
    request.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE)?.takeIf(String::isNotBlank)?.let(reply::setPackage)
    context.sendBroadcast(reply)
  }
}
