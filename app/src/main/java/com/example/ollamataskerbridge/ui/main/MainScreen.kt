package com.example.ollamataskerbridge.ui.main

import android.content.ClipData
import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ollamataskerbridge.data.OllamaModel
import com.example.ollamataskerbridge.data.supportsVision
import com.example.ollamataskerbridge.diagnostics.DiagnosticsLog
import com.example.ollamataskerbridge.data.ModelSource
import com.example.ollamataskerbridge.data.SettingsStore
import com.example.ollamataskerbridge.data.SystemPromptPreset
import com.example.ollamataskerbridge.theme.MyApplicationTheme

private fun OllamaModel.isCloudOnly(): Boolean = source == ModelSource.OLLAMA && !local && (remote || name.contains(":cloud", ignoreCase = true) || !downloadable)
private fun OllamaModel.executionLabel(): String = when {
  local -> "端末に保存済み"
  isCloudOnly() -> "Cloud利用可能"
  else -> "未登録"
}

private fun OllamaModel.modelKind(): String {
  val value = name.lowercase().replace("_", "-").replace(":", "-")
  return when {
    supportsVision() || listOf("vlm", "vision", "llava", "minicpm-v", "moondream", "qwen2-vl", "qwen2.5-vl", "qwen2.5vl", "qwen3-vl", "qwen3vl", "qwen-vl", "pixtral", "internvl", "molmo", "glm-5.3-flash", "qwen3.8", "ornith").any { value.contains(it) } -> "VLM"
    listOf("whisper", "speech", "audio", "audio-language", "audiolanguage", "ultravox", "voxtral", "qwen2-audio", "voice", "tts").any { value.contains(it) } -> "Audio-Language Model"
    listOf("embed", "rerank", "embedding").any { value.contains(it) } -> "その他"
    else -> "LLM"
  }
}
enum class MainSection { SETTINGS, MODELS, PROMPTS }

@Composable
fun MainScreen(viewModel: MainScreenViewModel = viewModel(), modifier: Modifier = Modifier, section: MainSection = MainSection.SETTINGS, onOpenDrawer: () -> Unit = {}, onOpenChat: () -> Unit = {}) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var pendingDelete by remember { mutableStateOf<String?>(null) }
  var pendingCancel by remember { mutableStateOf(false) }
  var editingPreset by remember { mutableStateOf<SystemPromptPreset?>(null) }
  var showPresetDialog by remember { mutableStateOf(false) }
  var systemPresetMenu by remember { mutableStateOf(false) }
  var pendingGemmaDownload by remember { mutableStateOf<String?>(null) }
  var availabilityMenu by remember { mutableStateOf(false) }
  var kindMenu by remember { mutableStateOf(false) }
  var sourceFilterMenu by remember { mutableStateOf(false) }
  var kindFilter by remember { mutableStateOf(setOf("LLM", "VLM", "Audio-Language Model", "その他")) }
  var executionFilter by remember { mutableStateOf(setOf("端末に保存済み", "Cloud利用可能", "未登録")) }
  var executionMenu by remember { mutableStateOf(false) }
  var sourceMenu by remember { mutableStateOf(false) }
  var modelTab by remember { mutableStateOf(0) }
  val clipboard = LocalClipboardManager.current
  val diagnosticsScope = rememberCoroutineScope()
  val context = LocalContext.current
  var contextTokens by remember { mutableStateOf(SettingsStore(context).liteRtContextTokens.toString()) }
  val memoryInfo = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
  val totalRamGb = memoryInfo.totalMem / 1_000_000_000.0
  val freeStorageGb = StatFs(context.filesDir.path).availableBytes / 1_000_000_000.0
  val recommendedModelGb = minOf(totalRamGb * 0.5, (freeStorageGb - 1.0).coerceAtLeast(0.5)).coerceIn(0.5, 8.0)
  val requestDownload: (String) -> Unit = { name -> val item = state.models.firstOrNull { it.name == name }; if (item?.source == ModelSource.OLLAMA && !item.local && !item.downloadable) viewModel.enableCloudModel(name) else if (name.contains("gemma", ignoreCase = true) && !viewModel.gemmaTermsAccepted()) pendingGemmaDownload = name else viewModel.downloadModel(name) }
  val minBytes = state.minLocalModelSizeGb.toDoubleOrNull()?.coerceAtLeast(0.0)?.times(1_000_000_000.0)?.toLong() ?: 0L
  val maxBytes = state.maxLocalModelSizeGb.toDoubleOrNull()?.takeIf { it >= 0 }?.times(1_000_000_000.0)?.toLong() ?: Long.MAX_VALUE
  // Tabs represent storage state only. Cloud-enabled models are still online
  // catalog entries and must not disappear from the model search tab.
  val shownModels = state.models.filter { it.source in state.enabledSources }.filter { if (modelTab == 0) it.local else !it.local }
    .filter { if (state.downloadedOnly) it.local || it.enabled else if (state.showLocal == state.showCloud) true else if (state.showCloud) it.isCloudOnly() else !it.isCloudOnly() }
    .filter { it.remote || it.sizeBytes <= 0L || (it.sizeBytes >= minBytes && it.sizeBytes <= maxBytes) }
    .filter { it.executionLabel() in executionFilter }
    .filter { it.modelKind() in kindFilter }
    .filter { state.search.isBlank() || it.name.contains(state.search, true) }
  LaunchedEffect(state.models.size, state.enabledSources, kindFilter, executionFilter, modelTab, state.search, state.minLocalModelSizeGb, state.maxLocalModelSizeGb) {
    val cloudVisionCount = shownModels.count { it.isCloudOnly() && it.supportsVision() }
    DiagnosticsLog.note("モデル一覧フィルタ: service=${state.enabledSources.joinToString()} kind=${kindFilter.joinToString()} execution=${executionFilter.joinToString()} tab=$modelTab searchChars=${state.search.length} resultCount=${shownModels.size} cloudVisionCount=$cloudVisionCount")
  }
  Column(modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { IconButton(onClick = onOpenDrawer) { Text("☰") }; Text("AI Model Bridge", style = MaterialTheme.typography.headlineSmall) }
    if (section == MainSection.SETTINGS) {
    OutlinedTextField(state.endpoint, viewModel::endpointChanged, Modifier.fillMaxWidth(), label = { Text("Ollama URL") }, supportingText = { Text("Cloudは https://ollama.com") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    OutlinedTextField(contextTokens, { value -> contextTokens = value.filter(Char::isDigit); value.toIntOrNull()?.let { SettingsStore(context).liteRtContextTokens = it } }, Modifier.fillMaxWidth(), label = { Text("LiteRT-LMコンテキスト上限") }, supportingText = { Text("入力＋会話履歴の上限（512〜8192）。最大トークン数とは別設定です") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(
      value = state.apiKey,
      onValueChange = viewModel::apiKeyChanged,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Ollama APIキー") },
      singleLine = true,
      visualTransformation = if (state.apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
      trailingIcon = {
        Row {
          IconButton(onClick = { viewModel.apiKeyVisibleChanged(!state.apiKeyVisible) }) { Text(if (state.apiKeyVisible) "隠す" else "表示") }
          IconButton(onClick = { clipboard.setText(AnnotatedString(state.apiKey)) }) { Text("コピー") }
        }
      },
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ollama.com/settings/keys"))) }) { Text("Ollama APIキーを取得", fontSize = 11.sp) }
    }
    OutlinedTextField(value = state.huggingFaceToken, onValueChange = viewModel::huggingFaceTokenChanged, modifier = Modifier.fillMaxWidth(), label = { Text("Hugging Faceアクセストークン（Gemma等）") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))) }) { Text("Hugging Faceトークンを取得", fontSize = 11.sp) }
    }
    }
    if (section == MainSection.MODELS) {
    TabRow(selectedTabIndex = modelTab, modifier = Modifier.fillMaxWidth()) { Tab(selected = modelTab == 0, onClick = { modelTab = 0; viewModel.refreshInstalledModels() }, text = { Text("ダウンロード済み") }); Tab(selected = modelTab == 1, onClick = { modelTab = 1; viewModel.refreshInstalledModels() }, text = { Text("モデルを探す") }) }
    Text("端末情報: RAM %.1fGB / 空き容量 %.1fGB / CPU %s".format(totalRamGb, freeStorageGb, Build.SUPPORTED_ABIS.firstOrNull() ?: "不明"), style = MaterialTheme.typography.bodySmall)
    Text("推奨モデルサイズ: %.1fGB以下（目安）".format(recommendedModelGb), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(state.search, viewModel::searchChanged, Modifier.fillMaxWidth().height(48.dp), label = { Text("モデルを検索", fontSize = 12.sp) }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("容量範囲: ${state.minLocalModelSizeGb}〜${state.maxLocalModelSizeGb} GB", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); OutlinedButton(onClick = { viewModel.modelSizeRangeChanged(0f, recommendedModelGb.toFloat()) }) { Text("推奨") } }
    RangeSlider(value = (state.minLocalModelSizeGb.toFloatOrNull()?.coerceIn(0f, 200f) ?: 0f)..(state.maxLocalModelSizeGb.toFloatOrNull()?.coerceIn(0f, 200f) ?: 15f), onValueChange = { viewModel.modelSizeRangeChanged(it.start, it.endInclusive) }, valueRange = 0f..200f, steps = 199)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Box {
        OutlinedButton(onClick = { sourceFilterMenu = true }) { Text("サービス: " + if (state.enabledSources.size == ModelSource.values().size) "すべて" else state.enabledSources.size.toString() + "件") }
        DropdownMenu(expanded = sourceFilterMenu, onDismissRequest = { sourceFilterMenu = false }) {
          DropdownMenuItem(text = { Text("すべて") }, onClick = { viewModel.sourceFilterChanged(null) })
          ModelSource.values().forEach { source -> DropdownMenuItem(text = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(source in state.enabledSources, { checked -> viewModel.sourceEnabled(source, checked) }); Text(source.displayName()) } }, onClick = { viewModel.sourceEnabled(source, source !in state.enabledSources) }) }
        }
      }
      Box {
        OutlinedButton(onClick = { kindMenu = true }) { Text("種類: " + if (kindFilter.size == 4) "すべて" else kindFilter.size.toString() + "件") }
        DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
          listOf("LLM", "VLM", "Audio-Language Model", "その他").forEach { kind -> DropdownMenuItem(text = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(kind in kindFilter, { checked -> kindFilter = if (checked) kindFilter + kind else kindFilter - kind }); Text(kind) } }, onClick = { kindFilter = if (kind in kindFilter) kindFilter - kind else kindFilter + kind }) }
        }
      }
      Box {
        OutlinedButton(onClick = { executionMenu = true }) { Text("実行先: " + if (executionFilter.size == 3) "すべて" else executionFilter.size.toString() + "件") }
        DropdownMenu(expanded = executionMenu, onDismissRequest = { executionMenu = false }) {
          listOf("端末に保存済み", "Cloud利用可能", "未登録").forEach { target -> DropdownMenuItem(text = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(target in executionFilter, { checked -> executionFilter = if (checked) executionFilter + target else executionFilter - target }); Text(target) } }, onClick = { executionFilter = if (target in executionFilter) executionFilter - target else executionFilter + target }) }
        }
      }
    }
    state.activeDownloadModel?.let { active -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("ダウンロード中: $active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); TextButton(onClick = viewModel::cancelDownload) { Text("キャンセル") } } }
    Text("${shownModels.size}件（上限以下。未知サイズは取得時に確認）", style = MaterialTheme.typography.bodySmall)
    if (shownModels.isEmpty()) {
      Text("表示できるモデルはありません。上限値または検索条件を確認してください。", style = MaterialTheme.typography.bodySmall)
    } else {
      Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shownModels.forEach { model -> ModelRow(model, state.loading, state.selectedModel == model.name, state.activeDownloadModel == model.name, viewModel::selectModel, requestDownload, { pendingDelete = it }, { pendingCancel = true }) }
      }
    }
    Text(if (state.selectedModel.isBlank()) "モデル未選択" else "選択中: ${state.selectedModel}（${state.models.firstOrNull { it.name == state.selectedModel }?.let { if (it.local) "ローカル実行" else if (it.isCloudOnly()) "Cloud実行" else "未登録" } ?: "未登録"}）")
    if (state.downloadTotalBytes > 0L) {
      val progress = (state.downloadedBytes.toFloat() / state.downloadTotalBytes.toFloat()).coerceIn(0f, 1f)
      LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
      Text("モデル取得中: %.0f%% (%.1f / %.1f GB)".format(progress * 100f, state.downloadedBytes / 1_000_000_000f, state.downloadTotalBytes / 1_000_000_000f), style = MaterialTheme.typography.bodySmall)
    }
    state.message?.let { Text(it, color = if (it.startsWith("エラー")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    state.helpUrl?.let { url -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("アクセス申請を開く") } }
    if (state.loading) CircularProgressIndicator()

    }

    if (section == MainSection.PROMPTS) {
    HorizontalDivider()
    Text("システムプロンプト管理", style = MaterialTheme.typography.titleMedium)
    state.presets.forEach { preset ->
      Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          Column(Modifier.weight(1f)) { Text(preset.name); Text(preset.body.take(60), style = MaterialTheme.typography.bodySmall) }
          TextButton(onClick = { editingPreset = preset }) { Text("編集") }
          TextButton(onClick = { pendingDelete = "preset:${preset.id}" }) { Text("削除") }
        }
      }
    }
    OutlinedButton(onClick = { showPresetDialog = true }) { Text("新しいプリセットを追加") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      Box(Modifier.combinedClickable(onClick = { diagnosticsScope.launch { clipboard.setText(AnnotatedString(DiagnosticsLog.copyableSnapshot())) } }, onLongClick = { DiagnosticsLog.clear(); Toast.makeText(context, "診断ログをクリアしました", Toast.LENGTH_SHORT).show() }).padding(8.dp)) { Text("ログをコピー（長押しでクリア）", fontSize = 11.sp) }
    }
    }
  }
  pendingGemmaDownload?.let { name -> AlertDialog(onDismissRequest = { pendingGemmaDownload = null }, title = { Text("Gemma利用条件") }, text = { Text("GemmaモデルはGoogleの利用規約に従って使用してください。 https://ai.google.dev/gemma/terms") }, confirmButton = { TextButton(onClick = { viewModel.acceptGemmaTerms(); pendingGemmaDownload = null; viewModel.downloadModel(name) }) { Text("同意してダウンロード") } }, dismissButton = { TextButton(onClick = { pendingGemmaDownload = null }) { Text("キャンセル") } }) }
  if (pendingCancel) AlertDialog(onDismissRequest = { pendingCancel = false }, title = { Text("ダウンロードをキャンセルしますか？") }, text = { Text("途中までのファイルは削除されます。") }, confirmButton = { TextButton(onClick = { viewModel.cancelDownload(); pendingCancel = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { pendingCancel = false }) { Text("キャンセル") } })
  pendingDelete?.let { target ->
    AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("削除しますか？") }, text = { Text(if (target.startsWith("preset:")) "プリセットを削除します。" else "$target を削除します。") }, confirmButton = { TextButton(onClick = { if (target.startsWith("preset:")) viewModel.deletePreset(target.removePrefix("preset:")) else viewModel.deleteModel(target); pendingDelete = null }) { Text("削除") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("キャンセル") } })
  }
  if (showPresetDialog) PresetDialog(null, { showPresetDialog = false }) { name, body, maxTokens, temperature -> viewModel.savePreset(name, body, maxTokens, temperature); showPresetDialog = false }
  editingPreset?.let { preset -> PresetDialog(preset, { editingPreset = null }) { name, body, maxTokens, temperature -> viewModel.savePreset(name, body, maxTokens, temperature, preset.id); editingPreset = null } }
}

@Composable
private fun ModelRow(model: OllamaModel, loading: Boolean, selected: Boolean, downloading: Boolean, onSelect: (String) -> Unit, onDownload: (String) -> Unit, onDelete: (String) -> Unit, onCancel: () -> Unit) {
  val clipboard = LocalClipboardManager.current; val context = LocalContext.current
  Card(onClick = { onSelect(model.name) }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(model.name, modifier = Modifier.combinedClickable(onClick = { onSelect(model.name) }, onLongClick = { clipboard.setText(AnnotatedString(model.name)); Toast.makeText(context, "モデル名をコピーしました", Toast.LENGTH_SHORT).show() }))
          Text(model.source.serviceEmoji(), style = MaterialTheme.typography.labelSmall)
          if (model.supportsVision()) Text("👁️", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
          if (model.isCloudOnly()) Text("☁", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)


        }
        Text(if (model.sizeBytes > 0) "%.2f GB".format(model.sizeBytes / 1_000_000_000.0) else "サイズ不明", style = MaterialTheme.typography.bodySmall)
      }
      if (model.local) TextButton(onClick = { onDelete(model.name) }, enabled = !loading) { Text("選択モデル削除", color = MaterialTheme.colorScheme.error) } else if (model.downloadable || (model.source == ModelSource.OLLAMA && !model.enabled)) IconButton(onClick = { if (downloading) onCancel() else onDownload(model.name) }, enabled = !loading || downloading) { if (downloading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (model.enabled) "✓" else "↓") } else if (model.enabled) Text("✓ Cloud利用可能", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) else Text("未登録", style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun PresetDialog(initial: SystemPromptPreset?, onDismiss: () -> Unit, onSave: (String, String, Int, Float) -> Unit) {
  var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
  var body by remember(initial) { mutableStateOf(initial?.body.orEmpty()) }
  var maxTokens by remember(initial) { mutableStateOf(initial?.maxTokens?.toString() ?: "1024") }
  var temperature by remember(initial) { mutableStateOf(initial?.temperature?.toString() ?: "0.7") }
  AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "新しいプリセット" else "プリセットを編集") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("名前") }); OutlinedTextField(body, { body = it }, label = { Text("本文") }, minLines = 5); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(maxTokens, { maxTokens = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("最大トークン数") }); OutlinedTextField(temperature, { temperature = it }, Modifier.weight(1f), label = { Text("Temperature") }) } } }, confirmButton = { TextButton(onClick = { onSave(name.trim(), body, maxTokens.toIntOrNull()?.coerceIn(1, 4096) ?: 1024, temperature.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f) }, enabled = name.isNotBlank() && body.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
}

private fun ModelSource.serviceEmoji(): String = when (this) { ModelSource.OLLAMA -> "🦙"; ModelSource.HUGGING_FACE -> "🤗"; ModelSource.LITERT_LM -> "🌞" }

private fun ModelSource.displayName(): String = when (this) {
  ModelSource.OLLAMA -> "🦙 Ollama"
  ModelSource.HUGGING_FACE -> "🤗 Hugging Face"
  ModelSource.LITERT_LM -> "🌞 LiteRT-LM"
}

@Composable
fun MainScreenPreview() { MyApplicationTheme { MainScreen() } }
