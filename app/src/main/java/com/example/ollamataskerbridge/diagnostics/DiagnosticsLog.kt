package com.example.ollamataskerbridge.diagnostics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-owned warning/error buffer plus best-effort relevant Logcat export. */
object DiagnosticsLog {
  private const val tag = "OllamaTaskerBridge"
  private val entries = ArrayDeque<String>()

  @Synchronized
  fun note(message: String) {
    record("I", message)
    Log.i(tag, message)
  }


  @Synchronized
  fun warn(message: String) {
    record("W", message)
    Log.w(tag, message)
  }

  @Synchronized
  fun error(message: String) {
    record("E", message)
    Log.e(tag, message)
  }

  @Synchronized
  private fun record(level: String, message: String) {
    if (entries.size >= 200) entries.removeFirst()
    entries.addLast("$level: ${message.take(500)}")
  }

  suspend fun copyableSnapshot(): String = withContext(Dispatchers.IO) {
    val appEntries = synchronized(this@DiagnosticsLog) { entries.toList() }
    val logcat = runCatching<String> {
      val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "*:W", "-t", "400").redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().use { it.readText() }
      process.waitFor()
      output.lineSequence()
        .filter { line -> listOf("OllamaTaskerBridge", "OllamaClient", "OllamaRegistry", "LiteRtLm", "Inference", "ai-chat", "ggml", "AndroidRuntime").any { tag -> line.contains(tag, ignoreCase = true) } }
        .toList().takeLast(200)
        .joinToString("\n")
    }.getOrDefault("")
    buildString {
      append("Ollama Tasker Bridge diagnostics\n")
      append("最低レベル: WARN\n\n")
      if (appEntries.isNotEmpty()) { append("[アプリ内ログ]\n"); append(appEntries.joinToString("\n")); append("\n\n") }
      if (logcat.isNotBlank()) { append("[関連Logcat]\n"); append(logcat) }
      if (appEntries.isEmpty() && logcat.isBlank()) append("警告以上のログはありません。")
    }
  }
}
