package com.example.ollamataskerbridge.data

class HuggingFaceClient {
  fun catalog(): List<OllamaModel> = listOf(
    model("qwen3-0.6b-q4_k_m", "bartowski/Qwen_Qwen3-0.6B-GGUF", "Qwen_Qwen3-0.6B-Q4_K_M.gguf", 523_000_000L),
    model("qwen3-1.7b-q4_k_m", "bartowski/Qwen_Qwen3-1.7B-GGUF", "Qwen_Qwen3-1.7B-Q4_K_M.gguf", 1_400_000_000L),
    model("qwen2.5-0.5b-instruct-q4_k_m", "bartowski/Qwen2.5-0.5B-Instruct-GGUF", "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf", 398_000_000L),
    model("qwen2.5-coder-0.5b-q4_k_m", "bartowski/Qwen2.5-Coder-0.5B-Instruct-GGUF", "Qwen2.5-Coder-0.5B-Instruct-Q4_K_M.gguf", 398_000_000L),
    model("smollm2_135m", "bartowski/SmolLM2-135M-Instruct-GGUF", "SmolLM2-135M-Instruct-Q4_K_M.gguf", 105_000_000L),
    model("tinyllama_1.1b", "bartowski/TinyLlama-1.1B-Chat-v1.0-GGUF", "TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf", 638_000_000L)
  )
  private fun model(id: String, repo: String, file: String, size: Long) = OllamaModel(id, false, true, size, false, ModelSource.HUGGING_FACE, "https://huggingface.co/$repo/resolve/main/$file")
}
