package com.example.ollamataskerbridge.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.ollamataskerbridge.bridge.BridgeContract
import com.example.ollamataskerbridge.bridge.Backend
import com.example.ollamataskerbridge.bridge.DefaultInferenceRepository
import com.example.ollamataskerbridge.bridge.GenerateRequest
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
        val presetId = values?.getString(LocalePluginContract.KEY_PRESET_ID).orEmpty()
        val customSystem = values?.getString(LocalePluginContract.KEY_CUSTOM_SYSTEM).orEmpty()
        val settings = SettingsStore(appContext)
        val system = if (presetId.isNotBlank() && presetId != "custom") {
          settings.presets().firstOrNull { it.id == presetId }?.body
        } else {
          customSystem.ifBlank { values?.getString(LocalePluginContract.KEY_SYSTEM) }
        }
        val resultVariable = normalizeResultVariable(values?.getString(LocalePluginContract.KEY_RESULT_VARIABLE).orEmpty())
        val backend = if (LocalModelStore(appContext).fileFor(model).isFile) Backend.LOCAL else Backend.OLLAMA
        val result = DefaultInferenceRepository.generateText(appContext, GenerateRequest(backend, model, prompt, system))
        finish(pending, appContext, intent, true, result, null, resultVariable)
      } catch (error: Exception) {
        finish(pending, appContext, intent, false, null, error.message ?: "実行に失敗しました", "")
      } finally {
        pending.finish()
      }
    }
  }

  private suspend fun generateCloud(context: Context, model: String, prompt: String, system: String?): String {
    val settings = SettingsStore(context)
    return OllamaClient(settings.endpoint, settings.apiKey).generate(model, prompt, system)
  }

  private fun normalizeResultVariable(value: String): String = value.trim()
    .removePrefix("{lv=")
    .removePrefix("%")
    .removeSuffix("}")
    .trim()

  private fun finish(pending: PendingResult, context: Context, request: Intent, ok: Boolean, result: String?, error: String?, resultVariable: String) {
    val extras = Bundle().apply {
      putBoolean(BridgeContract.EXTRA_OK, ok)
      result?.let {
        putString(BridgeContract.EXTRA_RESULT, it)
        putString("response", it)
        if (resultVariable.isNotBlank()) putString(resultVariable, it)
      }
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
