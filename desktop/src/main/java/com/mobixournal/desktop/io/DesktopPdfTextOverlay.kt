package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.sheet.SheetLayout
import com.mobixournal.desktop.shell.DesktopTab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.mobixournal.design.DesignTokens
import com.mobixournal.render.MagneticHighlighter
import com.mobixournal.render.PdfSelectionSpans
import com.mobixournal.render.PdfTextHandle
import com.mobixournal.render.PdfTextHandlePoint
import com.mobixournal.render.PdfTextHandles
import com.mobixournal.render.PdfWord
import kotlin.math.roundToInt

/**
 * A PDF text selection drawn **over the page**: one washed band per line, the spaces between its words inside
 * it and no border per word.
 *
 * The geometry is `PdfSelectionSpans`' — shared with the tablet — because the boxes a text layer reports are
 * the wrong shape for feedback: painted one per word they read as a row of tiles, a moving block rather than a
 * passage. A line at its union height looks like the selection a reader knows from native text, and nothing in
 * the band's interior moves as the pointer crosses words: only the boundary advances, with no spring, fade or
 * scale anywhere in this function.
 *
 * The colour is the theme's own selection accent at `DesignTokens.TEXT_SELECTION_WASH_ALPHA`, so both shells
 * wash a passage with one material. It is content feedback, not a floating surface, and never the page's ink:
 * nothing here is in the document, so `<Esc>` simply drops it and a save writes the file it read.
 *
 * [pxPerPt] is the sheet's own scale (`zoom × density`), so the bands follow a zoom exactly as the sheet does.
 */
internal fun DrawScope.drawPdfTextSelection(words: List<PdfWord>, pxPerPt: Float) {
    for (span in PdfSelectionSpans.of(words)) {
        val size = Size((span.width * pxPerPt).toFloat(), (span.height * pxPerPt).toFloat())
        if (size.width <= 0f || size.height <= 0f) continue
        drawRect(
            color = DesktopTheme.accent.copy(alpha = DesignTokens.TEXT_SELECTION_WASH_ALPHA),
            topLeft = Offset((span.left * pxPerPt).toFloat(), (span.top * pxPerPt).toFloat()),
            size = size,
        )
    }
}

/**
 * The tab's own text selection, drawn in the page's mapping — the one call a canvas makes.
 *
 * The transform is the sheet's (`SheetLayout`'s viewport and zoom), so the wash follows a zoom exactly as
 * the sheet does; nothing is drawn for another page's selection or for a page with no words.
 */
internal fun DrawScope.drawSelectedPdfText(
    tab: DesktopTab,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    density: Float,
) {
    val picked = tab.pdfTextSelection?.takeIf { it.page == tab.page } ?: return
    val words = DesktopPdfTextSelect.boxes(tab, picked)
    if (words.isEmpty()) return
    withTransform({ translate(offsetX * density, offsetY * density) }) {
        drawPdfTextSelection(words, zoom * density)
    }
}

/**
 * The magnetic highlighter's bands while a drag snaps, at the highlighter's own translucency — a preview
 * of the ink, not chrome.
 */
internal fun DrawScope.drawPdfPreviewBands(bands: List<MagneticHighlighter.Band>, colour: Int, pxPerPt: Float) {
    for (band in bands) {
        drawRect(
            color = Color(colour).copy(alpha = 0.35f),
            topLeft = Offset((band.left * pxPerPt).toFloat(), (band.top * pxPerPt).toFloat()),
            size = Size((band.width * pxPerPt).toFloat(), (band.height * pxPerPt).toFloat()),
        )
    }
}

/**
 * The two **endpoint handles** of a PDF text selection, drawn over the page and reachable from the keyboard.
 *
 * This is the window's control layer, not its gesture layer: the pointer drag itself is answered by the canvas
 * ([com.mobixournal.desktop.sheet.pdfTextHandleDrag]) from the same [PdfTextHandles] geometry, so the dot a
 * reader sees and the target a press must hit cannot drift apart — one rule, two doors.
 *
 * What lives here is what a canvas cannot provide:
 * - **A name.** Each dot is a focusable node carrying [PdfTextHandles.label] — `Selection start` or `Selection
 *   end` — so assistive input can say which end it is on instead of finding two anonymous dots.
 * - **A keyboard path.** Left and Right move the focused end one word in reading order, the counted twin of the
 *   drag, and the same moves are exposed as accessibility actions. That is the equivalent a grip owes a reader
 *   who cannot drag (`docs/interface-contract.md`), and it is also how a handle beside the viewport's edge is
 *   adjusted when half of it is out of the pane.
 * - **The state.** The dot wears the theme's accent and a paper-white halo while resting, and the focus ring of
 *   the shared chrome when the keyboard is on it. Nothing animates: a passage must not look like it is being
 *   translated as one block while one of its ends is adjusted.
 *
 * A dot that has been panned or zoomed outside the pane is **not** drawn and not focusable: a control in the
 * margin belongs to the window's chrome, not to a page that is showing none of it.
 */
@Composable
internal fun PdfTextHandlesOverlay(
    ui: PdfTextCopyUi,
    zoom: Float,
    density: Float,
    offsetX: Float,
    offsetY: Float,
    canvas: Size,
) {
    if (ui.handles.isEmpty() || canvas.width <= 0f || canvas.height <= 0f) return
    val touchPx = PdfTextHandles.touchRadiusPx(density) * 2f
    val touchDp = with(LocalDensity.current) { touchPx.toDp() }
    val pxPerPt = zoom * density
    for (handle in ui.handles) {
        val at = PdfTextHandles.screen(
            handle,
            pxPerPt.toDouble(),
            (offsetX * density).toDouble(),
            (offsetY * density).toDouble(),
        )
        if (at.x < 0f || at.y < 0f || at.x > canvas.width || at.y > canvas.height) continue
        HandleDot(
            handle = handle,
            density = density,
            onNudge = ui.onHandleNudge,
            modifier = Modifier
                .offset {
                    IntOffset((at.x - touchPx / 2f).roundToInt(), (at.y - touchPx / 2f).roundToInt())
                }
                .size(touchDp),
        )
    }
}

/**
 * One handle: a dot inside a screen-sized target square, focusable, with the shared focus ring and the counted
 * moves on the arrow keys.
 *
 * The square is [DesignTokens.TEXT_SELECTION_HANDLE_TOUCH_DP] across and it does not scale with the page, so the
 * grip is the same at 30 % and at 300 %; the dot inside is [DesignTokens.TEXT_SELECTION_HANDLE_DP]. Both are
 * tokens, and neither is a page quantity — a handle that shrank with the zoom would stop being a target exactly
 * where a reader needs it most.
 */
@Composable
private fun HandleDot(
    handle: PdfTextHandlePoint,
    density: Float,
    onNudge: ((PdfTextHandle, Int) -> Boolean)?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val radiusPx = PdfTextHandles.drawnRadiusPx(density)
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onKeyEvent false
                }
                onNudge?.invoke(handle.handle, step) ?: false
            }
            .semantics {
                contentDescription = PdfTextHandles.label(handle.handle)
                customActions = listOf(
                    CustomAccessibilityAction("Move one word back") {
                        onNudge?.invoke(handle.handle, -1) ?: false
                    },
                    CustomAccessibilityAction("Move one word forward") {
                        onNudge?.invoke(handle.handle, 1) ?: false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(with(LocalDensity.current) { (radiusPx * 2f + 8f * density).toDp() })) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            // The sheet's own white under the dot, as a component's pin wears it: on black ink, on a grey scan
            // and on the theme's dark paper the accent needs an edge to be seen as an object.
            drawCircle(color = Color.White, radius = radiusPx + 2f * density, center = centre)
            drawCircle(color = DesktopTheme.accent, radius = radiusPx, center = centre)
            if (focused) {
                drawCircle(
                    color = DesktopTheme.accent,
                    radius = radiusPx + 6f * density,
                    center = centre,
                    style = Stroke(width = 2f * density),
                )
            }
        }
    }
}
