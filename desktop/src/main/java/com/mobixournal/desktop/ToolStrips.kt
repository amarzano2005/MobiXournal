package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.DesignTokens
import com.mobixournal.design.PenPalette
import com.mobixournal.design.ToolGlyph
import com.mobixournal.render.CircuitFamily
import com.mobixournal.render.ShapeKind

/**
 * The two tool placements of `docs/ui-modernization.md` §2, and the metrics that keep them honest.
 *
 * **A band in its own lane, never a bar over the page.** The rail stands in a lane [railWidth] wide
 * beside the canvas and the ribbon takes a [ribbonHeight] row under the header; neither is a floating
 * surface on the sheet, so the page is never underneath the glass and the whole visible sheet stays
 * writable (`docs/chrome.md`). Inside its lane each strip is a **capsule** — the tablet's own recipe,
 * 52 dp in a 64 dp lane — so the tools sit on a raised pane of glass rather than edge to edge on a
 * slab that happens to be the same colour.
 *
 * The strips only **set a [ToolSelection]** — this is the chrome Phase 2 asks for, and what a pen does
 * with the picked tool arrives with the desktop editing phase. Because of that, the two placements draw
 * the same list from [DesktopTool], in the enum's order, with the same glyphs and the same pills: a
 * test can therefore assert they offer *the same tools* rather than two hand-kept copies.
 */
object ToolStripMetrics {

    /** The rail's lane: 64 dp (the capsule plus its air), its own column beside the canvas. */
    val railWidth = DesktopTheme.railLaneWidth

    /** The ribbon's lane: 56 dp — the capsule plus the air above and below it. */
    val ribbonHeight = 56.dp

    /** The ribbon's floating capsule inside that lane: 48 dp, so a 40 dp slot keeps 4 dp of air. */
    val ribbonCapsuleHeight = 48.dp

    /** One tool slot — the compact button of the token table (40 dp). */
    val slotSize = DesignTokens.COMPACT_BUTTON_SIZE_DP.dp

    /** The glyph drawn inside a slot: monoline art at a size that keeps the strokes apart. */
    val glyphSize = 20.dp

    /** The gap between slots, and the strip's own padding. */
    val gap = 4.dp

    /** How far the hover pill sits from the slot it names. */
    val tooltipGap = 6.dp

    /**
     * Where slot 0 starts inside the rail's lane — the capsule's side air plus the column's padding.
     * Derived rather than written down, so the strip and a render test cannot disagree about it.
     */
    val railSlotLeft = (railWidth - DesktopTheme.railCapsuleWidth) / 2f + DesktopTheme.railLanePadding

    /** Where slot 0 starts down the rail's lane: the capsule's vertical air plus the column's padding. */
    val railSlotTop = DesktopTheme.railOuterPadding + gap

    /** Where the first slot starts across the ribbon: the capsule's air plus the row's padding. */
    val ribbonSlotLeft = DesktopTheme.railOuterPadding + DesktopTheme.railLanePadding

    /** The ribbon's slots are centred in the lane, so they start this far down it. */
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
    /** Which group's picker is open, as an index into [DESKTOP_TOOL_GROUPS]; null when none is. */
    val openGroup: Int? = null,
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
     * The page's own two questions, drawn after the strip's divider: what the sheet *is* (its paper and
     * ruling) and how big it is. They are commands on the page, not ink, which is why they stand apart
     * from the tools and the palette (`docs/interface-contract.md` §2).
     */
    val onPageBackground: () -> Unit = {},
    val onPageFormat: () -> Unit = {},
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
 * The tool slots and the ink slots are ~900 dp tall together, so the lane scrolls rather than dropping
 * its last tools on a window that is shorter than that — a tool that is off screen because the window is
 * short would be a tool the user has to guess at.
 */
@Composable
fun ToolRail(
    state: ToolStripState,
    onSelect: (DesktopTool) -> Unit,
    ink: InkActions = InkActions(),
    modifier: Modifier = Modifier,
) {
    // The lane is **air**, not a band: the rail itself is the capsule, exactly as the tablet draws it —
    // 52 dp inside a 64 dp lane, 6 dp of air each side and 8 dp above and below, its own shadow and its
    // own rim. Painting the lane as a band as well stacked two layers of 0.85 glass under the capsule
    // (0.85 over 0.85 is 0.98), so the window's rail came out #161618 where the tablet's is #131314.
    Box(
        modifier
            .width(DesktopTheme.railLaneWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(vertical = DesktopTheme.railOuterPadding)
                .width(DesktopTheme.railCapsuleWidth)
                .fillMaxHeight()
                .glassCapsule(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = DesktopTheme.railLanePadding,
                        vertical = ToolStripMetrics.gap,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ToolStripMetrics.gap),
            ) {
                for ((index, group) in DESKTOP_TOOL_GROUPS.withIndex()) {
                    ToolSlot(
                        group = group,
                        selection = state.selection,
                        pickerOpen = state.openGroup == index,
                        onSelect = onSelect,
                        onOpenPicker = { state.onOpenGroup(index) },
                        onClosePicker = { state.onOpenGroup(null) },
                        circuitKinds = state.circuitKinds,
                        circuitPickerOpen = state.circuitPickerOpen,
                        onCircuitKind = state.onCircuitKind,
                        onCircuitPicker = state.onCircuitPicker,
                        side = StripSide.RIGHT,
                    )
                }
                StripDivider(vertical = true)
                ColourSlot(
                    ink = state.ink,
                    palette = state.palette,
                    custom = state.custom,
                    favourites = state.favourites,
                    onPick = ink.onColour,
                    onLineStyle = ink.onLineStyle,
                    onEditCustom = ink.onEditCustom,
                    onAddColour = ink.onAddColour,
                    side = StripSide.RIGHT,
                )
                SizeSlot(state.ink, ink.onWidth, side = StripSide.RIGHT)
                // The page itself, after its own rule: paper and format act on the sheet under the ink,
                // not on the ink, so they are the last two objects of the strip rather than four more
                // tools in it.
                StripDivider(vertical = true)
                PageSetupSlots(state, StripSide.RIGHT)
            }
        }
    }
}

/**
 * Option A: the horizontal ribbon, a band under the header.
 *
 * It scrolls horizontally for the same reason the rail scrolls vertically: eighteen slots do not fit a
 * narrow window, and a ribbon that clipped them would silently hide the STEM half of the tool set.
 */
@Composable
fun ToolRibbon(
    state: ToolStripState,
    onSelect: (DesktopTool) -> Unit,
    ink: InkActions = InkActions(),
    modifier: Modifier = Modifier,
) {
    // The ribbon is the rail laid on its side, and it wears the same material: a capsule floating in
    // its lane's air, not a band with a second sheet of glass behind it (see the rail's note).
    Box(
        modifier
            .fillMaxWidth()
            .height(ToolStripMetrics.ribbonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ToolStripMetrics.ribbonCapsuleHeight)
                .padding(horizontal = DesktopTheme.railOuterPadding)
                .glassCapsule(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DesktopTheme.railLanePadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolStripMetrics.gap),
            ) {
                for ((index, group) in DESKTOP_TOOL_GROUPS.withIndex()) {
                    ToolSlot(
                        group = group,
                        selection = state.selection,
                        pickerOpen = state.openGroup == index,
                        onSelect = onSelect,
                        onOpenPicker = { state.onOpenGroup(index) },
                        onClosePicker = { state.onOpenGroup(null) },
                        circuitKinds = state.circuitKinds,
                        circuitPickerOpen = state.circuitPickerOpen,
                        onCircuitKind = state.onCircuitKind,
                        onCircuitPicker = state.onCircuitPicker,
                        side = StripSide.BELOW,
                    )
                }
                StripDivider(vertical = false)
                ColourSlot(
                    ink = state.ink,
                    palette = state.palette,
                    custom = state.custom,
                    favourites = state.favourites,
                    onPick = ink.onColour,
                    onLineStyle = ink.onLineStyle,
                    onEditCustom = ink.onEditCustom,
                    onAddColour = ink.onAddColour,
                    side = StripSide.BELOW,
                )
                SizeSlot(state.ink, ink.onWidth, side = StripSide.BELOW)
                StripDivider(vertical = false)
                PageSetupSlots(state, StripSide.BELOW)
            }
        }
    }
}

/**
 * A hairline between the tools and the ink slots — the only rule inside a strip.
 *
 * It is a **gap with one edge**, not a second band: the tools are what you draw with, the colour and the
 * width are what they draw *in*, and the strip already has an edge on the page side. The vertical rail
 * gets a short horizontal stroke, the ribbon a full-height vertical one.
 */
@Composable
private fun StripDivider(vertical: Boolean) {
    if (vertical) {
        Box(Modifier.width(20.dp).height(1.dp).background(DesktopTheme.hairline))
    } else {
        Box(Modifier.width(1.dp).height(20.dp).background(DesktopTheme.hairline))
    }
}



/**
 * One **slot** of a strip: the glyph of the tool it currently faces, in a 40 dp frame, the picked one
 * wearing the steel [`DesktopTheme.activeSlot`] rather than the accent — the app's rule for a *picked*
 * slot, and the tablet's own `primaryContainer` (the steel `#38383E`) with the glyph in the accent
 * (`docs/ui-modernization.md`).
 *
 * A slot that [ToolGroup.hasPicker] carries the chevron and opens the group's picker — by clicking the
 * chevron, by right-clicking anywhere on the slot, or by holding it; a click on the glyph picks the tool
 * the slot faces, exactly as pressing that tool's key does. The picker only re-faces the slot.
 *
 * Hovering shows the name-and-hotkey pill the doc asks for. It is a [Popup] rather than a box inside
 * the strip because the strip is a narrow band: a pill laid out inside it would be clipped by it.
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    side: StripSide,
) {
    val tool = group.faceFor(selection)
    val family = circuitFamilyOf(tool)
    val active = tool == selection.current
    // What the slot's chevron (and a second click on a picked slot) opens: for a circuit slot the
    // family's **palette of parts**, for every other grouped slot the list of its members. A circuit
    // group has one member, so its own list would be empty — the palette is its submenu.
    val openSurface: () -> Unit = {
        if (family != null) onCircuitPicker(family) else onOpenPicker()
    }
    var hovered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val slotPx = with(density) { ToolStripMetrics.slotSize.roundToPx() }
    val gapPx = with(density) { ToolStripMetrics.tooltipGap.roundToPx() }

    Box {
        SlotFace(
            tool = tool,
            group = group,
            selection = selection,
            active = active,
            hovered = hovered,
            onHover = { hovered = it },
            onSelect = onSelect,
            onOpenPicker = openSurface,
            opensSurface = group.opensSubmenu(),
        )
        // The pill stands aside while the slot's own surface is up — either of them, so a circuit slot's
        // palette is not covered by its tooltip.
        if (hovered && !pickerOpen && !(family != null && circuitPickerOpen == family)) {
            TooltipPill(
                tool = tool,
                offset = when (side) {
                    StripSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
                    StripSide.BELOW -> IntOffset(0, slotPx + gapPx)
                },
            )
        }
        SlotPickerStack(
            group = group,
            selection = selection,
            tool = tool,
            pickerOpen = pickerOpen,
            circuitKinds = circuitKinds,
            circuitPickerOpen = circuitPickerOpen,
            onSelect = onSelect,
            onClosePicker = onClosePicker,
            onCircuitKind = onCircuitKind,
            onCircuitPicker = onCircuitPicker,
            offset = when (side) {
                StripSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
                StripSide.BELOW -> IntOffset(0, slotPx + gapPx)
            },
        )
    }
}

/**
 * The lists a slot can open, in one place: the group's members and — for the Circuit slot — the
 * component palette. Which of the two belongs to which slot is the window's rule, so it lives beside the
 * slot rather than in each strip.
 */
@Composable
private fun SlotPickerStack(
    group: ToolGroup,
    selection: ToolSelection,
    tool: DesktopTool,
    pickerOpen: Boolean,
    circuitKinds: Map<CircuitFamily, ShapeKind>,
    circuitPickerOpen: CircuitFamily?,
    onSelect: (DesktopTool) -> Unit,
    onClosePicker: () -> Unit,
    onCircuitKind: (CircuitFamily, ShapeKind) -> Unit,
    onCircuitPicker: (CircuitFamily?) -> Unit,
    offset: IntOffset,
) {
    if (pickerOpen) {
        ToolGroupPicker(
            group = group,
            selection = selection,
            onPick = { member ->
                onSelect(member)
                onClosePicker()
            },
            onDismiss = onClosePicker,
            offset = offset,
        )
    }
    val family = circuitFamilyOf(tool)
    if (family != null && circuitPickerOpen == family) {
        CircuitKindPicker(
            family = family,
            current = circuitKinds[family] ?: family.face,
            onPick = { kind ->
                onCircuitKind(family, kind)
                onCircuitPicker(null)
            },
            onDismiss = { onCircuitPicker(null) },
            offset = offset,
        )
    }
}

/**
 * The slot's face: its glyph, its background and the two gestures it answers — a click that picks (or
 * asks which member) and, for a grouped tool, a chevron with a tap target of its own.
 *
 * Split from [ToolSlot] so the slot itself is placement and the surfaces it opens, and the 40 dp frame
 * with its five states is one component both strips share.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SlotFace(
    tool: DesktopTool,
    group: ToolGroup,
    selection: ToolSelection,
    active: Boolean,
    hovered: Boolean,
    onHover: (Boolean) -> Unit,
    onSelect: (DesktopTool) -> Unit,
    onOpenPicker: () -> Unit,
    /** Whether this slot has a submenu at all — a lone tool has nothing to open. */
    opensSurface: Boolean,
) {
    Box(
        Modifier
            .size(ToolStripMetrics.slotSize)
            .background(
                when {
                    active -> DesktopTheme.activeSlot
                    hovered -> DesktopTheme.hoverSlot
                    else -> Color.Transparent
                },
                DesktopTheme.railSlotShape,
            )
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
            // The tablet's rule: a glyph on the glass is the chrome ink, and the picked one is the
            // accent. A grey glyph beside a white one reads as disabled, not as a different tool.
            drawToolGlyph(
                tool,
                color = if (active) DesktopTheme.accent else DesktopTheme.chromeInk,
            )
        }
        if (opensSurface) {
            // The marker, at the end the strip's own side leaves free, with its own tap target so a
            // chevron click never doubles as a pick of the face.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .pointerInput(group) { detectTapGestures { onOpenPicker() } },
            ) {
                SlotChevron(active = active)
            }
        }
    }
}

/**
 * The strip's two **page** slots: the sheet's paper and its format, drawn after the divider.
 *
 * They are commands, not tools — nothing about them becomes the picked slot — but they wear the same
 * frame, hover and glyph as every other object on the strip, because the strip is one family and a
 * command that looked like a link beside it would be a second visual language (`docs/interface-contract.md`
 * §4: the glyph comes from the catalogue, which is why `ToolGlyph.PAGE` and `PAGE_SIZE` exist there).
 *
 * The **defaults** for new sheets are not a third command: they are part of the page's settings, and they
 * live inside the format panel (`DefaultLine`), under the page's own size — the tablet keeps them in one
 * place too, and a default must never be a button away from the size it belongs to.
 */
@Composable
private fun PageSetupSlots(state: ToolStripState, side: StripSide) {
    StripCommandSlot(
        glyph = ToolGlyph.PAGE,
        label = "Page background",
        offsetSide = side,
        onClick = state.onPageBackground,
    )
    StripCommandSlot(
        glyph = ToolGlyph.PAGE_SIZE,
        label = "Page format",
        offsetSide = side,
        onClick = state.onPageFormat,
    )
}

/**
 * One command of a strip: the same 40 dp frame and glyph as a tool slot, with the tooltip pill a
 * tool has and no picked state (a command is over the moment it opens its surface).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StripCommandSlot(
    glyph: ToolGlyph,
    label: String,
    offsetSide: StripSide,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val slotPx = with(density) { ToolStripMetrics.slotSize.roundToPx() }
    val gapPx = with(density) { ToolStripMetrics.tooltipGap.roundToPx() }
    Box {
        Box(
            Modifier
                .size(ToolStripMetrics.slotSize)
                .background(
                    if (hovered) DesktopTheme.hoverSlot else Color.Transparent,
                    DesktopTheme.railSlotShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(glyph) { detectTapGestures { onClick() } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(ToolStripMetrics.glyphSize)) {
                drawToolGlyph(glyph, DesktopTheme.chromeInk)
            }
        }
        if (hovered) {
            StripPill(
                label = label,
                accent = "",
                offset = when (offsetSide) {
                    StripSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
                    StripSide.BELOW -> IntOffset(0, slotPx + gapPx)
                },
            )
        }
    }
}

/** The subtle pill: the tool's name in the chrome's ink, its accelerator in the accent. */
@Composable
private fun TooltipPill(tool: DesktopTool, offset: IntOffset) {
    StripPill(label = tool.label, accent = tool.shortcut, offset = offset)
}

/**
 * The one hover pill of a strip: a name in the chrome's ink and its accelerator in the accent.
 *
 * Shared with the ink slots ([ColourSlot], [SizeSlot]) so the strip's own words — a tool's key, a
 * favourite's digit, a width's `S`/`M`/`L` — are drawn by one component and cannot drift in style.
 */
@Composable
internal fun StripPill(label: String, accent: String, offset: IntOffset) {
    Popup(offset = offset, properties = FloatingSurface) {
        Row(
            Modifier
                .glassSurface()
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
