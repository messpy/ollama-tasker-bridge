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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// LiteRT-LM adapter. .litertlm is separate from llama.cpp GGUF files.
object LiteRtLmInferenceBridge {
  private val mutex = Mutex()
  private var loadedPath: String? = null
  private var loadedSystem: String? = null
  private var loadedTemperature: Float = -1f
  private var loadedContextTokens: Int = 0
  private var loadedVision: Boolean = false
  private var engine: Engine? = null
  private var conversation: Conversation? = null

  fun generate(context: Context, model: String, prompt: String, system: String?, maxTokens: Int, temperature: Float, imageBytes: ByteArray? = null): Flow<GenerateEvent> = flow {
    InferenceNotification.start(context, model)
    try {
      mutex.withLock {
        val file = LocalModelStore(context).liteRtFileFor(model)
        require(file.isFile) { "LiteRT-LMモデル未取得です: $model" }
        val normalizedSystem = system?.takeIf { it.isNotBlank() }
        val wantsVision = imageBytes != null
        val contextTokens = SettingsStore(context).liteRtContextTokens
        val normalizedTemperature = temperature.coerceIn(0f, 2f)

        // Engine initialization is expensive for large models. Rebuild it only when
        // engine-level settings change. maxTokens is an output limit from the common
        // request contract; LiteRT-LM's EngineConfig.maxNumTokens is context capacity,
        // so changing maxTokens must not force a model reload.
        val engineChanged = engine == null ||
          loadedPath != file.absolutePath ||
          loadedContextTokens != contextTokens ||
          loadedVision != wantsVision

        if (engineChanged) {
          closeLocked()
          // Gemma 3n's vision encoder requires the GPU vision backend even when
          // the language decoder itself runs on the CPU. Gallery uses the same
          // split backend configuration; omitting it causes a native null
          // dereference when an image is supplied on Pixel devices.
          val newEngine = Engine(EngineConfig(
            modelPath = file.absolutePath,
            backend = Backend.CPU(),
            visionBackend = if (wantsVision) Backend.GPU() else null,
            maxNumTokens = contextTokens,
          ))
          newEngine.initialize()
          engine = newEngine
          loadedPath = file.absolutePath
          loadedContextTokens = contextTokens
          loadedVision = wantsVision
        }

        // System instructions and sampler settings belong to ConversationConfig.
        // Recreate only the conversation when they change so the loaded model stays hot.
        if (conversation == null || engineChanged || loadedSystem != normalizedSystem || loadedTemperature != normalizedTemperature) {
          closeConversationLocked()
          conversation = requireNotNull(engine).createConversation(
            ConversationConfig(
              systemInstruction = normalizedSystem?.let { Contents.of(it) } ?: Contents.of(""),
              samplerConfig = SamplerConfig(
                topK = 20,
                topP = 0.95,
                temperature = normalizedTemperature.toDouble(),
                seed = 0,
              ),
            ),
          )
          loadedSystem = normalizedSystem
          loadedTemperature = normalizedTemperature
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

  private fun closeConversationLocked() {
    runCatching { conversation?.close() }
    conversation = null
    loadedSystem = null
    loadedTemperature = -1f
  }

  private fun closeLocked() {
    closeConversationLocked()
    runCatching { engine?.close() }
    engine = null
    loadedPath = null
    loadedContextTokens = 0
    loadedVision = false
  }
}
