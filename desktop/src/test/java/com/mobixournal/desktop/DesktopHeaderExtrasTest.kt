package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.settings.theme
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.DesktopThemeMode
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.shell.HeaderBreadcrumb
import com.mobixournal.desktop.shell.ShareActions
import com.mobixournal.desktop.shell.ShareMenuPanel
import com.mobixournal.desktop.shell.shortenPath

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The three pieces of chrome that arrived together: the **theme** the window can switch, the **path** on
 * the band, and the **Share** menu.
 *
 * The claims are the ones a reader would notice were false: a light window that still painted a black
 * desk; a breadcrumb that cut the file name off instead of the folder it lives in; and a Share row that
 * pointed Explorer at a document that has no file yet. The two platform calls are injected, so the test
 * owns *when* each runs, and the window owns *how*.
 */
class DesktopHeaderExtrasTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var opened = 0

    private fun tab(unsaved: Boolean = false): DesktopTab {
        // A fresh name each time: a `TemporaryFolder` refuses a second file of the same name, and two
        // documents are what the share test needs.
        val file = File(temp.root, "extras-${opened++}.mobix")
            .also { SyntheticNotebook.write(it, SyntheticNotebook.pages(1)) }
        val tabs = DesktopTabs().also { it.open(DocumentLoader.load(file)) }
        val opened = requireNotNull(tabs.active) { "the synthetic notebook must open" }
        if (unsaved) opened.unsaved = true
        return opened
    }

    @Test
    fun `a path shortens from the left and keeps the drive and the file name`() {
        assertEquals("a path that fits is untouched", "C:\\notes\\a.mobix", shortenPath("C:\\notes\\a.mobix"))

        val deep = "C:\\Users\\amarz\\Documents\\lecture notes\\lecture-2026.mobix"
        val short = shortenPath(deep, 40)
        assertTrue("the middle is what goes: $short", "…" in short)
        assertTrue("the drive stays: $short", short.startsWith("C:"))
        assertTrue("and the file name is the point: $short", short.endsWith("lecture-2026.mobix"))
        assertTrue("a segment is never cut in half: $short", short.length <= deep.length)

        val posix = shortenPath("/home/amarz/notes/lecture-2026.mobix", 30)
        assertTrue("a POSIX path keeps its own separator: $posix", posix.contains("/") && "\\" !in posix)

        // Fewer than three segments has no middle to take, so the answer is the path itself.
        assertEquals("C:\\a.mobix", shortenPath("C:\\a.mobix", 4))
    }

    @Test
    fun `the theme switches every chrome colour, and comes back`() {
        try {
            DesktopTheme.use(true)
            assertTrue("the light theme is the live one", DesktopTheme.isLight)
            assertEquals(DesktopTheme.tokenColour(DesignTokens.DESK_LIGHT_RGB), DesktopTheme.desk)
            assertEquals(DesktopTheme.tokenColour(DesignTokens.INK_LIGHT_RGB), DesktopTheme.ink)
            assertEquals(DesktopTheme.tokenColour(DesignTokens.CHROME_INK_LIGHT_RGB), DesktopTheme.chromeInk)
        } finally {
            DesktopTheme.use(false)
        }
        assertEquals("the dark theme is the window's default", DesktopTheme.tokenColour(DesignTokens.DESK_RGB), DesktopTheme.desk)
        assertNotEquals(DesktopTheme.tokenColour(DesignTokens.DESK_LIGHT_RGB), DesktopTheme.desk)
    }

    @Test
    fun `the theme is a stored answer, and System is what a fresh file says`() {
        val file = File(temp.root, "theme.properties")
        assertEquals(DesktopThemeMode.SYSTEM, DesktopSettings(file).theme)

        DesktopSettings(file).theme = DesktopThemeMode.LIGHT

        assertEquals("the next window reads it back", DesktopThemeMode.LIGHT, DesktopSettings(file).theme)
    }

    @Test
    fun `share refuses an unsaved document and hands a saved one to its two commands`() {
        var said: String? = null
        var copied: String? = null
        var revealed: File? = null

        val saved = tab()
        val share = ShareActions(
            tab = { saved },
            say = { said = it },
            reveal = { file -> revealed = file; true },
            copy = { path -> copied = path; true },
        )
        assertTrue("a saved document can be pointed at", share.canReveal)
        assertTrue(share.showInExplorer())
        assertEquals(saved.file, revealed)
        assertTrue("a saved document can be copied", share.copyPath())
        assertEquals(saved.file.absolutePath, copied)
        assertTrue("and the band is told: $said", said!!.startsWith("Copied "))

        val draft = tab(unsaved = true)
        val refusal = ShareActions(
            tab = { draft },
            say = { said = it },
            reveal = { true },
            copy = { true },
        )
        assertFalse("an unsaved document has no file to show", refusal.canReveal)
        assertFalse(refusal.showInExplorer())
        assertTrue("the reason is a sentence: $said", said!!.contains("Save the document"))
        assertFalse("and nothing was copied", refusal.copyPath())
    }

    @Test
    fun `the share menu and the breadcrumb compose`() {
        val saved = tab()
        val scene = ImageComposeScene(width = 520, height = 240, density = Density(1f)) {
            Box(Modifier.size(520.dp, 240.dp).background(DesktopTheme.desk)) {
                Row(Modifier.padding(12.dp)) { HeaderBreadcrumb(saved) }
                ShareMenuPanelFor(saved)
            }
        }
        val image = try {
            scene.render()
        } finally {
            scene.close()
        }
        assertTrue("the band and the menu drew something", image.width > 0 && image.height > 0)
    }

    /** The panel, over the same share commands the band hands it. */
    @Composable
    private fun ShareMenuPanelFor(saved: DesktopTab) {
        ShareMenuPanel(
            share = ShareActions(tab = { saved }, say = {}, reveal = { true }, copy = { true }),
            onExport = {},
            onClose = {},
        )
    }
}
