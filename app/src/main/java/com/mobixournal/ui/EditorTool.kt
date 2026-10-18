package com.mobixournal.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ChangeHistory
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Polyline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.ui.graphics.vector.ImageVector
import com.mobixournal.format.model.Tool
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind

/**
 * The editor's interaction modes. PEN/HIGHLIGHTER/ERASER map to the document [Tool]; HAND is a
 * view-only pan mode; SELECT rubber-band-selects objects to move/delete them; TEXT/IMAGE/TEXIMAGE
 * are authoring modes where a canvas tap places (or edits) that element (see
 * [com.mobixournal.render.PlaceKind]).
 */
enum class EditorTool {
    PEN, HIGHLIGHTER, ERASER, ERASER_WHOLE, HAND, SELECT, LASSO_SELECT, TEXT_SELECT, BG_SELECT,
    TEXT, IMAGE, TEXIMAGE,
    LINE, ARROW, DOUBLE_ARROW, COORDINATE_AXIS, RECTANGLE, ELLIPSE, SPLINE, VERTICAL_SPACE,
    PLAY_OBJECT,
    // The STEM figures (see [ShapeKind]): regular polygons, a square, a rhombus and a trapezoid.
    TRIANGLE, SQUARE, RHOMBUS, TRAPEZOID, PENTAGON, HEXAGON,
    TABLE,
    // Electronic circuits & logic gates
    RESISTOR, CAPACITOR, INDUCTOR, GROUND,
    DIODE, LED, ZENER_DIODE, OPAMP, BJT_NPN, BJT_PNP, DC_SOURCE, CURRENT_SOURCE,
    AND_GATE, OR_GATE, NOT_GATE,
    NAND_GATE, NOR_GATE, XOR_GATE, XNOR_GATE, BUFFER_GATE,
    SWITCH_OPEN, SWITCH_CLOSED, TRANSFORMER, JUNCTION,
    // Technical dimensioning.
    DIMENSION,
    // Chemistry: the bonds of a skeletal formula and an aromatic ring (see [ChemShapes]).
    CHEM_BOND_SINGLE, CHEM_BOND_DOUBLE, CHEM_BOND_TRIPLE, CHEM_BENZENE,
}

/**
 * The marquee tools — rectangle select, lasso and background select. All three drag out a region and
 * can act on elements, so they are the tools under which a paste (which lands a fresh selection) makes
 * sense to offer.
 */
val MARQUEE_TOOLS: Set<EditorTool> = setOf(EditorTool.SELECT, EditorTool.LASSO_SELECT, EditorTool.BG_SELECT)

/** The geometric shape tools — drawn as ordinary pen strokes (see [ShapeKind]). */
val SHAPE_TOOLS: List<EditorTool> = listOf(
    EditorTool.LINE, EditorTool.ARROW, EditorTool.DOUBLE_ARROW, EditorTool.COORDINATE_AXIS,
    EditorTool.RECTANGLE, EditorTool.ELLIPSE, EditorTool.SPLINE,
    EditorTool.TRIANGLE, EditorTool.SQUARE, EditorTool.RHOMBUS, EditorTool.TRAPEZOID,
    EditorTool.PENTAGON, EditorTool.HEXAGON,
    EditorTool.TABLE,
    EditorTool.RESISTOR, EditorTool.CAPACITOR, EditorTool.INDUCTOR, EditorTool.GROUND,
    EditorTool.DIODE, EditorTool.LED, EditorTool.ZENER_DIODE,
    EditorTool.OPAMP, EditorTool.BJT_NPN, EditorTool.BJT_PNP,
    EditorTool.DC_SOURCE, EditorTool.CURRENT_SOURCE,
    EditorTool.AND_GATE, EditorTool.OR_GATE, EditorTool.NOT_GATE,
    EditorTool.NAND_GATE, EditorTool.NOR_GATE, EditorTool.XOR_GATE, EditorTool.XNOR_GATE,
    EditorTool.BUFFER_GATE,
    EditorTool.SWITCH_OPEN, EditorTool.SWITCH_CLOSED, EditorTool.TRANSFORMER, EditorTool.JUNCTION,
    EditorTool.DIMENSION,
    EditorTool.CHEM_BOND_SINGLE, EditorTool.CHEM_BOND_DOUBLE, EditorTool.CHEM_BOND_TRIPLE,
    EditorTool.CHEM_BENZENE,
)

private data class ToolInfo(val tool: EditorTool, val label: String, val icon: ImageVector)

private val TOOLS: List<ToolInfo> = listOf(
    ToolInfo(EditorTool.PEN, "Pen", Icons.Outlined.Create),
    ToolInfo(EditorTool.HIGHLIGHTER, "Highlighter", Icons.Outlined.Brush),
    ToolInfo(EditorTool.ERASER, "Eraser (partial)", Icons.Outlined.Delete),
    ToolInfo(EditorTool.ERASER_WHOLE, "Eraser (whole stroke)", Icons.Outlined.DeleteSweep),
    ToolInfo(EditorTool.LINE, "Line", Icons.Outlined.HorizontalRule),
    ToolInfo(EditorTool.ARROW, "Arrow", Icons.AutoMirrored.Outlined.ArrowRightAlt),
    ToolInfo(EditorTool.DOUBLE_ARROW, "Double arrow", Icons.Outlined.SwapHoriz),
    ToolInfo(EditorTool.COORDINATE_AXIS, "Coordinate axis", CoordinateAxisIcon),
    ToolInfo(EditorTool.RECTANGLE, "Rectangle", RectangleIcon),
    ToolInfo(EditorTool.ELLIPSE, "Ellipse", Icons.Outlined.RadioButtonUnchecked),
    ToolInfo(EditorTool.TRIANGLE, "Triangle", Icons.Outlined.ChangeHistory),
    ToolInfo(EditorTool.SQUARE, "Square", SquareIcon),
    ToolInfo(EditorTool.RHOMBUS, "Rhombus", RhombusIcon),
    ToolInfo(EditorTool.TRAPEZOID, "Trapezoid", TrapezoidIcon),
    ToolInfo(EditorTool.PENTAGON, "Pentagon", PentagonIcon),
    ToolInfo(EditorTool.HEXAGON, "Hexagon", HexagonIcon),
    ToolInfo(EditorTool.TABLE, "Table", TableIcon),
    ToolInfo(EditorTool.RESISTOR, "Resistor", ResistorIcon),
    ToolInfo(EditorTool.CAPACITOR, "Capacitor", CapacitorIcon),
    ToolInfo(EditorTool.INDUCTOR, "Inductor", InductorIcon),
    ToolInfo(EditorTool.GROUND, "Ground", GroundIcon),
    ToolInfo(EditorTool.DIODE, "Diode", DiodeIcon),
    ToolInfo(EditorTool.LED, "LED", LedIcon),
    ToolInfo(EditorTool.ZENER_DIODE, "Zener diode", ZenerDiodeIcon),
    ToolInfo(EditorTool.OPAMP, "Op-Amp", OpAmpIcon),
    ToolInfo(EditorTool.BJT_NPN, "BJT NPN", BjtNpnIcon),
    ToolInfo(EditorTool.BJT_PNP, "BJT PNP", BjtPnpIcon),
    ToolInfo(EditorTool.DC_SOURCE, "DC voltage source", DcSourceIcon),
    ToolInfo(EditorTool.CURRENT_SOURCE, "Current source", CurrentSourceIcon),
    ToolInfo(EditorTool.AND_GATE, "AND Gate", AndGateIcon),
    ToolInfo(EditorTool.OR_GATE, "OR Gate", OrGateIcon),
    ToolInfo(EditorTool.NOT_GATE, "NOT Gate", NotGateIcon),
    ToolInfo(EditorTool.NAND_GATE, "NAND Gate", NandGateIcon),
    ToolInfo(EditorTool.NOR_GATE, "NOR Gate", NorGateIcon),
    ToolInfo(EditorTool.XOR_GATE, "XOR Gate", XorGateIcon),
    ToolInfo(EditorTool.XNOR_GATE, "XNOR Gate", XnorGateIcon),
    ToolInfo(EditorTool.BUFFER_GATE, "Buffer", BufferGateIcon),
    ToolInfo(EditorTool.SWITCH_OPEN, "Switch (open)", SwitchOpenIcon),
    ToolInfo(EditorTool.SWITCH_CLOSED, "Switch (closed)", SwitchClosedIcon),
    ToolInfo(EditorTool.TRANSFORMER, "Transformer", TransformerIcon),
    ToolInfo(EditorTool.JUNCTION, "Junction dot", JunctionDotIcon),
    ToolInfo(EditorTool.DIMENSION, "Dimension arrow", DimensionIcon),
    ToolInfo(EditorTool.CHEM_BOND_SINGLE, "Single bond", BondSingleIcon),
    ToolInfo(EditorTool.CHEM_BOND_DOUBLE, "Double bond", BondDoubleIcon),
    ToolInfo(EditorTool.CHEM_BOND_TRIPLE, "Triple bond", BondTripleIcon),
    ToolInfo(EditorTool.CHEM_BENZENE, "Benzene ring", BenzeneIcon),
    ToolInfo(EditorTool.SPLINE, "Spline", Icons.Outlined.Gesture),
    ToolInfo(EditorTool.HAND, "Hand (pan)", Icons.Outlined.PanTool),
    ToolInfo(EditorTool.SELECT, "Select rectangle", Icons.Outlined.HighlightAlt),
    ToolInfo(EditorTool.LASSO_SELECT, "Select lasso", Icons.Outlined.Polyline),
    ToolInfo(EditorTool.TEXT_SELECT, "Select text (PDF)", Icons.Outlined.SelectAll),
    ToolInfo(EditorTool.BG_SELECT, "Select background (flatten)", Icons.Outlined.Crop),
    ToolInfo(EditorTool.TEXT, "Text", Icons.Outlined.TextFields),
    ToolInfo(EditorTool.IMAGE, "Image", Icons.Outlined.Image),
    ToolInfo(EditorTool.TEXIMAGE, "LaTeX", Icons.Outlined.Functions),
    ToolInfo(EditorTool.VERTICAL_SPACE, "Vertical space", Icons.Outlined.SwapVert),
    ToolInfo(EditorTool.PLAY_OBJECT, "Play object", Icons.Outlined.GraphicEq),
)

/** Human-readable label for a tool (the same text the rail's tool menu shows). */
val EditorTool.label: String get() = TOOLS.first { it.tool == this }.label

/** The rail icon for a tool. */
val EditorTool.icon: ImageVector get() = TOOLS.first { it.tool == this }.icon

/**
 * The tools that make sense to *start* a document in, offered by the "Default tool" setting. The
 * place-modes (TEXT/IMAGE/TEXIMAGE) and SELECT aren't here: opening straight into them would strand
 * the user in a mode with nothing to act on, so a default is one of the four drawing/pan tools.
 */
val DEFAULT_TOOL_CHOICES: List<EditorTool> =
    listOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.HAND)

/**
 * Resolves the display icon for [tool], taking the specific geometric variant into account when the
 * tool is [EditorTool.TRIANGLE] or [EditorTool.TRAPEZOID].
 */
fun toolDisplayIcon(
    tool: EditorTool,
    triangleKind: TriangleKind = TriangleKind.ISOSCELES,
    trapezoidKind: TrapezoidKind = TrapezoidKind.ISOSCELES,
): ImageVector = when (tool) {
    EditorTool.TRIANGLE -> triangleKindIcon(triangleKind)
    EditorTool.TRAPEZOID -> trapezoidKindIcon(trapezoidKind)
    else -> tool.icon
}
