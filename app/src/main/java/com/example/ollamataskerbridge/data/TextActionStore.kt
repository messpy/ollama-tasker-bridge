package com.example.ollamataskerbridge.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TextActionOutputMode { DISPLAY, COPY, REPLACE }

data class TextAction(
  val id: String,
  val name: String,
  val enabled: Boolean = true,
  val model: String = "",
  val backend: String = "auto",
  val promptTemplate: String,
  val systemPromptPresetId: String = "",
  val customSystemPrompt: String = "",
  val maxTokens: Int = 1024,
  val temperature: Float = 0.7f,
  val outputMode: TextActionOutputMode = TextActionOutputMode.DISPLAY,
)

class TextActionStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("connection_settings", Context.MODE_PRIVATE)
  private val key = "text_actions"

  fun actions(): List<TextAction> {
    val saved = runCatching {
      val array = JSONArray(prefs.getString(key, "[]"))
      (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toAction() }
    }.getOrDefault(emptyList())
    if (saved.isNotEmpty()) return saved
    val defaults = defaultActions()
    saveAll(defaults)
    return defaults
  }

  fun save(action: TextAction) = saveAll(actions().filterNot { it.id == action.id } + action)
  fun delete(id: String) = saveAll(actions().filterNot { it.id == id })
  fun saveAll(actions: List<TextAction>) {
    val json = JSONArray().apply { actions.forEach { put(it.toJson()) } }
    prefs.edit().putString(key, json.toString()).apply()
  }

  private fun JSONObject.toAction() = TextAction(
    id = optString("id"), name = optString("name"), enabled = optBoolean("enabled", true),
    model = optString("model"), backend = optString("backend", "auto"),
    promptTemplate = optString("promptTemplate"), systemPromptPresetId = optString("systemPromptPresetId"),
    customSystemPrompt = optString("customSystemPrompt"), maxTokens = optInt("maxTokens", 1024),
    temperature = optDouble("temperature", 0.7).toFloat(),
    outputMode = runCatching { TextActionOutputMode.valueOf(optString("outputMode")) }.getOrDefault(TextActionOutputMode.DISPLAY),
  ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.promptTemplate.isNotBlank() }!!

  private fun TextAction.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("enabled", enabled); put("model", model); put("backend", backend)
    put("promptTemplate", promptTemplate); put("systemPromptPresetId", systemPromptPresetId)
    put("customSystemPrompt", customSystemPrompt); put("maxTokens", maxTokens); put("temperature", temperature)
    put("outputMode", outputMode.name)
  }

  companion object {
    fun newId() = UUID.randomUUID().toString()
    private fun defaultActions() = listOf(
      TextAction("summary", "要約", promptTemplate = "以下の文章を簡潔に要約してください。\n\n{{text}}"),
      TextAction("translate-ja", "日本語へ翻訳", promptTemplate = "以下の文章を自然な日本語に翻訳してください。\n\n{{text}}"),
      TextAction("explain", "わかりやすく説明", promptTemplate = "以下の文章の内容を、初心者にもわかるように説明してください。\n\n{{text}}"),
      TextAction("proofread", "校正", promptTemplate = "以下の文章の誤字脱字や不自然な表現を直してください。修正版だけを返してください。\n\n{{text}}"),
    )
  }
}

object ModelCatalog {
  fun available(context: Context): List<OllamaModel> {
    val settings = SettingsStore(context)
    val localStore = LocalModelStore(context)
    val local = localStore.directory.listFiles().orEmpty()
      .filter { it.extension == "gguf" || it.extension == "litertlm" }
      .map { file -> OllamaModel(file.nameWithoutExtension, false, true, file.length(), true, if (file.extension == "litertlm") ModelSource.LITERT_LM else ModelSource.HUGGING_FACE) }
    val names = local.map { it.name }.toSet()
    return (settings.cachedModels() + local).distinctBy { it.name }
      .map { it.copy(local = it.local || it.name in names) }
      .filter { it.local || it.enabled }
  }
}
