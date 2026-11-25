package com.mobixournal.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [LivingInspectorAnchor] positioning and universal keyboard-avoidance helpers.
 */
class LivingInspectorAnchorTest {

    @Test
    fun effectiveCanvasSizeSubtractsTallestKeyboardInset() {
        val full = IntSize(width = 1200, height = 800)

        assertEquals(
            IntSize(width = 1200, height = 500),
            effectiveInspectorCanvasSize(full, imeBottomPx = 300, customKeyboardHeightPx = 0),
        )
        assertEquals(
            IntSize(width = 1200, height = 460),
            effectiveInspectorCanvasSize(full, imeBottomPx = 200, customKeyboardHeightPx = 340),
        )
        assertEquals(
            IntSize(width = 1200, height = 0),
            effectiveInspectorCanvasSize(full, imeBottomPx = 950, customKeyboardHeightPx = 0),
        )
    }

    @Test
    fun clampInspectorToCanvasLiftsPanelAboveKeyboardWithoutChangingX() {
        val inspector = IntSize(width = 320, height = 280)
        val reducedCanvas = IntSize(width = 1200, height = 500)

        val clamped = clampInspectorToCanvas(
            offset = IntOffset(x = 640, y = 420),
            inspectorSize = inspector,
            canvasSize = reducedCanvas,
            marginPx = 8,
        )

        // maxY = 500 - 280 - 8 = 212
        assertEquals(IntOffset(x = 640, y = 212), clamped)
    }

    @Test
    fun livingKeyboardControllerEditsMovesCaretAndCloses() {
        var latest = ""
        val controller = LivingKeyboardController()
        controller.open(
            fieldId = "edge_0",
            kind = LivingKeyboardKind.NUMERIC,
            initialText = "12",
            allowNegative = false,
            onTextChange = { latest = it },
        )
        controller.applyKey(CircuitKey.Ch("."))
        controller.applyKey(CircuitKey.Ch("5"))
        assertEquals("12.5", latest)
        controller.moveCaret(-1)
        controller.applyKey(CircuitKey.Backspace)
        assertEquals("125", latest)
        controller.close()
        assertEquals(false, controller.isOpen)
    }

    @Test
    fun anchoredOffsetFallsBackBelowWhenNeitherSideFits() {
        val offset = anchoredInspectorOffset(
            selectionLeft = 100f,
            selectionTop = 100f,
            selectionRight = 600f,
            selectionBottom = 350f,
            inspectorSize = IntSize(width = 280, height = 300),
            canvasSize = IntSize(width = 720, height = 800),
            gapPx = 12,
            marginPx = 8,
        )
        // Neither right (612+280 > 712) nor left (100-12-280 < 8) fits -> below at 350+12 = 362
        assertEquals(IntOffset(x = 100, y = 362), offset)
    }
}
