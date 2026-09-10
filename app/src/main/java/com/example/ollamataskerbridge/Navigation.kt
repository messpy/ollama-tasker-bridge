package com.example.ollamataskerbridge

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.ollamataskerbridge.ui.main.MainScreen
import com.example.ollamataskerbridge.ui.main.MainSection
import com.example.ollamataskerbridge.ui.chat.ChatScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Home)
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var selectedSection by remember { mutableStateOf(MainSection.SETTINGS) }
  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        Text("AI Model Bridge", modifier = Modifier.padding(20.dp))
        NavigationDrawerItem(label = { Text("目次") }, selected = backStack.lastOrNull() == Home, onClick = { scope.launch { drawerState.close() }; backStack.removeAll { it != Home } })
        NavigationDrawerItem(label = { Text("MacroDroid") }, selected = backStack.lastOrNull() == MacroDroid, onClick = { scope.launch { drawerState.close() }; backStack.removeAll { it != Home }; backStack.add(MacroDroid) })
        NavigationDrawerItem(label = { Text("テストチャット") }, selected = backStack.lastOrNull() == Chat, onClick = { selectedSection = MainSection.SETTINGS; scope.launch { drawerState.close() }; backStack.removeAll { it != Main }; backStack.add(Chat) })
        NavigationDrawerItem(label = { Text("モデル管理・ダウンロード") }, selected = selectedSection == MainSection.MODELS, onClick = { selectedSection = MainSection.MODELS; scope.launch { drawerState.close() }; backStack.removeAll { it != Main }; backStack.add(Models) })
        NavigationDrawerItem(label = { Text("システムプロンプト") }, selected = selectedSection == MainSection.PROMPTS, onClick = { selectedSection = MainSection.PROMPTS; scope.launch { drawerState.close() }; backStack.removeAll { it != Main }; backStack.add(Prompts) })
        NavigationDrawerItem(label = { Text("接続・API設定") }, selected = backStack.lastOrNull() == Main, onClick = { selectedSection = MainSection.SETTINGS; scope.launch { drawerState.close() }; backStack.removeAll { it != Main } })
      }
    },
  ) {

    NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
        entry<Home> { HomeScreen(onOpenDrawer = { scope.launch { drawerState.open() } }, onOpenSettings = { backStack.add(Main) }, onOpenModels = { backStack.add(Models) }, onOpenPrompts = { backStack.add(Prompts) }, onOpenChat = { backStack.add(Chat) }, modifier = Modifier.safeDrawingPadding()) }
        entry<Main> { MainScreen(section = MainSection.SETTINGS, onOpenDrawer = { scope.launch { drawerState.open() } }, modifier = Modifier.safeDrawingPadding(), onOpenChat = { selectedSection = MainSection.SETTINGS; backStack.add(Chat) }) }
        entry<Models> { MainScreen(section = MainSection.MODELS, onOpenDrawer = { scope.launch { drawerState.open() } }, modifier = Modifier.safeDrawingPadding()) }
        entry<Prompts> { MainScreen(section = MainSection.PROMPTS, onOpenDrawer = { scope.launch { drawerState.open() } }, modifier = Modifier.safeDrawingPadding()) }
        entry<MacroDroid> { MacroDroidGuideScreen(modifier = Modifier.safeDrawingPadding(), onOpenDrawer = { scope.launch { drawerState.open() } }) }
        entry<Chat> { ChatScreen(modifier = Modifier.safeDrawingPadding(), onOpenDrawer = { scope.launch { drawerState.open() } }) }
      },
  )
  }
}



@Composable
private fun HomeScreen(
  modifier: Modifier = Modifier,
  onOpenDrawer: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenModels: () -> Unit,
  onOpenPrompts: () -> Unit,
  onOpenChat: () -> Unit,
) {
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(Modifier.fillMaxWidth()) {
      androidx.compose.material3.TextButton(onClick = onOpenDrawer) { Text("☰") }
      Column(Modifier.padding(start = 8.dp)) {
        Text("AI Model Bridge", style = MaterialTheme.typography.headlineSmall)
        Text("目次", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
      }
    }
    Text("使いたい機能を選択してください", style = MaterialTheme.typography.bodyLarge)
    HomeItem("💬  テストチャット", "モデルとシステムプロンプトを選んで会話します", onOpenChat)
    HomeItem("📦  モデル管理・ダウンロード", "Ollama・Hugging Face・LiteRT-LMのモデルを管理します", onOpenModels)
    HomeItem("📝  システムプロンプト", "プリセットの追加・編集・削除を行います", onOpenPrompts)
    HomeItem("⚙️  接続・API設定", "Ollama接続先、APIキー、各サービスを設定します", onOpenSettings)
  }
}


@Composable
private fun HomeItem(title: String, description: String, onClick: () -> Unit) {
  Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
 private fun MacroDroidGuideScreen(modifier: Modifier = Modifier, onOpenDrawer: () -> Unit) { Column(modifier.padding(20.dp)) { androidx.compose.material3.TextButton(onClick = onOpenDrawer) { Text("☰ MacroDroid") }; Text("1つのマクロでLLMを実行。プロンプトは %prompt、結果は %answer です。"); Text("プラグイン設定で完了までブロックをON、タイムアウトを120秒にしてください。"); Text("次のアクションでは {lv=answer} を使えます。") } }
