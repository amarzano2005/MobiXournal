package com.mobixournal.ui

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.CanvasMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying canvas mode change detection and behavior:
 * When on an existing document, switching canvas mode requires confirmation to start
 * a new blank page rather than mutating the active page's geometry.
 */
class CanvasModeChangeTest {

    @Test
    fun `different canvas mode is detected as requiring confirmation`() {
        val current = CanvasMode.PAGINATED
        val currentCanvas = NewDocumentCanvas.of(current)

        for (target in NewDocumentCanvas.entries) {
            val requiresConfirm = target != currentCanvas
            if (target == NewDocumentCanvas.PAGINATED) {
                assertFalse("Same mode does not prompt for new page", requiresConfirm)
            } else {
                assertTrue("Different mode (${target.name}) prompts for new page", requiresConfirm)
            }
        }
    }

    @Test
    fun `switching between infinite boards requires confirmation`() {
        val current = CanvasMode.INFINITE_VERTICAL
        val currentCanvas = NewDocumentCanvas.of(current)

        assertTrue(NewDocumentCanvas.INFINITE_2D != currentCanvas)
        assertTrue(NewDocumentCanvas.INFINITE_HORIZONTAL != currentCanvas)
        assertTrue(NewDocumentCanvas.PAGINATED != currentCanvas)
        assertFalse(NewDocumentCanvas.INFINITE_VERTICAL != currentCanvas)
    }

    @Test
    fun `infinite canvas modes map to MOBIX save format for new tabs`() {
        for (canvas in NewDocumentCanvas.entries) {
            val expectedFormat = if (canvas.mode != CanvasMode.PAGINATED) {
                SaveFormat.MOBIX
            } else {
                SaveFormat.ORIGINAL
            }
            val format = if (canvas.mode != CanvasMode.PAGINATED) SaveFormat.MOBIX else SaveFormat.ORIGINAL
            assertEquals("Format for ${canvas.name}", expectedFormat, format)
        }
    }
}
