package com.example.ollamataskerbridge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class OllamaModel(val name: String, val remote: Boolean)

class OllamaClient(private val baseUrl: String, private val apiKey: String = "") {
  suspend fun listModels(): List<OllamaModel> = withContext(Dispatchers.IO) {
    request("GET", "/api/tags").let { body ->
      val models = JSONArray(org.json.JSONObject(body).optJSONArray("models")?.toString() ?: "[]")
      (0 until models.length()).mapNotNull { models.optJSONObject(it)?.let { item ->
        item.optString("name").takeIf(String::isNotBlank)?.let { name -> OllamaModel(name, !item.optString("remote_host").isNullOrBlank()) }
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

  suspend fun generate(model: String, prompt: String, system: String? = null): String = withContext(Dispatchers.IO) {
    require(model.isNotBlank()) { "モデル名が必要です" }
    require(prompt.isNotBlank()) { "プロンプトが必要です" }
    val payload = org.json.JSONObject()
      .put("model", model)
      .put("prompt", prompt)
      .put("stream", false)
    if (!system.isNullOrBlank()) payload.put("system", system)
    val response = org.json.JSONObject(request("POST", "/api/generate", payload.toString()))
    response.optString("response").takeIf { it.isNotBlank() }
      ?: throw IOException("Ollamaから応答がありません")
  }

  private fun request(method: String, path: String, body: String? = null, readTimeoutMs: Int = 30_000): String {
    val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = 8_000
      readTimeout = readTimeoutMs
      setRequestProperty("Accept", "application/json")
      if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
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
      if (code !in 200..299) throw IOException("Ollama returned HTTP $code")
      return response
    } finally { connection.disconnect() }
  }
}
