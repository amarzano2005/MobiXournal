package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A board's cells have to stay **one per page, in the document's own order**: `grid[i]` names
 * `pages[i]`, and a chunk is written under the cell its name carries. The two cases that can break it
 * are a cell created by a write and an undo/redo moving the page list —
 * `docs/infinite-canvas.md` → *Writing on a board*.
 */
class BoardGridTest {

    @Test
    fun aCellAppendedByAWriteSitsAfterTheOnesTheFileDeclared() {
        val grid = BoardGrid(listOf(0 to 0), CanvasMode.INFINITE_2D)
        grid.append(2 to -1)

        assertEquals(2, grid.size)
        assertEquals(0 to 0, grid.coordAt(0))
        assertEquals(2 to -1, grid.coordAt(1))
        assertEquals(1, grid.indexOf(2 to -1))
        assertEquals(-1, grid.indexOf(5 to 5))
        assertNull(grid.coordAt(2))
    }

    @Test
    fun anUndoTrimsTheTailAndARedoHandsTheSameCellBack() {
        val grid = BoardGrid(listOf(0 to 0), CanvasMode.INFINITE_2D)
        grid.append(1 to 0)
        grid.append(1 to 1)

        grid.fit(2) // undo: the second cell's page is gone
        assertEquals(listOf(0 to 0, 1 to 0), grid.toList())

        grid.fit(3) // redo: the page is back, at the cell it was written into
        assertEquals(listOf(0 to 0, 1 to 0, 1 to 1), grid.toList())
    }

    @Test
    fun aWriteAfterAnUndoDropsTheBranchNothingCanRedo() {
        val grid = BoardGrid(listOf(0 to 0), CanvasMode.INFINITE_2D)
        grid.append(1 to 0)
        grid.fit(1) // undone
        grid.append(0 to 3) // a new cell, so the undo branch is gone

        assertEquals(listOf(0 to 0, 0 to 3), grid.toList())
        // Undoing and redoing the *new* edit brings the new cell back — never the abandoned one.
        grid.fit(1)
        grid.fit(2)
        assertEquals(listOf(0 to 0, 0 to 3), grid.toList())
    }

    @Test
    fun aPageNothingRecordedACellForTakesTheStripPosition() {
        // A page-list command on a board (which the chrome refuses) can leave a page with no cell
        // recorded: it falls back to the mode's strip, the same reading the writer makes.
        val grid = BoardGrid(listOf(1 to 1), CanvasMode.INFINITE_HORIZONTAL)
        grid.fit(3)

        assertEquals(listOf(1 to 1, 1 to 0, 2 to 0), grid.toList())
    }

    @Test
    fun aWriteIntoACellTheBoardAlreadyHasKeepsItsIndex() {
        val grid = BoardGrid(listOf(0 to 0, 1 to 0), CanvasMode.INFINITE_VERTICAL)

        assertEquals(1, grid.indexOf(1 to 0))
        // The strip fallback never overwrites a cell the file named, however the pages shift.
        grid.fit(2)
        assertEquals(listOf(0 to 0, 1 to 0), grid.toList())
    }
}
