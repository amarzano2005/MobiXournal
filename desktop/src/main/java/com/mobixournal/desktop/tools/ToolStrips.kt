package com.mobixournal.desktop.tools
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.PaletteCodec
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.SlotPickerStack
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.drawToolGlyph
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.sheet.GuideStrip
import com.mobixournal.desktop.sheet.circuitFamilyOf
import com.mobixournal.desktop.sheet.guideSlotGlyph
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.shell.PageMenuActions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.DesignTokens
import com.mobixournal.design.PenPalette
import com.mobixournal.design.ToolGlyph
import com.mobixournal.render.CircuitFamily
import com.mobixournal.render.ShapeKind
import kotlinx.coroutines.delay

/**
 * The two tool placements, as the flat workstation draws them: **two docked bands**, and no object
 * standing in either.
 *
 * Phase 1A of `desktop-ui-flat-workstation` had taken the strips out of the sheet, but left the tablet's
 * capsule inside them — 52 dp of glass with its own rim and shadow, floating in a 64 dp lane — so the
 * window was a bar inside a bar. `docs/interface-contract.md` §1 settles it: chrome is one tone and one
 * hairline, and a band is geometry, not an object lying on another. The rail is therefore 48 dp wide with
 * its line on the side facing the canvas, the ribbon 44 dp tall with its line beneath it, and the 36 dp
 * slots sit straight in that fill.
 *
 * Three things follow and are as much of the change as the pixels:
 *
 *  - the **families** are separated by a 24 dp hairline ([StripItem.Divider]) instead of reading as one
 *    run of twenty buttons;
 *  - an overflowed strip shows a **3 dp thumb** ([StripScrollCue]) so a tool past the window's edge is
 *    known to be there rather than silently clipped;
 *  - the tooltip waits **350 ms** ([StripPill]) so sweeping the pointer across the rail does not strobe a
 *    pill per slot on the way.
 *
 * The page is still framed in what the bands leave (`ChromeInset`), so a document opens beside the rail
 * and under the ribbon rather than underneath either. The strips only set a [ToolSelection]; what a pen
 * does with the picked tool is the canvas's own business.
 */
object ToolStripMetrics {

    /** The rail's lane: one band down an edge, 48 dp (`DESKTOP_RAIL_WIDTH_DP`). */
    val railWidth = DesignTokens.DESKTOP_RAIL_WIDTH_DP.dp

    /** The ribbon's band under the header: 44 dp (`DESKTOP_RIBBON_HEIGHT_DP`). */
    val ribbonHeight = DesignTokens.DESKTOP_RIBBON_HEIGHT_DP.dp

    /** One tool slot. 36 dp in a 48 dp lane leaves 6 dp of air on each side. */
    val slotSize = 36.dp

    /** The glyph drawn inside a slot: monoline art at a size that keeps the strokes apart. */
    val glyphSize = 20.dp

    /** The gap between slots, and every strip's own padding: what centres a slot in its lane. */
    val gap = 4.dp
    val stripPadding = 6.dp

    /** How far the hover pill sits from the slot it names. */
    val tooltipGap = 6.dp

    /** A family's rule inside a strip: 1 dp across, 24 dp long, with [gap] of air either side. */
    val dividerThickness = 1.dp
    val dividerLength = 24.dp

    /** The overflow thumb, at the lane's edge on the canvas side. */
    val scrollCue = 3.dp

    /** How long a resting pointer must stay on a slot before its pill appears. */
    const val tooltipDelayMs = 350L

    /**
     * Where slot 0 starts inside the rail's lane. Derived rather than written down, so the strip and a
     * render test cannot disagree about it.
     */
    val railSlotLeft = stripPadding
    val railSlotTop = stripPadding

    /** Where the first slot starts across the ribbon, and how far down the 44 dp band it is centred. */
    val ribbonSlotLeft = stripPadding
    val ribbonSlotTop = (ribbonHeight - slotSize) / 2f
}

/**
 * Everything a strip draws, as one value: the picked tool, the ink and the palette it offers.
 *
 * Bundled rather than passed as four parameters because both placements must draw the same thing — a
 * rail that showed a different colour from the ribbon would be two palettes again.
 */
data class ToolStripState(
    val selection: ToolSelection = ToolSelection(),
    val ink: DesktopInk = DesktopInk(),
    val palette: List<Int> = PenPalette.COLORS,
    /** The palette's editable custom slot, which both tools share; null while it is empty. */
    val custom: Int? = null,
    val favourites: List<Int> = PenPalette.COLORS.take(PaletteCodec.FAVOURITES),
    /** Which group's picker is open, as an index into [groups]; null when none is. */
    val openGroup: Int? = null,
    /**
     * The slots in the reader's order, from the Toolbar section ([orderedToolGroups]).
     *
     * Part of the value both strips draw, for the same reason the ink is: the rail and the ribbon are two
     * layouts of one arrangement, so a reordered button has to move in both of them at once.
     */
    val groups: List<ToolGroup> = DESKTOP_TOOL_GROUPS,
    /** Raw persisted slot + divider order (`StripItems`), empty for factory auto-dividers. */
    val toolOrder: List<String> = emptyList(),
    /** The slot **Insert image** stands under — the strip's one movable command (`stripItems`). */
    val imageAfter: String = DEFAULT_IMAGE_ANCHOR.name,
    /** Opening (an index) or closing (null) the picker — the slot's chevron and `Alt+Down` both go here. */
    val onOpenGroup: (Int?) -> Unit = {},
    /** The part each circuit slot is armed with — the palettes' answers, one per family. */
    val circuitKinds: Map<CircuitFamily, ShapeKind> =
        CircuitFamily.entries.associateWith { it.face },
    /** The family whose palette is open, or null when none is. */
    val circuitPickerOpen: CircuitFamily? = null,
    /** Arm a circuit slot with a part of its own family. */
    val onCircuitKind: (CircuitFamily, ShapeKind) -> Unit = { _, _ -> },
    /** Open one family's palette, or close the open one with null. */
    val onCircuitPicker: (CircuitFamily?) -> Unit = {},
    /**
     * The **figures' own answers**: the variant each figure slot draws, its menu, and the pick these
     * two commands answer. One value, so the rail's slot and the ribbon's cannot show different ones.
     */
    val figures: FigureStrip = FigureStrip(),
    /**
     * The **guides slot's** own answer: the instrument standing on the active tab, and the two commands
     * that place it and put it away. One value, so the rail's slot and the ribbon's cannot show different
     * instruments (`docs/drawing-tools.md`).
     */
    val guides: GuideStrip = GuideStrip(),
    /**
     * The **page** section, drawn after the strip's divider: the tablet's own menu of what a page is
     * (its paper and its format) and what the document's page list can become (add, remove, step).
     * Commands on the page, not ink, which is why they stand apart from the tools and the palette
     * (`docs/interface-contract.md` §2).
     */
    val pages: PageMenuActions = PageMenuActions(),
    val onInsertImage: () -> Unit = {},
)

/** Which side of its slot a strip's transient surface opens on: the rail has no room below it. */
internal enum class StripSide { RIGHT, BELOW }

/**
 * What the strips' ink slots report: a picked colour or width, and the two requests only the panel can
 * answer (redefine the custom slot, add a new palette entry).
 *
 * A plain class with defaults so a render test can draw a strip without wiring the window up.
 */
class InkActions(
    val onColour: (Int) -> Unit = {},
    val onWidth: (Float) -> Unit = {},
    /** The pen's stroke pattern: solid, dashed, dash-dot or dotted. */
    val onLineStyle: (com.mobixournal.format.model.LineStyle) -> Unit = {},
    val onEditCustom: () -> Unit = {},
    val onAddColour: () -> Unit = {},
)

/**
 * Option B: the vertical rail, docked beside the canvas.
 *
 * One band 48 dp wide, its hairline on the side the canvas is on — [canvasEdge] is `END` for a rail on
 * the left and `START` for one on the right, so the line always lands between the tools and the sheet.
 * The slots scroll when the window cannot hold them, and a 3 dp thumb says so instead of dropping the
 * last tools off the band in silence.
 */
@Composable
internal fun ToolRail(
    state: ToolStripState,
    onSelect: (DesktopTool) -> Unit,
    ink: InkActions = InkActions(),
    modifier: Modifier = Modifier,
    /** The edge the canvas is on, which is the edge the band's one hairline is drawn against. */
    canvasEdge: BandEdge = BandEdge.END,
) {
    val scroll = rememberScrollState()
    Box(
        modifier
            .width(ToolStripMetrics.railWidth)
            .fillMaxHeight()
            .bandSurface()
            .bandDivider(canvasEdge),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(ToolStripMetrics.stripPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ToolStripMetrics.gap),
        ) {
            for (item in stripItems(state.groups, state.imageAfter, state.toolOrder)) {
                when (item) {
                    is StripItem.Tools -> ToolSlot(
                        group = item.group, selection = state.selection,
                        pickerOpen = state.openGroup == item.index, onSelect = onSelect,
                        onOpenPicker = { state.onOpenGroup(item.index) },
                        onClosePicker = { state.onOpenGroup(null) },
                        circuitKinds = state.circuitKinds, circuitPickerOpen = state.circuitPickerOpen,
                        onCircuitKind = state.onCircuitKind, onCircuitPicker = state.onCircuitPicker,
                        figures = state.figures, guides = state.guides, side = StripSide.RIGHT,
                    )

                    StripItem.Divider -> StripDivider(vertical = true)

                    else -> StripCommand(item, state, ink, StripSide.RIGHT)
                }
            }
        }
        // The thumb sits on the edge facing the canvas, like the band's own hairline.
        StripScrollCue(
            scroll,
            vertical = true,
            alignment = if (canvasEdge == BandEdge.START) Alignment.CenterStart else Alignment.CenterEnd,
        )
    }
}

/**
 * Option A: the horizontal ribbon, a band under the header.
 *
 * The rail laid on its side: 44 dp tall, its hairline along its lower edge, the same 6 dp of side air and
 * the same family rules. It scrolls across when the window is narrow, with the overflow thumb under it.
 */
@Composable
fun ToolRibbon(
    state: ToolStripState,
    onSelect: (DesktopTool) -> Unit,
    ink: InkActions = InkActions(),
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Box(
        modifier
            .fillMaxWidth()
            .height(ToolStripMetrics.ribbonHeight)
            .bandSurface()
            .bandDivider(BandEdge.BOTTOM),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .horizontalScroll(scroll)
                .padding(horizontal = ToolStripMetrics.stripPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolStripMetrics.gap),
        ) {
            for (item in stripItems(state.groups, state.imageAfter, state.toolOrder)) {
                when (item) {
                    is StripItem.Tools -> ToolSlot(
                        group = item.group, selection = state.selection,
                        pickerOpen = state.openGroup == item.index, onSelect = onSelect,
                        onOpenPicker = { state.onOpenGroup(item.index) },
                        onClosePicker = { state.onOpenGroup(null) },
                        circuitKinds = state.circuitKinds, circuitPickerOpen = state.circuitPickerOpen,
                        onCircuitKind = state.onCircuitKind, onCircuitPicker = state.onCircuitPicker,
                        figures = state.figures, guides = state.guides, side = StripSide.BELOW,
                    )

                    StripItem.Divider -> StripDivider(vertical = false)

                    else -> StripCommand(item, state, ink, StripSide.BELOW)
                }
            }
        }
        StripScrollCue(scroll, vertical = false, alignment = Alignment.BottomCenter)
    }
}

/**
 * The **overflow thumb**: a 3 dp bar in the lane's own gutter whenever the strip has more than it can
 * show.
 *
 * A strip that clipped silently was the old bug — twenty slots in a window that held fourteen, with no
 * sign that the STEM half was still there. The bar is drawn from the scroll state, so it is the strip's
 * own answer rather than a second opinion about how much is hidden.
 */
@Composable
private fun BoxScope.StripScrollCue(scroll: ScrollState, vertical: Boolean, alignment: Alignment) {
    // Nothing hidden: a thumb would be a promise of content that is not there.
    if (scroll.maxValue <= 0) return
    Canvas(
        if (vertical) {
            Modifier.align(alignment).fillMaxHeight().width(ToolStripMetrics.scrollCue)
        } else {
            Modifier.align(alignment).fillMaxWidth().height(ToolStripMetrics.scrollCue)
        },
    ) {
        val total = scroll.maxValue + scroll.viewportSize
        if (total <= 0 || scroll.maxValue <= 0) return@Canvas
        val ink = DesktopTheme.inkMuted.copy(alpha = 0.45f)
        val radius = CornerRadius(size.minDimension / 2f)
        if (vertical) {
            val thumb = size.height * scroll.viewportSize / total
            val start = (size.height - thumb) * (scroll.value.toFloat() / scroll.maxValue)
            drawRoundRect(ink, Offset(0f, start), Size(size.width, thumb), radius)
        } else {
            val thumb = size.width * scroll.viewportSize / total
            val start = (size.width - thumb) * (scroll.value.toFloat() / scroll.maxValue)
            drawRoundRect(ink, Offset(start, 0f), Size(thumb, size.height), radius)
        }
    }
}

/**
 * A family's rule: one 1 dp hairline, 24 dp long across the strip and **perpendicular** to its run.
 *
 * A rule drawn along the run would read as a tick inside the lane rather than as a break in it: a
 * vertical rail is separated by a horizontal line 24 dp wide, a horizontal ribbon by a vertical one
 * 24 dp tall. The line is the panel's own hairline, so it is the same edge as the band's, at a quarter
 * of its length.
 */
@Composable
private fun StripDivider(vertical: Boolean) {
    val line = Modifier
        .then(
            if (vertical) {
                Modifier.width(ToolStripMetrics.dividerLength).height(ToolStripMetrics.dividerThickness)
            } else {
                Modifier.width(ToolStripMetrics.dividerThickness).height(ToolStripMetrics.dividerLength)
            },
        )
        .background(DesktopTheme.hairline)
    Box(line)
}

/**
 * One tool slot of a strip: the picked glyph in the accent, the 4 dp corner's hover step, and the
 * gesture it answers.
 *
 * A slot that [ToolGroup.hasPicker] carries the chevron and opens the group's picker — by clicking the
 * chevron, by right-clicking anywhere on the slot, or by holding it; a click on the glyph picks the tool
 * in place (`docs/interface-contract.md` §3).
 */
@Composable
private fun ToolSlot(
    group: ToolGroup,
    selection: ToolSelection,
    pickerOpen: Boolean,
    onSelect: (DesktopTool) -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    circuitKinds: Map<CircuitFamily, ShapeKind>,
    circuitPickerOpen: CircuitFamily?,
    onCircuitKind: (CircuitFamily, ShapeKind) -> Unit,
    onCircuitPicker: (CircuitFamily?) -> Unit,
    figures: FigureStrip,
    guides: GuideStrip,
    side: StripSide,
) {
    val tool = group.faceFor(selection)
    val figure = tool.takeIf { it in FIGURE_TOOLS }
    val active = tool == selection.current
    // What its chevron (or a second click on a picked slot) opens: the group, a circuit palette, a figure.
    val surface = slotSurface(
        tool = tool,
        pickerOpen = pickerOpen,
        circuitPickerOpen = circuitPickerOpen,
        figures = figures,
        openGroup = onOpenPicker,
        closeGroup = onClosePicker,
        circuit = onCircuitPicker,
    )
    // A surface belongs to the slot that opened it: its next click closes it, and the popup's own
    // dismissal is remembered so that same press cannot reopen it (DismissGuard).
    val guard = remember { DismissGuard() }
    val toggleSurface: () -> Unit = {
        when {
            surface.up -> surface.close()
            guard.mayOpen(group) -> surface.open()
        }
    }
    var hovered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val slotPx = with(density) { ToolStripMetrics.slotSize.roundToPx() }
    val gapPx = with(density) { ToolStripMetrics.tooltipGap.roundToPx() }
    // The pill and the picker open on the same side, at the same distance: one slot, one offset.
    val surfaceOffset = side.surfaceOffset(slotPx, gapPx)

    Box {
        val isScaleneEdit = active && figures.isScalene(tool)
        val armedCircuit = circuitFamilyOf(tool)?.let { circuitKinds[it] ?: it.face }
        SlotFace(
            tool = tool,
            // A guides slot wears the instrument **placed**, so a reader back on the pen sees it; every
            // other slot draws its own face.
            glyph = guideSlotGlyph(tool, guides.placed) ?: figureSlotGlyph(tool, figures.shape),
            circuitKind = armedCircuit,
            badge = if (figure == DesktopTool.POLYGON) "${figures.shape.polygonSides}" else null,
            group = group,
            selection = selection,
            active = active,
            hovered = hovered,
            onHover = { hovered = it },
            onSelect = onSelect,
            onOpenPicker = toggleSurface,
            opensSurface = group.opensSubmenu(),
            onEditFigure = if (isScaleneEdit) { { figures.onOpenDialog(tool) } } else null,
        )
        // The pill stands aside while the slot's own surface is up — any of them, so a circuit slot's
        // palette or a figure's variants are not covered by its tooltip. It appears after the pointer has
        // rested a moment (`StripPill`), so a sweep across the rail does not strobe.
        if (hovered && !surface.up) TooltipPill(tool = tool, offset = surfaceOffset)
        SlotPickerStack(
            group = group,
            selection = selection,
            tool = tool,
            pickerOpen = pickerOpen,
            circuitKinds = circuitKinds,
            circuitPickerOpen = circuitPickerOpen,
            figures = figures,
            guides = guides,
            onSelect = onSelect,
            onClosePicker = onClosePicker,
            // A dismissal is the popup's own (a click outside it), and it is remembered so that same
            // click cannot open the surface again on its way to the slot under it.
            onDismissPicker = { guard.closed(group); onClosePicker() },
            onDismissCircuit = { guard.closed(group); onCircuitPicker(null) },
            onDismissFigure = { guard.closed(group); figures.onMenu(null) },
            onCircuitKind = onCircuitKind,
            onCircuitPicker = onCircuitPicker,
            offset = surfaceOffset,
        )
    }
}

/**
 * Where a slot's transient surfaces open: beside it on the rail, under it on the ribbon and in the
 * settings list's own strips.
 */
private fun StripSide.surfaceOffset(slotPx: Int, gapPx: Int): IntOffset = when (this) {
    StripSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
    StripSide.BELOW -> IntOffset(0, slotPx + gapPx)
}

/**
 * The slot's face: its glyph, its background and the three gestures it answers — a click that picks (or
 * asks which member), a chevron with a 16 dp tap target of its own, and the focus ring.
 *
 * Split from [ToolSlot] so the slot itself is placement and the surfaces it opens, and the 36 dp frame
 * with its five states is one component both strips share: resting on the band, hovered with the 4 dp
 * corner, picked in the active steel with an accent glyph, focused with a 1 dp accent ring, and pressed
 * through the same fill one step in.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SlotFace(
    tool: DesktopTool,
    /** The picture on the slot: a figure's chosen variant, or the tool's own glyph. */
    glyph: ToolGlyph,
    /** A count in the slot's top-left corner — the polygon's sides, as the tablet's slot prints it. */
    badge: String?,
    group: ToolGroup,
    selection: ToolSelection,
    active: Boolean,
    hovered: Boolean,
    onHover: (Boolean) -> Unit,
    onSelect: (DesktopTool) -> Unit,
    onOpenPicker: () -> Unit,
    /** Whether this slot has a submenu at all — a lone tool has nothing to open. */
    opensSurface: Boolean,
    circuitKind: ShapeKind? = null,
    onEditFigure: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(ToolStripMetrics.slotSize)
            .background(if (active) DesktopTheme.activeSlot else Color.Transparent, DesktopTheme.controlShape)
            .hoverFill(DesktopTheme.controlShape, active = active, focused = focused)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onPointerEvent(PointerEventType.Enter) { onHover(true) }
            .onPointerEvent(PointerEventType.Exit) { onHover(false) }
            .pointerInput(group, selection) {
                detectTapGestures(
                    onTap = {
                        // One click **arms** the slot; the submenu is the second click, on the slot that is
                        // already picked. A circuit family is no exception — its palette is a surface and
                        // must not open from the single click that arms it (the chevron opens it at once).
                        when (val click = slotClick(group, selection)) {
                            is SlotClick.Pick -> onSelect(click.tool)
                            SlotClick.OpenPicker -> onOpenPicker()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ToolStripMetrics.glyphSize)) {
            val color = if (active) DesktopTheme.accent else DesktopTheme.chromeInk
            if (circuitKind != null) drawCircuitSymbol(circuitKind, color)
            else drawToolGlyph(glyph, color)
        }
        if (badge != null) {
            // The figure is drawn from this number, so the slot says which one it is — tucked into the
            // corner the chevron leaves free, clear of the drawing in the middle.
            Text(
                text = badge,
                color = if (active) DesktopTheme.accent else DesktopTheme.chromeInk,
                style = DesktopTheme.numeric,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 2.dp),
            )
        }
        if (onEditFigure != null) {
            // The scalene edit: 16 dp of target in a 36 dp slot, so it is a button and not a hairline.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .pointerInput(onEditFigure) { detectTapGestures { onEditFigure() } },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(10.dp)) {
                    drawChromeGlyph(ChromeGlyph.EDIT, DesktopTheme.accent)
                }
            }
        }
        if (opensSurface) {
            // The marker, at the end the strip's own side leaves free, in a 16 dp target so a chevron
            // click never doubles as a pick of the face.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(SlotChevronTarget)
                    .pointerInput(group) { detectTapGestures { onOpenPicker() } },
                contentAlignment = Alignment.Center,
            ) {
                SlotChevron(active = active)
            }
        }
    }
}

/** The chevron's tap target: 16 dp inside a 36 dp slot, with the 7 dp mark centred in it. */
private val SlotChevronTarget = 16.dp

/** The subtle pill: the tool's name in the chrome's ink, its accelerator in the accent. */
@Composable
private fun TooltipPill(tool: DesktopTool, offset: IntOffset) {
    StripPill(label = tool.label, accent = tool.shortcut, offset = offset)
}

/**
 * The one hover pill of the window: a name in the ink and its accelerator in the accent.
 *
 * Shared with the ink slots ([StrokeSlot]), the header's commands and the status band, so the strip's own
 * words — a tool's key, a favourite's digit, a width's `S`/`M`/`L` — are drawn by one component and cannot
 * drift in style. It is a **label**, not a capsule: the 4 dp corner, one hairline of [DesktopTheme.borderStrong]
 * and the popup's own tone (`docs/interface-contract.md` §6).
 *
 * The pill appears only after the pointer has rested [ToolStripMetrics.tooltipDelayMs] on its trigger: a
 * pointer crossing the rail passes twenty slots on its way somewhere, and twenty pills on the way is a
 * strobe, not an answer. The caller drops the pill the moment the pointer leaves, which cancels the wait.
 */
@Composable
internal fun StripPill(label: String, accent: String, offset: IntOffset) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ToolStripMetrics.tooltipDelayMs)
        shown = true
    }
    if (!shown) return
    Popup(offset = offset, properties = FloatingSurface) {
        Row(
            Modifier
                .background(DesktopTheme.popupSurface, DesktopTheme.controlShape)
                .border(1.dp, DesktopTheme.borderStrong, DesktopTheme.controlShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = DesktopTheme.ink, style = DesktopTheme.label)
            // A command has no accelerator: the gap and the empty accent are dropped rather than
            // printed as a space before nothing.
            if (accent.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(accent, color = DesktopTheme.accent, style = DesktopTheme.numeric)
            }
        }
    }
}
