package com.mobixournal.desktop

import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopOpens
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.openInBackground
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * An open that reads on the window's own thread stops the frame loop, and a stopped loop is a click that
 * looks ignored: `--diagnose` recorded gaps of 1 032-1 861 ms around an open whose paint was 1 ms.
 *
 * The claim here is the **return**: [DesktopShellState.openInBackground] comes back before its read, says
 * which file it is waiting for, and still ends with the tab. The loader is handed in so a read can be held
 * open long enough to tell the two apart — a real 6-page `.xopp` reads too fast to see.
 */
class DesktopOpenBackgroundTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `an open comes back before its read, and names the file while it waits`() {
        val notebook = repositoryNotebook() ?: return
        val state = DesktopShellState(DesktopSettings(temp.newFile("settings.properties")))
        val document = DocumentLoader.load(notebook)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val began = System.nanoTime()
            state.openInBackground(notebook, scope) { held ->
                Thread.sleep(400)
                document
            }
            val returned = (System.nanoTime() - began) / 1_000_000.0
            assertTrue("the call waited for the read ($returned ms)", returned < 200.0)
            assertEquals("the file named while it is in flight", notebook.name, DesktopOpens.opening)
            assertEquals("a tab before its document was read", 0, state.tabs.all.size)
            val deadline = System.nanoTime() + 10_000_000_000L
            while (state.tabs.all.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals("the tab after the read", 1, state.tabs.all.size)
            assertNull("the waiting sentence ends with the read", DesktopOpens.opening)
        } finally {
            scope.cancel()
            state.tabs.closeAll()
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
        println("udiff.xopp not in this checkout; the background open is not measured")
        return null
    }
}
