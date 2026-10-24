package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.TextElement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Text tool's rules, without a window: where a press puts the box, what the box carries, what a blank
 * one does, and which key the shell may take while it is up.
 *
 * The gesture end of it (a real press through a scene, the field drawn in place) is
 * [DesktopTextSceneTest]'s; these are the answers a scene would only reach indirectly.
 */
class DesktopTextAuthoringTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("text.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    @Test
    fun `a press anchors a one-line box a third of the page wide, in the ink`() {
        val ink = DesktopInk(colour = 0xFF112233.toInt(), width = 1.5f)
        val box = textPlacement(595.28, 841.89, 120.0, 200.0, ink)

        assertEquals(TEXT_FONT, box.font)
        assertEquals("the pen's width, scaled", 15.0, box.size, 1e-9)
        assertEquals("the caret lands where the press did", 120.0, box.x, 1e-9)
        assertEquals(200.0, box.y, 1e-9)
        assertEquals(ink.colour, box.color)
        assertEquals("a fresh box types its own text", "", box.content)
    }

    @Test
    fun `the size follows the nib but stays readable`() {
        assertEquals(TEXT_MIN_SIZE_PT, textSizeFor(0.05f), 1e-9)
        assertEquals(TEXT_MAX_SIZE_PT, textSizeFor(40f), 1e-9)
        assertEquals(14.0, textSizeFor(1.4f), 1e-4)
    }

    @Test
    fun `a box near the edge is kept on the paper`() {
        val ink = DesktopInk(width = 1f)
        val box = textPlacement(595.28, 841.89, 590.0, 840.0, ink)

        assertTrue("the whole width of the box is on the page", box.x + textBoxWidth(595.28) <= 595.28 + 1e-9)
        assertTrue("and its line is too", box.y + box.size * TEXT_LINE_HEIGHT <= 841.89 + 1e-9)
        assertEquals(595.28 - textBoxWidth(595.28), box.x, 1e-9)
    }

    @Test
    fun `a draft knows whether it is inside itself, blank, and what it writes`() {
        val draft = TextBoxDraft(
            tabKey = "a.mobix",
            page = 0,
            pageW = 600.0,
            pageH = 800.0,
            x = 100.0,
            y = 200.0,
            size = 12.0,
            colour = 0xFF000000.toInt(),
        )
        assertTrue(draft.blank)
        assertTrue("the press point is inside the box", draft.contains(120.0, 205.0))
        assertFalse("a point past the box's width is not", draft.contains(100.0 + draft.width + 1.0, 205.0))
        assertEquals(1, draft.lines())
        assertEquals("two lines ask for two", 2, draft.withText("a\nb").lines())

        val written: TextElement = draft.withText("hello\nworld").element()
        assertEquals("hello\nworld", written.content)
        assertEquals(TEXT_FONT, written.font)
        assertEquals(12.0, written.size, 1e-9)
        assertFalse(draft.withText("hello").blank)
        assertTrue("whitespace alone is nothing to write", draft.withText("  \n ").blank)
    }

    @Test
    fun `only Esc belongs to the shell while a box is up`() {
        assertEquals(TextBoxKey.COMMIT, textBoxCommand(Key.Escape))
        for (key in listOf(Key.Spacebar, Key.B, Key.H, Key.Delete, Key.One, Key.Enter)) {
            assertNull("$key is a character, not a command", textBoxCommand(key))
        }
    }

    @Test
    fun `a committed box is one text element and one undo step`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        state.textBox.begin(tab, 140.0, 220.0, 595.0, 842.0, state.ink) { _, _ -> }
        state.textBox.change("Typed here")

        val before = elements(tab).size
        state.textBox.commit(tab) { _, _ -> }

        val written = elements(tab).filterIsInstance<TextElement>().single()
        assertEquals("Typed here", written.content)
        assertEquals(140.0, written.x, 1e-9)
        assertEquals(state.ink.colour, written.color)
        assertEquals(before + 1, elements(tab).size)
        assertNull("the field closes with the commit", state.textBox.box)
        assertTrue("the commit is one undo step", tab.canUndo)
        assertNotNull("what was just typed stays selected", tab.selection)

        assertTrue(tab.undo())
        assertTrue("undo takes the whole box back", elements(tab).none { it is TextElement })
    }

    @Test
    fun `a blank box commits nothing at all`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        state.textBox.begin(tab, 140.0, 220.0, 595.0, 842.0, state.ink) { _, _ -> }
        state.textBox.change("   ")

        val before = elements(tab).size
        state.textBox.commit(tab) { _, _ -> }

        assertEquals("no element", before, elements(tab).size)
        assertFalse("and no undo step", tab.canUndo)
        assertNull(state.textBox.box)
    }

    @Test
    fun `a second press commits the first box before opening the next`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        state.textBox.begin(tab, 100.0, 100.0, 595.0, 842.0, state.ink) { _, _ -> }
        state.textBox.change("first")
        state.textBox.begin(tab, 300.0, 400.0, 595.0, 842.0, state.ink) { _, _ -> }

        assertEquals("the first box was written", listOf("first"), texts(tab))
        assertEquals("and the second is open", 300.0, state.textBox.box!!.x, 1e-9)
        assertEquals("", state.textBox.box!!.text)
    }

    @Test
    fun `leaving the Text tool commits, it does not drop`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        state.selectTool(DesktopTool.TEXT)
        state.textBox.begin(tab, 100.0, 100.0, 595.0, 842.0, state.ink) { _, _ -> }
        state.textBox.change("kept")

        state.selectTool(DesktopTool.PEN)

        assertEquals(listOf("kept"), texts(tab))
        assertNull(state.textBox.box)
    }

    /** The Text slot is offered exactly because it acts: the strip's own rule. */
    @Test
    fun `the Text slot is on the strip and acts`() {
        assertTrue(DesktopTool.TEXT in DESKTOP_TOOLS_THAT_ACT)
        assertNotNull("so it has a slot", groupOf(DesktopTool.TEXT))
        assertFalse("text is not ink, so the pen's packets must stand aside", DesktopTool.TEXT.inksByPackets())
    }

    private fun elements(tab: DesktopTab) = tab.document.page(tab.page).layers.flatMap { it.elements }

    private fun texts(tab: DesktopTab): List<String> =
        elements(tab).filterIsInstance<TextElement>().map { it.content }
}
