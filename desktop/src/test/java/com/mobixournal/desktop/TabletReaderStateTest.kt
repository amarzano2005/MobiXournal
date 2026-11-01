package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DesktopTablet
import com.mobixournal.desktop.sheet.PageCanvas

import androidx.compose.runtime.MutableState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's **device readers** must be snapshot state, not plain fields.
 *
 * The canvas reads the pen stream and the packet source **during composition** (`PageCanvas`'s `pen` and
 * `packets` arguments), and a tablet plugged in under a running window rebuilds those readers — new
 * objects, on the same window. A plain `var` made that rebuild invisible: the composed page kept pointing
 * at the readers that had just been closed, so the live packets were subscribed by nobody, the pen's
 * pressure vanished, and the ink fell back on Windows' mouse emulation. The status bar said `pen: Wintab`
 * while nothing was listening, which is exactly the "input goes mad when I plug the tablet in" report.
 *
 * Reading the identity as state is what makes the rebuild reach the canvas: it disposes its old
 * subscription (committing the stroke it was carrying) and takes the new one. This test is structural on
 * purpose — the readers cannot be opened without a real tablet, so the requirement is pinned where it can
 * be: the fields behind the three properties are `MutableState`, which is the difference between "the
 * canvas is told" and "the canvas is told nothing".
 */
class TabletReaderStateTest {

    @Test
    fun `the device readers are observable, so a hot-plug rebuild reaches the canvas`() {
        for (name in listOf("ink", "wintab", "wheel")) {
            val field = DesktopTablet::class.java.getDeclaredField("$name\$delegate")
            assertTrue(
                "$name must be snapshot state: a plain var leaves the canvas on a closed reader",
                MutableState::class.java.isAssignableFrom(field.type),
            )
        }
    }
}
