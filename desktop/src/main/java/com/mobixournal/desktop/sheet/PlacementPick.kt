package com.mobixournal.desktop.sheet

import com.mobixournal.desktop.ink.shapeKind
import com.mobixournal.desktop.tools.DesktopTool

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.ElementRef
import com.mobixournal.render.FigurePins
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ParametricShapes
import com.mobixournal.render.SelectionTester
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import kotlin.math.hypot

/**
 * What a press with a **placement slot** — a figure, a table, a circuit part, a plot, a formula — does with
 * the object of its own kind that lies under it: it *selects* it, and only paper that holds none takes a new
 * one.
 *
 * The tablet answered this first (`app/src/main/java/com/mobixournal/render/PlacementPick.kt`): a press on a
 * resistor with the resistor slot aimed to size it placed a second one on top, and a rectangle drawn over a
 * rectangle did the same. The window's slots stay armed after they place something too, so the press on the
 * sheet is how the next object is put down — and without this rule that press stacked the objects instead of
 * picking up the one already there. The press that picked the object is the one that then drags it, which is
 * what makes an element **inserted and then sized** one gesture and not three.
 *
 * Two vocabularies answer "the same object", and each is the model's own rather than a list written here: a
 * **living** element is matched by its family (a table is a table, any circuit part is a part, a plot is a
 * plot), while a **figure** was drawn as a stroke and names the kind the shape tool minted into it
 * ([FigurePins.isFigureOf]), so the triangle slot picks triangles and leaves a rectangle alone.
 *
 * A plot and a formula are picked here by their **own slot** (`LivingSlot.PLOT`, `LivingSlot.MATH`), because
 * the window keeps every placement slot in hand (there is no hand-over to Marquee any more): a slot that could
 * not pick its own object would stack a second one on the first click. The tablet answers the same two through
 * its **LaTeX** slot (`app/.../render/PlacementPick.kt`, `pickMathBlock`) and its Graph dialog; `docs/element-
 * authoring.md` owns how each shell reaches them.
 *
 * Pure page geometry, no Compose: the rule is a JVM test (`PlacementPickTest`).
 */

/** What a placement slot makes under a press. */
internal sealed interface Placement {

    /** A **figure** slot's stroke, matched by the kind it minted into the element. */
    data class Figure(val kind: ShapeKind) : Placement

    /** A **living** slot's element, matched by its family — the vocabulary the double-tap uses too. */
    data class Living(val slot: LivingSlot) : Placement
}

/**
 * The kind whose **pins** the sheet marks: every slot that places something with pins — a connector, a
 * figure, a table or a circuit part.
 *
 * A connector's marks are the targets its magnet takes, a drawn figure's are the ends it may be paired with,
 * and an armed slot shows them **before** the hand goes down (`FigurePins`), which is the tablet's own rule:
 * the marks used to arrive with the gesture, so an armed slot read as a sheet with no pins until the press had
 * already been made. A slot that places nothing with pins — the pen, the eraser, a plot — marks nothing.
 */
internal fun markKindFor(tool: DesktopTool, circuitKind: ShapeKind): ShapeKind? {
    val candidate = if (circuitFamilyOf(tool) != null) circuitKind else tool.shapeKind()
    return candidate?.takeIf { FigurePins.familyOf(it) != null }
}

/** What [tool] places, or null for a slot that inks, selects or reflows instead of placing an object. */
internal fun placementOf(tool: DesktopTool): Placement? =
    livingSlotOf(tool)?.let(Placement::Living) ?: tool.shapeKind()?.let(Placement::Figure)

/**
 * The topmost object [placement] makes under ([xPt], [yPt]) on [page], or null.
 *
 * A **figure written as several strokes** is never picked: a dimension is two halves around its gap and the
 * model gives the parts no identity to be gathered by, so selecting one half would select half an object —
 * the slot goes on drawing there, exactly as the tablet does.
 */
internal fun placementPick(
    page: Page,
    placement: Placement,
    xPt: Double,
    yPt: Double,
): ElementRef? = when (placement) {
    is Placement.Figure -> figurePick(page, placement.kind, xPt, yPt)
    // Every part of the palette is one family, so any circuit slot picks any part; a table, a plot and a
    // formula are their own family, each matched by its own element type.
    is Placement.Living -> SelectionTester.pickTopmost(page, xPt, yPt) { element ->
        when (placement.slot) {
            LivingSlot.TABLE -> element is TableElement
            LivingSlot.CIRCUIT -> element is CircuitElement
            LivingSlot.PLOT -> element is PlotElement
            LivingSlot.MATH -> element is MathBlockElement
        }
    }
}

/** The figure [kind]'s own stroke or living polygon under the press, or null — and never half of a multi-stroke figure. */
private fun figurePick(page: Page, kind: ShapeKind, xPt: Double, yPt: Double): ElementRef? {
    if (ShapeBuilder.isMultiStroke(kind)) return null
    val shapeName = ParametricShapes.shapeNameForTool(kind)
    return SelectionTester.pickTopmost(page, xPt, yPt) {
        (it is Stroke && FigurePins.isFigureOf(it, kind)) ||
            (shapeName != null && it is ParametricShapeElement && it.shape == shapeName)
    }
}

/**
 * The kind whose pins this placement's gesture anchors to, or null when there is nothing to aim at.
 *
 * A figure aims with its own kind and a circuit part with the part in hand; a table, a plot and a formula
 * carry no pins, so their press is never a pin press.
 */
internal fun Placement.pinKind(circuitKind: ShapeKind): ShapeKind? = when (this) {
    is Placement.Figure -> kind
    is Placement.Living -> if (slot == LivingSlot.CIRCUIT) circuitKind else null
}

/**
 * The **pin** a press at ([xPt], [yPt]) is aimed at, or null: the first of the targets [kind]'s gesture would
 * be anchored to ([CircuitSnapping.draftTargets], the very list the drag's magnet snaps to) within [reachPt].
 *
 * A pin is a **terminal**, not a piece of the object it belongs to: asking this before the pick is what lets a
 * press on a mark start the stroke *on* the pin — a wire leaves the terminal, a figure begins on a wire's end
 * — instead of selecting the object the mark sits on.
 */
internal fun pinUnderPress(
    page: Page,
    kind: ShapeKind,
    xPt: Double,
    yPt: Double,
    reachPt: Double,
): CircuitSnapping.PinTarget? = CircuitSnapping.draftTargets(kind, page)
    .firstOrNull { hypot(it.absX - xPt, it.absY - yPt) <= reachPt }
