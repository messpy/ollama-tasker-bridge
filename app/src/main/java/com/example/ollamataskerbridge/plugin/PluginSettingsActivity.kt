package com.example.ollamataskerbridge.plugin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.SettingsStore
import com.example.ollamataskerbridge.MainActivity
import kotlinx.coroutines.launch
import com.example.ollamataskerbridge.theme.MyApplicationTheme

class PluginSettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val initial = intent.extras?.getBundle(LocalePluginContract.EXTRA_BUNDLE)
    val appContext = applicationContext
    setContent {
      MyApplicationTheme {
        PluginSettingsContent(
          initialModel = initial?.getString(LocalePluginContract.KEY_MODEL).orEmpty(),
          initialPrompt = initial?.getString(LocalePluginContract.KEY_PROMPT).orEmpty(),
          initialSystem = initial?.getString(LocalePluginContract.KEY_SYSTEM).orEmpty(),
          initialMode = initial?.getString(LocalePluginContract.KEY_MODE) ?: "auto",
          initialResultVariable = initial?.getString(LocalePluginContract.KEY_RESULT_VARIABLE).orEmpty(),
          onOpenApp = {
            startActivity(Intent(this@PluginSettingsActivity, MainActivity::class.java))
          },
          loadModels = {
            val local = LocalModelStore(appContext).directory.listFiles()
              ?.filter { it.extension == "gguf" }
              ?.map { it.nameWithoutExtension }
              .orEmpty()
            val settings = SettingsStore(appContext)
            val remote = runCatching {
              OllamaClient(settings.endpoint, settings.apiKey).listModels().map { it.name }
            }.getOrDefault(emptyList())
            PluginModelLists(cloud = remote.distinct().sorted(), local = local.distinct().sorted())
          },
          onCancel = { setResult(Activity.RESULT_CANCELED); finish() },
          onSave = { model, prompt, system, mode, resultVariable ->
            val values = Bundle().apply {
              putString(LocalePluginContract.KEY_MODEL, model.trim())
              putString(LocalePluginContract.KEY_PROMPT, prompt)
              putString(LocalePluginContract.KEY_SYSTEM, system)
              putString(LocalePluginContract.KEY_MODE, mode)
              putString(LocalePluginContract.KEY_RESULT_VARIABLE, resultVariable.trim())
            }
            val result = Intent().apply {
              putExtra(LocalePluginContract.EXTRA_BUNDLE, values)
              putExtra(LocalePluginContract.EXTRA_STRING_BLURB, "$model (${modeLabel(mode)})")
            }
            setResult(Activity.RESULT_OK, result)
            finish()
          },
        )
      }
    }
  }

  private fun modeLabel(mode: String) = when (mode) {
    "local" -> "ローカル"
    "cloud" -> "Cloud"
    else -> "自動"
  }
}

private data class PluginModelLists(
  val cloud: List<String> = emptyList(),
  val local: List<String> = emptyList(),
)

@Composable
private fun PluginSettingsContent(
  initialModel: String,
  initialPrompt: String,
  initialSystem: String,
  initialMode: String,
  initialResultVariable: String,
  onOpenApp: () -> Unit,
  loadModels: suspend () -> PluginModelLists,
  onCancel: () -> Unit,
  onSave: (String, String, String, String, String) -> Unit,
) {
  var model by remember { mutableStateOf(initialModel) }
  var prompt by remember { mutableStateOf(initialPrompt) }
  var system by remember { mutableStateOf(initialSystem) }
  var mode by remember { mutableStateOf(initialMode) }
  var resultVariable by remember { mutableStateOf(initialResultVariable) }
  var query by remember { mutableStateOf("") }
  var modelLists by remember { mutableStateOf(PluginModelLists()) }
  var loadingModels by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  fun refreshModels() = scope.launch {
    loadingModels = true
    modelLists = loadModels()
    loadingModels = false
  }
  LaunchedEffect(Unit) { refreshModels() }
  Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Taskerアクション設定")
    Text("この画面はTasker/MacroDroid用です。APIキーやモデル取得は本体アプリで行います。")
    Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) { Text("本体アプリを開く") }
    OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("モデル名") })
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("モデルを検索") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { refreshModels() }, enabled = !loadingModels) {
        Text(if (loadingModels) "更新中…" else "候補を更新")
      }
      Text("本体アプリのAPIキーを使用", Modifier.padding(top = 12.dp))
    }
    val filteredCloud = modelLists.cloud.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    val filteredLocal = modelLists.local.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      item { Text("Cloudモデル", modifier = Modifier.padding(top = 4.dp)) }
      if (filteredCloud.isEmpty()) item { Text("Cloudモデルなし（APIキー設定後に候補を更新）") }
      items(filteredCloud, key = { "cloud:$it" }) { candidate ->
        Button(onClick = { model = candidate }, modifier = Modifier.fillMaxWidth()) { Text(candidate) }
      }
      item { Text("Android内ローカルモデル", modifier = Modifier.padding(top = 8.dp)) }
      if (filteredLocal.isEmpty()) item { Text("Android内に取得済みのモデルなし") }
      items(filteredLocal, key = { "local:$it" }) { candidate ->
        Button(onClick = { model = candidate }, modifier = Modifier.fillMaxWidth()) { Text(candidate) }
      }
    }
    OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("プロンプト") })
    OutlinedTextField(system, { system = it }, Modifier.fillMaxWidth(), label = { Text("システムプロンプト（任意）") })
    OutlinedTextField(resultVariable, { resultVariable = it }, Modifier.fillMaxWidth(), label = { Text("結果を入れるMacroDroid変数名（任意）") }, singleLine = true)
    Text("実行先")
    Row {
      listOf("auto" to "自動", "local" to "ローカル", "cloud" to "Cloud").forEach { (value, label) ->
        Row {
          RadioButton(selected = mode == value, onClick = { mode = value })
          Text(label, Modifier.padding(top = 12.dp, end = 8.dp))
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(onClick = onCancel) { Text("キャンセル") }
      Button(onClick = { onSave(model, prompt, system, mode, resultVariable) }, enabled = model.isNotBlank() && prompt.isNotBlank()) {
        Text("保存")
      }
    }
  }
}
