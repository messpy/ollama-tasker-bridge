package com.example.ollamataskerbridge.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.os.PersistableBundle
import android.os.Build
import android.util.Log
import com.example.ollamataskerbridge.bridge.InferenceJobService
import com.example.ollamataskerbridge.bridge.InferenceForegroundService
import com.example.ollamataskerbridge.diagnostics.DiagnosticsLog
import net.dinglisch.android.tasker.TaskerPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocaleFireReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != LocalePluginContract.ACTION_FIRE_SETTING) return
    val values = intent.getBundleExtra(LocalePluginContract.EXTRA_BUNDLE)
    val model = values?.getString(LocalePluginContract.KEY_MODEL).orEmpty()
    val backend = values?.getString(LocalePluginContract.KEY_BACKEND).orEmpty()
    val suppliedExecutionId = intent.getStringExtra("com.example.ollamataskerbridge.REQUEST_ID").orEmpty()
    val executionId = suppliedExecutionId.ifBlank { UUID.randomUUID().toString() }
    val fingerprint = model + "\u0000" + backend + "\u0000" + values?.getString(LocalePluginContract.KEY_PROMPT).orEmpty()
    if (!RequestTracker.accept(executionId, fingerprint)) {
      DiagnosticsLog.warn("重複FIRE_SETTINGを無視: executionId=" + executionId)
      setResultCode(TaskerPlugin.Setting.RESULT_CODE_FAILED)
      return
    }
    Log.i("OllamaTaskerBridge", "FIRE_SETTING受付: ordered=" + isOrderedBroadcast + " model=" + model + " backend=" + backend + " executionId=" + executionId)
    DiagnosticsLog.note("MacroDroid/Tasker受付: model=" + model + " backend=" + backend + " executionPath=receiver executionId=" + executionId)
    if (model.isBlank() || backend.isBlank()) {
      DiagnosticsLog.error("空のFIRE_SETTINGを拒否: model=" + model + " backend=" + backend + " executionId=" + executionId)
      setResultCode(TaskerPlugin.Setting.RESULT_CODE_FAILED)
      return
    }
    setResultCode(TaskerPlugin.Setting.RESULT_CODE_PENDING)
    try {
      val serviceIntent = Intent(context, InferenceForegroundService::class.java)
        .putExtra(InferenceForegroundService.EXTRA_EXECUTION_ID, executionId)
        .putExtras(intent)
        .putExtra(InferenceForegroundService.EXTRA_ORIGIN, InferenceForegroundService.ORIGIN_LOCALE)
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
      DiagnosticsLog.note("推論Service起動要求: executionPath=foreground-service model=" + model + " executionId=" + executionId)
    } catch (error: Exception) {
      val message = error.message ?: "推論Serviceの起動に失敗しました"
      Log.e("OllamaTaskerBridge", "推論Service起動失敗: " + message, error)
      DiagnosticsLog.warn("ForegroundService起動不可、JobSchedulerへ切替: " + message)
      scheduleFallbackJob(context, intent, values, executionId)
    }
  }

  private fun scheduleFallbackJob(context: Context, original: Intent, values: android.os.Bundle?, executionId: String) {
    val extras = PersistableBundle().apply {
      putString(InferenceJobService.KEY_MODEL, values?.getString(LocalePluginContract.KEY_MODEL).orEmpty())
      putString(InferenceJobService.KEY_BACKEND, values?.getString(LocalePluginContract.KEY_BACKEND).orEmpty())
      putString(InferenceJobService.KEY_PROMPT, values?.getString(LocalePluginContract.KEY_PROMPT).orEmpty())
      values?.getString(LocalePluginContract.KEY_SYSTEM)?.let { putString(InferenceJobService.KEY_SYSTEM, it) }
      values?.getString(LocalePluginContract.KEY_CUSTOM_SYSTEM)?.let { putString(InferenceJobService.KEY_CUSTOM_SYSTEM, it) }
      values?.getString(LocalePluginContract.KEY_PRESET_ID)?.let { putString(InferenceJobService.KEY_PRESET_ID, it) }
      values?.getString(LocalePluginContract.KEY_PLATFORM)?.let { putString(InferenceJobService.KEY_PLATFORM, it) }
      values?.getInt(LocalePluginContract.KEY_MAX_TOKENS)?.let { putString(InferenceJobService.KEY_MAX_TOKENS, it.toString()) }
      values?.getFloat(LocalePluginContract.KEY_TEMPERATURE)?.let { putString(InferenceJobService.KEY_TEMPERATURE, it.toString()) }
      putString(InferenceJobService.KEY_EXECUTION_ID, executionId)
      original.getStringExtra(COMPLETION_INTENT)?.let { putString(InferenceJobService.KEY_COMPLETION, it) }
    }
    val result = context.getSystemService(JobScheduler::class.java).schedule(
      JobInfo.Builder(InferenceJobService.jobIdFor(executionId), ComponentName(context, InferenceJobService::class.java))
        .setMinimumLatency(0).setOverrideDeadline(5_000)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(extras).build()
    )
    DiagnosticsLog.note("推論Job登録: executionPath=macrodroid-job-scheduler result=" + result)
  }

  companion object {
    private const val JOB_ID_BASE = 3002
    private const val COMPLETION_INTENT = "net.dinglisch.android.tasker.extras.COMPLETION_INTENT"
    private object RequestTracker {
      private val ids = ConcurrentHashMap<String, Long>()
      private val fingerprints = ConcurrentHashMap<String, Long>()
      fun accept(id: String, fingerprint: String): Boolean {
        val now = System.currentTimeMillis()
        ids.entries.removeIf { now - it.value > 600000 }
        fingerprints.entries.removeIf { now - it.value > 1000 }
        if (ids.putIfAbsent(id, now) != null) return false
        if (fingerprint.isNotBlank() && fingerprints.putIfAbsent(fingerprint, now) != null) { ids.remove(id); return false }
        return true
      }
    }
  }
}
