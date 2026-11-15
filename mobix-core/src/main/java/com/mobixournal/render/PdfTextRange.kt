package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page

/**
 * Which PDF **sheet** a page draws: the source's own identity plus the sheet's 0-based [pageNo].
 *
 * A text selection names words of one sheet, and a notebook page's position is not that sheet: pages can be
 * reordered, two pages can show the same sheet, and a paper page in the same document shows none. Reading a
 * page's words through its own `Background.Pdf.pageNo` is what keeps a selection on the sheet the reader sees
 * (`PdfSheetRefTest`); the position was only ever a coincidence of an unreordered import.
 *
 * [source] is stable for the lifetime of the open document: the bundled asset's entry name when the PDF travels
 * inside a `.mobix`, else the file path or URI, and the reference's domain — so two different files that both
 * happen to show a third page are different sheets.
 */
data class PdfSheetRef(val source: String, val pageNo: Int) {

    companion object {
        /** The sheet [page] draws, or null when the page is not a PDF page at all. */
        fun of(page: Page): PdfSheetRef? {
            val pdf = page.background as? Background.Pdf ?: return null
            val source = listOfNotNull(pdf.bundledAsset, pdf.filename, pdf.domain).joinToString("|")
            return PdfSheetRef(source, pdf.pageNo)
        }
    }
}

/**
 * A transient PDF text range: the sheet it lives on, the word the press grabbed ([anchor]) and the word under
 * the pointer now ([focus]) — two indices into that sheet's own reading order, never into the document.
 *
 * Keeping both endpoints is what makes a drag behave like native selection: [anchor] is the fixed end, so
 * dragging back over it reverses [start]/[end] without moving the grab, and a reader who crosses their starting
 * word keeps it as the end that stays put. A range whose sheet changes is not extended at all — the range's
 * words belong to one sheet, and extending into another would select a passage the reader never drew over.
 *
 * Selection is reading, so this is view state: it never enters a page, an undo step or either file format.
 */
data class PdfTextRange(val sheet: PdfSheetRef, val anchor: Int, val focus: Int) {

    /** The range in reading order — the inclusive pair [PdfTextIndex.rangeBoxes] takes. */
    val start: Int get() = minOf(anchor, focus)
    val end: Int get() = maxOf(anchor, focus)

    /** True while the pointer has crossed the word the press grabbed (a backwards drag). */
    val reversed: Boolean get() = focus < anchor

    /** How many words the range covers. */
    val wordCount: Int get() = end - start + 1

    /**
     * Move the moving end to [word], keeping the grab. A [sheet] other than [sheet] is refused, so a pointer
     * that drifted onto another sheet leaves the range where it was.
     */
    fun focusOn(sheet: PdfSheetRef, word: Int): PdfTextRange =
        if (sheet == this.sheet) copy(focus = word) else this

    /** Grab the other end (a selection handle): the word under the pointer becomes the fixed end. */
    fun anchorOn(sheet: PdfSheetRef, word: Int): PdfTextRange =
        if (sheet == this.sheet) copy(anchor = word) else this
}

/**
 * The two pure steps of a direct text-selection gesture, beside [PdfTextIndex]'s own queries.
 *
 * The asymmetry is deliberate and is what the gesture needs: **entering** is strict ([PdfTextIndex.hitWord]) so
 * a press on whitespace starts nothing rather than grabbing the nearest word from across the page, while
 * **extending** uses the nearest word ([PdfTextIndex.anchorWord]) so dragging over the gap between two words —
 * or past the end of a line — keeps the range alive. Both answer from the already-extracted [PdfTextIndex] that
 * the shells cache per file, so no gesture ever reads the PDF again.
 */
object PdfTextSelection {

    /**
     * Begin at a pointer position: a one-word range when ([x], [y]) hits a word inside its box or within
     * [tolerancePt] of it, or null when the point is whitespace. [tolerancePt] comes from
     * [PdfHitTolerance.ofScreenPx], so the press radius is a screen-space quantity, bounded in page points.
     */
    fun begin(
        index: PdfTextIndex,
        sheet: PdfSheetRef,
        x: Double,
        y: Double,
        tolerancePt: Double,
    ): PdfTextRange? {
        val word = index.hitWord(sheet.pageNo, x, y, tolerancePt) ?: return null
        return PdfTextRange(sheet, word, word)
    }

    /**
     * Extend [range] to the word nearest ([x], [y]) on [sheet]. A press on another sheet — a reordered page, a
     * second pane's page — is refused and the range is returned unchanged.
     */
    fun extend(index: PdfTextIndex, range: PdfTextRange, sheet: PdfSheetRef, x: Double, y: Double): PdfTextRange {
        if (sheet != range.sheet) return range
        val word = index.anchorWord(sheet.pageNo, x, y) ?: return range
        return range.focusOn(sheet, word)
    }

    /** The word boxes [range] covers, in reading order — what an overlay washes. */
    fun boxes(index: PdfTextIndex, range: PdfTextRange): List<PdfWord> =
        index.rangeBoxes(range.sheet.pageNo, range.anchor, range.focus)

    /** The selected text, words joined with single spaces — what a Copy puts on the clipboard. */
    fun text(index: PdfTextIndex, range: PdfTextRange): String =
        index.rangeText(range.sheet.pageNo, range.anchor, range.focus)
}
