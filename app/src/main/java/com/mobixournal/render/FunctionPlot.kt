/**
 * The **2D function plotter**: `f(x)` in, page geometry out.
 *
 * Everything here is pure — a small expression parser, the sampling, and the mapping onto page points
 * — so a plot is unit-tested on the JVM and the UI only collects the formula and inserts the result.
 * The plot comes back as **ordinary strokes** (frame, axes, ticks and the curve) plus a few **labels**,
 * which means it behaves like anything else on the page: it saves to the `.xopp` unchanged, reopens in
 * desktop Xournal++, and can be selected, moved, restyled or erased. Nothing about the formula itself
 * is stored, because the format has nowhere to put it (the project's scope rule) — the plot *is* ink.
 *
 * The parser takes the arithmetic people actually type: `+ - * / ^`, parentheses, unary minus, the
 * constants `pi` and `e`, and the functions in [FUNCTIONS]. Implicit multiplication is **not**
 * supported (`2x` is a typo; write `2*x`). A formula that doesn't parse compiles to `null` rather than
 * throwing, so a typo in the dialog can never take the editor down.
 */
package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.E
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/** A compiled formula: one variable `x` in, one value out. */
fun interface Formula {
    /** The value at [x], or NaN where the expression is undefined (a pole, a domain edge). */
    fun at(x: Double): Double
}

/** A tick label the plot wants drawn beside an axis: [text] anchored at ([x], [y]) top-left, in pt. */
data class PlotLabel(val text: String, val x: Double, val y: Double)

/** One plotted function: the lines to insert, and the numbers to write beside its axes. */
data class Plot(val strokes: List<List<StrokePoint>>, val labels: List<PlotLabel>)

object FunctionPlot {

    /** The one-variable functions the parser knows, under the names they are written. */
    val FUNCTIONS: Map<String, (Double) -> Double> = mapOf(
        "sin" to ::sin, "cos" to ::cos, "tan" to ::tan,
        "asin" to ::asin, "acos" to ::acos, "atan" to ::atan,
        "sinh" to ::sinh, "cosh" to ::cosh, "tanh" to ::tanh,
        "sqrt" to ::sqrt, "abs" to ::abs, "ln" to ::ln, "log" to ::log10, "exp" to ::exp,
        "floor" to ::floor, "ceil" to ::ceil, "round" to ::round,
    )

    /** The constants the parser knows. */
    val CONSTANTS: Map<String, Double> = mapOf("pi" to PI, "e" to E)

    /** The formula the dialog offers before the user types one. */
    const val DEFAULT_SOURCE: String = "sin(x)"

    /** Default sample count across the x range: fine enough that a curve reads as one. */
    const val DEFAULT_SAMPLES: Int = 600

    /** Ceiling on the sample count, so a pasted-in huge number can't stall a frame. */
    const val MAX_SAMPLES: Int = 4000

    /** How much of the sampled y range is left as padding above and below, as a fraction. */
    private const val Y_PADDING = 0.08

    /**
     * How large a step between neighbouring samples counts as a **break** in the curve, as a fraction
     * of the visible height. A continuous function's steepest step is a small fraction of its own
     * range; a pole's is a large one, so the curve stops instead of being joined across it.
     */
    private const val BREAK_FRACTION = 0.25

    /** Roughly how many ticks an axis wants. */
    private const val TICK_TARGET = 8

    /**
     * Compile [source], or null when it isn't a valid formula. Case-insensitive; whitespace ignored.
     */
    fun compile(source: String): Formula? = try {
        val parser = Parser(source)
        val f = parser.expression()
        if (parser.rest().isNotEmpty()) null else f
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * Sample [formula] across [xMin]..[xMax] into [samples] + 1 (x, y) pairs. A value that isn't finite
     * is kept as NaN, so the caller can break the curve there instead of drawing across a pole.
     */
    fun sample(formula: Formula, xMin: Double, xMax: Double, samples: Int): List<Pair<Double, Double>> {
        val n = samples.coerceIn(1, MAX_SAMPLES)
        val out = ArrayList<Pair<Double, Double>>(n + 1)
        for (i in 0..n) {
            val x = xMin + (xMax - xMin) * i / n
            out += x to formula.at(x)
        }
        return out
    }

    /**
     * Plot [source] over [xMin]..[xMax] inside the `widthPt` × `heightPt` box whose top-left corner is
     * ([leftPt], [topPt]): a frame, whichever axes fall inside the sampled y range, a tick on every
     * "nice" step with its number, and the curve — **broken** wherever the function is undefined or
     * leaps, so the plot has gaps where the function does.
     *
     * An empty [Plot] when the formula doesn't compile or has no finite value in range: the caller
     * then inserts nothing instead of an empty frame.
     *
     * @param strokeWidthPt Stroke width for every line of the plot.
     */
    fun plot(
        source: String,
        xMin: Double,
        xMax: Double,
        leftPt: Double,
        topPt: Double,
        widthPt: Double,
        heightPt: Double,
        strokeWidthPt: Double,
        samples: Int = DEFAULT_SAMPLES,
    ): Plot {
        val formula = compile(source) ?: return EMPTY_PLOT
        if (xMax <= xMin || widthPt <= 0.0 || heightPt <= 0.0) return EMPTY_PLOT
        val points = sample(formula, xMin, xMax, samples)
        val finite = points.mapNotNull { it.second.takeIf(Double::isFinite) }
        if (finite.isEmpty()) return EMPTY_PLOT

        var yMin = finite.min()
        var yMax = finite.max()
        if (yMax - yMin < 1e-9) {
            // A constant function: give the band height, or the curve would be drawn on the axis.
            yMin -= 1.0
            yMax += 1.0
        }
        val pad = (yMax - yMin) * Y_PADDING
        yMin -= pad
        yMax += pad

        val spanX = xMax - xMin
        val spanY = yMax - yMin
        val toX = { x: Double -> leftPt + (x - xMin) / spanX * widthPt }
        val toY = { y: Double -> topPt + (yMax - y) / spanY * heightPt }

        val strokes = ArrayList<List<StrokePoint>>()
        strokes += frame(leftPt, topPt, widthPt, heightPt, strokeWidthPt)

        val labels = ArrayList<PlotLabel>()
        strokes += axes(
            xMin, xMax, yMin, yMax, leftPt, topPt, widthPt, heightPt, strokeWidthPt, toX, toY, labels,
        )

        strokes += ticks(
            xMin, xMax, niceStep(spanX, TICK_TARGET),
            leftPt, topPt, heightPt, strokeWidthPt, toX, labels, vertical = false,
        )
        strokes += ticks(
            yMin, yMax, niceStep(spanY, TICK_TARGET),
            leftPt, topPt, heightPt, strokeWidthPt, toY, labels, vertical = true,
        )

        strokes += curve(points, spanY, toX, toY, strokeWidthPt)
        return Plot(strokes, labels)
    }

    /**
     * The "nice" tick step covering [range] in about [target] steps: 1, 2 or 5 times a power of ten,
     * which is what makes an axis readable. Zero (or a non-positive range) gives 0 — no ticks.
     */
    fun niceStep(range: Double, target: Int): Double {
        if (range <= 0.0 || target <= 0) return 0.0
        val raw = range / target
        val magnitude = 10.0.pow(floor(log10(raw)))
        val normalised = raw / magnitude
        val factor = when {
            normalised <= 1.0 -> 1.0
            normalised <= 2.0 -> 2.0
            normalised <= 5.0 -> 5.0
            else -> 10.0
        }
        return factor * magnitude
    }

    /**
     * A tick's number as short text: as many decimals as the *step* has (a 0.25 step gives `0.25`, a
     * whole step gives integers), which is exactly the precision the axis can distinguish.
     */
    fun label(value: Double, step: Double): String {
        if (abs(value) < step / 1000.0) return "0"
        return trimmed(value, decimalsFor(step))
    }

    /** How many decimals a step needs, read off its own shortest decimal form (0.25 → 2, 1.0 → 0). */
    private fun decimalsFor(step: Double): Int {
        val text = String.format(java.util.Locale.US, "%.6f", step).trimEnd('0').trimEnd('.')
        val dot = text.indexOf('.')
        return if (dot < 0) 0 else (text.length - dot - 1).coerceIn(0, 6)
    }

    private fun trimmed(value: Double, decimals: Int): String {
        val text = String.format(java.util.Locale.US, "%.${decimals}f", value)
        return if (decimals == 0) text else text.trimEnd('0').trimEnd('.')
    }

    // --- geometry ------------------------------------------------------------------------------

    /** The four sides of the plot box, as one closed polyline. */
    private fun frame(
        leftPt: Double,
        topPt: Double,
        widthPt: Double,
        heightPt: Double,
        strokeWidthPt: Double,
    ): List<StrokePoint> {
        fun p(x: Double, y: Double) = StrokePoint(x, y, strokeWidthPt)
        return listOf(
            p(leftPt, topPt),
            p(leftPt + widthPt, topPt),
            p(leftPt + widthPt, topPt + heightPt),
            p(leftPt, topPt + heightPt),
            p(leftPt, topPt),
        )
    }

    /**
     * The x and y axes, drawn only where zero actually falls inside the range (a plot of `x^2 + 1` has
     * no x-axis inside its window, and drawing one on the frame's bottom edge would be a lie).
     */
    private fun axes(
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        leftPt: Double,
        topPt: Double,
        widthPt: Double,
        heightPt: Double,
        strokeWidthPt: Double,
        toX: (Double) -> Double,
        toY: (Double) -> Double,
        labels: MutableList<PlotLabel>,
    ): List<List<StrokePoint>> {
        val out = ArrayList<List<StrokePoint>>()
        if (yMin <= 0.0 && yMax >= 0.0) {
            val y = toY(0.0)
            out += listOf(
                StrokePoint(leftPt, y, strokeWidthPt),
                StrokePoint(leftPt + widthPt, y, strokeWidthPt),
            )
            // The origin's number, so the axes say where they cross.
            labels += PlotLabel("0", leftPt - X_LABEL_OFFSET_PT, y + Y_LABEL_OFFSET_PT)
        }
        if (xMin <= 0.0 && xMax >= 0.0) {
            val x = toX(0.0)
            out += listOf(
                StrokePoint(x, topPt, strokeWidthPt),
                StrokePoint(x, topPt + heightPt, strokeWidthPt),
            )
        }
        return out
    }

    /**
     * A tick mark on every [step] between [from] and [to], on one axis, each with its number written
     * just outside the frame — to the left for the y axis, below for the x axis.
     *
     * @param heightPt Frame height, used to keep a y tick inside the frame vertically.
     * @param toPt Maps an axis value to its page coordinate (y for the x axis, x for the y axis).
     */
    private fun ticks(
        from: Double,
        to: Double,
        step: Double,
        leftPt: Double,
        topPt: Double,
        heightPt: Double,
        strokeWidthPt: Double,
        toPt: (Double) -> Double,
        labels: MutableList<PlotLabel>,
        vertical: Boolean,
    ): List<List<StrokePoint>> {
        if (step <= 0.0) return emptyList()
        val out = ArrayList<List<StrokePoint>>()
        var value = ceil(from / step) * step
        while (value <= to + step * 1e-6) {
            val at = toPt(value)
            val text = label(value, step)
            if (vertical) {
                if (at >= topPt - 1e-6 && at <= topPt + heightPt + 1e-6) {
                    out += listOf(
                        StrokePoint(leftPt, at, strokeWidthPt),
                        StrokePoint(leftPt + TICK_PT, at, strokeWidthPt),
                    )
                    labels += PlotLabel(
                        text,
                        leftPt - X_LABEL_OFFSET_PT - text.length * LABEL_CHAR_PT,
                        at - LABEL_CHAR_PT / 2,
                    )
                }
            } else {
                out += listOf(
                    StrokePoint(at, topPt + heightPt, strokeWidthPt),
                    StrokePoint(at, topPt + heightPt - TICK_PT, strokeWidthPt),
                )
                labels += PlotLabel(
                    text,
                    at - text.length * LABEL_CHAR_PT / 2,
                    topPt + heightPt + Y_LABEL_OFFSET_PT,
                )
            }
            value += step
        }
        return out
    }

    /**
     * The curve itself, split into one polyline per continuous run: a run ends at a sample that isn't
     * finite (a pole or a domain edge) or at a leap larger than half the visible height, which is what
     * an asymptote looks like once it is sampled.
     */
    private fun curve(
        points: List<Pair<Double, Double>>,
        spanY: Double,
        toX: (Double) -> Double,
        toY: (Double) -> Double,
        strokeWidthPt: Double,
    ): List<List<StrokePoint>> {
        val out = ArrayList<List<StrokePoint>>()
        var run = ArrayList<StrokePoint>()
        var previous: Double? = null
        for ((x, y) in points) {
            val broken = !y.isFinite() || (previous != null && abs(y - previous) > spanY * BREAK_FRACTION)
            if (broken) {
                if (run.size >= 2) out += run
                run = ArrayList()
                previous = if (y.isFinite()) y else null
                continue
            }
            run += StrokePoint(toX(x), toY(y), strokeWidthPt)
            previous = y
        }
        if (run.size >= 2) out += run
        return out
    }

    private val EMPTY_PLOT = Plot(emptyList(), emptyList())

    /** How far a tick label sits from the frame, in pt. */
    private const val X_LABEL_OFFSET_PT = 4.0

    /** How long a tick mark is, in pt. */
    private const val TICK_PT = 4.0

    /** How far a tick label sits from its tick line, in pt (vertical axis). */
    private const val Y_LABEL_OFFSET_PT = 4.0

    /** Rough width of one character at the label size, in pt — used to centre labels in pure code. */
    private const val LABEL_CHAR_PT = 5.0
}

/**
 * The recursive-descent parser behind [FunctionPlot.compile]: term/additive/`^` precedence, unary
 * minus, parentheses, numbers, `x`, the constants and the functions. Throws
 * [IllegalArgumentException] on anything it doesn't understand — [FunctionPlot.compile] turns that
 * into a null so callers only ever see "a formula" or "no formula".
 */
private class Parser(private val source: String) {

    private var at = 0

    fun expression(): Formula {
        var value = term()
        while (true) {
            skipSpace()
            value = when (peek()) {
                '+' -> { next(); add(value, term()) }
                '-' -> { next(); sub(value, term()) }
                else -> return value
            }
        }
    }

    /** The text left after a successful parse; anything but whitespace means the formula didn't fit. */
    fun rest(): String {
        skipSpace()
        return source.substring(at)
    }

    private fun term(): Formula {
        var value = power()
        while (true) {
            val c = peek()
            when (c) {
                '*' -> { next(); val left = value; value = mul(left, power()) }
                '/' -> { next(); val left = value; value = div(left, power()) }
                // Anything else ends the term — including a letter, which is an implicit product we
                // deliberately don't accept (`2x` must be written `2*x`).
                else -> return value
            }
        }
    }

    private fun power(): Formula {
        val base = unary()
        skipSpace()
        return if (peek() == '^') {
            next()
            val exponent = power() // right-associative: 2^3^2 is 2^(3^2)
            Formula { x -> base.at(x).pow(exponent.at(x)) }
        } else {
            base
        }
    }

    private fun unary(): Formula {
        skipSpace()
        return when (peek()) {
            '-' -> { next(); val inner = unary(); Formula { x -> -inner.at(x) } }
            '+' -> { next(); unary() }
            else -> primary()
        }
    }

    private fun primary(): Formula {
        skipSpace()
        val c = peek() ?: throw IllegalArgumentException("unexpected end of formula at $at")
        if (c == '(') {
            next()
            val inner = expression()
            skipSpace()
            if (peek() != ')') throw IllegalArgumentException("missing ) at $at")
            next()
            return inner
        }
        if (c.isDigit() || c == '.') return number()
        if (c.isLetter()) return identifier()
        throw IllegalArgumentException("unexpected '$c' at $at")
    }

    /** A number: digits with an optional single decimal point. */
    private fun number(): Formula {
        val start = at
        while (at < source.length && (source[at].isDigit() || source[at] == '.')) at++
        val text = source.substring(start, at)
        val value = text.toDoubleOrNull() ?: throw IllegalArgumentException("bad number \"$text\"")
        return Formula { value }
    }

    /**
     * A name: the variable `x`, one of the constants, or a function call (whose argument may be
     * parenthesised or — as in `sin x` — the rest of the term). An unknown name is an error.
     */
    private fun identifier(): Formula {
        val start = at
        while (at < source.length && source[at].isLetter()) at++
        val name = source.substring(start, at).lowercase()
        val function = FunctionPlot.FUNCTIONS[name]
        if (function != null) {
            skipSpace()
            val argument = if (peek() == '(') {
                next()
                val inner = expression()
                skipSpace()
                if (peek() != ')') throw IllegalArgumentException("missing ) at $at")
                next()
                inner
            } else {
                power()
            }
            return Formula { x -> function(argument.at(x)) }
        }
        FunctionPlot.CONSTANTS[name]?.let { constant -> return Formula { constant } }
        if (name == "x") return Formula { x -> x }
        throw IllegalArgumentException("unknown name \"$name\" at $start")
    }

    private fun peek(): Char? = source.getOrNull(at)

    private fun next() {
        at++
    }

    private fun skipSpace() {
        while (at < source.length && source[at].isWhitespace()) at++
    }

    private fun add(a: Formula, b: Formula) = Formula { x -> a.at(x) + b.at(x) }
    private fun sub(a: Formula, b: Formula) = Formula { x -> a.at(x) - b.at(x) }
    private fun mul(a: Formula, b: Formula) = Formula { x -> a.at(x) * b.at(x) }
    private fun div(a: Formula, b: Formula) = Formula { x -> a.at(x) / b.at(x) }
}
