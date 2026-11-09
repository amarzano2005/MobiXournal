package com.mobixournal.audio

import com.mobixournal.format.mobix.MobixPayload
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Registering a page's recording performs no I/O. Only a worker resolves its independent payload. */
internal class DeferredRecordings(private val local: (String) -> File,
    private val adopt: (String, java.io.InputStream) -> Unit) {
    private class Entry(val token: String, val payload: MobixPayload) { @Volatile var ready = false }
    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Any>()

    fun register(name: String, token: String, payload: MobixPayload) {
        entries.compute(name) { _, old -> if (old?.token == token) old else Entry(token, payload) }
    }
    fun known(name: String): Boolean = entries.containsKey(name) || local(name).isFile

    fun resolve(name: String): File? {
        synchronized(locks.computeIfAbsent(name) { Any() }) {
            val entry = entries[name]
            if (entry != null) {
                if (!entry.ready || !local(name).isFile) {
                    entry.payload.open().use { adopt(name, it) }
                    entry.ready = true
                }
            }
        }
        return local(name).takeIf(File::isFile)
    }
}
