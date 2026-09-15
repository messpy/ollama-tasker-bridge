package com.example.ollamataskerbridge.data

import android.content.Context
import android.app.ActivityManager
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject

data class SystemPromptPreset(val id: String, val name: String, val body: String, val maxTokens: Int = 1024, val temperature: Float = 0.7f)

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

  fun exportJson(): String {
    val root = JSONObject().put("format", "ollama-tasker-bridge-settings").put("version", 1).put("endpoint", endpoint).put("apiKey", apiKey).put("huggingFaceToken", huggingFaceToken).put("lastPresetId", lastPresetId).put("pluginPlatform", pluginPlatform).put("modelSource", modelSource).put("liteRtContextTokens", liteRtContextTokens).put("gemmaTermsAccepted", gemmaTermsAccepted).put("minLocalModelSizeGb", minLocalModelSizeGb).put("maxLocalModelSizeGb", maxLocalModelSizeGb)
    root.put("enabledModelSources", JSONArray(enabledModelSources.map { it.name }))
    root.put("cachedModels", JSONArray(prefs.getString("cached_models", "[]")))
    root.put("presets", JSONArray(prefs.getString("system_prompt_presets", "[]")))
    return root.toString(2)
  }

  fun importJson(text: String) {
    val root = JSONObject(text)
    require(root.optString("format") == "ollama-tasker-bridge-settings") { "このファイルは設定バックアップではありません" }
    require(root.optInt("version", 0) == 1) { "対応していない設定バックアップ形式です" }
    val edit = prefs.edit()
    if (root.has("endpoint")) edit.putString("endpoint", root.optString("endpoint"))
    if (root.has("apiKey")) edit.putString("api_key", root.optString("apiKey"))
    if (root.has("huggingFaceToken")) edit.putString("huggingface_token", root.optString("huggingFaceToken"))
    if (root.has("lastPresetId")) edit.putString("last_preset_id", root.optString("lastPresetId"))
    if (root.has("pluginPlatform")) edit.putString("plugin_platform", root.optString("pluginPlatform"))
    if (root.has("modelSource")) edit.putString("model_source", root.optString("modelSource"))
    if (root.has("liteRtContextTokens")) edit.putInt("litert_context_tokens", root.optInt("liteRtContextTokens", 2048).coerceIn(512, 8192))
    if (root.has("gemmaTermsAccepted")) edit.putBoolean("gemma_terms_accepted", root.optBoolean("gemmaTermsAccepted"))
    if (root.has("minLocalModelSizeGb")) edit.putFloat("min_local_model_size_gb", root.optDouble("minLocalModelSizeGb", 0.0).toFloat().coerceAtLeast(0f))
    if (root.has("maxLocalModelSizeGb")) edit.putFloat("max_local_model_size_gb", root.optDouble("maxLocalModelSizeGb", 15.0).toFloat().coerceAtLeast(0f))
    root.optJSONArray("enabledModelSources")?.let { values -> edit.putStringSet("enabled_model_sources", (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.toSet()) }
    root.optJSONArray("cachedModels")?.let { edit.putString("cached_models", it.toString()) }
    root.optJSONArray("presets")?.let { edit.putString("system_prompt_presets", it.toString()) }
    edit.apply()
  }

  fun cachedModels(): List<OllamaModel> = runCatching {
    val array = JSONArray(prefs.getString("cached_models", "[]"))
    (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
      val storedSource = item.optString("source", ModelSource.OLLAMA.name)
      val source = if (storedSource == "LITERT_LM") ModelSource.HUGGING_FACE else runCatching { ModelSource.valueOf(storedSource) }.getOrDefault(ModelSource.OLLAMA)
      val format = if (storedSource == "LITERT_LM") ModelFormat.LITERT_LM else runCatching { ModelFormat.valueOf(item.optString("format", ModelFormat.GGUF.name)) }.getOrDefault(ModelFormat.GGUF)
      OllamaModel(item.optString("name"), item.optBoolean("remote", false), item.optBoolean("downloadable", true), item.optLong("size", -1L), item.optBoolean("local", false), source, item.optString("downloadUrl"), item.optBoolean("enabled", false), item.optBoolean("vision", false), format)
    } }
  }.getOrDefault(emptyList())

  fun setModelEnabled(name: String, enabled: Boolean) { saveCachedModels(cachedModels().map { if (it.name == name) it.copy(enabled = enabled) else it }) }
  fun saveCachedModels(models: List<OllamaModel>) {
    val array = JSONArray().apply { models.forEach { put(JSONObject().put("name", it.name).put("remote", it.remote).put("downloadable", it.downloadable).put("size", it.sizeBytes).put("local", it.local).put("source", it.source.name).put("downloadUrl", it.downloadUrl).put("enabled", it.enabled).put("vision", it.vision).put("format", it.format.name)) } }
    prefs.edit().putString("cached_models", array.toString()).apply()
  }

  fun presets(): List<SystemPromptPreset> = runCatching {
    val array = JSONArray(prefs.getString("system_prompt_presets", "[]"))
    (0 until array.length()).mapNotNull { index ->
      array.optJSONObject(index)?.let { item ->
        SystemPromptPreset(item.optString("id"), item.optString("name"), item.optString("body"), item.optInt("maxTokens", 1024), item.optDouble("temperature", 0.7).toFloat())
      }?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
    }
  }.getOrDefault(emptyList())

  fun savePreset(preset: SystemPromptPreset) {
    val values = presets().filterNot { it.id == preset.id } + preset
    val array = JSONArray().apply { values.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("body", it.body).put("maxTokens", it.maxTokens).put("temperature", it.temperature)) } }
    prefs.edit().putString("system_prompt_presets", array.toString()).putString("last_preset_id", preset.id).apply()
  }

  fun deletePreset(id: String) {
    val array = JSONArray().apply { presets().filterNot { it.id == id }.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("body", it.body).put("maxTokens", it.maxTokens).put("temperature", it.temperature)) } }
    prefs.edit().putString("system_prompt_presets", array.toString()).apply()
  }
}
