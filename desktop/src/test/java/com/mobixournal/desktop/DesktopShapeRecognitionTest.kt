package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageInkGesture
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.FigureChoices

import androidx.compose.runtime.mutableStateListOf
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerPhase
import com.mobixournal.render.ParametricShapes
import com.mobixournal.render.PointerKind
import com.mobixournal.render.ShapeRecognition
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Shape recognition on desktop: freehand strokes snap to geometric primitives on lift when
 * [DesktopSettings.recognizeShapes] is enabled, matching mobile behavior.
 */
class DesktopShapeRecognitionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("recognition.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
    }

    private fun fixture(recognizeShapes: Boolean): Pair<DesktopTab, PageInkGesture> {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val page = tab.document.page(0)
        val draft = mutableStateListOf<InkSample>()
        val gesture = PageInkGesture(
            tab = tab,
            tool = DesktopTool.PEN,
            ink = DesktopInk(width = 4f),
            view = { tab.viewport },
            pageW = page.width.toFloat(),
            pageH = page.height.toFloat(),
            density = 1f,
            draft = draft,
            onEdited = {},
            startOnDown = true,
            recognition = if (recognizeShapes) ShapeRecognition.ON_LIFT else ShapeRecognition.OFF,
        )
        return tab to gesture
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

    private fun drawCorners(gesture: PageInkGesture, corners: List<Pair<Float, Float>>) {
        var time = 1_000L
        gesture.down(pointer(corners[0].first, corners[0].second, InkPointerPhase.DOWN, time))
        for (edge in 1 until corners.size) {
            val (x0, y0) = corners[edge - 1]
            val (x1, y1) = corners[edge]
            for (step in 1..15) {
                time += 8
                val t = step / 15f
                gesture.move(pointer(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, InkPointerPhase.MOVE, time))
            }
        }
        gesture.up(pointer(corners.last().first, corners.last().second, InkPointerPhase.UP, time + 8))
    }

    @Test
    fun aDrawnRectangleBecomesAParametricShapeOnLiftWhenEnabled() {
        val (tab, gesture) = fixture(recognizeShapes = true)
        try {
            val corners = listOf(100f to 100f, 300f to 100f, 300f to 250f, 100f to 250f, 100f to 100f)
            drawCorners(gesture, corners)

            val shapes = tab.document.page(0).layers.flatMap { it.elements }
                .filterIsInstance<ParametricShapeElement>()
            assertEquals("lift commits a parametric shape", 1, shapes.size)
            assertEquals(ParametricShapes.RECTANGLE, shapes[0].shape)
            assertEquals("four rectangle vertices", 4, shapes[0].vertices.size)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun aDrawnTriangleBecomesAParametricShapeOnLiftWhenEnabled() {
        val (tab, gesture) = fixture(recognizeShapes = true)
        try {
            val corners = listOf(150f to 100f, 250f to 250f, 50f to 250f, 150f to 100f)
            drawCorners(gesture, corners)

            val shapes = tab.document.page(0).layers.flatMap { it.elements }
                .filterIsInstance<ParametricShapeElement>()
            assertEquals("lift commits a parametric triangle", 1, shapes.size)
            assertEquals(ParametricShapes.TRIANGLE, shapes[0].shape)
            assertEquals("three triangle vertices", 3, shapes[0].vertices.size)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun aDrawnRectangleStaysRawInkWhenRecognitionIsDisabled() {
        val (tab, gesture) = fixture(recognizeShapes = false)
        try {
            val corners = listOf(100f to 100f, 300f to 100f, 300f to 250f, 100f to 250f, 100f to 100f)
            drawCorners(gesture, corners)

            val shapes = tab.document.page(0).layers.flatMap { it.elements }
                .filterIsInstance<ParametricShapeElement>()
            assertTrue("no parametric shape committed", shapes.isEmpty())

            val strokes = tab.document.page(0).layers.flatMap { it.elements }
                .filterIsInstance<Stroke>()
            assertEquals("committed as raw ink stroke", 2, strokes.size) // 1 initial + 1 drawn
            assertTrue("retains multi-point hand-drawn path", strokes.last().points.size > 10)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun theRecognitionRowPicksOneAnswerAndPersistsIt() {
        val file = temp.newFile("fig-settings.properties")
        val store = DesktopSettings(file)
        assertTrue("on lift by default", store.recognizeShapes)

        val choices = FigureChoices(store)
        assertEquals(ShapeRecognition.ON_LIFT, choices.recognition)

        choices.chooseRecognition(ShapeRecognition.ON_HOLD)
        assertEquals(ShapeRecognition.ON_HOLD, choices.recognition)
        assertTrue("the hold is on", choices.recognizeOnHold)
        assertFalse("and the lift went off with it", choices.recognizeShapes)
        assertFalse(store.recognizeShapes)
        assertTrue(store.recognizeOnHold)

        choices.chooseRecognition(ShapeRecognition.OFF)
        assertEquals(ShapeRecognition.OFF, choices.recognition)

        // Reload from disk: the row's answer survives a reopen, and nothing is left ticking.
        val reloaded = DesktopSettings(file)
        assertFalse(reloaded.recognizeShapes)
        assertFalse(reloaded.recognizeOnHold)
        assertEquals(ShapeRecognition.OFF, FigureChoices(reloaded).recognition)
    }

    @Test
    fun oneTriggerAtATimeWhicheverAnswerIsPicked() {
        val choices = FigureChoices(DesktopSettings(temp.newFile("fig-exclusive.properties")))
        for (answer in ShapeRecognition.entries) {
            choices.chooseRecognition(answer)
            assertEquals(answer, choices.recognition)
            assertFalse(
                "the two stored flags are never both on (after ${answer.label})",
                choices.recognizeShapes && choices.recognizeOnHold,
            )
        }
    }
}
