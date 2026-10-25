package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
        type(state, "Typed here")

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
        type(state, "   ")

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
        type(state, "first")
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
        type(state, "kept")

        state.selectTool(DesktopTool.PEN)

        assertEquals(listOf("kept"), texts(tab))
        assertNull(state.textBox.box)
    }

    // ---- A box that is already on the page: the tablet's "tap the words again to edit them" ----

    /** Place one styled box on the open page, the way a file from the tablet or Xournal++ arrives. */
    private fun placeOldWords(state: DesktopShellState, tab: DesktopTab): TextElement {
        val box = TextElement(
            font = "Sans Bold",
            size = 18.0,
            x = 120.0,
            y = 200.0,
            color = 0xFF0000FF.toInt(),
            content = "old words",
            id = "txt_1",
        )
        placeLivingElement(tab, box)
        return elements(tab).filterIsInstance<TextElement>().single()
    }

    @Test
    fun `a press on existing words re-opens them instead of stacking a box on top`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        val old = placeOldWords(state, tab)

        state.textBox.begin(tab, 130.0, 210.0, 595.0, 842.0, state.ink) { _, _ -> }

        val box = state.textBox.box!!
        assertEquals("the box under the press is the one opened", old, box.editing)
        assertEquals("with its words in it", "old words", box.text)
        assertEquals("and its own face", "Sans Bold", box.font)
        assertEquals(18.0, box.size, 1e-9)
        assertEquals(0xFF0000FF.toInt(), box.colour)
        assertEquals("the caret is at the end of them", "old words".length, box.typing.selection.end)
    }

    @Test
    fun `a press in the middle of a word keeps the caret where the field puts it`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        placeOldWords(state, tab)
        state.textBox.begin(tab, 130.0, 210.0, 595.0, 842.0, state.ink) { _, _ -> }

        // The field's own answer: the text plus a caret in the middle of it. A draft that kept only
        // the string would snap the caret back to the end on the next keystroke.
        state.textBox.change(TextFieldValue("old words", TextRange(3)))

        assertEquals("the caret is still where it was put", 3, state.textBox.box!!.typing.selection.end)
        state.textBox.commit(tab) { _, _ -> }
        assertEquals("old words", elements(tab).filterIsInstance<TextElement>().single().content)
    }

    @Test
    fun `retyping keeps the box's place, face and identity, as one undo step`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        placeOldWords(state, tab)

        state.textBox.begin(tab, 130.0, 210.0, 595.0, 842.0, state.ink) { _, _ -> }
        type(state, "new words")
        state.textBox.commit(tab) { _, _ -> }

        val boxes = elements(tab).filterIsInstance<TextElement>()
        assertEquals("still one box, not a new one added beside the old", 1, boxes.size)
        val box = boxes.single()
        assertEquals("new words", box.content)
        assertEquals("the face survives the retype", "Sans Bold", box.font)
        assertEquals(18.0, box.size, 1e-9)
        assertEquals("and the colour", 0xFF0000FF.toInt(), box.color)
        assertEquals("in the place it held", 120.0, box.x, 1e-9)
        assertEquals(200.0, box.y, 1e-9)
        assertEquals("its identity survives too, so a .mobix save keeps keying the same element", "txt_1", box.id)
        assertNotNull("the corrected box stays selected", tab.selection)

        assertTrue("one undo takes the retype back", tab.undo())
        assertEquals(
            "the words it had before",
            "old words",
            elements(tab).filterIsInstance<TextElement>().single().content,
        )
        assertTrue("and only the placement is left to undo", tab.undo())
        assertTrue("nothing of it remains", elements(tab).none { it is TextElement })
    }

    @Test
    fun `emptying a box deletes it, as one undo step`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        placeOldWords(state, tab)

        state.textBox.begin(tab, 130.0, 210.0, 595.0, 842.0, state.ink) { _, _ -> }
        type(state, "   ")
        state.textBox.commit(tab) { _, _ -> }

        assertTrue("the box is gone", elements(tab).none { it is TextElement })
        assertNull("and nothing is selected in its place", tab.selection)
        assertTrue("one undo brings it back", tab.undo())
        assertEquals(
            "old words",
            elements(tab).filterIsInstance<TextElement>().single().content,
        )
    }

    @Test
    fun `closing a box without typing in it records nothing`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        placeOldWords(state, tab)

        state.textBox.begin(tab, 130.0, 210.0, 595.0, 842.0, state.ink) { _, _ -> }
        state.textBox.commit(tab) { _, _ -> }

        assertEquals(1, elements(tab).count { it is TextElement })
        assertEquals("old words", texts(tab).single())
        assertTrue("the placement is the only step in the history", tab.undo())
        assertTrue("so the byte-identical reopen added none", elements(tab).none { it is TextElement })
    }

    @Test
    fun `the top-most of two boxes is the one a press picks`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        placeLivingElement(tab, TextElement("Sans", 18.0, 100.0, 100.0, 0, "under"))
        placeLivingElement(tab, TextElement("Sans", 18.0, 100.0, 100.0, 0, "over"))

        state.textBox.begin(tab, 110.0, 110.0, 595.0, 842.0, state.ink) { _, _ -> }

        assertEquals("the one drawn last", "over", state.textBox.box!!.text)
    }

    @Test
    fun `a press nowhere near a box still starts a new one`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        placeOldWords(state, tab)

        state.textBox.begin(tab, 380.0, 600.0, 595.0, 842.0, state.ink) { _, _ -> }

        val box = state.textBox.box!!
        assertNull("nothing is being retyped", box.editing)
        assertEquals("", box.text)
        assertEquals(380.0, box.x, 1e-9)
    }

    /** The Text slot is offered exactly because it acts: the strip's own rule. */
    @Test
    fun `the Text slot is on the strip and acts`() {
        assertTrue(DesktopTool.TEXT in DESKTOP_TOOLS_THAT_ACT)
        assertNotNull("so it has a slot", groupOf(DesktopTool.TEXT))
        assertFalse("text is not ink, so the pen's packets must stand aside", DesktopTool.TEXT.inksByPackets())
    }

    /** Type [text] into the open box, the way the field hands it over (caret at the end). */
    private fun type(state: DesktopShellState, text: String) =
        state.textBox.change(TextFieldValue(text, TextRange(text.length)))

    private fun elements(tab: DesktopTab) = tab.document.page(tab.page).layers.flatMap { it.elements }

    private fun texts(tab: DesktopTab): List<String> =
        elements(tab).filterIsInstance<TextElement>().map { it.content }
}
