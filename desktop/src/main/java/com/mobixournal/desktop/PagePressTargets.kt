package com.mobixournal.desktop

import androidx.compose.runtime.MutableState
import com.mobixournal.format.model.Element
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ShapeKind

/**
 * What a press on the sheet means when it is **not ink**, carried as one value.
 *
 * A page press can place a living element, open the Text slot's box, open a living element's editor, or
 * keep a handle on the drag `Esc` would cancel — five answers the shell gives and the gesture only relays.
 * Grouped here for two reasons: [PointerInputScope.pageGestures] keeps a signature a person can read
 * (`SourceStructureTest` caps a function's length, and nine documented parameters are most of it), and a
 * test that only inks can leave every answer at its default instead of writing a lambda per slot.
 */
internal class PagePressTargets(
    /** A lifted living-slot drag: the shell asks the family's values before anything is written. */
    val onPlaceLiving: (LivingSlot, Bounds) -> Unit = { _, _ -> },
    /**
     * The **Text slot**'s press: open a box at a page point, on a sheet this wide and this tall. Null when
     * the canvas has no shell behind it (a test that only inks).
     */
    val onTextPress: ((x: Double, y: Double, pageW: Double, pageH: Double) -> Unit)? = null,
    /** A **double-click on a living element**: open its editor, as the tablet's double-tap does. */
    val onEditLiving: (() -> Unit)? = null,
    /**
     * The living element a placement drag is drawing right now — so a component is pulled out along the
     * pen instead of appearing when it lifts.
     */
    val livingPreview: MutableState<Element?>? = null,
    /**
     * A lifted **circuit** drag: the part and the two page points it was pulled out between, already
     * snapped to the pins in reach (`CircuitGesture`). Null on a canvas with no shell behind it, which
     * commits the family's defaults itself.
     */
    val onPlaceCircuit: ((ShapeKind, CircuitSpan) -> Unit)? = null,
    /** The part the circuit slot in hand would place — the picker's answer, read by the drag. */
    val circuitKind: ShapeKind = com.mobixournal.render.LivingPlacement.DEFAULT_CIRCUIT_KIND,
    /**
     * The triangle's and trapezoid's own answers, read by a figure drag — Settings ▸ Figures' value, so
     * the preview and the lift build the same figure.
     */
    val figures: FigureShape = FigureShape(),
    /**
     * The drag in flight that `Esc` would cancel, or null once it is over. The shell holds the other end
     * (`DesktopShellState.liveDrag`): a shape is drawn from a drag, and a drag with no way back is a
     * gesture a keyboard cannot answer.
     */
    val onLiveDrag: ((() -> Unit)?) -> Unit = {},
)
