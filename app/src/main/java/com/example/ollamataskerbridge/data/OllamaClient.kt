package com.example.ollamataskerbridge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class OllamaClient(private val baseUrl: String) {
  suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
    request("GET", "/api/tags").let { body ->
      val models = JSONArray(org.json.JSONObject(body).optJSONArray("models")?.toString() ?: "[]")
      (0 until models.length()).mapNotNull { models.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
    }
  }

  suspend fun pullModel(name: String) = withContext(Dispatchers.IO) {
    require(name.isNotBlank())
    request("POST", "/api/pull", org.json.JSONObject().put("model", name).put("stream", false).toString())
  }

  suspend fun deleteModel(name: String) = withContext(Dispatchers.IO) {
    require(name.isNotBlank() && !name.contains("/../") && !name.contains("\\"))
    request("DELETE", "/api/delete", org.json.JSONObject().put("model", name).toString())
  }

  suspend fun ping() = withContext(Dispatchers.IO) { request("GET", "/api/tags"); Unit }

  private fun request(method: String, path: String, body: String? = null): String {
    val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = 8_000
      readTimeout = 30_000
      setRequestProperty("Accept", "application/json")
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
