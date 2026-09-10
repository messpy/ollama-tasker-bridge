package com.example.ollamataskerbridge.data

import android.content.Context
import android.app.ActivityManager
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject

data class SystemPromptPreset(val id: String, val name: String, val body: String)

class SettingsStore(context: Context) {
  private val appContext = context.applicationContext

  private fun recommendedModelSizeGb(): Float {
    val memory = ActivityManager.MemoryInfo().also { appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
    val freeStorageGb = StatFs(appContext.filesDir.path).availableBytes / 1_000_000_000.0
    return (minOf(memory.totalMem / 1_000_000_000.0 * 0.5, (freeStorageGb - 1.0).coerceAtLeast(0.5))).coerceIn(0.5, 8.0).toFloat()
  }
  private val prefs = context.getSharedPreferences("connection_settings", Context.MODE_PRIVATE)
  var endpoint: String
    get() = prefs.getString("endpoint", null)
      ?.takeUnless { it.contains("100.122.68.52") || it.contains("127.0.0.1:11434") }
      ?: "https://ollama.com"
    set(value) { prefs.edit().putString("endpoint", value.trim()).apply() }
  var apiKey: String
    get() = prefs.getString("api_key", "").orEmpty()
    set(value) { prefs.edit().putString("api_key", value.trim()).apply() }
  var huggingFaceToken: String
    get() = prefs.getString("huggingface_token", "").orEmpty()
    set(value) { prefs.edit().putString("huggingface_token", value.trim()).apply() }

  var lastPresetId: String
    get() = prefs.getString("last_preset_id", "").orEmpty()
    set(value) { prefs.edit().putString("last_preset_id", value).apply() }

  var pluginPlatform: String
    get() = prefs.getString("plugin_platform", "tasker").orEmpty()
    set(value) { prefs.edit().putString("plugin_platform", value).apply() }

  var modelSource: String
    get() = prefs.getString("model_source", "").orEmpty()
    set(value) { prefs.edit().putString("model_source", value).apply() }

  var liteRtContextTokens: Int
    get() = prefs.getInt("litert_context_tokens", 2048).coerceIn(512, 8192)
    set(value) { prefs.edit().putInt("litert_context_tokens", value.coerceIn(512, 8192)).apply() }

  var enabledModelSources: Set<ModelSource>
    get() = prefs.getStringSet("enabled_model_sources", null)
      ?.mapNotNull { runCatching { ModelSource.valueOf(it) }.getOrNull() }
      ?.toSet()
      ?.ifEmpty { ModelSource.values().toSet() }
      ?: ModelSource.values().toSet()
    set(value) { prefs.edit().putStringSet("enabled_model_sources", value.map { it.name }.toSet()).apply() }

  var gemmaTermsAccepted: Boolean
    get() = prefs.getBoolean("gemma_terms_accepted", false)
    set(value) { prefs.edit().putBoolean("gemma_terms_accepted", value).apply() }

  var minLocalModelSizeGb: Float
    get() = prefs.getFloat("min_local_model_size_gb", 0f)
    set(value) { prefs.edit().putFloat("min_local_model_size_gb", value.coerceAtLeast(0f)).apply() }

  var maxLocalModelSizeGb: Float
    get() {
      val saved = if (prefs.contains("max_local_model_size_gb")) prefs.getFloat("max_local_model_size_gb", 15f) else null
      return if (saved == null || saved == 15f) recommendedModelSizeGb() else saved
    }
    set(value) { prefs.edit().putFloat("max_local_model_size_gb", value.coerceAtLeast(0f)).apply() }

  fun cachedModels(): List<OllamaModel> = runCatching {
    val array = JSONArray(prefs.getString("cached_models", "[]"))
    (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let {
      OllamaModel(it.optString("name"), it.optBoolean("remote", false), it.optBoolean("downloadable", true), it.optLong("size", -1L), it.optBoolean("local", false), runCatching { ModelSource.valueOf(it.optString("source", ModelSource.OLLAMA.name)) }.getOrDefault(ModelSource.OLLAMA), it.optString("downloadUrl"))
    } }
  }.getOrDefault(emptyList())

  fun saveCachedModels(models: List<OllamaModel>) {
    val array = JSONArray().apply { models.forEach { put(JSONObject().put("name", it.name).put("remote", it.remote).put("downloadable", it.downloadable).put("size", it.sizeBytes).put("local", it.local).put("source", it.source.name).put("downloadUrl", it.downloadUrl)) } }
    prefs.edit().putString("cached_models", array.toString()).apply()
  }

  fun presets(): List<SystemPromptPreset> = runCatching {
    val array = JSONArray(prefs.getString("system_prompt_presets", "[]"))
    (0 until array.length()).mapNotNull { index ->
      array.optJSONObject(index)?.let { item ->
        SystemPromptPreset(item.optString("id"), item.optString("name"), item.optString("body"))
      }?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
    }
  }.getOrDefault(emptyList())

  fun savePreset(preset: SystemPromptPreset) {
    val values = presets().filterNot { it.id == preset.id } + preset
    val array = JSONArray().apply { values.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("body", it.body)) } }
    prefs.edit().putString("system_prompt_presets", array.toString()).putString("last_preset_id", preset.id).apply()
  }

  fun deletePreset(id: String) {
    val array = JSONArray().apply { presets().filterNot { it.id == id }.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("body", it.body)) } }
    prefs.edit().putString("system_prompt_presets", array.toString()).apply()
  }
}
