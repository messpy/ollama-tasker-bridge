package com.example.ollamataskerbridge.ui.main

import junit.framework.TestCase.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_defaultsToLocalOllamaEndpoint() {
    val state = MainScreenUiState("http://100.122.68.52:42049")
    assertEquals("http://100.122.68.52:42049", state.endpoint)
    assertEquals(emptyList<com.example.ollamataskerbridge.data.OllamaModel>(), state.models)
  }
}
