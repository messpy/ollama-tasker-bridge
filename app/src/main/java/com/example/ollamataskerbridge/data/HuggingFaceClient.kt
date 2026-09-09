package com.example.ollamataskerbridge.data

class HuggingFaceClient {
  fun catalog(): List<OllamaModel> = listOf(
    model("qwen3-0.6b-q4_k_m", "bartowski/Qwen_Qwen3-0.6B-GGUF", "Qwen_Qwen3-0.6B-Q4_K_M.gguf", 523_000_000L),
    model("qwen3-1.7b-q4_k_m", "bartowski/Qwen_Qwen3-1.7B-GGUF", "Qwen_Qwen3-1.7B-Q4_K_M.gguf", 1_400_000_000L),
    model("qwen2.5-0.5b-instruct-q4_k_m", "bartowski/Qwen2.5-0.5B-Instruct-GGUF", "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf", 398_000_000L),
    model("qwen2.5-coder-0.5b-q4_k_m", "bartowski/Qwen2.5-Coder-0.5B-Instruct-GGUF", "Qwen2.5-Coder-0.5B-Instruct-Q4_K_M.gguf", 398_000_000L),
    model("gemma3-12b-it-q4_k_m", "bartowski/google_gemma-3-12b-it-GGUF", "google_gemma-3-12b-it-Q4_K_M.gguf", 7_300_000_000L),
    model("gemma3-4b-it-q4_k_m", "bartowski/google_gemma-3-4b-it-GGUF", "google_gemma-3-4b-it-Q4_K_M.gguf", 2_490_000_000L),
    model("gemma3-1b-it-q4_k_m", "bartowski/google_gemma-3-1b-it-GGUF", "google_gemma-3-1b-it-Q4_K_M.gguf", 806_000_000L),
    liteRt("gemma3-1b-it-litert", "litert-community/Gemma3-1B-IT", "gemma3-1b-it-int4.litertlm", 584_417_280L, "42d538a932e8d5b12e6b3b455f5572560bd60b2c"),
    liteRt("qwen2.5-1.5b-litert", "litert-community/Qwen2.5-1.5B-Instruct", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", 1_597_931_520L, "19edb84c69a0212f29a6ef17ba0d6f278b6a1614"),
    liteRt("gemma3n-e2b-it-litert", "google/gemma-3n-E2B-it-litert-lm", "gemma-3n-E2B-it-int4.litertlm", 3_655_827_456L, "ba9ca88da013b537b6ed38108be609b8db1c3a16"),
    // Gallery uses LiteRT-LM for Gemma; these GGUF variants use this app
    model("smollm2_135m", "bartowski/SmolLM2-135M-Instruct-GGUF", "SmolLM2-135M-Instruct-Q4_K_M.gguf", 105_000_000L),
    model("tinyllama_1.1b", "bartowski/TinyLlama-1.1B-Chat-v1.0-GGUF", "TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf", 638_000_000L)
  )
  private fun model(id: String, repo: String, file: String, size: Long) = OllamaModel(id, false, true, size, false, ModelSource.HUGGING_FACE, "https://huggingface.co/$repo/resolve/main/$file")
  private fun liteRt(id: String, repo: String, file: String, size: Long, commit: String) = OllamaModel(id, false, true, size, false, ModelSource.LITERT_LM, "https://huggingface.co/$repo/resolve/$commit/$file")
}
