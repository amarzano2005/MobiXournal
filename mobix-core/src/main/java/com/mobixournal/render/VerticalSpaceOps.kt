package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * The vertical-space tool's pure edit: grab a horizontal line on a page and drag to insert (down) or
 * remove (up) vertical space, shifting everything below the line with the drag. Desktop Xournal++
 * has this as a core rail tool; it round-trips perfectly because it only rewrites element
 * coordinates, which every `.xopp` element already stores on disk.
 *
 * **Which elements move** is the desktop rule: *"all the items which lie entirely between the cursor
 * position and the end of the page"*. "Entirely" is decided by an element's **top** edge
 * ([ElementBounds]): an element that starts below the grab line moves whole, and one the line passes
 * through stays put rather than being torn in half. The amount of space inserted snaps to the page's
 * ruling when snapping is on ([dragShift]) — desktop's *"Added snapping for vertical space"*.
 *
 * **The block may cross the line.** The shift follows the pointer in both directions with no stop at
 * the grab line, as on the desktop: pulling up past it lifts the block above the line it was grabbed
 * at (and, if pulled far enough, off the top of the sheet — see [shiftBelow]). The old clamp that
 * stopped the topmost element at the line made closing a gap *below* an element impossible without
 * first inserting space somewhere else, which is precisely what the desktop does not do.
 *
 * **Which elements move is still decided once, at grab time**, from the line's original position: a
 * block that follows the pointer must not recruit new elements on the way past them, so the drag
 * moves exactly what was below the line when it started.
 *
 * **Layers are a deliberate difference from the desktop**, which reflows only the current layer: here
 * every layer of the page moves together, so a note written on one layer can't be left behind by
 * space opened on another. The tool reflows the page, not one layer of it.
 *
 * Free of Android types, so it is unit-testable on the JVM (see `VerticalSpaceOpsTest`).
 */
object VerticalSpaceOps {

    /**
     * [pages] with every element on page [pageIndex] whose top edge sits at or below [yPt] shifted
     * down by [dy] pt (negative pulls up). The shift is whatever [dragShift] allows, and a no-op
     * returns the same list so a live drag that can't move anything doesn't churn snapshots.
     *
     * Nothing bounds the shift to the sheet: content pulled above the top of the page stays there
     * (off-page, exactly as desktop Xournal++ leaves it) and is recoverable by dragging back down —
     * which is why the elements are still in the file and still counted by the block, rather than
     * being quietly dropped or re-homed behind the user's back.
     */
    fun shiftBelow(
        pages: List<Page>,
        pageIndex: Int,
        yPt: Double,
        dy: Double,
        snapSpacingPt: Double = 0.0,
    ): List<Page> {
        val page = pages.getOrNull(pageIndex) ?: return pages
        val shifted = shiftBlock(page, blockBelow(page, yPt), dy, snapSpacingPt)
        // Nothing to move (no element below the line, or a zero-length drag) — return the same list so
        // a drag over empty space doesn't churn a snapshot and record an empty undo step.
        if (shifted === page) return pages
        return pages.toMutableList().also { it[pageIndex] = shifted }
    }

    /**
     * The elements a grab line at [yPt] carries — **the answer to a question that does not change**.
     *
     * A live drag re-applies its shift on every frame of the gesture, and the block is the same set from the
     * press to the lift: it is what lay below the line when the press happened ([shiftBelow] says why).
     * Answering it again per frame meant walking every element and recomputing its bounding box — measured at
     * **1.6 ms of a 5.2 ms reflow** over 5000 strokes — for an answer that cannot have changed. A drag holds
     * this once ([blockBelow]) and re-applies it with [shiftBlock]; a one-shot edit still goes through
     * [shiftBelow], which is the same two steps.
     *
     * Elements are addressed by their position in their layer, which is exactly what the tool leaves alone:
     * it rewrites coordinates and never inserts, removes or reorders, so the positions stay true for the
     * whole gesture.
     */
    class Block internal constructor(internal val layers: List<IntArray>) {

        /** True when the line carried nothing — a drag then has nothing to re-apply. */
        val isEmpty: Boolean get() = layers.all { it.isEmpty() }

        /**
         * True when the element at ([layerIndex], [elementIndex]) is one this block carries.
         *
         * For a consumer that has to **partition** a page rather than rewrite it: the window's live preview
         * records the elements that stay and the elements that follow the line as two pictures, and asks
         * this once per element when it records them.
         */
        fun moves(layerIndex: Int, elementIndex: Int): Boolean {
            val picked = layers.getOrNull(layerIndex) ?: return false
            return picked.binarySearch(elementIndex) >= 0
        }
    }

    /** The elements of [page] whose top edge sits at or below [yPt], per layer, in layer order. */
    fun blockBelow(page: Page, yPt: Double): Block = Block(
        page.layers.map { layer ->
            val below = layer.elements.indices.filter { isBelow(layer.elements[it], yPt) }
            IntArray(below.size) { below[it] }
        },
    )

    /**
     * [page] with exactly [block]'s elements shifted by [dy] pt (negative pulls up), snapped by
     * [snapSpacingPt] — the drag's own half of the edit, re-applied from the page **as it was at grab time**.
     *
     * A layer with nothing to move is handed back as the same object, and a zero shift or an empty block
     * returns [page] itself, so a frame that moved nothing costs no allocation at all.
     */
    fun shiftBlock(
        page: Page,
        block: Block,
        dy: Double,
        snapSpacingPt: Double = 0.0,
    ): Page {
        val shift = dragShift(dy, snapSpacingPt)
        if (shift == 0.0 || block.isEmpty) return page
        val layers = page.layers.mapIndexed { layerIndex, layer ->
            val picked = block.layers.getOrNull(layerIndex) ?: IntArray(0)
            if (picked.isEmpty()) return@mapIndexed layer
            var next = 0
            Layer(
                layer.elements.mapIndexed { index, element ->
                    if (next < picked.size && picked[next] == index) {
                        next++
                        ElementShift.translate(element, 0.0, shift)
                    } else {
                        element
                    }
                },
                layer.name,
            )
        }
        return page.copy(layers = layers)
    }

    /**
     * The shift a live drag actually applies: the requested [dy], snapped to the page's ruling when
     * [snapSpacingPt] is positive.
     *
     * Snapping is what makes the tool land on the ruling: with **Snap to grid** on, the gap opened is
     * a whole number of ruled lines instead of wherever the pointer happened to stop, which is
     * desktop Xournal++'s behaviour for this tool. Snapping the shift (not the line) keeps the grabbed
     * line exactly where the user pressed it and moves only the amount of space measured from it.
     *
     * There is no clamp in either direction: the drag is the pointer's travel, positive (down, making
     * room) or negative (up, closing it and past the line when the user keeps pulling).
     */
    fun dragShift(dy: Double, snapSpacingPt: Double): Double = Snapping.snap(dy, snapSpacingPt)

    /** True when [element]'s box top sits at or below the grab line — the desktop's "entirely below". */
    private fun isBelow(element: Element, yPt: Double): Boolean = ElementBounds.of(element).top >= yPt
}
