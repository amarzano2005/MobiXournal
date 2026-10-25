package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A tiny **SVG path reader**, because the window's chrome glyphs are published as path data.
 *
 * The shared catalogue (`ChromeGlyphCatalogue`) holds the tablet's own pictures as the `d` strings
 * Material ships; the tablet hands them to an `ImageVector`, and the window has no such thing — Skia's
 * `Path` is built from imperative calls. This is the bridge, and it is deliberately small: the subset
 * those icons use (move, line, horizontal, vertical, cubic, smooth cubic, quadratic, smooth quadratic,
 * elliptical arc and close, absolute and relative) and nothing else. An unknown command throws, because
 * a glyph that silently drops a segment is a picture that is quietly wrong.
 *
 * The result is a **fillable** path in the source's own units (the 24-unit grid); scaling it is the
 * caller's job.
 */
internal fun svgPath(data: String): Path = PathWalker(data).also { it.walk() }.path

/**
 * One walk over one `d` string: the current point, where the subpath began, the previous control point
 * each smooth form reflects, and one method per command.
 *
 * Split this way because each command is a handful of lines and its own rule — a single `when` with all
 * ten inlined is a function nobody can read, and `SourceStructureTest` says so at 100 lines.
 */
private class PathWalker(private val data: String) {

    val path = Path()
    private val cursor = PathCursor(data)
    private var current = Offset.Zero
    private var start = Offset.Zero
    private var lastCubic: Offset? = null
    private var lastQuad: Offset? = null
    private var command = ' '

    fun walk() {
        while (cursor.hasMore()) {
            cursor.peekCommand()?.let { command = cursor.command() }
            val relative = command.isLowerCase()
            val origin = if (relative) current else Offset.Zero
            when (command.uppercaseChar()) {
                'M' -> move(origin)
                'L' -> line(origin)
                'H' -> horizontal(relative)
                'V' -> vertical(relative)
                'C' -> cubic(origin)
                'S' -> smoothCubic(origin)
                'Q' -> quad(origin)
                'T' -> smoothQuad(origin)
                'A' -> arc(origin)
                'Z' -> close()
                else -> error("unsupported path command '$command' in $data")
            }
        }
    }

    private fun move(origin: Offset) {
        current = origin + pair()
        path.moveTo(current.x, current.y)
        start = current
        // A second pair after a move is a line, as the grammar says.
        command = if (command.isLowerCase()) 'l' else 'L'
        forget()
    }

    private fun line(origin: Offset) {
        current = origin + pair()
        path.lineTo(current.x, current.y)
        forget()
    }

    private fun horizontal(relative: Boolean) {
        current = Offset(cursor.number() + (if (relative) current.x else 0f), current.y)
        path.lineTo(current.x, current.y)
        forget()
    }

    private fun vertical(relative: Boolean) {
        current = Offset(current.x, cursor.number() + (if (relative) current.y else 0f))
        path.lineTo(current.x, current.y)
        forget()
    }

    private fun cubic(origin: Offset) {
        val first = origin + pair()
        val second = origin + pair()
        current = origin + pair()
        path.cubicTo(first.x, first.y, second.x, second.y, current.x, current.y)
        lastCubic = second
        lastQuad = null
    }

    private fun smoothCubic(origin: Offset) {
        val first = lastCubic?.let { current * 2f - it } ?: current
        val second = origin + pair()
        current = origin + pair()
        path.cubicTo(first.x, first.y, second.x, second.y, current.x, current.y)
        lastCubic = second
        lastQuad = null
    }

    private fun quad(origin: Offset) {
        val control = origin + pair()
        current = origin + pair()
        path.quadraticBezierTo(control.x, control.y, current.x, current.y)
        lastQuad = control
        lastCubic = null
    }

    private fun smoothQuad(origin: Offset) {
        val control = lastQuad?.let { current * 2f - it } ?: current
        current = origin + pair()
        path.quadraticBezierTo(control.x, control.y, current.x, current.y)
        lastQuad = control
        lastCubic = null
    }

    private fun arc(origin: Offset) {
        val from = current
        val rx = cursor.number()
        val ry = cursor.number()
        val rotation = cursor.number()
        val largeArc = cursor.flag()
        val sweep = cursor.flag()
        current = origin + pair()
        path.arcTo(from, rx, ry, rotation, largeArc, sweep, current)
        forget()
    }

    private fun close() {
        path.close()
        current = start
        forget()
    }

    /** Neither smooth form reflects across a line: the reflection dies at every straight segment. */
    private fun forget() {
        lastCubic = null
        lastQuad = null
    }

    private fun pair(): Offset = Offset(cursor.number(), cursor.number())
}

/** A reader: one string, a position, and the grammar's token rules. */
internal class PathCursor(private val data: String) {

    private var index = 0

    fun hasMore(): Boolean {
        skipSeparators()
        return index < data.length
    }

    fun peekCommand(): Char? {
        skipSeparators()
        return if (index < data.length && data[index].isLetter()) data[index] else null
    }

    fun command(): Char = data[index++]

    /** An arc flag: the grammar allows `0`/`1` with no separator at all (`a 8 8 0 0 1 …`). */
    fun flag(): Boolean {
        skipSeparators()
        val bit = data[index]
        check(bit == '0' || bit == '1') { "not an arc flag: '$bit' in $data" }
        index++
        return bit == '1'
    }

    fun number(): Float {
        skipSeparators()
        val from = index
        if (index < data.length && (data[index] == '-' || data[index] == '+')) index++
        while (index < data.length && data[index].isDigit()) index++
        if (index < data.length && data[index] == '.') {
            index++
            while (index < data.length && data[index].isDigit()) index++
        }
        if (index < data.length && (data[index] == 'e' || data[index] == 'E')) {
            index++
            if (index < data.length && (data[index] == '-' || data[index] == '+')) index++
            while (index < data.length && data[index].isDigit()) index++
        }
        check(index > from) { "expected a number at $from in $data" }
        return data.substring(from, index).toFloat()
    }

    private fun skipSeparators() {
        while (index < data.length && (data[index] == ' ' || data[index] == ',' || data[index] == '\n' ||
                data[index] == '\r' || data[index] == '\t')
        ) {
            index++
        }
    }
}

/**
 * An elliptical arc as cubic segments — the SVG implementation note's endpoint-to-centre conversion,
 * split into quarter-turns so the error stays invisible at 20 dp.
 */
private fun Path.arcTo(
    from: Offset,
    rx0: Float,
    ry0: Float,
    rotationDeg: Float,
    largeArc: Boolean,
    sweep: Boolean,
    to: Offset,
) {
    if (rx0 == 0f || ry0 == 0f || from == to) {
        lineTo(to.x, to.y)
        return
    }
    val phi = Math.toRadians(rotationDeg.toDouble())
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)
    val dx = (from.x - to.x) / 2.0
    val dy = (from.y - to.y) / 2.0
    val x1 = cosPhi * dx + sinPhi * dy
    val y1 = -sinPhi * dx + cosPhi * dy
    var rx = abs(rx0.toDouble())
    var ry = abs(ry0.toDouble())
    val lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
    if (lambda > 1.0) {
        val grow = sqrt(lambda)
        rx *= grow
        ry *= grow
    }
    val numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
    val denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
    val coefficient = sqrt(max(0.0, numerator / denominator)) * (if (largeArc == sweep) -1.0 else 1.0)
    val cxp = coefficient * rx * y1 / ry
    val cyp = -coefficient * ry * x1 / rx
    val cx = cosPhi * cxp - sinPhi * cyp + (from.x + to.x) / 2.0
    val cy = sinPhi * cxp + cosPhi * cyp + (from.y + to.y) / 2.0

    val startAngle = angleOf(1.0, 0.0, (x1 - cxp) / rx, (y1 - cyp) / ry)
    var sweepAngle = angleOf((x1 - cxp) / rx, (y1 - cyp) / ry, (-x1 - cxp) / rx, (-y1 - cyp) / ry)
    if (!sweep && sweepAngle > 0) sweepAngle -= 2 * Math.PI
    if (sweep && sweepAngle < 0) sweepAngle += 2 * Math.PI

    val segments = ceil(abs(sweepAngle) / (Math.PI / 2)).toInt().coerceAtLeast(1)
    val step = sweepAngle / segments
    val handle = 4.0 / 3.0 * tan(step / 4.0)

    fun pointAt(angle: Double): Offset = Offset(
        (cx + rx * cosPhi * cos(angle) - ry * sinPhi * sin(angle)).toFloat(),
        (cy + rx * sinPhi * cos(angle) + ry * cosPhi * sin(angle)).toFloat(),
    )

    fun tangentAt(angle: Double): Offset = Offset(
        (-rx * cosPhi * sin(angle) - ry * sinPhi * cos(angle)).toFloat(),
        (-rx * sinPhi * sin(angle) + ry * cosPhi * cos(angle)).toFloat(),
    )

    var angle = startAngle
    var point = from
    repeat(segments) {
        val nextAngle = angle + step
        val nextPoint = pointAt(nextAngle)
        val startTangent = tangentAt(angle)
        val endTangent = tangentAt(nextAngle)
        cubicTo(
            (point.x + handle * startTangent.x).toFloat(),
            (point.y + handle * startTangent.y).toFloat(),
            (nextPoint.x - handle * endTangent.x).toFloat(),
            (nextPoint.y - handle * endTangent.y).toFloat(),
            nextPoint.x,
            nextPoint.y,
        )
        angle = nextAngle
        point = nextPoint
    }
}

/** The signed angle from vector (ux, uy) to (vx, vy). */
private fun angleOf(ux: Double, uy: Double, vx: Double, vy: Double): Double {
    val dot = ux * vx + uy * vy
    val lengths = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
    val magnitude = acos((dot / lengths).coerceIn(-1.0, 1.0))
    return if (ux * vy - uy * vx < 0) -magnitude else magnitude
}
