package com.example.ollamataskerbridge.bridge

import android.app.job.JobParameters
import android.app.job.JobService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
    val data = params.extras
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
    DiagnosticsLog.note("推論Job開始: model=" + model + " backend=" + data.getString(KEY_BACKEND).orEmpty())
    val task = scope.launch {
      try {
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
          backend, data.getString(KEY_MODEL).orEmpty(), data.getString(KEY_PROMPT).orEmpty(), system, maxTokens, temperature,
          readImage(data.getString(KEY_IMAGE_URI))
        ))
        val signaled = TaskerPlugin.Setting.signalFinish(applicationContext, original, TaskerPlugin.Setting.RESULT_CODE_OK,
          Bundle().apply { putString("%answer", result); putString("%ok", "true") })
        DiagnosticsLog.note("推論Job完了: resultChars=" + result.length + " signalFinish=" + signaled)
      } catch (error: CancellationException) {
        DiagnosticsLog.warn("推論Jobキャンセル: jobId=" + params.jobId)
      } catch (error: Exception) {
        val message = error.message ?: "推論に失敗しました"
        Log.e(TAG, "推論Job失敗: " + message, error)
        DiagnosticsLog.error("推論Job失敗: " + message)
        val signaled = TaskerPlugin.Setting.signalFinish(applicationContext, original, TaskerPlugin.Setting.RESULT_CODE_FAILED,
          Bundle().apply { putString("%error", message); putString("%ok", "false") })
        DiagnosticsLog.note("推論Job失敗通知: signalFinish=" + signaled + " errorChars=" + message.length)
      } finally {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        val shouldReschedule = rescheduleJobs.remove(params.jobId) == true
        runningJobs.remove(params.jobId)
        jobFinished(params, shouldReschedule)
      }
    }
    runningJobs[params.jobId] = task
    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    if (runningJobs.remove(params.jobId) != null) {
      rescheduleJobs[params.jobId] = true
      DiagnosticsLog.warn("推論Job停止・再スケジュール: jobId=" + params.jobId)
    }
    return true
  }
  override fun onDestroy() { scope.cancel(); super.onDestroy() }

  companion object {
    private const val TAG = "OllamaTaskerBridge"
    // v2 avoids an already-created IMPORTANCE_LOW channel being permanently silent.
    private const val CHANNEL_ID = "inference_background_v2"
    private const val NOTIFICATION_ID = 3002
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
    private const val COMPLETION_INTENT = "net.dinglisch.android.tasker.extras.COMPLETION_INTENT"
  }

  private fun createNotificationChannel() {
    getSystemService(NotificationManager::class.java).createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "LLMバックグラウンド推論", NotificationManager.IMPORTANCE_DEFAULT)
    )
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
