package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalSpaceOpsTest {

    private fun stroke(top: Double, bottom: Double = top + 10.0) = Stroke(
        Tool.PEN, 0, "round",
        listOf(StrokePoint(5.0, top, 1.0), StrokePoint(15.0, bottom, 1.0)),
        uniformWidth = true,
    )

    private fun page(vararg layers: Layer) =
        Page(200.0, 400.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), layers.toList())

    private fun topsOf(page: Page): List<Double> =
        page.layers.flatMap { it.elements }.map { ElementBounds.of(it).top }

    @Test fun insertsSpaceOnlyBelowTheGrabLine() {
        val pages = listOf(page(Layer(listOf(stroke(20.0), stroke(120.0)))))
        val out = VerticalSpaceOps.shiftBelow(pages, 0, yPt = 100.0, dy = 50.0)
        assertEquals("stroke above the line stays put", 19.5, topsOf(out[0])[0], 1e-6)
        assertEquals("stroke below the line moves down", 169.5, topsOf(out[0])[1], 1e-6)
    }

    @Test fun straddlingElementStaysPutRatherThanBeingTorn() {
        // The line at y=25 passes through a stroke spanning 20..30; its top is above, so it holds.
        val pages = listOf(page(Layer(listOf(stroke(20.0, 30.0)))))
        val out = VerticalSpaceOps.shiftBelow(pages, 0, yPt = 25.0, dy = 40.0)
        assertSame("no element moved, so the page list is untouched", pages, out)
    }

    @Test fun everyLayerMovesTogether() {
        val pages = listOf(page(Layer(listOf(stroke(150.0))), Layer(listOf(TextElement("Sans", 12.0, 8.0, 200.0, 0, "hi")))))
        val out = VerticalSpaceOps.shiftBelow(pages, 0, yPt = 100.0, dy = 25.0)
        assertEquals(listOf(174.5, 225.0), topsOf(out[0]))
    }

    @Test fun pullingUpCarriesTheBlockAcrossTheGrabLine() {
        // The desktop behaviour: the shift is the pointer's travel, so pulling up moves the block past
        // the line it was grabbed at (139.5 - 80 = 59.5, well above the line at 100) instead of
        // stopping level with it.
        val pages = listOf(page(Layer(listOf(stroke(140.0)))))
        val out = VerticalSpaceOps.shiftBelow(pages, 0, yPt = 100.0, dy = -80.0)
        assertEquals(59.5, topsOf(out[0])[0], 1e-6)
    }

    @Test fun theBlockIsDecidedAtGrabTimeAndNoElementJoinsItMidDrag() {
        // The element above the line (top 19.5) was outside the block when the drag started, so it
        // stays put even as the block slides up past where it sits — a moving block must not recruit
        // the elements it passes.
        val pages = listOf(page(Layer(listOf(stroke(20.0, 30.0), stroke(140.0)))))
        val out = VerticalSpaceOps.shiftBelow(pages, 0, yPt = 100.0, dy = -60.0)
        assertEquals(listOf(19.5, 79.5), topsOf(out[0]))
    }

    @Test fun pullingUpWithNothingBelowIsANoOp() {
        val pages = listOf(page(Layer(listOf(stroke(20.0)))))
        assertSame(pages, VerticalSpaceOps.shiftBelow(pages, 0, yPt = 300.0, dy = -50.0))
    }

    @Test fun theInsertedGapSnapsToTheRulingWhenSnappingIsOn() {
        // A 27 pt drag on a 20 pt ruling becomes exactly one ruled line of space…
        assertEquals(20.0, VerticalSpaceOps.dragShift(dy = 27.0, snapSpacingPt = 20.0), 1e-9)
        // …and a plain sheet (spacing 0) leaves the drag continuous, as it was before snapping.
        assertEquals(27.0, VerticalSpaceOps.dragShift(dy = 27.0, snapSpacingPt = 0.0), 1e-9)
    }

    @Test fun snappingAppliesToAPullUpAsWell() {
        // Snapping is a property of the amount, not of the direction: -27 pt on a 20 pt ruling closes
        // exactly one ruled line, and can carry the block past the line just the same.
        assertEquals(-20.0, VerticalSpaceOps.dragShift(dy = -27.0, snapSpacingPt = 20.0), 1e-9)
    }

    @Test fun aSnappedShiftMovesTheElementsByTheRuledGap() {
        val pages = listOf(page(Layer(listOf(stroke(120.0)))))
        val out = VerticalSpaceOps.shiftBelow(pages, 0, yPt = 100.0, dy = 26.0, snapSpacingPt = 20.0)
        // 119.5 (the ink top) + one ruled line of 20, not the 26 pt the pointer actually travelled.
        assertEquals(139.5, topsOf(out[0])[0], 1e-6)
    }

    @Test fun snappingToTheRulingStillOnlyMovesWhatIsWhollyBelowTheLine() {
        // The desktop rule survives snapping: the block moves, the element the line passes through
        // does not — snapping changes *how far* the block goes, never *what* it is.
        val pages = listOf(page(Layer(listOf(stroke(20.0, 30.0), stroke(150.0)))))
        val out = VerticalSpaceOps.shiftBelow(pages, 0, yPt = 100.0, dy = 27.0, snapSpacingPt = 20.0)
        assertEquals(listOf(19.5, 169.5), topsOf(out[0]))
    }

    @Test fun zeroDragAndBadPageIndexAreNoOps() {
        val pages = listOf(page(Layer(listOf(stroke(120.0)))))
        assertSame(pages, VerticalSpaceOps.shiftBelow(pages, 0, yPt = 50.0, dy = 0.0))
        assertSame(pages, VerticalSpaceOps.shiftBelow(pages, 7, yPt = 50.0, dy = 30.0))
    }

    @Test fun aHeldBlockReAppliesExactlyWhatTheLineCarried() {
        // The live drag's shape: the block is read once, then re-applied to the *grab-time* page on every
        // frame. Two frames and one one-shot edit must agree element for element, including that the element
        // above the line is never recruited by the block sliding past it.
        val p = page(Layer(listOf(stroke(20.0, 30.0), stroke(140.0))))
        val block = VerticalSpaceOps.blockBelow(p, 100.0)
        val twice = VerticalSpaceOps.shiftBlock(p, block, -60.0)
        val once = VerticalSpaceOps.shiftBelow(listOf(p), 0, yPt = 100.0, dy = -60.0)[0]
        assertEquals(topsOf(once), topsOf(twice))
        assertEquals(listOf(19.5, 79.5), topsOf(twice))
    }

    @Test fun anUntouchedLayerIsNotRebuilt() {
        // What a frame pays: the layers with nothing below the line come back as the same objects, and a
        // shift that moves nothing returns the page itself, so a drag over an empty page allocates nothing.
        val still = Layer(listOf(stroke(20.0)))
        val moving = Layer(listOf(stroke(140.0)))
        val p = page(still, moving)
        val out = VerticalSpaceOps.shiftBlock(p, VerticalSpaceOps.blockBelow(p, 100.0), 30.0)
        assertSame("the layer above the line is shared, not copied", still, out.layers[0])
        assertEquals(169.5, ElementBounds.of(out.layers[1].elements[0]).top, 1e-6)
        assertSame("a zero shift is the page", p, VerticalSpaceOps.shiftBlock(p, VerticalSpaceOps.blockBelow(p, 100.0), 0.0))
    }

    @Test fun aBlockAnswersMembershipByPosition() {
        // The window's preview partitions a page with this instead of copying it: "does the line carry the
        // element at (layer, index)" is the one question both halves of the recorded sheet ask.
        val p = page(Layer(listOf(stroke(20.0), stroke(140.0))))
        val block = VerticalSpaceOps.blockBelow(p, 100.0)
        assertFalse("the element above the line stays", block.moves(0, 0))
        assertTrue("the one below it follows", block.moves(0, 1))
        assertFalse("a layer the block does not know", block.moves(3, 0))
    }

    @Test fun anEmptyBlockSaysSoAndReturnsThePage() {
        val p = page(Layer(listOf(stroke(20.0))))
        val block = VerticalSpaceOps.blockBelow(p, 300.0)
        assertTrue("nothing at or below the line", block.isEmpty)
        assertSame(p, VerticalSpaceOps.shiftBlock(p, block, 40.0))
    }
}
