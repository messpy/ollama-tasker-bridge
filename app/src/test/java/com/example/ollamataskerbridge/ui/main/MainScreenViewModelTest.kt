package com.example.ollamataskerbridge.ui.main

import junit.framework.TestCase.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_defaultsToLocalOllamaEndpoint() {
    val state = MainScreenUiState("http://127.0.0.1:11434")
    assertEquals("http://127.0.0.1:11434", state.endpoint)
    assertEquals(emptyList<com.example.ollamataskerbridge.data.OllamaModel>(), state.models)
  }
}
