/**
 * What a **placement tap** is: the mode a slot is in ([PlaceKind]) and where its tap landed ([Placement]).
 *
 * Its own file because it is its own topic — the door a press opens (a text box, a picture, a formula) and the
 * answer that press carries back, which is what lets a slot **pick** the object it already made instead of
 * laying a second one on it (a text box, a formula: `pickMathBlock`). It used to sit at the top of
 * [DrawingSurfaceView], whose ceiling is a legacy one that may only shrink.
 */
package com.mobixournal.render

import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.TextElement

/** What a canvas tap places when a placement tool is active (see [DrawingSurfaceView.placeKind]). */
enum class PlaceKind { TEXT, IMAGE, TEX, PLOT }

/**
 * Where a placement tap landed: the page and its page-local pt coordinates. [existing] carries the
 * text box the tap hit (so the editor opens it for editing — prefilling its content, font, size and
 * colour — instead of creating a new one); null means "create new". [math] is the same answer for the
 * **LaTeX** slot: the formula under the tap, whose editor opens instead of a second formula being laid
 * on it (`pickMathBlock`).
 *
 * [bounds] is the **box a plot** takes from this press: the drag's own two corners, or the family's default size
 * centred on a tap ([LivingPlacement.bounds]) — the graph dialog's answer lands in it. A press on a plot the slot
 * already made never carries one: that press is the pick, which selects the plot on the way down
 * (`PlacementPick.beginPlacementPress`).
 */
data class Placement(
    val pageIndex: Int,
    val xPt: Double,
    val yPt: Double,
    val existing: TextElement? = null,
    val math: MathBlockElement? = null,
    val bounds: Bounds? = null,
)
