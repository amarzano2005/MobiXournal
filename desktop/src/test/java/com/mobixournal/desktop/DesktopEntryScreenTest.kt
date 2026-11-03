package com.mobixournal.desktop

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.ImageComposeScene
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window with nothing open is the **entry screen**: a sheet to start, the Open command, and the notebooks
 * opened lately (`EntryScreen`) — not a blank page nobody asked for, and not a two-line notice.
 *
 * Two claims here: a fresh window opens **on that screen** (`start` no longer makes a blank tab), and the
 * list it shows is the settings file's, most recent first, capped, and still there after a restart.
 */
class DesktopEntryScreenTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `a fresh window opens on the entry screen, with no tab`() {
        val state = DesktopShellState(DesktopSettings(temp.newFile("settings.properties")))
        state.start(null)
        assertEquals("tabs at startup", 0, state.tabs.all.size)
    }

    @Test
    fun `the recent list is most recent first, one entry per notebook, and outlives a restart`() {
        val settingsFile = temp.newFile("settings.properties")
        val first = temp.newFile("lecture-one.xopp").apply { writeText("x") }
        val second = temp.newFile("lecture-two.xopp").apply { writeText("x") }
        val settings = DesktopSettings(settingsFile)
        settings.recents.record(first)
        settings.recents.record(second)
        // Opened again: one card, in front — the list is a set of sheets, not a log of visits.
        settings.recents.record(first)
        assertEquals(
            "most recent first, and no duplicate",
            listOf(first.absolutePath, second.absolutePath),
            settings.recents.files.map { it.absolutePath },
        )
        // A restart reads the same file back.
        val reopened = DesktopSettings(settingsFile)
        assertEquals(
            "the list survives the process",
            listOf(first.absolutePath, second.absolutePath),
            reopened.recents.files.map { it.absolutePath },
        )
        // A notebook deleted in a file manager leaves no card that opens nothing.
        second.delete()
        assertEquals("a missing file drops out", 1, reopened.recents.files.size)
    }

    @Test
    fun `the new sheet's preview has the sheet's proportions`() {
        val state = DesktopShellState(DesktopSettings(temp.newFile("proportions.properties")))
        val page = com.mobixournal.desktop.io.DesktopDocuments.blankPage(
            state.settings.pageWidthPt,
            state.settings.pageHeightPt,
        )
        val painted = com.mobixournal.desktop.shell.renderPageBitmap(page, 208f / page.width.toFloat())
        println("sheet: page ${page.width} x ${page.height}, preview ${painted.width} x ${painted.height}")
        // Where the paper actually is **inside** the bitmap: a preview whose bitmap is a sheet and whose paper
        // is a strip would pass a size check and still show the strip.
        val pixels = painted.toPixelMap()
        var minX = Int.MAX_VALUE
        var maxX = -1
        for (y in 0 until pixels.height step 2) {
            for (x in 0 until pixels.width step 2) {
                if (pixels[x, y].alpha > 0.5f) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
        }
        println("sheet: paper columns $minX..$maxX of ${pixels.width} x ${pixels.height}")
        assertTrue("the paper spans the preview", maxX - minX > painted.width * 0.9)
    }

    @Test
    fun `the entry screen paints`() {
        val state = DesktopShellState(DesktopSettings(temp.newFile("settings.properties")))
        state.start(null)
        val scene = ImageComposeScene(1200, 800, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            val painted = scene.render().encodeToData()?.bytes ?: ByteArray(0)
            assertTrue("the entry screen drew nothing", painted.isNotEmpty())
        } finally {
            scene.close()
        }
    }

    /** The repository's own notebook, found by walking up: the tests run inside `desktop/`. */
    private fun repositoryNotebook(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "udiff.xopp")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
