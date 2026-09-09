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
  val backStack = rememberNavBackStack(Main)
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var selectedSection by remember { mutableStateOf(MainSection.SETTINGS) }
  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        Text("Ollama Tasker Bridge", modifier = Modifier.padding(20.dp))
        NavigationDrawerItem(label = { Text("接続・モデル管理") }, selected = backStack.lastOrNull() == Main, onClick = { selectedSection = MainSection.SETTINGS; scope.launch { drawerState.close() }; backStack.removeAll { it != Main } })
        NavigationDrawerItem(label = { Text("モデル管理・ダウンロード") }, selected = selectedSection == MainSection.MODELS, onClick = { selectedSection = MainSection.MODELS; scope.launch { drawerState.close() }; backStack.removeAll { it != Main }; backStack.add(Models) })
        NavigationDrawerItem(label = { Text("システムプロンプト") }, selected = selectedSection == MainSection.PROMPTS, onClick = { selectedSection = MainSection.PROMPTS; scope.launch { drawerState.close() }; backStack.removeAll { it != Main }; backStack.add(Prompts) })
        NavigationDrawerItem(label = { Text("テストチャット") }, selected = backStack.lastOrNull() == Chat, onClick = { selectedSection = MainSection.SETTINGS; scope.launch { drawerState.close() }; backStack.removeAll { it != Main }; backStack.add(Chat) })
      }
    },
  ) {

    NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> { MainScreen(section = MainSection.SETTINGS, onOpenDrawer = { scope.launch { drawerState.open() } }, modifier = Modifier.safeDrawingPadding(), onOpenChat = { selectedSection = MainSection.SETTINGS; backStack.add(Chat) }) }
        entry<Models> { MainScreen(section = MainSection.MODELS, onOpenDrawer = { scope.launch { drawerState.open() } }, modifier = Modifier.safeDrawingPadding()) }
        entry<Prompts> { MainScreen(section = MainSection.PROMPTS, onOpenDrawer = { scope.launch { drawerState.open() } }, modifier = Modifier.safeDrawingPadding()) }
        entry<Chat> { ChatScreen(modifier = Modifier.safeDrawingPadding()) }
      },
  )
  }
}
