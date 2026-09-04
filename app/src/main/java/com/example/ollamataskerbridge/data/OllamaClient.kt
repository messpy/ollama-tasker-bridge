package com.example.ollamataskerbridge.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class OllamaModel(
  val name: String,
  val remote: Boolean,
  val downloadable: Boolean = true,
  val sizeBytes: Long = -1L,
  val local: Boolean = false,
)

class OllamaClient(private val baseUrl: String, private val apiKey: String = "") {
  private val logTag = "OllamaClient"
  suspend fun listModels(): List<OllamaModel> = withContext(Dispatchers.IO) {
    request("GET", "/api/tags", authenticated = false).let { body ->
      val models = JSONArray(org.json.JSONObject(body).optJSONArray("models")?.toString() ?: "[]")
      (0 until models.length()).mapNotNull { models.optJSONObject(it)?.let { item ->
        item.optString("name").takeIf(String::isNotBlank)?.let { name ->
          val format = item.optJSONObject("details")?.optString("format").orEmpty()
          OllamaModel(name, !item.optString("remote_host").isNullOrBlank(), format == "gguf", item.optLong("size", -1L))
        }
      } }
    }
  }

  suspend fun pullModel(name: String) = withContext(Dispatchers.IO) {
    require(name.isNotBlank())
    request("POST", "/api/pull", org.json.JSONObject().put("model", name).put("stream", false).toString(), readTimeoutMs = 15 * 60 * 1000)
  }

  suspend fun deleteModel(name: String) = withContext(Dispatchers.IO) {
    require(name.isNotBlank() && !name.contains("/../") && !name.contains("\\"))
    request("DELETE", "/api/delete", org.json.JSONObject().put("model", name).toString())
  }

  suspend fun ping() = withContext(Dispatchers.IO) { request("GET", "/api/tags", authenticated = false); Unit }

  suspend fun generate(model: String, prompt: String, system: String? = null, maxTokens: Int = 256, temperature: Float = 0.7f): String = withContext(Dispatchers.IO) {
    require(model.isNotBlank()) { "モデル名が必要です" }
    require(prompt.isNotBlank()) { "プロンプトが必要です" }
    val payload = org.json.JSONObject()
      .put("model", model)
      .put("prompt", prompt)
      .put("stream", false)
    if (!system.isNullOrBlank()) payload.put("system", system)
    payload.put("options", org.json.JSONObject().put("num_predict", maxTokens.coerceAtLeast(1)).put("temperature", temperature.coerceIn(0f, 2f)))
    val response = org.json.JSONObject(request("POST", "/api/generate", payload.toString()))
    response.optString("response").takeIf { it.isNotBlank() }
      ?: throw IOException("Ollamaから応答がありません")
  }

  private fun request(method: String, path: String, body: String? = null, readTimeoutMs: Int = 30_000, authenticated: Boolean = true): String {
    Log.d(logTag, method + " " + baseUrl + path + " authenticated=" + authenticated + " apiKeyPresent=" + apiKey.isNotBlank() + " apiKeyLength=" + apiKey.trim().removePrefix("Bearer ").length)
    val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = 8_000
      readTimeout = readTimeoutMs
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8a) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
      if (authenticated && apiKey.isNotBlank()) {
        val token = apiKey.removePrefix("Bearer ").trim()
        setRequestProperty("Authorization", "Bearer " + token)
      }
      if (body != null) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }
    }
    try {
      if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      if (code !in 200..299) {
        val detail = response.take(240).replace(Regex("\\s+"), " ")
        Log.e(logTag, "HTTP " + code + " for " + path + ": " + detail)
        throw IOException("Ollama HTTP " + code + if (detail.isNotBlank()) ": " + detail else "")
      }
      return response
    } finally { connection.disconnect() }
  }
}
