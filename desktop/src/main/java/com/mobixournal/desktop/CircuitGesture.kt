package com.mobixournal.desktop

import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.CircuitElementFactory
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.ShapeKind

/**
 * A **circuit drag**: the two page points a component is pulled out between, and the part that span
 * places.
 *
 * The tablet has always authored a component from the drag's own line — a part drawn at 45° is rotated
 * to lie along the hand — and aims both ends at the page's pins through `CircuitSnapping`. The window
 * used to take the drag's *bounding box* instead, so a drag up or down laid the component out
 * horizontally across the box's width and a part could not be turned at all; and its lift never asked
 * the magnet, so a wire or a component drawn onto a pin stayed a few points off it and nothing was ever
 * joined.
 *
 * Both ends are snapped **before** the element is built, by the same reach the tablet previews with
 * (`CircuitSnapping.thresholdFor`), so what the drag shows is exactly what the lift writes — and a wire
 * (the Line tool) is snapped by the same rule in `shapeDrag`.
 */
internal data class CircuitSpan(
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double,
)

/**
 * The span a drag across [page] means for [kind], with both ends pulled onto the nearest pin or wire
 * end in reach.
 *
 * Pure, so the magnet is a JVM test rather than something to feel for with a pen. [scale] is the view's
 * page-points-to-pixels factor, which is what sets the magnet's reach on screen.
 */
internal fun circuitSpan(
    kind: ShapeKind,
    page: Page,
    startX: Double,
    startY: Double,
    endX: Double,
    endY: Double,
    scale: Float,
): CircuitSpan {
    val targets = CircuitSnapping.gestureTargets(kind, page).orEmpty()
    val (sx, sy) = CircuitSnapping.snapPoint(
        page, startX, startY, snapToGrid = false, magnetic = true, scale = scale, targets = targets,
    )
    val (ex, ey) = CircuitSnapping.snapPoint(
        page, endX, endY, snapToGrid = false, magnetic = true, scale = scale, targets = targets,
    )
    return CircuitSpan(sx, sy, ex, ey)
}

/**
 * The component [span] places, drawn in [ink] — the preview the sheet paints while the pen is still
 * down, and (with an id minted at the commit) the element that lands.
 *
 * It is `CircuitElementFactory.placedElementFor`, the same placement rule the tablet commits with: a tap
 * is the family's canonical length, a drag spans its two ends. Nothing here decides geometry.
 */
internal fun circuitPreview(kind: ShapeKind, span: CircuitSpan, ink: DesktopInk): Element? =
    CircuitElementFactory.placedElementFor(
        symbol = kind.name,
        startX = span.startX,
        startY = span.startY,
        endX = span.endX,
        endY = span.endY,
        strokeColor = ink.colour,
        strokeWidth = ink.width.toDouble(),
    )

/**
 * The element a lifted [span] commits: the same geometry the preview drew, with the identity the model
 * mints at a placement ([LivingPlacement.circuitBetween]).
 */
internal fun circuitElementForSpan(kind: ShapeKind, span: CircuitSpan, ink: DesktopInk): Element? =
    LivingPlacement.circuitBetween(
        kind = kind,
        startX = span.startX,
        startY = span.startY,
        endX = span.endX,
        endY = span.endY,
        colour = ink.colour,
        strokeWidthPt = ink.width.toDouble(),
    )

/**
 * A press with a **circuit slot**: the part is pulled out of the sheet along the pen.
 *
 * The gesture is the tablet's: the press anchors one end, every move re-aims the other through the
 * magnet, the sheet shows the part at the current span, and the lift hands that span over — so nothing
 * is written until it lifts, and the placement is one edit and one undo step.
 *
 * It is not ink ([DesktopTool.inksByPackets] is false for a circuit slot), so the pen's packets stand
 * aside and this Compose gesture keeps the drag even with the stylus in proximity. The drag reaches the
 * canvas's border and keeps pulling (`DragView`), so a component may be longer than the window.
 */
internal suspend fun AwaitPointerEventScope.circuitDrag(
    start: PointerInputChange,
    kind: ShapeKind,
    page: Page,
    ink: DesktopInk,
    view: DragView,
    previewLiving: MutableState<Element?>?,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onPlace: (ShapeKind, CircuitSpan) -> Unit,
) {
    val (x0, y0) = pagePointOf(start.position.x, start.position.y, density, view.current())
    if (x0 !in 0.0..view.pageW.toDouble() || y0 !in 0.0..view.pageH.toDouble()) return
    var lastX = start.position.x
    var lastY = start.position.y
    var cancelled = false
    val cancel = {
        cancelled = true
        previewLiving?.value = null
    }
    onLiveDrag(cancel)

    /** The span from the press to the pointer at ([px], [py]), magnet and preview in one place. */
    fun spanAt(px: Float, py: Float): CircuitSpan {
        val (x1, y1) = pagePointOf(px, py, density, view.current())
        return circuitSpan(kind, page, x0, y0, x1, y1, view.current().zoom * density)
    }

    fun preview(px: Float, py: Float) {
        previewLiving?.value = circuitPreview(kind, spanAt(px, py), ink)
    }

    // The part is already on the sheet at the press: a click is the family's canonical length, and the
    // drag only re-aims the far end. A preview that appeared on the first move would blink.
    preview(lastX, lastY)
    view.beginDrag(lastX, lastY) { px, py -> preview(px, py) }
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            lastX = change.position.x
            lastY = change.position.y
            view.moved(lastX, lastY)
            preview(lastX, lastY)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                val span = spanAt(lastX, lastY)
                previewLiving?.value = null
                if (!cancelled) onPlace(kind, span)
                break
            }
            change.consume()
        }
    } finally {
        view.endDrag()
        previewLiving?.value = null
        onLiveDrag(null)
    }
}

/**
 * A press with a circuit slot, or false when [tool] is not one — the canvas's dispatch, kept beside the
 * drag it decides so the pointer file is about the pointer and not about components.
 *
 * A canvas with no shell behind it (`targets.onPlaceCircuit` null, as in a test) commits the family's own
 * default part, which is the same element the shell would build.
 */
internal suspend fun AwaitPointerEventScope.circuitPress(
    start: PointerInputChange,
    tool: DesktopTool,
    tab: DesktopTab,
    ink: DesktopInk,
    view: DragView,
    targets: PagePressTargets,
    onEdited: (Int) -> Unit,
): Boolean {
    if (circuitFamilyOf(tool) == null) return false
    val page = runCatching { tab.document.page(tab.page) }.getOrNull() ?: return false
    circuitDrag(
        start = start,
        kind = targets.circuitKind,
        page = page,
        ink = ink,
        view = view,
        previewLiving = targets.livingPreview,
        onLiveDrag = targets.onLiveDrag,
        onPlace = targets.onPlaceCircuit ?: { kind, span ->
            circuitElementForSpan(kind, span, ink)?.let { element -> placeLivingElement(tab, element, onEdited) }
            Unit
        },
    )
    return true
}

/**
 * The **wire** magnet: a drawn stroke's two ends pulled onto the page's pins, when [kind] is a
 * connector (the Line tool, or a circuit symbol drawn as ink).
 *
 * Every other figure is the user's own geometry and is returned untouched — a rectangle near a pin must
 * not be pulled off the hand — and a page with no components has nothing to pull toward, so the answer
 * is the stroke itself.
 */
internal fun snapWireEnds(
    kind: ShapeKind,
    points: List<com.mobixournal.format.model.StrokePoint>,
    page: Page?,
    scale: Float,
): List<com.mobixournal.format.model.StrokePoint> {
    if (page == null || points.size < 2 || !CircuitElementFactory.isConnectorKind(kind)) return points
    return CircuitSnapping.snapStrokeEndpoints(
        points,
        CircuitSnapping.collectAllTargets(page),
        thresholdPt = CircuitSnapping.thresholdFor(scale),
    )
}
