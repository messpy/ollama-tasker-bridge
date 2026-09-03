package com.example.ollamataskerbridge.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
fun MainScreen(viewModel: MainScreenViewModel = viewModel(), modifier: Modifier = Modifier) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var pendingDelete by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = modifier.fillMaxSize().padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Ollama Tasker Bridge", style = MaterialTheme.typography.headlineSmall)
    Text("ローカルOllama接続", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
      value = state.endpoint,
      onValueChange = viewModel::endpointChanged,
      label = { Text("Ollama URL") },
      supportingText = { Text("例: http://192.168.1.10:11434") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = state.downloadModel,
      onValueChange = viewModel::downloadModelChanged,
      label = { Text("取得するモデル名") },
      supportingText = { Text("例: llama3.2") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = viewModel::testConnection, enabled = !state.loading) { Text("接続テスト") }
      OutlinedButton(onClick = viewModel::loadModels, enabled = !state.loading) { Text("モデル一覧") }
      Button(onClick = { viewModel.downloadModel(state.downloadModel) }, enabled = !state.loading && state.downloadModel.isNotBlank()) { Text("取得") }
    }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (state.loading) CircularProgressIndicator()
    HorizontalDivider()
    Text("モデル", style = MaterialTheme.typography.titleMedium)
    if (state.models.isEmpty()) Text("まだ取得していません")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(state.models, key = { it }) { model ->
        Card(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(model, modifier = Modifier.weight(1f).padding(top = 8.dp))
            TextButton(onClick = { pendingDelete = model }, enabled = !state.loading) { Text("削除") }
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
