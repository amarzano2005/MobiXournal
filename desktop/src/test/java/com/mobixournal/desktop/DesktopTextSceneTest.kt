package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TextElement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Text slot, end to end through a headless scene: a real press on the sheet opens a box **where the
 * press landed**, the field is painted over the page, the typing reaches the draft, and the commit writes
 * exactly one `TextElement` as one undo step.
 *
 * The placement arithmetic is [DesktopTextAuthoringTest]'s; what only a scene can prove is that Compose's
 * own dispatch reaches the canvas with the Text tool, that the field is on screen (the frame changes
 * without a character being typed), and that the same press does **not** start a stroke.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopTextSceneTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A view narrower than the page, so the sheet is not centred and pointer = page point. */
    private val viewW = 500
    private val viewH = 700

    private fun notebook(): File = temp.newFile("text-scene.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    private class Fixture(val state: DesktopShellState, val tab: DesktopTab, val scene: ImageComposeScene)

    private fun fixture(): Fixture {
        val state = DesktopShellState()
        val tab = state.tabs.open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = DesktopTool.TEXT,
                ink = state.ink,
                text = TextBoxUi(
                    box = state.textBox.box,
                    onPress = { x, y, pageW, pageH ->
                        state.textBox.begin(tab, x, y, pageW, pageH, state.ink) { _, _ -> }
                    },
                    onChange = { typed -> state.textBox.change(typed) },
                ),
                onViewport = {},
                onFit = {},
            )
        }
        scene.render()
        Snapshot.sendApplyNotifications()
        return Fixture(state, tab, scene)
    }

    @Test
    fun `a press opens a box where it landed, drawn over the page`() {
        val fixture = fixture()
        try {
            val before = frame(fixture.scene)
            tap(fixture.scene, 150f, 200f)

            val box = fixture.state.textBox.box
            assertNotNull("the Text slot opens a box", box)
            assertEquals("the box's caret is the press point", 150.0, box!!.x, 1.0)
            assertEquals(200.0, box.y, 1.0)
            assertEquals("", box.text)
            assertEquals("nothing was written yet", emptyList<String>(), texts(fixture.tab))
            assertFalse("and nothing is in the history", fixture.tab.canUndo)
            assertNotEquals(
                "the field is painted over the sheet, or the press would be invisible",
                before.toList(),
                frame(fixture.scene).toList(),
            )

            // A drag with the Text slot types nothing and inks nothing: it is a click, not a stroke.
            val strokes = strokes(fixture.tab)
            drag(fixture.scene, 300f, 320f, 380f, 360f)
            assertEquals("a Text drag leaves no ink", strokes, strokes(fixture.tab))
            assertEquals("it moves the caret instead", 300.0, fixture.state.textBox.box!!.x, 1.0)
        } finally {
            fixture.scene.close()
            fixture.tab.document.close()
        }
    }

    @Test
    fun `typing then committing writes one element and one undo step`() {
        val fixture = fixture()
        try {
            tap(fixture.scene, 150f, 200f)
            type(fixture.state, "Hello page")
            val typed = frame(fixture.scene)

            fixture.state.textBox.commit(fixture.tab) { _, _ -> }

            val written = elements(fixture.tab).filterIsInstance<TextElement>().single()
            assertEquals("Hello page", written.content)
            assertEquals(150.0, written.x, 1.0)
            assertEquals("the ink's colour, as the strip is set", fixture.state.ink.colour, written.color)
            assertNull("the field closes on its commit", fixture.state.textBox.box)
            assertTrue("the commit is one undo step", fixture.tab.canUndo)
            assertNotNull("and the box stays selected", fixture.tab.selection)
            assertNotEquals(
                "the page now paints the text",
                typed.toList(),
                frame(fixture.scene).toList(),
            )

            assertTrue(fixture.tab.undo())
            assertTrue("undo takes the whole box back", elements(fixture.tab).none { it is TextElement })
        } finally {
            fixture.scene.close()
            fixture.tab.document.close()
        }
    }

    @Test
    fun `a second press commits the first box and opens the next`() {
        val fixture = fixture()
        try {
            tap(fixture.scene, 140f, 180f)
            type(fixture.state, "first")
            tap(fixture.scene, 320f, 420f)

            assertEquals("the first box was written", listOf("first"), texts(fixture.tab))
            val second = fixture.state.textBox.box
            assertNotNull("the second box is open", second)
            assertEquals(320.0, second!!.x, 1.0)
            assertEquals("", second.text)
            assertTrue("and it is a second step of its own", fixture.tab.canUndo)
        } finally {
            fixture.scene.close()
            fixture.tab.document.close()
        }
    }

    /**
     * The tablet's rule, through the same canvas a click goes through: words already on the page are
     * **edited**, not covered by a second box. This is the whole path a press to correct text takes —
     * Compose's dispatch, the padded hit test, the field opened on that element, one in-place write.
     */
    @Test
    fun `a press on words already on the page re-opens them, not a new box`() {
        val fixture = fixture()
        try {
            placeLivingElement(
                fixture.tab,
                TextElement("Sans Bold", 20.0, 120.0, 200.0, 0xFF000000.toInt(), "existing"),
            )
            val before = frame(fixture.scene)
            tap(fixture.scene, 140f, 208f)

            val box = fixture.state.textBox.box
            assertNotNull("the field opens on the words", box)
            assertEquals("with the box's own words in it", "existing", box!!.text)
            assertNotNull("retyping them rather than starting a new box", box.editing)
            assertEquals("the box's own face", "Sans Bold", box.font)
            assertEquals("and nothing on the page changed yet", listOf("existing"), texts(fixture.tab))
            assertNotEquals(
                "the field is painted over the sheet",
                before.toList(),
                frame(fixture.scene).toList(),
            )

            type(fixture.state, "corrected")
            fixture.state.textBox.commit(fixture.tab) { _, _ -> }
            assertEquals("one box, corrected where it stood", listOf("corrected"), texts(fixture.tab))
        } finally {
            fixture.scene.close()
            fixture.tab.document.close()
        }
    }

    /** Type [text] into the open box, the way the field hands it over (caret at the end). */
    private fun type(state: DesktopShellState, text: String) =
        state.textBox.change(TextFieldValue(text, TextRange(text.length)))

    private fun elements(tab: DesktopTab) = tab.document.page(tab.page).layers.flatMap { it.elements }

    private fun texts(tab: DesktopTab): List<String> =
        elements(tab).filterIsInstance<TextElement>().map { it.content }

    private fun strokes(tab: DesktopTab): Int = elements(tab).count { it is Stroke }

    private fun frame(scene: ImageComposeScene): ByteArray {
        Snapshot.sendApplyNotifications()
        return requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
    }

    private fun drag(scene: ImageComposeScene, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        mouse(scene, PointerEventType.Press, fromX, fromY, 2_000L)
        mouse(scene, PointerEventType.Move, (fromX + toX) / 2f, (fromY + toY) / 2f, 2_016L)
        mouse(scene, PointerEventType.Move, toX, toY, 2_032L)
        mouse(scene, PointerEventType.Release, toX, toY, 2_048L, pressed = false)
    }

    private fun tap(scene: ImageComposeScene, x: Float, y: Float) {
        mouse(scene, PointerEventType.Press, x, y, 1_000L)
        mouse(scene, PointerEventType.Release, x, y, 1_032L, pressed = false)
    }

    private fun mouse(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        time: Long,
        pressed: Boolean = true,
    ) {
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
            timeMillis = time,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}
