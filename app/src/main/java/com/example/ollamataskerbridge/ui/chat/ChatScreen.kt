package com.example.ollamataskerbridge.ui.chat

import android.app.Application
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ollamataskerbridge.bridge.*
import com.example.ollamataskerbridge.data.*
import com.example.ollamataskerbridge.diagnostics.DiagnosticsLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

data class ChatMessage(val user: Boolean, val text: String, val model: String = "", val generating: Boolean = false, val error: Boolean = false, val retryPrompt: String = "", val elapsedMs: Long = 0L, val inputTokens: Int = 0, val outputTokens: Int = 0, val tokenCountEstimated: Boolean = false)
data class ChatUiState(val messages: List<ChatMessage> = emptyList(), val selectedModel: String = "", val maxTokens: String = "1024", val temperature: String = "0.7", val systemPromptId: String = "", val systemPrompt: String = "", val presets: List<SystemPromptPreset> = emptyList(), val generating: Boolean = false, val input: String = "", val notice: String? = null, val imageBytes: ByteArray? = null, val imageName: String = "")

class ChatViewModel(application: Application) : AndroidViewModel(application) {
  private val settings = SettingsStore(application)
  private val store = LocalModelStore(application)
  private var running: Job? = null
  fun selectImage(uri: Uri) {
    val resolver = getApplication<Application>().contentResolver
    runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("画像を読み込めません") }
      .onSuccess { bytes ->
        when {
          bytes.size > 20 * 1024 * 1024 -> _state.value = _state.value.copy(notice = "画像が大きすぎます（20MB以下にしてください）")
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null -> _state.value = _state.value.copy(notice = "対応していない画像形式です。PNGまたはJPEGを選択してください。")
          else -> _state.value = _state.value.copy(imageBytes = bytes, imageName = uri.lastPathSegment ?: "image", notice = null)
        }
      }
      .onFailure { _state.value = _state.value.copy(notice = "画像を読み込めませんでした: " + it.message) }
  }
  fun clearImage() { _state.value = _state.value.copy(imageBytes = null, imageName = "", notice = null) }
  private fun localModels(): List<OllamaModel> = store.directory.listFiles().orEmpty().filter { it.extension == "gguf" || it.extension == "litertlm" }.map { file -> OllamaModel(file.nameWithoutExtension, false, true, file.length(), true, if (file.extension == "litertlm") ModelSource.LITERT_LM else ModelSource.HUGGING_FACE) }
  private fun availableModels(): List<OllamaModel> {
    val local = localModels()
    val localNames = local.map { it.name }.toSet()
    return (settings.cachedModels() + local).distinctBy { it.name }.filter { it.local || it.enabled || it.name in localNames }.map { it.copy(local = it.local || it.name in localNames) }
  }
  private val _state = MutableStateFlow(ChatUiState(selectedModel = (availableModels().firstOrNull { it.local } ?: availableModels().firstOrNull())?.name.orEmpty(), presets = settings.presets(), systemPromptId = settings.lastPresetId, systemPrompt = settings.presets().firstOrNull { it.id == settings.lastPresetId }?.body.orEmpty()))
  val state = _state.asStateFlow()
  fun models() = availableModels()
  fun refreshPresets() { _state.value = _state.value.copy(presets = settings.presets()) }
  private fun Boolean?.orFalse() = this == true
  fun input(value: String) { _state.value = _state.value.copy(input = value) }
  fun selectModel(value: String) { _state.value = _state.value.copy(selectedModel = value) }
  fun maxTokens(value: String) { _state.value = _state.value.copy(maxTokens = value) }
  fun temperature(value: String) { _state.value = _state.value.copy(temperature = value) }
  fun selectPreset(value: SystemPromptPreset?) {
    val old = _state.value
    val changed = old.systemPrompt != value?.body.orEmpty()
    _state.value = old.copy(systemPromptId = value?.id.orEmpty(), systemPrompt = value?.body.orEmpty(), messages = if (changed && old.messages.isNotEmpty()) emptyList() else old.messages, notice = if (changed && old.messages.isNotEmpty()) "システムプロンプトを変更したため、会話をリセットしました。" else old.notice)
  }
  fun stop() { running?.cancel(); _state.value = _state.value.copy(generating = false) }
  fun retry(prompt: String) = send(prompt)
  fun send(value: String = _state.value.input) {
    val prompt = value.trim(); val old = _state.value
    if (prompt.isBlank() || old.generating || old.selectedModel.isBlank()) return
    val selectedModel = availableModels().firstOrNull { it.name == old.selectedModel }
    if (old.imageBytes != null && selectedModel?.supportsVision() != true) {
      _state.value = old.copy(notice = "このモデルは画像認識に対応していません。👁️付きモデルを選択してください。")
      return
    }
    val index = old.messages.size + 1
    _state.value = old.copy(input = "", imageBytes = null, imageName = "", generating = true, messages = old.messages + ChatMessage(true, prompt) + ChatMessage(false, "", old.selectedModel, true, retryPrompt = prompt))
    val backend = if (selectedModel?.source == ModelSource.OLLAMA) Backend.OLLAMA else Backend.LOCAL
    val request = GenerateRequest(backend, old.selectedModel, prompt, old.systemPrompt.takeIf { it.isNotBlank() }, old.maxTokens.toIntOrNull()?.coerceAtLeast(1) ?: 1024, old.temperature.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f, old.imageBytes)
    val startedAt = SystemClock.elapsedRealtime()
    DiagnosticsLog.note("テストチャット生成開始: backend=" + request.backend + " model=" + request.model + " maxTokens=" + request.maxTokens + " temperature=" + request.temperature + " image=" + (request.imageBytes != null) + " promptChars=" + request.prompt.length)
    DiagnosticsLog.note("テストチャットプロンプト: " + request.prompt)
    running = viewModelScope.launch {
      try { DefaultInferenceRepository.generate(getApplication(), request).collect { event -> when (event) { is GenerateEvent.Token -> update(index, ChatMessage(false, _state.value.messages.getOrNull(index)?.text.orEmpty() + event.text, request.model, true, retryPrompt = prompt)); is GenerateEvent.Done -> { val elapsed = SystemClock.elapsedRealtime() - startedAt; val exact = event.inputTokens > 0 || event.outputTokens > 0; val inputTokens = if (exact) event.inputTokens else estimateTokens(prompt); val outputTokens = if (exact) event.outputTokens else estimateTokens(event.fullText); DiagnosticsLog.note("テストチャット応答: " + event.fullText); update(index, ChatMessage(false, event.fullText, request.model, false, retryPrompt = prompt, elapsedMs = elapsed, inputTokens = inputTokens, outputTokens = outputTokens, tokenCountEstimated = !exact)) }; is GenerateEvent.Error -> { DiagnosticsLog.error(event.message); update(index, ChatMessage(false, event.message, request.model, false, true, prompt)) } } } }
      catch (e: CancellationException) { DiagnosticsLog.warn("生成を中断しました"); update(index, ChatMessage(false, "生成を中断しました", request.model, error = true, retryPrompt = prompt)) }
      catch (e: Exception) { DiagnosticsLog.error(e.message ?: "生成に失敗しました"); update(index, ChatMessage(false, "生成に失敗しました: ${e.message ?: "モデルを確認してください"}", request.model, error = true, retryPrompt = prompt)) }
      finally { DiagnosticsLog.note("テストチャット生成終了: model=" + request.model); _state.value = _state.value.copy(generating = false) }
    }
  }
  private fun estimateTokens(text: String): Int = (text.codePointCount(0, text.length) / 2).coerceAtLeast(1)
  private fun update(index: Int, message: ChatMessage) { _state.value = _state.value.copy(messages = _state.value.messages.toMutableList().also { if (index in it.indices) it[index] = message }) }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel(), modifier: Modifier = Modifier, onOpenDrawer: () -> Unit = {}) {
  val state by viewModel.state.collectAsStateWithLifecycle(); val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(viewModel::selectImage) }; val list = rememberLazyListState()
  var menu by remember { mutableStateOf(false) }; var models by remember { mutableStateOf(false) }; var tokens by remember { mutableStateOf(false) }; var temp by remember { mutableStateOf(false) }; var prompts by remember { mutableStateOf(false) }
  LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) { if (state.messages.isNotEmpty()) { delay(80); list.scrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) } }
  Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onOpenDrawer) { Text("☰") }; Text("テストチャット", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.weight(1f)); Text(state.selectedModel.ifBlank { "モデル未選択" }, style = MaterialTheme.typography.labelSmall) }
    state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp)) }
    state.imageBytes?.let { bytes ->
      val preview = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
      preview?.let { bitmap ->
        Box(Modifier.padding(bottom = 6.dp)) {
          androidx.compose.foundation.Image(bitmap, contentDescription = "選択した画像", modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)))
          IconButton(
            onClick = viewModel::clearImage,
            modifier = Modifier.size(24.dp).align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface, CircleShape)
          ) { Text("×", style = MaterialTheme.typography.labelSmall) }
        }
      }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) { items(state.messages) { MessageBubble(it, { viewModel.retry(it.retryPrompt) }) } }
    Box(Modifier.fillMaxWidth()) {
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem({ Text("画像を選択") }, { menu = false; imageLauncher.launch("image/*") })
        DropdownMenuItem({ Text("モデルを選択  ${state.selectedModel.ifBlank { "未選択" }}") }, { menu = false; models = true })
        DropdownMenuItem({ Text("最大トークン数  ${state.maxTokens}") }, { menu = false; tokens = true })
        DropdownMenuItem({ Text("Temperature  ${state.temperature}") }, { menu = false; temp = true })
        DropdownMenuItem({ Text("システムプロンプト  ${state.presets.firstOrNull { it.id == state.systemPromptId }?.name ?: "なし"}") }, { menu = false; viewModel.refreshPresets(); prompts = true })
      }
      Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        IconButton({ menu = true }) { Text("＋", style = MaterialTheme.typography.headlineSmall) }
        OutlinedTextField(state.input, viewModel::input, Modifier.weight(1f), placeholder = { Text("メッセージを入力") }, maxLines = 4, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
        Spacer(Modifier.width(4.dp)); IconButton({ if (state.generating) viewModel.stop() else viewModel.send() }, enabled = state.generating || state.input.isNotBlank(), modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) { Text(if (state.generating) "■" else "➤", color = MaterialTheme.colorScheme.onPrimary) }
      }
    }
  }
  if (models) ModelPickerDialog(viewModel, { models = false })
  if (tokens) NumberDialog("最大トークン数", state.maxTokens, { viewModel.maxTokens(it); tokens = false }, { tokens = false })
  if (temp) NumberDialog("Temperature", state.temperature, { viewModel.temperature(it); temp = false }, { temp = false })
  if (prompts) AlertDialog(onDismissRequest = { prompts = false }, title = { Text("システムプロンプト") }, text = { Column { OutlinedButton({ viewModel.selectPreset(null); prompts = false }, Modifier.fillMaxWidth()) { Text("なし") }; state.presets.forEach { item -> OutlinedButton({ viewModel.selectPreset(item); prompts = false }, Modifier.fillMaxWidth()) { Text(item.name) } } } }, confirmButton = { Button({ prompts = false }) { Text("閉じる") } })
}

@Composable private fun ModelPickerDialog(viewModel: ChatViewModel, dismiss: () -> Unit) {
  var showCloud by remember { mutableStateOf(false) }
  val candidates = viewModel.models().filter { it.local || (showCloud && it.source == ModelSource.OLLAMA && !it.local) }
  AlertDialog(onDismissRequest = dismiss, title = { Text("モデルを選択") }, text = { Column { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(showCloud, { showCloud = it }); Text("Cloudモデルを表示") }; if (candidates.isEmpty()) Text(if (showCloud) "選択できるモデルがありません" else "ダウンロード済みモデルがありません") else candidates.forEach { item -> OutlinedButton({ viewModel.selectModel(item.name); dismiss() }, Modifier.fillMaxWidth().padding(2.dp)) { Text(buildString { append(item.name); if (item.remote && !item.local) append(" ☁"); if (item.supportsVision()) append(" 👁️") }) } } } }, confirmButton = { Button(dismiss) { Text("閉じる") } })
}

@Composable private fun NumberDialog(title: String, value: String, save: (String) -> Unit, dismiss: () -> Unit) { var input by remember(value) { mutableStateOf(value) }; AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(input, { input = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }, confirmButton = { Button({ save(input) }) { Text("保存") } }, dismissButton = { OutlinedButton(dismiss) { Text("キャンセル") } }) }
@Composable private fun MessageBubble(message: ChatMessage, retry: () -> Unit) {
  val clipboard = LocalClipboardManager.current; val context = LocalContext.current; val copyText = { if (message.text.isNotBlank() && !message.generating) { clipboard.setText(AnnotatedString(message.text)); Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show() } }
  if (message.user) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Text(message.text, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).combinedClickable(onClick = {}, onLongClick = copyText).padding(12.dp)) }
  else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Surface(Modifier.size(34.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(contentAlignment = Alignment.Center) { Text("AI") } }; Column(Modifier.padding(start = 8.dp).weight(1f)) { Text(message.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Card(shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp), border = if (message.error) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null) { Text(if (message.generating) "•••" else message.text, color = if (message.error) MaterialTheme.colorScheme.error else Color.Unspecified, modifier = Modifier.combinedClickable(onClick = {}, onLongClick = copyText).padding(12.dp)) }; if (!message.generating && !message.error && message.elapsedMs > 0L) Text("${if (message.tokenCountEstimated) "推定 " else ""}入力 ${message.inputTokens}・出力 ${message.outputTokens}・合計 ${message.inputTokens + message.outputTokens} tokens・${message.elapsedMs.div(1000f)}秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (message.error) OutlinedButton(retry) { Text("再試行") } } }
}
