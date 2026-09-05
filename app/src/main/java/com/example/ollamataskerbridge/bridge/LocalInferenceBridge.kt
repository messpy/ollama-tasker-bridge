package com.example.ollamataskerbridge.bridge

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.example.ollamataskerbridge.data.LocalModelStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.ollamataskerbridge.data.OllamaClient
import com.example.ollamataskerbridge.data.SettingsStore

data class GenerateRequest(val backend: Backend, val model: String, val prompt: String, val systemPrompt: String? = null, val maxTokens: Int = 256, val temperature: Float = 0.7f)
enum class Backend { LOCAL, OLLAMA }

sealed interface GenerateEvent {
  data class Token(val text: String) : GenerateEvent
  data class Done(val fullText: String) : GenerateEvent
  data class Error(val message: String) : GenerateEvent
}

interface InferenceRepository {
  fun generate(context: Context, request: GenerateRequest): Flow<GenerateEvent>
}

object DefaultInferenceRepository : InferenceRepository {
  override fun generate(context: Context, request: GenerateRequest): Flow<GenerateEvent> = when (request.backend) {
    Backend.LOCAL -> LocalInferenceBridge.generate(context, request.model, request.prompt, request.systemPrompt, request.maxTokens, request.temperature)
    Backend.OLLAMA -> flow {
      try {
        val settings = SettingsStore(context)
        emit(GenerateEvent.Done(OllamaClient(settings.endpoint, settings.apiKey).generate(request.model, request.prompt, request.systemPrompt, request.maxTokens, request.temperature)))
      } catch (error: Exception) {
        emit(GenerateEvent.Error(error.message ?: "生成に失敗しました"))
      }
    }
  }

  suspend fun generateText(context: Context, request: GenerateRequest): String = generate(context, request).awaitFullText()
}

 suspend fun Flow<GenerateEvent>.awaitFullText(): String {
  var result = ""
  collect { event ->
    when (event) {
      is GenerateEvent.Token -> result += event.text
      is GenerateEvent.Done -> result = event.fullText
      is GenerateEvent.Error -> error(event.message)
    }
  }
  return result
}


object LocalInferenceBridge {
  private val mutex = Mutex()
  private var loadedPath: String? = null
  private var loadedSystemPrompt: String? = null

  fun generate(context: Context, model: String, prompt: String, system: String?, maxTokens: Int = 256, temperature: Float = 0.7f): Flow<GenerateEvent> = flow {
    try {
      mutex.withLock {
        val file = LocalModelStore(context).fileFor(model)
        require(file.isFile) { "モデル未取得です。先にPULLを実行してください: " + model }
        val engine = AiChat.getInferenceEngine(context)
        val state = engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.ModelReady || it is InferenceEngine.State.Error }
        if (state is InferenceEngine.State.Error) {
          engine.cleanUp()
          loadedPath = null
          loadedSystemPrompt = null
        }
        val normalizedSystem = system?.takeIf { it.isNotBlank() }
        val modelWasLoaded = loadedPath == file.absolutePath && engine.state.value is InferenceEngine.State.ModelReady
        val systemChanged = modelWasLoaded && loadedSystemPrompt != normalizedSystem
        if (!modelWasLoaded || systemChanged) {
          // Policy A: changing system prompt reloads the model and resets native conversation/KV state.
          if (engine.state.value is InferenceEngine.State.ModelReady) engine.cleanUp()
          engine.loadModel(file.absolutePath)
          loadedPath = file.absolutePath
          loadedSystemPrompt = normalizedSystem
          if (normalizedSystem != null) engine.setSystemPrompt(normalizedSystem)
        }
        val fullText = StringBuilder()
        engine.sendUserPrompt(prompt, maxTokens, temperature).collect { token ->
          fullText.append(token)
          emit(GenerateEvent.Token(token))
        }
        emit(GenerateEvent.Done(fullText.toString()))
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      loadedPath = null
      loadedSystemPrompt = null
      emit(GenerateEvent.Error(error.message ?: "生成に失敗しました"))
    }
  }
}
