package com.mobixournal.concurrent

import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Serial automatic checkpoints: one running write and at most one replaceable pending request. */
class LatestWriteQueue<T>(private val executor: Executor, private val write: (T) -> Boolean) {
    private data class Request<T>(val generation: Long, val value: T)
    private val lock = Object()
    private var pending: Request<T>? = null
    private var running = false
    private var requested = 0L
    private var completed = 0L
    private var durable = 0L

    fun submit(value: T): Long = synchronized(lock) {
        val generation = ++requested
        pending = Request(generation, value)
        if (!running) {
            running = true
            executor.execute(::drain)
        }
        generation
    }

    fun awaitLatest(timeoutMs: Long): Boolean = synchronized(lock) {
        val target = requested
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (completed < target) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return false
            TimeUnit.NANOSECONDS.timedWait(lock, remaining)
        }
        durable >= target
    }

    private fun drain() {
        while (true) {
            val request = synchronized(lock) {
                val next = pending
                if (next == null) { running = false; lock.notifyAll(); return }
                pending = null
                next
            }
            val success = runCatching { write(request.value) }.getOrDefault(false)
            synchronized(lock) {
                completed = request.generation
                if (success) durable = request.generation
                lock.notifyAll()
            }
        }
    }
}
