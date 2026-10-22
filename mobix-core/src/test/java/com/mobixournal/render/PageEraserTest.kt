package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEraserTest {

    private fun stroke(vararg xs: Double): Stroke =
        Stroke(Tool.PEN, 0xFF000000.toInt(), "round", xs.map { StrokePoint(it, 0.0, 1.0) }, false)

    private fun page(vararg layers: Layer): Page =
        Page(100.0, 100.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), layers.toList())

    @Test fun aMissLeavesThePageAlone() {
        val p = page(Layer(listOf(stroke(0.0, 10.0))))
        assertNull(PageEraser.erase(p, 90.0, 90.0, 5.0, EraserMode.STANDARD))
    }

    @Test fun standardModeSplitsTheTouchedStroke() {
        val p = page(Layer(listOf(stroke(0.0, 5.0, 10.0, 15.0, 20.0))))
        val out = PageEraser.erase(p, 10.0, 0.0, 2.0, EraserMode.STANDARD)!!
        assertEquals(2, out.layers[0].elements.size)
    }

    @Test fun wholeStrokeModeDeletesTheTouchedStroke() {
        val p = page(Layer(listOf(stroke(0.0, 10.0, 20.0), stroke(80.0, 90.0))))
        val out = PageEraser.erase(p, 10.0, 0.0, 2.0, EraserMode.WHOLE_STROKE)!!
        assertEquals(1, out.layers[0].elements.size)
        assertEquals(80.0, (out.layers[0].elements[0] as Stroke).points[0].x, 1e-9)
    }

    @Test fun hiddenLayersAreNeverErased() {
        val p = page(Layer(listOf(stroke(0.0, 10.0, 20.0))), Layer(listOf(stroke(0.0, 10.0, 20.0))))
        val out = PageEraser.erase(p, 10.0, 0.0, 2.0, EraserMode.WHOLE_STROKE, hiddenLayers = setOf(0))!!
        assertEquals(1, out.layers[0].elements.size)
        assertEquals(0, out.layers[1].elements.size)
    }

    @Test fun anAllHiddenPageReportsNoChange() {
        val p = page(Layer(listOf(stroke(0.0, 10.0, 20.0))))
        assertNull(PageEraser.erase(p, 10.0, 0.0, 2.0, EraserMode.STANDARD, hiddenLayers = setOf(0)))
    }

    @Test fun onlyTheSelectedLayerIsErased() {
        val p = page(Layer(listOf(stroke(0.0, 10.0, 20.0))), Layer(listOf(stroke(0.0, 10.0, 20.0))))
        val out = PageEraser.erase(p, 10.0, 0.0, 2.0, EraserMode.WHOLE_STROKE, onlyLayer = 1)!!
        assertEquals(1, out.layers[0].elements.size)
        assertEquals(0, out.layers[1].elements.size)
    }

    @Test fun aHitOnAnUnselectedLayerReportsNoChange() {
        val p = page(Layer(listOf(stroke(0.0, 10.0, 20.0))), Layer(emptyList()))
        assertNull(PageEraser.erase(p, 10.0, 0.0, 2.0, EraserMode.STANDARD, onlyLayer = 1))
    }

    @Test fun untouchedLayersKeepTheirIdentity() {
        val keep = Layer(listOf(stroke(80.0, 90.0)), "ink")
        val p = page(keep, Layer(listOf(stroke(0.0, 10.0, 20.0))))
        val out = PageEraser.erase(p, 10.0, 0.0, 2.0, EraserMode.WHOLE_STROKE)!!
        assertTrue(out.layers[0] === keep)
    }

    /** The eraser has no size scheme of its own: a thicker pen slot must mean a wider rubber. */
    @Test fun eraserTipGrowsWithThePenTip() {
        assertTrue(eraserRadiusPt(0.85f) < eraserRadiusPt(1.5f))
        assertTrue(eraserRadiusPt(1.5f) < eraserRadiusPt(2.6f))
    }

    /** …and it is always wider than the ink it rubs out, however hair-thin the pen slot is. */
    @Test fun eraserTipStaysAimableForAThinPen() {
        assertTrue(eraserRadiusPt(0.1f) >= ERASER_RADIUS_MIN_PT)
        // The radius is the pen's full width, i.e. twice the ink's own radius.
        assertEquals(1.5, eraserRadiusPt(1.5f), 1e-9)
    }

    private fun table(left: Double = 50.0, top: Double = 50.0): TableElement = TableElement(
        left = left,
        top = top,
        colWidths = listOf(100.0, 100.0),
        rowHeights = listOf(30.0, 30.0),
        cells = listOf(
            listOf(TableCell("A"), TableCell("B")),
            listOf(TableCell("C"), TableCell("D")),
        ),
        headerRow = true,
        id = "tbl_test",
    )

    private fun plot(): PlotElement = PlotElement(
        left = 100.0,
        top = 100.0,
        right = 300.0,
        bottom = 200.0,
        curves = listOf(PlotCurve(PlotKind.CARTESIAN, "x", 0xFF000000.toInt())),
        xMin = -10.0,
        xMax = 10.0,
        id = "plt_test",
    )

    @Test fun erasingTableDeletesItInWholeStrokeMode() {
        val tbl = table()
        val p = page(Layer(listOf(tbl)))
        val out = PageEraser.erase(p, 50.0, 50.0, 3.0, EraserMode.WHOLE_STROKE)
        assertNotNull(out)
        assertTrue(out!!.layers[0].elements.isEmpty())
    }

    @Test fun erasingTableDeletesItInStandardMode() {
        val tbl = table()
        val p = page(Layer(listOf(tbl)))
        val out = PageEraser.erase(p, 50.0, 50.0, 3.0, EraserMode.STANDARD)
        assertNotNull(out)
        assertTrue(out!!.layers[0].elements.isEmpty())
    }

    @Test fun missingLivingEntityLeavesItAlone() {
        val tbl = table()
        val p = page(Layer(listOf(tbl)))
        assertNull(PageEraser.erase(p, 500.0, 500.0, 5.0, EraserMode.STANDARD))
    }

    @Test fun erasingPlotDeletesIt() {
        val plt = plot()
        val p = page(Layer(listOf(plt)))
        val out = PageEraser.erase(p, 100.0, 100.0, 5.0, EraserMode.WHOLE_STROKE)
        assertNotNull(out)
        assertTrue(out!!.layers[0].elements.isEmpty())
    }

    @Test fun erasingHandwritingInsideTableCellDoesNotDeleteTable() {
        val tbl = TableElement(
            left = 50.0,
            top = 50.0,
            colWidths = listOf(200.0),
            rowHeights = listOf(200.0),
            cells = listOf(listOf(TableCell())),
            id = "tbl_cell",
        )
        val note = stroke(149.0, 150.0, 151.0).let {
            it.copy(points = it.points.map { pt -> pt.copy(y = 150.0) })
        }
        val p = page(Layer(listOf(tbl, note)))

        val out = PageEraser.erase(p, 150.0, 150.0, 2.0, EraserMode.WHOLE_STROKE)!!
        assertEquals(1, out.layers[0].elements.size)
        assertTrue(out.layers[0].elements[0] is TableElement)
    }
}
