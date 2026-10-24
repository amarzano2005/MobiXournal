package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import kotlin.math.cos
import kotlin.math.sin

/**
 * A **selection on a page**, as both shells hold it: the region the user drew, and the elements it caught.
 *
 * The two are kept apart on purpose. The region is what the marching outline and the handles follow, and
 * it is **page geometry**, not a set of elements: a marquee can enclose nothing and still be drawn, and a
 * lasso's outline is the rope the user traced rather than the box around it. The caught [refs] are the
 * selection's *contents* — fixed at the moment of the drag, because an edit does not reorder elements, so
 * the refs stay valid across a live move (`ElementRef`'s own note).
 *
 * Both shells work from this one type: the tablet's gesture controller and the window's canvas decide what a
 * drag *selected* here, and [SelectionEdit] is the one statement of what an edit does to it. Nothing in this
 * file knows about pixels, Compose or Android, so the maths is a JVM test.
 */
data class PageSelection(
    /** What the user traced: a rectangle or a freehand loop, in page points. */
    val region: SelectionRegion,
    /** The elements the region caught, by position — see [SelectionRegion.catches]. */
    val refs: Set<ElementRef>,
) {
    /** The box the outline and the handles sit on. */
    val bounds: Bounds get() = region.bounds

    /** True when nothing was caught: the outline is drawn, but there is nothing to move. */
    val isEmpty: Boolean get() = refs.isEmpty()
}

/**
 * The shape of a traced selection, in page points.
 *
 * A marquee is a [Rect] and a lasso a [Polygon]; both answer the same questions — their [bounds], whether a
 * point is [inside], and how they transform — so a tool that only needs "the region" does not branch on the
 * kind. A rotation turns a [Rect] into a [Polygon], because a rotated rectangle is not a rectangle.
 */
sealed interface SelectionRegion {

    /** The axis-aligned box the region covers. */
    val bounds: Bounds

    /** True when (x, y) — page points — is inside the traced region. */
    fun inside(x: Double, y: Double): Boolean

    /** The region nudged by (dx, dy) pt. */
    fun translate(dx: Double, dy: Double): SelectionRegion

    /** The region scaled by [sx]/[sy] about the anchor ([anchorX], [anchorY]) pt. */
    fun scale(sx: Double, sy: Double, anchorX: Double, anchorY: Double): SelectionRegion

    /** The region rotated by [radians] about the pivot ([pivotX], [pivotY]) pt — always a polygon after. */
    fun rotate(radians: Double, pivotX: Double, pivotY: Double): SelectionRegion

    /** Every element on [page] that lies wholly inside the region (`SelectionTester`'s rule). */
    fun catches(page: Page, onlyLayer: Int? = null): Set<ElementRef> = when (this) {
        is Rect -> SelectionTester.inRect(page, box, onlyLayer)
        is Polygon -> SelectionTester.inPolygon(page, points, onlyLayer)
    }

    /** A rectangle drag, in page points. */
    data class Rect(val box: Bounds) : SelectionRegion {
        override val bounds: Bounds get() = box

        override fun inside(x: Double, y: Double): Boolean = box.contains(x, y)

        override fun translate(dx: Double, dy: Double): SelectionRegion = Rect(box.translate(dx, dy))

        override fun scale(sx: Double, sy: Double, anchorX: Double, anchorY: Double): SelectionRegion {
            fun map(v: Double, anchor: Double, s: Double) = anchor + (v - anchor) * s
            return Rect(
                Bounds(
                    map(box.left, anchorX, sx), map(box.top, anchorY, sy),
                    map(box.right, anchorX, sx), map(box.bottom, anchorY, sy),
                ).normalised(),
            )
        }

        override fun rotate(radians: Double, pivotX: Double, pivotY: Double): SelectionRegion = Polygon(
            listOf(
                box.left to box.top, box.right to box.top,
                box.right to box.bottom, box.left to box.bottom,
            ).map { (x, y) -> rotated(x, y, radians, pivotX, pivotY) },
        )
    }

    /** A lasso, as the closed loop of page points the user traced. */
    data class Polygon(val points: List<Vec2>) : SelectionRegion {
        override val bounds: Bounds = run {
            var l = points.firstOrNull()?.x ?: 0.0
            var t = points.firstOrNull()?.y ?: 0.0
            var r = l
            var b = t
            for (p in points) {
                if (p.x < l) l = p.x
                if (p.x > r) r = p.x
                if (p.y < t) t = p.y
                if (p.y > b) b = p.y
            }
            Bounds(l, t, r, b)
        }

        override fun inside(x: Double, y: Double): Boolean {
            // Even-odd ray cast, the same rule `SelectionTester` uses for a lasso's containment.
            var inside = false
            var j = points.size - 1
            for (i in points.indices) {
                val a = points[i]
                val b = points[j]
                if ((a.y > y) != (b.y > y)) {
                    val cross = a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x)
                    if (x < cross) inside = !inside
                }
                j = i
            }
            return inside
        }

        override fun translate(dx: Double, dy: Double): SelectionRegion =
            Polygon(points.map { Vec2(it.x + dx, it.y + dy) })

        override fun scale(sx: Double, sy: Double, anchorX: Double, anchorY: Double): SelectionRegion =
            Polygon(
                points.map {
                    Vec2(anchorX + (it.x - anchorX) * sx, anchorY + (it.y - anchorY) * sy)
                },
            )

        override fun rotate(radians: Double, pivotX: Double, pivotY: Double): SelectionRegion =
            Polygon(points.map { rotated(it.x, it.y, radians, pivotX, pivotY) })
    }
}

/** A rectangle drag from two page points, whatever order the corners arrived in. */
fun rectRegion(x0: Double, y0: Double, x1: Double, y1: Double): SelectionRegion.Rect =
    SelectionRegion.Rect(Bounds(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1)))

/**
 * The edits a selection applies, in page space — **one** implementation, shared by both shells.
 *
 * Every function is built from the element-level half of [SelectionOps] (`translate`, `affineXY`,
 * `rotate`, `deleteOn`), which is also what the tablet's gesture controller calls, so the window and the
 * slate cannot end up disagreeing about whether a resize thickens an arrow or whether a rotation moves a
 * text box. What is new here is only the **region bookkeeping**: the outline has to travel with its
 * contents, and a rotation turns a rectangle into a polygon.
 *
 * Each op hands back the page *and* the selection, because a tool that commits an edit must also leave its
 * outline where the user can see it.
 */
object SelectionEdit {

    /** A drag: the caught elements and the outline move together. */
    fun move(page: Page, selection: PageSelection, dx: Double, dy: Double): Pair<Page, PageSelection> {
        if (selection.isEmpty || (dx == 0.0 && dy == 0.0)) return page to selection
        val moved = map(page, selection.refs) { SelectionOps.translate(it, dx, dy) }
        return moved to selection.copy(region = selection.region.translate(dx, dy))
    }

    /** A resize from a handle: the anchor is the opposite corner, which stays put. */
    fun scale(
        page: Page,
        selection: PageSelection,
        sx: Double,
        sy: Double,
        anchorX: Double,
        anchorY: Double,
    ): Pair<Page, PageSelection> {
        if (selection.isEmpty || (sx == 1.0 && sy == 1.0)) return page to selection
        val dx = anchorX * (1.0 - sx)
        val dy = anchorY * (1.0 - sy)
        val scaled = map(page, selection.refs) {
            SelectionOps.affineXY(it, sx, sy, dx, dy, scaleWidth = false)
        }
        return scaled to selection.copy(region = selection.region.scale(sx, sy, anchorX, anchorY))
    }

    /** A turn of the rotate handle, about the selection's own centre. */
    fun rotate(page: Page, selection: PageSelection, radians: Double): Pair<Page, PageSelection> {
        if (selection.isEmpty || radians == 0.0) return page to selection
        val cx = selection.bounds.let { (it.left + it.right) / 2.0 }
        val cy = selection.bounds.let { (it.top + it.bottom) / 2.0 }
        val turned = map(page, selection.refs) { SelectionOps.rotate(it, radians, cx, cy) }
        return turned to selection.copy(region = selection.region.rotate(radians, cx, cy))
    }

    /** `Delete`: the caught elements are removed, in one edit. */
    fun delete(page: Page, selection: PageSelection): Page =
        if (selection.isEmpty) page else SelectionOps.deleteOn(page, selection.refs)

    /** [page] with [op] applied to every element [refs] names, and to nothing else. */
    private fun map(page: Page, refs: Set<ElementRef>, op: (Element) -> Element): Page {
        if (refs.isEmpty()) return page
        val layers = page.layers.mapIndexed { li, layer ->
            val here = refs.filter { it.layerIndex == li }.map { it.elementIndex }.toSet()
            if (here.isEmpty()) layer
            else Layer(layer.elements.mapIndexed { ei, el -> if (ei in here) op(el) else el }, layer.name)
        }
        return page.copy(layers = layers)
    }
}

/** A point in page space rotated by [radians] about the pivot. */
private fun rotated(x: Double, y: Double, radians: Double, px: Double, py: Double): Vec2 {
    val c = cos(radians)
    val s = sin(radians)
    val rx = x - px
    val ry = y - py
    return Vec2(px + rx * c - ry * s, py + rx * s + ry * c)
}

/** The same box with its corners the right way round, after a negative scale flipped one axis. */
private fun Bounds.normalised(): Bounds =
    Bounds(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))
