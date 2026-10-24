package com.mobixournal.render

import kotlin.math.max
import kotlin.math.min

/**
 * Lays a parsed formula out as **positioned glyphs and rules**, so a PDF export can typeset a living
 * math block instead of printing its raw source.
 *
 * The canvas draws formulas through the LaTeX renderer, which is Android-bound; the PDF plan has to
 * stay platform-free. This is that shared arithmetic, in the smallest form that keeps a formula
 * legible: a fixed advance per glyph, scripts at a smaller size on a raised or lowered baseline, a
 * fraction stacked over a rule, a radical with its overline. No font file and no measured metrics are
 * involved, which is why it can compile in `mobix-core` and run on either shell.
 *
 * Coordinates are a **top-left page frame in points** (y down, like the model), with [MathGlyph.y]
 * the baseline. The page plan flips them into PDF space through [PdfPageTransform]. The layout is
 * scaled once to fit the element's box, so a long formula shrinks rather than spilling out of the
 * box the user drew.
 */
data class MathGlyph(val text: String, val x: Double, val y: Double, val size: Double)

/** A rule of a formula (a fraction bar, a radical's overline), in the same top-left frame. */
data class MathRule(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val widthPt: Double,
)

/** The whole formula's drawing: glyphs in reading order, its rules, and the measured box. */
data class MathLayout(
    val glyphs: List<MathGlyph>,
    val rules: List<MathRule>,
    val widthPt: Double,
    val heightPt: Double,
    /** The first baseline, from the box's top. */
    val baselinePt: Double,
)

object LatexLayout {

    /** The width of one glyph as a fraction of its size — a monospace approximation. */
    const val ADVANCE_RATIO = 0.6

    /** A script's size, relative to its base. */
    const val SCRIPT_RATIO = 0.72

    /** How far a superscript's baseline is raised, relative to the base size. */
    const val SUP_RAISE_RATIO = 0.45

    /** How far a subscript's baseline is lowered, relative to the base size. */
    const val SUB_DROP_RATIO = 0.22

    /** A fraction's numerator and denominator size, relative to the fraction. */
    const val FRACTION_RATIO = 0.82

    /** The share of the box's height the formula may fill. */
    const val HEIGHT_FILL = 0.9

    /** The layout of [node] fitted into a [boxHeightPt] × [maxWidthPt] box. */
    fun layout(node: LatexNode, boxHeightPt: Double, maxWidthPt: Double): MathLayout {
        var size = (boxHeightPt * 0.6).coerceIn(2.0, 48.0)
        var box = Engine(size).layout(node)
        // Two passes are enough: one to measure at the natural size, one after shrinking to fit.
        repeat(2) {
            val fitWidth = if (maxWidthPt > 0 && box.width > 0) maxWidthPt / box.width else 1.0
            val fitHeight = if (boxHeightPt > 0 && box.height > 0) (boxHeightPt * HEIGHT_FILL) / box.height else 1.0
            val scale = min(1.0, min(fitWidth, fitHeight))
            if (scale >= 0.999) return@repeat
            size = (size * scale).coerceAtLeast(1.0)
            box = Engine(size).layout(node)
        }
        val top = ((boxHeightPt - box.height) / 2.0).coerceAtLeast(0.0)
        return MathLayout(
            glyphs = box.glyphs.map { it.copy(y = it.y + top) },
            rules = box.rules.map { it.copy(y1 = it.y1 + top, y2 = it.y2 + top) },
            widthPt = box.width,
            heightPt = box.height,
            baselinePt = box.ascent + top,
        )
    }

    /**
     * A laid-out subtree. [ascent] is the distance from the box's top to its baseline and [descent]
     * from the baseline to its bottom, which is what lets two boxes sit on one baseline.
     */
    private data class Box(
        val width: Double,
        val ascent: Double,
        val descent: Double,
        val glyphs: List<MathGlyph>,
        val rules: List<MathRule>,
    ) {
        val height: Double get() = ascent + descent
    }

    private class Engine(private val size: Double) {

        fun layout(node: LatexNode): Box = when (node) {
            is Row -> row(node.children.map(::layout))
            is SymbolRun -> symbols(node.text)
            is Superscript -> scripted(layout(node.base), sup = layoutAt(node.sup, SCRIPT_RATIO), sub = null)
            is Subscript -> scripted(layout(node.base), sup = null, sub = layoutAt(node.sub, SCRIPT_RATIO))
            is SubSup -> scripted(
                layout(node.base),
                sup = layoutAt(node.sup, SCRIPT_RATIO),
                sub = layoutAt(node.sub, SCRIPT_RATIO),
            )
            is Fraction -> fraction(layoutAt(node.num, FRACTION_RATIO), layoutAt(node.den, FRACTION_RATIO))
            is Sqrt -> sqrt(layout(node.radicand))
        }

        private fun layoutAt(node: LatexNode, ratio: Double): Box = Engine(size * ratio).layout(node)

        /** Children left to right, all sitting on the tallest child's baseline. */
        private fun row(children: List<Box>): Box {
            if (children.isEmpty()) return empty()
            val ascent = children.maxOf { it.ascent }
            val descent = children.maxOf { it.descent }
            var x = 0.0
            val glyphs = ArrayList<MathGlyph>()
            val rules = ArrayList<MathRule>()
            for (child in children) {
                val dy = ascent - child.ascent
                child.glyphs.forEach { glyphs += it.copy(x = it.x + x, y = it.y + dy) }
                child.rules.forEach { rules += it.copy(x1 = it.x1 + x, x2 = it.x2 + x, y1 = it.y1 + dy, y2 = it.y2 + dy) }
                x += child.width
            }
            return Box(x, ascent, descent, glyphs, rules)
        }

        /** One glyph per character, at the current size's baseline. */
        private fun symbols(text: String): Box {
            if (text.isEmpty()) return empty()
            val glyphs = text.mapIndexed { index, ch ->
                MathGlyph(ch.toString(), index * size * ADVANCE_RATIO, size * 0.75, size)
            }
            return Box(text.length * size * ADVANCE_RATIO, size * 0.75, size * 0.25, glyphs, emptyList())
        }

        private fun scripted(base: Box, sup: Box?, sub: Box?): Box {
            val gap = size * 0.06
            val raise = size * SUP_RAISE_RATIO
            val drop = size * SUB_DROP_RATIO
            val ascent = max(base.ascent, sup?.let { raise + it.ascent } ?: 0.0)
            val descent = max(base.descent, sub?.let { drop + it.descent } ?: 0.0)
            var x = base.width + gap
            val glyphs = ArrayList<MathGlyph>()
            val rules = ArrayList<MathRule>()
            base.glyphs.forEach { glyphs += it.copy(y = it.y + ascent - base.ascent) }
            base.rules.forEach { rules += it.copy(y1 = it.y1 + ascent - base.ascent, y2 = it.y2 + ascent - base.ascent) }
            sup?.let {
                val dy = ascent - raise - it.ascent
                it.glyphs.forEach { glyph -> glyphs += glyph.copy(x = glyph.x + x, y = glyph.y + dy) }
                it.rules.forEach { rule -> rules += rule.copy(x1 = rule.x1 + x, x2 = rule.x2 + x, y1 = rule.y1 + dy, y2 = rule.y2 + dy) }
                x += it.width + (if (sub != null) gap else 0.0)
            }
            sub?.let {
                val dy = ascent + drop - it.ascent
                it.glyphs.forEach { glyph -> glyphs += glyph.copy(x = glyph.x + x, y = glyph.y + dy) }
                it.rules.forEach { rule -> rules += rule.copy(x1 = rule.x1 + x, x2 = rule.x2 + x, y1 = rule.y1 + dy, y2 = rule.y2 + dy) }
                x += it.width
            }
            return Box(x, ascent, descent, glyphs, rules)
        }

        /** Numerator over the bar, denominator under it; the bar is the fraction's own baseline. */
        private fun fraction(num: Box, den: Box): Box {
            val pad = size * 0.1
            val gap = size * 0.12
            val width = max(num.width, den.width) + pad * 2
            val barY = num.height + gap
            val glyphs = ArrayList<MathGlyph>()
            val rules = ArrayList<MathRule>()
            num.glyphs.forEach { glyphs += it.copy(x = it.x + (width - num.width) / 2.0) }
            num.rules.forEach { rules += it.copy(x1 = it.x1 + (width - num.width) / 2.0, x2 = it.x2 + (width - num.width) / 2.0) }
            den.glyphs.forEach { glyphs += it.copy(x = it.x + (width - den.width) / 2.0, y = it.y + barY + gap) }
            den.rules.forEach {
                rules += it.copy(
                    x1 = it.x1 + (width - den.width) / 2.0,
                    x2 = it.x2 + (width - den.width) / 2.0,
                    y1 = it.y1 + barY + gap,
                    y2 = it.y2 + barY + gap,
                )
            }
            rules += MathRule(pad, barY, width - pad, barY, max(0.4, size * 0.05))
            return Box(width, barY, den.height + gap, glyphs, rules)
        }

        /** A radical glyph before the radicand, with a rule over the radicand's own box. */
        private fun sqrt(radicand: Box): Box {
            val radicalWidth = size * 0.8
            val width = radicalWidth + radicand.width + size * 0.1
            val ascent = max(size * 0.85, radicand.ascent)
            val glyphs = ArrayList<MathGlyph>(radicand.glyphs.size + 1)
            glyphs += MathGlyph("√", 0.0, ascent, size)
            val dy = ascent - radicand.ascent
            radicand.glyphs.forEach { glyphs += it.copy(x = it.x + radicalWidth, y = it.y + dy) }
            val rules = ArrayList<MathRule>(radicand.rules.size + 1)
            radicand.rules.forEach {
                rules += it.copy(x1 = it.x1 + radicalWidth, x2 = it.x2 + radicalWidth, y1 = it.y1 + dy, y2 = it.y2 + dy)
            }
            val overlineY = ascent - radicand.ascent - size * 0.06
            rules += MathRule(radicalWidth, overlineY, width, overlineY, max(0.4, size * 0.05))
            val bottom = max(ascent, dy + radicand.height)
            return Box(width, ascent, (bottom - ascent).coerceAtLeast(0.0), glyphs, rules)
        }

        private fun empty() = Box(0.0, 0.0, 0.0, emptyList(), emptyList())
    }
}
