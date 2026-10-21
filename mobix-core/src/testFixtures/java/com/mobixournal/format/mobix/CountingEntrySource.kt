package com.mobixournal.format.mobix

import java.io.InputStream
import java.util.Collections

/**
 * A [MobixEntrySource] that counts how each entry was read — the measurement both shells' paging and
 * save tests need, and the reason they can live outside this module.
 *
 * It is a **fixture, not an API**: production code has no use for a source that remembers every read, so
 * it sits in `testFixtures` where the suites share one copy without the library promising it.
 *
 * Its two counters separate the two ways an entry can be read, which is exactly the distinction that
 * matters: [openCount] counts **inflations** (the JSON was decoded), [copyCount] counts **verbatim
 * copies** (bytes moved out of the source for a repack). A test asserting "this page was never touched"
 * wants both at zero; one asserting "this page was copied, not rebuilt" wants `copyCount > 0` and
 * `openCount == 0`.
 *
 * [rawEntry] is forwarded on purpose: a decorator that answers `null` there silently makes every repack
 * through it a full rewrite, which would hide the very behaviour these counters exist to prove.
 */
class CountingEntrySource(val delegate: MobixEntrySource) : MobixEntrySource {

    private val opens = Collections.synchronizedMap(mutableMapOf<String, Int>())
    private val copies = Collections.synchronizedMap(mutableMapOf<String, Int>())

    override val entryNames: Set<String> get() = delegate.entryNames

    override fun openEntry(name: String): InputStream? {
        opens[name] = (opens[name] ?: 0) + 1
        return delegate.openEntry(name)
    }

    override fun rawEntry(name: String): MobixRawEntry? =
        delegate.rawEntry(name)?.also { copies[name] = (copies[name] ?: 0) + 1 }

    /** How many times [name] was opened/inflated. */
    fun openCount(name: String): Int = opens[name] ?: 0

    /** How many times [name] was copied out verbatim, without being inflated. */
    fun copyCount(name: String): Int = copies[name] ?: 0

    /** How many distinct entries were read at all, either way. */
    val openedEntries: Int get() = entryNames.count { openCount(it) > 0 || copyCount(it) > 0 }

    override fun close() = delegate.close()
}
