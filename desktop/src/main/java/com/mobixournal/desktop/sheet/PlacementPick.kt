package com.mobixournal.desktop.sheet

import com.mobixournal.desktop.ink.shapeKind
import com.mobixournal.desktop.tools.DesktopTool

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.CircuitElementFactory
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.ElementRef
import com.mobixournal.render.FigurePins
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.SelectionTester
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import kotlin.math.hypot

/**
 * What a press with a **placement slot** — a figure, a table or a circuit part — does with the object of
 * its own kind that lies under it: it *selects* it, and only paper that holds none takes a new one.
 *
 * The tablet answered this first (`app/src/main/java/com/mobixournal/render/PlacementPick.kt`): a press on a
 * resistor with the resistor slot aimed to size it placed a second one on top, and a rectangle drawn over a
 * rectangle did the same. The window's slots stay armed after they place something too, so the press on the
 * sheet is how the next object is put down — and without this rule that press stacked the figures instead of
 * picking up the one already there. The press that picked the object is the one that then drags it, which is
 * what makes an element **inserted and then sized** one gesture and not three.
 *
 * Two vocabularies answer "the same object", and each is the model's own rather than a list written here: a
 * **living** element is matched by its family (a table is a table, any circuit part is a part), while a
 * **figure** was drawn as a stroke and names the kind the shape tool minted into it
 * ([FigurePins.isFigureOf]), so the triangle slot picks triangles and leaves a rectangle alone.
 *
 * Pure page geometry, no Compose: the rule is a JVM test (`PlacementPickTest`).
 */

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

/** The shape [tool] places, or null for a slot that inks, selects, reflows or places another recipe. */
internal fun placementKindOf(tool: DesktopTool, circuitKind: ShapeKind): ShapeKind? =
    when (livingSlotOf(tool)) {
        LivingSlot.TABLE -> ShapeKind.TABLE
        // Every circuit palette stands for the same family, so any part slot picks any part.
        LivingSlot.CIRCUIT -> circuitKind
        // A plot and a formula are recipes of their own, not a figure a press can pick by kind.
        LivingSlot.PLOT, LivingSlot.MATH -> null
        null -> tool.shapeKind()
    }

/**
 * The topmost object [kind] makes under ([xPt], [yPt]) on [page], or null.
 *
 * A **figure written as several strokes** is never picked: a dimension is two halves around its gap and the
 * model gives the parts no identity to be gathered by, so selecting one half would select half an object —
 * the slot goes on drawing there, exactly as the tablet does.
 */
internal fun placementPick(page: Page, kind: ShapeKind, xPt: Double, yPt: Double): ElementRef? = when {
    kind == ShapeKind.TABLE -> SelectionTester.pickTopmost(page, xPt, yPt) { it is TableElement }
    CircuitElementFactory.isCircuitKind(kind) ->
        SelectionTester.pickTopmost(page, xPt, yPt) { it is CircuitElement }
    ShapeBuilder.isMultiStroke(kind) -> null
    else -> SelectionTester.pickTopmost(page, xPt, yPt) { it is Stroke && FigurePins.isFigureOf(it, kind) }
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
