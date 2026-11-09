package com.mobixournal.render

import com.mobixournal.format.model.PlotElement
import org.junit.Assert.*
import org.junit.Test

class PlotPreviewPaletteTest {
    private val plot = PlotElementFactory.plotElementIn(
        PlotBox(0.0, 0.0, 320.0, 140.0), listOf(PlotCurve("sin(x)", 0xFF000000.toInt())), -10.0, 10.0,
    )

    @Test fun darkPreviewAdaptsInvisibleInkWithoutChangingTheSavedPlot() {
        val original = LivingInk.of(plot)
        val preview = PlotPreviewPalette.adapt(plot, 0xFF161618.toInt(), 0xFFAAAAAA.toInt(), 0xFF64A8FF.toInt())
        assertEquals(0xFF64A8FF.toInt(), preview.curves.single().color)
        assertEquals(0xFFAAAAAA.toInt(), preview.axisColor)
        assertEquals(0xFF000000.toInt(), plot.curves.single().color)
        assertEquals(original, LivingInk.of(plot))
        assertEquals(plot.copy(axisColor = preview.axisColor, curves = preview.curves), preview)
    }

    @Test fun lightPreviewKeepsReadableDocumentColors() {
        val preview = PlotPreviewPalette.adapt(plot, 0xFFFFFFFF.toInt(), 0xFF464C55.toInt(), 0xFF2460FF.toInt())
        assertEquals(plot.curves, preview.curves)
        assertTrue(PlotPreviewPalette.contrast(preview.curves.single().color, 0xFFFFFFFF.toInt()) >= 3.0)
    }

    @Test fun whiteCurveAlsoAdaptsOnPaperAndContrastIsSymmetric() {
        val white = plot.copy(curves = plot.curves.map { it.copy(color = 0xFFFFFFFF.toInt()) })
        val preview = PlotPreviewPalette.adapt(white, 0xFFFFFFFF.toInt(), 0xFF464C55.toInt(), 0xFF2460FF.toInt())
        assertEquals(0xFF2460FF.toInt(), preview.curves.single().color)
        assertEquals(21.0, PlotPreviewPalette.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.001)
        assertEquals(21.0, PlotPreviewPalette.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.001)
    }
}
