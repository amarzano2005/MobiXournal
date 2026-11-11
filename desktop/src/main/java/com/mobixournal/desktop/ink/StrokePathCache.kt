package com.mobixournal.desktop.ink
import com.mobixournal.desktop.sheet.PageInk
import com.mobixournal.desktop.sheet.number

import androidx.compose.ui.graphics.Path
import com.mobixournal.desktop.shell.DesktopDiagnostics
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.InkGeometry
import com.mobixournal.collections.IdentityLruCache

/**
 * The **path of a stroke, built once** and reused by every frame that paints it.
 *
 * A stroke's geometry is a function of its points, its width rule and the scale it is drawn at — and the
 * points do not change once the lift has written them. The window rebuilt those paths anyway: a page's
 * picture is re-recorded whenever the ink under it changes (an erase, an undo, a move, a reflow), and each
 * re-record walked every stroke and rebuilt its outline. On a written page that is thousands of outlines
 * per frame while an eraser drags — the lag a person feels as "the strokes are handled badly" — and it is
 * work whose answer was already known.
 *
 * Keyed by **identity**, not equality: a stroke is a data class over a long point list, so hashing one is
 * as expensive as drawing it, and the editor shares the elements it did not change, which is exactly the
 * case this cache is for. Scale is part of the key because a zoom is a different path; the map for one
 * stroke is tiny (a page is drawn at one or two scales) and global LRU budgets bound the retained identities and geometry points.
 *
 * Live drafts are cached too, by the list's identity and the box its points cover, which is what makes a
 * frame that only redraws (a scroll, a hover, a selection moving) cost nothing to paint.
 */
internal object StrokePathCache {

    /**
     * What a stroke needs painted: the polyline, the filled taper of a pressure stroke (null for a uniform
     * one), and the highlighter's band width in pixels.
     */
    class Shapes(val polyline: Path, val taper: Path?, val bandWidthPx: Float?)

    private val smoothing = IdentityLruCache<Any, IncrementalSmoothing>(32, 200_000) { it.points.size.toLong() }
    private val tapers = IdentityLruCache<Any, com.mobixournal.render.IncrementalTaper>(32, 200_000) { it.retainedPoints.toLong() }
    private class Cached(val shapes: Shapes, val weight: Long)
    private val byStroke = IdentityLruCache<Stroke, Map<Float, Cached>>(4096, 250_000) { values ->
        values.values.sumOf { it.weight }
    }
    private val byDraft = IdentityLruCache<Any, Map<DraftKey, Cached>>(32, 200_000) { values ->
        values.values.sumOf { it.weight }
    }
    internal val cachedIdentities: Int get() = byStroke.size + byDraft.size
    internal val cachedGeometryPoints: Long get() = byStroke.cachedWeight + byDraft.cachedWeight

    /**
     * How many geometries have been **built** since the cache was last cleared.
     *
     * Read by the tests and by nothing else: a frame's cost is a wall-clock number a shared machine
     * distorts, while "this erase rebuilt no outline at all" is a count that cannot be flattered.
     */
    var builds: Int = 0
        private set

    /**
     * A draft's identity plus what its geometry currently **is**: the length it has reached, the box it
     * covers, the scale and the rule it is drawn with.
     *
     * The **box**, not the last sample. A figure's draft is rewritten each frame at the same length (a
     * rectangle is four corners however far it is dragged), so a key of length alone handed back the first
     * frame's geometry for the whole drag — the preview stood still and jumped at the lift. The last sample
     * seems to answer that, and does for an open figure, but **not for a closed one**: a rectangle's and an
     * ellipse's last vertex is the figure's first one, the corner the press landed on, which never moves.
     * Every frame of such a drag matched the first, so the preview stayed a few points wide until the lift.
     * A box moves with the pointer whatever the figure's point order is.
     */
    private data class DraftKey(
        val size: Int,
        val scale: Float,
        val tool: Tool,
        val dash: LineStyle,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val smooth: Boolean = true,
    )

    /** A stroke is a figure when explicitly marked or when it has constant width and zero telemetry dtMs. */
    internal fun isFigure(stroke: Stroke): Boolean {
        if (stroke.extraAttrs.containsKey("shape") || stroke.extraAttrs.containsKey("figure")) return true
        if (stroke.uniformWidth && stroke.points.isNotEmpty() && stroke.points.all { it.dtMs == 0 }) {
            return true
        }
        return false
    }

    /** The shapes of one recorded stroke at [scale]. */
    @Synchronized
    fun of(stroke: Stroke, scale: Float): Shapes {
        val perScale = byStroke[stroke].orEmpty()
        perScale[scale]?.let { return it.shapes }
        val shapes = build(stroke.points, stroke.tool, stroke.lineStyle, scale, smooth = !isFigure(stroke))
        // A page is drawn at one scale at a time; keeping two keeps a zoom round-trip warm without
        // letting a wheel gesture's every frame live for ever.
        val retained = if (perScale.size >= 2) emptyMap() else perScale
        byStroke.put(stroke, retained + (scale to Cached(shapes, geometryWeight(stroke.points, shapes))))
        return shapes
    }

    /**
     * The shapes of a **draft** in progress.
     *
     * [identity] is the list the draft lives in (the canvas's two state lists), so the cache entry follows
     * the one stroke being drawn and is dropped the moment another begins. The length is in the key because
     * every batch makes the shape longer — read from [points] itself, so the key cannot describe a shape the
     * cache does not hold. [smooth] is false for geometric shapes so edges stay crisp and straight.
     */
    @Synchronized
    fun draft(
        identity: Any,
        tool: Tool,
        lineStyle: LineStyle,
        points: List<StrokePoint>,
        scale: Float,
        smooth: Boolean = true,
    ): Shapes {
        // One numeric pass for the box: a fraction of the cost of building the path it guards against
        // rebuilding, and it is the only part of a draft that changes for every figure that is dragged.
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (point in points) {
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        // The key's length is the geometry's own length, never a number the caller read elsewhere: a
        // caller that asked with a stale count (a draft list read once for its points and once for its
        // size, while the pen appended between the two) got a key describing a stroke it did not store,
        // and one frame painted a deformed draft before the next repaired it.
        val key = DraftKey(points.size, scale, tool, lineStyle, left, top, right, bottom, smooth)
        val perDraft = byDraft[identity].orEmpty()
        perDraft[key]?.let { return it.shapes }
        val sampled = if (smooth) (smoothing[identity] ?: IncrementalSmoothing()).also {
            it.update(points)
            smoothing.put(identity, it)
        } else null
        val numeric = if (sampled != null) tapers[identity] ?: com.mobixournal.render.IncrementalTaper() else null
        val shapes = build(points, tool, lineStyle, scale, smooth, sampled, numeric)
        if (numeric != null) tapers.put(identity, numeric)
        val retained = if (perDraft.size >= 4) emptyMap() else perDraft
        byDraft.put(identity, retained + (key to Cached(shapes, geometryWeight(points, shapes))))
        return shapes
    }

    private fun geometryWeight(points: List<StrokePoint>, shapes: Shapes): Long =
        points.size.toLong() * if (shapes.taper == null) 1 else 2

    /**
     * Diagnose-only: the draft's incremental geometry against a **full rebuild** of the same samples.
     *
     * Both halves are checked, because a stroke is two shapes here: the smoothed points the outline is
     * fanned around, and the contour itself. A disagreement is not a slow frame — it is a line drawn in a
     * shape the hand did not make, which is the report this probe answers. It writes one line per
     * disagreement, at the moment it happens, and it mutates nothing (the comparison builds its own
     * smoothing and its own projection).
     */
    private fun auditDraft(sampled: IncrementalSmoothing, incremental: FloatArray, scale: Float) {
        val fresh = IncrementalSmoothing().also { it.update(sampled.points.toList()) }
        if (fresh.points.size != sampled.points.size) {
            DesktopDiagnostics.draftMismatch(
                "smoothed points ${sampled.points.size} incremental vs ${fresh.points.size} full",
            )
            return
        }
        var worstPoint = 0.0
        for (i in 0 until fresh.points.size) {
            val a = sampled.points[i]
            val b = fresh.points[i]
            val d = kotlin.math.hypot(a.x - b.x, a.y - b.y)
            if (d > worstPoint) worstPoint = d
        }
        val full = InkGeometry.taperOutline(fresh.points, scale, 0f, 0f)
        if (full.size != incremental.size) {
            DesktopDiagnostics.draftMismatch(
                "outline ${incremental.size} values incremental vs ${full.size} full at ${fresh.points.size} points",
            )
            return
        }
        var worstOutline = 0f
        for (i in full.indices) {
            val d = kotlin.math.abs(incremental[i] - full[i])
            if (d > worstOutline) worstOutline = d
        }
        if (worstPoint > 0.01 || worstOutline > 0.5f) {
            DesktopDiagnostics.draftMismatch(
                "points off " + String.format(java.util.Locale.ROOT, "%.3f", worstPoint) +
                    " pt, outline off " + String.format(java.util.Locale.ROOT, "%.2f", worstOutline) +
                    " px at ${fresh.points.size} points",
            )
        }
    }

    private fun build(
        points: List<StrokePoint>,
        tool: Tool,
        lineStyle: LineStyle,
        scale: Float,
        smooth: Boolean = true,
        sampled: IncrementalSmoothing? = null,
        numeric: com.mobixournal.render.IncrementalTaper? = null,
    ): Shapes {
        builds++
        val polyline = PageInk.polyline(points, scale, smooth = smooth, curves = sampled?.curves)
        // Only a **pressure** stroke needs its outline filled; a band or a uniform line is the polyline
        // with a width, and building an outline for it would be a path nobody draws.
        val taper = if (tool != Tool.HIGHLIGHTER && !InkGeometry.uniformWidth(points)) {
            if (sampled != null && numeric != null) {
                val outline = numeric.outline(sampled.points, scale, 0f, 0f, sampled.stablePrefix)
                // Asked only when the window keeps the record: a draft's outline is the one geometry that
                // can differ from its own full rebuild, and a report of "it warps for an instant" needs the
                // difference in pixels, not a theory (`--diagnose`).
                if (DesktopDiagnostics.enabled) auditDraft(sampled, outline, scale)
                PageInk.outlinePath(outline)
            } else PageInk.taperPath(points, scale, smooth = smooth)
        } else {
            null
        }
        val band = if (tool == Tool.HIGHLIGHTER) {
            (InkGeometry.bandWidth(points) * scale).toFloat().coerceAtLeast(0.5f)
        } else {
            null
        }
        return Shapes(polyline, taper, band)
    }

    /** Drop everything — the window's own tests measure that a cache cannot outlive its page. */
    @Synchronized
    fun clear() {
        byStroke.clear()
        byDraft.clear()
        smoothing.clear()
        tapers.clear()
        builds = 0
    }
}
