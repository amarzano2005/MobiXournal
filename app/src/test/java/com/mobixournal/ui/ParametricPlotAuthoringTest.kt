package com.mobixournal.ui

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixAssetSource
import com.mobixournal.format.mobix.MobixElementCodec
import com.mobixournal.render.PlotElementFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parametric path from the plot dialog to a saved, reopened element.
 *
 * `PlotElementFactoryTest` proves the factory, the codec and the ink for a parametric pair; what no test
 * covered is the seam *above* it — the answer [resolveActiveCurves] hands the overlay. The dialog's
 * second tab is a kind, not a second input (see `docs/function-plots.md`): it asks for `x(t)` and
 * `y(t)` and answers **one** `PlotCurve` whose `kind` is [PlotKind.PARAMETRIC], which is the fact the
 * guides used to contradict by saying the plot tool places Cartesian curves only.
 *
 * The curve's parameter range is the dialog's own two bound fields (`t min`/`t max` on the parametric
 * tab), so the element's `tMin`/`tMax` and the plot's `xMin`/`xMax` are the same two numbers, and the
 * sample count and line style are the ones the insertion was given.
 */
class ParametricPlotAuthoringTest {

    private val red = 0xFFD32F2F.toInt()

    private fun page() = Page(
        width = 595.27559100,
        height = 841.88976400,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(emptyList())),
    )

    /** The element the Android shell's Insert places for a parametric answer. */
    private fun elementFor(paramX: String, paramY: String, from: Double = 0.0, to: Double = 6.2831853): PlotElement {
        val curves = resolveActiveCurves(
            modeTab = 1,
            curves = listOf(CurveEditorState()),
            paramX = paramX,
            paramY = paramY,
        )
        return PlotElementFactory.plotElementFor(
            curves = curves,
            xMin = from,
            xMax = to,
            centreX = 400.0,
            centreY = 500.0,
            page = page(),
            strokeWidthPt = 1.5,
            axisColor = red,
        )
    }

    @Test
    fun theParametricTabAnswersOneCurveCarryingBothExpressions() {
        val curves = resolveActiveCurves(
            modeTab = 1,
            curves = listOf(CurveEditorState()),
            paramX = "cos(x)",
            paramY = "sin(x)",
        )

        // One curve, not the two halves: two curves here would be drawn as the two waves their formulas
        // look like as `y = f(x)`, which is the bug the kind field exists to prevent.
        assertEquals(1, curves.size)
        val curve = curves.single()
        assertEquals(PlotKind.PARAMETRIC, curve.kind)
        assertEquals("cos(x)", curve.source)
        assertEquals("sin(x)", curve.sourceY)
        assertEquals("a new parametric plot is drawn solid, like a new Cartesian one", LineStyle.PLAIN, curve.lineStyle)
    }

    @Test
    fun theCartesianTabStillAnswersWithNoKindAndNoSecondExpression() {
        // The other side of the same decision: the kind is what the tab says, so the first tab keeps
        // producing plain `y = f(x)` curves and nothing about an old document changes.
        val curves = resolveActiveCurves(
            modeTab = 0,
            curves = listOf(CurveEditorState("sin(x)", red), CurveEditorState("cos(x)", red)),
            paramX = "cos(x)",
            paramY = "sin(x)",
        )

        assertEquals(2, curves.size)
        assertTrue("no curve of the Cartesian tab claims a kind", curves.all { it.kind == PlotKind.CARTESIAN })
        assertTrue("and none of them carries a y(t)", curves.all { it.sourceY == null })
        assertEquals(listOf("f1", "f2"), curves.map { it.label })
    }

    @Test
    fun halfAPairIsNotDrawableSoTheInsertButtonStaysOff() {
        // The gate Insert uses (`PlotCurve.isDrawable`): `x(t)` alone is half a curve, and a plot the
        // element compiler would report as unparsed must not be placeable.
        val missingY = resolveActiveCurves(1, listOf(CurveEditorState()), "cos(x)", "").single()
        val unparsableY = resolveActiveCurves(1, listOf(CurveEditorState()), "cos(x)", "sin(").single()
        val complete = resolveActiveCurves(1, listOf(CurveEditorState()), "cos(x)", "sin(x)").single()

        assertFalse("x(t) with no y(t) is not a curve", missingY.isDrawable())
        assertFalse("a y(t) that does not parse is not a curve", unparsableY.isDrawable())
        assertTrue("the pair is", complete.isDrawable())
    }

    @Test
    fun theDialogsAnswerBecomesAnElementThatKeepsItsRangeSamplesAndStyle() {
        val plot = elementFor("cos(x)", "sin(x)", from = 0.0, to = 6.2831853)

        val curve = plot.curves.single()
        assertEquals(PlotKind.PARAMETRIC, curve.kind)
        assertEquals("cos(x)", curve.source)
        assertEquals("sin(x)", curve.sourceY)
        assertEquals("the t fields are the curve's parameter range", 0.0, curve.tMin!!, 0.0)
        assertEquals(6.2831853, curve.tMax!!, 0.0)
        assertEquals("the plot keeps the domain it was placed with", 0.0, plot.xMin, 0.0)
        assertEquals(6.2831853, plot.xMax, 0.0)
        assertEquals("samples are the element's own default", PlotElement.DEFAULT_SAMPLES, plot.samples)
        assertEquals("the style the dialog chose is stored", LineStyle.PLAIN, curve.lineStyle)
        assertEquals("the parametric tab has one ink of its own today", XOPP_BLUE, curve.color)
        assertNull("the codec mints the identity, not the insertion", plot.id)
    }

    @Test
    fun savingAndReopeningAnswersTheSamePairAgain() {
        // The whole chain the guides describe: dialog tab -> element -> codec -> reopen. The element's
        // stored curves are mapped back the way the shell's edit path maps them, and the dialog's
        // parametric tab must resolve that back into the same single curve.
        val saved = elementFor("cos(2*x)", "sin(3*x)", from = 0.0, to = 3.141592653589793)
        val decoded = MobixElementCodec.decode(
            MobixElementCodec.encode(saved, MobixAssetSink()),
            MobixAssetSource { null },
        ) as PlotElement

        assertEquals(PlotKind.PARAMETRIC, decoded.curves.single().kind)
        assertEquals("cos(2*x)", decoded.curves.single().source)
        assertEquals("sin(3*x)", decoded.curves.single().sourceY)
        assertEquals(0.0, decoded.curves.single().tMin!!, 0.0)
        assertEquals(3.141592653589793, decoded.curves.single().tMax!!, 0.0)
        assertEquals(PlotElement.DEFAULT_SAMPLES, decoded.samples)

        val reopened = resolveActiveCurves(
            modeTab = 1,
            curves = listOf(CurveEditorState(decoded.curves.single().source)),
            paramX = decoded.curves.single().source,
            paramY = decoded.curves.single().sourceY!!,
        ).single()

        assertEquals("the pair comes back as one curve", PlotKind.PARAMETRIC, reopened.kind)
        assertEquals("cos(2*x)", reopened.source)
        assertEquals("sin(3*x)", reopened.sourceY)
        assertTrue("and it is still placeable", reopened.isDrawable())
    }
}
