package com.mobixournal.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The import card's arithmetic**, so a compact window is a decision and not a guess: the card never
 * asks for more width than it is offered, it keeps the 420 dp ceiling on a wide screen, and the range
 * stacks once either the width or the text scale says the two fields no longer fit.
 */
class ImportCardLayoutTest {

    @Test
    fun `a wide window gets exactly the roomy card it always had`() {
        assertEquals(420f, ImportCardLayout.cardWidth(900f), 0.01f)
        assertEquals(420f, ImportCardLayout.cardWidth(2560f), 0.01f)
        assertEquals(420f, ImportCardLayout.cardWidth(452f), 0.01f)
    }

    @Test
    fun `a narrower window gets a narrower card, with the edge air kept on both sides`() {
        assertEquals(388f, ImportCardLayout.cardWidth(420f), 0.01f)
        assertEquals(268f, ImportCardLayout.cardWidth(300f), 0.01f)
        assertEquals(168f, ImportCardLayout.cardWidth(200f), 0.01f)
    }

    @Test
    fun `the card is never wider than the space it sits in, however small that is`() {
        for (room in listOf(0f, 16f, 31f, 32f, 33f, 100f)) {
            val width = ImportCardLayout.cardWidth(room)
            assertTrue("cardWidth($room) = $width", width >= 0f)
            assertTrue("cardWidth($room) = $width must fit $room", width <= room)
        }
    }

    @Test
    fun `the range stays on one line while the width and the text scale allow it`() {
        assertFalse(ImportCardLayout.stacked(420f, 1f))
        assertFalse(ImportCardLayout.stacked(360f, 1f))
        assertFalse(ImportCardLayout.stacked(388f, 1.2f))
    }

    @Test
    fun `the range stacks under a narrow card`() {
        assertTrue(ImportCardLayout.stacked(359f, 1f))
        assertTrue(ImportCardLayout.stacked(268f, 1f))
        assertTrue(ImportCardLayout.stacked(200f, 1f))
    }

    @Test
    fun `the range stacks at a large text scale, whatever the width`() {
        assertTrue(ImportCardLayout.stacked(420f, 1.3f))
        assertTrue(ImportCardLayout.stacked(420f, 1.5f))
        assertTrue(ImportCardLayout.stacked(420f, 2f))
    }
}
