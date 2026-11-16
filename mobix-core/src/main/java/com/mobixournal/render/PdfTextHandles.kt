package com.mobixournal.render

import com.mobixournal.design.DesignTokens
import kotlin.math.hypot

/**
 * Which end of a live PDF text range a handle stands for.
 *
 * The names are the **passage's** two ends in reading order, not the gesture's: a range dragged upwards has its
 * START handle at the top of the passage and its END handle at the bottom, and the reader can still grab either
 * of them. Crossing the other end reverses the range without swapping the handle in the hand — that is the
 * whole reason a handle names an end rather than a field of [PdfTextRange].
 */
enum class PdfTextHandle {
    /** The reading start of the passage — the first selected word's left edge. */
    START,

    /** The reading end of the passage — the last selected word's right edge. */
    END,
}

/**
 * One handle's dot: the end it belongs to, the word it moves, and its centre in **page-local points** (the
 * frame `PdfTextIndex`'s word boxes and the ink live in).
 *
 * A shell maps [x]/[y] through its own page box or viewport, so the dot follows a zoom and a scroll with the
 * sheet; the dot's own size is a screen quantity and never enters this value.
 */
data class PdfTextHandlePoint(val handle: PdfTextHandle, val word: Int, val x: Double, val y: Double)

/**
 * A grabbed handle and the field of [PdfTextRange] it moves.
 *
 * [movesAnchor] is what keeps the grab across a crossing: the moving endpoint stays the grabbed one even after
 * the pointer passes the other end, so the range reverses (`PdfTextRange.start`/`end` are min/max) without the
 * handles trading places under the finger. A single-word range has both dots on one word; grabbing START then
 * moves `anchor` and grabbing END moves `focus`, which is how each dot can be pulled apart from the same word.
 */
data class PdfTextHandleGrab(val handle: PdfTextHandle, val movesAnchor: Boolean)

/** A handle's dot in a shell's own canvas pixels, produced by [PdfTextHandles.screen]. */
data class PdfTextHandleScreen(val x: Float, val y: Float)

/**
 * The two **endpoint handles** of a PDF text selection: where they sit, which one a press grabbed, and where a
 * drag carries it.
 *
 * A washed passage without handles can only be replaced — the next press starts again — so the paragraph a
 * reader is one word short of has to be selected twice. A handle makes the existing range adjustable: the start
 * dot sits at the first selected word's left edge and the end dot at the last selected word's right edge, one
 * on the line below each, and dragging either one moves that end word by word **in reading order**.
 *
 * Four rules, all decided here rather than once per shell:
 * - **One sheet.** A handle reads and moves words of the range's own sheet ([PdfTextRange.sheet]) through the
 *   already-extracted [PdfTextIndex]; nothing ever selects across pages, and a sheet that is not the range's is
 *   refused by [PdfTextRange.focusOn]'s own rule.
 * - **Reading order, never nearest-remote.** A drag extends by [PdfTextIndex.anchorWord], the nearest word, so
 *   crossing the gap between two words or passing the end of a line keeps the range alive instead of dropping
 *   it at the first press that missed a glyph box.
 * - **The grab survives a crossing.** Dragging the start dot past the end reverses the passage; the dot in the
 *   hand stays that same end, so the range never jumps and the reader never has to find the handle again.
 * - **A screen-sized grip.** The dot is drawn at [DesignTokens.TEXT_SELECTION_HANDLE_DP] and its target is a
 *   [DesignTokens.TEXT_SELECTION_HANDLE_TOUCH_DP] square of screen dp, converted to page points by
 *   [touchRadiusPt] and bounded by [MAX_TOUCH_PT] — so a word at 30 % zoom is as easy to grab as at 300 %,
 *   without a finger 60 points wide swallowing the page's own pan.
 *
 * Nothing here writes: a handle drag moves view state, exactly as the range itself is view state.
 */
object PdfTextHandles {

    /**
     * The widest a handle's press radius may become, in page points.
     *
     * The target is a screen size, so at a small zoom it is a large page distance; bounded here so a page shown
     * at a fifth of its size does not answer a press in the middle of a paragraph with a handle at its edge.
     */
    const val MAX_TOUCH_PT = 24.0

    /** The dot's drawn radius in screen pixels, for a view whose [density] is pixels per dp. */
    fun drawnRadiusPx(density: Float): Float =
        (DesignTokens.TEXT_SELECTION_HANDLE_DP / 2.0 * density).toFloat()

    /** The grab target's radius in screen pixels, for a view whose [density] is pixels per dp. */
    fun touchRadiusPx(density: Float): Float =
        (DesignTokens.TEXT_SELECTION_HANDLE_TOUCH_DP / 2.0 * density).toFloat()

    /**
     * A screen-dp grab radius in page points at [pxPerPt] pixels per point, bounded by [MAX_TOUCH_PT].
     *
     * Zero for a view that has not been laid out or zoomed — a grab with no scale is no grab.
     */
    fun touchRadiusPt(touchPx: Double, pxPerPt: Double): Double {
        if (!(pxPerPt > 0.0) || !(touchPx > 0.0)) return 0.0
        return (touchPx / pxPerPt).coerceAtMost(MAX_TOUCH_PT)
    }

    /**
     * The two dots of [range], in page points: START at the first selected word's left edge, END at the last
     * selected word's right edge, each centred on the line's bottom.
     *
     * Empty when the range covers no word — a sheet with no text layer, or an index that has been replaced.
     */
    fun points(index: PdfTextIndex, range: PdfTextRange): List<PdfTextHandlePoint> {
        val words = index.words(range.sheet.pageNo)
        if (words.isEmpty()) return emptyList()
        val first = words.getOrNull(range.start) ?: return emptyList()
        val last = words.getOrNull(range.end) ?: return emptyList()
        return listOf(
            PdfTextHandlePoint(PdfTextHandle.START, range.start, first.left, first.bottom),
            PdfTextHandlePoint(PdfTextHandle.END, range.end, last.right, last.bottom),
        )
    }

    /**
     * Which handle a press at ([x], [y]) grabs, or null when it is outside both targets.
     *
     * The nearest dot wins, which is what makes the two usable on a one-word range where their centres are a
     * word apart. [radiusPt] is [touchRadiusPt]'s answer for this view's zoom.
     */
    fun grab(index: PdfTextIndex, range: PdfTextRange, x: Double, y: Double, radiusPt: Double): PdfTextHandleGrab? {
        val reach = radiusPt.coerceIn(0.0, MAX_TOUCH_PT)
        if (reach <= 0.0) return null
        val nearest = points(index, range)
            .minByOrNull { hypot(it.x - x, it.y - y) }
            ?: return null
        if (hypot(nearest.x - x, nearest.y - y) > reach) return null
        return grabOf(range, nearest.handle)
    }

    /**
     * The grab a handle performs on [range] as it stands now.
     *
     * The field named is the one holding that end of the passage: START is the lower index, END the higher one —
     * in whichever direction the drag that made the range went. On a single-word range both fields hold the same
     * word, so a tie gives START `anchor` and END `focus`, which is what lets the two dots be pulled apart from
     * one word instead of both moving the same end.
     */
    fun grabOf(range: PdfTextRange, handle: PdfTextHandle): PdfTextHandleGrab = PdfTextHandleGrab(
        handle = handle,
        movesAnchor = if (handle == PdfTextHandle.START) range.anchor <= range.focus else range.anchor > range.focus,
    )

    /**
     * A drag of [grab] to ([x], [y]): the moving end lands on the nearest word of the range's own sheet.
     *
     * The other end is untouched, so the passage grows or shrinks from the grabbed dot; once the pointer passes
     * the fixed end the range reverses, and [grab] still names the dot in the hand. A point the sheet cannot
     * answer (an empty text layer, a replaced index) leaves the range exactly where it was.
     */
    fun drag(index: PdfTextIndex, range: PdfTextRange, grab: PdfTextHandleGrab, x: Double, y: Double): PdfTextRange {
        val word = index.anchorWord(range.sheet.pageNo, x, y) ?: return range
        return move(range, grab, word)
    }

    /**
     * Move one end of [range] by [delta] words — the keyboard's and assistive input's way to adjust a passage.
     *
     * It is the same move a drag makes, by a counted step instead of a pointer: START moves the reading start,
     * END the reading end, clamped to the sheet's own first and last word. A step past the other end reverses
     * the range, as a crossing drag does, and a step that would leave the sheet changes nothing.
     */
    fun nudge(index: PdfTextIndex, range: PdfTextRange, handle: PdfTextHandle, delta: Int): PdfTextRange {
        val words = index.words(range.sheet.pageNo)
        if (words.isEmpty() || delta == 0) return range
        val base = if (handle == PdfTextHandle.START) range.start else range.end
        val target = (base + delta).coerceIn(0, words.lastIndex)
        if (target == base) return range
        return move(range, grabOf(range, handle), target)
    }

    /** Map a dot's page-point centre into a shell's canvas pixels. */
    fun screen(point: PdfTextHandlePoint, pxPerPt: Double, originX: Double, originY: Double): PdfTextHandleScreen =
        PdfTextHandleScreen((point.x * pxPerPt + originX).toFloat(), (point.y * pxPerPt + originY).toFloat())

    /** The English name of a handle, for the accessible label a shell attaches to it. */
    fun label(handle: PdfTextHandle): String = when (handle) {
        PdfTextHandle.START -> "Selection start"
        PdfTextHandle.END -> "Selection end"
    }

    /** [range] with the grabbed end moved to [word], keeping both its sheet and its fixed end. */
    private fun move(range: PdfTextRange, grab: PdfTextHandleGrab, word: Int): PdfTextRange =
        if (grab.movesAnchor) range.copy(anchor = word) else range.copy(focus = word)
}
