package com.example.ollamataskerbridge.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

enum class ModelSource { OLLAMA, HUGGING_FACE, LITERT_LM }

fun OllamaModel.supportsVision(): Boolean {
  val value = name.lowercase()
  return vision || when (source) { ModelSource.LITERT_LM -> listOf("gemma3n", "gemma-3n").any { value.contains(it) }; ModelSource.OLLAMA, ModelSource.HUGGING_FACE -> listOf("minicpm-v", "minicpmv", "llava", "gemma3n", "gemma-3n", "gemma3", "gemma-3", "qwen2-vl", "qwen2.5-vl", "qwen2.5vl", "qwen3-vl", "qwen3vl", "qwen-vl", "vision", "moondream", "pixtral", "internvl", "molmo", "glm-5.3-flash", "glm-4.1v", "qwen3.8", "ornith", "llama4", "mistral-small3.1", "mistral-small3.2", "granite-vision", "granite3.2-vision", "phi-4-multimodal", "phi4-multimodal").any { value.contains(it) } }
}

data class OllamaModel(
  val name: String,
  val remote: Boolean,
  val downloadable: Boolean = true,
  val sizeBytes: Long = -1L,
  val local: Boolean = false,
  val source: ModelSource = ModelSource.OLLAMA,
  val downloadUrl: String = "",
  val enabled: Boolean = false,
  /* Ollama検索結果のvision能力タグ。名前だけでは判定できないモデル用。 */
  val vision: Boolean = false,
)

class OllamaClient(private val baseUrl: String, private val apiKey: String = "") {
  private val logTag = "OllamaClient"
  suspend fun listModels(): List<OllamaModel> = withContext(Dispatchers.IO) {
    request("GET", "/api/tags").let { body ->
      val models = JSONArray(org.json.JSONObject(body).optJSONArray("models")?.toString() ?: "[]")
      (0 until models.length()).mapNotNull { models.optJSONObject(it)?.let { item ->
        item.optString("name").takeIf(String::isNotBlank)?.let { name ->
          val format = item.optJSONObject("details")?.optString("format").orEmpty()
          val remoteHost = item.optString("remote_host")
          val cloudModel = remoteHost.isNotBlank() || name.contains(":cloud", ignoreCase = true) || name.startsWith("gpt-oss", ignoreCase = true)
          OllamaModel(name, cloudModel, format == "gguf", item.optLong("size", -1L))
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

  suspend fun ping() = withContext(Dispatchers.IO) { request("GET", "/api/tags"); Unit }

  suspend fun generate(model: String, prompt: String, system: String? = null, maxTokens: Int = 1024, temperature: Float = 0.7f, imageBytes: ByteArray? = null): String = withContext(Dispatchers.IO) {
    require(model.isNotBlank()) { "モデル名が必要です" }
    require(prompt.isNotBlank()) { "プロンプトが必要です" }
    val payload = org.json.JSONObject()
      .put("model", model)
      .put("prompt", prompt)
      .put("stream", false)
      .put("think", "low")
    if (!system.isNullOrBlank()) payload.put("system", system)
    imageBytes?.let { payload.put("images", org.json.JSONArray().put(Base64.getEncoder().encodeToString(it))) }
    val effectiveMaxTokens = if (model.startsWith("gpt-oss", ignoreCase = true)) maxOf(maxTokens, 1024) else maxTokens.coerceAtLeast(1)
    payload.put("options", org.json.JSONObject().put("num_predict", effectiveMaxTokens).put("temperature", temperature.coerceIn(0f, 2f)))
    val response = org.json.JSONObject(request("POST", "/api/generate", payload.toString()))
    val text = response.optString("response")
    val thinking = response.optString("thinking")
    val doneReason = response.optString("done_reason", "unknown")
    val evalCount = response.optLong("eval_count", -1L)
    Log.d(logTag, "generate responseChars=" + text.length + " thinkingChars=" + thinking.length + " done=" + response.optBoolean("done", false) + " doneReason=" + doneReason + " evalCount=" + evalCount + " maxTokens=" + effectiveMaxTokens)
    if (text.isNotBlank()) return@withContext text
    if (thinking.isNotBlank() && doneReason == "length") {
      throw IOException("Ollamaがthinking中に生成上限へ到達しました（thinkingChars=" + thinking.length + ", evalCount=" + evalCount + ", maxTokens=" + effectiveMaxTokens + ").最大トークン数を増やしてください")
    }
    throw IOException(if (thinking.isNotBlank()) "Ollamaが本文を返さず終了しました（thinkingChars=" + thinking.length + ", doneReason=" + doneReason + ")" else "Ollamaから応答がありません（responseとthinkingが空です、doneReason=" + doneReason + ")")
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
