package com.example.ollamataskerbridge.ui.main

import junit.framework.TestCase.assertEquals
import com.example.ollamataskerbridge.data.ModelSource
import com.example.ollamataskerbridge.data.OllamaModel
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_defaultsToLocalOllamaEndpoint() {
    val state = MainScreenUiState("https://ollama.com")
    assertEquals("https://ollama.com", state.endpoint)
    assertEquals(emptyList<com.example.ollamataskerbridge.data.OllamaModel>(), state.models)
  }
  
  @Test
  fun catalogClassification_excludesInstalledAndSeparatesCloud() {
    val localDownload = OllamaModel("qwen3:8b", remote = false, downloadable = true, local = false, source = ModelSource.OLLAMA)
    val installed = localDownload.copy(local = true)
    val cloud = OllamaModel("gpt-oss:120b", remote = true, downloadable = false, local = false, source = ModelSource.OLLAMA)
    assertEquals(false, localDownload.local)
    assertEquals(true, installed.local)
    assertEquals(false, installed.isCloudOnly())
    assertEquals(false, localDownload.isCloudOnly())
    assertEquals(true, cloud.isCloudOnly())

  }
}
