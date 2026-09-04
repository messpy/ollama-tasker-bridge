package com.example.ollamataskerbridge.bridge

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.example.ollamataskerbridge.data.LocalModelStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.SettingsStore

data class GenerateRequest(val backend: Backend, val model: String, val prompt: String, val systemPrompt: String? = null, val maxTokens: Int = 256, val temperature: Float = 0.7f)
enum class Backend { LOCAL, OLLAMA }

interface InferenceRepository {
  suspend fun generate(context: Context, request: GenerateRequest): String
}

object DefaultInferenceRepository : InferenceRepository {
  override suspend fun generate(context: Context, request: GenerateRequest): String = when (request.backend) {
    Backend.LOCAL -> LocalInferenceBridge.generate(context, request.model, request.prompt, request.systemPrompt, request.maxTokens, request.temperature)
    Backend.OLLAMA -> { val settings = SettingsStore(context); OllamaClient(settings.endpoint, settings.apiKey).generate(request.model, request.prompt, request.systemPrompt, request.maxTokens, request.temperature) }
  }
}


object LocalInferenceBridge {
  private val mutex = Mutex()
  private var loadedPath: String? = null

  suspend fun generate(context: Context, model: String, prompt: String, system: String?, maxTokens: Int = 256, temperature: Float = 0.7f): String = mutex.withLock {
    val file = LocalModelStore(context).fileFor(model)
    require(file.isFile) { "モデル未取得です。先にPULLを実行してください: " + model }
    val engine = AiChat.getInferenceEngine(context)
    val state = engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.ModelReady || it is InferenceEngine.State.Error }
    if (state is InferenceEngine.State.Error) {
      engine.cleanUp()
      loadedPath = null
    }
    val modelWasLoaded = loadedPath == file.absolutePath && engine.state.value is InferenceEngine.State.ModelReady
    if (!modelWasLoaded) {
      if (engine.state.value is InferenceEngine.State.ModelReady) engine.cleanUp()
      engine.loadModel(file.absolutePath)
      loadedPath = file.absolutePath
      if (!system.isNullOrBlank()) engine.setSystemPrompt(system)
    }
    buildString { engine.sendUserPrompt(prompt, maxTokens, temperature).collect { append(it) } }
  }
}
