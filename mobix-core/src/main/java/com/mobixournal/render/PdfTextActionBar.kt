package com.mobixournal.render

/**
 * Where a PDF text selection's **actions** are in their life — the single state both shells ask before they
 * mount a bar over the page.
 *
 * It is not the selection's state: the range exists in [EXTENDING] exactly as it does in [SETTLED], and the
 * words are washed the whole time. What changes is only whether the capsule is due, because a bar that
 * followed the pointer while the range was being made was chrome chasing a gesture — it flashed, it moved
 * under the finger, and it covered the very words being selected.
 */
enum class PdfTextActionPhase {
    /** No range: no bar. */
    ABSENT,

    /** A pointer or a handle is still moving the range: the capsule waits. */
    EXTENDING,

    /** A nonempty range was released: the capsule is due. */
    SETTLED;

    /** True only in [SETTLED] — the one question a shell asks to decide whether to mount the bar. */
    val showsBar: Boolean get() = this == SETTLED
}

/**
 * The transitions of [PdfTextActionPhase], pure and in one place so the two shells can be checked against the
 * same rule instead of each hoping its own drag flag means the same thing.
 *
 * Dismissal — Escape, a click off the words, a page or source change, pen contact — is not a transition that
 * has to remember anything: whatever happened, there is no range afterwards, so the bar is [ABSENT].
 */
object PdfTextActionLifecycle {

    /** A press landed on a word: the range exists and is being made. */
    fun onPress(hasRange: Boolean): PdfTextActionPhase =
        if (hasRange) PdfTextActionPhase.EXTENDING else PdfTextActionPhase.ABSENT

    /** The pointer or a handle moved: still being made, even when the range shrank to one word. */
    fun onExtend(phase: PdfTextActionPhase): PdfTextActionPhase = when (phase) {
        PdfTextActionPhase.ABSENT -> PdfTextActionPhase.ABSENT
        else -> PdfTextActionPhase.EXTENDING
    }

    /**
     * The pointer or handle was let go: the bar is due, unless nothing is selected any more.
     *
     * A release that arrives without a range in flight starts nothing — an up event from another gesture (a
     * pan, a pen stroke, a second finger) must not summon a bar over a selection the reader is not touching.
     */
    fun onRelease(phase: PdfTextActionPhase, hasRange: Boolean): PdfTextActionPhase = when {
        !hasRange -> PdfTextActionPhase.ABSENT
        phase == PdfTextActionPhase.ABSENT -> PdfTextActionPhase.ABSENT
        else -> PdfTextActionPhase.SETTLED
    }

    /** Escape, a click off the words, a page change, pen contact: no range afterwards. */
    fun onDismiss(): PdfTextActionPhase = PdfTextActionPhase.ABSENT
}

/** A rectangle in **canvas pixels** — the placement's only currency, so each shell hands it what it paints. */
data class PdfBarBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centreX: Float get() = (left + right) / 2f

    /** True when the two rectangles share any area at all. */
    fun overlaps(other: PdfBarBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

/** A bar's top-left corner in canvas pixels. */
data class PdfBarOffset(val x: Float, val y: Float)

/**
 * Where the PDF text action bar sits: **beside the line the gesture is on**, not under the middle of the whole
 * passage.
 *
 * Centring on the union of a multiline selection answered the wrong question twice. It put the bar under the
 * *last* line however far away the reader's finger was, and on a passage that filled the page it landed on the
 * words themselves. The rule here is about the **active line** — the band the endpoint is on — and the union is
 * not consulted at all, because a reader who has just extended a range is looking at that endpoint.
 *
 * Four guarantees, in the order they are decided:
 * - **Only visible lines count.** A line scrolled out of the pane is not a place to hang a bar; the active line
 *   offscreen falls back to the nearest *visible* selected line, and a selection with nothing visible answers
 *   null — the bar is hidden rather than pinned to an edge of a pane that shows none of it.
 * - **The side the range does not cover.** A bar goes below the active line — where a selection's actions
 *   belong — unless the range continues *downwards* from that line, in which case below is more selected, washed
 *   words and the bar stands above instead; selecting upwards therefore puts it above the top line, in the clear.
 *   The other side is the fallback when the preferred one has no room, and only when neither fits is the bar
 *   pulled inside, nearest the line.
 * - **Clear of the handle.** The touch target under the endpoint is passed as `avoid`: a bar that lands on it
 *   takes the other side, so the reader can keep dragging the handle they just released.
 * - **Inside its own pane.** The x is centred on the active line and clamped, so a wide capsule at a large text
 *   scale stays in the pane instead of hanging over the divider.
 */
object PdfTextBarPlacement {

    /**
     * The bar's offset, or null when no selected line is visible (hide the bar).
     *
     * A bar whose size is not measured yet parks against the margin: the first frame of a mount must not put
     * the capsule at the pane's origin for the reader to watch it move.
     */
    fun offset(
        spans: List<PdfBarBox>,
        active: PdfBarBox?,
        barWidth: Float,
        barHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        gapPx: Float,
        marginPx: Float,
        avoid: PdfBarBox? = null,
    ): PdfBarOffset? {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return null
        // Visibility first: a pane showing none of the selection hides the bar whatever its size is — including
        // the frame before the capsule has been measured.
        val visible = spans.filter { it.bottom > 0f && it.top < canvasHeight }
        if (visible.isEmpty()) return null
        if (barWidth <= 0f || barHeight <= 0f) return PdfBarOffset(marginPx, marginPx)
        val line = visible.firstOrNull { active != null && it.sameLine(active) } ?: nearestTo(visible, active)
        val maxX = (canvasWidth - barWidth - marginPx).coerceAtLeast(marginPx)
        val maxY = (canvasHeight - barHeight - marginPx).coerceAtLeast(marginPx)
        val x = (line.centreX - barWidth / 2f).coerceIn(marginPx, maxX)
        val blocked = avoid?.let {
            PdfBarBox(it.left - gapPx, it.top - gapPx, it.right + gapPx, it.bottom + gapPx)
        }
        fun fits(top: Float): Boolean {
            val bar = PdfBarBox(x, top, x + barWidth, top + barHeight)
            return (blocked == null || !bar.overlaps(blocked)) && top in marginPx..maxY
        }
        val below = line.bottom + gapPx
        val above = line.top - gapPx - barHeight
        // The range continues downwards from the active line, so below it is more washed words: the bar takes
        // the clear side instead. A single-line selection, or one ending where the pointer is, gets it below.
        val continuesBelow = visible.any { it.top > line.bottom }
        val preferred = if (continuesBelow) above else below
        val other = if (continuesBelow) below else above
        val y = when {
            fits(preferred) -> preferred
            fits(other) -> other
            else -> below
        }
        return PdfBarOffset(x, y.coerceIn(marginPx, maxY))
    }

    /** Two bands from the same line: same top and bottom, because a line's spans share its union height. */
    private fun PdfBarBox.sameLine(other: PdfBarBox): Boolean =
        top == other.top && bottom == other.bottom

    /**
     * The visible band closest to the active one — the fallback when the line the endpoint is on has been
     * scrolled out of the pane. Without an active band the *first* visible one is used, so a range whose
     * beginning is on screen still hangs its bar where the reading starts.
     */
    private fun nearestTo(visible: List<PdfBarBox>, active: PdfBarBox?): PdfBarBox {
        if (active == null) return visible.first()
        val target = (active.top + active.bottom) / 2f
        return visible.minByOrNull { kotlin.math.abs((it.top + it.bottom) / 2f - target) } ?: visible.first()
    }
}
