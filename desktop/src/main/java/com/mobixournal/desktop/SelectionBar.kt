package com.mobixournal.desktop

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
import com.mobixournal.render.Bounds
import com.mobixournal.render.SelectionOps
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
    /** Two or more caught elements: something to align against. */
    val canAlign: Boolean = false,
    /** Three or more: the two ends already define a distribution. */
    val canDistribute: Boolean = false,
    /** Exactly one caught element, and it is a living entity — the pencil's one condition. */
    val canEdit: Boolean = false,
    /** The tool the pencil names, printed in its tooltip (`Table`, `Plot`, …). */
    val editLabel: String = "",
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
    val box = Size(barWidth(BAR_BUTTONS, BAR_DIVIDERS, density), barHeight(density))
    when {
        selectionBounds != null -> SelectionBar(bar) {
            selectionBarOffset(
                bounds = selectionBounds,
                pxPerPt = zoom * density,
                originPx = Offset(offsetX * density, offsetY * density),
                canvas = canvas,
                bar = box,
                gapPx = 8f * density,
            )
        }
        bar.hasClipboard -> SelectionBar(bar) {
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
    var alignOpen by remember { mutableStateOf(false) }
    // A second click on the align button puts its menu away (see [DismissGuard]).
    val alignGuard = remember { DismissGuard() }
    if (!ui.hasSelection && !ui.hasClipboard) return
    Box(modifier.offset { placement() }) {
        Row(
            Modifier
                .glassCapsule(cornerRadius = DesktopTheme.panelRadius, shadow = DesktopTheme.bandShadow)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BarButton(ChromeGlyph.CUT, ui.enabled(SelectionAction.CUT), "Cut", "Ctrl+X") { ui.onAction(SelectionAction.CUT) }
            BarButton(ChromeGlyph.COPY, ui.enabled(SelectionAction.COPY), "Copy", "Ctrl+C") { ui.onAction(SelectionAction.COPY) }
            BarButton(ChromeGlyph.PASTE, ui.enabled(SelectionAction.PASTE), "Paste", "Ctrl+V") { ui.onAction(SelectionAction.PASTE) }
            BarButton(ChromeGlyph.DUPLICATE, ui.enabled(SelectionAction.DUPLICATE), "Duplicate", "Ctrl+D") {
                ui.onAction(SelectionAction.DUPLICATE)
            }
            BarDivider()
            BarButton(ChromeGlyph.RECOLOUR, ui.enabled(SelectionAction.RECOLOUR), "Recolour", "") {
                ui.onAction(SelectionAction.RECOLOUR)
            }
            BarButton(ChromeGlyph.WIDTH, ui.enabled(SelectionAction.WIDTH), "Line width", "") {
                ui.onAction(SelectionAction.WIDTH)
            }
            Box {
                BarButton(
                    // The tablet's own align button wears FormatAlignLeft; the catalogue's left-aligned
                    // lines are that picture, so the menu opens from the same mark on both shells.
                    ChromeGlyph.ALIGN_LEFT,
                    ui.enabled(SelectionAction.ALIGN_LEFT),
                    "Align & distribute",
                    "",
                    active = alignOpen,
                ) { alignOpen = !alignOpen && alignGuard.mayOpen(Unit) }
                if (alignOpen) {
                    AlignMenu(ui) {
                        alignGuard.closed(Unit)
                        alignOpen = false
                    }
                }
            }
            BarDivider()
            BarButton(ChromeGlyph.EDIT, ui.enabled(SelectionAction.EDIT), ui.editLabel.ifEmpty { "Edit" }, "") {
                ui.onAction(SelectionAction.EDIT)
            }
            BarButton(ChromeGlyph.DELETE, ui.enabled(SelectionAction.DELETE), "Delete", "Del") {
                ui.onAction(SelectionAction.DELETE)
            }
        }
    }
}

/** The six alignments and the two distributions, in the bar's own material, anchored under the button. */
@Composable
private fun AlignMenu(ui: SelectionBarUi, onClose: () -> Unit) {
    Popup(offset = IntOffset(0, 46), onDismissRequest = onClose, properties = FloatingSurface) {
        Column(Modifier.glassSurface().padding(6.dp)) {
            Text("Align", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            Spacer(Modifier.height(4.dp))
            val rows = listOf(
                listOf(
                    SelectionAction.ALIGN_LEFT to ChromeGlyph.ALIGN_LEFT,
                    SelectionAction.ALIGN_H_CENTRE to ChromeGlyph.ALIGN_H_CENTRE,
                    SelectionAction.ALIGN_RIGHT to ChromeGlyph.ALIGN_RIGHT,
                ),
                listOf(
                    SelectionAction.ALIGN_TOP to ChromeGlyph.ALIGN_TOP,
                    SelectionAction.ALIGN_V_MIDDLE to ChromeGlyph.ALIGN_V_MIDDLE,
                    SelectionAction.ALIGN_BOTTOM to ChromeGlyph.ALIGN_BOTTOM,
                ),
                listOf(
                    SelectionAction.DISTRIBUTE_H to ChromeGlyph.DISTRIBUTE_H,
                    SelectionAction.DISTRIBUTE_V to ChromeGlyph.DISTRIBUTE_V,
                ),
            )
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for ((action, glyph) in row) {
                        BarButton(glyph, ui.enabled(action), label(action), "") {
                            ui.onAction(action)
                            onClose()
                        }
                    }
                }
            }
        }
    }
}

/** The tooltip a bar control prints, in the same words the tablet's bar uses. */
private fun label(action: SelectionAction): String = when (action) {
    SelectionAction.ALIGN_LEFT -> "Align left"
    SelectionAction.ALIGN_H_CENTRE -> "Centre horizontally"
    SelectionAction.ALIGN_RIGHT -> "Align right"
    SelectionAction.ALIGN_TOP -> "Align top"
    SelectionAction.ALIGN_V_MIDDLE -> "Centre vertically"
    SelectionAction.ALIGN_BOTTOM -> "Align bottom"
    SelectionAction.DISTRIBUTE_H -> "Distribute horizontally"
    SelectionAction.DISTRIBUTE_V -> "Distribute vertically"
    else -> action.name.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun BarDivider() {
    Box(Modifier.width(1.dp).height(16.dp).background(DesktopTheme.hairline))
}

/** One square control: 32 dp, hover fill, the accent when it is the open menu, dimmed when unavailable. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BarButton(
    glyph: ChromeGlyph,
    enabled: Boolean,
    tooltip: String,
    shortcut: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
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
                    DesktopTheme.railSlotShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(glyph, enabled) { detectTapGestures { if (enabled) onClick() } },
            contentAlignment = Alignment.Center,
        ) {
            // The catalogue's filled art at the tablet's own 18 dp, not the bar's old 14 dp monoline: the
            // two shells draw one picture, which means one size and one kind of mark.
            Canvas(Modifier.size(BAR_GLYPH)) {
                drawChromeGlyph(
                    glyph,
                    if (enabled) DesktopTheme.chromeInk else DesktopTheme.chromeInk.copy(alpha = 0.32f),
                )
            }
        }
        if (hovered) {
            StripPill(
                label = tooltip,
                accent = shortcut,
                offset = IntOffset(sizePx + gapPx.toInt(), 0),
            )
        }
    }
}

/** The bar's square, in dp — the tablet's `SelectionBarButton`. */
internal val BAR_BUTTON = 32.dp

/** The glyph inside it, in dp — the tablet's `SelectionBarIcon`. */
private val BAR_GLYPH = 18.dp

/** How many squares and dividers the bar draws, so its width is arithmetic and not a measurement. */
internal const val BAR_BUTTONS = 8
internal const val BAR_DIVIDERS = 2

/**
 * The availability a document gives the bar, in one place: what the shell hands [SelectionBar] so the UI
 * holds no rule of its own. A test can call this on a real tab.
 */
internal fun selectionBarUi(
    tab: DesktopTab?,
    clipboard: DesktopClipboard,
    onAction: (SelectionAction) -> Unit = {},
): SelectionBarUi {
    val selection = tab?.selection
    val refs = selection?.refs.orEmpty()
    val target = tab?.let { livingTargetOf(it) }
    val page = tab?.let { runCatching { it.document.page(it.page) }.getOrNull() }
    val element = target?.let { ref ->
        page?.layers?.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
    }
    return SelectionBarUi(
        hasSelection = !refs.isEmpty(),
        hasClipboard = !clipboard.isEmpty,
        canAlign = refs.size >= 2,
        canDistribute = refs.size >= 3,
        canEdit = element != null,
        editLabel = element?.let { labelOf(it) } ?: "",
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
