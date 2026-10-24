package com.mobixournal.desktop

import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import com.mobixournal.format.model.Element
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.SelectionRegion

/**
 * A press with a **STEM slot** (Plot, Table, Circuit): the drag previews the box the element will take,
 * and the lift hands that box to the placement — which asks the family's values first for a plot or a
 * table, and writes a component at once because its question was the palette.
 *
 * The box is [LivingPlacement]'s — a drag is the element's bounds, a click is the family's default size
 * centred on the press, both kept inside the page — so the preview and the answers act on the same
 * rectangle. Two previews share that box: the sheet's traced-region outline (`SelectionRegion.Rect`, the
 * dashed shape a marquee shows), and — for a slot whose element needs no question — the element itself,
 * rebuilt on every move by [buildPreview]. A component is therefore pulled out of the sheet along the pen
 * the way a figure is drawn, instead of appearing only when the pen lifts.
 *
 * A living slot is **not ink**: [DesktopTool.inksByPackets] is false for it, so the pen's packets stand
 * aside and this Compose gesture keeps the drag even with the stylus in proximity — the same rule
 * geometry and vertical space already follow. `Esc` cancels through [onLiveDrag].
 */
internal suspend fun AwaitPointerEventScope.livingPress(
    start: PointerInputChange,
    tool: DesktopTool,
    viewport: Viewport,
    pageW: Float,
    pageH: Float,
    previewRegion: MutableState<SelectionRegion?>?,
    /** The element a drag is drawing right now, painted by the sheet and cleared on the lift. */
    previewLiving: MutableState<Element?>? = null,
    /** What [bounds] would place, for the live preview; null for a family that asks its values first. */
    buildPreview: ((Bounds) -> Element?)? = null,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onPlace: (LivingSlot, Bounds) -> Unit,
): Boolean {
    val slot = livingSlotOf(tool) ?: return false
    val (x0, y0) = pagePointOf(start.position.x, start.position.y, density, viewport)
    if (x0 !in 0.0..pageW.toDouble() || y0 !in 0.0..pageH.toDouble()) return true
    val default = LivingPlacement.defaultSize(slot, pageW.toDouble())
    var cancelled = false
    val cancel = {
        cancelled = true
        previewRegion?.value = null
        previewLiving?.value = null
    }
    onLiveDrag(cancel)
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            val (x1, y1) = pagePointOf(change.position.x, change.position.y, density, viewport)
            val bounds = LivingPlacement.bounds(pageW.toDouble(), pageH.toDouble(), x0, y0, x1, y1, default)
            previewRegion?.value = SelectionRegion.Rect(bounds)
            previewLiving?.value = buildPreview?.invoke(bounds)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                previewRegion?.value = null
                previewLiving?.value = null
                if (!cancelled) onPlace(slot, bounds)
                break
            }
            change.consume()
        }
    } finally {
        previewRegion?.value = null
        previewLiving?.value = null
        onLiveDrag(null)
    }
    return true
}

/** The slot's name, as the strip's own fallback face prints it — for a status line or a failure. */
internal fun LivingSlot.label(): String = when (this) {
    LivingSlot.PLOT -> "Plot"
    LivingSlot.TABLE -> "Table"
    LivingSlot.CIRCUIT -> "Circuit"
    LivingSlot.MATH -> "Formula"
}
