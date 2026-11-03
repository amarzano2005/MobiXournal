package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What a **tab switch costs on screen**, in milliseconds — the reading `DesktopTabs.activate` cannot give.
 *
 * Switching is one state write (`activeIndex = index`), so the number a hand feels is the frame the window
 * draws afterwards: the page read, its picture rebuilt, and the shell re-composed around it. This measures
 * that frame for the **whole shell** and for the **canvas alone**, so a pause is attributed rather than
 * guessed at, on documents shaped like the ones the window is opened on.
 */
class DesktopTabSwitchCostTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** One document of [pages] pages, each carrying [strokes] marks — a written page, not a blank one. */
    private fun document(name: String, pages: Int = 10, strokes: Int = 200): File {
        val file = temp.newFile(name)
        SyntheticNotebook.write(
            file,
            (0 until pages).map { index ->
                Page(
                    width = 850.0,
                    height = 1100.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(Layer((0 until strokes).map { SyntheticNotebook.stroke(40.0 + index) })),
                    id = "pg_${name}_$index",
                )
            },
        )
        return file
    }

    @Test
    fun `the frame after a tab switch`() {
        val settings = DesktopSettings(temp.newFile("settings.properties"))
        val state = DesktopShellState(settings)
        state.open(document("primo.mobix"))
        state.open(document("secondo.mobix"))

        val shell = ImageComposeScene(1400, 900, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        // The canvas alone, reading the active tab itself: the switch reaches it, the chrome does not.
        val canvas = ImageComposeScene(1100, 800, Density(1f)) {
            val tab = state.tabs.active ?: return@ImageComposeScene
            PageCanvas(
                tab = tab,
                panMode = false,
                onViewport = { tab.viewport = it },
                onFit = { tab.fit = it },
            )
        }
        try {
            // Warm-up: both tabs drawn once before anything is timed, so what is measured is a switch
            // between two documents the window has already shown — the case a reader repeats all day.
            for (index in listOf(0, 1, 0, 1)) {
                state.tabs.activate(index)
                repeat(3) {
                    frame(shell)
                    frame(canvas)
                }
            }
            val full = switching(state) { frame(shell) }
            val canvasOnly = switching(state) { frame(canvas) }
            report("switch, full shell", full)
            report("switch, canvas only", canvasOnly)

            val tab = state.tabs.active!!
            val pageChange = switching(state) {
                tab.page = if (tab.page == 0) 1 else 0
                frame(canvas)
            }
            report("page change, canvas", pageChange)

            // The budgets are set to catch an order of magnitude — the return of the halo that cost a
            // quarter of a second a page — not to grade this machine: a switch that misses a frame is
            // allowed, a switch that is a pause is not.
            assertTrue("the readings landed", full.size == 8 && canvasOnly.size == 8 && pageChange.size == 8)
            val medianFull = full.sorted()[full.size / 2]
            val medianCanvas = canvasOnly.sorted()[canvasOnly.size / 2]
            assertTrue(
                "a switch must not be a pause (full shell %.1f ms, canvas %.1f ms)".format(medianFull, medianCanvas),
                medianFull < 200.0,
            )
            assertTrue(
                "the sheet is not what a switch pays for (canvas %.1f ms)".format(medianCanvas),
                medianCanvas < 50.0,
            )
        } finally {
            shell.close()
            canvas.close()
            state.tabs.closeAll()
        }
    }

    /** Eight switches, timed from the state write to the last pixel of the frame it caused. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun switching(state: DesktopShellState, draw: () -> Unit): List<Double> {
        val samples = mutableListOf<Double>()
        repeat(8) { round ->
            if (state.tabs.size < 2) return samples
            state.tabs.activate(if (round % 2 == 0) 0 else 1)
            Snapshot.sendApplyNotifications()
            val started = System.nanoTime()
            draw()
            samples += (System.nanoTime() - started) / 1_000_000.0
        }
        return samples
    }

    private fun report(label: String, samples: List<Double>) {
        val sorted = samples.sorted()
        println(
            "%-22s median %6.1f ms  p95 %6.1f ms  max %6.1f ms  (n=%d)".format(
                label,
                sorted[sorted.size / 2],
                sorted[(sorted.size * 95) / 100],
                sorted.last(),
                sorted.size,
            ),
        )
    }

    /**
     * The same switch on the notebook the window is actually opened on (6 pages, thousands of strokes):
     * synthetic pages made of two points say what a paint costs, not what a *written* page costs.
     *
     * Skipped when the fixture is not in the checkout — it is the repository's own sample, not a test asset.
     */
    @Test
    fun `the switch costs on the real notebook`() {
        var dir: java.io.File? = java.io.File("").absoluteFile
        var notebook: java.io.File? = null
        while (dir != null && notebook == null) {
            val candidate = java.io.File(dir, "udiff.xopp")
            if (candidate.isFile) notebook = candidate
            dir = dir.parentFile
        }
        val source = notebook ?: run {
            println("udiff.xopp not in this checkout; the real-notebook reading is skipped")
            return
        }
        val settings = DesktopSettings(temp.newFile("settings.properties"))
        val state = DesktopShellState(settings)
        val copy = temp.newFile("udiff-copy.xopp")
        source.copyTo(copy, overwrite = true)
        val started = System.nanoTime()
        state.open(source)
        println("open, first document: %.0f ms".format((System.nanoTime() - started) / 1_000_000.0))
        val second = System.nanoTime()
        state.open(copy)
        println("open, second document: %.0f ms".format((System.nanoTime() - second) / 1_000_000.0))
        val scene = ImageComposeScene(1400, 900, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            val warm = System.nanoTime()
            frame(scene)
            println("first frame after open (composition + record): %.0f ms".format((System.nanoTime() - warm) / 1_000_000.0))
            for (index in listOf(0, 1, 0, 1)) {
                state.tabs.activate(index)
                repeat(3) { frame(scene) }
            }
            val reads = mutableListOf<Double>()
            val page = state.tabs.all[0]
            repeat(5) {
                val begin = System.nanoTime()
                page.document.page(0)
                reads += (System.nanoTime() - begin) / 1_000_000.0
            }
            report("document.page(0) read", reads)
            report("switch, real notebook", switching(state) { frame(scene) })
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    /** A frame with the snapshot notifications a real frame loop sends; nothing is written to disk here. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun frame(scene: ImageComposeScene) {
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}
