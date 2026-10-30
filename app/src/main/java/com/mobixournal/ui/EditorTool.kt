package com.mobixournal.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.mobixournal.design.ToolGlyph
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
    LINE, ARROW, DOUBLE_ARROW, COORDINATE_AXIS, RECTANGLE, ELLIPSE, VERTICAL_SPACE,
    PLAY_OBJECT,
    // The STEM figures (see [ShapeKind]): a triangle, a rhombus, a trapezoid, the generic polygon
    // (any number of sides, set under Settings ▸ Figures) and the two regular presets. Square
    // survives as an enum entry (a saved tool selection may still name it) but no toolbar offers it
    // any more — the rectangle is the square.
    TRIANGLE, SQUARE, RHOMBUS, TRAPEZOID, PENTAGON, HEXAGON, POLYGON,
    TABLE,
    // Electronic circuits & logic gates
    RESISTOR, CAPACITOR, INDUCTOR, GROUND,
    DIODE, LED, ZENER_DIODE, OPAMP, BJT_NPN, BJT_PNP, DC_SOURCE, CURRENT_SOURCE,
    AND_GATE, OR_GATE, NOT_GATE,
    NAND_GATE, NOR_GATE, XOR_GATE, XNOR_GATE, BUFFER_GATE,
    SWITCH_OPEN, SWITCH_CLOSED, TRANSFORMER, JUNCTION,
    // Technical dimensioning.
    DIMENSION,
    // Chemistry: the bonds of a skeletal formula and an aromatic ring (see [ChemShapes]). The
    // section that offered them is gone; the entries stay so a shortcut table or a saved selection
    // written before that can still be decoded instead of dropping a key.
    CHEM_BOND_SINGLE, CHEM_BOND_DOUBLE, CHEM_BOND_TRIPLE, CHEM_BENZENE,
}

/**
 * The marquee tools — rectangle select, lasso and background select. All three drag out a region and
 * can act on elements, so they are the tools under which a paste (which lands a fresh selection) makes
 * sense to offer.
 */
val MARQUEE_TOOLS: Set<EditorTool> = setOf(EditorTool.SELECT, EditorTool.LASSO_SELECT, EditorTool.BG_SELECT)

/**
 * The two erasers — the tools the eraser switch leaves and comes back from.
 *
 * They are one family rather than one tool because the rail's slot can stand for either; a toggle that
 * only recognised the partial eraser would send a whole-stroke eraser to the pen instead of back to the
 * tool the user was holding.
 */
val ERASER_TOOLS: Set<EditorTool> = setOf(EditorTool.ERASER, EditorTool.ERASER_WHOLE)

/**
 * The geometric shape tools — drawn as ordinary pen strokes (see [ShapeKind]).
 *
 * A square is not among them: the rectangle covers it, and the slot it took only made the Secondary
 * Toolbar longer. The chemistry bonds and the benzene ring are not either — the section that offered
 * them is gone, so nothing can put them in hand; their [ShapeKind]s stay, because a document may still
 * carry the strokes they drew.
 */
val SHAPE_TOOLS: List<EditorTool> = listOf(
    EditorTool.LINE, EditorTool.ARROW, EditorTool.DOUBLE_ARROW, EditorTool.COORDINATE_AXIS,
    EditorTool.RECTANGLE, EditorTool.ELLIPSE,
    EditorTool.TRIANGLE, EditorTool.RHOMBUS, EditorTool.TRAPEZOID,
    EditorTool.POLYGON, EditorTool.PENTAGON, EditorTool.HEXAGON,
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

/**
 * One tool's name and picture.
 *
 * [glyph] is the shared catalogue entry (`ToolGlyphCatalogue`, `mobix-core`) and it is **required**:
 * every tool, including the members a STEM picker offers, draws the same picture on the desktop as on
 * the tablet. There is no second source left to fall back to — the vectors this table used to name
 * were transcribed into the catalogue and deleted.
 */
private data class ToolInfo(
    val tool: EditorTool,
    val label: String,
    val glyph: ToolGlyph,
)

private val TOOLS: List<ToolInfo> = listOf(
    ToolInfo(EditorTool.PEN, "Pen", glyph = ToolGlyph.PEN),
    ToolInfo(EditorTool.HIGHLIGHTER, "Highlighter", glyph = ToolGlyph.HIGHLIGHTER),
    ToolInfo(EditorTool.ERASER, "Eraser (partial)", glyph = ToolGlyph.ERASER),
    ToolInfo(EditorTool.ERASER_WHOLE, "Eraser (whole stroke)", glyph = ToolGlyph.ERASER_WHOLE),
    ToolInfo(EditorTool.LINE, "Line", glyph = ToolGlyph.LINE),
    ToolInfo(EditorTool.ARROW, "Arrow", glyph = ToolGlyph.ARROW),
    ToolInfo(EditorTool.DOUBLE_ARROW, "Double arrow", glyph = ToolGlyph.DOUBLE_ARROW),
    ToolInfo(EditorTool.COORDINATE_AXIS, "Coordinate axis", glyph = ToolGlyph.AXES),
    ToolInfo(EditorTool.RECTANGLE, "Rectangle", glyph = ToolGlyph.RECTANGLE),
    ToolInfo(EditorTool.ELLIPSE, "Ellipse", glyph = ToolGlyph.ELLIPSE),
    ToolInfo(EditorTool.TRIANGLE, "Triangle", glyph = ToolGlyph.TRIANGLE),
    ToolInfo(EditorTool.SQUARE, "Square", glyph = ToolGlyph.SQUARE),
    ToolInfo(EditorTool.RHOMBUS, "Rhombus", glyph = ToolGlyph.RHOMBUS),
    ToolInfo(EditorTool.TRAPEZOID, "Trapezoid", glyph = ToolGlyph.TRAPEZOID),
    ToolInfo(EditorTool.PENTAGON, "Pentagon", glyph = ToolGlyph.PENTAGON),
    ToolInfo(EditorTool.HEXAGON, "Hexagon", glyph = ToolGlyph.HEXAGON),
    ToolInfo(EditorTool.POLYGON, "Polygon", glyph = ToolGlyph.POLYGON),
    ToolInfo(EditorTool.TABLE, "Table", glyph = ToolGlyph.TABLE),
    ToolInfo(EditorTool.RESISTOR, "Resistor", glyph = ToolGlyph.RESISTOR),
    ToolInfo(EditorTool.CAPACITOR, "Capacitor", glyph = ToolGlyph.CAPACITOR),
    ToolInfo(EditorTool.INDUCTOR, "Inductor", glyph = ToolGlyph.INDUCTOR),
    ToolInfo(EditorTool.GROUND, "Ground", glyph = ToolGlyph.GROUND),
    ToolInfo(EditorTool.DIODE, "Diode", glyph = ToolGlyph.DIODE),
    ToolInfo(EditorTool.LED, "LED", glyph = ToolGlyph.LED),
    ToolInfo(EditorTool.ZENER_DIODE, "Zener diode", glyph = ToolGlyph.ZENER_DIODE),
    ToolInfo(EditorTool.OPAMP, "Op-Amp", glyph = ToolGlyph.OPAMP),
    ToolInfo(EditorTool.BJT_NPN, "BJT NPN", glyph = ToolGlyph.BJT_NPN),
    ToolInfo(EditorTool.BJT_PNP, "BJT PNP", glyph = ToolGlyph.BJT_PNP),
    ToolInfo(EditorTool.DC_SOURCE, "DC voltage source", glyph = ToolGlyph.DC_SOURCE),
    ToolInfo(EditorTool.CURRENT_SOURCE, "Current source", glyph = ToolGlyph.CURRENT_SOURCE),
    ToolInfo(EditorTool.AND_GATE, "AND Gate", glyph = ToolGlyph.AND_GATE),
    ToolInfo(EditorTool.OR_GATE, "OR Gate", glyph = ToolGlyph.OR_GATE),
    ToolInfo(EditorTool.NOT_GATE, "NOT Gate", glyph = ToolGlyph.NOT_GATE),
    ToolInfo(EditorTool.NAND_GATE, "NAND Gate", glyph = ToolGlyph.NAND_GATE),
    ToolInfo(EditorTool.NOR_GATE, "NOR Gate", glyph = ToolGlyph.NOR_GATE),
    ToolInfo(EditorTool.XOR_GATE, "XOR Gate", glyph = ToolGlyph.XOR_GATE),
    ToolInfo(EditorTool.XNOR_GATE, "XNOR Gate", glyph = ToolGlyph.XNOR_GATE),
    ToolInfo(EditorTool.BUFFER_GATE, "Buffer", glyph = ToolGlyph.BUFFER_GATE),
    ToolInfo(EditorTool.SWITCH_OPEN, "Switch (open)", glyph = ToolGlyph.SWITCH_OPEN),
    ToolInfo(EditorTool.SWITCH_CLOSED, "Switch (closed)", glyph = ToolGlyph.SWITCH_CLOSED),
    ToolInfo(EditorTool.TRANSFORMER, "Transformer", glyph = ToolGlyph.TRANSFORMER),
    ToolInfo(EditorTool.JUNCTION, "Junction dot", glyph = ToolGlyph.JUNCTION),
    ToolInfo(EditorTool.DIMENSION, "Dimension arrow", glyph = ToolGlyph.DIMENSION),
    ToolInfo(EditorTool.CHEM_BOND_SINGLE, "Single bond", glyph = ToolGlyph.CHEM_BOND_SINGLE),
    ToolInfo(EditorTool.CHEM_BOND_DOUBLE, "Double bond", glyph = ToolGlyph.CHEM_BOND_DOUBLE),
    ToolInfo(EditorTool.CHEM_BOND_TRIPLE, "Triple bond", glyph = ToolGlyph.CHEM_BOND_TRIPLE),
    ToolInfo(EditorTool.CHEM_BENZENE, "Benzene ring", glyph = ToolGlyph.BENZENE),
    ToolInfo(EditorTool.HAND, "Hand (pan)", glyph = ToolGlyph.HAND),
    ToolInfo(EditorTool.SELECT, "Select rectangle", glyph = ToolGlyph.MARQUEE),
    ToolInfo(EditorTool.LASSO_SELECT, "Select lasso", glyph = ToolGlyph.LASSO),
    ToolInfo(EditorTool.TEXT_SELECT, "Select text (PDF)", glyph = ToolGlyph.SELECT_TEXT),
    ToolInfo(EditorTool.BG_SELECT, "Select background (flatten)", glyph = ToolGlyph.SELECT_BG),
    ToolInfo(EditorTool.TEXT, "Text", glyph = ToolGlyph.TEXT),
    ToolInfo(EditorTool.IMAGE, "Image", glyph = ToolGlyph.IMAGE),
    ToolInfo(EditorTool.TEXIMAGE, "LaTeX", glyph = ToolGlyph.TEXIMAGE),
    ToolInfo(EditorTool.VERTICAL_SPACE, "Vertical space", glyph = ToolGlyph.VSPACE),
    ToolInfo(EditorTool.PLAY_OBJECT, "Play object", glyph = ToolGlyph.PLAY),
)

/** Human-readable label for a tool (the same text the rail's tool menu shows). */
val EditorTool.label: String get() = TOOLS.first { it.tool == this }.label

/**
 * The shared-catalogue glyph for this tool. Non-null by construction: the coverage test reads this to
 * hold every tool to the catalogue, which is what keeps the two shells' toolbars one family.
 */
fun EditorTool.catalogueGlyph(): ToolGlyph = TOOLS.first { it.tool == this }.glyph

/**
 * The rail icon for a tool: always the shared catalogue's picture. The tablet's own vectors are gone —
 * the deeper circuit, gate and bond members were transcribed into the catalogue — so `icon` and the
 * window's `drawToolGlyph` cannot disagree about any tool.
 */
val EditorTool.icon: ImageVector
    get() = TOOLS.first { it.tool == this }.glyph.imageVector()

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
    // The chosen variant, not the generic figure: the slot face shows the shape the tool will draw.
    EditorTool.TRIANGLE -> triangleKindIcon(triangleKind)
    EditorTool.TRAPEZOID -> trapezoidKindIcon(trapezoidKind)
    else -> tool.icon
}
