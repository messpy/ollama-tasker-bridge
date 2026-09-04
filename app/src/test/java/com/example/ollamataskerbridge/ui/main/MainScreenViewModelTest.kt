package com.example.ollamataskerbridge.ui.main

import junit.framework.TestCase.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_defaultsToLocalOllamaEndpoint() {
    val state = MainScreenUiState("https://ollama.com")
    assertEquals("https://ollama.com", state.endpoint)
    assertEquals(emptyList<com.example.ollamataskerbridge.data.OllamaModel>(), state.models)
  }
}
