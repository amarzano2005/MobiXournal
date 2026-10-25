package com.mobixournal.desktop

import androidx.compose.ui.graphics.Path
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.InkGeometry
import java.util.IdentityHashMap

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
 * stroke is tiny (a page is drawn at one or two scales) and the whole cache is weak, so a closed document's
 * strokes are collected with the page that held them.
 *
 * Live drafts are cached too, by the list's identity and its size, which is what makes a frame that only
 * redraws (a scroll, a hover, a selection moving) cost nothing to paint.
 */
internal object StrokePathCache {

    /**
     * What a stroke needs painted: the polyline, the filled taper of a pressure stroke (null for a uniform
     * one), and the highlighter's band width in pixels.
     */
    class Shapes(val polyline: Path, val taper: Path?, val bandWidthPx: Float?)

    private val byStroke = IdentityHashMap<Stroke, MutableMap<Float, Shapes>>()
    private val byDraft = IdentityHashMap<Any, MutableMap<DraftKey, Shapes>>()

    /**
     * How many geometries have been **built** since the cache was last cleared.
     *
     * Read by the tests and by nothing else: a frame's cost is a wall-clock number a shared machine
     * distorts, while "this erase rebuilt no outline at all" is a count that cannot be flattered.
     */
    var builds: Int = 0
        private set

    /**
     * A draft's identity plus the length and the **last sample** it has reached.
     *
     * Both are needed. A pen's draft grows, so its length says what it is; a **figure's** draft is
     * rewritten each frame at the same length (a rectangle is four corners however far it is dragged), so
     * a key of length alone handed back the first frame's geometry for the whole drag — the preview stood
     * still and jumped at the lift, which reads as a lag. The last point moves with the hand in both cases.
     */
    private data class DraftKey(
        val size: Int,
        val scale: Float,
        val tool: Tool,
        val dash: LineStyle,
        val last: StrokePoint?,
    )

    /** The shapes of one recorded stroke at [scale]. */
    @Synchronized
    fun of(stroke: Stroke, scale: Float): Shapes {
        val perScale = byStroke.getOrPut(stroke) { HashMap(2) }
        perScale[scale]?.let { return it }
        val shapes = build(stroke.points, stroke.tool, stroke.lineStyle, scale)
        // A page is drawn at one scale at a time; keeping two keeps a zoom round-trip warm without
        // letting a wheel gesture's every frame live for ever.
        if (perScale.size >= 2) perScale.clear()
        perScale[scale] = shapes
        return shapes
    }

    /**
     * The shapes of a **draft** in progress.
     *
     * [identity] is the list the draft lives in (the canvas's two state lists), so the cache entry follows
     * the one stroke being drawn and is dropped the moment another begins. The size is in the key because
     * every batch makes the shape longer.
     */
    @Synchronized
    fun draft(identity: Any, size: Int, tool: Tool, lineStyle: LineStyle, points: List<StrokePoint>, scale: Float): Shapes {
        val key = DraftKey(size, scale, tool, lineStyle, points.lastOrNull())
        val perDraft = byDraft.getOrPut(identity) { HashMap(4) }
        perDraft[key]?.let { return it }
        if (perDraft.size >= 4) perDraft.clear()
        val shapes = build(points, tool, lineStyle, scale)
        perDraft[key] = shapes
        return shapes
    }

    private fun build(points: List<StrokePoint>, tool: Tool, lineStyle: LineStyle, scale: Float): Shapes {
        builds++
        val polyline = PageInk.polyline(points, scale)
        // Only a **pressure** stroke needs its outline filled; a band or a uniform line is the polyline
        // with a width, and building an outline for it would be a path nobody draws.
        val taper = if (tool != Tool.HIGHLIGHTER && !InkGeometry.uniformWidth(points)) {
            PageInk.taperPath(points, scale)
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
        builds = 0
    }
}
