package com.example.ollamataskerbridge.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

class LocalModelStore(context: Context) {
  val directory: File = File(context.filesDir, "models").also { it.mkdirs() }
  fun fileFor(model: String): File {
    require(model.isNotBlank()) { "モデル名が必要です" }
    val safe = model.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(directory, safe + ".gguf")
  }
}

class OllamaRegistryClient(
  private val store: LocalModelStore,
  private val registryBase: String = "https://registry.ollama.ai",
) {
  data class ModelMetadata(val downloadable: Boolean, val sizeBytes: Long)
  suspend fun catalog(): List<OllamaModel> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val connection = open("https://ollama.com/search?o=newest", readTimeoutMs = 30_000)
    try {
      check(connection.responseCode in 200..299) { "モデル検索 HTTP " + connection.responseCode }
      val html = connection.inputStream.bufferedReader().use { it.readText() }
      Regex("href=\\\"/library/([a-zA-Z0-9._/-]+)\\\"")
        .findAll(html)
        .map { it.groupValues[1] }
        .distinct()
        .map { OllamaModel(it, true, true, -1L, false) }
        .toList()
    } finally { connection.disconnect() }
  }
  private val logTag = "OllamaRegistry"
  suspend fun metadata(model: String): ModelMetadata = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val parsed = parseModel(model)
    val manifestUrl = registryBase + "/v2/library/" + parsed.first + "/manifests/" + parsed.second
    val layers = requestJson(manifestUrl).optJSONArray("layers") ?: return@withContext ModelMetadata(false, -1L)
    var size = -1L
    var downloadable = false
    for (i in 0 until layers.length()) {
      val layer = layers.getJSONObject(i)
      if (layer.optString("mediaType") == "application/vnd.ollama.image.model") {
        downloadable = true
        size = layer.optLong("size", -1L)
        break
      }
    }
    ModelMetadata(downloadable, size)
  }
  fun download(model: String): File {
    hfUrlFor(model)?.let { return downloadHf(it, model) }
    val parsed = parseModel(model)
    val manifestUrl = registryBase + "/v2/library/" + parsed.first + "/manifests/" + parsed.second
    Log.d(logTag, "GET " + manifestUrl)
    val manifest = requestJson(manifestUrl)
    val layers = manifest.optJSONArray("layers") ?: error("モデルマニフェストにレイヤーがありません")
    var modelDigest: String? = null
    var modelSize = -1L
    for (i in 0 until layers.length()) {
      val layer = layers.getJSONObject(i)
      if (layer.optString("mediaType") == "application/vnd.ollama.image.model") {
        modelDigest = layer.optString("digest")
        modelSize = layer.optLong("size", -1L)
        break
      }
    }
    val digest = requireNotNull(modelDigest) { "GGUFモデルレイヤーが見つかりません" }
    Log.d(logTag, "model=" + model + " size=" + modelSize + " digest=" + digest)
    val target = store.fileFor(model)
    val temp = File(target.path + ".download")
    downloadBlob(registryBase + "/v2/library/" + parsed.first + "/blobs/" + digest, digest, modelSize, temp)
    check(temp.renameTo(target)) { "モデルファイルを保存できません" }
    return target
  }

  private fun hfUrlFor(model: String): String? = when (model.lowercase()) {
    "smollm2:135m", "smollm2_135m" -> "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
    "tinyllama:1.1b", "tinyllama_1.1b" -> "https://huggingface.co/bartowski/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf"
    else -> null
  }

  private fun downloadHf(url: String, model: String): File {
    val target = store.fileFor(model)
    val temp = File(target.path + ".download")
    var resumeBytes = temp.takeIf { it.isFile }?.length() ?: 0L
    val connection = open(url, readTimeoutMs = 15 * 60 * 1000)
    if (resumeBytes > 0L) connection.setRequestProperty("Range", "bytes=" + resumeBytes + "-")
    try {
      if (resumeBytes > 0L && connection.responseCode == HttpURLConnection.HTTP_OK) { temp.delete(); resumeBytes = 0L }
      check(connection.responseCode in 200..299) { "Hugging Face HTTP " + connection.responseCode }
      FileOutputStream(temp, resumeBytes > 0L).use { output -> connection.inputStream.use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
      } }
      check(temp.length() > 0L) { "GGUFファイルが空です" }
      check(temp.renameTo(target)) { "モデルファイルを保存できません" }
      Log.i(logTag, "Hugging Face download complete: " + target.name + " bytes=" + target.length())
      return target
    } finally { connection.disconnect() }
  }

  private fun parseModel(model: String): Pair<String, String> {
    val value = model.trim().removePrefix("library/")
    val slash = value.lastIndexOf('/')
    val colon = value.lastIndexOf(':')
    val hasTag = colon > slash
    val repository = value.substring(0, if (hasTag) colon else value.length)
    val tag = if (hasTag) value.substring(colon + 1) else "latest"
    require(repository.matches(Regex("[a-zA-Z0-9._/-]+")) && tag.matches(Regex("[a-zA-Z0-9._-]+"))) { "不正なモデル名です" }
    return repository to tag
  }

  private fun requestJson(url: String): JSONObject {
    val connection = open(url, readTimeoutMs = 30_000)
    try {
      check(connection.responseCode in 200..299) { "Registry HTTP " + connection.responseCode }
      return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally { connection.disconnect() }
  }

  private fun downloadBlob(url: String, digest: String, expectedSize: Long, temp: File) {
    Log.d(logTag, "download start url=" + url + " expectedSize=" + expectedSize + " temp=" + temp.name)
    var resumeBytes = temp.takeIf { it.isFile }?.length() ?: 0L
    val connection = open(url, readTimeoutMs = 15 * 60 * 1000)
    if (resumeBytes > 0L) connection.setRequestProperty("Range", "bytes=" + resumeBytes + "-")
    try {
      if (resumeBytes > 0L && connection.responseCode == HttpURLConnection.HTTP_OK) {
        Log.w(logTag, "registry ignored Range; restarting download")
        temp.delete()
        resumeBytes = 0L
      }
      check(connection.responseCode in 200..299) { "モデルBlob HTTP " + connection.responseCode }
      val digestor = MessageDigest.getInstance("SHA-256")
      if (resumeBytes > 0L) FileInputStream(temp).use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          digestor.update(buffer, 0, count)
        }
      }
      var total = 0L
      FileOutputStream(temp, resumeBytes > 0L).use { output ->
        connection.inputStream.use { input ->
          val buffer = ByteArray(1024 * 1024)
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            digestor.update(buffer, 0, count)
            total += count
          }
        }
      }
      total += resumeBytes
      check(expectedSize < 0 || total == expectedSize) { "モデルサイズが一致しません" }
      val actual = "sha256:" + digestor.digest().joinToString("") { "%02x".format(it) }
      check(actual == digest) { "モデル検証に失敗しました" }
      Log.d(logTag, "download complete bytes=" + total)
    } finally { connection.disconnect() }
  }

  private fun open(url: String, readTimeoutMs: Int) = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 15_000
    readTimeout = readTimeoutMs
    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8a) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
    setRequestProperty("Accept", "application/vnd.docker.distribution.manifest.v2+json, application/json")
  }
}
