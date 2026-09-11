package com.example.ollamataskerbridge.bridge

import android.app.job.JobParameters
import android.app.job.JobService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.os.BatteryManager
import android.os.PowerManager
import android.content.IntentFilter
import android.app.ActivityManager
import com.example.ollamataskerbridge.data.SettingsStore
import com.example.ollamataskerbridge.diagnostics.DiagnosticsLog
import com.example.ollamataskerbridge.plugin.LocalePluginContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import net.dinglisch.android.tasker.TaskerPlugin

/** Runs plugin inference when Android rejects a background foreground-service start. */
class InferenceJobService : JobService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val runningJobs = ConcurrentHashMap<Int, Job>()
  private val rescheduleJobs = ConcurrentHashMap<Int, Boolean>()

  override fun onStartJob(params: JobParameters): Boolean {
    InferenceExecutionRegistry.initialize(this)
    val data = params.extras
    val executionId = data.getString(KEY_EXECUTION_ID).orEmpty()
    createNotificationChannel()
    val model = data.getString(KEY_MODEL).orEmpty()
    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, Notification.Builder(this, CHANNEL_ID)
      .setContentTitle("推論中").setContentText(model + " を実行しています")
      .setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(true).build())
    val original = Intent(LocalePluginContract.ACTION_FIRE_SETTING).putExtra("com.example.ollamataskerbridge.REQUEST_ID", data.getString(KEY_EXECUTION_ID)).putExtra(LocalePluginContract.EXTRA_BUNDLE, Bundle().apply {
      putString(LocalePluginContract.KEY_MODEL, data.getString(KEY_MODEL).orEmpty())
      putString(LocalePluginContract.KEY_PROMPT, data.getString(KEY_PROMPT).orEmpty())
      putString(LocalePluginContract.KEY_IMAGE_URI, data.getString(KEY_IMAGE_URI))
      putString(LocalePluginContract.KEY_SYSTEM, data.getString(KEY_SYSTEM))
      putString(LocalePluginContract.KEY_CUSTOM_SYSTEM, data.getString(KEY_CUSTOM_SYSTEM))
      putString(LocalePluginContract.KEY_PRESET_ID, data.getString(KEY_PRESET_ID))
      putString(LocalePluginContract.KEY_BACKEND, data.getString(KEY_BACKEND))
      putString(LocalePluginContract.KEY_PLATFORM, data.getString(KEY_PLATFORM))
      putString(LocalePluginContract.KEY_MAX_TOKENS, data.getString(KEY_MAX_TOKENS))
      putString(LocalePluginContract.KEY_TEMPERATURE, data.getString(KEY_TEMPERATURE))
    })
    data.getString(KEY_COMPLETION)?.let { original.putExtra(COMPLETION_INTENT, it) }
    if (executionId.isBlank()) {
      DiagnosticsLog.error("推論Job拒否: executionIdが空 jobId=" + params.jobId + " model=" + model)
      return false
    }
    var snapshot = InferenceExecutionRegistry.snapshot(executionId)
    if (snapshot?.state == InferenceExecutionRegistry.State.COMPLETED || snapshot?.state == InferenceExecutionRegistry.State.CANCELLED || snapshot?.state == InferenceExecutionRegistry.State.OBSOLETE || snapshot?.signalFinished == true) {
      DiagnosticsLog.warn("不要な推論Jobを無視: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " state=" + snapshot.state + " completed=" + (snapshot.state == InferenceExecutionRegistry.State.COMPLETED) + " obsolete=" + (snapshot.state == InferenceExecutionRegistry.State.OBSOLETE))
      getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
      return false
    }
    if (snapshot == null) {
      val createdAt = data.getLong(KEY_CREATED_AT, 0L)
      if (createdAt > 0L && System.currentTimeMillis() - createdAt <= MAX_EXECUTION_AGE_MS && InferenceExecutionRegistry.register(executionId)) {
        snapshot = InferenceExecutionRegistry.snapshot(executionId)
        DiagnosticsLog.note("再起動後の未登録推論Jobを復元: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model)
      } else {
        DiagnosticsLog.warn("古い・未登録の推論Jobを無視: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " createdAt=" + createdAt)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        return false
      }
    }
    InferenceExecutionRegistry.markRunning(executionId)
    DiagnosticsLog.note("推論Job開始: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " backend=" + data.getString(KEY_BACKEND).orEmpty())
    val task = scope.launch {
      try {
        InferenceQueue.withSlot {
          DiagnosticsLog.note("推論開始: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " backend=" + data.getString(KEY_BACKEND).orEmpty())
          val backend = when (data.getString(KEY_BACKEND).orEmpty().lowercase()) {
            "local" -> Backend.LOCAL
            "ollama" -> Backend.OLLAMA
            else -> error("実行先backendが未設定です。Tasker/MacroDroid設定を保存し直してください")
          }
          val settings = SettingsStore(applicationContext)
          val presetId = data.getString(KEY_PRESET_ID).orEmpty()
          val system = if (presetId.isNotBlank() && presetId != "custom") settings.presets().firstOrNull { it.id == presetId }?.body else data.getString(KEY_CUSTOM_SYSTEM).orEmpty().ifBlank { data.getString(KEY_SYSTEM) }
          val maxTokens = data.getString(KEY_MAX_TOKENS)?.toIntOrNull()?.coerceIn(1, 4096) ?: 1024
          val temperature = data.getString(KEY_TEMPERATURE)?.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f
          val result = DefaultInferenceRepository.generateText(applicationContext, GenerateRequest(
            backend, model, data.getString(KEY_PROMPT).orEmpty(), system, maxTokens, temperature,
            readImage(data.getString(KEY_IMAGE_URI))
          ))
          InferenceExecutionRegistry.markCompleted(executionId)
          val signaled = if (InferenceExecutionRegistry.markSignalFinished(executionId)) TaskerPlugin.Setting.signalFinish(applicationContext, original, TaskerPlugin.Setting.RESULT_CODE_OK,
            Bundle().apply { putString("%answer", result); putString("%ok", "true") }) else false
          DiagnosticsLog.note("推論成功: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " backend=" + data.getString(KEY_BACKEND).orEmpty() + " resultChars=" + result.length + " signalFinish=" + signaled)
        }
      } catch (error: CancellationException) {
        // onStopJob() marks non-retryable work obsolete. Do not overwrite that
        // decision from the cancellation callback racing with this coroutine.
        if (InferenceExecutionRegistry.canRetry(executionId)) {
          InferenceExecutionRegistry.markPending(executionId)
        }
        DiagnosticsLog.warn("推論キャンセル: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " reason=job-stopped")
      } catch (error: Exception) {
        InferenceExecutionRegistry.markCancelled(executionId)
        val message = error.message ?: "推論に失敗しました"
        Log.e(TAG, "推論Job失敗: " + message, error)
        DiagnosticsLog.error("推論Job失敗: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " backend=" + data.getString(KEY_BACKEND).orEmpty() + " message=" + message)
        val signaled = if (InferenceExecutionRegistry.markSignalFinished(executionId)) TaskerPlugin.Setting.signalFinish(applicationContext, original, TaskerPlugin.Setting.RESULT_CODE_FAILED,
          Bundle().apply { putString("%error", message); putString("%ok", "false") }) else false
        DiagnosticsLog.note("推論Job失敗通知: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " backend=" + data.getString(KEY_BACKEND).orEmpty() + " signalFinish=" + signaled + " errorChars=" + message.length)
      } finally {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        val shouldReschedule = rescheduleJobs.remove(params.jobId) == true && InferenceExecutionRegistry.canRetry(executionId)
        runningJobs.remove(params.jobId)
        DiagnosticsLog.note("推論Job完了: jobId=" + params.jobId + " executionId=" + executionId + " model=" + model + " reschedule=" + shouldReschedule)
        if (!shouldReschedule) jobFinished(params, false)
      }
    }
    runningJobs[params.jobId] = task
    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    val executionId = params.extras.getString(KEY_EXECUTION_ID).orEmpty()
    val stopReason = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) params.stopReason else -1
    val snapshot = InferenceExecutionRegistry.snapshot(executionId)
    val completed = snapshot?.state == InferenceExecutionRegistry.State.COMPLETED || snapshot?.signalFinished == true
    val obsolete = snapshot?.state == InferenceExecutionRegistry.State.OBSOLETE
    val wasRunning = runningJobs[params.jobId] != null
    val retry = wasRunning && !completed && !obsolete && InferenceExecutionRegistry.canRetry(executionId) && isRetryableStopReason(stopReason)
    DiagnosticsLog.warn("推論Job停止: jobId=" + params.jobId + " executionId=" + executionId + " model=" + params.extras.getString(KEY_MODEL).orEmpty() + " backend=" + params.extras.getString(KEY_BACKEND).orEmpty() + " stopReason=" + stopReason + " stopReasonName=" + stopReasonName(stopReason) + " running=" + wasRunning + " completed=" + completed + " obsolete=" + obsolete + " retry=" + retry)
    if (stopReason == JobParameters.STOP_REASON_DEVICE_STATE) logDeviceState(executionId, params.jobId)
    if (wasRunning) {
      if (retry) rescheduleJobs[params.jobId] = true
      runningJobs[params.jobId]?.cancel()
      DiagnosticsLog.warn("推論キャンセル: jobId=" + params.jobId + " executionId=" + executionId + " reason=job-stopped retry=" + retry)
    }
    if (!retry && !completed && !obsolete) {
      InferenceExecutionRegistry.markObsolete(executionId)
    }
    return retry
  }
  override fun onDestroy() { scope.cancel(); super.onDestroy() }

  companion object {
    private const val TAG = "OllamaTaskerBridge"
    // v2 avoids an already-created IMPORTANCE_LOW channel being permanently silent.
    private const val CHANNEL_ID = "inference_background_v2"
    private const val NOTIFICATION_ID = 3002
    private const val MAX_EXECUTION_AGE_MS = 24L * 60L * 60L * 1000L
    const val KEY_MODEL = "inference.job.model"
    const val KEY_EXECUTION_ID = "inference.job.execution_id"
    const val KEY_BACKEND = "inference.job.backend"
    const val KEY_PROMPT = "inference.job.prompt"
    const val KEY_IMAGE_URI = "inference.job.image_uri"
    const val KEY_SYSTEM = "inference.job.system"
    const val KEY_CUSTOM_SYSTEM = "inference.job.custom_system"
    const val KEY_PRESET_ID = "inference.job.preset_id"
    const val KEY_PLATFORM = "inference.job.platform"
    const val KEY_MAX_TOKENS = "inference.job.max_tokens"
    const val KEY_TEMPERATURE = "inference.job.temperature"
    const val KEY_COMPLETION = "inference.job.completion"
    const val KEY_CREATED_AT = "inference.job.created_at"
    private const val COMPLETION_INTENT = "net.dinglisch.android.tasker.extras.COMPLETION_INTENT"

    private fun stopReasonName(reason: Int): String = when (reason) {
      JobParameters.STOP_REASON_UNDEFINED -> "UNDEFINED"
      JobParameters.STOP_REASON_CANCELLED_BY_APP -> "CANCELLED_BY_APP"
      JobParameters.STOP_REASON_PREEMPT -> "PREEMPT"
      JobParameters.STOP_REASON_TIMEOUT -> "TIMEOUT"
      JobParameters.STOP_REASON_DEVICE_STATE -> "DEVICE_STATE"
      JobParameters.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "CONSTRAINT_BATTERY_NOT_LOW"
      JobParameters.STOP_REASON_CONSTRAINT_CHARGING -> "CONSTRAINT_CHARGING"
      JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "CONSTRAINT_CONNECTIVITY"
      JobParameters.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "CONSTRAINT_DEVICE_IDLE"
      JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "CONSTRAINT_STORAGE_NOT_LOW"
      JobParameters.STOP_REASON_QUOTA -> "QUOTA"
      JobParameters.STOP_REASON_BACKGROUND_RESTRICTION -> "BACKGROUND_RESTRICTION"
      JobParameters.STOP_REASON_APP_STANDBY -> "APP_STANDBY"
      JobParameters.STOP_REASON_USER -> "USER"
      JobParameters.STOP_REASON_SYSTEM_PROCESSING -> "SYSTEM_PROCESSING"
      JobParameters.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "ESTIMATED_APP_LAUNCH_TIME_CHANGED"
      else -> "UNKNOWN(" + reason + ")"
    }

    private fun isRetryableStopReason(reason: Int): Boolean = reason == JobParameters.STOP_REASON_PREEMPT || reason == JobParameters.STOP_REASON_TIMEOUT || reason == JobParameters.STOP_REASON_DEVICE_STATE || reason == JobParameters.STOP_REASON_SYSTEM_PROCESSING
  }

  private fun createNotificationChannel() {
    getSystemService(NotificationManager::class.java).createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "LLMバックグラウンド推論", NotificationManager.IMPORTANCE_DEFAULT)
    )
  }

  private fun logDeviceState(executionId: String, jobId: Int) {
    val power = getSystemService(PowerManager::class.java)
    val activity = getSystemService(ActivityManager::class.java)
    val battery = getSystemService(BatteryManager::class.java)
    val batteryIntent = registerReceiver(null, IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
    val charging = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING || batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_FULL
    val capacity = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    val restricted = android.os.Build.VERSION.SDK_INT >= 28 && activity.isBackgroundRestricted
    DiagnosticsLog.warn("DEVICE_STATE詳細: jobId=" + jobId + " executionId=" + executionId + " idle=" + (power?.isDeviceIdleMode ?: false) + " powerSave=" + (power?.isPowerSaveMode ?: false) + " batteryLevel=" + level + "/" + scale + " capacity=" + capacity + " charging=" + charging + " backgroundRestricted=" + restricted)
  }

  private fun readImage(value: String?): ByteArray? {
    val path = value?.trim().orEmpty()
    if (path.isBlank()) return null
    val bytes = try {
      if (path.startsWith("content://")) contentResolver.openInputStream(android.net.Uri.parse(path))?.use { it.readBytes() }
      else java.io.File(path.removePrefix("file://")).takeIf { it.isFile }?.readBytes()
    } catch (error: SecurityException) {
      throw SecurityException("画像へのアクセスが拒否されました。アプリを一度開いて写真へのアクセスを許可するか、MacroDroidではcontent://形式のURIを指定してください", error)
    }
    val image = bytes ?: error("画像ファイルを読み込めません: $path")
    require(image.isNotEmpty()) { "画像ファイルを読み込めません: $path" }
    require(image.size <= 20 * 1024 * 1024) { "画像サイズが大きすぎます（20MB以下にしてください）" }
    return image
  }
}
