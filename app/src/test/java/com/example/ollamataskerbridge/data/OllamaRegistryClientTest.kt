package com.example.ollamataskerbridge.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OllamaRegistryClientTest {
  
  @Test
  fun catalogParser_supportsQuotedTaggedAndDuplicateLinks() {
    val html = "<a href=\"/library/qwen3:8b\">Qwen</a><a href=\u0027/library/llama3.2\u0027>Llama</a><a href=\"/library/qwen3:8b\">duplicate</a>"
    assertEquals(listOf("qwen3:8b", "llama3.2"), parseOllamaCatalogNames(html))
    assertEquals("qwen3.5:cloud", cloudCatalogModelName("qwen3.5"))
    assertEquals("qwen3.5:cloud", cloudCatalogModelName("qwen3.5:cloud"))
    assertEquals("library", ollamaCatalogPath("", false))
    assertEquals("search?q=qwen3%3A8b", ollamaCatalogPath("qwen3:8b", false))
    assertEquals("search?c=cloud&q=qwen3%3A8b", ollamaCatalogPath("qwen3:8b", true))
  }
}
