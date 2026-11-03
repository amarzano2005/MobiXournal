package com.mobixournal.ui.entry

import android.content.Context
import android.net.Uri

/**
 * A notebook the app opened lately, as the tablet can know one: a **URI**, not a path.
 *
 * The desktop's recents are `File`s; on Android the document lives wherever the Storage Access
 * Framework put it and its identity is the content URI. The grant that lets us read it again is kept
 * at open time (`DocumentIo.persist`), so a card that is tapped months later still opens.
 */
data class RecentNotebook(val uri: String, val name: String)

/**
 * The recents list's rules, pure and free of Android so they are a JVM test rather than a tap.
 *
 * One entry per document (the same notebook opened twice is one card, most recent first), at most
 * [LIMIT] of them — the desktop's six, because a short list is one somebody reads.
 */
object RecentNotebooks {

    const val LIMIT = 6

    /** Rows are separated by a record separator and fields by a unit separator: neither can appear in a name. */
    private const val ROW = '\u001E'
    private const val FIELD = '\u001F'

    /** [existing] with [entry] in front, de-duplicated by URI and cut to [LIMIT]. */
    fun record(existing: List<RecentNotebook>, entry: RecentNotebook): List<RecentNotebook> =
        (listOf(entry.cleaned()) + existing.filterNot { it.uri == entry.uri })
            .take(LIMIT)

    /** [existing] without the notebook at [uri] — a file that has gone leaves no card. */
    fun forget(existing: List<RecentNotebook>, uri: String): List<RecentNotebook> =
        existing.filterNot { it.uri == uri }

    /** [list] as one line of text. Names are cleaned here as well as in [record]: a caller may hand over
     *  anything, and a name carrying a separator would come back as two cards. */
    fun encode(list: List<RecentNotebook>): String =
        list.map { it.cleaned() }.joinToString(ROW.toString()) { "${it.uri}$FIELD${it.name}" }

    /** The list [raw] holds, or an empty one for nothing, a blank string, or a row this build cannot read. */
    fun decode(raw: String?): List<RecentNotebook> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ROW).mapNotNull { row ->
            val at = row.indexOf(FIELD)
            if (at <= 0) return@mapNotNull null
            val uri = row.substring(0, at)
            RecentNotebook(uri, row.substring(at + 1).ifBlank { uri })
        }.take(LIMIT)
    }

    /** A name never carries the two separators: a notebook called "a\u001Eb" is not two cards. */
    private fun RecentNotebook.cleaned() =
        RecentNotebook(uri, name.replace(ROW.toString(), " ").replace(FIELD.toString(), " "))
}

/**
 * Where the recents live: its own preferences file, beside the settings but not inside them.
 *
 * It is device state rather than a preference — nobody chose it, and losing it costs a card, not a
 * setting — which is why it does not widen `AppSettings` and its store.
 */
class RecentNotebookStore(context: Context) {

    private val prefs = context.getSharedPreferences("entry_recents", Context.MODE_PRIVATE)

    fun list(): List<RecentNotebook> = RecentNotebooks.decode(prefs.getString(KEY, null))

    /** Remember [uri] as the notebook called [name] and return the list that results. */
    fun record(uri: Uri, name: String): List<RecentNotebook> {
        val next = RecentNotebooks.record(list(), RecentNotebook(uri.toString(), name))
        prefs.edit().putString(KEY, RecentNotebooks.encode(next)).apply()
        return next
    }

    /** Drop [uri] — a card whose document can no longer be read. */
    fun forget(uri: String): List<RecentNotebook> {
        val next = RecentNotebooks.forget(list(), uri)
        prefs.edit().putString(KEY, RecentNotebooks.encode(next)).apply()
        return next
    }

    private companion object {
        const val KEY = "recents"
    }
}
