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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ollamataskerbridge.MainActivity
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.OllamaModel
import com.example.ollamataskerbridge.data.SettingsStore
import com.example.ollamataskerbridge.data.SystemPromptPreset
import com.example.ollamataskerbridge.theme.MyApplicationTheme

class PluginSettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val initial = intent.extras?.getBundle(LocalePluginContract.EXTRA_BUNDLE)
    val settings = SettingsStore(this)
    val local = LocalModelStore(this).directory.listFiles().orEmpty()
      .filter { it.extension == "gguf" }
      .map { OllamaModel(it.nameWithoutExtension, false, true, it.length(), true) }
    val models = (settings.cachedModels() + local).distinctBy { it.name }
    setContent {
      MyApplicationTheme {
        PluginSettingsContent(
          initialModel = initial?.getString(LocalePluginContract.KEY_MODEL).orEmpty(),
          initialPrompt = initial?.getString(LocalePluginContract.KEY_PROMPT).orEmpty(),
          initialPresetId = initial?.getString(LocalePluginContract.KEY_PRESET_ID) ?: settings.lastPresetId,
          initialCustomSystem = initial?.getString(LocalePluginContract.KEY_CUSTOM_SYSTEM).orEmpty(),
          initialPlatform = initial?.getString(LocalePluginContract.KEY_PLATFORM) ?: settings.pluginPlatform,
          initialResultVariable = initial?.getString(LocalePluginContract.KEY_RESULT_VARIABLE).orEmpty(),
          models = models,
          presets = settings.presets(),
          onOpenApp = { startActivity(Intent(this@PluginSettingsActivity, MainActivity::class.java)) },
          onCancel = { setResult(Activity.RESULT_CANCELED); finish() },
          onSave = { model, prompt, presetId, customSystem, platform, resultVariable ->
            settings.pluginPlatform = platform
            if (presetId.isNotBlank() && presetId != "custom") settings.lastPresetId = presetId
            val values = Bundle().apply {
              putString(LocalePluginContract.KEY_MODEL, model)
              putString(LocalePluginContract.KEY_PROMPT, prompt)
              putString(LocalePluginContract.KEY_PRESET_ID, presetId)
              putString(LocalePluginContract.KEY_CUSTOM_SYSTEM, customSystem)
              putString(LocalePluginContract.KEY_PLATFORM, platform)
              putString(LocalePluginContract.KEY_RESULT_VARIABLE, normalizeResultVariable(resultVariable))
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(LocalePluginContract.EXTRA_BUNDLE, values)
              .putExtra(LocalePluginContract.EXTRA_STRING_BLURB, "$model / $platform"))
            finish()
          },
        )
      }
    }
  }

  private fun normalizeResultVariable(value: String) = value.trim()
    .removePrefix("{lv=").removePrefix("%").removeSuffix("}").trim()
}

@Composable
private fun PluginSettingsContent(
  initialModel: String,
  initialPrompt: String,
  initialPresetId: String,
  initialCustomSystem: String,
  initialPlatform: String,
  initialResultVariable: String,
  models: List<OllamaModel>,
  presets: List<SystemPromptPreset>,
  onOpenApp: () -> Unit,
  onCancel: () -> Unit,
  onSave: (String, String, String, String, String, String) -> Unit,
) {
  var platform by remember { mutableStateOf(initialPlatform.ifBlank { "tasker" }) }
  var model by remember { mutableStateOf(initialModel) }
  var prompt by remember { mutableStateOf(initialPrompt) }
  var query by remember { mutableStateOf("") }
  var localOnly by remember { mutableStateOf(false) }
  var presetId by remember { mutableStateOf(initialPresetId) }
  var customSystem by remember { mutableStateOf(initialCustomSystem) }
  var resultVariable by remember { mutableStateOf(initialResultVariable) }
  var presetMenu by remember { mutableStateOf(false) }
  val selectedPreset = presets.firstOrNull { it.id == presetId }
  val shown = models.filter { !localOnly || it.local }.filter { query.isBlank() || it.name.contains(query, true) }
  val platformName = if (platform == "macrodroid") "MacroDroid" else "Tasker"
  Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("$platformName アクション設定")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { platform = "tasker" }) { Text("Tasker") }
      OutlinedButton(onClick = { platform = "macrodroid" }) { Text("MacroDroid") }
    }
    Text("モデルの取得・APIキー・プリセット管理は本体アプリで行います。")
    Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) { Text("本体アプリを開く") }
    OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("モデル") }, singleLine = true)
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("本体のモデル一覧を検索") }, singleLine = true)
    Row { androidx.compose.material3.Checkbox(localOnly, { localOnly = it }); Text("ローカル取得済みのみ") }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      items(shown, key = { it.name }) { item ->
        Card(onClick = { model = item.name }, Modifier.fillMaxWidth()) {
          Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text(item.name); if (item.remote) Text("Cloud", style = androidx.compose.material3.MaterialTheme.typography.labelSmall); Text(if (item.sizeBytes > 0) "%.2f GB".format(item.sizeBytes / 1_000_000_000.0) else "サイズ不明", style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            Text(if (item.local) "✓" else "未取得")
          }
        }
      }
    }
    OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("プロンプト") }, minLines = 2)
    Text(if (platform == "macrodroid") "入力例: {lv=変数名}" else "入力例: %変数名")
    Row {
      OutlinedButton(onClick = { presetMenu = true }, modifier = Modifier.weight(1f)) { Text(selectedPreset?.name ?: "カスタム入力…") }
      DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
        presets.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { presetId = item.id; presetMenu = false }) }
        DropdownMenuItem(text = { Text("カスタム入力…") }, onClick = { presetId = "custom"; presetMenu = false })
      }
    }
    if (selectedPreset != null) Text(selectedPreset.body.take(200), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    if (presetId == "custom") OutlinedTextField(customSystem, { customSystem = it }, Modifier.fillMaxWidth(), label = { Text("システムプロンプト（カスタム）") }, minLines = 3)
    OutlinedTextField(resultVariable, { resultVariable = it }, Modifier.fillMaxWidth(), label = { Text("${platformName}変数名（結果）") }, singleLine = true)
    Text(if (platform == "macrodroid") "結果例: {lv=answer}" else "結果例: %answer")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick = onCancel) { Text("キャンセル") }; Button(onClick = { onSave(model.trim(), prompt, presetId, customSystem, platform, resultVariable) }, enabled = model.isNotBlank() && prompt.isNotBlank()) { Text("保存") } }
  }
}
