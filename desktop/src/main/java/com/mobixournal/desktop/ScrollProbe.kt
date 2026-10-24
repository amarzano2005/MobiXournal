package com.mobixournal.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import kotlinx.coroutines.delay

/**
 * Scrolling and zooming a dense page, measured through the window's own watchdog.
 *
 * The frame budget is about **paint**, and a scroll or a pinch used to be the one gesture that paid the
 * full Kotlin paint on every frame: `PagePicture` recorded the sheet *per viewport*, so the wheel changed
 * the key and `drawPageContent` ran again — `LivingInk` for every living entity, a measurement per text
 * box, a `Path` per stroke (`udiff.xopp` costs 3.8 ms that way, over the 6.9 ms a 144 Hz frame allows).
 * The sheet is now recorded at document scale and placed by the canvas matrix, so both gestures are
 * replays; this probe is what turns that claim into a number.
 *
 * It opens the document in the editor's own `PageCanvas`, scrolls it for about two seconds and then zooms
 * it for about one, changing nothing else — no synthetic packets, no drawing — and runs with `--diagnose`
 * so `DesktopDiagnostics` writes the one-line-per-second record the numbers are read from
 * (`mobix-diagnose.log`: `paints/s`, `worst`, `paint=<mean>/<worst>`). It is deliberately a *window*: the
 * paint cost measured here is the one the GPU surface pays, not a headless software rasterisation.
 */
internal fun runScrollProbe(document: File?) {
    val file = document ?: error("the scroll probe needs a document: --probe-scroll <file>")
    val tabs = DesktopTabs()
    val tab = tabs.open(DocumentLoader.load(file))
    tab.fit = FitMode.NONE
    // At 200 % the sheet is taller than the probe's window, so a scroll has somewhere to go: a fitted
    // page is clamped in place and would repaint nothing.
    tab.viewport = Viewport(zoom = 2f)
    println("MobiXournal scroll probe: ${file.name} -> mobix-diagnose.log")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1400.dp, height = 900.dp),
            title = "MobiXournal scroll probe",
        ) {
            LaunchedEffect(Unit) {
                delay(1200) // layout + first paint
                val startY = tab.viewport.offsetY
                val startZoom = tab.viewport.zoom
                // A wheel's own cadence: one notch's worth of travel per frame, 60 steps a second.
                for (step in 1..PROBE_SCROLL_STEPS) {
                    // Down the page and back up (a triangle, not a ramp): the viewport is clamped at the
                    // sheet's edges, and a scroll parked against one would stop invalidating the canvas.
                    val travel = (if (step <= PROBE_SCROLL_STEPS / 2) step else PROBE_SCROLL_STEPS - step)
                    tab.viewport = tab.viewport.copy(offsetY = startY - travel * PROBE_SCROLL_STEP_PX)
                    delay(PROBE_FRAME_MS)
                }
                // Then a pinch: the zoom itself changes every frame, which re-recorded the sheet before.
                for (step in 1..PROBE_ZOOM_STEPS) {
                    tab.viewport = tab.viewport.copy(zoom = startZoom + step * PROBE_ZOOM_STEP)
                    delay(PROBE_FRAME_MS)
                }
                delay(400)
                exitApplication()
            }
            PageCanvas(
                tab = tab,
                panMode = false,
                onViewport = {},
                onFit = {},
            )
        }
    }
    tab.document.close()
}

/** Pixels of travel per frame: small enough to be a scroll, large enough that every frame repaints. */
private const val PROBE_SCROLL_STEP_PX = 6f

/** The frame interval the probe asks for — `delay` is a floor, the display's vsync is the ceiling. */
private const val PROBE_FRAME_MS = 8L

private const val PROBE_SCROLL_STEPS = 300
private const val PROBE_ZOOM_STEPS = 120
private const val PROBE_ZOOM_STEP = 0.005f
