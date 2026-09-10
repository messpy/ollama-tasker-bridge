package com.example.ollamataskerbridge.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
class HuggingFaceClient {
  suspend fun catalog(accessToken: String = ""): List<OllamaModel> = withContext(Dispatchers.IO) {
    val found = linkedMapOf<String, OllamaModel>()
    val queries = listOf("https://huggingface.co/api/models?filter=gguf&sort=downloads&direction=-1&limit=100", "https://huggingface.co/api/models?search=litertlm&sort=downloads&direction=-1&limit=100")
    queries.forEach { query ->
      val listing = runCatching { JSONArray(request(query, accessToken)) }.getOrNull() ?: return@forEach
      for (index in 0 until listing.length()) {
        val id = listing.optJSONObject(index)?.optString("id").orEmpty()
        if (id.isBlank()) continue
        val detail = runCatching { JSONObject(request("https://huggingface.co/api/models/" + id, accessToken)) }.getOrNull() ?: continue
        val files = detail.optJSONArray("siblings") ?: continue
        val candidate = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
          .map { it.optString("rfilename") to it.optJSONObject("lfs")?.optLong("size", it.optLong("size", -1L)) }
          .filter { it.first.endsWith(".gguf", true) || it.first.endsWith(".litertlm", true) }
          .sortedWith(compareBy<Pair<String, Long?>> { if (it.first.contains("Q4_K_M", true) || it.first.contains("int4", true)) 0 else 1 }.thenBy { it.first })
          .firstOrNull() ?: continue
        val litert = candidate.first.endsWith(".litertlm", true)
        val source = if (litert) ModelSource.LITERT_LM else ModelSource.HUGGING_FACE
        val fileSize = candidate.second ?: -1L
        found[id + ":" + candidate.first] = OllamaModel(id, false, true, fileSize, false, source, "https://huggingface.co/" + id + "/resolve/main/" + candidate.first)
      }
    }
    found.values.toList()
  }

  private fun request(url: String, accessToken: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 8000
      connection.readTimeout = 30000
      connection.setRequestProperty("Accept", "application/json")
      if (accessToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer " + accessToken.removePrefix("Bearer ").trim())
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      check(code in 200..299) { "Hugging Face HTTP " + code + if (body.isNotBlank()) ": " + body.take(240) else "" }
      return body
    } finally { connection.disconnect() }
  }
}
