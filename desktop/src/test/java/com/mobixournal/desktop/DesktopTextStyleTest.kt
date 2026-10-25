package com.mobixournal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import org.junit.Assert.assertNull
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

    /**
     * The lane's numbers are written and read in one notation, whatever locale the window runs in.
     *
     * A `"%.1f".format` used the default locale, so an Italian machine showed `26,0` in the size field and
     * the field's own parse then refused the value it had just printed — pressing `Enter` wrote nothing.
     * A whole size also printed as `26.0`, which is not a size anyone typed.
     */
    @Test
    fun `the lane's numbers print and parse in one notation`() {
        assertEquals("a whole size has no decimal", "26", formatNumber(26.000000000000004))
        assertEquals("and a fraction keeps one", "1.5", formatNumber(1.54))
        assertEquals("a full stop separates the fraction", "18.5", formatNumber(18.5))
        assertEquals("a comma is read too, for an Italian keyboard", 26.5, parseNumber("26,5")!!, 1e-9)
        assertEquals("and so is a full stop", 26.5, parseNumber("26.5")!!, 1e-9)
        assertEquals("as is a padded entry", 8.0, parseNumber(" 8 ")!!, 1e-9)
        assertNull("a word is not a number", parseNumber("large"))
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

    @Test
    fun `the lane's answer reaches the box that is being typed`() {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        state.textBox.begin(tab, 140.0, 220.0, 595.0, 842.0, state.ink) { _, _ -> }
        state.textBox.change(TextFieldValue("styled", TextRange(6)))

        // What the panel's controls hand back while the field is up: a face, a size and an ink.
        state.textBox.restyle(
            TextElement("Serif Bold Italic", 24.0, 0.0, 0.0, 0xFF00FF00.toInt(), "", id = "ignored"),
        )
        assertEquals("the field follows the restyle while typing", "Serif Bold Italic", state.textBox.box!!.font)
        assertEquals(24.0, state.textBox.box!!.size, 1e-9)

        state.textBox.commit(tab) { _, _ -> }

        val written = textElement(tab)
        assertEquals("the words are what was typed", "styled", written.content)
        assertEquals("in the face the lane set", "Serif Bold Italic", written.font)
        assertEquals("at the size the lane set", 24.0, written.size, 1e-9)
        assertEquals("in the colour the lane set", 0xFF00FF00.toInt(), written.color)
        assertEquals("and where the press put it, not where the style element claimed", 140.0, written.x, 1e-9)
    }

    // --- the controls' own answer ----------------------------------------------------------------

    /** One `ChoiceRow` as the lane composes it, with a tag captured as a **plain** value. */
    @Composable
    private fun Probe(tag: String, onCall: (String) -> Unit) {
        Box(Modifier.width(300.dp)) {
            ChoiceRow(options = listOf("Sans", "Serif"), picked = "Sans") { choice -> onCall("$tag:$choice") }
        }
    }

    /**
     * A chip answers with the callback it has **now**.
     *
     * A `pointerInput` keeps the lambda it was built with, so a chip composed while the box said one thing
     * went on reporting that box after the lane had moved on — the bug the user sees as "the buttons don't
     * do what I clicked": a family picked after a colour, or a colour after a size, was written into the
     * element as it had been before the previous change.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a chip answers with the answer it has now, not the one it was built with`() {
        var tag by mutableStateOf("first")
        val calls = mutableListOf<String>()
        val scene = ImageComposeScene(300, 40, Density(1f)) { Probe(tag) { call -> calls += call } }
        try {
            scene.render()
            tag = "second"
            Snapshot.sendApplyNotifications()
            scene.render()

            click(scene, 220f, 16f)

            assertEquals("the chip used the callback the lane has now", listOf("second:Serif"), calls)
        } finally {
            scene.close()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun click(scene: ImageComposeScene, x: Float, y: Float) {
        for ((type, pressed) in listOf(PointerEventType.Press to true, PointerEventType.Release to false)) {
            scene.sendPointerEvent(
                eventType = type,
                pointers = listOf(
                    ComposeScenePointer(
                        id = PointerId(1),
                        position = Offset(x, y),
                        pressed = pressed,
                        type = PointerType.Mouse,
                        pressure = 1f,
                    ),
                ),
                buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
                timeMillis = if (pressed) 1_000L else 1_032L,
            )
            Snapshot.sendApplyNotifications()
            scene.render()
        }
    }

    private fun textElement(tab: DesktopTab): TextElement =
        tab.document.page(tab.page).layers.flatMap { it.elements }.filterIsInstance<TextElement>().single()
}
