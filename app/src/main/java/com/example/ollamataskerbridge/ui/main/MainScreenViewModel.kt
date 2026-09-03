package com.example.ollamataskerbridge.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.SettingsStore
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
  private val settings = SettingsStore(application)
  private val _uiState = MutableStateFlow(MainScreenUiState(endpoint = settings.endpoint))
  val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

  fun endpointChanged(value: String) { _uiState.value = _uiState.value.copy(endpoint = value, message = null) }

  fun testConnection() { runRequest { client().ping(); "接続成功" } }
  fun loadModels() { runRequest { client().listModels().also { models -> _uiState.value = _uiState.value.copy(models = models) }; "モデル一覧を更新しました" } }
  fun deleteModel(name: String) { runRequest { client().deleteModel(name); _uiState.value = _uiState.value.copy(models = _uiState.value.models - name); "削除しました: $name" } }

  private fun client(): OllamaClient {
    val value = _uiState.value.endpoint.trim().removeSuffix("/")
    val uri = URI(value)
    require(uri.scheme == "http" || uri.scheme == "https") { "URLはhttpまたはhttpsで入力してください" }
    require(!uri.host.isNullOrBlank()) { "URLのホストが必要です" }
    settings.endpoint = value
    return OllamaClient(value)
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
  val models: List<String> = emptyList(),
  val loading: Boolean = false,
  val message: String? = null,
)
