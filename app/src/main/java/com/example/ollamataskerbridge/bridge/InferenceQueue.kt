package com.example.ollamataskerbridge.bridge

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** Serializes model execution so separate MacroDroid/Tasker requests cannot run concurrently. */
object InferenceQueue {
  private val mutex = Mutex()
  private val waiting = AtomicInteger(0)

  suspend fun <T> withSlot(block: suspend () -> T): T {
    waiting.incrementAndGet()
    return try {
      mutex.withLock { block() }
    } finally {
      waiting.decrementAndGet()
    }
  }

  fun queueSize(): Int = waiting.get()
}
