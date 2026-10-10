package com.mobixournal.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The quick-colour dots are tapped in **rectangular cells** — three of them share one rail slot's
 * height, so each cell is wider than it is tall — and a tap's ripple, or a hovering stylus' state
 * layer, is clipped by the shape of the node it is drawn on. On the bare cell that lit up a grey box
 * around a round dot; [DotCellShape] is what keeps the highlight the dot's own circle while the cell
 * stays the touch target.
 *
 * The distinction it exists for is the one worth pinning: in a cell that is not square, the highlight
 * is *as wide as the cell is tall* and centred, where `CircleShape` would stretch across the whole
 * cell as an oval (`RoundRect`'s own radii are not readable from the outline, so the assertions are on
 * the box the shape covers, which is what separates the two).
 */
class DotCellShapeTest {

    private fun outlineOf(width: Float, height: Float): Outline.Rounded =
        DotCellShape.createOutline(Size(width, height), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded

    @Test
    fun `a wide cell gets a circle as wide as the cell is tall, centred in it`() {
        // The real geometry: one rail slot's width share by a third of its height.
        val rect = outlineOf(width = 46f, height = 30f).roundRect
        assertEquals(8f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(38f, rect.right, 0.01f)
        assertEquals(30f, rect.bottom, 0.01f)
        // As wide as it is tall: a circle. An oval would have spanned the cell's full 46f.
        assertEquals(rect.bottom - rect.top, rect.right - rect.left, 0.01f)
    }

    @Test
    fun `a tall cell is capped by its width instead`() {
        val rect = outlineOf(width = 30f, height = 46f).roundRect
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(8f, rect.top, 0.01f)
        assertEquals(30f, rect.right, 0.01f)
        assertEquals(38f, rect.bottom, 0.01f)
        assertEquals(rect.right - rect.left, rect.bottom - rect.top, 0.01f)
    }

    @Test
    fun `a square cell is the whole cell — nothing is trimmed from a dot that already fits`() {
        val rect = outlineOf(width = 30f, height = 30f).roundRect
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(30f, rect.right, 0.01f)
        assertEquals(30f, rect.bottom, 0.01f)
    }

    @Test
    fun `it is not what CircleShape draws in a wide cell`() {
        val ours = outlineOf(width = 46f, height = 30f).roundRect
        val circleShape = CircleShape
            .createOutline(Size(46f, 30f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        val theirs = circleShape.roundRect
        // CircleShape spans the cell's full width (an oval); ours stops at the cell's height.
        assertEquals(46f, theirs.right - theirs.left, 0.01f)
        assertNotEquals(theirs.right - theirs.left, ours.right - ours.left)
    }
}
