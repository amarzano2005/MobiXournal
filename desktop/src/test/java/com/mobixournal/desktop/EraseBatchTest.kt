package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The eraser's **batching**: several moves per frame become one sweep, and the first bite still lands now.
 *
 * The hand reports moves faster than the screen draws, and every sweep is a page copy plus a re-record of
 * the page's whole ink picture — so one edit per *move* meant two or three pages per frame while rubbing
 * out, which is the eraser falling behind. The policy is here, clock-injected, so what it does to a drag
 * is a JVM test.
 */
class EraseBatchTest {

    private val frame = EraseBatch.FRAME_NANOS

    @Test
    fun `the first bite is due at once, so the eraser starts under the pen`() {
        val batch = EraseBatch()
        assertTrue("a press leaves its mark immediately", batch.add(10.0, 20.0, nowNanos = 5_000_000_000L))
        assertEquals(listOf(10.0 to 20.0), batch.drain())
    }

    @Test
    fun `moves inside one frame are gathered and swept together`() {
        val batch = EraseBatch()
        assertTrue(batch.add(10.0, 20.0, 1_000_000_000L))
        batch.drain()
        // Three moves within the same 8.3 ms frame: only the last one is due.
        assertFalse(batch.add(11.0, 20.0, 1_000_000_000L + frame / 4))
        assertFalse(batch.add(12.0, 20.0, 1_000_000_000L + frame / 2))
        assertTrue(batch.add(13.0, 20.0, 1_000_000_000L + frame))
        assertEquals(
            "the whole frame's travel, in the order the hand made it",
            listOf(11.0 to 20.0, 12.0 to 20.0, 13.0 to 20.0),
            batch.drain(),
        )
        assertTrue("and the batch is empty once it is applied", batch.drain().isEmpty())
    }

    @Test
    fun `a cancelled drag keeps nothing it had not swept`() {
        val batch = EraseBatch()
        batch.add(10.0, 20.0, 1_000_000_000L)
        batch.drain()
        batch.add(11.0, 20.0, 1_000_000_000L + frame / 4)
        batch.clear()
        assertTrue(batch.drain().isEmpty())
    }
}
