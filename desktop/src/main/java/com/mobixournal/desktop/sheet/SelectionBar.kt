package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.tools.glassCapsule
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DismissGuard
import com.mobixournal.desktop.tools.FloatingSurface
import com.mobixournal.desktop.tools.PaletteStrip
import com.mobixournal.desktop.tools.StripPill
import com.mobixournal.desktop.tools.GlassPopup
import com.mobixournal.desktop.tools.GlassPresence

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.PenPalette
import com.mobixournal.render.Bounds
import com.mobixournal.render.SelectionOps
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The bar that appears with a selection — the tablet's Cut / Copy / Paste / Duplicate / Recolour / Width /
 * Align / Delete, plus the **pencil** that opens the selected living entity's editor.
 *
 * It rides just **below** the outline, as the slate's bar does, and only lifts above the box when the page
 * ends below it; either way it is clamped inside the canvas, so it can never be dragged off the window. It
 * is a floating surface in the interface contract's own material — glass, one rim, the panel radius — and
 * its glyphs are chrome ink on the same 32 dp square the tablet uses, with the accent only where a control
 * is *picked*. A control the selection cannot support is drawn at reduced alpha and does nothing: the
 * contract's rule is that a command is unavailable rather than a silent no-op.
 *
 * Alignment lives in a small anchored menu instead of eight more squares: six alignments and the two
 * distributions are one decision (where does this group sit relative to itself), so the bar keeps the
 * actions a hand reaches for and opens the rest on demand.
 *
 * [SelectionBarUi] is what the bar needs to know, decided by the shell, so a render test can draw the bar
 * without a document and a JVM test can check the availability rules.
 */
data class SelectionBarUi(
    val hasSelection: Boolean = false,
    val hasClipboard: Boolean = false,
    /**
     * Whether the **pick tools** are in hand (marquee or lasso).
     *
     * The bar's paste stand-in belongs to them and only them: with a pen in hand a full clipboard is not
     * something the hand is doing on this page, so nothing stands on the sheet for it. The commands are
     * unaffected — `Ctrl+V` still pastes whatever the tool.
     */
    val selectTool: Boolean = true,
    /** Two or more caught elements: something to align against. */
    val canAlign: Boolean = false,
    /** Three or more: the two ends already define a distribution. */
    val canDistribute: Boolean = false,
    /** Exactly one caught element, and it is a living entity — the pencil's one condition. */
    val canEdit: Boolean = false,
    /**
     * The swatches **Recolour** offers: the user's palette, and nothing else.
     *
     * The tablet's recolour menu hangs the shared palette including its editable custom slot; the window's
     * strip is the stored colours, because the slot's editor is a panel of its own (`ColourSettingsPage`)
     * and a strip was not the place to open one.
     */
    val palette: List<Int> = emptyList(),
    /** Recolour what is caught with one swatch of [palette] — one undoable edit. */
    val onPickColour: (Int) -> Unit = {},
    /** Width presets offered by the Line width popup. */
    val widthSlots: List<Float> = PenPalette.DEFAULT_WIDTHS,
    /** Re-width what is caught with one slot from [widthSlots] — one undoable edit. */
    val onPickWidth: (Float) -> Unit = {},
    /** The tool the pencil names, printed in its tooltip (`Table`, `Plot`, …). */
    val editLabel: String = "",
    /** Direct spline knot & loop actions when a single spline is selected. */
    val splineSelected: Boolean = false,
    val splineCanRemoveNode: Boolean = false,
    val splineCanToggleClosed: Boolean = false,
    val splineClosed: Boolean = false,
    val onAction: (SelectionAction) -> Unit = {},
) {
    /** The truth of one control, in the one place a test can read it. */
    fun enabled(action: SelectionAction): Boolean = when (action) {
        SelectionAction.PASTE -> hasClipboard
        SelectionAction.DUPLICATE, SelectionAction.DELETE, SelectionAction.CUT, SelectionAction.COPY,
        SelectionAction.RECOLOUR, SelectionAction.WIDTH,
        -> hasSelection
        SelectionAction.ALIGN_LEFT, SelectionAction.ALIGN_H_CENTRE, SelectionAction.ALIGN_RIGHT,
        SelectionAction.ALIGN_TOP, SelectionAction.ALIGN_V_MIDDLE, SelectionAction.ALIGN_BOTTOM,
        -> canAlign
        SelectionAction.DISTRIBUTE_H, SelectionAction.DISTRIBUTE_V -> canDistribute
        SelectionAction.EDIT -> canEdit
        SelectionAction.SPLINE_ADD_NODE, SelectionAction.SPLINE_TOGGLE_SMOOTH -> splineSelected
        SelectionAction.SPLINE_REMOVE_NODE -> splineSelected && splineCanRemoveNode
        SelectionAction.SPLINE_TOGGLE_CLOSED -> splineSelected && splineCanToggleClosed
    }
}

/** Where the bar sits over the canvas, in window pixels: below the box, above it when there is no room. */
internal fun selectionBarOffset(
    bounds: Bounds,
    pxPerPt: Float,
    originPx: Offset,
    canvas: Size,
    bar: Size,
    gapPx: Float,
    marginPx: Float = 6f,
): IntOffset {
    val left = (bounds.left * pxPerPt).toFloat() + originPx.x
    val right = (bounds.right * pxPerPt).toFloat() + originPx.x
    val top = (bounds.top * pxPerPt).toFloat() + originPx.y
    val bottom = (bounds.bottom * pxPerPt).toFloat() + originPx.y
    val below = bottom + gapPx
    val above = top - gapPx - bar.height
    val y = if (below + bar.height <= canvas.height - marginPx) below else above
    val centred = (left + right) / 2f - bar.width / 2f
    val maxX = (canvas.width - bar.width - marginPx).coerceAtLeast(marginPx)
    val x = centred.coerceIn(marginPx, maxX)
    val maxY = (canvas.height - bar.height - marginPx).coerceAtLeast(marginPx)
    return IntOffset(x.roundToInt(), y.coerceIn(marginPx, maxY).roundToInt())
}

/**
 * The bar **hosted** in the canvas: under the selected outline when there is one, at the page's own
 * bottom-centre while only the clipboard has something to paste.
 *
 * It exists so the canvas passes five numbers and adds one line, instead of carrying the branch and the
 * placement arithmetic inside its own composable (`SourceStructureTest` caps a function's length).
 */
@Composable
internal fun SelectionBarHost(
    bar: SelectionBarUi,
    selectionBounds: Bounds?,
    zoom: Float,
    density: Float,
    offsetX: Float,
    offsetY: Float,
    canvas: Size,
) {
    if (canvas.width <= 0f || canvas.height <= 0f) return
    // **One** bar, whichever state the canvas is in: the same composable shrinks to the paste button when
    // nothing is caught and grows back under the outline, so no frame is left with the old surface torn
    // down and the new one not yet placed — which is what read as a flicker.
    val pillOnly = selectionBounds == null
    val box = if (pillOnly) {
        Size(barWidth(PASTE_ONLY_BUTTONS, 0, density), barHeight(density))
    } else {
        val extraButtons = if (bar.splineSelected) {
            2 + (if (bar.splineCanRemoveNode) 1 else 0) + (if (bar.splineCanToggleClosed) 1 else 0)
        } else 0
        val extraDividers = if (bar.splineSelected) 1 else 0
        Size(barWidth(BAR_BUTTONS + extraButtons, BAR_DIVIDERS + extraDividers, density), barHeight(density))
    }
    SelectionBar(bar) {
        if (selectionBounds != null) {
            selectionBarOffset(
                bounds = selectionBounds,
                pxPerPt = zoom * density,
                originPx = Offset(offsetX * density, offsetY * density),
                canvas = canvas,
                bar = box,
                gapPx = 8f * density,
            )
        } else {
            // The stand-in keeps the page's bottom-centre and is measured as the one square it draws, so
            // it sits centred: a full bar of dimmed buttons under the sheet claimed eight commands while
            // seven of them could not act.
            IntOffset(
                ((canvas.width - box.width) / 2f).roundToInt().coerceAtLeast(0),
                (canvas.height - box.height - 12f * density).roundToInt().coerceAtLeast(0),
            )
        }
    }
}

/** The bar itself, drawn over the sheet; callers mount it inside the canvas box. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SelectionBar(
    ui: SelectionBarUi,
    modifier: Modifier = Modifier,
    placement: () -> IntOffset,
) {
    // Recolour opens the palette **under the bar**, the tablet's own mechanism: the swatches are a
    // decided-by-a-tap command, not eight more squares on a bar that is already eight wide.
    var recolourOpen by remember { mutableStateOf(false) }
    val recolourGuard = remember { DismissGuard() }
    var widthOpen by remember { mutableStateOf(false) }
    val widthGuard = remember { DismissGuard() }
    if (!ui.hasSelection && !ui.hasClipboard) return
    // The placement **glides**: the bar's own width changes when a selection appears (a paste square ↔
    // eight), and the correction lands a frame late, so a hard switch flashed it at the paste spot before
    // snapping it under the outline.
    val at by animateIntOffsetAsState(placement(), label = "selectionBarPlace")
    Box(modifier.offset { at }) {
        Row(
            Modifier
                // An **opaque surface**, not a glass capsule: a translucent bar over a white page went
                // grey and muddied the words under it. The 8 dp surface with its one hairline reads as an
                // object above the sheet and hides nothing it lies on (`docs/interface-contract.md` §1).
                .glassSurface(cornerRadius = DesktopTheme.surfaceRadius)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Paste is the bar's one command that needs **no** selection, so it is the only square the
            // stand-in draws: with nothing caught the bar *is* the paste button, not a bar of dimmed
            // squares that say nothing.
            if (ui.hasSelection) {
                if (ui.splineSelected) SplineSelectionCommands(ui)
                BarButton(ChromeGlyph.CUT, ui.enabled(SelectionAction.CUT), "Cut", "Ctrl+X") { ui.onAction(SelectionAction.CUT) }
                BarButton(ChromeGlyph.COPY, ui.enabled(SelectionAction.COPY), "Copy", "Ctrl+C") { ui.onAction(SelectionAction.COPY) }
            }
            BarButton(ChromeGlyph.PASTE, ui.enabled(SelectionAction.PASTE), "Paste", "Ctrl+V") { ui.onAction(SelectionAction.PASTE) }
            if (ui.hasSelection) {
                SelectionCommands(
                    ui = ui,
                    recolourOpen = recolourOpen,
                    widthOpen = widthOpen,
                    onRecolour = {
                        widthOpen = false
                        recolourOpen = !recolourOpen && recolourGuard.mayOpen(Unit)
                    },
                    onWidth = {
                        recolourOpen = false
                        widthOpen = !widthOpen && widthGuard.mayOpen(Unit)
                    },
                )
            }
        }
        // Nowhere near the button: the strip hangs from the **bar**, left edge on left edge, so it stands
        // exactly as wide as the surface that opened it instead of running off to the right of the button
        // in the middle of the row.
        val dropPx = with(LocalDensity.current) { 44.dp.roundToPx() }
        GlassPresence(recolourOpen && ui.hasSelection) {
            GlassPopup(
                offset = IntOffset(0, dropPx),
                onDismissRequest = { recolourGuard.closed(Unit); recolourOpen = false },
            ) {
                Column(Modifier.glassSurface().padding(4.dp)) {
                    PaletteStrip(colours = ui.palette, current = null, onPick = { colour ->
                        ui.onPickColour(colour)
                        recolourGuard.closed(Unit)
                        recolourOpen = false
                    })
                }
            }
        }
        GlassPresence(widthOpen && ui.hasSelection) {
            GlassPopup(
                offset = IntOffset(0, dropPx),
                onDismissRequest = { widthGuard.closed(Unit); widthOpen = false },
            ) {
                WidthPickerStrip(widths = ui.widthSlots) { width ->
                    ui.onPickWidth(width)
                    widthGuard.closed(Unit)
                    widthOpen = false
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WidthPickerStrip(widths: List<Float>, onPick: (Float) -> Unit) {
    Row(
        Modifier.glassSurface().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        widths.forEachIndexed { index, width ->
            var hovered by remember { mutableStateOf(false) }
            val label = PenPalette.WIDTH_LABELS.getOrElse(index) { "${index + 1}" }
            Row(
                Modifier
                    .background(
                        if (hovered) DesktopTheme.hoverSlot else Color.Transparent,
                        DesktopTheme.controlShape,
                    )
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .pointerInput(width) { detectTapGestures { onPick(width) } }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .width(14.dp)
                        .height(width.coerceIn(1f, 6f).dp)
                        .background(DesktopTheme.chromeInk, DesktopTheme.controlShape),
                )
                Text(
                    text = "$label · ${formatWidthPt(width)} pt",
                    color = DesktopTheme.ink,
                    style = DesktopTheme.numeric,
                )
            }
        }
    }
}

private fun formatWidthPt(pt: Float): String {
    val s = "%.2f".format(Locale.US, pt)
    return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
}

/**
 * The commands a selection adds, after Paste: duplicate, the ink's two doors, then edit and delete.
 *
 * Split out of [SelectionBar] so that composable stays a placement and a row — the row itself is longer than
 * a function's budget once seven buttons are written inside it.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SelectionCommands(
    ui: SelectionBarUi,
    recolourOpen: Boolean,
    widthOpen: Boolean,
    onRecolour: () -> Unit,
    onWidth: () -> Unit,
) {
    BarButton(ChromeGlyph.DUPLICATE, ui.enabled(SelectionAction.DUPLICATE), "Duplicate", "Ctrl+D") {
        ui.onAction(SelectionAction.DUPLICATE)
    }
    BarDivider()
    BarButton(ChromeGlyph.RECOLOUR, ui.enabled(SelectionAction.RECOLOUR), "Recolour", "", active = recolourOpen) {
        onRecolour()
    }
    BarButton(ChromeGlyph.WIDTH, ui.enabled(SelectionAction.WIDTH), "Line width", "", active = widthOpen) {
        onWidth()
    }
    BarDivider()
    BarButton(ChromeGlyph.EDIT, ui.enabled(SelectionAction.EDIT), ui.editLabel.ifEmpty { "Edit" }, "") {
        ui.onAction(SelectionAction.EDIT)
    }
    BarButton(ChromeGlyph.DELETE, ui.enabled(SelectionAction.DELETE), "Delete", "Del") {
        ui.onAction(SelectionAction.DELETE)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SplineSelectionCommands(ui: SelectionBarUi) {
    BarButton(ChromeGlyph.ADD, true, "Add knot", "") { ui.onAction(SelectionAction.SPLINE_ADD_NODE) }
    BarButton(ChromeGlyph.TIMELINE, true, "Toggle smooth or sharp corner", "") {
        ui.onAction(SelectionAction.SPLINE_TOGGLE_SMOOTH)
    }
    if (ui.splineCanRemoveNode) {
        BarButton(ChromeGlyph.MINUS, true, "Remove selected knot", "") {
            ui.onAction(SelectionAction.SPLINE_REMOVE_NODE)
        }
    }
    if (ui.splineCanToggleClosed) {
        BarButton(
            ChromeGlyph.LOOP,
            true,
            if (ui.splineClosed) "Open curve" else "Close loop",
            "",
            active = ui.splineClosed,
            accented = ui.splineClosed,
        ) { ui.onAction(SelectionAction.SPLINE_TOGGLE_CLOSED) }
    }
    BarDivider()
}

@Composable
private fun BarDivider() {
    Box(Modifier.width(1.dp).height(16.dp).background(DesktopTheme.hairline))
}

/** One square control: 32 dp, hover fill, the accent when it is the open menu, dimmed when unavailable. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BarButton(
    glyph: ChromeGlyph,
    enabled: Boolean,
    tooltip: String,
    shortcut: String,
    active: Boolean = false,
    accented: Boolean = false,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val density = LocalDensity.current
    val sizePx = with(density) { BAR_BUTTON.roundToPx() }
    val gapPx = with(density) { 6.dp.toPx() }
    Box {
        Box(
            Modifier
                .size(BAR_BUTTON)
                .background(
                    when {
                        active -> DesktopTheme.activeSlot
                        hovered && enabled -> DesktopTheme.hoverSlot
                        else -> Color.Transparent
                    },
                    DesktopTheme.controlShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(glyph, enabled) { detectTapGestures { if (enabled) currentOnClick() } },
            contentAlignment = Alignment.Center,
        ) {
            // The catalogue's filled art at the tablet's own 18 dp, not the bar's old 14 dp monoline: the
            // two shells draw one picture, which means one size and one kind of mark.
            val tint = when {
                !enabled -> DesktopTheme.chromeInk.copy(alpha = 0.32f)
                accented -> DesktopTheme.accent
                else -> DesktopTheme.chromeInk
            }
            Canvas(Modifier.size(BAR_GLYPH)) {
                drawChromeGlyph(glyph, tint)
            }
        }
        if (hovered) {
            // **Below** the button, not beside it: a pill to the right covered the very command next to the
            // one the reader was asking about.
            StripPill(
                label = tooltip,
                accent = shortcut,
                offset = IntOffset(0, sizePx + gapPx.toInt()),
            )
        }
    }
}

/** The bar's square, in dp — the tablet's `SelectionBarButton`. */
internal val BAR_BUTTON = 32.dp

/** The glyph inside it, in dp — the tablet's `SelectionBarIcon`. */
private val BAR_GLYPH = 18.dp

/**
 * How many squares and dividers the bar draws, so its width is arithmetic and not a measurement.
 *
 * Nine squares: cut, copy, paste, duplicate, recolour, width, align, edit and delete. It said eight while nine
 * were drawn, which put the bar's measured centre half a button off and made its clamp a button too narrow.
 */
internal const val BAR_BUTTONS = 8
internal const val BAR_DIVIDERS = 2

/** The stand-in draws **Paste** and nothing else, so its width is one square with no divider. */
internal const val PASTE_ONLY_BUTTONS = 1

/**
 * The availability a document gives the bar, in one place: what the shell hands [SelectionBar] so the UI
 * holds no rule of its own. A test can call this on a real tab.
 */
internal fun selectionBarUi(
    tab: DesktopTab?,
    clipboard: DesktopClipboard,
    selectTool: Boolean = true,
    palette: List<Int> = emptyList(),
    onPickColour: (Int) -> Unit = {},
    widthSlots: List<Float> = PenPalette.DEFAULT_WIDTHS,
    onPickWidth: (Float) -> Unit = {},
    onAction: (SelectionAction) -> Unit = {},
): SelectionBarUi {
    val selection = tab?.selection
    val refs = selection?.refs.orEmpty()
    val target = tab?.let { livingTargetOf(it) }
    val page = tab?.let { runCatching { it.document.page(it.page) }.getOrNull() }
    val element = target?.let { ref ->
        page?.layers?.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
    }
    val spline = element as? com.mobixournal.format.model.SplineElement
    return SelectionBarUi(
        hasSelection = !refs.isEmpty(),
        hasClipboard = !clipboard.isEmpty || clipboard.hasSystemImage(),
        selectTool = selectTool,
        canAlign = refs.size >= 2,
        canDistribute = refs.size >= 3,
        canEdit = element != null,
        editLabel = element?.let { labelOf(it) } ?: "",
        splineSelected = spline != null,
        splineCanRemoveNode = (spline?.nodes?.size ?: 0) > 2,
        splineCanToggleClosed = spline?.let { it.nodes.size >= 3 || it.closed } == true,
        splineClosed = spline?.closed == true,
        palette = palette,
        onPickColour = onPickColour,
        widthSlots = widthSlots,
        onPickWidth = onPickWidth,
        onAction = onAction,
    )
}

/** The family's name, printed on the pencil's tooltip: what the editor about to open edits. */
private fun labelOf(element: com.mobixournal.format.model.Element): String = when (element) {
    is com.mobixournal.format.model.TableElement -> "Edit table"
    is com.mobixournal.format.model.PlotElement -> "Edit plot"
    is com.mobixournal.format.model.ParametricShapeElement -> "Edit shape"
    is com.mobixournal.format.model.CircuitElement -> "Edit component"
    is com.mobixournal.format.model.MathBlockElement -> "Edit formula"
    is com.mobixournal.format.model.SplineElement -> "Edit spline"
    else -> "Edit"
}

/** The six alignments, for a caller that wants to draw them in one row (the menu, and the tests). */
internal val ALIGNMENTS: List<SelectionAction> = listOf(
    SelectionAction.ALIGN_LEFT, SelectionAction.ALIGN_H_CENTRE, SelectionAction.ALIGN_RIGHT,
    SelectionAction.ALIGN_TOP, SelectionAction.ALIGN_V_MIDDLE, SelectionAction.ALIGN_BOTTOM,
)

/** The two distributions, beside the alignments. */
internal val DISTRIBUTIONS: List<SelectionAction> = listOf(
    SelectionAction.DISTRIBUTE_H, SelectionAction.DISTRIBUTE_V,
)

/** `SelectionOps.SelectionAlign` of an action, or null when it is not an alignment. */
internal fun SelectionAction.asAlignment(): SelectionOps.SelectionAlign? = when (this) {
    SelectionAction.ALIGN_LEFT -> SelectionOps.SelectionAlign.LEFT
    SelectionAction.ALIGN_H_CENTRE -> SelectionOps.SelectionAlign.H_CENTRE
    SelectionAction.ALIGN_RIGHT -> SelectionOps.SelectionAlign.RIGHT
    SelectionAction.ALIGN_TOP -> SelectionOps.SelectionAlign.TOP
    SelectionAction.ALIGN_V_MIDDLE -> SelectionOps.SelectionAlign.V_MIDDLE
    SelectionAction.ALIGN_BOTTOM -> SelectionOps.SelectionAlign.BOTTOM
    else -> null
}

/** A bar's width for its controls, so a placement test can name it without measuring a composition. */
internal fun barWidth(buttons: Int, dividers: Int, density: Float): Float {
    val buttonPx = BAR_BUTTON.value * density
    val dividerPx = 1f * density
    val gaps = (buttons + dividers - 1) * 2f * density
    val padding = 8f * density
    return buttons * buttonPx + dividers * dividerPx + gaps + padding
}

/** A comfortably-sized bar box for the placement arithmetic — 32 dp tall plus its padding. */
internal fun barHeight(density: Float): Float = 32f * density + 6f * density
