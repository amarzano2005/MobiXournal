package com.mobixournal.desktop

import com.mobixournal.format.FontDescription
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.render.ElementRef
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The text box as a thing the **inspector lane** edits: which family it is, the line the lane's header
 * prints, the five controls' pure half (family/bold/italic) and the one-way commit.
 *
 * The controls' pixels belong to the lane's render tests; what these pin is that a restyle writes a
 * `TextElement` the file already understands, keeps everything the change did not ask about — the words,
 * the place, the identity, the colour — and records exactly one undo step.
 */
class DesktopTextStyleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("style.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    private fun box(font: String = "Sans") = TextElement(
        font = font,
        size = 18.0,
        x = 120.0,
        y = 200.0,
        color = 0xFF112233.toInt(),
        content = "first line\nsecond line",
        id = "txt_style",
    )

    @Test
    fun `changing the family keeps the styles the box already carries`() {
        val bold = box("Sans Bold")
        assertEquals("Serif Bold", bold.withFamily("Serif").font)
        assertEquals("Monospace Bold Italic", box("Sans Bold Italic").withFamily("Monospace").font)
        assertEquals("the words are not the family's business", "first line\nsecond line", bold.withFamily("Serif").content)
        assertEquals(18.0, bold.withFamily("Serif").size, 1e-9)
        assertEquals(0xFF112233.toInt(), bold.withFamily("Serif").color)
        assertEquals("txt_style", bold.withFamily("Serif").id)
    }

    @Test
    fun `bold and italic toggle their own token and nothing else`() {
        assertEquals("Sans Bold", box().withBold(true).font)
        assertEquals("Sans Bold Italic", box().withBold(true).withItalic(true).font)
        assertEquals("Sans Italic", box("Sans Bold").withBold(false).withItalic(true).font)
        assertEquals("Sans", box("Sans Bold Italic").withBold(false).withItalic(false).font)
        val both = box("Serif").withBold(true).withItalic(true)
        assertEquals("the family survives both", "Serif", FontDescription.parse(both.font).family)
    }

    @Test
    fun `a text box is a family the window edits, and ink is not`() {
        assertEquals(LivingFamily.TEXT, LivingFamily.of(box()))
        assertTrue(hasInspector(box()))
        assertFalse(
            "a stroke has no values the lane could set",
            hasInspector(
                Stroke(
                    tool = Tool.PEN,
                    color = 0,
                    capStyle = null,
                    points = listOf(StrokePoint(1.0, 1.0, 1.0)),
                    uniformWidth = true,
                ),
            ),
        )
    }

    @Test
    fun `the header names the box by its own words and size`() {
        val summary = livingSummary(LivingTarget(LivingFamily.TEXT, ElementRef(0, 0), box("Sans Bold")))
        assertTrue("the first line is what tells two boxes apart: $summary", summary.contains("first line"))
        assertTrue("and its size is the number the panel edits: $summary", summary.contains("18 pt"))
    }

    @Test
    fun `an empty box still has a summary`() {
        val empty = box().copy(content = "  \n ")
        assertEquals("Sans · 18 pt", livingSummary(LivingTarget(LivingFamily.TEXT, ElementRef(0, 0), empty)))
    }

    @Test
    fun `the selection's pencil opens the lane on a text box, and one control is one undo step`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        placeLivingElement(tab, box())

        state.runSelection(SelectionAction.EDIT)

        val opened = state.editingLiving
        assertNotNull("the lane opens on a selected text box, as on a table or a plot", opened)
        assertEquals("Text", livingTarget(tab, opened!!)!!.family.title)
        assertNotNull("and it stays attached while the selection names it", attachedLivingEditor(tab, opened))

        val current = textElement(tab)
        state.commitLiving(current.withBold(true))

        assertEquals("one box, its face changed", "Sans Bold", textElement(tab).font)
        assertEquals("the words are untouched", "first line\nsecond line", textElement(tab).content)
        assertTrue("one undo takes the restyle back", tab.undo())
        assertEquals("Sans", textElement(tab).font)
    }

    @Test
    fun `setting the value a box already has records nothing`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        placeLivingElement(tab, box())
        state.runSelection(SelectionAction.EDIT)
        val current = textElement(tab)

        val wrote = replaceLiving(tab, state.editingLiving!!, current.copy(font = current.font))

        assertFalse("the same element is not an edit", wrote)
        assertTrue("so the placement is the only step left to undo", tab.undo())
        assertTrue(
            "and the box is what the undo took",
            tab.document.page(tab.page).layers.flatMap { it.elements }.none { it is TextElement },
        )
    }

    private fun textElement(tab: DesktopTab): TextElement =
        tab.document.page(tab.page).layers.flatMap { it.elements }.filterIsInstance<TextElement>().single()
}
