package com.example.ollamataskerbridge.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun screen_showsConnectionControls() {
    composeTestRule.setContent { MainScreen() }
    composeTestRule.onNodeWithText("Ollama Tasker Bridge").assertExists()
    composeTestRule.onNodeWithText("端末内モデル実行は準備中です").assertExists()
  }
}
