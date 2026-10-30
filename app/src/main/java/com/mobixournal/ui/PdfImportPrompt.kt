package com.mobixournal.ui

import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges
import java.io.File

/**
 * The question the tablet asks **after** it has read a picked PDF, the way the window asks it: which of
 * its pages, and where do they go.
 *
 * The old tablet flow chose the mode before the picker and then brought the whole file, so a chapter out
 * of a four-hundred-page scan was a four-hundred-page document to scroll through. Reading the page count
 * first is what makes the range a real question: the card can say how many pages there are, and the
 * import brings only the ones typed.
 *
 * [file] is the staged copy (the picker's URI may be on a network share, so the bytes are fetched once),
 * and [reference] is the URI the pages record as their background's `filename` — the string a later save
 * reopens the PDF through.
 */
data class PdfImportPrompt(
    val file: File,
    /** What the pages carry as the background's `filename` — the picked document's URI. */
    val reference: String,
    /** The picked PDF's display name, so the card can name the file it is about. */
    val name: String,
    /** How many pages the file holds — the card's readout. */
    val pageCount: Int,
    /** The answer being considered. Append is the default: it keeps the work. */
    val mode: ImportPdfMode = ImportPdfMode.APPEND,
    /** The first and last page to bring, as typed (1-based); the whole PDF until told otherwise. */
    val first: String = "1",
    val last: String = pageCount.toString(),
) {
    /** The run this card asks for, or the sentence saying why it cannot be used. */
    val range: PageRangePlan get() = PageRanges.pages(PageRangeRequest(first, last), pageCount)

    /** The run as 0-based indices, or null while the fields cannot be used. */
    val indices: IntRange?
        get() = (range as? PageRangePlan.Pages)?.let { it.first..it.last }
}
