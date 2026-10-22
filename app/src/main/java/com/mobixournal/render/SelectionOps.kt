package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure edits the selection tool applies to a page list: translate, resize (affine scale, uniform or
 * per-axis), rotate,
 * delete, recolour/re-width, and cut/copy/paste (move between pages) the elements named by a set of
 * [ElementRef]s. Every op returns a new page list (immutable pages/layers share structure, so a
 * snapshot is cheap); in-place ops never reorder elements, so the refs stay valid across a live
 * drag. Free of Android types — unit-testable on the JVM.
 *
 * **Round-trip scope.** The `.xopp` format stores only what each element carries on disk, so an op
 * exists only where it round-trips: scale bakes into coordinates/box/font-size, rotate bakes into a
 * stroke's vertex coordinates (the only element with a free-form point list). Text and images have
 * no rotation attribute and axis-aligned boxes, so [rotate] leaves them untouched — the view offers
 * the rotate handle only for all-stroke selections (see `docs/selection.md`).
 */
object SelectionOps {

    /**
     * Shift a single element by (dx, dy) pt, preserving everything else about it.
     * @param element Element to translate.
     * @param dx Horizontal delta in points.
     * @param dy Vertical delta in points.
     * @return Translated element, or unchanged if unmodelled (RawElement).
     */
    fun translate(element: Element, dx: Double, dy: Double): Element = when (element) {
        is Stroke -> element.copy(points = element.points.map { StrokePoint(it.x + dx, it.y + dy, it.width) })
        is TextElement -> element.copy(x = element.x + dx, y = element.y + dy)
        is ImageElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        is TexImageElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        // Elements we don't model carry geometry we can't interpret, so every op leaves them be.
        is RawElement -> element
        // PlotElement translates its page-local frame while preserving mathematical data ranges (xMin..xMax).
        is PlotElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        // TableElement translates its origin while colWidths/rowHeights stay put.
        is TableElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
        )
        // CircuitElement translates its position anchor while pins move with it.
        is CircuitElement -> element.copy(
            positionX = element.positionX + dx, positionY = element.positionY + dy,
        )
        // ParametricShapeElement translates its vertices: they are the shape's geometry, so the
        // figure moves as a whole and its editable corners travel with it.
        is ParametricShapeElement -> element.copy(
            vertices = element.vertices.map { it.copy(x = it.x + dx, y = it.y + dy) },
        )
    }

    /**
     * Return [pages] with the elements at [refs] on page [pageIndex] shifted by (dx, dy) pt.
     * @param pages Document page list.
     * @param pageIndex Index of page to modify (0-based).
     * @param refs Set of element references to translate.
     * @param dx Horizontal delta in points.
     * @param dy Vertical delta in points.
     * @return New page list with translated elements, or [pages] if no change.
     */
    fun translate(pages: List<Page>, pageIndex: Int, refs: Set<ElementRef>, dx: Double, dy: Double): List<Page> {
        if (refs.isEmpty() || (dx == 0.0 && dy == 0.0)) return pages
        return mapPage(pages, pageIndex) { li, ei, el ->
            if (ElementRef(li, ei) in refs) translate(el, dx, dy) else el
        }
    }

    /**
     * Apply the affine `x' = x·s + dx`, `y' = y·s + dy` to one element. This single primitive backs
     * both uniform resize (a scale about an anchor) and cross-page re-homing (a scale+shift between
     * two pages' pt frames). [s] is expected positive so image/box corner order is preserved.
     *
     * @param element Element to transform.
     * @param s Scale factor (positive).
     * @param dx Translation X in points.
     * @param dy Translation Y in points.
     * @param scaleWidth Whether a stroke's per-vertex width follows the scale. True for re-homing
     *   between two pages' frames (the ink is rescaled to the new paper), **false** for a resize
     *   gesture: a figure dragged bigger grows in *length*, and its line keeps the thickness it was
     *   drawn with — see [scaleXY].
     * @return Transformed element, or unchanged if unmodelled.
     */
    fun affine(
        element: Element,
        s: Double,
        dx: Double,
        dy: Double,
        scaleWidth: Boolean = true,
    ): Element = affineXY(element, s, s, dx, dy, scaleWidth)

    /**
     * Apply the axis-independent affine `x' = x·sx + dx`, `y' = y·sy + dy` to one element — the
     * primitive behind a **non-uniform** resize, where a drag on an edge handle stretches the
     * selection in one axis only.
     *
     * A stroke's per-vertex width has no single axis, so it has two honest answers and the caller
     * picks: with [scaleWidth] it follows the **geometric mean** `sqrt(sx·sy)` (re-homing ink onto
     * another page's frame — 4:1 makes it twice as thick, not 4× at one axis's whim), and without it
     * the width is **carried through untouched** (a resize gesture: the figure's geometry grows, its
     * line stays the thickness it was drawn with, so lengthening an arrow does not fatten it). A text
     * box's font size follows the geometry either way — letters are the figure there, not a stroke.
     * [sx] and [sy] are expected positive so image/box corner order is preserved.
     * @param element Element to transform.
     * @param sx Horizontal scale factor (positive).
     * @param sy Vertical scale factor (positive).
     * @param dx Translation X in points.
     * @param dy Translation Y in points.
     * @param scaleWidth Whether a stroke's width follows the scale (see above). A resize gesture
     *   (`false`) also leaves each headed figure at its drawn size — only the shaft lengthens
     *   ([FigureRedraw]); re-homing ink (`true`) scales the whole drawing, heads included.
     * @return Transformed element, or unchanged if unmodelled.
     */
    fun affineXY(
        element: Element,
        sx: Double,
        sy: Double,
        dx: Double,
        dy: Double,
        scaleWidth: Boolean = true,
    ): Element {
        val scalar = kotlin.math.sqrt(sx * sy)
        val ink = if (scaleWidth) scalar else 1.0
        return when (element) {
            is Stroke -> element.copy(
                points = if (scaleWidth) {
                    element.points.map {
                        StrokePoint(it.x * sx + dx, it.y * sy + dy, it.width * ink)
                    }
                } else {
                    FigureRedraw.stretch(element.points, sx, sy, dx, dy)
                },
            )
            is TextElement -> element.copy(
                x = element.x * sx + dx,
                y = element.y * sy + dy,
                size = element.size * scalar,
            )
            is ImageElement -> element.copy(
                left = element.left * sx + dx, top = element.top * sy + dy,
                right = element.right * sx + dx, bottom = element.bottom * sy + dy,
            )
            is TexImageElement -> element.copy(
                left = element.left * sx + dx, top = element.top * sy + dy,
                right = element.right * sx + dx, bottom = element.bottom * sy + dy,
            )
            is RawElement -> element
            // PlotElement scales its bounding frame and preserves its mathematical data ranges (xMin..xMax).
            is PlotElement -> element.copy(
                left = element.left * sx + dx, top = element.top * sy + dy,
                right = element.right * sx + dx, bottom = element.bottom * sy + dy,
            )
            // TableElement scales its origin and column/row dimensions (enforcing minimums).
            is TableElement -> {
                val newLeft = element.left * sx + dx
                val newTop = element.top * sy + dy
                val newColWidths = element.colWidths.map {
                    (it * sx).coerceAtLeast(TableElementFactory.MIN_COL_WIDTH_PT)
                }
                val newRowHeights = element.rowHeights.map {
                    (it * sy).coerceAtLeast(TableElementFactory.MIN_ROW_HEIGHT_PT)
                }
                element.copy(
                    left = newLeft,
                    top = newTop,
                    colWidths = newColWidths,
                    rowHeights = newRowHeights,
                )
            }
            is CircuitElement -> {
                val newX = element.positionX * sx + dx
                val newY = element.positionY * sy + dy
                val scaledPins = element.pins.map {
                    it.copy(relX = it.relX * sx, relY = it.relY * sy)
                }
                element.copy(
                    positionX = newX,
                    positionY = newY,
                    pins = scaledPins,
                )
            }
            // ParametricShapeElement scales its vertices, keeping its outline width as drawn (unless
            // the caller is re-homing the ink, which scales that too, exactly as for a stroke).
            is ParametricShapeElement -> element.copy(
                vertices = element.vertices.map { it.copy(x = it.x * sx + dx, y = it.y * sy + dy) },
                strokeWidth = element.strokeWidth * ink,
            )
        }
    }

    /**
     * Return [pages] with the elements at [refs] on page [pageIndex] uniformly scaled by [factor]
     * about the anchor point (`anchorX`, `anchorY`) pt — the opposite corner of a resize drag, which
     * stays fixed. [factor] should be positive.
     * @param pages Document page list.
     * @param pageIndex Index of page to modify (0-based).
     * @param refs Set of element references to scale.
     * @param factor Scale factor (1.0 = no change).
     * @param anchorX Anchor X in points (fixed point of transform).
     * @param anchorY Anchor Y in points.
     * @return New page list with scaled elements, or [pages] if no change.
     */
    fun scale(
        pages: List<Page>, pageIndex: Int, refs: Set<ElementRef>,
        factor: Double, anchorX: Double, anchorY: Double,
    ): List<Page> = scaleXY(pages, pageIndex, refs, factor, factor, anchorX, anchorY)

    /**
     * Return [pages] with the elements at [refs] on page [pageIndex] scaled by [sx] horizontally and
     * [sy] vertically about the anchor point (`anchorX`, `anchorY`) pt — the corner/edge handle
     * opposite the one being dragged, which stays fixed. Pass `sx == sy` for the ordinary uniform
     * resize; differing factors are the edge handles' out-of-proportion stretch, which is what lets a
     * drawing be widened without being given a matching increase in height. Both factors should be
     * positive.
     * @param pages Document page list.
     * @param pageIndex Index of page to modify (0-based).
     * @param refs Set of element references to scale.
     * @param sx Horizontal scale factor (1.0 = no change).
     * @param sy Vertical scale factor (1.0 = no change).
     * @param anchorX Anchor X in points (fixed point of transform).
     * @param anchorY Anchor Y in points.
     * @return New page list with scaled elements, or [pages] if no change.
     */
    fun scaleXY(
        pages: List<Page>, pageIndex: Int, refs: Set<ElementRef>,
        sx: Double, sy: Double, anchorX: Double, anchorY: Double,
    ): List<Page> {
        if (refs.isEmpty() || (sx == 1.0 && sy == 1.0)) return pages
        val dx = anchorX * (1.0 - sx)
        val dy = anchorY * (1.0 - sy)
        // A resize stretches the figure, not its ink: the geometry scales and every stroke keeps the
        // width it was drawn with, so an arrow made longer stays as thin as it was, and a headed
        // figure is re-emitted with the head it was drawn with (`docs/stylus.md` → *A figure is
        // resized, not thickened*, `FigureRedraw`).
        return mapPage(pages, pageIndex) { li, ei, el ->
            if (ElementRef(li, ei) in refs) affineXY(el, sx, sy, dx, dy, scaleWidth = false) else el
        }
    }

    /**
     * Rotate a single element by [angle] rad about the pivot (`px`, `py`) pt. See the class note:
     * only a [Stroke] carries a rotatable point list, so text/images are returned unchanged.
     * @param element Element to rotate.
     * @param angle Rotation angle in radians.
     * @param px Pivot X in points.
     * @param py Pivot Y in points.
     * @return Rotated element, or unchanged if not a stroke.
     */
    fun rotate(element: Element, angle: Double, px: Double, py: Double): Element = when (element) {
        is Stroke -> {
            val c = cos(angle); val s = sin(angle)
            element.copy(points = element.points.map {
                val rx = it.x - px; val ry = it.y - py
                StrokePoint(px + rx * c - ry * s, py + rx * s + ry * c, it.width)
            })
        }
        is CircuitElement -> {
            val c = cos(angle); val s = sin(angle)
            val rx = element.positionX - px
            val ry = element.positionY - py
            val newX = px + rx * c - ry * s
            val newY = py + rx * s + ry * c
            val deg = Math.toDegrees(angle)
            val newRot = (element.rotation + deg) % 360.0
            element.copy(
                positionX = newX,
                positionY = newY,
                rotation = if (newRot < 0.0) newRot + 360.0 else newRot,
            )
        }
        else -> element
    }

    /**
     * Return [pages] with the strokes at [refs] on page [pageIndex] rotated by [angle] rad about
     * the pivot (`pivotX`, `pivotY`) pt.
     * @param pages Document page list.
     * @param pageIndex Index of page to modify (0-based).
     * @param refs Set of element references to rotate.
     * @param angle Rotation angle in radians.
     * @param pivotX Pivot X in points.
     * @param pivotY Pivot Y in points.
     * @return New page list with rotated strokes, or [pages] if no change.
     */
    fun rotate(
        pages: List<Page>, pageIndex: Int, refs: Set<ElementRef>,
        angle: Double, pivotX: Double, pivotY: Double,
    ): List<Page> {
        if (refs.isEmpty() || angle == 0.0) return pages
        return mapPage(pages, pageIndex) { li, ei, el ->
            if (ElementRef(li, ei) in refs) rotate(el, angle, pivotX, pivotY) else el
        }
    }

    /**
     * Recolour ([color]) and/or re-width ([widthPt]) one element. Width applies to strokes only
     * (as a uniform width); colour applies to strokes/text/LaTeX (images have no colour). A null
     * field leaves that property unchanged.
     * @param element Element to restyle.
     * @param color New ARGB color, or null to leave unchanged.
     * @param widthPt New stroke width in points, or null to leave unchanged.
     * @return Restyled element, or unchanged if unmodelled.
     */
    fun restyle(element: Element, color: Int?, widthPt: Double?): Element = when (element) {
        is Stroke -> {
            var e = if (color != null) element.copy(color = color) else element
            if (widthPt != null) e = e.copy(points = e.points.map { StrokePoint(it.x, it.y, widthPt) }, uniformWidth = true)
            e
        }
        is TextElement -> if (color != null) element.copy(color = color) else element
        is TexImageElement -> if (color != null) element.copy(color = color) else element
        is ImageElement -> element
        is CircuitElement -> {
            var e = if (color != null) element.copy(strokeColor = color) else element
            if (widthPt != null) e = e.copy(strokeWidth = widthPt)
            e
        }
        // A shape carries exactly one outline style of its own, so it restyles like a component.
        is ParametricShapeElement -> {
            var e = if (color != null) element.copy(strokeColor = color) else element
            if (widthPt != null) e = e.copy(strokeWidth = widthPt)
            e
        }
        // The other living entities' colour is part of their own record (a plot's curves, a table's
        // rules), not a single style this op could set, so they are left alone rather than half-restyled.
        is TableElement, is PlotElement, is RawElement -> element
    }

    /**
     * Return [pages] with the elements at [refs] on page [pageIndex] recoloured/re-widthed.
     * @param pages Document page list.
     * @param pageIndex Index of page to modify (0-based).
     * @param refs Set of element references to restyle.
     * @param color New ARGB color, or null to leave unchanged.
     * @param widthPt New stroke width in points, or null to leave unchanged.
     * @return New page list with restyled elements, or [pages] if no change.
     */
    fun restyle(
        pages: List<Page>, pageIndex: Int, refs: Set<ElementRef>, color: Int?, widthPt: Double?,
    ): List<Page> {
        if (refs.isEmpty() || (color == null && widthPt == null)) return pages
        return mapPage(pages, pageIndex) { li, ei, el ->
            if (ElementRef(li, ei) in refs) restyle(el, color, widthPt) else el
        }
    }

    /**
     * The elements named by [refs] on [page], in a stable layer-then-index order (for copy/cut).
     * @param page Page to read from.
     * @param refs Set of element references to fetch.
     * @return List of elements in stable order, skipping missing refs.
     */
    fun elementsAt(page: Page, refs: Set<ElementRef>): List<Element> =
        refs.sortedWith(compareBy({ it.layerIndex }, { it.elementIndex }))
            .mapNotNull { page.layers.getOrNull(it.layerIndex)?.elements?.getOrNull(it.elementIndex) }

    /**
     * The union bounds of [elements] in page-local pt, or null if none has trustworthy geometry.
     * Used to re-centre a clipboard paste on the part of the page the user is looking at; a
     * [RawElement] (whose box [ElementBounds] can't trust) is skipped rather than skewing the box.
     */
    fun boundsOf(elements: List<Element>): Bounds? = elements
        .filter { ElementBounds.isHitTestable(it) }
        .map { ElementBounds.of(it) }
        .reduceOrNull { a, b -> a.union(b) }

    /**
     * Append [elements] to page [pageIndex]'s top (last) layer — creating a layer if the page has
     * none — and return the new page list paired with the [ElementRef]s of the appended elements
     * (used to select a freshly pasted/duplicated set).
     * @param pages Document page list.
     * @param pageIndex Index of page to modify (0-based).
     * @param elements Elements to append.
     * @return Pair of (new page list, refs of appended elements).
     */
    fun addToTopLayer(
        pages: List<Page>, pageIndex: Int, elements: List<Element>,
    ): Pair<List<Page>, Set<ElementRef>> {
        val page = pages.getOrNull(pageIndex) ?: return pages to emptySet()
        if (elements.isEmpty()) return pages to emptySet()
        val (edited, refs) = addToTopLayerOn(page, elements)
        val out = pages.toMutableList().also { it[pageIndex] = edited }
        return out to refs
    }

    /**
     * [addToTopLayer] for **one page** — the same append with no page list in sight, so a caller that
     * holds a single page (a page window editing a page it just materialised) does not have to build
     * the document's list to append three elements.
     *
     * @return the edited page and the [ElementRef]s of the appended elements on it.
     */
    fun addToTopLayerOn(page: Page, elements: List<Element>): Pair<Page, Set<ElementRef>> {
        if (elements.isEmpty()) return page to emptySet()
        val layers = page.layers
        val topIndex = (layers.size - 1).coerceAtLeast(0)
        val base = layers.lastOrNull()?.elements?.size ?: 0
        val newLayers = if (layers.isEmpty()) {
            listOf(Layer(elements.toList()))
        } else {
            layers.toMutableList().also { it[topIndex] = Layer(it[topIndex].elements + elements, it[topIndex].name) }
        }
        val refs = elements.indices.map { ElementRef(topIndex, base + it) }.toSet()
        return page.copy(layers = newLayers) to refs
    }

    /**
     * Move the elements at [refs] from page [from] to page [to], applying the affine
     * `x' = x·s + dx`, `y' = y·s + dy` so they land where the drop point is in the target page's pt
     * frame (equal-width pages → a pure shift; differing widths → also rescaled to keep the visual
     * position). Returns the new page list and the refs of the moved elements on [to].
     * @param pages Document page list.
     * @param from Source page index (0-based).
     * @param to Destination page index (0-based).
     * @param refs Set of element references to move.
     * @param s Scale factor for cross-page transform.
     * @param dx Translation X in points.
     * @param dy Translation Y in points.
     * @return Pair of (new page list, refs of moved elements on destination page).
     */
    fun moveToPage(
        pages: List<Page>, from: Int, to: Int, refs: Set<ElementRef>, s: Double, dx: Double, dy: Double,
    ): Pair<List<Page>, Set<ElementRef>> {
        if (from == to || refs.isEmpty()) return pages to refs
        val src = pages.getOrNull(from) ?: return pages to refs
        val dst = pages.getOrNull(to) ?: return pages to refs
        val (editedFrom, editedTo, refsOnTo) = moveOnto(src, dst, refs, s, dx, dy)
        val out = pages.toMutableList().also { it[from] = editedFrom; it[to] = editedTo }
        return out to refsOnTo
    }

    /**
     * [moveToPage] for **two pages** — the same move with no page list, for a caller that holds the
     * two pages (a page window editing a page it just materialised).
     *
     * @return the edited source page, the edited destination page, and the moved elements' refs on it.
     */
    fun moveOnto(
        from: Page, to: Page, refs: Set<ElementRef>, s: Double, dx: Double, dy: Double,
    ): Triple<Page, Page, Set<ElementRef>> {
        if (refs.isEmpty()) return Triple(from, to, refs)
        val moved = elementsAt(from, refs).map { affine(it, s, dx, dy) }
        val (appended, refsOnTo) = addToTopLayerOn(to, moved)
        return Triple(deleteOn(from, refs), appended, refsOnTo)
    }

    /**
     * Return [pages] with the elements at [refs] on page [pageIndex] removed.
     * @param pages Document page list.
     * @param pageIndex Index of page to modify (0-based).
     * @param refs Set of element references to delete.
     * @return New page list with elements removed, or [pages] if no change.
     */
    fun delete(pages: List<Page>, pageIndex: Int, refs: Set<ElementRef>): List<Page> {
        if (refs.isEmpty()) return pages
        val page = pages.getOrNull(pageIndex) ?: return pages
        val edited = deleteOn(page, refs)
        if (edited === page) return pages
        return pages.toMutableList().also { it[pageIndex] = edited }
    }

    /**
     * [delete] for **one page**: remove the elements at [refs], or hand back the same instance when
     * none of them is there. What a page window calls, so the page list is never built.
     */
    fun deleteOn(page: Page, refs: Set<ElementRef>): Page {
        if (refs.isEmpty()) return page
        var changed = false
        val layers = page.layers.mapIndexed { li, layer ->
            val kept = layer.elements.filterIndexed { ei, _ ->
                val gone = ElementRef(li, ei) in refs
                changed = changed || gone
                !gone
            }
            if (kept.size == layer.elements.size) layer else Layer(kept, layer.name)
        }
        return if (changed) page.copy(layers = layers) else page
    }

    /** Rebuild page [pageIndex]'s layers, letting [transform] replace each element in place. */
    private inline fun mapPage(
        pages: List<Page>,
        pageIndex: Int,
        transform: (layerIndex: Int, elementIndex: Int, element: Element) -> Element,
    ): List<Page> {
        val page = pages.getOrNull(pageIndex) ?: return pages
        val layers = page.layers.mapIndexed { li, layer ->
            Layer(layer.elements.mapIndexed { ei, el -> transform(li, ei, el) }, layer.name)
        }
        return pages.toMutableList().also { it[pageIndex] = page.copy(layers = layers) }
    }

    /** How a multi-element selection is aligned (the two horizontal and three vertical edges). */
    enum class SelectionAlign { LEFT, H_CENTRE, RIGHT, TOP, V_MIDDLE, BOTTOM }

    /**
     * Align the elements at [refs] on page [pageIndex] to the selection's bounding box: each element
     * is translated so the chosen edge (or centre line) matches the box's. A move is a pure
     * translation, so every element round-trips unchanged in kind — see [translate]. A selection of
     * fewer than two elements is returned untouched (nothing to align against).
     */
    fun align(
        pages: List<Page>, pageIndex: Int, refs: Set<ElementRef>, alignment: SelectionAlign,
    ): List<Page> {
        if (refs.size < 2) return pages
        val page = pages.getOrNull(pageIndex) ?: return pages
        val boxes = selectedBounds(page, refs)
        if (boxes.size < 2) return pages
        val union = boxes.map { it.second }.reduce { a, b -> a.union(b) }
        val deltas = HashMap<ElementRef, Pair<Double, Double>>(boxes.size)
        for ((ref, b) in boxes) {
            val dx = when (alignment) {
                SelectionAlign.LEFT -> union.left - b.left
                SelectionAlign.H_CENTRE -> union.centreX() - b.centreX()
                SelectionAlign.RIGHT -> union.right - b.right
                else -> 0.0
            }
            val dy = when (alignment) {
                SelectionAlign.TOP -> union.top - b.top
                SelectionAlign.V_MIDDLE -> union.centreY() - b.centreY()
                SelectionAlign.BOTTOM -> union.bottom - b.bottom
                else -> 0.0
            }
            deltas[ref] = dx to dy
        }
        return mapPage(pages, pageIndex) { li, ei, el ->
            val d = deltas[ElementRef(li, ei)]
            if (d != null) translate(el, d.first, d.second) else el
        }
    }

    /**
     * Spread the elements at [refs] on page [pageIndex] so their centres are **evenly spaced** between
     * the two outermost ones, which stay put — evenly distributing *gaps* would need equal element
     * sizes, while equal centre spacing always works. [horizontal] distributes along x, otherwise y.
     * Fewer than three elements is returned untouched (the two ends already define both extremes).
     */
    fun distribute(
        pages: List<Page>, pageIndex: Int, refs: Set<ElementRef>, horizontal: Boolean,
    ): List<Page> {
        if (refs.size < 3) return pages
        val page = pages.getOrNull(pageIndex) ?: return pages
        val boxes = selectedBounds(page, refs)
        if (boxes.size < 3) return pages
        val sorted = boxes.sortedBy { if (horizontal) it.second.centreX() else it.second.centreY() }
        val first = if (horizontal) sorted.first().second.centreX() else sorted.first().second.centreY()
        val last = if (horizontal) sorted.last().second.centreX() else sorted.last().second.centreY()
        val step = (last - first) / (sorted.size - 1)
        val deltas = HashMap<ElementRef, Pair<Double, Double>>(sorted.size)
        sorted.forEachIndexed { i, (ref, b) ->
            if (i == 0 || i == sorted.size - 1) return@forEachIndexed
            val target = first + step * i
            val current = if (horizontal) b.centreX() else b.centreY()
            deltas[ref] = if (horizontal) (target - current) to 0.0 else 0.0 to (target - current)
        }
        return mapPage(pages, pageIndex) { li, ei, el ->
            val d = deltas[ElementRef(li, ei)]
            if (d != null) translate(el, d.first, d.second) else el
        }
    }

    /** Each selected element's bounds, skipping unmodelled/non-hit-testable ones. */
    private fun selectedBounds(page: Page, refs: Set<ElementRef>): List<Pair<ElementRef, Bounds>> =
        refs.mapNotNull { ref ->
            val el = page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
                ?: return@mapNotNull null
            if (!ElementBounds.isHitTestable(el)) return@mapNotNull null
            ref to ElementBounds.of(el)
        }

    private fun Bounds.centreX(): Double = (left + right) / 2.0
    private fun Bounds.centreY(): Double = (top + bottom) / 2.0
}
