package com.mobixournal.ui.entry

import android.content.Context
import android.net.Uri
import com.mobixournal.tabs.OpenTab
import com.mobixournal.tabs.TabSession

/**
 * A notebook the app opened lately, as the tablet can know one: a **URI**, not a path.
 *
 * The desktop's recents are `File`s; on Android the document lives wherever the Storage Access
 * Framework put it and its identity is the content URI. The grant that lets us read it again is kept
 * at open time (`DocumentIo.persist`), so a card that is tapped months later still opens.
 *
 * [sessionTab] is the other kind of card: a document the **last run left open**, whose edits never
 * reached its file. It carries a tab id rather than relying on the URI, because that tab's snapshot
 * is where the unsaved work lives — a tap restores it, and the badge says why.
 *
 * [splitTab] is the third: a run closed in **split view** is offered as **one** card, and this is the tab
 * the right-hand pane was showing. Tapping it brings back both halves, in their panes and on their pages,
 * with the split open again — which is the workspace the reader left, not one document from it.
 */
data class RecentNotebook(
    val uri: String,
    val name: String,
    val sessionTab: String? = null,
    val splitTab: String? = null,
) {
    /** True when this card is a document the last run left open and unsaved. */
    val fromPreviousSession: Boolean get() = sessionTab != null

    /** True when this card is the **split** the last run left open: two documents and their two panes. */
    val fromSplitSession: Boolean get() = splitTab != null
}

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
    fun encode(list: List<RecentNotebook>): String = list.map { it.cleaned() }.joinToString(ROW.toString()) {
        "${it.uri}$FIELD${it.name}$FIELD${it.sessionTab.orEmpty()}$FIELD${it.splitTab.orEmpty()}"
    }

    /** The list [raw] holds, or an empty one for nothing, a blank string, or a row this build cannot read. */
    fun decode(raw: String?): List<RecentNotebook> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ROW).mapNotNull { row ->
            val fields = row.split(FIELD)
            if (fields.size < 2 || fields[0].isEmpty()) return@mapNotNull null
            val uri = fields[0]
            RecentNotebook(
                uri,
                fields[1].ifBlank { uri },
                fields.getOrNull(2)?.ifBlank { null },
                // A list written by a build that knew no split cards has three fields: that is a card
                // without one, not an unreadable row.
                fields.getOrNull(3)?.ifBlank { null },
            )
        }.take(LIMIT)
    }

    /** A name never carries the two separators: a notebook called "a\u001Eb" is not two cards. */
    private fun RecentNotebook.cleaned() = RecentNotebook(
        uri,
        name.replace(ROW.toString(), " ").replace(FIELD.toString(), " "),
        sessionTab,
        splitTab,
    )
}

/**
 * What the entry screen draws: the workspace the last run left open, then the recents.
 *
 * A session card comes **first** because it is the newest work there is — a document with edits the
 * app never wrote to its file — and it carries its tab's id. A plain card the session already stands
 * for is left out: two cards for one document would ask the reader which copy they meant.
 *
 * A run closed in **split view** ([split] is the right pane's session) is offered as **one** card naming
 * both halves instead of one card per document: what the reader left is a workspace, and the way back to
 * it is a single tap that restores both panes. Its documents are not listed singly as well — that would
 * ask which half they meant, when the answer is "the two I was looking at".
 */
object EntryCards {

    /** The workspace as cards, followed by [recents] without the documents they represent. */
    fun of(session: TabSession?, split: TabSession?, recents: List<RecentNotebook>): List<RecentNotebook> {
        val tabs = session?.tabs.orEmpty()
        val left = session?.showing()
        val right = split?.showing()
        if (left != null && right != null) {
            val both = tabs + split?.tabs.orEmpty()
            val card = RecentNotebook(
                uri = left.uri.orEmpty(),
                name = "${left.title} · ${right.title}",
                sessionTab = left.id,
                splitTab = right.id,
            )
            // The card stands for the two documents **on screen**. Every other tab of either half is a
            // document the last run left open as well, and it keeps its own card: drawing the workspace as
            // one card must not make the notebooks behind it vanish from the screen, which is what a
            // card that swallowed every tab of both sessions did.
            val others = both
                .filterNot { it.id == left.id || it.id == right.id }
                .map { tab -> RecentNotebook(tab.uri.orEmpty(), tab.title, sessionTab = tab.id) }
            val claimed = both.mapNotNull { it.uri }.toSet()
            return listOf(card) + others + recents.filterNot { it.uri in claimed }
        }
        val claimed = tabs.mapNotNull { it.uri }.toSet()
        return tabs.map { RecentNotebook(it.uri.orEmpty(), it.title, sessionTab = it.id) } +
            recents.filterNot { it.uri in claimed }
    }

    /** The tab a session was left on, or its first when the stored index no longer names one. */
    private fun TabSession.showing(): OpenTab? = tabs.getOrNull(activeIndex) ?: tabs.firstOrNull()
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

    /**
     * Whether the last run was closed **in split view**. Its own flag rather than "the right pane has a
     * session": pane 1 keeps its cache after the split is closed, so the session alone would keep offering
     * a split that is no longer what the reader left.
     */
    fun splitOpen(): Boolean = prefs.getBoolean(KEY_SPLIT_OPEN, false)

    fun setSplitOpen(open: Boolean) {
        prefs.edit().putBoolean(KEY_SPLIT_OPEN, open).apply()
    }

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
        const val KEY_SPLIT_OPEN = "split_open"
    }
}
