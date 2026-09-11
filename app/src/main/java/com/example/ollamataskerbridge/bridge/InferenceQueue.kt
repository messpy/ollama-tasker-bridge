package com.example.ollamataskerbridge.bridge

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes model execution so separate MacroDroid/Tasker requests cannot run concurrently. */
object InferenceQueue {
  private val mutex = Mutex()

  suspend fun <T> withSlot(block: suspend () -> T): T = mutex.withLock { block() }
}
