package com.example.ollamataskerbridge.ui.main

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ollamataskerbridge.data.OllamaModel
import com.example.ollamataskerbridge.data.ModelSource
import com.example.ollamataskerbridge.data.SystemPromptPreset
import com.example.ollamataskerbridge.theme.MyApplicationTheme

@Composable
fun MainScreen(viewModel: MainScreenViewModel = viewModel(), modifier: Modifier = Modifier, onOpenChat: () -> Unit = {}) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var pendingDelete by remember { mutableStateOf<String?>(null) }
  var editingPreset by remember { mutableStateOf<SystemPromptPreset?>(null) }
  var showPresetDialog by remember { mutableStateOf(false) }
  var systemPresetMenu by remember { mutableStateOf(false) }
  val clipboard = LocalClipboardManager.current
  val maxBytes = state.maxLocalModelSizeGb.toDoubleOrNull()?.takeIf { it >= 0 }?.times(1_000_000_000.0)?.toLong() ?: Long.MAX_VALUE
  val shownModels = state.models.filter { it.source == state.source }.filter { it.sizeBytes <= 0L || it.sizeBytes <= maxBytes }.filter { !state.localOnly || it.local }.filter { state.search.isBlank() || it.name.contains(state.search, true) }
  Column(modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Ollama Tasker Bridge", style = MaterialTheme.typography.headlineSmall)
    Text("本体アプリ", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(state.endpoint, viewModel::endpointChanged, Modifier.fillMaxWidth(), label = { Text("Ollama URL") }, supportingText = { Text("Cloudは https://ollama.com") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
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

    Text("モデル管理", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (state.source == ModelSource.OLLAMA) Button(onClick = { viewModel.sourceChanged(ModelSource.OLLAMA) }) { Text("Ollama") } else OutlinedButton(onClick = { viewModel.sourceChanged(ModelSource.OLLAMA) }) { Text("Ollama") }
      if (state.source == ModelSource.HUGGING_FACE) Button(onClick = { viewModel.sourceChanged(ModelSource.HUGGING_FACE) }) { Text("Hugging Face") } else OutlinedButton(onClick = { viewModel.sourceChanged(ModelSource.HUGGING_FACE) }) { Text("Hugging Face") }
    }
    Text(if (state.source == ModelSource.OLLAMA) "Ollama公式・接続先のモデル" else "Hugging FaceのAndroid向けGGUFモデル", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      OutlinedTextField(state.search, viewModel::searchChanged, Modifier.weight(1f), label = { Text("モデルを検索") }, singleLine = true)
      OutlinedTextField(
        value = state.maxLocalModelSizeGb,
        onValueChange = viewModel::maxLocalModelSizeChanged,
        modifier = Modifier.width(132.dp),
        label = { Text("上限GB", fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      )
      OutlinedButton(onClick = viewModel::loadModels, enabled = !state.loading) { Text("↻") }
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Checkbox(state.localOnly, viewModel::localOnlyChanged)
      Text("ローカル取得済みのみ表示")
    }
    
    Text("${shownModels.size}件（上限以下。未知サイズは取得時に確認）", style = MaterialTheme.typography.bodySmall)
    if (shownModels.isEmpty()) {
      Text("表示できるモデルはありません。上限値または検索条件を確認してください。", style = MaterialTheme.typography.bodySmall)
    } else {
      Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shownModels.forEach { model -> ModelRow(model, state.loading, state.selectedModel == model.name, viewModel::selectModel, viewModel::downloadModel) { pendingDelete = it } }
      }
    }
    Text(if (state.selectedModel.isBlank()) "モデル未選択" else "選択中: ${state.selectedModel}（${if (state.models.firstOrNull { it.name == state.selectedModel }?.local == true) "ローカル実行" else "Cloud実行"}）")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = viewModel::testConnection, enabled = !state.loading) { Text("接続テスト") }
      Button(onClick = { viewModel.downloadModel(state.selectedModel) }, enabled = !state.loading && state.selectedModel.isNotBlank() && state.models.firstOrNull { it.name == state.selectedModel }?.local != true) { Text("選択モデルを取得") }
    }
    if (state.downloadTotalBytes > 0L) {
      val progress = (state.downloadedBytes.toFloat() / state.downloadTotalBytes.toFloat()).coerceIn(0f, 1f)
      LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
      Text("モデル取得中: %.0f%% (%.1f / %.1f GB)".format(progress * 100f, state.downloadedBytes / 1_000_000_000f, state.downloadTotalBytes / 1_000_000_000f), style = MaterialTheme.typography.bodySmall)
    }
    state.message?.let { Text(it, color = if (it.startsWith("エラー")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    if (state.loading) CircularProgressIndicator()

    OutlinedButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) { Text("テストチャットを開く") }

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
  }
  pendingDelete?.let { target ->
    AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("削除しますか？") }, text = { Text(if (target.startsWith("preset:")) "プリセットを削除します。" else "$target を削除します。") }, confirmButton = { TextButton(onClick = { if (target.startsWith("preset:")) viewModel.deletePreset(target.removePrefix("preset:")) else viewModel.deleteModel(target); pendingDelete = null }) { Text("削除") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("キャンセル") } })
  }
  if (showPresetDialog) PresetDialog(null, { showPresetDialog = false }) { name, body -> viewModel.savePreset(name, body); showPresetDialog = false }
  editingPreset?.let { preset -> PresetDialog(preset, { editingPreset = null }) { name, body -> viewModel.savePreset(name, body, preset.id); editingPreset = null } }
}

@Composable
private fun ModelRow(model: OllamaModel, loading: Boolean, selected: Boolean, onSelect: (String) -> Unit, onDownload: (String) -> Unit, onDelete: (String) -> Unit) {
  Card(onClick = { onSelect(model.name) }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(model.name); Text(if (model.source == ModelSource.OLLAMA) "Ollama" else "Hugging Face", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        Text(if (model.sizeBytes > 0) "%.2f GB".format(model.sizeBytes / 1_000_000_000.0) else "サイズ不明", style = MaterialTheme.typography.bodySmall)
      }
      if (model.local) TextButton(onClick = { onDelete(model.name) }, enabled = !loading) { Text("選択モデル削除", color = MaterialTheme.colorScheme.error) } else if (model.downloadable) IconButton(onClick = { onDownload(model.name) }, enabled = !loading) { Text("↓") } else Text("Cloudのみ（取得不可）", style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun PresetDialog(initial: SystemPromptPreset?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
  var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
  var body by remember(initial) { mutableStateOf(initial?.body.orEmpty()) }
  AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "新しいプリセット" else "プリセットを編集") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("名前") }); OutlinedTextField(body, { body = it }, label = { Text("本文") }, minLines = 5) } }, confirmButton = { TextButton(onClick = { onSave(name.trim(), body) }, enabled = name.isNotBlank() && body.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
}

@Composable
fun MainScreenPreview() { MyApplicationTheme { MainScreen() } }
