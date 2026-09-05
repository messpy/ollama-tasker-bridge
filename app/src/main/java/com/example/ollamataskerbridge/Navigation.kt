package com.example.ollamataskerbridge

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.ollamataskerbridge.ui.main.MainScreen
import com.example.ollamataskerbridge.ui.chat.ChatScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> { MainScreen(modifier = Modifier.safeDrawingPadding(), onOpenChat = { backStack.add(Chat) }) }
        entry<Chat> { ChatScreen(modifier = Modifier.safeDrawingPadding()) }
      },
  )
}
