package com.mobixournal.render

/**
 * The sheets a PDF export cannot print, said as a **sheet number** so the reader can relink and retry.
 *
 * A page over a PDF whose source is missing, unreadable, or short of that sheet used to export as blank
 * paper: a page that looks printed and carries nothing, discovered (if at all) after the file was
 * handed on. The export refuses instead. Sheet numbers are 1-based — what the page counter shows —
 * because that is the number the reader can find in the app.
 *
 * The message is the recovery, in English, and names the sheets rather than the failure: a page that
 * says *which* sheet is empty can be fixed by importing the PDF again, and one that silently is cannot.
 */
internal class MissingPdfBackgrounds(val sheets: List<Int>) : Exception(message(sheets)) {

    private companion object {
        fun message(sheets: List<Int>): String {
            val named = if (sheets.size == 1) "Page ${sheets.single()}" else "Pages ${sheets.joinToString(", ")}"
            return "$named ${if (sheets.size == 1) "has" else "have"} no PDF background this export can " +
                "read. Import the PDF again so the page has its source, then export once more."
        }
    }
}
