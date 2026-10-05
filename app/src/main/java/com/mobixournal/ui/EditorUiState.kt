package com.mobixournal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobixournal.format.FontDescription
import com.mobixournal.format.model.LineStyle
import com.mobixournal.render.Placement

/** Default point size for a newly authored text box, and the slider bounds for editing it. */
const val TEXT_SIZE_PT = 12.0
const val TEXT_SIZE_MIN = 6f
const val TEXT_SIZE_MAX = 96f

/**
 * The styling a *new* text box starts from. An edit of an existing box seeds from the element
 * instead; confirming a brand-new box writes its choices back here so the next one matches.
 */
class TextDefaults {
    /** Font family name for new text boxes (e.g. "Sans", "Serif"). */
    var family by mutableStateOf(FontDescription.DEFAULT_FAMILY)
    /** Whether new text boxes are bold. */
    var bold by mutableStateOf(false)
    /** Whether new text boxes are italic. */
    var italic by mutableStateOf(false)
    /** Font size in points for new text boxes. */
    var size by mutableStateOf(TEXT_SIZE_PT)
    /** Font colour (opaque ARGB) for new text boxes. */
    var color by mutableStateOf(PEN_COLORS.first())
}

/**
 * Everything [EditorScreen] remembers that isn't a mirror of a canvas (that's [PaneState]) and isn't
 * persisted (that's [AppSettings]): the live pen, which dialogs are open, and the pending authoring
 * placement.
 *
 * It exists so the screen's regions can be separate composables — each takes this one holder rather
 * than a dozen values and setters — and so the read of a single flag only recomposes the region that
 * reads it.
 */
class EditorUiState(tool: EditorTool, color: Int, width: Float, highlighterColorInit: Int,
                    highlighterWidthInit: Float,) {
    /** The live pen: the rail's selected tool and the colour/width/style pushed onto the surface. */
    var tool by mutableStateOf(tool)
    /** Current pen colour (opaque ARGB). */
    var color by mutableStateOf(color)
    /** Current pen width in points. */
    var width by mutableStateOf(width)
    /** Current line style (plain/dashed/dash-dot/dotted). */
    var lineStyle by mutableStateOf(LineStyle.PLAIN)
    /** Highlighter colour (opaque ARGB) — independent from pen colour. */
    var highlighterColor by mutableStateOf(highlighterColorInit)
    /** Highlighter width in points — independent from pen width. */
    var highlighterWidth by mutableStateOf(highlighterWidthInit)

    // Dialog / overlay visibility.
    /** Whether the Settings screen is showing. */
    var showSettings by mutableStateOf(false)
    /** Whether the Save As dialog is showing. */
    var showSaveAs by mutableStateOf(false)
    /** Whether the Import PDF dialog is showing. */
    var showImportPdf by mutableStateOf(false)

    /** Full-page (immersive) view: a Hand-tool centre double-tap hides the top bar and side toolbar. */
    var fullPage by mutableStateOf(false)

    /**
     * Whether the live **pen diagnostics** panel is showing over the canvas: the raw stylus stream
     * (tool type, buttons, key codes, input devices) as the app receives it, for working out what a
     * particular pen actually sends. A debugging aid, not a preference — so it is deliberately not
     * persisted and starts off on every launch.
     */
    var penDiagnostics by mutableStateOf(false)

    /** Where the split bar sits, as the left pane's share of the width. Dragged, not persisted. */
    var splitFraction by mutableStateOf(0.5f)

    // An authoring tap is waiting on its dialog: where the text / LaTeX element goes.
    /** Pending text box placement, or null if none. */
    var textPlacement by mutableStateOf<Placement?>(null)
    /** Pending LaTeX image placement, or null if none. */
    var texPlacement by mutableStateOf<Placement?>(null)

    /** Defaults for new text boxes; updated when a new box is confirmed. */
    val textDefaults = TextDefaults()

    /** One mirror of canvas state per pane; the chrome reads whichever pane has focus. */
    val panes = List(PANE_COUNT) { PaneState() }

    /**
     * Switch to [target], or back to the tool that was live before if [target] is already selected.
     * Used by the barrel double-click bindings, where the same gesture has to toggle both ways.
     */
    fun toggleTool(target: EditorTool) {
        if (tool == target) {
            tool = toolBeforeToggle ?: EditorTool.PEN
            toolBeforeToggle = null
        } else {
            toolBeforeToggle = tool
            tool = target
        }
    }

    private var toolBeforeToggle: EditorTool? = null

    /**
     * Make [target] the live tool, carrying each tool's own colour and width across: the single
     * rule every tool switch follows — rail slot, radial palette, keyboard shortcut, barrel button.
     *
     * The outgoing tool's style goes into its own slot first and [target]'s is restored from the
     * result, so both halves read the same settings object and no path can leave the pen holding
     * the highlighter's or a figure's width (the bug behind "switch to the pen and it's fat too").
     * [previous] defaults to the tool currently live; pass it when the caller has already moved
     * [tool], which is what the toggle paths do.
     *
     * @return the settings to persist — the outgoing tool's style written back into its slot.
     */
    fun switchToolTo(target: EditorTool, settings: AppSettings, previous: EditorTool = tool): AppSettings {
        val saved = when (previous) {
            EditorTool.HIGHLIGHTER -> {
                // The live fields and the persisted ones move together: a highlighter colour picked
                // from the radial palette only reaches one of them, and this keeps both honest.
                highlighterColor = color
                highlighterWidth = width
                settings.copy(highlighterColor = color, highlighterWidth = width)
            }
            EditorTool.PEN -> settings.copy(lastColor = color, lastWidth = width).withColorUsed(color)
            else -> if (previous in SHAPE_TOOLS) settings.copy(shapeWidth = width) else settings
        }
        tool = target
        when {
            target == EditorTool.HIGHLIGHTER -> {
                color = highlighterColor
                width = highlighterWidth
            }
            target == EditorTool.PEN -> {
                color = saved.lastColor
                width = saved.lastWidth
            }
            // Between figures the width stays in sync with the unified figure slot; arriving
            // from a non-figure adopts the figure slot's width.
            target in SHAPE_TOOLS -> width = saved.shapeWidth
            // Eraser/hand/select/insert don't ink: they keep the live pen's colour and width, which
            // is what they hand back when the pen is picked again.
            else -> Unit
        }
        return saved
    }

    /** The pane the toolbar and overlays drive. */
    fun pane(active: Int): PaneState = panes[active.coerceIn(panes.indices)]
}

/** Build the editor's state holder once, seeded from the persisted [settings]. */
@Composable
fun rememberEditorUiState(settings: AppSettings): EditorUiState = remember {
    val starting = startingTool(settings.defaultTool, settings.toolGroupSelections)
    EditorUiState(
        tool = starting,
        color = settings.lastColor,
        // Shapes have their own width; every other tool starts at the pen's.
        width = if (starting in SHAPE_TOOLS) settings.shapeWidth else settings.lastWidth,
        highlighterColorInit = settings.highlighterColor,
        highlighterWidthInit = settings.highlighterWidth,
    )
}
