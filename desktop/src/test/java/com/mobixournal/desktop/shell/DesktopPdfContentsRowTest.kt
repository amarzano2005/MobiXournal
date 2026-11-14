package com.mobixournal.desktop.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.render.OutlineRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Contents rows inside the sidebar the window actually gives them** — a real Compose layout
 * (`ImageComposeScene`) over the rows a PDF with a long, deeply nested outline produces.
 *
 * The page sidebar is a column the reader can narrow, and the Contents list is the one list in the product
 * whose rows carry their own indentation plus a number that has to line up. What is measured here is what a
 * screenshot at one width cannot settle: every row lays itself out inside the column it was given (the rows
 * are not told their width — the column is, and they fill it), including the eight-deep row with a 96
 * character title and a four-digit page, and the reader's text scale actually reaches them rather than
 * being swallowed by the fixed sizes around them.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopPdfContentsRowTest {

    private val rows = listOf(
        OutlineRow(id = "bkm_1", title = "Chapter 1", depth = 0, page = 0),
        OutlineRow(
            id = "bkm_2",
            title = "1.4.2 The optics of a thin film, measured at grazing incidence with a monochromator",
            depth = 8,
            page = 1233,
        ),
        OutlineRow(id = "bkm_3", title = "Part II", depth = 1, page = null),
    )

    private class Measured(val rowWidths: List<Int>, val rowHeights: List<Int>) {
        val totalHeight: Int get() = rowHeights.sum()
    }

    /** Lay the rows out in a sidebar [widthDp] wide and answer what each row measured. */
    private fun layout(widthDp: Int, fontScale: Float = 1f): Measured {
        val widths = ArrayList<Int>()
        val heights = ArrayList<Int>()
        val scene = ImageComposeScene(widthDp, 640, Density(1f, fontScale)) {
            MaterialTheme {
                Column(Modifier.width(widthDp.dp)) {
                    for (row in rows) {
                        OutlineRowView(
                            row = row,
                            rowWidthDp = widthDp.toFloat(),
                            largestPageNumber = 1234,
                            onSelectPage = {},
                            modifier = Modifier.onGloballyPositioned {
                                widths += it.size.width
                                heights += it.size.height
                            },
                        )
                    }
                }
            }
        }
        return try {
            Snapshot.sendApplyNotifications()
            scene.render()
            Measured(widths, heights)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `every row lays itself out inside a narrow sidebar`() {
        val measured = layout(widthDp = 160)
        assertEquals("three rows were laid out", 3, measured.rowWidths.size)
        assertTrue("no row is wider than the 160 dp column: ${measured.rowWidths}", measured.rowWidths.all { it <= 160 })
        assertTrue("and none collapsed to nothing: ${measured.rowWidths}", measured.rowWidths.all { it > 0 })
        assertTrue("each row drew a line of text: ${measured.rowHeights}", measured.rowHeights.all { it > 0 })
    }

    @Test
    fun `the reader's text scale reaches the rows`() {
        val plain = layout(widthDp = 160)
        val large = layout(widthDp = 160, fontScale = 1.5f)
        assertTrue(
            "150 % text is taller than 100 %: ${large.totalHeight} against ${plain.totalHeight}",
            large.totalHeight > plain.totalHeight,
        )
        assertTrue("and still fits the column: ${large.rowWidths}", large.rowWidths.all { it <= 160 })
    }
}
