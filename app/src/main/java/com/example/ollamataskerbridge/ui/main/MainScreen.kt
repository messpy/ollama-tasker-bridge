package com.example.ollamataskerbridge.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ollamataskerbridge.theme.MyApplicationTheme

private const val SHOW_REMOTE_OLLAMA = true

@Composable
fun MainScreen(viewModel: MainScreenViewModel = viewModel(), modifier: Modifier = Modifier) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var pendingDelete by remember { mutableStateOf<String?>(null) }
  if (!SHOW_REMOTE_OLLAMA) {
    Column(
      modifier = modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Text("Ollama Tasker Bridge", style = MaterialTheme.typography.headlineSmall)
      Text("端末内AI", style = MaterialTheme.typography.titleLarge)
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("準備中", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          Text("スマートフォン上でモデルをダウンロードして実行する機能を準備しています。")
          Text("モデル管理とTasker連携は、この画面から利用できるようになります。", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
    return
  }

  Column(
    modifier = modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Ollama Tasker Bridge", style = MaterialTheme.typography.headlineSmall)
    Text("Ollama RegistryからAndroidへ取得", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
      value = state.endpoint,
      onValueChange = viewModel::endpointChanged,
      label = { Text("Ollama URL（接続テスト用）") },
      supportingText = { Text("モデル取得はOllama RegistryからAndroidへ直接行います") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = state.apiKey,
      onValueChange = viewModel::apiKeyChanged,
      label = { Text("Ollama APIキー") },
      supportingText = { Text("ollama.comのCloud API用。端末内に保存します") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = state.testPrompt,
      onValueChange = viewModel::testPromptChanged,
      label = { Text("テスト用プロンプト") },
      supportingText = { Text("ここで本体のCloud/ローカル実行を確認できます") },
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = state.downloadModel,
      onValueChange = viewModel::downloadModelChanged,
      label = { Text("テスト/取得するモデル名") },
      supportingText = { Text("例: llama3.2。取得済みならローカル、未取得ならCloudでテスト") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = viewModel::testConnection, enabled = !state.loading) { Text("接続テスト") }
      OutlinedButton(onClick = viewModel::loadModels, enabled = !state.loading) { Text("モデル一覧") }
      Button(onClick = { viewModel.downloadModel(state.downloadModel) }, enabled = !state.loading && state.downloadModel.isNotBlank()) { Text("取得") }
      OutlinedButton(onClick = viewModel::runTest, enabled = !state.loading && state.downloadModel.isNotBlank() && state.testPrompt.isNotBlank()) { Text("テスト実行") }
    }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (state.loading) CircularProgressIndicator()
    HorizontalDivider()
    Text("Android内のモデル", style = MaterialTheme.typography.titleMedium)
    if (state.models.isEmpty()) Text("まだ取得していません")
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(state.models.sortedBy { it.remote }, key = { it.name }) { model ->
        Card(modifier = Modifier.fillMaxWidth()) {
          Text(if (model.remote) "リモート" else "ローカル", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
          Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(model.name, modifier = Modifier.weight(1f).padding(top = 8.dp))
            if (model.remote && model.downloadable) {
              Button(onClick = { viewModel.downloadModel(model.name) }, enabled = !state.loading) { Text("Androidへ取得") }
            } else if (model.remote) {
              Text("Cloud専用", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
            } else {
              TextButton(onClick = { pendingDelete = model.name }, enabled = !state.loading) { Text("削除") }
            }
          }
        }
      }
    }
  }

  pendingDelete?.let { model ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("モデルを削除しますか？") },
      text = { Text("${model}\nこの操作は元に戻せません。") },
      confirmButton = { TextButton(onClick = { pendingDelete = null; viewModel.deleteModel(model) }) { Text("削除する") } },
      dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("キャンセル") } },
    )
  }
}


@Composable
fun MainScreenPreview() { MyApplicationTheme { MainScreen() } }
