package com.mobixournal.desktop.io

import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

private val gates = WeakHashMap<DesktopDocument, ReentrantReadWriteLock>()
private fun gate(document: DesktopDocument): ReentrantReadWriteLock = synchronized(gates) {
    gates.getOrPut(document) { ReentrantReadWriteLock(true) }
}
internal fun <T> DesktopDocument.withReader(block: () -> T): T {
    val lock = gate(this).readLock()
    lock.lock()
    try { check(!isClosed) { "Document is closed" }; return block() } finally { lock.unlock() }
}
internal fun <T> DesktopDocument.withExclusive(block: () -> T): T {
    val lock = gate(this).writeLock()
    lock.lock()
    try { return block() } finally { lock.unlock() }
}

/**
 * [withExclusive] with a **deadline**: null when the lock did not come free within [timeoutMs].
 *
 * A save's commit is the one writer that must not wait for ever. The lock is taken for a whole page read by
 * whoever is painting, and a commit that simply queued behind it held the window's input gate down for as long
 * as that took — with nothing on screen saying why. A commit that gives up answers "busy", the save reports it
 * and can be asked again, and the gate is never left down.
 */
internal fun <T> DesktopDocument.tryExclusive(timeoutMs: Long, block: () -> T): T? {
    val lock = gate(this).writeLock()
    val taken = try {
        lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
    if (!taken) return null
    try { return block() } finally { lock.unlock() }
}
