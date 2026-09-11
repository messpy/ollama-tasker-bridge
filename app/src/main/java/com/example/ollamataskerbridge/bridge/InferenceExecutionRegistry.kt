package com.example.ollamataskerbridge.bridge

import java.util.concurrent.ConcurrentHashMap

/** In-process execution state used to prevent duplicate completion/retry. */
object InferenceExecutionRegistry {
  enum class State { PENDING, RUNNING, COMPLETED, CANCELLED, OBSOLETE }
  data class Snapshot(val state: State, val signalFinished: Boolean)

  private data class Entry(var state: State, var signalFinished: Boolean = false)
  private val entries = ConcurrentHashMap<String, Entry>()

  fun register(executionId: String): Boolean = entries.putIfAbsent(executionId, Entry(State.PENDING)) == null
  fun snapshot(executionId: String): Snapshot? = entries[executionId]?.let { Snapshot(it.state, it.signalFinished) }
  fun markRunning(executionId: String) { entries[executionId]?.state = State.RUNNING }
  fun markPending(executionId: String) { entries[executionId]?.state = State.PENDING }
  fun markCompleted(executionId: String) { entries[executionId]?.state = State.COMPLETED }
  fun markCancelled(executionId: String) { entries[executionId]?.state = State.CANCELLED }
  fun markObsolete(executionId: String) { entries[executionId]?.state = State.OBSOLETE }

  @Synchronized
  fun markSignalFinished(executionId: String): Boolean {
    val entry = entries[executionId] ?: return true
    if (entry.signalFinished) return false
    entry.signalFinished = true
    return true
  }

  fun canRetry(executionId: String): Boolean = when (snapshot(executionId)?.state) {
    State.COMPLETED, State.CANCELLED, State.OBSOLETE -> false
    else -> snapshot(executionId)?.signalFinished != true
  }
}
