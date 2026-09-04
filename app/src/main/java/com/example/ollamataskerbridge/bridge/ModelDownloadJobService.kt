package com.example.ollamataskerbridge.bridge

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaRegistryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ModelDownloadJobService : JobService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onStartJob(params: JobParameters): Boolean {
    val model = params.extras.getString(BridgeContract.EXTRA_MODEL).orEmpty()
    val action = params.extras.getString(BridgeContract.EXTRA_REPLY_ACTION) ?: BridgeContract.ACTION_RESULT
    val packageName = params.extras.getString(BridgeContract.EXTRA_REPLY_PACKAGE)
    scope.launch {
      try {
        val file = OllamaRegistryClient(LocalModelStore(applicationContext)).download(model)
        send(action, packageName, true, "モデルをAndroidへ保存しました: ${file.name}", null)
      } catch (error: Exception) {
        send(action, packageName, false, null, error.message ?: "モデル取得に失敗しました")
      } finally {
        jobFinished(params, false)
      }
    }
    return true
  }

  private fun send(action: String, packageName: String?, ok: Boolean, result: String?, error: String?) {
    sendBroadcast(Intent(action).apply {
      putExtra(BridgeContract.EXTRA_OK, ok)
      result?.let { putExtra(BridgeContract.EXTRA_RESULT, it) }
      error?.let { putExtra(BridgeContract.EXTRA_ERROR, it) }
      packageName?.takeIf(String::isNotBlank)?.let(::setPackage)
    })
  }

  override fun onStopJob(params: JobParameters): Boolean = true
  override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
