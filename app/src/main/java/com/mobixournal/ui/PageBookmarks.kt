package com.mobixournal.ui

import android.content.Context

/**
 * **Page bookmarks** — a labelled, colour-coded flag on a page, and the list of them that makes a long
 * document navigable without a PDF outline.
 *
 * They are deliberately **app-side navigation state**: the `.xopp` format has no page name, label or
 * bookmark of any kind (desktop Xournal++ has none either), so writing one into the file would mean
 * inventing markup the desktop would ignore or strip. Per the project's scope rule the bookmark list
 * therefore lives beside the documents — persisted per document key in [BookmarkStore] — and never
 * touches the file, which keeps a bookmarked document byte-identical to an unbookmarked one.
 *
 * @param page 0-based page index in the document.
 * @param label What the bookmark says; empty means "use the automatic page label".
 * @param color ARGB colour of the bookmark's tab and label.
 */
data class PageBookmark(val page: Int, val label: String, val color: Int) {

    /** The label to show: [label] when the user set one, else [PageBookmarks.defaultLabel]. */
    fun displayLabel(): String = label.ifBlank { PageBookmarks.defaultLabel(page) }
}

/**
 * The bookmark model's pure half: the label/colour rules and the text codec the store persists.
 * Android-free apart from the store below, so the rules and the round trip are unit-tested on the JVM.
 */
object PageBookmarks {

    /** The label colours a new bookmark cycles through, so a page's flag is recognisable at a glance. */
    val COLORS: List<Int> = listOf(
        0xFFE57373.toInt(), // red
        0xFF64B5F6.toInt(), // blue
        0xFF81C784.toInt(), // green
        0xFFFFB74D.toInt(), // amber
        0xFFBA68C8.toInt(), // purple
        0xFF4DB6AC.toInt(), // teal
    )

    /** The label a bookmark gets when the user doesn't type one. */
    fun defaultLabel(page: Int): String = "Page ${page + 1}"

    /** The colour a *new* bookmark takes, cycling so consecutive bookmarks differ. */
    fun nextColor(existing: List<PageBookmark>): Int = COLORS[existing.size % COLORS.size]

    /** [bookmarks] with `page` bookmarked (replacing any bookmark already on it), in page order. */
    fun with(bookmarks: List<PageBookmark>, page: Int, label: String, color: Int): List<PageBookmark> =
        (bookmarks.filterNot { it.page == page } + PageBookmark(page, label.trim(), color))
            .sortedBy { it.page }

    /** [bookmarks] without the bookmark on [page]. */
    fun without(bookmarks: List<PageBookmark>, page: Int): List<PageBookmark> =
        bookmarks.filterNot { it.page == page }

    /**
     * [bookmarks] after an empty page was inserted **at** [page]: a bookmark on that page or later
     * moves down one, so every flag stays on the page it was put on.
     */
    fun insertedAt(bookmarks: List<PageBookmark>, page: Int): List<PageBookmark> =
        bookmarks.map { if (it.page >= page) it.copy(page = it.page + 1) else it }.sortedBy { it.page }

    /**
     * [bookmarks] after the page **at** [page] was deleted: its own bookmark goes with it, and later
     * flags move up one.
     */
    fun removedAt(bookmarks: List<PageBookmark>, page: Int): List<PageBookmark> =
        bookmarks
            .filterNot { it.page == page }
            .map { if (it.page > page) it.copy(page = it.page - 1) else it }
            .sortedBy { it.page }

    /**
     * The persisted form: one bookmark per line, `page<TAB>color<TAB>label`, with tabs, newlines and
     * backslashes escaped in the label so a label containing any of them still round-trips.
     */
    fun encode(bookmarks: List<PageBookmark>): String =
        bookmarks.joinToString("\n") { "${it.page}\t${it.color}\t${escape(it.label)}" }

    /** The inverse of [encode]; unparsable lines are dropped rather than failing the whole list. */
    fun decode(text: String?): List<PageBookmark> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split('\n').mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val page = parts[0].toIntOrNull() ?: return@mapNotNull null
            val color = parts[1].toIntOrNull() ?: return@mapNotNull null
            if (page < 0) return@mapNotNull null
            PageBookmark(page, unescape(parts.drop(2).joinToString("\t")), color)
        }.sortedBy { it.page }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> { out.append('\t'); i += 2 }
                    'n' -> { out.append('\n'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    else -> { out.append(c); i += 1 }
                }
            } else {
                out.append(c); i += 1
            }
        }
        return out.toString()
    }
}

/**
 * Where bookmarks are kept: one SharedPreferences entry per document key, holding that document's
 * whole list ([PageBookmarks.encode]). Keys are the tab's document key — the same identity the tab
 * strip uses — so a document's bookmarks come back with it when the session is restored.
 */
class BookmarkStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mobixournal_bookmarks", Context.MODE_PRIVATE)

    /** The bookmarks stored for [key], in page order; empty when it has none. */
    fun load(key: String?): List<PageBookmark> {
        if (key.isNullOrEmpty()) return emptyList()
        return PageBookmarks.decode(prefs.getString(entryKey(key), null))
    }

    /** Replace the bookmarks stored for [key] (an empty list drops the entry entirely). */
    fun save(key: String?, bookmarks: List<PageBookmark>) {
        if (key.isNullOrEmpty()) return
        prefs.edit().apply {
            if (bookmarks.isEmpty()) remove(entryKey(key)) else putString(entryKey(key), PageBookmarks.encode(bookmarks))
        }.apply()
    }

    private fun entryKey(key: String): String = "doc:$key"
}
