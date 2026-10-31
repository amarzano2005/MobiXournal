package com.mobixournal.desktop

import com.mobixournal.design.ToolGlyph
import com.mobixournal.design.toolGlyph
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind

/**
 * What the strips know about the **figures**: the variants they will draw, and the one figure whose
 * menu is up.
 *
 * One value rather than four parameters, for the reason the ink is one: the rail and the ribbon are two
 * layouts of the same slot, so a menu opened in one must be the same menu in the other, and a variant
 * picked there must re-face both at once ([FigureShape]).
 */
data class FigureStrip(
    /** The triangle's and trapezoid's variants and the polygon's side count, as the drag reads them. */
    val shape: FigureShape = FigureShape(),
    /** The figure whose variant menu is open (`DesktopTool.TRIANGLE`, …), or null when none is. */
    val menu: DesktopTool? = null,
    /** Open one figure's menu, or close the open one with null. */
    val onMenu: (DesktopTool?) -> Unit = {},
    /** Pick a variant (or a side count); the menu closes, because the question is answered. */
    val onShape: (FigureShape) -> Unit = {},
    /** The figure whose angle customization dialog is active (`TRIANGLE`, `TRAPEZOID`), or null when none is. */
    val editingFigure: DesktopTool? = null,
    /** Open a figure's angle customization dialog. */
    val onOpenDialog: (DesktopTool) -> Unit = {},
    /** Close the angle customization dialog. */
    val onCloseDialog: () -> Unit = {},
)

/** Whether [tool] is currently configured as a scalene variant with customizable angles. */
internal fun FigureStrip.isScalene(tool: DesktopTool): Boolean =
    (tool == DesktopTool.TRIANGLE && shape.triangleKind == TriangleKind.SCALENE) ||
    (tool == DesktopTool.TRAPEZOID && shape.trapezoidKind == TrapezoidKind.SCALENE)

/**
 * One answer of a figure's menu: a triangle's kind, a trapezoid's kind, or the polygon's side count.
 *
 * [shape] is the whole [FigureShape] the pick writes, not a field of it, so a cell carries no second
 * idea of what picking it means — the drag and the menu read the same value.
 */
internal data class FigureOption(
    val label: String,
    /** The catalogue glyph beside the name, or null for the polygon, whose own count draws itself. */
    val glyph: ToolGlyph?,
    val shape: FigureShape,
    val selected: Boolean,
)

/**
 * The menu of a figure slot, in the order the tablet's own submenu lists it — or null for a slot that
 * is not a figure.
 *
 * The triangle and trapezoid entries are their kinds, each with the catalogue's picture of that kind;
 * the polygon's are its side counts, from [ShapeBuilder.MIN_POLYGON_SIDES] to
 * [ShapeBuilder.MAX_POLYGON_SIDES] — the same range the builder clamps to, so a cell always names a
 * figure the page can draw.
 */
internal fun figureMenu(tool: DesktopTool, shape: FigureShape): List<FigureOption>? = when (tool) {
    DesktopTool.TRIANGLE -> TriangleKind.entries.map { kind ->
        FigureOption(
            label = kind.label,
            glyph = kind.toolGlyph,
            shape = shape.copy(triangleKind = kind),
            selected = kind == shape.triangleKind,
        )
    }

    DesktopTool.TRAPEZOID -> TrapezoidKind.entries.map { kind ->
        FigureOption(
            label = kind.label,
            glyph = kind.toolGlyph,
            shape = shape.copy(trapezoidKind = kind),
            selected = kind == shape.trapezoidKind,
        )
    }

    DesktopTool.POLYGON -> (ShapeBuilder.MIN_POLYGON_SIDES..ShapeBuilder.MAX_POLYGON_SIDES).map { sides ->
        FigureOption(
            label = "$sides",
            glyph = null,
            shape = shape.withPolygonSides(sides),
            selected = sides == shape.polygonSides,
        )
    }

    else -> null
}

/** The heading over [tool]'s menu — the figure's name, as the tablet's variant pickers print it. */
internal fun figureMenuTitle(tool: DesktopTool): String = when (tool) {
    DesktopTool.TRIANGLE -> "Triangle"
    DesktopTool.TRAPEZOID -> "Trapezoid"
    DesktopTool.POLYGON -> "Polygon sides"
    else -> ""
}

/**
 * How many cells a row of [tool]'s menu holds: the kinds read two to a row with their names, while the
 * polygon's ten counts are numerals and fit five, so the picker stays a square rather than a column.
 */
internal fun figureMenuRowSize(tool: DesktopTool): Int =
    if (tool == DesktopTool.POLYGON) 5 else 2

/**
 * The glyph a figure slot wears: **the variant it will draw**, not the family's generic picture.
 *
 * Choosing a right-angled triangle and still seeing an equilateral picture on the slot is the defect
 * this answers — the tablet draws the chosen kind on its own button for the same reason.
 */
internal fun figureSlotGlyph(tool: DesktopTool, shape: FigureShape): ToolGlyph = when (tool) {
    DesktopTool.TRIANGLE -> shape.triangleKind.toolGlyph
    DesktopTool.TRAPEZOID -> shape.trapezoidKind.toolGlyph
    else -> tool.glyph
}
