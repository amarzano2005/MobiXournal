package com.mobixournal.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.mobixournal.desktop.io.PdfTextCopyBar
import com.mobixournal.desktop.io.PdfTextCopyUi
import com.mobixournal.desktop.sheet.selectionBarOffset
import com.mobixournal.render.Bounds
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The window's PDF Copy bar, sized and placed against the selected words.**
 *
 * It used to place itself with a hard-coded 78 × 30 dp capsule — true at 100 % text in one font, and wrong
 * the moment the window's text scale or density made the capsule wider: the clamp still subtracted 78 dp
 * and let the real capsule hang over the pane's edge. So there are two claims, and each is measured where
 * it lives: the scene composes the real bar and **measures** the capsule it draws, and the placement rule is
 * asked what it does with exactly that size — at the right edge, under and above the highlight, and in a
 * pane narrower than the bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PdfTextCopyBarTest {

    private val canvas = Size(600f, 400f)

    /** Compose the bar and answer the size of the capsule it actually drew, in px at density 1. */
    private fun measuredCapsule(fontScale: Float): Size {
        var size = Size.Zero
        val scene = ImageComposeScene(600, 400, Density(1f, fontScale)) {
            MaterialTheme {
                PdfTextCopyBar(
                    ui = PdfTextCopyUi(bounds = Bounds(100.0, 100.0, 300.0, 140.0), onCopy = {}),
                    zoom = 1f,
                    density = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                    canvas = canvas,
                    modifier = Modifier.onGloballyPositioned {
                        size = Size(it.size.width.toFloat(), it.size.height.toFloat())
                    },
                )
            }
        }
        return try {
            Snapshot.sendApplyNotifications()
            scene.render()
            size
        } finally {
            scene.close()
        }
    }

    private fun placed(bounds: Bounds, bar: Size, pane: Size = canvas): IntOffset = selectionBarOffset(
        bounds = bounds,
        pxPerPt = 1f,
        originPx = Offset.Zero,
        canvas = pane,
        bar = bar,
        gapPx = 8f,
    )

    @Test
    fun `the capsule is measured rather than assumed`() {
        val plain = measuredCapsule(fontScale = 1f)
        val large = measuredCapsule(fontScale = 1.5f)
        assertTrue("the bar drew a capsule: $plain", plain.width > 70f && plain.height > 20f)
        assertTrue("150 % text draws a wider capsule than 100 %: $large against $plain", large.width > plain.width)
    }

    @Test
    fun `the measured capsule is placed under the selected lines`() {
        val bar = measuredCapsule(fontScale = 1f)
        val placed = placed(Bounds(100.0, 100.0, 300.0, 140.0), bar)
        assertTrue("it starts below the highlight: $placed", placed.y >= 140f)
        assertTrue("and is centred on it: $placed", placed.x > 60f)
    }

    @Test
    fun `it moves above a selection at the foot of the pane`() {
        val bar = measuredCapsule(fontScale = 1f)
        val placed = placed(Bounds(100.0, 300.0, 300.0, 390.0), bar)
        assertTrue("the capsule clears the highlight upwards: $placed", placed.y + bar.height <= 300f)
        assertTrue("and stays inside the pane: $placed", placed.y >= 6f)
    }

    @Test
    fun `a selection at the right edge keeps the real capsule inside the pane`() {
        // The clamp subtracts the capsule's **measured** width: at 150 % that is wider than the 78 dp the
        // bar used to assume, and a stale number would put its right edge past the canvas.
        val bar = measuredCapsule(fontScale = 1.5f)
        val placed = placed(Bounds(500.0, 100.0, 595.0, 140.0), bar)
        assertTrue("the whole capsule ends inside the pane: ${placed.x + bar.width}", placed.x + bar.width <= canvas.width)
        assertTrue("and keeps its margin: ${placed.x}", placed.x <= canvas.width - bar.width - 6f)
    }

    @Test
    fun `a capsule wider than the pane is pinned to the margin, not pushed off`() {
        val wide = Size(canvas.width + 40f, 30f)
        val placed = placed(Bounds(100.0, 100.0, 595.0, 140.0), wide)
        assertTrue("it starts at the margin instead of going negative: $placed", placed.x >= 6f)
    }
}
