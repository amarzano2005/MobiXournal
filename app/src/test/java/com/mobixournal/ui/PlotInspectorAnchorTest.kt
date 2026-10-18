package com.mobixournal.ui

import android.graphics.RectF
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mobixournal.format.mobix.MobixPlotCodec
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.PlotCurve as ModelPlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.PlotShading
import com.mobixournal.render.PlotCurve
import com.mobixournal.render.PlotElementFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the anchored function plot inspector (`docs/ui-modernization.md`, Phase 3).
 *
 * Covers:
 *  1. [plotInspectorOffset] geometry: positioning beside the selection (preferring right, falling back
 *     to left, clamping to canvas bounds).
 *  2. Edit application round-trip via [PlotElementFactory.applyPlotEdit]: ensuring edited fields update
 *     while unedited fields ([PlotElement.samples], [PlotElement.parameters], [PlotElement.axisColor],
 *     [PlotElement.extraAttrs], [PlotElement.id]) survive the edit and survive a write/read round trip
 *     through [MobixPlotCodec].
 */
class PlotInspectorAnchorTest {

    @Test
    fun offsetPrefersRightSideWhenSpacePermits() {
        val canvasSize = IntSize(width = 1920, height = 1080)
        val inspectorSize = IntSize(width = 340, height = 400)
        val gapPx = 12
        val marginPx = 8

        val offset = plotInspectorOffset(
            selectionLeft = 100f,
            selectionTop = 200f,
            selectionRight = 600f,
            selectionBottom = 600f,
            inspectorSize = inspectorSize,
            canvasSize = canvasSize,
            gapPx = gapPx,
            marginPx = marginPx,
        )

        // Preferred right edge = 600 + 12 = 612; fits easily within 1920 - 8 = 1912
        assertEquals(IntOffset(x = 612, y = 200), offset)
    }

    @Test
    fun offsetFallsBackToLeftSideWhenRightSideClips() {
        val canvasSize = IntSize(width = 1000, height = 800)
        val inspectorSize = IntSize(width = 340, height = 400)
        val gapPx = 12
        val marginPx = 8

        val offset = plotInspectorOffset(
            selectionLeft = 500f,
            selectionTop = 150f,
            selectionRight = 900f,
            selectionBottom = 500f,
            inspectorSize = inspectorSize,
            canvasSize = canvasSize,
            gapPx = gapPx,
            marginPx = marginPx,
        )

        // Left placement = 500 - 12 - 340 = 148 >= 8 (fits on left)
        assertEquals(IntOffset(x = 148, y = 150), offset)
    }

    @Test
    fun offsetClampsVerticallyToCanvasBounds() {
        val canvasSize = IntSize(width = 1200, height = 800)
        val inspectorSize = IntSize(width = 340, height = 500)
        val gapPx = 12
        val marginPx = 8

        val offset = plotInspectorOffset(
            selectionLeft = 100f,
            selectionTop = 600f,
            selectionRight = 400f,
            selectionBottom = 750f,
            inspectorSize = inspectorSize,
            canvasSize = canvasSize,
            gapPx = gapPx,
            marginPx = marginPx,
        )

        // Y clamped to canvasHeight - inspectorHeight - margin = 800 - 500 - 8 = 292
        assertEquals(412, offset.x)
        assertEquals(292, offset.y)
    }

    @Test
    fun offsetHandlesNullSelectionGracefully() {
        val canvasSize = IntSize(width = 1200, height = 800)
        val inspectorSize = IntSize(width = 340, height = 400)
        val gapPx = 12
        val marginPx = 8

        val offset = plotInspectorOffset(
            selectionLeft = null,
            selectionTop = null,
            selectionRight = null,
            selectionBottom = null,
            inspectorSize = inspectorSize,
            canvasSize = canvasSize,
            gapPx = gapPx,
            marginPx = marginPx,
        )

        // Centered fallback: (1200 - 340) / 2 = 430, (800 - 400) / 2 = 200
        assertEquals(IntOffset(x = 430, y = 200), offset)
    }

    @Test
    fun editAppliedThroughInspectorRoundTripsAndPreservesUneditedFields() {
        // Create an original plot with unedited fields that the inspector has no inputs for
        val original = PlotElement(
            left = 100.0,
            top = 100.0,
            right = 420.0,
            bottom = 292.0,
            curves = listOf(
                ModelPlotCurve(kind = PlotKind.CARTESIAN, source = "sin(x)", color = 0xFF1976D2.toInt(), lineStyle = LineStyle.PLAIN),
            ),
            xMin = -6.28,
            xMax = 6.28,
            samples = 500, // custom samples
            showCriticalPoints = false,
            includeDerivative = false,
            shadeIntegral = null,
            parameters = mapOf("a" to 2.5, "b" to 4.0), // living parameters
            axisColor = 0xFF455A64.toInt(), // custom axis color
            summaryText = null,
            extraAttrs = mapOf("customTag" to "mobixV2"),
            id = "plot-stable-id-1234",
        )

        // Apply an edit simulating the inspector's Apply action:
        // Update formula to 2*cos(x), domain to [-3.14, 3.14], turn on critical points, derivative, summary & shade
        val editedCurves = listOf(
            PlotCurve(
                source = "2*cos(x)",
                color = 0xFFD32F2F.toInt(),
                lineStyle = LineStyle.DASHED,
            ),
        )

        val updated = PlotElementFactory.applyPlotEdit(
            original = original,
            editedCurves = editedCurves,
            xMin = -3.14,
            xMax = 3.14,
            showCriticalPoints = true,
            includeDerivative = true,
            includeSummary = true,
            shadeIntegral = PlotShading(0.0, 3.14),
        )

        // Verify edited fields updated
        assertEquals("2*cos(x)", updated.curves.single().source)
        assertEquals(0xFFD32F2F.toInt(), updated.curves.single().color)
        assertEquals(LineStyle.DASHED, updated.curves.single().lineStyle)
        assertEquals(-3.14, updated.xMin, 1e-9)
        assertEquals(3.14, updated.xMax, 1e-9)
        assertTrue(updated.showCriticalPoints)
        assertTrue(updated.includeDerivative)
        assertNotNull(updated.summaryText)
        assertEquals(PlotShading(0.0, 3.14), updated.shadeIntegral)

        // Verify unedited fields survived intact
        assertEquals("samples must be preserved", 500, updated.samples)
        assertEquals("parameters map must be preserved", mapOf("a" to 2.5, "b" to 4.0), updated.parameters)
        assertEquals("axisColor must be preserved", 0xFF455A64.toInt(), updated.axisColor)
        assertEquals("extraAttrs must be preserved", mapOf("customTag" to "mobixV2"), updated.extraAttrs)
        assertEquals("id must be preserved", "plot-stable-id-1234", updated.id)

        // Verify round-trip through MobixPlotCodec
        val encodedBytes = MobixPlotCodec.encode(updated)
        val decoded = MobixPlotCodec.decode(encodedBytes)
        assertNotNull("decoded plot element must not be null", decoded)
        val nonNullDecoded = decoded!!

        assertEquals(updated.curves.single().source, nonNullDecoded.curves.single().source)
        assertEquals(updated.curves.single().color, nonNullDecoded.curves.single().color)
        assertEquals(updated.curves.single().lineStyle, nonNullDecoded.curves.single().lineStyle)
        assertEquals(updated.xMin, nonNullDecoded.xMin, 1e-9)
        assertEquals(updated.xMax, nonNullDecoded.xMax, 1e-9)
        assertEquals(updated.samples, nonNullDecoded.samples)
        assertEquals(updated.showCriticalPoints, nonNullDecoded.showCriticalPoints)
        assertEquals(updated.includeDerivative, nonNullDecoded.includeDerivative)
        assertEquals(updated.shadeIntegral, nonNullDecoded.shadeIntegral)
        assertEquals(updated.parameters, nonNullDecoded.parameters)
        assertEquals(updated.axisColor, nonNullDecoded.axisColor)
        assertEquals(updated.id, nonNullDecoded.id)
    }
}
