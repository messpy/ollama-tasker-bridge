package com.example.ollamataskerbridge.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaRegistryClient
import com.example.ollamataskerbridge.data.SettingsStore
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
  private val settings = SettingsStore(application)
  private val localModels = LocalModelStore(application)
  private val registry = OllamaRegistryClient(localModels)
  private val _uiState = MutableStateFlow(MainScreenUiState(endpoint = settings.endpoint, apiKey = settings.apiKey))
  val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

  fun endpointChanged(value: String) { _uiState.value = _uiState.value.copy(endpoint = value, message = null) }
  fun apiKeyChanged(value: String) { _uiState.value = _uiState.value.copy(apiKey = value, message = null) }
  fun downloadModelChanged(value: String) { _uiState.value = _uiState.value.copy(downloadModel = value, message = null) }
  fun testPromptChanged(value: String) { _uiState.value = _uiState.value.copy(testPrompt = value, message = null) }

  fun testConnection() { runRequest { client().ping(); "接続成功" } }
  fun downloadModel(name: String) { runRequest { registry.download(name); loadModelsInternal(); "Androidへモデルを保存しました" } }
  fun loadModels() { runRequest { loadModelsInternal(); "OllamaとAndroid内のモデル一覧を更新しました" } }
  fun deleteModel(name: String) { runRequest { localModels.fileFor(name).delete(); loadModelsInternal(); "削除しました: $name" } }
  fun runTest() {
    runRequest {
      val model = _uiState.value.downloadModel.trim()
      val prompt = _uiState.value.testPrompt.trim()
      require(model.isNotBlank()) { "テストするモデル名を入力してください" }
      require(prompt.isNotBlank()) { "テスト用プロンプトを入力してください" }
      val result = if (localModels.fileFor(model).isFile) {
        com.example.ollamataskerbridge.bridge.LocalInferenceBridge.generate(getApplication(), model, prompt, null)
      } else {
        client().generate(model, prompt)
      }
      "テスト結果:\n$result"
    }
  }

  private suspend fun loadModelsInternal() {
    val local = localModels.directory.listFiles()
      ?.filter { it.extension == "gguf" }
      ?.map { com.example.ollamataskerbridge.data.OllamaModel(it.nameWithoutExtension, false, true) }
      .orEmpty()
    val remote = client().listModels().map { com.example.ollamataskerbridge.data.OllamaModel(it.name, true, it.downloadable) }
    _uiState.value = _uiState.value.copy(models = remote + local)
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

  private fun runRequest(action: suspend () -> String) {
    _uiState.value = _uiState.value.copy(loading = true, message = null)
    viewModelScope.launch {
      runCatching { action() }
        .onSuccess { message -> _uiState.value = _uiState.value.copy(loading = false, message = message) }
        .onFailure { error -> _uiState.value = _uiState.value.copy(loading = false, message = "エラー: ${error.message ?: "接続に失敗しました"}") }
    }
  }
}

data class MainScreenUiState(
  val endpoint: String,
  val apiKey: String = "",
  val downloadModel: String = "",
  val testPrompt: String = "Tasker連携テストです。成功したら『テスト成功』とだけ返してください。",
  val models: List<com.example.ollamataskerbridge.data.OllamaModel> = emptyList(),
  val loading: Boolean = false,
  val message: String? = null,
)
