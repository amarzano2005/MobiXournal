package com.mobixournal.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import com.mobixournal.design.DesignTokens
import com.mobixournal.desktop.io.PdfTextCopyBar
import com.mobixournal.desktop.io.PdfTextCopyUi
import com.mobixournal.render.Bounds
import com.mobixournal.render.PdfBarBox
import com.mobixournal.render.PdfBarOffset
import com.mobixournal.render.PdfSelectionSpan
import com.mobixournal.render.PdfTextBarPlacement
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The window's PDF Copy bar**: how big it draws, where it puts itself against the words, and when it exists.
 *
 * It used to be placed by a hard-coded 78 × 30 dp capsule — true at 100 % text in one font — and it was mounted
 * for as long as a range existed, so it followed the pointer through the drag and hung from the union of a
 * multiline passage. So the cases here **measure the real composable in a scene** (`ImageComposeScene`): the
 * size it drew, and the offset it actually took — below the line the pointer is on, above it when the pane ends
 * below, inside the pane at either edge, absent while the range is still being made, absent when no selected
 * line is visible at all.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PdfTextCopyBarTest {

    private val canvas = Size(600f, 400f)

    /** What the bar drew, and where the scene put it, at density 1. */
    private data class Measured(val size: Size, val at: Offset)

    private fun measured(
        bounds: Bounds? = Bounds(100.0, 100.0, 300.0, 140.0),
        spans: List<PdfSelectionSpan> = emptyList(),
        active: PdfSelectionSpan? = null,
        settled: Boolean = true,
        highlightable: Boolean = false,
        fontScale: Float = 1f,
        pane: Size = canvas,
    ): Measured {
        var measured = Measured(Size.Zero, Offset.Zero)
        val scene = ImageComposeScene(pane.width.toInt(), pane.height.toInt(), Density(1f, fontScale)) {
            MaterialTheme {
                PdfTextCopyBar(
                    ui = PdfTextCopyUi(
                        bounds = bounds,
                        onCopy = {},
                        onHighlight = if (highlightable) ({}) else null,
                        spans = spans,
                        active = active,
                        settled = settled,
                    ),
                    zoom = 1f,
                    density = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                    canvas = pane,
                    modifier = Modifier.onGloballyPositioned {
                        measured = Measured(
                            Size(it.size.width.toFloat(), it.size.height.toFloat()),
                            it.positionInRoot(),
                        )
                    },
                )
            }
        }
        return try {
            Snapshot.sendApplyNotifications()
            scene.render()
            measured
        } finally {
            scene.close()
        }
    }

    private fun measuredCapsule(fontScale: Float, highlightable: Boolean = false): Size =
        measured(fontScale = fontScale, highlightable = highlightable).size

    // --- when it exists --------------------------------------------------------------------------

    @Test
    fun `the bar waits while the range is still being made`() {
        val extending = measured(settled = false, highlightable = true)
        assertTrue("nothing is drawn mid-drag: $extending", extending.size == Size.Zero)
        val settledBar = measured(settled = true, highlightable = true)
        assertTrue("and it is there on the release: ${settledBar.size}", settledBar.size.width > 70f)
    }

    @Test
    fun `a selection scrolled out of the pane draws no bar`() {
        val offscreen = measured(
            bounds = Bounds(-500.0, -500.0, -300.0, -400.0),
            spans = listOf(PdfSelectionSpan(-500.0, -500.0, -300.0, -400.0)),
        )
        assertTrue("the capsule is hidden, not pinned to an edge: ${offscreen.size}", offscreen.size == Size.Zero)
    }

    // --- how it draws ----------------------------------------------------------------------------

    @Test
    fun `the capsule is measured rather than assumed`() {
        val plain = measuredCapsule(fontScale = 1f)
        val large = measuredCapsule(fontScale = 1.5f)
        assertTrue("the bar drew a capsule: $plain", plain.width > 70f && plain.height > 20f)
        assertTrue("150 % text draws a wider capsule than 100 %: $large against $plain", large.width > plain.width)
    }

    @Test
    fun `the bar carries Highlight beside Copy, and only where the page may be written`() {
        val copying = measuredCapsule(fontScale = 1f)
        val both = measuredCapsule(fontScale = 1f, highlightable = true)
        assertTrue(
            "Highlight is a second word in the capsule: $both against $copying",
            both.width > copying.width + 20f,
        )
        // A read-only document passes no `onHighlight`, so the verb does not exist at all.
        assertTrue("Copy alone is the read-only bar: $copying", copying.width > 70f)
    }

    @Test
    fun `the bar is compact and every action still sits on the shared target`() {
        val bar = measuredCapsule(fontScale = 1f, highlightable = true)
        assertTrue(
            "the capsule is no taller than a compact control plus its hair of edge air: ${bar.height}",
            bar.height <= DesignTokens.COMPACT_BUTTON_SIZE_DP + 4,
        )
        assertTrue(
            "and no action is smaller than the shared control: ${bar.height}",
            bar.height >= DesignTokens.COMPACT_BUTTON_SIZE_DP,
        )
    }

    @Test
    fun `enlarged text widens the bar, and grows it rather than cropping a label that outgrows the pill`() {
        val plain = measuredCapsule(fontScale = 1f, highlightable = true)
        val large = measuredCapsule(fontScale = 2f, highlightable = true)
        assertTrue("200 % text draws a wider bar: $large against $plain", large.width > plain.width)
        assertTrue(
            "and stays on the shared target: ${large.height}",
            large.height >= DesignTokens.COMPACT_BUTTON_SIZE_DP,
        )
        // The pill's height is a floor, not a ceiling: text taller than 32 dp grows the bar instead of being
        // cropped by it, so a reader who enlarges text never loses half a word.
        val huge = measuredCapsule(fontScale = 4f, highlightable = true)
        assertTrue(
            "a label taller than the pill grows the bar: ${huge.height} against ${plain.height}",
            huge.height > plain.height,
        )
    }

    // --- where it goes ---------------------------------------------------------------------------

    /** The rule the bar applies, asked with the capsule it actually measured — density 1, so pt and px agree. */
    private fun placed(spans: List<PdfSelectionSpan>, active: PdfSelectionSpan?, bar: Size): PdfBarOffset? =
        PdfTextBarPlacement.offset(
            spans = spans.map { PdfBarBox(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) },
            active = active?.let {
                PdfBarBox(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
            },
            barWidth = bar.width,
            barHeight = bar.height,
            canvasWidth = canvas.width,
            canvasHeight = canvas.height,
            gapPx = 8f,
            marginPx = 6f,
        )

    @Test
    fun `it stands beside the line the pointer is on, clear of the rest of the range`() {
        val first = PdfSelectionSpan(100.0, 100.0, 300.0, 130.0)
        val last = PdfSelectionSpan(100.0, 200.0, 300.0, 230.0)
        val bar = measured(spans = listOf(first, last), active = first).size
        val offset = placed(listOf(first, last), first, bar)
        assertNotNull(offset)
        // Selecting upwards: the endpoint is on the top line, the range continues below it.
        assertTrue("above that line, not over the words below it: ${offset!!.y}", offset.y + bar.height <= 100f)
    }

    @Test
    fun `a range ending where the pointer is gets the bar below the last line`() {
        val first = PdfSelectionSpan(100.0, 100.0, 300.0, 130.0)
        val last = PdfSelectionSpan(100.0, 200.0, 300.0, 230.0)
        val bar = measured(spans = listOf(first, last), active = last).size
        val offset = placed(listOf(first, last), last, bar)!!
        assertTrue("one gap under the line: ${offset.y}", offset.y >= 200f)
        assertTrue("and clear of the passage", offset.y < canvas.height)
    }

    @Test
    fun `a selection at the foot of the pane lifts the capsule above its line`() {
        val line = PdfSelectionSpan(100.0, 300.0, 300.0, 390.0)
        val bar = measured(bounds = Bounds(100.0, 300.0, 300.0, 390.0), spans = listOf(line), active = line).size
        val offset = placed(listOf(line), line, bar)!!
        assertTrue("the capsule clears the words upwards: ${offset.y}", offset.y + bar.height <= 300f)
        assertTrue("and stays inside the pane: ${offset.y}", offset.y >= 6f)
    }

    @Test
    fun `a selection at the right edge keeps the real capsule inside the pane`() {
        val line = PdfSelectionSpan(500.0, 100.0, 595.0, 140.0)
        val bar = measured(bounds = Bounds(500.0, 100.0, 595.0, 140.0), spans = listOf(line), active = line).size
        val offset = placed(listOf(line), line, bar)!!
        assertTrue(
            "the whole capsule ends inside the pane: ${offset.x + bar.width}",
            offset.x + bar.width <= canvas.width,
        )
    }

    @Test
    fun `a capsule wider than the pane is pinned to the margin, not pushed off`() {
        val placed = PdfTextBarPlacement.offset(
            spans = listOf(PdfBarBox(100f, 100f, 595f, 140f)),
            active = null,
            barWidth = canvas.width + 40f,
            barHeight = 30f,
            canvasWidth = canvas.width,
            canvasHeight = canvas.height,
            gapPx = 8f,
            marginPx = 6f,
        )
        assertNotNull(placed)
        assertTrue("it starts at the margin instead of going negative: ${placed!!.x}", placed.x >= 6f)
    }
}
