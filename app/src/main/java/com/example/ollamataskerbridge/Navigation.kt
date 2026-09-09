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
import com.example.ollamataskerbridge.ui.chat.ChatScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var selectedChat by remember { mutableStateOf(false) }
  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        Text("Ollama Tasker Bridge", modifier = Modifier.padding(20.dp))
        NavigationDrawerItem(label = { Text("接続・モデル管理") }, selected = !selectedChat, onClick = { selectedChat = false; scope.launch { drawerState.close() }; backStack.removeAll { it != Main } })
        NavigationDrawerItem(label = { Text("テストチャット") }, selected = selectedChat, onClick = { selectedChat = true; scope.launch { drawerState.close() }; if (backStack.lastOrNull() != Chat) backStack.add(Chat) })
      }
    },
  ) {

    NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> { MainScreen(modifier = Modifier.safeDrawingPadding(), onOpenChat = { selectedChat = true; backStack.add(Chat) }) }
        entry<Chat> { ChatScreen(modifier = Modifier.safeDrawingPadding()) }
      },
  )
  }
}
