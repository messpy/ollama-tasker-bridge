package com.example.ollamataskerbridge.bridge

import android.content.BroadcastReceiver
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.ollamataskerbridge.bridge.Backend
import com.example.ollamataskerbridge.bridge.DefaultInferenceRepository
import com.example.ollamataskerbridge.bridge.GenerateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import com.example.ollamataskerbridge.diagnostics.DiagnosticsLog

class OllamaBridgeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == BridgeContract.ACTION_GENERATE) {
      val executionId = UUID.randomUUID().toString()
      InferenceExecutionRegistry.initialize(context)
      InferenceExecutionRegistry.register(executionId)
      val serviceIntent = Intent(context, InferenceForegroundService::class.java).putExtras(intent).putExtra(InferenceForegroundService.EXTRA_ORIGIN, "bridge").putExtra(InferenceForegroundService.EXTRA_EXECUTION_ID, executionId)
      try { if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent) else context.startService(serviceIntent) } catch (error: Exception) {
        DiagnosticsLog.warn("Bridge FGS start failed; falling back to JobScheduler executionId=" + executionId)
        scheduleInferenceJob(context, intent, executionId)
      }
      return
    }
    val pending = goAsync()
    val appContext = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val model = intent.getStringExtra(BridgeContract.EXTRA_MODEL).orEmpty()
        val response = when (intent.action) {
          BridgeContract.ACTION_GENERATE -> {
            val prompt = intent.getStringExtra(BridgeContract.EXTRA_PROMPT).orEmpty()
            val system = intent.getStringExtra(BridgeContract.EXTRA_SYSTEM)
            val backend = intent.getStringExtra(BridgeContract.EXTRA_BACKEND)?.lowercase()
            val maxTokens = intent.getIntExtra(BridgeContract.EXTRA_MAX_TOKENS, 1024)
            val temperature = intent.getFloatExtra(BridgeContract.EXTRA_TEMPERATURE, 0.7f)

            val resolvedBackend = backend ?: throw IllegalArgumentException("backendを明示指定してください（local または ollama）")
            require(resolvedBackend == "local" || resolvedBackend == "ollama") { "backendはlocalまたはollamaを指定してください" }
            DefaultInferenceRepository.generateText(appContext, GenerateRequest(if (resolvedBackend == "local") Backend.LOCAL else Backend.OLLAMA, model, prompt, system, maxTokens, temperature))
          }
          BridgeContract.ACTION_PULL -> {
            val extras = android.os.PersistableBundle().apply {
              putString(BridgeContract.EXTRA_MODEL, model)
              putString(BridgeContract.EXTRA_REPLY_ACTION, intent.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION))
              putString(BridgeContract.EXTRA_REPLY_PACKAGE, intent.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE))
              putString(BridgeContract.EXTRA_REQUEST_ID, intent.getStringExtra(BridgeContract.EXTRA_REQUEST_ID))
            }
            val pullJobId = 1002 + abs((intent.getStringExtra(BridgeContract.EXTRA_REQUEST_ID) ?: UUID.randomUUID().toString()).hashCode() % 10000)
            val job = JobInfo.Builder(pullJobId, ComponentName(appContext, ModelDownloadJobService::class.java))
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

  private fun scheduleInferenceJob(context: Context, request: Intent, executionId: String) { val extras = android.os.PersistableBundle().apply { putString(InferenceJobService.KEY_ORIGIN, InferenceJobService.ORIGIN_BRIDGE); putString(InferenceJobService.KEY_EXECUTION_ID, executionId); putLong(InferenceJobService.KEY_CREATED_AT, System.currentTimeMillis()); putString(InferenceJobService.KEY_REQUEST_ID, request.getStringExtra(BridgeContract.EXTRA_REQUEST_ID)); putString(InferenceJobService.KEY_MODEL, request.getStringExtra(BridgeContract.EXTRA_MODEL).orEmpty()); putString(InferenceJobService.KEY_BACKEND, request.getStringExtra(BridgeContract.EXTRA_BACKEND).orEmpty()); putString(InferenceJobService.KEY_PROMPT, request.getStringExtra(BridgeContract.EXTRA_PROMPT).orEmpty()); putString(InferenceJobService.KEY_SYSTEM, request.getStringExtra(BridgeContract.EXTRA_SYSTEM)); putString(InferenceJobService.KEY_IMAGE_URI, request.getStringExtra(BridgeContract.EXTRA_IMAGE_URI)); putString(InferenceJobService.KEY_MAX_TOKENS, request.getIntExtra(BridgeContract.EXTRA_MAX_TOKENS, 1024).toString()); putString(InferenceJobService.KEY_TEMPERATURE, request.getFloatExtra(BridgeContract.EXTRA_TEMPERATURE, 0.7f).toString()); putString(InferenceJobService.KEY_REPLY_ACTION, request.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION)); putString(InferenceJobService.KEY_REPLY_PACKAGE, request.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE)) }; val jobId = 20000 + abs(executionId.hashCode() % 10000); val result = context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(jobId, ComponentName(context, InferenceJobService::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(extras).build()); check(result == JobScheduler.RESULT_SUCCESS) { "AndroidがBridge推論ジョブを登録できません" } }
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
