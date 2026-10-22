package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent content-aware auto-slicer for continuous and infinite canvas strips (`docs/infinite-canvas.md`).
 *
 * Rather than cutting mechanically at rigid heights (H, 2H, 3H), the slicer searches a +/- [DEFAULT_SEARCH_WINDOW_PT]
 * window (default 40.0 pt) around each target seam for whitespace bands. If a stroke, table, plot, or formula
 * spans the cut target, the cut is shifted above or below the element to keep it intact on a single page.
 */
object CanvasAutoSlicer {

    /** Default whitespace search window: +/- 40 pt around target cut. */
    const val DEFAULT_SEARCH_WINDOW_PT: Double = 40.0

    /** Standard A4 dimensions in points at 72 DPI. */
    const val A4_WIDTH_PT: Double = 595.28
    const val A4_HEIGHT_PT: Double = 841.89

    /** Vertical interval occupied by an element in continuous strip coordinates. */
    private data class ElementSpan(val start: Double, val end: Double, val element: Element)

    /** Compute the axis-aligned bounding box of any [element] for slicing purposes. */
    fun boundsOf(element: Element): Bounds = when (element) {
        is Stroke -> strokeBounds(element)
        is TextElement -> textBounds(element)
        is ImageElement -> Bounds(element.left, element.top, element.right, element.bottom)
        is TexImageElement -> Bounds(element.left, element.top, element.right, element.bottom)
        is PlotElement -> Bounds(element.left, element.top, element.right, element.bottom)
        is TableElement -> Bounds(element.left, element.top, element.left + element.width, element.top + element.height)
        is ParametricShapeElement -> shapeBounds(element)
        is CircuitElement -> Bounds(element.positionX, element.positionY, element.positionX + 50.0, element.positionY + 50.0)
        is RawElement -> Bounds(0.0, 0.0, 0.0, 0.0)
    }

    private fun strokeBounds(s: Stroke): Bounds {
        val pts = s.points
        if (pts.isEmpty()) return Bounds(0.0, 0.0, 0.0, 0.0)
        var left = Double.MAX_VALUE
        var top = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        var bottom = -Double.MAX_VALUE
        for (p in pts) {
            val half = p.width / 2.0
            if (p.x - half < left) left = p.x - half
            if (p.y - half < top) top = p.y - half
            if (p.x + half > right) right = p.x + half
            if (p.y + half > bottom) bottom = p.y + half
        }
        return Bounds(left, top, right, bottom)
    }

    private fun textBounds(t: TextElement): Bounds {
        val lines = t.content.split("\n")
        val h = lines.size * t.size * 1.3
        val w = (lines.maxOfOrNull { it.length } ?: 1) * t.size * 0.62
        return Bounds(t.x, t.y, t.x + w, t.y + h)
    }

    private fun shapeBounds(s: ParametricShapeElement): Bounds {
        val v = s.vertices
        if (v.isEmpty()) return Bounds(0.0, 0.0, 0.0, 0.0)
        val left = v.minOf { it.x } - s.strokeWidth / 2.0
        val top = v.minOf { it.y } - s.strokeWidth / 2.0
        val right = v.maxOf { it.x } + s.strokeWidth / 2.0
        val bottom = v.maxOf { it.y } + s.strokeWidth / 2.0
        return Bounds(left, top, right, bottom)
    }

    /** Translate [element] by ([dx], [dy]) points. */
    fun translateElement(element: Element, dx: Double, dy: Double): Element = when (element) {
        is Stroke -> element.copy(points = element.points.map { it.copy(x = it.x + dx, y = it.y + dy) })
        is TextElement -> element.copy(x = element.x + dx, y = element.y + dy)
        is ImageElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        is TexImageElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        is PlotElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        is TableElement -> element.copy(left = element.left + dx, top = element.top + dy)
        is ParametricShapeElement -> element.copy(vertices = element.vertices.map { ShapeVertex(it.x + dx, it.y + dy) })
        is CircuitElement -> element.copy(positionX = element.positionX + dx, positionY = element.positionY + dy)
        is RawElement -> element
    }

    /** Compute clean vertical slice seams along [startYPt..totalHeightPt]. */
    fun findVerticalCuts(
        elements: List<Element>,
        targetHeightPt: Double = A4_HEIGHT_PT,
        totalHeightPt: Double,
        startYPt: Double = 0.0,
        windowPt: Double = DEFAULT_SEARCH_WINDOW_PT,
    ): List<Double> {
        if (totalHeightPt <= startYPt) return listOf(startYPt, startYPt + targetHeightPt)

        val intervals = elements.map {
            val b = boundsOf(it)
            ElementSpan(b.top, b.bottom, it)
        }.filter { it.end > it.start }

        val cuts = mutableListOf(startYPt)
        var currentY = startYPt

        while (currentY < totalHeightPt) {
            val target = currentY + targetHeightPt
            if (target >= totalHeightPt) {
                cuts.add(maxOf(target, totalHeightPt))
                break
            }

            val windowMin = max(currentY + targetHeightPt * 0.4, target - windowPt)
            val windowMax = target + windowPt
            val cut = findBestSeam(intervals, target, windowMin, windowMax, windowPt)
            cuts.add(cut)
            currentY = cut
        }

        return cuts
    }

    private fun findBestSeam(
        intervals: List<ElementSpan>,
        target: Double,
        windowMin: Double,
        windowMax: Double,
        windowPt: Double,
    ): Double {
        val relevant = intervals.filter { it.end > windowMin && it.start < windowMax }
        if (relevant.isEmpty()) return target

        // If target itself does not split any element, cut directly at target
        val targetCollisions = relevant.filter { it.start < target && target < it.end }
        if (targetCollisions.isEmpty()) return target

        // Merge overlapping forbidden blocks
        val sorted = relevant.sortedBy { it.start }
        val merged = mutableListOf<Pair<Double, Double>>()
        for (span in sorted) {
            val s = max(windowMin, span.start)
            val e = min(windowMax, span.end)
            if (merged.isEmpty() || merged.last().second < s) {
                merged.add(s to e)
            } else {
                val last = merged.removeAt(merged.size - 1)
                merged.add(last.first to max(last.second, e))
            }
        }

        val whitespaceCandidates = mutableListOf<Double>()
        if (merged.first().first > windowMin) {
            whitespaceCandidates.add((windowMin + merged.first().first) / 2.0)
        }
        for (i in 0 until merged.size - 1) {
            val gapStart = merged[i].second
            val gapEnd = merged[i + 1].first
            if (gapEnd > gapStart) {
                whitespaceCandidates.add((gapStart + gapEnd) / 2.0)
            }
        }
        if (merged.last().second < windowMax) {
            whitespaceCandidates.add((merged.last().second + windowMax) / 2.0)
        }

        if (whitespaceCandidates.isNotEmpty()) {
            return whitespaceCandidates.minByOrNull { abs(it - target) } ?: target
        }

        // Entire window is occupied: shift above or below the colliding element
        val element = targetCollisions.minByOrNull { abs((it.start + it.end) / 2.0 - target) }!!
        val shiftAbove = element.start - 1.0
        val shiftBelow = element.end + 1.0

        return when {
            shiftAbove >= windowMin && abs(shiftAbove - target) <= abs(shiftBelow - target) -> shiftAbove
            shiftBelow <= windowMax + windowPt -> shiftBelow
            shiftAbove >= windowMin -> shiftAbove
            else -> target
        }
    }

    /**
     * Slice a continuous vertical strip of elements into standard multi-page [Page] documents.
     */
    fun sliceVerticalStripToPages(
        elements: List<Element>,
        pageWidthPt: Double = A4_WIDTH_PT,
        targetPageHeightPt: Double = A4_HEIGHT_PT,
        totalHeightPt: Double = elements.maxOfOrNull { boundsOf(it).bottom } ?: targetPageHeightPt,
        startYPt: Double = 0.0,
        windowPt: Double = DEFAULT_SEARCH_WINDOW_PT,
        background: Background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
    ): List<Page> {
        val cuts = findVerticalCuts(elements, targetPageHeightPt, totalHeightPt, startYPt, windowPt)
        val pages = mutableListOf<Page>()

        for (i in 0 until cuts.size - 1) {
            val cutTop = cuts[i]
            val cutBottom = cuts[i + 1]
            val pageHeight = cutBottom - cutTop

            val pageElements = elements.filter {
                val b = boundsOf(it)
                val midY = (b.top + b.bottom) / 2.0
                midY >= cutTop && midY < cutBottom
            }.map { translateElement(it, 0.0, -cutTop) }

            pages.add(
                Page(
                    width = pageWidthPt,
                    height = pageHeight,
                    background = background,
                    layers = listOf(Layer(elements = pageElements)),
                ),
            )
        }

        return pages
    }

    /**
     * Slice a continuous horizontal strip of elements into discrete pages.
     */
    fun sliceHorizontalStripToPages(
        elements: List<Element>,
        targetPageWidthPt: Double = A4_WIDTH_PT,
        pageHeightPt: Double = A4_HEIGHT_PT,
        totalWidthPt: Double = elements.maxOfOrNull { boundsOf(it).right } ?: targetPageWidthPt,
        startXPt: Double = 0.0,
        windowPt: Double = DEFAULT_SEARCH_WINDOW_PT,
        background: Background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
    ): List<Page> {
        if (totalWidthPt <= startXPt) return listOf(
            Page(width = targetPageWidthPt, height = pageHeightPt, background = background, layers = listOf(Layer(elements = emptyList()))),
        )

        val intervals = elements.map {
            val b = boundsOf(it)
            ElementSpan(b.left, b.right, it)
        }.filter { it.end > it.start }

        val cuts = mutableListOf(startXPt)
        var currentX = startXPt

        while (currentX < totalWidthPt) {
            val target = currentX + targetPageWidthPt
            if (target >= totalWidthPt) {
                cuts.add(maxOf(target, totalWidthPt))
                break
            }
            val windowMin = max(currentX + targetPageWidthPt * 0.4, target - windowPt)
            val windowMax = target + windowPt
            val cut = findBestSeam(intervals, target, windowMin, windowMax, windowPt)
            cuts.add(cut)
            currentX = cut
        }

        val pages = mutableListOf<Page>()
        for (i in 0 until cuts.size - 1) {
            val cutLeft = cuts[i]
            val cutRight = cuts[i + 1]
            val pageWidth = cutRight - cutLeft

            val pageElements = elements.filter {
                val b = boundsOf(it)
                val midX = (b.left + b.right) / 2.0
                midX >= cutLeft && midX < cutRight
            }.map { translateElement(it, -cutLeft, 0.0) }

            pages.add(
                Page(
                    width = pageWidth,
                    height = pageHeightPt,
                    background = background,
                    layers = listOf(Layer(elements = pageElements)),
                ),
            )
        }
        return pages
    }
}
