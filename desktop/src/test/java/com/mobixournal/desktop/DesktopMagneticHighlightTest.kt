package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.PageInkGesture
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerPhase
import com.mobixournal.render.MagneticHighlighter
import com.mobixournal.render.PointerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **magnetic highlighter**, driven through the real gesture: a horizontal drag over a PDF
 * line commits that line's band as ordinary ink, one undo step for the whole stroke.
 *
 * The cases that must **not** snap are the point of the rules: a steep drag (marking a margin), a drag
 * over no text, and a page with no text layer all commit the freehand path unchanged — snapping is a
 * convenience, never a correction the user did not ask for.
 */
class DesktopMagneticHighlightTest {

    @get:Rule val temp = TemporaryFolder()

    private data class Fixture(val tab: DesktopTab, val gesture: PageInkGesture, val bands: androidx.compose.runtime.MutableState<List<MagneticHighlighter.Band>>)

    private fun fixture(hasIndex: Boolean = true): Fixture {
        val tab = DesktopPdfFixture.tab(temp.root)
        if (!hasIndex) tab.pdfIndex = null
        val draft = mutableStateListOf<InkSample>()
        val bands = mutableStateOf<List<MagneticHighlighter.Band>>(emptyList())
        val gesture = PageInkGesture(
            tab = tab,
            tool = DesktopTool.HIGHLIGHTER,
            ink = DesktopInk(width = 12f),
            view = { tab.viewport },
            pageW = DesktopPdfFixture.PAGE_WIDTH_PT.toFloat(),
            pageH = DesktopPdfFixture.PAGE_HEIGHT_PT.toFloat(),
            density = 1f,
            draft = draft,
            onEdited = {},
            startOnDown = true,
            bands = bands,
        )
        return Fixture(tab, gesture, bands)
    }

    private fun pointer(x: Float, y: Float, phase: InkPointerPhase, timeMs: Long) = InkPointer(
        id = 1,
        x = x,
        y = y,
        pressure = 1f,
        timeMs = timeMs,
        tool = PointerKind.STYLUS,
        phase = phase,
        buttons = InkPointer.PRIMARY,
    )

    private fun drag(fixture: Fixture, from: Pair<Float, Float>, to: Pair<Float, Float>) {
        val gesture = fixture.gesture
        var time = 1_000L
        gesture.down(pointer(from.first, from.second, InkPointerPhase.DOWN, time))
        time += 16
        gesture.move(pointer((from.first + to.first) / 2, (from.second + to.second) / 2, InkPointerPhase.MOVE, time))
        time += 16
        gesture.move(pointer(to.first, to.second, InkPointerPhase.MOVE, time))
        time += 16
        gesture.up(pointer(to.first, to.second, InkPointerPhase.UP, time))
    }

    private fun strokes(tab: DesktopTab) =
        tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    @Test
    fun `a horizontal drag over a line commits that line's band`() {
        val fixture = fixture()
        try {
            drag(fixture, 45f to 106f, 185f to 106f)
            val stroke = strokes(fixture.tab).single()
            assertEquals(Tool.HIGHLIGHTER, stroke.tool)
            assertTrue("a band is one constant width", stroke.uniformWidth)
            assertEquals("the band is two points", 2, stroke.points.size)
            assertEquals(
                "the band's width is the line's own height plus the documented air",
                (112.0 - 100.0) + 2 * MagneticHighlighter.BAND_PAD_PT,
                stroke.points.first().width,
                1e-6,
            )
            assertEquals("one edit, one undo step", 1, fixture.tab.document.edits)
            assertTrue(fixture.tab.canUndo)
            assertTrue("the preview is cleared at the lift", fixture.bands.value.isEmpty())
            // The band sits on the line it crossed, not on the ink's own path.
            assertEquals(stroke.points.first().y, stroke.points.last().y, 0.0)
            assertEquals(106.0, stroke.points.first().y, 1e-6)
        } finally {
            fixture.tab.document.close()
        }
    }

    @Test
    fun `the lift commits the bands it previewed, and one undo takes them back`() {
        val fixture = fixture()
        try {
            drag(fixture, 45f to 106f, 185f to 106f)
            assertEquals(1, strokes(fixture.tab).size)
            assertTrue(fixture.tab.undo())
            assertEquals("undo removes the band in one step", 0, strokes(fixture.tab).size)
        } finally {
            fixture.tab.document.close()
        }
    }

    @Test
    fun `a steep drag keeps its freehand shape`() {
        val fixture = fixture()
        try {
            drag(fixture, 100f to 40f, 110f to 160f)
            val stroke = strokes(fixture.tab).single()
            assertTrue("a vertical mark is not a highlight", stroke.points.size > 2)
            assertTrue("the preview never showed a band", fixture.bands.value.isEmpty())
        } finally {
            fixture.tab.document.close()
        }
    }

    @Test
    fun `a drag over no text stays freehand`() {
        val fixture = fixture()
        try {
            drag(fixture, 45f to 400f, 185f to 402f)
            assertTrue(strokes(fixture.tab).single().points.size > 2)
        } finally {
            fixture.tab.document.close()
        }
    }

    @Test
    fun `a page with no text layer behaves exactly as before`() {
        val fixture = fixture(hasIndex = false)
        try {
            drag(fixture, 45f to 106f, 185f to 106f)
            val stroke = strokes(fixture.tab).single()
            assertTrue("no index, no snapping", stroke.points.size > 2)
            assertFalse(fixture.tab.pdfIndex != null)
        } finally {
            fixture.tab.document.close()
        }
    }
}
