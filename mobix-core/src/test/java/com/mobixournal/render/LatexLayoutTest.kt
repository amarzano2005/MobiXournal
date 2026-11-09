package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform-free formula layout: scripts are small and raised, a fraction has its bar and stacks,
 * a radical has its overline, and a long formula shrinks to the box it was given instead of spilling
 * out of it. These are the guarantees the PDF plan relies on when it turns a math block's source into
 * positioned text ops.
 */
class LatexLayoutTest {

    private fun layout(source: String, height: Double = 40.0, width: Double = 240.0) =
        LatexLayout.layout(LatexParser.parse(source), height, width)

    private fun List<MathGlyph>.glyph(text: String): MathGlyph =
        single { it.text == text }

    @Test
    fun aSuperscriptIsSmallerAndRaisedAboveItsBase() {
        val out = layout("x^2")
        val base = out.glyphs.glyph("x")
        val script = out.glyphs.glyph("2")
        assertTrue("the script is smaller", script.size < base.size)
        assertTrue("the script's baseline is above the base's", script.y < base.y)
        // The layout centres the formula inside the 40 pt box it was handed, so a baseline lies inside
        // that box: [MathLayout.heightPt] is the formula's own height, without that top offset.
        val top = ((40.0 - out.heightPt) / 2.0).coerceAtLeast(0.0)
        assertTrue("both are inside the measured box", out.glyphs.all { it.y in top..(top + out.heightPt) })
    }

    @Test
    fun aFractionStacksItsPartsAroundARule() {
        val out = layout("\\frac{a}{b}")
        val numerator = out.glyphs.glyph("a")
        val denominator = out.glyphs.glyph("b")
        assertTrue("the denominator sits below the numerator", denominator.y > numerator.y)
        assertEquals("one bar", 1, out.rules.size)
        val bar = out.rules.single()
        assertTrue("the bar is between the parts", bar.y1 > numerator.y && bar.y1 < denominator.y)
        assertTrue("the bar spans the formula", bar.x2 - bar.x1 > 0)
    }

    @Test
    fun aRootWearsARadicalAndAnOverline() {
        val out = layout("\\sqrt{x}")
        out.glyphs.glyph("√")
        out.glyphs.glyph("x")
        assertEquals("the radical's overline", 1, out.rules.size)
        val rule = out.rules.single()
        assertTrue("the overline is above the radicand", rule.y1 < out.glyphs.glyph("x").y)
    }

    @Test
    fun aBlankSourceLaysOutNothing() {
        val out = layout("")
        assertTrue(out.glyphs.isEmpty())
        assertTrue(out.rules.isEmpty())
    }

    @Test
    fun aLongFormulaShrinksToFitItsBox() {
        val out = layout("x+y+z+a+b+c+d+e+f+g+h+i+j+k+l+m+n", height = 24.0, width = 100.0)
        assertTrue("the formula fits the width it was given", out.widthPt <= 100.0 + 1e-6)
        assertTrue("and the height", out.heightPt <= 24.0 + 1e-6)
        assertTrue("everything is finite", out.glyphs.all { it.x.isFinite() && it.y.isFinite() && it.size.isFinite() })
        assertTrue("the formula produced glyphs", out.glyphs.size >= 16)
    }

    /**
     * A run's baseline and depth are the glyphs' measured ones — the same table the rest of the app lays
     * text out with — so the formula's box is the drawn one and not a 0.75 em guess above the baseline
     * with 0.25 below it.
     */
    @Test
    fun aRunsBoxIsTheMeasuredExtentOfItsGlyphs() {
        val tight = layout("x")
        val glyph = tight.glyphs.single()
        val top = ((40.0 - tight.heightPt) / 2.0).coerceAtLeast(0.0)
        assertEquals(
            "the baseline is the glyph's own rise",
            TextAdvance.ascentEm('x') * glyph.size + top,
            glyph.y,
            1e-9,
        )
        assertEquals("and the box is exactly as tall as the glyph", TextAdvance.ascentEm('x') * glyph.size, tight.heightPt, 1e-9)

        val hanging = layout("g")
        val descender = hanging.glyphs.single()
        assertEquals(
            "a descender adds its own depth below the baseline",
            (TextAdvance.ascentEm('g') + TextAdvance.descentEm('g')) * descender.size,
            hanging.heightPt,
            1e-9,
        )
        assertTrue("which is deeper than an x's", TextAdvance.descentEm('g') > 0.1)
    }

    @Test
    fun aSubscriptSitsBelowItsBase() {
        val out = layout("a_1")
        val base = out.glyphs.glyph("a")
        val script = out.glyphs.glyph("1")
        assertTrue("the subscript is smaller", script.size < base.size)
        assertTrue("the subscript is lower", script.y > base.y)
    }
}
