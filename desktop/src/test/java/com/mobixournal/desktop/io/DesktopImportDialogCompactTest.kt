package com.mobixournal.desktop.io

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.mobixournal.design.ImportCardLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The import card in the window's smallest places** — the claim `docs/guide-pdf-and-audio.md` makes when
 * it says the card fits the room it is given.
 *
 * The window sets no minimum size, so the card has to hold together wherever the OS leaves it. These are
 * real Compose layouts (`ImageComposeScene`), not arithmetic: the card is composed at the sizes a window
 * can be dragged to, with the text at 100 % and at 150 %, and what is measured is the card's own bounds
 * after layout — not what it asked for.
 *
 * What is checked is the one thing a reader would lose if it regressed: the card stays **inside** the
 * window on both axes. The body scrolls and the buttons stay outside that scroll, which is why a card
 * shorter than its content is still usable rather than cut off at the Cancel line.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopImportDialogCompactTest {

    private fun request() = ImportPdfRequest(
        file = File("lecture-handout-physics-optics-chapter-three.pdf"),
        pageCount = 120,
        chooseMode = true,
    )

    /** Lay the card out in a [widthPx] × [heightPx] window and answer the size it ended up with. */
    private fun cardSize(widthPx: Int, heightPx: Int, fontScale: Float = 1f): IntSize {
        var measured = IntSize.Zero
        val scene = ImageComposeScene(widthPx, heightPx, Density(1f, fontScale)) {
            ImportPdfDialog(
                request = request(),
                onEdit = {},
                onConfirm = {},
                onCancel = {},
                modifier = Modifier.onGloballyPositioned { measured = it.size },
            )
        }
        return try {
            Snapshot.sendApplyNotifications()
            scene.render()
            measured
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a roomy window keeps the card at its 420 dp width`() {
        val size = cardSize(900, 640)
        assertEquals(420, size.width)
        assertTrue("the card must fit the window: $size", size.height <= 640)
    }

    @Test
    fun `a narrow window narrows the card instead of clipping it`() {
        val size = cardSize(320, 420)
        assertTrue("the card must fit the window's width: $size", size.width <= 320)
        assertTrue("and its height: $size", size.height <= 420)
    }

    @Test
    fun `a short window caps the card's height so the buttons stay on screen`() {
        val size = cardSize(420, 260)
        assertTrue("the card must fit 260 px: $size", size.height <= 260)
        assertTrue("it keeps the edge air below itself", size.height <= 260 - 2 * ImportCardLayout.EDGE_DP.toInt())
    }

    @Test
    fun `a large text scale keeps the card inside the same window`() {
        val size = cardSize(320, 260, fontScale = 1.5f)
        assertTrue("the card must fit a 320 x 260 window at 150 %: $size", size.width <= 320 && size.height <= 260)
    }
}
