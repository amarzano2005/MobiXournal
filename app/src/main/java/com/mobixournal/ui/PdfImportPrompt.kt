package com.mobixournal.ui

import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRanges
import com.mobixournal.render.PdfOutlineSeeding
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
 * **[pages] is a selection, not a run.** A chapter is often pages 2, 4–6 and 9, and naming it used to take
 * two fields that could only mean one continuous stretch; the card now takes the reader's own grammar
 * (`2,4-6,9`) and [PageRanges.selection] decides what it means. The answer stays in the request rather
 * than in the card, so picking *Add* and then fixing a page number never loses the answer.
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
    /** The pages to bring, as typed (1-based); the whole PDF until told otherwise. */
    val pages: String = PageRanges.whole(pageCount),
    /**
     * Whether the reader explicitly adds a Contents row named after the file. The PDF chapters need no wrapper.
     */
    val bookmark: Boolean = false,
    /** The title that row carries — the file's name without its `.pdf`, and the reader's to change. */
    val bookmarkTitle: String = PdfOutlineSeeding.defaultTitle(name),
) {
    /** What the field names, or the sentence saying why it cannot be used. */
    val range: PageRangePlan get() = PageRanges.selection(pages, pageCount)

    /** The pages as 0-based source indices, in the file's own order, or null while the field is refused. */
    val chosen: List<Int>? get() = range.indices()

    /** How many pages the field names now — the card's count, 0 while it is refused. */
    val count: Int get() = chosen?.size ?: 0
}
