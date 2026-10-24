package com.mobixournal.desktop

import com.mobixournal.design.PenPalette
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.PlotBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A graph's **equations each carry their own colour**.
 *
 * A plot of two curves is read by colour — `sin(x)` and `cos(x)` in the same black are two lines the eye
 * has to trace — so the placement card and the lane both give every equation its own ink. What this file
 * protects is that the colour the card shows is the colour the placed element carries: both go through
 * [PendingPlacement.plotIn], the builder **Place** commits, so a card that drew one ink and wrote another
 * cannot ship.
 */
class DesktopPlotColoursTest {

    private val box = PlotBox(0.0, 0.0, 300.0, 180.0)
    private val bounds = Bounds(0.0, 0.0, 300.0, 180.0)

    private fun card(ink: Int = 0xFF000000.toInt()) =
        PendingPlacement(LivingSlot.PLOT, bounds, "tab", ink)

    @Test
    fun `the first equation starts in the tool's own ink`() {
        val pending = card(ink = 0xFFE53935.toInt())
        assertEquals(0xFFE53935.toInt(), pending.formulaColours.single())
        val plot = pending.plotIn(box, 1.0)!!
        assertEquals(0xFFE53935.toInt(), plot.curves.single().color)
    }

    @Test
    fun `a second equation takes an ink of its own`() {
        val pending = card()
        pending.formulas[0] = "sin(x)"
        pending.addFormula()
        pending.formulas[1] = "cos(x)"
        assertEquals(2, pending.formulaColours.size)
        assertTrue(
            "a new row takes one of the palette's colours",
            pending.formulaColours[1] in PenPalette.COLORS,
        )
        val plot = pending.plotIn(box, 1.0)!!
        assertEquals("sin(x)", plot.curves[0].source)
        assertEquals("cos(x)", plot.curves[1].source)
        assertEquals("the first keeps the tool's ink", pending.formulaColours[0], plot.curves[0].color)
        assertEquals("the second carries its own", pending.formulaColours[1], plot.curves[1].color)
    }

    @Test
    fun `a colour picked in the card is the colour of the curve`() {
        val pending = card()
        pending.formulas[0] = "x"
        pending.addFormula()
        pending.formulas[1] = "x^2"
        pending.setFormulaColour(1, 0xFF1E88E5.toInt())

        val plot = pending.plotIn(box, 1.0)!!
        assertEquals(0xFF1E88E5.toInt(), plot.curves[1].color)
        assertNotEquals("and the first curve is left alone", 0xFF1E88E5.toInt(), plot.curves[0].color)
    }

    @Test
    fun `removing an equation takes its colour with it`() {
        val pending = card()
        pending.formulas[0] = "a"
        pending.addFormula()
        pending.formulas[1] = "b"
        pending.setFormulaColour(1, 0xFF43A047.toInt())
        pending.removeFormula(0)

        assertEquals(1, pending.formulas.size)
        assertEquals("b", pending.formulas.single())
        assertEquals("the colour that survives is the one its row had", 0xFF43A047.toInt(), pending.formulaColours.single())
        assertEquals(0xFF43A047.toInt(), pending.plotIn(box, 1.0)!!.curves.single().color)
    }

    @Test
    fun `a blank equation is neither drawn nor given a colour`() {
        val pending = card()
        pending.formulas[0] = "sin(x)"
        pending.addFormula()
        val plot = pending.plotIn(box, 1.0)!!
        assertEquals("only the filled row is a curve", 1, plot.curves.size)
        assertEquals("sin(x)", plot.curves.single().source)
    }
}
