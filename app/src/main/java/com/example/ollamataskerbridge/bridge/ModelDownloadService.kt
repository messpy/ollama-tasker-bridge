package com.example.ollamataskerbridge.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaRegistryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ModelDownloadService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    createChannel()
    startForeground(1001, notification())
    val model = intent?.getStringExtra(BridgeContract.EXTRA_MODEL).orEmpty()
    val replyAction = intent?.getStringExtra(BridgeContract.EXTRA_REPLY_ACTION)
      ?.takeIf(String::isNotBlank) ?: BridgeContract.ACTION_RESULT
    val replyPackage = intent?.getStringExtra(BridgeContract.EXTRA_REPLY_PACKAGE)
    scope.launch {
      try {
        val file = OllamaRegistryClient(LocalModelStore(applicationContext)).download(model)
        sendReply(replyAction, replyPackage, true, "モデルをAndroidへ保存しました: ${file.name}", null)
      } catch (error: Exception) {
        sendReply(replyAction, replyPackage, false, null, error.message ?: "モデル取得に失敗しました")
      } finally {
        stopSelf(startId)
      }
    }
    return START_NOT_STICKY
  }

  private fun sendReply(action: String, packageName: String?, ok: Boolean, result: String?, error: String?) {
    val reply = Intent(action).apply {
      putExtra(BridgeContract.EXTRA_OK, ok)
      result?.let { putExtra(BridgeContract.EXTRA_RESULT, it) }
      error?.let { putExtra(BridgeContract.EXTRA_ERROR, it) }
      packageName?.takeIf(String::isNotBlank)?.let(::setPackage)
    }
    sendBroadcast(reply)
  }

  private fun createChannel() {
    getSystemService(NotificationManager::class.java).createNotificationChannel(
      NotificationChannel("model_download", "モデル取得", NotificationManager.IMPORTANCE_LOW),
    )
  }

  private fun notification(): Notification = Notification.Builder(this, "model_download")
    .setContentTitle("Ollamaモデルを取得中")
    .setContentText("Androidへモデルを保存しています")
    .setSmallIcon(android.R.drawable.stat_sys_download)
    .setOngoing(true)
    .build()

  override fun onDestroy() { scope.cancel(); super.onDestroy() }
  override fun onBind(intent: Intent?): IBinder? = null
}
