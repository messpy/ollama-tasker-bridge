package com.example.ollamataskerbridge.bridge

import android.content.BroadcastReceiver
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.OllamaRegistryClient
import com.example.ollamataskerbridge.data.SettingsStore
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
          BridgeContract.ACTION_GENERATE -> {
            val prompt = intent.getStringExtra(BridgeContract.EXTRA_PROMPT).orEmpty()
            val system = intent.getStringExtra(BridgeContract.EXTRA_SYSTEM)
            val localFile = LocalModelStore(appContext).fileFor(model)
            if (localFile.isFile) {
              LocalInferenceBridge.generate(appContext, model, prompt, system)
            } else {
              val settings = SettingsStore(appContext)
              OllamaClient(settings.endpoint, settings.apiKey).generate(model, prompt, system)
            }
          }
          BridgeContract.ACTION_PULL -> {
            val extras = android.os.PersistableBundle().apply {
              putString(BridgeContract.EXTRA_MODEL, model)
              putString(BridgeContract.EXTRA_REPLY_ACTION, intent.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION))
              putString(BridgeContract.EXTRA_REPLY_PACKAGE, intent.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE))
            }
            val job = JobInfo.Builder(1002, ComponentName(appContext, ModelDownloadJobService::class.java))
              .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
              .setExtras(extras)
              .build()
            val result = appContext.getSystemService(JobScheduler::class.java).schedule(job)
            check(result == JobScheduler.RESULT_SUCCESS) { "Androidがモデル取得ジョブを登録できません" }
            "モデル取得をAndroidのバックグラウンドジョブとして開始しました: $model"
          }
          else -> throw IllegalArgumentException("未対応のActionです")
        }
        sendResult(pending, appContext, intent, ok = true, result = response)
      } catch (error: Exception) {
        Log.e("OllamaBridge", "bridge action failed", error)
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
