package com.mobixournal.desktop.sheet

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import com.mobixournal.desktop.settings.DesktopNavigationState
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.PointerOwnership
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
 * dp a **pen's own reading** moves the sheet by, per dp the pen travels — the pen's extra over the hand.
 *
 * The three inputs share one distance ([handScrollGain] and [notchTravelDp]) and this is the only
 * difference between them: a stylus is held like a pen, moving a few centimetres while the page has to
 * cross a window, so 1:1 under it reads as a crawl. It used to be 1.6 — the pen ran visibly ahead of the
 * wheel set beside it (a notch and a hand agreed at 64 dp while the pen covered 96) — so the compromise is
 * **1.25**: still further than a hand, no longer far enough ahead of a notch to read as another rule.
 */
internal const val PEN_GAIN = 1.25f

/** The pen's distance: [PEN_GAIN] over the same *Scroll distance* the wheel and the hand read. */
internal fun penScrollGain(scrollStepDp: Float): Float = PEN_GAIN * handScrollGain(scrollStepDp)

/**
 * dp one wheel notch travels: the notch's [delta] (Compose reports it in notches, sign included) over the
 * same rule the hand reads — the reference notch times [handScrollGain].
 *
 * It used to be the raw *Scroll distance*, which made the notch unbounded while the hand's gain was not: at
 * the slider's top a notch covered 512 dp and a hand drag of the same `512 dp` covered 160, which is exactly
 * the "the wheel and the hand follow two rules" this rule exists to prevent. A function, so the compromise
 * is arithmetic rather than two numbers kept equal by hand (`DesktopHandPanTest`).
 */
internal fun notchTravelDp(delta: Float, scrollStepDp: Float): Float =
    delta * WHEEL_STEP_DP * handScrollGain(scrollStepDp)

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
        fitted.pannedBy(
            notchTravelDp(-delta.x, scrollStepDp),
            notchTravelDp(-delta.y, scrollStepDp),
            pageW, pageH, viewW, viewH,
        )
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
    /** The canvas's edge reading, so a notch that hits a side flashes it (`EdgeBump`). */
    edge: EdgeBumpState? = null,
): Modifier = this
    // No press handler: cancelling the glide on a pointer press took the press with it, so the hand's
    // pan did nothing after a wheel notch until the tool was changed. A glide now ends by itself when
    // another writer moves the sheet (`SmoothScroller`), which is what a press that pans turns out to be.
    .onPointerEvent(PointerEventType.Scroll) { event ->
        // A surface over the page owns the wheel as well as the press: a scroll inside a Settings page must
        // not carry the sheet along behind it.
        if (!PointerOwnership.pageIsLive) return@onPointerEvent
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
            val dx = notchTravelDp(-delta.x, navigation.scrollStepDp)
            val dy = notchTravelDp(-delta.y, navigation.scrollStepDp)
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
                // The glide's own target is where the clamp bites, so a notch into the edge flashes even
                // though the sheet only arrives there a few frames later.
                onRefused = { refusedX, refusedY -> edge?.report(edgeHitOf(refusedX, refusedY)) },
            )
        } else {
            scroller.cancel()
            val before = sheet.viewport
            val after = wheelViewport(
                event, before, sheet.zoom, density, sheet.pageW, sheet.pageH,
                sheet.viewW, sheet.viewH, onFit,
                forceZoom = false,
                zoomRate = navigation.zoomStepWheel,
                scrollStepDp = navigation.scrollStepDp,
            ) ?: return@onPointerEvent
            edge?.report(
                edgeRefusal(
                    before, after,
                    notchTravelDp(-change.scrollDelta.x, navigation.scrollStepDp),
                    notchTravelDp(-change.scrollDelta.y, navigation.scrollStepDp),
                ),
            )
            onViewport(after)
        }
    }
