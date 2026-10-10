package com.mobixournal.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.vector.ImageVector
import com.mobixournal.format.model.Tool

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
}

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
)

private data class ToolInfo(val tool: EditorTool, val label: String, val icon: ImageVector)

private val TOOLS: List<ToolInfo> = listOf(
    ToolInfo(EditorTool.PEN, "Pen", Icons.Filled.Create),
    ToolInfo(EditorTool.HIGHLIGHTER, "Highlighter", Icons.Filled.Brush),
    ToolInfo(EditorTool.ERASER, "Eraser (partial)", Icons.Filled.Delete),
    ToolInfo(EditorTool.ERASER_WHOLE, "Eraser (whole stroke)", Icons.Filled.DeleteSweep),
    ToolInfo(EditorTool.LINE, "Line", Icons.Filled.HorizontalRule),
    ToolInfo(EditorTool.ARROW, "Arrow", Icons.Filled.ArrowRightAlt),
    ToolInfo(EditorTool.DOUBLE_ARROW, "Double arrow", Icons.Filled.SwapHoriz),
    ToolInfo(EditorTool.COORDINATE_AXIS, "Coordinate axis", Icons.Filled.ShowChart),
    ToolInfo(EditorTool.RECTANGLE, "Rectangle", RectangleIcon),
    ToolInfo(EditorTool.ELLIPSE, "Ellipse", Icons.Filled.RadioButtonUnchecked),
    ToolInfo(EditorTool.TRIANGLE, "Triangle", Icons.Filled.ChangeHistory),
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
    ToolInfo(EditorTool.SPLINE, "Spline", Icons.Filled.Gesture),
    ToolInfo(EditorTool.HAND, "Hand (pan)", Icons.Filled.PanTool),
    ToolInfo(EditorTool.SELECT, "Select rectangle", Icons.Filled.HighlightAlt),
    ToolInfo(EditorTool.LASSO_SELECT, "Select lasso", Icons.Filled.Polyline),
    ToolInfo(EditorTool.TEXT_SELECT, "Select text (PDF)", Icons.Filled.SelectAll),
    ToolInfo(EditorTool.BG_SELECT, "Select background (flatten)", Icons.Filled.Crop),
    ToolInfo(EditorTool.TEXT, "Text", Icons.Filled.TextFields),
    ToolInfo(EditorTool.IMAGE, "Image", Icons.Filled.Image),
    ToolInfo(EditorTool.TEXIMAGE, "LaTeX", Icons.Filled.Functions),
    ToolInfo(EditorTool.VERTICAL_SPACE, "Vertical space", Icons.Filled.SwapVert),
    ToolInfo(EditorTool.PLAY_OBJECT, "Play object", Icons.Filled.PlayCircleOutline),
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
