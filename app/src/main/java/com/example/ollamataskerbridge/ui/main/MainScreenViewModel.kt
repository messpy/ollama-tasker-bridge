package com.example.ollamataskerbridge.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaRegistryClient
import com.example.ollamataskerbridge.data.SettingsStore
import com.example.ollamataskerbridge.data.SystemPromptPreset
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
  private val settings = SettingsStore(application)
  private val localModels = LocalModelStore(application)
  private val registry = OllamaRegistryClient(localModels)
  private val _uiState = MutableStateFlow(MainScreenUiState(endpoint = settings.endpoint, apiKey = settings.apiKey, presets = settings.presets()))
  val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

  fun endpointChanged(value: String) { _uiState.value = _uiState.value.copy(endpoint = value, message = null) }
  fun apiKeyChanged(value: String) { _uiState.value = _uiState.value.copy(apiKey = value, message = null) }
  fun downloadModelChanged(value: String) { _uiState.value = _uiState.value.copy(downloadModel = value, message = null) }
  fun testPromptChanged(value: String) { _uiState.value = _uiState.value.copy(testPrompt = value, message = null) }
  fun systemPromptChanged(value: String) { _uiState.value = _uiState.value.copy(systemPrompt = value, message = null) }
  fun searchChanged(value: String) { _uiState.value = _uiState.value.copy(search = value) }
  fun localOnlyChanged(value: Boolean) { _uiState.value = _uiState.value.copy(localOnly = value) }
  fun selectModel(name: String) { _uiState.value = _uiState.value.copy(selectedModel = name, downloadModel = name, message = null) }
  fun apiKeyVisibleChanged(value: Boolean) { _uiState.value = _uiState.value.copy(apiKeyVisible = value) }
  fun savePreset(name: String, body: String, id: String = java.util.UUID.randomUUID().toString()) { settings.savePreset(SystemPromptPreset(id, name, body)); _uiState.value = _uiState.value.copy(presets = settings.presets()) }
  fun deletePreset(id: String) { settings.deletePreset(id); _uiState.value = _uiState.value.copy(presets = settings.presets()) }

  fun testConnection() { runRequest("接続テストに失敗しました。APIキーとネットワークを確認してください。") { val model = _uiState.value.selectedModel; require(model.isNotBlank()) { "先にモデルを選択してください" }; if (localModels.fileFor(model).isFile) com.example.ollamataskerbridge.bridge.LocalInferenceBridge.generate(getApplication(), model, _uiState.value.testPrompt, _uiState.value.systemPrompt) else client().generate(model, _uiState.value.testPrompt, _uiState.value.systemPrompt); "接続成功（実際の応答を確認しました）" } }
  fun downloadModel(name: String) { runRequest("モデルの取得に失敗しました。ストレージの空き容量とネットワークを確認してください。") { registry.download(name); loadModelsInternal(); "Androidへモデルを保存しました" } }
  fun loadModels() { runRequest("モデル一覧の取得に失敗しました。APIキーとネットワークを確認してください。") { loadModelsInternal(); "モデル一覧を更新しました" } }
  fun deleteModel(name: String) { runRequest("モデルの削除に失敗しました。") { localModels.fileFor(name).delete(); loadModelsInternal(); "削除しました: $name" } }
  fun runTest() {
    runRequest("動作検証に失敗しました。モデル・APIキー・ネットワークを確認してください。") {
      val model = _uiState.value.selectedModel.trim()
      val prompt = _uiState.value.testPrompt.trim()
      require(model.isNotBlank()) { "テストするモデル名を入力してください" }
      require(prompt.isNotBlank()) { "テスト用プロンプトを入力してください" }
      val result = if (localModels.fileFor(model).isFile) {
        com.example.ollamataskerbridge.bridge.LocalInferenceBridge.generate(getApplication(), model, prompt, _uiState.value.systemPrompt)
      } else {
        client().generate(model, prompt, _uiState.value.systemPrompt)
      }
      "テスト結果:\n$result"
    }
  }

  private suspend fun loadModelsInternal() {
    val local = localModels.directory.listFiles()
      ?.filter { it.extension == "gguf" }
      ?.map { com.example.ollamataskerbridge.data.OllamaModel(it.nameWithoutExtension, false, true, it.length(), true) }
      .orEmpty()
    val localByName = local.associateBy { it.name }
    val remote = client().listModels().map { item ->
      val registryInfo = runCatching { registry.metadata(item.name) }.getOrNull()
      item.copy(
        local = localByName[item.name] != null,
        downloadable = registryInfo?.downloadable ?: item.downloadable,
        sizeBytes = localByName[item.name]?.sizeBytes ?: registryInfo?.sizeBytes?.takeIf { it > 0 } ?: item.sizeBytes,
      )
    }
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
    _uiState.value = _uiState.value.copy(loading = true, message = null)
    viewModelScope.launch {
      runCatching { action() }
        .onSuccess { message -> _uiState.value = _uiState.value.copy(loading = false, message = message) }
        .onFailure { error -> val detail = if (error.message?.contains("timeout", true) == true) "接続がタイムアウトしました。ネットワークを確認してください。" else failureMessage + (error.message?.let { " ($it)" } ?: ""); _uiState.value = _uiState.value.copy(loading = false, message = "エラー: $detail") }
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
  val systemPrompt: String = "",
  val apiKeyVisible: Boolean = false,
  val testPrompt: String = "Tasker連携テストです。成功したら『テスト成功』とだけ返してください。",
  val models: List<com.example.ollamataskerbridge.data.OllamaModel> = emptyList(),
  val presets: List<SystemPromptPreset> = emptyList(),
  val loading: Boolean = false,
  val message: String? = null,
)
