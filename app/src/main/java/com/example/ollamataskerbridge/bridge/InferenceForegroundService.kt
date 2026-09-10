package com.example.ollamataskerbridge.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.example.ollamataskerbridge.data.SettingsStore;
import com.example.ollamataskerbridge.plugin.LocalePluginContract;
import net.dinglisch.android.tasker.TaskerPlugin
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.SupervisorJob;
import kotlinx.coroutines.cancel;
import kotlinx.coroutines.launch;

class InferenceForegroundService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO);

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    createChannel();
    startForeground(NOTIFICATION_ID, notification(intent?.getStringExtra(BridgeContract.EXTRA_MODEL).orEmpty()));
    Log.i(TAG, "推論Service開始: origin=" + intent?.getStringExtra(EXTRA_ORIGIN) + " model=" + intent?.getStringExtra(BridgeContract.EXTRA_MODEL))
    scope.launch {
      try {
        if (intent?.getStringExtra(EXTRA_ORIGIN) == ORIGIN_LOCALE) runLocale(intent) else runBridge(intent ?: Intent());
      } catch (error: Exception) {
        val message = error.message ?: "推論に失敗しました";
        val action = intent?.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION)?.takeIf(String::isNotBlank) ?: BridgeContract.ACTION_RESULT;
        sendReply(action, intent?.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE), false, null, message);
        if (intent?.getStringExtra(EXTRA_ORIGIN) == ORIGIN_LOCALE) {
          val variables = Bundle().apply { putString("%error", message); putString("%ok", "false") };
          val signaled = TaskerPlugin.Setting.signalFinish(applicationContext, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, variables);
          Log.i(TAG, "signalFinish失敗通知: signaled=" + signaled + " errorChars=" + message.length)
        }
      } finally {
        stopSelf(startId);
      }
    }
    return START_NOT_STICKY;
  }

  private suspend fun runBridge(intent: Intent) {
    val backend = intent.getStringExtra(BridgeContract.EXTRA_BACKEND)?.lowercase()
      ?: throw IllegalArgumentException("backendを明示指定してください（local または ollama）");
    require(backend == "local" || backend == "ollama") { "backendはlocalまたはollamaを指定してください" };
    val request = GenerateRequest(
      if (backend == "local") Backend.LOCAL else Backend.OLLAMA,
      intent.getStringExtra(BridgeContract.EXTRA_MODEL).orEmpty(),
      intent.getStringExtra(BridgeContract.EXTRA_PROMPT).orEmpty(),
      intent.getStringExtra(BridgeContract.EXTRA_SYSTEM),
      intent.getIntExtra(BridgeContract.EXTRA_MAX_TOKENS, 256),
      intent.getFloatExtra(BridgeContract.EXTRA_TEMPERATURE, 0.7f)
    );
    val result = DefaultInferenceRepository.generateText(applicationContext, request);
    Log.i(TAG, "LLM生成成功: backend=" + backend + " resultChars=" + result.length)
    sendReply(intent.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION)?.takeIf(String::isNotBlank) ?: BridgeContract.ACTION_RESULT,
      intent.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE), true, result, null);
  }

  private suspend fun runLocale(intent: Intent) {
    val values = intent.getBundleExtra(LocalePluginContract.EXTRA_BUNDLE);
    val model = values?.getString(LocalePluginContract.KEY_MODEL).orEmpty();
    val presetId = values?.getString(LocalePluginContract.KEY_PRESET_ID).orEmpty();
    val settings = SettingsStore(applicationContext);
    val system = if (presetId.isNotBlank() && presetId != "custom") settings.presets().firstOrNull { it.id == presetId }?.body
      else values?.getString(LocalePluginContract.KEY_CUSTOM_SYSTEM).orEmpty().ifBlank { values?.getString(LocalePluginContract.KEY_SYSTEM) };
    val configuredBackend = values?.getString(LocalePluginContract.KEY_BACKEND).orEmpty().lowercase();
    val backend = when (configuredBackend) {
      "local" -> Backend.LOCAL
      "ollama" -> Backend.OLLAMA
      else -> throw IllegalArgumentException("実行先backendが未設定です。Tasker/MacroDroid設定を保存し直してください")
    };
    val request = GenerateRequest(backend, model, values?.getString(LocalePluginContract.KEY_PROMPT).orEmpty(), system);
    val result = DefaultInferenceRepository.generateText(applicationContext, request);
    Log.i(TAG, "LLM生成成功: backend=" + backend + " resultChars=" + result.length)
    val extras = Bundle().apply {
      putBoolean(BridgeContract.EXTRA_OK, true);
      putString(BridgeContract.EXTRA_REQUEST_ID, intent.getStringExtra(BridgeContract.EXTRA_REQUEST_ID));
      putString(BridgeContract.EXTRA_RESULT, result);
      putString("response", result);
      putString("answer", result);
          };
    sendReply(intent.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION)?.takeIf(String::isNotBlank) ?: BridgeContract.ACTION_RESULT,
      intent.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE), extras);
    TaskerPlugin.addVariableBundle(extras, Bundle().apply { putString("%answer", result); putString("%ok", "true") })
    val variables = Bundle().apply { putString("%answer", result); putString("%ok", "true") };
    val signaled = TaskerPlugin.Setting.signalFinish(applicationContext, intent, TaskerPlugin.Setting.RESULT_CODE_OK, variables);
    Log.i(TAG, "signalFinish完了通知: signaled=" + signaled + " %answer文字数=" + result.length)
    if (values?.getString(LocalePluginContract.KEY_PLATFORM) == "macrodroid") {
      sendBroadcast(Intent(BridgeContract.ACTION_MACRODROID_RESULT).putExtras(extras).putExtra(BridgeContract.EXTRA_MODEL, model));
    }
  }

  private fun sendReply(action: String, packageName: String?, ok: Boolean, result: String?, error: String?) {
    val extras = Bundle().apply {
      putBoolean(BridgeContract.EXTRA_OK, ok);
      result?.let { putString(BridgeContract.EXTRA_RESULT, it) };
      error?.let { putString(BridgeContract.EXTRA_ERROR, it) };
    };
    sendReply(action, packageName, extras);
  }

  private fun sendReply(action: String, packageName: String?, extras: Bundle) {
    val reply = Intent(action).putExtras(extras);
    packageName?.takeIf(String::isNotBlank)?.let(reply::setPackage);
    sendBroadcast(reply);
  }

  private fun createChannel() {
    getSystemService(NotificationManager::class.java).createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "LLM推論", NotificationManager.IMPORTANCE_LOW)
    );
  }

  private fun notification(model: String): Notification = Notification.Builder(this, CHANNEL_ID)
    .setContentTitle("推論中")
    .setContentText(if (model.isBlank()) "LLMを実行しています" else model + " を実行しています")
    .setSmallIcon(android.R.drawable.stat_sys_upload)
    .setOngoing(true)
    .build();

  override fun onDestroy() { scope.cancel(); super.onDestroy(); }
  override fun onBind(intent: Intent?): IBinder? = null;

  companion object {
    const val EXTRA_ORIGIN = "com.example.ollamataskerbridge.bridge.ORIGIN";
    private const val TAG = "OllamaTaskerBridge"
    const val ORIGIN_LOCALE = "locale";
    private const val CHANNEL_ID = "inference_foreground";
    private const val NOTIFICATION_ID = 3001;
  }
}
