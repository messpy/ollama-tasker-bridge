package com.example.ollamataskerbridge.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaRegistryClient
import com.example.ollamataskerbridge.data.HuggingFaceClient
import com.example.ollamataskerbridge.data.ModelSource
import com.example.ollamataskerbridge.data.SettingsStore
import com.example.ollamataskerbridge.data.SystemPromptPreset
import com.example.ollamataskerbridge.bridge.Backend
import com.example.ollamataskerbridge.bridge.DefaultInferenceRepository
import com.example.ollamataskerbridge.bridge.GenerateRequest
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
  private val settings = SettingsStore(application)
  private val localModels = LocalModelStore(application)
  private val registry = OllamaRegistryClient(localModels)
  private val huggingFace = HuggingFaceClient()
  private fun installedModels() = localModels.directory.listFiles()
    ?.filter { it.extension == "gguf" }
    ?.map { com.example.ollamataskerbridge.data.OllamaModel(it.nameWithoutExtension, false, true, it.length(), true) }
    .orEmpty()
  private val installed = installedModels()
  private val cached = settings.cachedModels()
  private val initialModels = (installed + cached).distinctBy { it.name }
  private val initialSource = runCatching { ModelSource.valueOf(settings.modelSource) }.getOrElse { if (installed.isNotEmpty() || cached.any { it.source == ModelSource.HUGGING_FACE }) ModelSource.HUGGING_FACE else ModelSource.OLLAMA }
  private val initialPresets = settings.presets()
  private val initialPreset = initialPresets.firstOrNull { it.id == settings.lastPresetId } ?: initialPresets.firstOrNull()
  private val _uiState = MutableStateFlow(MainScreenUiState(endpoint = settings.endpoint, apiKey = settings.apiKey, maxLocalModelSizeGb = settings.maxLocalModelSizeGb.toString(), systemPromptPresetId = initialPreset?.id.orEmpty(), systemPrompt = initialPreset?.body.orEmpty(), presets = initialPresets, models = initialModels, source = initialSource))
  val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

  fun endpointChanged(value: String) { _uiState.value = _uiState.value.copy(endpoint = value, message = null) }
  fun apiKeyChanged(value: String) { _uiState.value = _uiState.value.copy(apiKey = value, message = null) }
  fun downloadModelChanged(value: String) { _uiState.value = _uiState.value.copy(downloadModel = value, message = null) }
  fun testPromptChanged(value: String) { _uiState.value = _uiState.value.copy(testPrompt = value, message = null) }
  fun systemPromptChanged(value: String) { _uiState.value = _uiState.value.copy(systemPrompt = value, message = null) }
  fun selectSystemPromptPreset(id: String) {
    if (id == CUSTOM_SYSTEM_PROMPT_ID) {
      _uiState.value = _uiState.value.copy(systemPromptPresetId = id, message = null)
    } else {
      val preset = settings.presets().firstOrNull { it.id == id } ?: return
      settings.lastPresetId = id
      _uiState.value = _uiState.value.copy(systemPromptPresetId = id, systemPrompt = preset.body, presets = settings.presets(), message = null)
    }
  }
  fun searchChanged(value: String) { _uiState.value = _uiState.value.copy(search = value) }
  fun localOnlyChanged(value: Boolean) { _uiState.value = _uiState.value.copy(localOnly = value) }
  fun maxLocalModelSizeChanged(value: String) { _uiState.value = _uiState.value.copy(maxLocalModelSizeGb = value); value.toFloatOrNull()?.takeIf { it >= 0f }?.let { settings.maxLocalModelSizeGb = it } }
  fun maxTokensChanged(value: String) { _uiState.value = _uiState.value.copy(maxTokens = value) }
  fun temperatureChanged(value: String) { _uiState.value = _uiState.value.copy(temperature = value) }
  fun selectModel(name: String) { _uiState.value = _uiState.value.copy(selectedModel = name, downloadModel = name, message = null) }
  fun sourceChanged(source: ModelSource) { settings.modelSource = source.name; _uiState.value = _uiState.value.copy(source = source, search = "", localOnly = false, message = null) }
  fun apiKeyVisibleChanged(value: Boolean) { _uiState.value = _uiState.value.copy(apiKeyVisible = value) }
  fun savePreset(name: String, body: String, id: String = java.util.UUID.randomUUID().toString()) { settings.savePreset(SystemPromptPreset(id, name, body)); _uiState.value = _uiState.value.copy(presets = settings.presets()) }
  fun deletePreset(id: String) { settings.deletePreset(id); _uiState.value = _uiState.value.copy(presets = settings.presets()) }

  fun testConnection() { runRequest("接続テストに失敗しました。モデル・APIキー・ネットワークを確認してください。") {
    val model = _uiState.value.selectedModel.trim()
    require(model.isNotBlank()) { "先にモデルを選択してください" }
    DefaultInferenceRepository.generateText(getApplication(), GenerateRequest(if (localModels.fileFor(model).isFile) Backend.LOCAL else Backend.OLLAMA, model, "接続テスト", _uiState.value.systemPrompt, _uiState.value.maxTokens.toIntOrNull() ?: 256, _uiState.value.temperature.toFloatOrNull() ?: 0.7f))
    "接続成功（選択モデルで応答を確認しました）"
  } }
  fun downloadModel(name: String) { runRequest("モデルの取得に失敗しました。HTTP応答・保存先・空き容量を確認してください。") {
    val maxBytes = settings.maxLocalModelSizeGb.toDouble().times(1_000_000_000.0).toLong()
    val model = _uiState.value.models.firstOrNull { it.name == name } ?: error("モデルが一覧にありません")
    require(model.sizeBytes <= 0L || model.sizeBytes <= maxBytes) {
      "上限超過です（%.2fGB）。ローカル上限を上げてください".format(model.sizeBytes / 1_000_000_000.0)
    }
    if (model.source == ModelSource.HUGGING_FACE) {
      registry.downloadFromUrl(model.downloadUrl, model.name) { downloaded, total ->
        _uiState.value = _uiState.value.copy(downloadedBytes = downloaded, downloadTotalBytes = total)
      }
    } else {
      val metadata = registry.metadata(model.name)
      require(metadata.sizeBytes <= 0L || metadata.sizeBytes <= maxBytes) {
        "上限超過です（%.2fGB）。ローカル上限を上げてください".format(metadata.sizeBytes / 1_000_000_000.0)
      }
      registry.download(model.name)
    }
    loadModelsInternal(); "Androidへモデルを保存しました"
  } }
  fun loadModels() { runRequest("モデル一覧の取得に失敗しました。APIキーとネットワークを確認してください。") { loadModelsInternal(); "モデル一覧を更新しました" } }
  fun deleteModel(name: String) { runRequest("モデルの削除に失敗しました。") { localModels.fileFor(name).delete(); loadModelsInternal(); "削除しました: $name" } }
  fun runTest() {
    runRequest("動作検証に失敗しました。モデル・APIキー・ネットワークを確認してください。") {
      val model = _uiState.value.selectedModel.trim()
      val prompt = _uiState.value.testPrompt.trim()
      require(model.isNotBlank()) { "テストするモデル名を入力してください" }
      require(prompt.isNotBlank()) { "テスト用プロンプトを入力してください" }
      val result = DefaultInferenceRepository.generateText(getApplication(), GenerateRequest(if (localModels.fileFor(model).isFile) Backend.LOCAL else Backend.OLLAMA, model, prompt, _uiState.value.systemPrompt, _uiState.value.maxTokens.toIntOrNull() ?: 256, _uiState.value.temperature.toFloatOrNull() ?: 0.7f))
      "テスト結果:\n$result"
    }
  }

  private suspend fun loadModelsInternal() {
    val local = localModels.directory.listFiles()
      ?.filter { it.extension == "gguf" }
      ?.map { com.example.ollamataskerbridge.data.OllamaModel(it.nameWithoutExtension, false, true, it.length(), true) }
      .orEmpty()
    val localByName = local.associateBy { it.name }
    val ollama = runCatching { (client().listModels() + registry.catalog()).distinctBy { it.name } }.getOrDefault(emptyList()).map { item ->
      val registryInfo = runCatching { registry.metadata(item.name) }.getOrNull()
      item.copy(
        local = localByName[item.name] != null,
        downloadable = registryInfo?.downloadable ?: item.downloadable,
        sizeBytes = localByName[item.name]?.sizeBytes ?: registryInfo?.sizeBytes?.takeIf { it > 0 } ?: item.sizeBytes,
      )
    }
    val huggingFaceModels = huggingFace.catalog().map { item ->
      item.copy(local = localByName[item.name] != null, sizeBytes = localByName[item.name]?.sizeBytes ?: item.sizeBytes)
    }
    val huggingFaceNames = huggingFaceModels.map { it.name }.toSet()
    // 同名モデルは取得元タブを混在させず、HFカタログを優先する。
    val remote = ollama.filterNot { it.name in huggingFaceNames } + huggingFaceModels
    val merged = (remote + local.filter { item -> remote.none { it.name == item.name } })
    settings.saveCachedModels(merged)
    _uiState.value = _uiState.value.copy(models = merged)
  }

  private fun isLocalHost(host: String): Boolean {
    val normalized = host.lowercase()
    if (normalized == "ollama.com" || normalized.endsWith(".ollama.com")) return true
    if (normalized == "localhost" || normalized.endsWith(".local") || normalized.endsWith(".ts.net")) return true
    val octets = normalized.split(".").mapNotNull { it.toIntOrNull() }
    if (octets.size != 4) return normalized == "::1" || normalized.startsWith("fc") || normalized.startsWith("fd")
    return octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 100 && octets[1] in 64..127) || octets[0] == 127
  }

  private fun client(): OllamaClient {
    val value = _uiState.value.endpoint.trim().removeSuffix("/")
    val uri = URI(value)
    require(uri.scheme == "http" || uri.scheme == "https") { "URLはhttpまたはhttpsで入力してください" }
    require(!uri.host.isNullOrBlank()) { "URLのホストが必要です" }
    require(isLocalHost(uri.host!!)) { "安全のためローカル接続先のみ許可しています" }
    settings.endpoint = value
    settings.apiKey = _uiState.value.apiKey
    return OllamaClient(value, _uiState.value.apiKey)
  }

  private fun runRequest(failureMessage: String, action: suspend () -> String) {
    _uiState.value = _uiState.value.copy(loading = true, message = null, downloadedBytes = 0L, downloadTotalBytes = 0L)
    viewModelScope.launch {
      runCatching { withContext(Dispatchers.IO) { action() } }
        .onSuccess { message -> _uiState.value = _uiState.value.copy(loading = false, message = message, downloadedBytes = 0L, downloadTotalBytes = 0L) }
        .onFailure { error ->
          val raw = error.message.orEmpty()
          val isModelDownload = failureMessage.startsWith("モデルの取得")
          val detail = when {
            failureMessage.startsWith("モデルの取得") && raw.matches(Regex(".*(?:HTTP \\d{3}|HTTP応答|Hugging Face HTTP|モデルBlob HTTP|Registry HTTP|Ollama HTTP).*")) -> "HTTP応答エラーです。サーバーの応答を確認してください。 ($raw)"
            failureMessage.startsWith("モデルの取得") && raw.matches(Regex(".*(?:保存|rename|ファイル|Permission denied|EACCES).*")) -> "保存先エラーです。アプリの保存領域への書き込みを確認してください。 ($raw)"
            failureMessage.startsWith("モデルの取得") && raw.matches(Regex(".*(?:空き容量|容量|No space|ENOSPC|上限超過).*")) -> "空き容量エラーです。モデル上限または端末ストレージを確認してください。 ($raw)"
            isModelDownload -> "ダウンロードエラーです。ネットワークまたはモデルURLを確認してください。 ($raw)"
            raw.contains("HTTP 402") -> "Cloud APIの利用枠がありません（HTTP 402）。Ollamaのプランまたは追加利用を確認してください。"
            error::class.simpleName?.contains("UnsupportedArchitecture") == true -> "このモデル形式は端末のローカル推論エンジンに未対応です。対応モデルを選択してください。"
            raw.contains("HTTP 401") -> "認証に失敗しました（HTTP 401）。APIキーを確認してください。"
            raw.contains("HTTP 403") -> "アクセスが拒否されました（HTTP 403）。APIキーの権限またはモデルの利用権限を確認してください。"
            raw.contains("timeout", true) || raw.contains("timed out", true) -> "接続がタイムアウトしました。ネットワークを確認してください。"
            else -> failureMessage + (raw.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
          }
          _uiState.value = _uiState.value.copy(loading = false, message = "エラー: $detail", downloadedBytes = 0L, downloadTotalBytes = 0L)
        }
    }
  }
}

data class MainScreenUiState(
  val endpoint: String,
  val apiKey: String = "",
  val downloadModel: String = "",
  val selectedModel: String = "",
  val search: String = "",
  val localOnly: Boolean = false,
  val maxLocalModelSizeGb: String = "15.0",
  val systemPrompt: String = "",
  val systemPromptPresetId: String = "",
  val apiKeyVisible: Boolean = false,
  val maxTokens: String = "256",
  val temperature: String = "0.7",
  val testPrompt: String = "Tasker連携テストです。成功したら『テスト成功』とだけ返してください。",
  val source: ModelSource = ModelSource.OLLAMA,
  val models: List<com.example.ollamataskerbridge.data.OllamaModel> = emptyList(),
  val presets: List<SystemPromptPreset> = emptyList(),
  val loading: Boolean = false,
  val downloadedBytes: Long = 0L,
  val downloadTotalBytes: Long = 0L,
  val message: String? = null,
)

const val CUSTOM_SYSTEM_PROMPT_ID = "__custom__"
