package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The page workspace: one page, zoomed and panned, on the desk.
 *
 * One page at a time rather than a scrolling column is a deliberate choice for a workstation: at 300 %
 * a document is a column of twenty screens, and "which page am I on" stops being answerable by
 * scrolling. The page you are on is [DesktopTab.page], the sidebar and the status bar both read it, and
 * the viewport (zoom, pan, fit) belongs to the tab, so switching tabs restores what each was showing.
 *
 * A page written as a **long strip** — a lecture note the tablet app saved as one 850 × 2976 pt sheet —
 * opens fitted to its **width** rather than shrunk to a sliver (`FitMode.AUTO`), and the wheel reads down
 * it. Scrolling therefore never abandons the fit: a width-fitted page must stay fitted while you travel
 * through it, or the first notch would turn the view into a free zoom and a window resize would stop
 * refitting the page.
 *
 * Input, in the conventions a document viewer has already taught everyone:
 *  - **wheel** scrolls (vertically, and horizontally for a wide page); **Ctrl + wheel** zooms at the
 *    cursor, which is the *page point under the cursor* staying still, not the page's centre;
 *  - **middle-drag** or **space-drag** pans;
 *  - a **primary drag with the pen or highlighter** draws, and with the eraser rubs out — the gesture
 *    is decided by the shared `InkPointerRouter` (`PageCanvasInput.kt`), so the window has no second
 *    opinion about what a press means or how hard it draws;
 *  - the page is clamped into the window by [Viewport], so it cannot be dragged out of sight.
 *
 * A stroke is **previewed as it is drawn** and committed on lift as one edit through
 * [DesktopDocument.editPage]: the page the painter shows is the page the tab will save. A press that
 * lands off the sheet starts nothing, because the desk is not paper.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PageCanvas(
    tab: DesktopTab,
    panMode: Boolean,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    tool: DesktopTool = DesktopTool.PEN,
    ink: DesktopInk = DesktopInk(),
    onEdited: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val page = remember(tab, tab.page, tab.document.edits) {
        runCatching { tab.document.page(tab.page) }
    }
    val loaded = page.getOrNull()
    // The stroke in progress, in page pt: the canvas draws it every frame, and the lift commits it.
    val draft = remember(tab, tab.page) { mutableStateListOf<InkSample>() }

    BoxWithConstraints(modifier.background(DESK).clipToBounds()) {
        val viewW = maxWidth.value
        val viewH = maxHeight.value
        if (loaded == null) {
            Text(
                text = "Could not read this page: ${page.exceptionOrNull()?.message}",
                color = DesktopTheme.danger,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        val pageW = loaded.width.toFloat()
        val pageH = loaded.height.toFloat()
        val zoom = tab.fit.zoomFor(pageW, pageH, viewW, viewH) ?: tab.viewport.zoom
        // A fitted page is re-centred every layout pass; a free one keeps its own offsets, clamped.
        val viewport = tab.viewport.copy(zoom = zoom).clamped(pageW, pageH, viewW, viewH)
        // The status bar reports the *effective* zoom, which for a fitted page is the window's answer and
        // not the tab's stored one: publishing it keeps the readout true without a second source of truth.
        //
        // Only while a fit mode is on. Publishing it unconditionally is a stale-write race: a zoom the user
        // asked for can be overwritten by the fitted value of the *previous* frame, which lands as a tab
        // that says 98 % and refuses to zoom.
        LaunchedEffect(zoom, viewW, viewH, tab.fit) {
            if (tab.fit != FitMode.NONE && tab.viewport.zoom != zoom) {
                tab.viewport = tab.viewport.copy(zoom = zoom)
            }
        }

        Canvas(
            Modifier.fillMaxSize()
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    wheelViewport(event, viewport, zoom, density, pageW, pageH, viewW, viewH, onFit)
                        ?.let(onViewport)
                }
                .pointerInput(tab, panMode, tool, ink, pageW, pageH, viewW, viewH, viewport) {
                    pageGestures(
                        tab = tab,
                        panMode = panMode,
                        tool = tool,
                        ink = ink,
                        viewport = viewport,
                        pageW = pageW,
                        pageH = pageH,
                        viewW = viewW,
                        viewH = viewH,
                        draft = draft,
                        onViewport = onViewport,
                        onEdited = onEdited,
                    )
                },
        ) {
            // The page is painted in its own coordinate space and moved by the viewport: the paper,
            // the picture and the ink all scale together, which is what makes a zoomed stroke keep the
            // width it was drawn with.
            // Resolved before the transform: inside it the receiver is a DrawTransform, which has no
            // display density of its own.
            val pxPerPt = zoom * density
            val dx = viewport.offsetX * density
            val dy = viewport.offsetY * density
            withTransform({ translate(dx, dy) }) {
                // (page, scale, measurer, backdrop, hidden layers) — the last one is the sidebar's eyes.
                drawPageContent(loaded, pxPerPt, measurer, tab.document.backdrop, tab.hiddenOn(tab.page))
                // The stroke in progress, painted with the same rule as the committed ink: a preview
                // that looked different from what the lift writes would be a lie about the file.
                val inkTool = tool.inkTool()
                if (inkTool != null && draft.isNotEmpty()) {
                    strokeOf(inkTool, ink.colour, ink.width, draft.toList())?.let { draftStroke ->
                        drawInkStroke(draftStroke, pxPerPt)
                    }
                }
            }
        }
    }
}

/** The desk behind the page: true black, as decided in `docs/ui-modernization.md`. */
private val DESK = DesktopTheme.desk

/** A page's on-screen size in dp at [zoom] — used by the sidebar's progress bar and the status bar. */
internal fun pageSizeDp(pageW: Double, pageH: Double, zoom: Float): Pair<Float, Float> =
    (pageW * zoom).toFloat() to (pageH * zoom).toFloat()

/** The zoom label a status bar shows. */
internal fun zoomLabel(zoom: Float): String = "${(zoom * 100f).roundToInt()}%"

/** A page's label: `Page 3 / 12`. */
internal fun pageLabel(page: Int, count: Int): String = "Page ${page + 1} / ${count.coerceAtLeast(1)}"
