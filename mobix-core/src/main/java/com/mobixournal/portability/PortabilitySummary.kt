package com.mobixournal.portability

/**
 * The **sentence a shell shows** about a document whose references are not all inside it, produced once
 * so the tablet and the window cannot describe the same file two ways.
 *
 * It is a value, not a Composable and not a string built in a view: `todo` calls this the report-to-copy
 * mapping, and keeping it here is what makes "1 part" versus "2 parts" and "nothing missing" testable on
 * the JVM in either shell (`PortabilitySummaryTest`), instead of being checked by reading two UI files
 * and hoping they agree.
 *
 * The copy rules are `docs/ui-modernization.md`'s: English, no internal class name in front of a person,
 * and it says **what happened and what to do** — a reference the document does not carry, and which page
 * it is on. Nothing is counted twice: the same picture behind four pages is one missing part listed once
 * with its first page, because a reader acts on a reference, not on a page.
 */
data class PortabilitySummary(
    /** How many **distinct** references are not inside the document. */
    val count: Int,

    /** The one sentence a status band shows: `2 parts are not inside this notebook.` */
    val headline: String,

    /** One line per missing reference, in page order: `Page 4 · PDF · notes.pdf`. */
    val lines: List<String>,
) {

    /** True when the document carries everything it names — nothing to tell the reader. */
    val isPortable: Boolean get() = count == 0

    /** Headline and references in one line, for a shell whose notice is a single sentence. */
    fun sentence(): String = if (isPortable) headline else "$headline ${lines.joinToString("; ")}"

    companion object {

        /**
         * The summary of [report] — one line per distinct missing reference, in the order the report
         * found them (page order), and the zero case stated rather than shown as an empty list.
         */
        fun of(report: PortabilityReport): PortabilitySummary {
            val grouped = report.byReference()
                .filterKeys { key -> report.entries.any { it.dedupeKey == key && it.status != PortabilityStatus.EMBEDDED } }
            val lines = grouped.values.map { occurrences -> line(occurrences.first()) }
            val count = lines.size
            return PortabilitySummary(count = count, headline = headline(count), lines = lines)
        }

        /** The sentence for [count] missing references — singular, plural and none, in one place. */
        fun headline(count: Int): String = when (count) {
            0 -> "Everything this notebook names is inside the file."
            1 -> "1 part is not inside this notebook."
            else -> "$count parts are not inside this notebook."
        }

        /** One reference's line: where it is, what kind of part it is, and what it names. */
        private fun line(entry: PortabilityEntry): String {
            val where = "Page ${entry.pageIndex + 1} · ${entry.kind.label} · ${entry.reference}"
            return when (entry.status) {
                PortabilityStatus.UNRECOVERABLE -> "$where — this one cannot be recovered"
                else -> where
            }
        }
    }
}
