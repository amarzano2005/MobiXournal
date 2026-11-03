package com.mobixournal.desktop

import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShellState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Which half of an archive open blocks: the **read**, or the tab that follows it.
 *
 * The window's own watchdog recorded gaps of ~1-2 s around an open, and a gap whose paint is 1 ms is a frame
 * loop that is not running. Timing the two halves separately says which one has to leave the frame thread —
 * the read already does (`DesktopShellState.openInBackground`), so if the open is still the gap the cost is in
 * what `DesktopTabStore.open` does with the loaded document.
 *
 * Skipped when `build-logs/heavy.mobix` is absent (`FixtureWriterTest` writes it).
 */
class DesktopArchiveOpenCostTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `an archive notebook's open is either the read or the tab`() {
        val heavy = File("../build-logs/heavy.mobix").takeIf { it.isFile } ?: run {
            println("heavy.mobix is not generated; the archive open is not measured")
            return
        }
        val reads = (0 until 3).map {
            val begin = System.nanoTime()
            DocumentLoader.load(heavy).close()
            (System.nanoTime() - begin) / 1_000_000.0
        }
        val opens = (0 until 3).map {
            val state = DesktopShellState(DesktopSettings(temp.newFile("settings-$it.properties")))
            val begin = System.nanoTime()
            state.open(heavy)
            val elapsed = (System.nanoTime() - begin) / 1_000_000.0
            state.tabs.closeAll()
            elapsed
        }
        println("archive open: read ${reads.min()} ms, read+tab ${opens.min()} ms (best of 3)")
    }
}
