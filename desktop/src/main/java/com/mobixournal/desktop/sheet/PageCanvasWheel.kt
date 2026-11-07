package com.mobixournal.desktop.sheet

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import com.mobixournal.desktop.settings.DesktopNavigationState
import com.mobixournal.desktop.shell.DesktopTab
import kotlin.math.exp

/** dp a wheel notch moves the page by default — and the distance a 1:1 hand pan reads as. */
internal const val WHEEL_STEP_DP = 64f

/**
 * How far the **hand** moves the sheet for each dp the pointer (or the pen) travels.
 *
 * Settings ▸ Scrolling's *Scroll distance* is a distance, not a wheel's private number: `64 dp` is the
 * default and reads as 1:1, `128` as twice the travel. A pan that ignored it made the one setting that
 * describes scrolling a wheel-only one, so the same document moved by two different rules depending on
 * how the reader pushed it. Bounded, because the slider's ends are a usable range and not a multiplier
 * anyone should be able to leave the page uncontrollable with.
 */
internal fun handScrollGain(scrollStepDp: Float): Float =
    (scrollStepDp / WHEEL_STEP_DP).coerceIn(HAND_GAIN_MIN, HAND_GAIN_MAX)

/** The smallest and largest gain the hand may take from *Scroll distance*: a quarter to two and a half. */
internal const val HAND_GAIN_MIN = 0.25f
internal const val HAND_GAIN_MAX = 2.5f

/**
 * What one wheel event does to the page: `Ctrl` zooms **at the cursor** (exponentially, so 30 % and
 * 300 % feel alike), otherwise it scrolls. Either way the viewport comes back for the caller to apply;
 * null when the event carried no change. A scroll keeps the fit, so reading down a width-fitted strip
 * does not silently turn it into a free zoom.
 *
 * [forceZoom] is the tablet dial's notch: Windows delivers it as a plain `WM_MOUSEWHEEL` with no `Ctrl`
 * held, so the caller says which wheel turned — identified by the device that sent it (`TabletWheel`) —
 * and the notch takes the zoom path with no modifier.
 */
internal fun wheelViewport(
    event: PointerEvent,
    viewport: Viewport,
    zoom: Float,
    density: Float,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    onFit: (FitMode) -> Unit,
    forceZoom: Boolean = false,
    zoomRate: Float = 0.12f,
    scrollStepDp: Float = WHEEL_STEP_DP,
): Viewport? {
    val change = event.changes.firstOrNull() ?: return null
    val delta = change.scrollDelta
    val fitted = viewport.copy(zoom = zoom)
    return if (forceZoom || event.keyboardModifiers.isCtrlPressed) {
        val factor = exp((-delta.y * zoomRate)).coerceIn(0.5f, 2f)
        val anchor = change.position / density
        onFit(FitMode.NONE)
        fitted.zoomBy(factor, anchor.x, anchor.y, pageW, pageH, viewW, viewH)
    } else {
        fitted.pannedBy(-delta.x * scrollStepDp, -delta.y * scrollStepDp, pageW, pageH, viewW, viewH)
    }
}

/**
 * Attaches wheel handling (zoom with Ctrl/Dial, or smooth/instant scrolling) to the canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.pageWheelScroll(
    tab: DesktopTab,
    sheet: SheetLayout,
    dialWheel: () -> Boolean,
    density: Float,
    navigation: DesktopNavigationState,
    scroller: SmoothScroller,
    onFit: (FitMode) -> Unit,
    onViewport: (Viewport) -> Unit,
): Modifier = this
    // No press handler: cancelling the glide on a pointer press took the press with it, so the hand's
    // pan did nothing after a wheel notch until the tool was changed. A glide now ends by itself when
    // another writer moves the sheet (`SmoothScroller`), which is what a press that pans turns out to be.
    .onPointerEvent(PointerEventType.Scroll) { event ->
        val change = event.changes.firstOrNull() ?: return@onPointerEvent
        val isZoom = dialWheel() || event.keyboardModifiers.isCtrlPressed
        if (isZoom) {
            scroller.cancel()
            wheelViewport(
                event, sheet.viewport, sheet.zoom, density, sheet.pageW, sheet.pageH,
                sheet.viewW, sheet.viewH, onFit,
                forceZoom = dialWheel(),
                zoomRate = navigation.zoomStepWheel,
                scrollStepDp = navigation.scrollStepDp,
            )?.let(onViewport)
        } else if (navigation.smoothScroll) {
            val delta = change.scrollDelta
            val dx = -delta.x * navigation.scrollStepDp
            val dy = -delta.y * navigation.scrollStepDp
            scroller.scrollBy(
                dx = dx,
                dy = dy,
                currentViewport = { tab.viewport },
                pageW = sheet.pageW,
                pageH = sheet.pageH,
                viewW = sheet.viewW,
                viewH = sheet.viewH,
                fluidity = navigation.scrollFluidity,
                onViewport = onViewport,
            )
        } else {
            scroller.cancel()
            wheelViewport(
                event, sheet.viewport, sheet.zoom, density, sheet.pageW, sheet.pageH,
                sheet.viewW, sheet.viewH, onFit,
                forceZoom = false,
                zoomRate = navigation.zoomStepWheel,
                scrollStepDp = navigation.scrollStepDp,
            )?.let(onViewport)
        }
    }
