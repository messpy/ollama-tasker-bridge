package com.example.ollamataskerbridge.bridge

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.example.ollamataskerbridge.data.LocalModelStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object LocalInferenceBridge {
  private val mutex = Mutex()
  private var loadedPath: String? = null

  suspend fun generate(context: Context, model: String, prompt: String, system: String?): String = mutex.withLock {
    val file = LocalModelStore(context).fileFor(model)
    require(file.isFile) { "モデル未取得です。先にPULLを実行してください: " + model }
    val engine = AiChat.getInferenceEngine(context)
    val state = engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.ModelReady || it is InferenceEngine.State.Error }
    if (state is InferenceEngine.State.Error) engine.cleanUp()
    if (loadedPath != file.absolutePath) {
      if (engine.state.value is InferenceEngine.State.ModelReady) engine.cleanUp()
      engine.loadModel(file.absolutePath)
      loadedPath = file.absolutePath
    }
    if (!system.isNullOrBlank()) engine.setSystemPrompt(system)
    buildString { engine.sendUserPrompt(prompt).collect { append(it) } }
  }
}
