package com.example.ollamataskerbridge.ui.chat

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ollamataskerbridge.bridge.*
import com.example.ollamataskerbridge.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val user: Boolean, val text: String, val model: String = "", val generating: Boolean = false, val error: Boolean = false, val retryPrompt: String = "")
data class ChatUiState(val messages: List<ChatMessage> = emptyList(), val selectedModel: String = "", val maxTokens: String = "256", val temperature: String = "0.7", val systemPromptId: String = "", val systemPrompt: String = "", val presets: List<SystemPromptPreset> = emptyList(), val generating: Boolean = false, val input: String = "")

class ChatViewModel(application: Application) : AndroidViewModel(application) {
  private val settings = SettingsStore(application)
  private val store = LocalModelStore(application)
  private var running: Job? = null
  private fun localModels() = store.directory.listFiles().orEmpty().filter { it.extension == "gguf" }.map { it.nameWithoutExtension }
  private val _state = MutableStateFlow(ChatUiState(selectedModel = localModels().firstOrNull().orEmpty(), presets = settings.presets(), systemPromptId = settings.lastPresetId, systemPrompt = settings.presets().firstOrNull { it.id == settings.lastPresetId }?.body.orEmpty()))
  val state = _state.asStateFlow()
  fun models() = localModels()
  fun input(value: String) { _state.value = _state.value.copy(input = value) }
  fun selectModel(value: String) { _state.value = _state.value.copy(selectedModel = value) }
  fun maxTokens(value: String) { _state.value = _state.value.copy(maxTokens = value) }
  fun temperature(value: String) { _state.value = _state.value.copy(temperature = value) }
  fun selectPreset(value: SystemPromptPreset?) { _state.value = _state.value.copy(systemPromptId = value?.id.orEmpty(), systemPrompt = value?.body.orEmpty()) }
  fun stop() { running?.cancel(); _state.value = _state.value.copy(generating = false) }
  fun retry(prompt: String) = send(prompt)
  fun send(value: String = _state.value.input) {
    val prompt = value.trim(); val old = _state.value
    if (prompt.isBlank() || old.generating || old.selectedModel.isBlank()) return
    val index = old.messages.size + 1
    _state.value = old.copy(input = "", generating = true, messages = old.messages + ChatMessage(true, prompt) + ChatMessage(false, "", old.selectedModel, true, retryPrompt = prompt))
    val request = GenerateRequest(Backend.LOCAL, old.selectedModel, prompt, old.systemPrompt.takeIf { it.isNotBlank() }, old.maxTokens.toIntOrNull()?.coerceAtLeast(1) ?: 256, old.temperature.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f)
    running = viewModelScope.launch {
      try { update(index, ChatMessage(false, DefaultInferenceRepository.generate(getApplication(), request), request.model)) }
      catch (e: CancellationException) { update(index, ChatMessage(false, "生成を中断しました", request.model, error = true, retryPrompt = prompt)) }
      catch (e: Exception) { update(index, ChatMessage(false, "生成に失敗しました: ${e.message ?: "モデルを確認してください"}", request.model, error = true, retryPrompt = prompt)) }
      finally { _state.value = _state.value.copy(generating = false) }
    }
  }
  private fun update(index: Int, message: ChatMessage) { _state.value = _state.value.copy(messages = _state.value.messages.toMutableList().also { if (index in it.indices) it[index] = message }) }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel(), modifier: Modifier = Modifier) {
  val state by viewModel.state.collectAsStateWithLifecycle(); val list = rememberLazyListState()
  var menu by remember { mutableStateOf(false) }; var models by remember { mutableStateOf(false) }; var tokens by remember { mutableStateOf(false) }; var temp by remember { mutableStateOf(false) }; var prompts by remember { mutableStateOf(false) }
  LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) { if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex) }
  Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("テストチャット", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.weight(1f)); Text(state.selectedModel.ifBlank { "モデル未選択" }, style = MaterialTheme.typography.labelSmall) }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) { items(state.messages) { MessageBubble(it, { viewModel.retry(it.retryPrompt) }) } }
    Box(Modifier.fillMaxWidth()) {
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem({ Text("モデルを選択  ${state.selectedModel.ifBlank { "未選択" }}") }, { menu = false; models = true })
        DropdownMenuItem({ Text("最大トークン数  ${state.maxTokens}") }, { menu = false; tokens = true })
        DropdownMenuItem({ Text("Temperature  ${state.temperature}") }, { menu = false; temp = true })
        DropdownMenuItem({ Text("システムプロンプト  ${state.presets.firstOrNull { it.id == state.systemPromptId }?.name ?: "なし"}") }, { menu = false; prompts = true })
      }
      Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        IconButton({ menu = true }) { Text("＋", style = MaterialTheme.typography.headlineSmall) }
        OutlinedTextField(state.input, viewModel::input, Modifier.weight(1f), placeholder = { Text("メッセージを入力") }, maxLines = 4, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
        Spacer(Modifier.width(4.dp)); IconButton({ if (state.generating) viewModel.stop() else viewModel.send() }, enabled = state.generating || state.input.isNotBlank(), modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) { Text(if (state.generating) "■" else "➤", color = MaterialTheme.colorScheme.onPrimary) }
      }
    }
  }
  if (models) AlertDialog(onDismissRequest = { models = false }, title = { Text("ダウンロード済みモデル") }, text = { Column { viewModel.models().forEach { item -> OutlinedButton({ viewModel.selectModel(item); models = false }, Modifier.fillMaxWidth().padding(2.dp)) { Text(item) } } } }, confirmButton = { Button({ models = false }) { Text("閉じる") } })
  if (tokens) NumberDialog("最大トークン数", state.maxTokens, { viewModel.maxTokens(it); tokens = false }, { tokens = false })
  if (temp) NumberDialog("Temperature", state.temperature, { viewModel.temperature(it); temp = false }, { temp = false })
  if (prompts) AlertDialog(onDismissRequest = { prompts = false }, title = { Text("システムプロンプト") }, text = { Column { OutlinedButton({ viewModel.selectPreset(null); prompts = false }, Modifier.fillMaxWidth()) { Text("なし") }; state.presets.forEach { item -> OutlinedButton({ viewModel.selectPreset(item); prompts = false }, Modifier.fillMaxWidth()) { Text(item.name) } } } }, confirmButton = { Button({ prompts = false }) { Text("閉じる") } })
}

@Composable private fun NumberDialog(title: String, value: String, save: (String) -> Unit, dismiss: () -> Unit) { var input by remember(value) { mutableStateOf(value) }; AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(input, { input = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }, confirmButton = { Button({ save(input) }) { Text("保存") } }, dismissButton = { OutlinedButton(dismiss) { Text("キャンセル") } }) }
@Composable private fun MessageBubble(message: ChatMessage, retry: () -> Unit) {
  if (message.user) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Text(message.text, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).padding(12.dp)) }
  else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Surface(Modifier.size(34.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(contentAlignment = Alignment.Center) { Text("AI") } }; Column(Modifier.padding(start = 8.dp).weight(1f)) { Text(message.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Card(shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp), border = if (message.error) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null) { Text(if (message.generating) "•••" else message.text, color = if (message.error) MaterialTheme.colorScheme.error else Color.Unspecified, modifier = Modifier.padding(12.dp)) }; if (message.error) OutlinedButton(retry) { Text("再試行") } } }
}
