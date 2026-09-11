package com.example.ollamataskerbridge.bridge

import android.content.Context

/**
 * Execution state used to prevent duplicate completion/retry.
 *
 * The state is also persisted in the existing connection_settings preferences so a
 * JobScheduler callback after process recreation cannot run an already completed job.
 */
object InferenceExecutionRegistry {
  enum class State { PENDING, QUEUED, RUNNING, COMPLETED, CANCELLED, OBSOLETE }
  data class Snapshot(val state: State, val signalFinished: Boolean, val retryCount: Int, val createdAt: Long, val lastRetryAt: Long)

  private data class Entry(
    var state: State,
    var signalFinished: Boolean = false,
    var retryCount: Int = 0,
    var createdAt: Long = System.currentTimeMillis(),
    var lastRetryAt: Long = 0L,
  )
  private val lock = Any()
  private val entries = mutableMapOf<String, Entry>()
  private var preferences: android.content.SharedPreferences? = null

  fun initialize(context: Context) {
    synchronized(lock) {
      if (preferences == null) {
        preferences = context.applicationContext.getSharedPreferences("connection_settings", Context.MODE_PRIVATE)
      }
    }
  }

  fun register(executionId: String, createdAt: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
    if (entries.containsKey(executionId) || persisted(executionId) != null) return@synchronized false
    entries[executionId] = Entry(State.PENDING, createdAt = createdAt)
    persist(executionId, entries.getValue(executionId))
    true
  }

  fun snapshot(executionId: String): Snapshot? = synchronized(lock) {
    entries[executionId]?.snapshot() ?: persisted(executionId)?.also { entries[executionId] = it }?.snapshot()
  }

  fun markQueued(executionId: String) = update(executionId) { it.state = State.QUEUED }
  fun markRunning(executionId: String) = update(executionId) { it.state = State.RUNNING }
  fun markPending(executionId: String) = update(executionId) { it.state = State.PENDING }
  fun markCompleted(executionId: String) = update(executionId) { it.state = State.COMPLETED }
  fun markCancelled(executionId: String) = update(executionId) { it.state = State.CANCELLED }
  fun markObsolete(executionId: String) = update(executionId) { it.state = State.OBSOLETE }

  /** Atomically reserves one retry. The caller must cancel the current coroutine afterwards. */
  fun reserveRetry(executionId: String, now: Long, maxRetries: Int, maxAgeMs: Long, minIntervalMs: Long): Boolean = synchronized(lock) {
    val entry = entries[executionId] ?: persisted(executionId)?.also { entries[executionId] = it } ?: return@synchronized false
    val expired = entry.createdAt <= 0L || now - entry.createdAt > maxAgeMs
    if (entry.state == State.COMPLETED || entry.state == State.CANCELLED || entry.state == State.OBSOLETE || entry.signalFinished || expired || entry.retryCount >= maxRetries || (entry.lastRetryAt > 0L && now - entry.lastRetryAt < minIntervalMs)) {
      if (expired && entry.state != State.COMPLETED && entry.state != State.CANCELLED) entry.state = State.OBSOLETE
      persist(executionId, entry)
      return@synchronized false
    }
    entry.retryCount += 1
    entry.lastRetryAt = now
    entry.state = State.PENDING
    persist(executionId, entry)
    true
  }

  fun markSignalFinished(executionId: String): Boolean {
    return synchronized(lock) {
      val entry = entries[executionId] ?: return true
      if (entry.signalFinished) return false
      entry.signalFinished = true
      persist(executionId, entry)
      true
    }
  }

  fun canRetry(executionId: String): Boolean = synchronized(lock) {
    val entry = entries[executionId] ?: persisted(executionId)
    when (entry?.state) {
      State.COMPLETED, State.CANCELLED, State.OBSOLETE -> false
      else -> entry?.signalFinished != true
    }
  }

  private fun update(executionId: String, change: (Entry) -> Unit) {
    synchronized(lock) {
      val entry = entries[executionId] ?: persisted(executionId)?.also { entries[executionId] = it } ?: return
      change(entry)
      persist(executionId, entry)
    }
  }

  private fun persisted(executionId: String): Entry? {
    val prefs = preferences ?: return null
    val state = prefs.getString(stateKey(executionId), null)?.let { runCatching { State.valueOf(it) }.getOrNull() } ?: return null
    return Entry(
      state = state,
      signalFinished = prefs.getBoolean(signalKey(executionId), false),
      retryCount = prefs.getInt(retryCountKey(executionId), 0),
      createdAt = prefs.getLong(createdAtKey(executionId), 0L),
      lastRetryAt = prefs.getLong(lastRetryAtKey(executionId), 0L),
    )
  }

  private fun persist(executionId: String, entry: Entry) {
    preferences?.edit()
      ?.putString(stateKey(executionId), entry.state.name)
      ?.putBoolean(signalKey(executionId), entry.signalFinished)
      ?.putInt(retryCountKey(executionId), entry.retryCount)
      ?.putLong(createdAtKey(executionId), entry.createdAt)
      ?.putLong(lastRetryAtKey(executionId), entry.lastRetryAt)
      ?.apply()
  }

  private fun stateKey(executionId: String) = "inference_execution_state_$executionId"
  private fun signalKey(executionId: String) = "inference_execution_signal_$executionId"
  private fun retryCountKey(executionId: String) = "inference_execution_retry_count_$executionId"
  private fun createdAtKey(executionId: String) = "inference_execution_created_at_$executionId"
  private fun lastRetryAtKey(executionId: String) = "inference_execution_last_retry_at_$executionId"
  private fun Entry.snapshot() = Snapshot(state, signalFinished, retryCount, createdAt, lastRetryAt)
}
