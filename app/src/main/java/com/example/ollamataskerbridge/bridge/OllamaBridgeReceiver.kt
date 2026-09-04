package com.example.ollamataskerbridge.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaRegistryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OllamaBridgeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    val appContext = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val model = intent.getStringExtra(BridgeContract.EXTRA_MODEL).orEmpty()
        val response = when (intent.action) {
          BridgeContract.ACTION_GENERATE -> LocalInferenceBridge.generate(
            context = appContext, model = model,
            prompt = intent.getStringExtra(BridgeContract.EXTRA_PROMPT).orEmpty(),
            system = intent.getStringExtra(BridgeContract.EXTRA_SYSTEM),
          )
          BridgeContract.ACTION_PULL -> {
            appContext.startForegroundService(Intent(appContext, ModelDownloadService::class.java).apply {
              putExtra(BridgeContract.EXTRA_MODEL, model)
              putExtra(BridgeContract.EXTRA_REPLY_ACTION, intent.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION))
              putExtra(BridgeContract.EXTRA_REPLY_PACKAGE, intent.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE))
            })
            "モデル取得をバックグラウンドで開始しました: $model"
          }
          else -> throw IllegalArgumentException("未対応のActionです")
        }
        sendResult(pending, appContext, intent, ok = true, result = response)
      } catch (error: Exception) {
        sendResult(pending, appContext, intent, ok = false, error = error.message ?: "処理に失敗しました")
      } finally {
        pending.finish()
      }
    }
  }

  private fun sendResult(pending: PendingResult, context: Context, request: Intent, ok: Boolean, result: String? = null, error: String? = null) {
    val action = request.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION)
      ?.takeIf { it.isNotBlank() } ?: BridgeContract.ACTION_RESULT
    val reply = Intent(action)
      .putExtra(BridgeContract.EXTRA_OK, ok)
      .putExtra(BridgeContract.EXTRA_REQUEST_ID, request.getStringExtra(BridgeContract.EXTRA_REQUEST_ID))
    request.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE)?.takeIf { it.isNotBlank() }?.let(reply::setPackage)
    if (result != null) reply.putExtra(BridgeContract.EXTRA_RESULT, result)
    if (error != null) reply.putExtra(BridgeContract.EXTRA_ERROR, error)
    val resultExtras = android.os.Bundle().apply {
      putBoolean(BridgeContract.EXTRA_OK, ok)
      putString(BridgeContract.EXTRA_REQUEST_ID, request.getStringExtra(BridgeContract.EXTRA_REQUEST_ID))
      if (result != null) putString(BridgeContract.EXTRA_RESULT, result)
      if (error != null) putString(BridgeContract.EXTRA_ERROR, error)
    }
    pending.setResultCode(if (ok) 0 else 1)
    pending.setResultExtras(resultExtras)
    context.sendBroadcast(reply)
  }
}
