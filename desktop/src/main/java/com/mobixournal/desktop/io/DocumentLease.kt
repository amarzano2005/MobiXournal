package com.mobixournal.desktop.io

import java.util.WeakHashMap
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
