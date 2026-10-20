package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The three **headed figures** the shape tools draw — arrow, double arrow and the coordinate
 * diagram — as one geometry, written from scratch around a single rule:
 *
 * > **A head is sized by the pen, not by the shaft.**
 *
 * The previous geometry took the head as a fraction of the arrow's length (`0.18 × shaft`, floored at
 * 8 pt). Two consequences, both of them visible on the page: a short arrow wore a head nearly half its
 * own length — a dart, not an arrow — and a long one wore the same head as a much shorter one, so a
 * page of arrows never looked like one hand drew them. The coordinate diagram was worse: each axis
 * sized its own head from *its own* length, so a wide, flat drag produced a long x-axis with a big
 * head beside a stubby y-axis with a tiny one — a lopsided diagram.
 *
 * Here every head is `6 × strokeWidth` pt (floored at 10 pt so a hairline pen still shows one, capped
 * at 30 pt so a fat pen doesn't grow a beak), and the only other clamp is against the shaft itself:
 * a head may take at most [HEAD_MAX_SHARE_OF_SHAFT] of it, so shrinking a figure until the head would
 * swallow the shaft stops at something that still reads as an arrow. Because the rule is a function of
 * the width alone, *every* figure drawn with one pen wears the same head — an arrow, a double arrow,
 * and both axes of a diagram — and a resize that keeps the width keeps the head too
 * (`docs/stylus.md` → *A figure is resized, not thickened*).
 *
 * ## The vertex layouts
 *
 * Every figure is **one stroke**, so it round-trips as one `<stroke>` in `.xopp` and a selection
 * resize treats it as one object. A head is a V traced *through* its tip (`tip, barb, tip, barb`),
 * which is what lets a shaft and its head be one polyline:
 *
 * | figure | vertices | layout |
 * |---|---|---|
 * | arrow | 5 | `tail, tip, barbA, tip, barbB` |
 * | double arrow | 8 | `barbA, tail, barbB, tail, tip, barbA, tip, barbB` |
 * | coordinate diagram | 9 | `barbB, yTip, barbA, yTip, origin, xTip, barbA, xTip, barbB` |
 *
 * [FigureRedraw] reads a committed stroke back through exactly these layouts, so the two must be
 * changed together — and [FigureGeometryTest] pins each one.
 *
 * Pure — no Android types, no view — so the arithmetic is unit-tested on the JVM.
 */
internal object FigureGeometry {

    /**
     * The half-angle of the V, in degrees: 24°, i.e. a 48° opening. Slightly tighter than the 56° the
     * first geometry used, which read as a bird's foot on a long shaft.
     */
    const val HALF_ANGLE_DEG = 24.0

    /** Head length as a multiple of the stroke width: 6× is a pen's own "nib" — legible, not a beak. */
    private const val HEAD_WIDTH_RATIO = 6.0

    /** The smallest head worth drawing (pt), so a hairline pen still puts a head on its arrow. */
    private const val HEAD_MIN_PT = 10.0

    /** The largest head (pt), so a very fat pen doesn't get a disproportionate one. */
    private const val HEAD_MAX_PT = 30.0

    /** A head may take at most this share of its shaft; beyond it the figure stops reading as an arrow. */
    const val HEAD_MAX_SHARE_OF_SHAFT = 0.45

    /** Where a head's two barbs sit for a shaft arriving from ([fromX], [fromY]) at ([tipX], [tipY]). */
    data class Barbs(val tip: P, val a: P, val b: P)

    /** A bare point; the figures pass bare points around and only make [StrokePoint]s at the end. */
    data class P(val x: Double, val y: Double)

    /**
     * The head length (pt) a pen of [widthPt] wears on a shaft [shaftPt] pt long: six times the stroke
     * width, inside 10…30 pt, and never more than [HEAD_MAX_SHARE_OF_SHAFT] of the shaft.
     *
     * A degenerate shaft (0 pt) has no head at all — callers fall back to a plain line.
     */
    fun headPt(widthPt: Double, shaftPt: Double): Double {
        if (shaftPt <= 0.0) return 0.0
        val wanted = (HEAD_WIDTH_RATIO * widthPt).coerceIn(HEAD_MIN_PT, HEAD_MAX_PT)
        return minOf(wanted, HEAD_MAX_SHARE_OF_SHAFT * shaftPt)
    }

    /**
     * The barbs of a head [headPt] pt long at ([tipX], [tipY]), for a shaft arriving from
     * ([fromX], [fromY]) — the tip plus one point down each side of the shaft. The tip is part of the
     * result so a caller can trace `tip, barb, tip, barb` as one polyline.
     */
    fun barbs(
        fromX: Double, fromY: Double,
        tipX: Double, tipY: Double,
        headPt: Double,
        halfAngleDeg: Double = HALF_ANGLE_DEG,
    ): Barbs {
        // Pointing the way the shaft already travels, then turned back on each side of the tip.
        val dir = atan2(tipY - fromY, tipX - fromX)
        val half = Math.toRadians(halfAngleDeg)
        val a = dir + PI - half
        val b = dir + PI + half
        return Barbs(
            tip = P(tipX, tipY),
            a = P(tipX + headPt * cos(a), tipY + headPt * sin(a)),
            b = P(tipX + headPt * cos(b), tipY + headPt * sin(b)),
        )
    }

    /** An arrow from tail to tip, its head sized for [widthPt] (`tail, tip, barbA, tip, barbB`). */
    fun arrow(
        tailX: Double, tailY: Double,
        tipX: Double, tipY: Double,
        widthPt: Double,
    ): List<StrokePoint> {
        val shaft = hypot(tipX - tailX, tipY - tailY)
        if (shaft <= 0.0) return line(tailX, tailY, tipX, tipY, widthPt)
        return arrowWith(tailX, tailY, tipX, tipY, widthPt, headPt(widthPt, shaft))
    }

    /**
     * An arrow at an **explicit** head length and opening — the form a redraw re-emits, so a resized
     * arrow keeps the head it was drawn with instead of one recomputed from its new length.
     */
    fun arrowWith(
        tailX: Double, tailY: Double,
        tipX: Double, tipY: Double,
        widthPt: Double,
        headPt: Double,
        halfAngleDeg: Double = HALF_ANGLE_DEG,
    ): List<StrokePoint> {
        val shaft = hypot(tipX - tailX, tipY - tailY)
        if (shaft <= 0.0) return line(tailX, tailY, tipX, tipY, widthPt)
        val head = barbs(tailX, tailY, tipX, tipY, clampedHead(headPt, shaft), halfAngleDeg)
        return listOf(
            p(tailX, tailY, widthPt),
            p(head.tip.x, head.tip.y, widthPt),
            p(head.a.x, head.a.y, widthPt),
            p(head.tip.x, head.tip.y, widthPt),
            p(head.b.x, head.b.y, widthPt),
        )
    }

    /** A head at each end of one shaft (`barbA, tail, barbB, tail, tip, barbA, tip, barbB`). */
    fun doubleArrow(
        tailX: Double, tailY: Double,
        tipX: Double, tipY: Double,
        widthPt: Double,
    ): List<StrokePoint> {
        val shaft = hypot(tipX - tailX, tipY - tailY)
        if (shaft <= 0.0) return line(tailX, tailY, tipX, tipY, widthPt)
        return doubleArrowWith(tailX, tailY, tipX, tipY, widthPt, headPt(widthPt, shaft))
    }

    /** A two-headed arrow at explicit head lengths — one number for both ends, so they stay a pair. */
    fun doubleArrowWith(
        tailX: Double, tailY: Double,
        tipX: Double, tipY: Double,
        widthPt: Double,
        headPt: Double,
        halfAngleDeg: Double = HALF_ANGLE_DEG,
    ): List<StrokePoint> {
        val shaft = hypot(tipX - tailX, tipY - tailY)
        if (shaft <= 0.0) return line(tailX, tailY, tipX, tipY, widthPt)
        val head = clampedHead(headPt, shaft)
        val atTail = barbs(tipX, tipY, tailX, tailY, head, halfAngleDeg)
        val atTip = barbs(tailX, tailY, tipX, tipY, head, halfAngleDeg)
        return listOf(
            p(atTail.b.x, atTail.b.y, widthPt),
            p(atTail.tip.x, atTail.tip.y, widthPt),
            p(atTail.a.x, atTail.a.y, widthPt),
            p(atTail.tip.x, atTail.tip.y, widthPt),
            p(atTip.tip.x, atTip.tip.y, widthPt),
            p(atTip.a.x, atTip.a.y, widthPt),
            p(atTip.tip.x, atTip.tip.y, widthPt),
            p(atTip.b.x, atTip.b.y, widthPt),
        )
    }

    /**
     * The coordinate diagram: two arrowed axes meeting at ([originX], [originY]), running out to
     * ([xTipX], [xTipY]) and ([yTipX], [yTipY]).
     *
     * Both heads come from **one** head length — the smaller the two axes allow — so a wide, flat
     * diagram wears two equal heads instead of a big one and a small one.
     *
     * A drag with no extent on an axis leaves that axis out entirely (there is no arrow to put a head
     * on); what is left is a single arrow, which is exactly what a horizontal or vertical drag means.
     */
    fun axes(
        originX: Double, originY: Double,
        xTipX: Double, xTipY: Double,
        yTipX: Double, yTipY: Double,
        widthPt: Double,
    ): List<StrokePoint> {
        val xShaft = hypot(xTipX - originX, xTipY - originY)
        val yShaft = hypot(yTipX - originX, yTipY - originY)
        if (xShaft <= 0.0 && yShaft <= 0.0) {
            return line(originX, originY, xTipX, xTipY, widthPt)
        }
        val head = if (xShaft <= 0.0) {
            headPt(widthPt, yShaft)
        } else if (yShaft <= 0.0) {
            headPt(widthPt, xShaft)
        } else {
            headPt(widthPt, minOf(xShaft, yShaft))
        }
        return axesWith(
            originX = originX, originY = originY,
            xTipX = xTipX, xTipY = xTipY,
            yTipX = yTipX, yTipY = yTipY,
            widthPt = widthPt,
            xHeadPt = if (xShaft <= 0.0) 0.0 else head,
            yHeadPt = if (yShaft <= 0.0) 0.0 else head,
        )
    }

    /**
     * The diagram at **explicit** head lengths — the form [FigureRedraw] re-emits. A zero head length
     * means "that axis has no head", which is what a drag with no extent on that axis produces, so the
     * drawn and the redrawn layout stay in step.
     */
    fun axesWith(
        originX: Double, originY: Double,
        xTipX: Double, xTipY: Double,
        yTipX: Double, yTipY: Double,
        widthPt: Double,
        xHeadPt: Double, yHeadPt: Double,
        halfAngleDeg: Double = HALF_ANGLE_DEG,
    ): List<StrokePoint> {
        val out = ArrayList<StrokePoint>(9)
        // The y axis first: its head is drawn tail-to-tip (walking out to the tip), then the origin is
        // the vertex both axes share, then the x axis runs out to its own head. An axis with no extent
        // is not drawn at all — there is no shaft and no head to put on it.
        val hasY = yHeadPt > 0.0 || yTipX != originX || yTipY != originY
        val hasX = xHeadPt > 0.0 || xTipX != originX || xTipY != originY
        if (yHeadPt > 0.0) {
            val y = barbs(originX, originY, yTipX, yTipY, yHeadPt, halfAngleDeg)
            out += p(y.b.x, y.b.y, widthPt)
            out += p(y.tip.x, y.tip.y, widthPt)
            out += p(y.a.x, y.a.y, widthPt)
            out += p(y.tip.x, y.tip.y, widthPt)
        } else if (hasY) {
            out += p(yTipX, yTipY, widthPt)
        }
        out += p(originX, originY, widthPt)
        if (xHeadPt > 0.0) {
            val x = barbs(originX, originY, xTipX, xTipY, xHeadPt, halfAngleDeg)
            out += p(x.tip.x, x.tip.y, widthPt)
            out += p(x.a.x, x.a.y, widthPt)
            out += p(x.tip.x, x.tip.y, widthPt)
            out += p(x.b.x, x.b.y, widthPt)
        } else if (hasX) {
            out += p(xTipX, xTipY, widthPt)
        }
        return out
    }

    /** A head length held to the shaft it has to fit on — the one clamp a redraw may never skip. */
    fun clampedHead(headPt: Double, shaftPt: Double): Double =
        headPt.coerceIn(0.0, HEAD_MAX_SHARE_OF_SHAFT * shaftPt)

    private fun p(x: Double, y: Double, w: Double) = StrokePoint(x, y, w)

    /** A figure collapsed by its own drag: a plain line, the one thing left of it. */
    private fun line(sx: Double, sy: Double, ex: Double, ey: Double, w: Double) =
        listOf(p(sx, sy, w), p(ex, ey, w))
}
