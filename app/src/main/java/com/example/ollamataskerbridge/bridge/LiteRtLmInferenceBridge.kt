package com.example.ollamataskerbridge.bridge

import android.content.Context
import com.example.ollamataskerbridge.data.LocalModelStore
import com.example.ollamataskerbridge.data.SettingsStore
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// LiteRT-LM adapter. .litertlm is separate from llama.cpp GGUF files.
object LiteRtLmInferenceBridge {
  private val mutex = Mutex()
  private var loadedPath: String? = null
  private var loadedSystem: String? = null
  private var loadedMaxTokens: Int = 0
  private var loadedTemperature: Float = -1f
  private var loadedContextTokens: Int = 0
  private var engine: Engine? = null
  private var conversation: Conversation? = null

  fun generate(context: Context, model: String, prompt: String, system: String?, maxTokens: Int, temperature: Float, imageBytes: ByteArray? = null): Flow<GenerateEvent> = flow {
    InferenceNotification.start(context, model)
    try {
      mutex.withLock {
        val file = LocalModelStore(context).liteRtFileFor(model)
        require(file.isFile) { "LiteRT-LMモデル未取得です: $model" }
        val normalizedSystem = system?.takeIf { it.isNotBlank() }
        if (loadedPath != file.absolutePath || loadedSystem != normalizedSystem || loadedMaxTokens != maxTokens || loadedTemperature != temperature || loadedContextTokens != SettingsStore(context).liteRtContextTokens) {
          // System instructions belong to ConversationConfig, so changing them creates a fresh conversation.
          closeLocked()
          // maxNumTokens is context capacity, not the UI output limit. Keep normal prompts and history above 256 tokens.
          val contextTokens = SettingsStore(context).liteRtContextTokens
          val newEngine = Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(), maxNumTokens = contextTokens))
          newEngine.initialize()
          engine = newEngine
          conversation = newEngine.createConversation(ConversationConfig(systemInstruction = normalizedSystem?.let { Contents.of(it) } ?: Contents.of(""), samplerConfig = SamplerConfig(topK = 20, topP = 0.95, temperature = temperature.coerceIn(0f, 2f).toDouble(), seed = 0)))
          loadedPath = file.absolutePath
          loadedSystem = normalizedSystem
          loadedMaxTokens = maxTokens
          loadedTemperature = temperature
          loadedContextTokens = contextTokens
        }
        val activeConversation = requireNotNull(conversation)
        val fullText = StringBuilder()
        val contents = imageBytes?.let { Contents.of(Content.ImageBytes(it), Content.Text(prompt)) } ?: Contents.of(prompt)
        activeConversation.sendMessageAsync(contents).collect { message ->
          message.contents.contents.filterIsInstance<Content.Text>().forEach { text ->
            if (text.text.isNotEmpty()) { fullText.append(text.text); emit(GenerateEvent.Token(text.text)) }
          }
        }
        emit(GenerateEvent.Done(fullText.toString()))
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      mutex.withLock { closeLocked() }
      emit(GenerateEvent.Error(error.message ?: "LiteRT-LM生成に失敗しました"))
    } finally {
      InferenceNotification.finish(context)
    }
  }.flowOn(Dispatchers.Default)

  private fun closeLocked() {
    runCatching { conversation?.close() }
    runCatching { engine?.close() }
    conversation = null
    engine = null
    loadedPath = null
    loadedSystem = null
    loadedMaxTokens = 0
    loadedTemperature = -1f
    loadedContextTokens = 0
  }
}
