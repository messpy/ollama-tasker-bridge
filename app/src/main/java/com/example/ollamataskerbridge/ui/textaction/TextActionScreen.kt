package com.example.ollamataskerbridge.ui.textaction

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ollamataskerbridge.bridge.Backend
import com.example.ollamataskerbridge.bridge.DefaultInferenceRepository
import com.example.ollamataskerbridge.bridge.GenerateRequest
import com.example.ollamataskerbridge.bridge.GenerateEvent
import com.example.ollamataskerbridge.data.*
import com.example.ollamataskerbridge.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TextActionUiState(val actions: List<TextAction> = emptyList(), val selected: TextAction? = null, val result: String = "", val error: String? = null, val running: Boolean = false)

class TextActionViewModel(application: Application) : AndroidViewModel(application) {
  private val store = TextActionStore(application)
  private val settings = SettingsStore(application)
  private val _state = MutableStateFlow(TextActionUiState(actions = store.actions()))
  val state = _state.asStateFlow()
  fun reload() { _state.value = _state.value.copy(actions = store.actions()) }
  fun save(action: TextAction) { store.save(action); reload() }
  fun delete(id: String) { store.delete(id); reload() }
  fun toggle(action: TextAction) = save(action.copy(enabled = !action.enabled))
  fun run(action: TextAction, text: String) {
    val model = action.model.ifBlank { ModelCatalog.available(getApplication()).firstOrNull()?.name.orEmpty() }
    if (model.isBlank()) { _state.value = _state.value.copy(error = "利用可能なモデルがありません。先にモデルを取得または登録してください。"); return }
    val system = settings.presets().firstOrNull { it.id == action.systemPromptPresetId }?.body ?: action.customSystemPrompt
    val backend = when (action.backend.lowercase()) { "local" -> Backend.LOCAL; "ollama" -> Backend.OLLAMA; else -> ModelCatalog.available(getApplication()).firstOrNull { it.name == model }?.let { if (it.local) Backend.LOCAL else Backend.OLLAMA } ?: Backend.LOCAL }
    val prompt = action.promptTemplate.replace("{{text}}", text)
    _state.value = _state.value.copy(selected = action, result = "", error = null, running = true)
    viewModelScope.launch {
      runCatching {
        DefaultInferenceRepository.generate(getApplication(), GenerateRequest(backend, model, prompt, system, action.maxTokens, action.temperature)).collect { event ->
          when (event) { is GenerateEvent.Token -> _state.value = _state.value.copy(result = _state.value.result + event.text); is GenerateEvent.Done -> _state.value = _state.value.copy(result = event.fullText); is GenerateEvent.Error -> error(event.message) }
        }
      }.onFailure { _state.value = _state.value.copy(error = it.message ?: "生成に失敗しました") }
        .onSuccess { _state.value = _state.value.copy(running = false) }
        .also { if (_state.value.running) _state.value = _state.value.copy(running = false) }
    }
  }
}

class TextActionActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
    val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    setContent { MyApplicationTheme { TextActionChooser(text, readOnly, onFinish = { finish() }, onReplace = { result -> setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result)); finish() }) } }
  }
}

@Composable
fun TextActionChooser(text: String, readOnly: Boolean, viewModel: TextActionViewModel = viewModel(), onFinish: () -> Unit, onReplace: (String) -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = androidx.compose.ui.platform.LocalContext.current
  LaunchedEffect(state.running, state.selected?.id) {
    val action = state.selected
    if (!state.running && action?.outputMode == TextActionOutputMode.COPY && state.result.isNotBlank()) {
      context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("AI Model Bridge", state.result))
    }
  }
  var editing by remember { mutableStateOf<TextAction?>(null) }
  var showResult by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("AI Model Bridge", style = MaterialTheme.typography.headlineSmall)
    Text("選択した文章に実行するアクション", style = MaterialTheme.typography.titleMedium)
    state.actions.filter { it.enabled }.forEach { action ->
      Card(onClick = { viewModel.run(action, text); showResult = true }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(action.name); Text("実行") } }
    }
    if (state.actions.none { it.enabled }) Text("有効なアクションがありません。アプリの設定で有効化してください。")
    if (showResult) {
      Text("結果", style = MaterialTheme.typography.titleMedium)
      if (state.running) Text("生成中…")
      if (state.result.isNotBlank()) Text(state.result)
      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { (context.getSystemService(android.content.ClipboardManager::class.java)).setPrimaryClip(android.content.ClipData.newPlainText("AI Model Bridge", state.result)) }) { Text("コピー") }
        if (!readOnly && state.result.isNotBlank()) Button(onClick = { onReplace(state.result) }) { Text("元の文章を置換") }
        OutlinedButton(onClick = onFinish) { Text("閉じる") }
      }
    }
  }
}

@Composable
fun TextActionSettingsScreen(viewModel: TextActionViewModel = viewModel(), modifier: Modifier = Modifier, onOpenDrawer: () -> Unit = {}) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var editor by remember { mutableStateOf<TextAction?>(null) }
  var adding by remember { mutableStateOf(false) }
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { TextButton(onClick = onOpenDrawer) { Text("☰") }; Text("AIテキストアクション", style = MaterialTheme.typography.headlineSmall) }
    Text("他アプリで文字を選択し、Android標準メニューの「AI Model Bridge」から実行するアクションを管理します。")
    state.actions.forEach { action ->
      Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(action.name, style = MaterialTheme.typography.titleMedium); Text(action.promptTemplate.take(100), style = MaterialTheme.typography.bodySmall); Text(if (action.enabled) "有効" else "無効", style = MaterialTheme.typography.labelSmall) }; Row { TextButton(onClick = { viewModel.toggle(action) }) { Text(if (action.enabled) "無効化" else "有効化") }; TextButton(onClick = { editor = action }) { Text("編集") }; TextButton(onClick = { viewModel.delete(action.id) }) { Text("削除") } } } }
    }
    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("新しいアクションを追加") }
  }
  if (adding) ActionEditorDialog(null, viewModel, { adding = false })
  editor?.let { ActionEditorDialog(it, viewModel, { editor = null }) }
}

@Composable
private fun ActionEditorDialog(existing: TextAction?, viewModel: TextActionViewModel, dismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val models = remember { ModelCatalog.available(context) }
  val presets = remember { SettingsStore(context).presets() }
  var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
  var model by remember(existing) { mutableStateOf(existing?.model.orEmpty()) }
  var backend by remember(existing) { mutableStateOf(existing?.backend ?: "auto") }
  var template by remember(existing) { mutableStateOf(existing?.promptTemplate ?: "{{text}}") }
  var presetId by remember(existing) { mutableStateOf(existing?.systemPromptPresetId.orEmpty()) }
  var customSystem by remember(existing) { mutableStateOf(existing?.customSystemPrompt.orEmpty()) }
  var maxTokens by remember(existing) { mutableStateOf((existing?.maxTokens ?: 1024).toString()) }
  var temperature by remember(existing) { mutableStateOf((existing?.temperature ?: 0.7f).toString()) }
  var outputMode by remember(existing) { mutableStateOf(existing?.outputMode ?: TextActionOutputMode.DISPLAY) }
  var modelMenu by remember { mutableStateOf(false) }
  var backendMenu by remember { mutableStateOf(false) }
  var presetMenu by remember { mutableStateOf(false) }
  var outputMenu by remember { mutableStateOf(false) }
  AlertDialog(onDismissRequest = dismiss, title = { Text(if (existing == null) "アクションを追加" else "アクションを編集") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(name, { name = it }, label = { Text("表示名") }, singleLine = true)
      OutlinedButton(onClick = { modelMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("モデル: ${model.ifBlank { "自動選択" }}") }
      DropdownMenu(modelMenu, { modelMenu = false }) { DropdownMenuItem({ Text("自動選択") }, { model = ""; modelMenu = false }); models.forEach { item -> DropdownMenuItem({ Text(item.name) }, { model = item.name; backend = if (item.local) "local" else "ollama"; modelMenu = false }) } }
      OutlinedButton(onClick = { backendMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("実行先: $backend") }
      DropdownMenu(backendMenu, { backendMenu = false }) { listOf("auto", "local", "ollama").forEach { item -> DropdownMenuItem({ Text(item) }, { backend = item; backendMenu = false }) } }
      OutlinedTextField(template, { template = it }, label = { Text("プロンプト（{{text}}が選択文字）") }, minLines = 3)
      OutlinedButton(onClick = { presetMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("システムプロンプト: ${presets.firstOrNull { it.id == presetId }?.name ?: "なし"}") }
      DropdownMenu(presetMenu, { presetMenu = false }) { DropdownMenuItem({ Text("なし") }, { presetId = ""; presetMenu = false }); presets.forEach { item -> DropdownMenuItem({ Text(item.name) }, { presetId = item.id; presetMenu = false }) }; DropdownMenuItem({ Text("カスタム") }, { presetId = "custom"; presetMenu = false }) }
      if (presetId == "custom") OutlinedTextField(customSystem, { customSystem = it }, label = { Text("カスタムシステムプロンプト") }, minLines = 2)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(maxTokens, { maxTokens = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("最大トークン") }, singleLine = true); OutlinedTextField(temperature, { temperature = it }, Modifier.weight(1f), label = { Text("温度") }, singleLine = true) }
      OutlinedButton(onClick = { outputMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("出力: ${outputMode.label()}") }
      DropdownMenu(outputMenu, { outputMenu = false }) { TextActionOutputMode.values().forEach { item -> DropdownMenuItem({ Text(item.label()) }, { outputMode = item; outputMenu = false }) } }
    }
  }, confirmButton = { Button(onClick = { if (name.isNotBlank() && template.isNotBlank()) { viewModel.save(TextAction(existing?.id ?: TextActionStore.newId(), name.trim(), true, model, backend, template, presetId.takeUnless { it == "custom" }.orEmpty(), if (presetId == "custom") customSystem else "", maxTokens.toIntOrNull()?.coerceIn(1, 8192) ?: 1024, temperature.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f, outputMode)); dismiss() } }) { Text("保存") } }, dismissButton = { OutlinedButton(onClick = dismiss) { Text("キャンセル") } })
}

private fun TextActionOutputMode.label() = when (this) { TextActionOutputMode.DISPLAY -> "結果表示"; TextActionOutputMode.COPY -> "クリップボードへコピー"; TextActionOutputMode.REPLACE -> "元テキストへ置換" }
