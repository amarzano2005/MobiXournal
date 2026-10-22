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

/**
 * The two tool placements of `docs/ui-modernization.md` §2, and the metrics that keep them honest.
 *
 * **A band in its own lane, never a bar over the page.** The rail stands in a lane [railWidth] wide
 * beside the canvas and the ribbon takes a [ribbonHeight] row under the header; neither is a floating
 * surface on the sheet, so the page is never underneath the glass and the whole visible sheet stays
 * writable (`docs/chrome.md`). That is the one structural difference from the Android rail, which is a
 * capsule in a lane of the same shape.
 *
 * The strips only **set a [ToolSelection]** — this is the chrome Phase 2 asks for, and what a pen does
 * with the picked tool arrives with the desktop editing phase. Because of that, the two placements draw
 * the same list from [DesktopTool], in the enum's order, with the same glyphs and the same pills: a
 * test can therefore assert they offer *the same tools* rather than two hand-kept copies.
 */
object ToolStripMetrics {

    /** The rail's lane: 48 dp, its own column beside the canvas. */
    val railWidth = 48.dp

    /** The ribbon's band: 48 dp, a row under the header and above the workspace. */
    val ribbonHeight = 48.dp

    /** One tool slot — the compact button of the token table (40 dp). */
    val slotSize = DesignTokens.COMPACT_BUTTON_SIZE_DP.dp

    /** The glyph drawn inside a slot: monoline art at a size that keeps the strokes apart. */
    val glyphSize = 20.dp

    /** The gap between slots, and the strip's own padding. */
    val gap = 4.dp

    /** How far the hover pill sits from the slot it names. */
    val tooltipGap = 6.dp
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
    val favourites: List<Int> = PenPalette.COLORS.take(PaletteCodec.FAVOURITES),
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
    val onEditCustom: () -> Unit = {},
    val onAddColour: () -> Unit = {},
)

/**
 * Option B: the vertical rail, docked beside the canvas.
 *
 * Fifteen slots are ~660 dp tall, so the lane scrolls rather than dropping its last tools on a window
 * that is shorter than that — a tool that is off screen because the window is short would be a tool the
 * user has to guess at.
 */
@Composable
fun ToolRail(
    state: ToolStripState,
    onSelect: (DesktopTool) -> Unit,
    ink: InkActions = InkActions(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(ToolStripMetrics.railWidth)
            .fillMaxHeight()
            .background(DesktopTheme.glass)
            .verticalScroll(rememberScrollState())
            .padding(vertical = ToolStripMetrics.gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ToolStripMetrics.gap),
    ) {
        for (tool in DesktopTool.entries) {
            ToolSlot(tool, active = tool == state.selection.current, onSelect = onSelect, side = StripSide.RIGHT)
        }
        StripDivider(vertical = true)
        ColourSlot(
            ink = state.ink,
            palette = state.palette,
            favourites = state.favourites,
            onPick = ink.onColour,
            onEditCustom = ink.onEditCustom,
            onAddColour = ink.onAddColour,
            side = StripSide.RIGHT,
        )
        SizeSlot(state.ink, ink.onWidth, side = StripSide.RIGHT)
    }
}

/**
 * Option A: the horizontal ribbon, a band under the header.
 *
 * It scrolls horizontally for the same reason the rail scrolls vertically: fifteen slots do not fit a
 * narrow window, and a ribbon that clipped them would silently hide the STEM half of the tool set.
 */
@Composable
fun ToolRibbon(
    state: ToolStripState,
    onSelect: (DesktopTool) -> Unit,
    ink: InkActions = InkActions(),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(ToolStripMetrics.ribbonHeight)
            .background(DesktopTheme.glass)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ToolStripMetrics.gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolStripMetrics.gap),
    ) {
        for (tool in DesktopTool.entries) {
            ToolSlot(tool, active = tool == state.selection.current, onSelect = onSelect, side = StripSide.BELOW)
        }
        StripDivider(vertical = false)
        ColourSlot(
            ink = state.ink,
            palette = state.palette,
            favourites = state.favourites,
            onPick = ink.onColour,
            onEditCustom = ink.onEditCustom,
            onAddColour = ink.onAddColour,
            side = StripSide.BELOW,
        )
        SizeSlot(state.ink, ink.onWidth, side = StripSide.BELOW)
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
 * One tool: its glyph in a 40 dp capsule, the active one wearing the steel [`DesktopTheme.activeSlot`]
 * rather than the accent — the app's rule for a *picked* slot (`docs/ui-modernization.md`).
 *
 * Hovering shows the name-and-hotkey pill the doc asks for. It is a [Popup] rather than a box inside
 * the strip because the strip is a narrow band: a pill laid out inside it would be clipped by it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ToolSlot(
    tool: DesktopTool,
    active: Boolean,
    onSelect: (DesktopTool) -> Unit,
    side: StripSide,
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
                    if (active) DesktopTheme.activeSlot else Color.Transparent,
                    DesktopTheme.capsuleShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(tool) { detectTapGestures { onSelect(tool) } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(ToolStripMetrics.glyphSize)) {
                drawToolGlyph(
                    tool,
                    color = if (active) DesktopTheme.ink else DesktopTheme.inkSecondary,
                    stroke = DesktopTheme.glyphStroke.toPx(),
                )
            }
        }
        if (hovered) {
            TooltipPill(
                tool = tool,
                offset = when (side) {
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
    Popup(offset = offset) {
        Row(
            Modifier
                .glassSurface()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = DesktopTheme.ink, style = DesktopTheme.label)
            Spacer(Modifier.width(6.dp))
            Text(accent, color = DesktopTheme.accent, style = DesktopTheme.numeric)
        }
    }
}
