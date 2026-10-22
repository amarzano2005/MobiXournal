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
 * Option B: the vertical rail, docked beside the canvas.
 *
 * Fifteen slots are ~660 dp tall, so the lane scrolls rather than dropping its last tools on a window
 * that is shorter than that — a tool that is off screen because the window is short would be a tool the
 * user has to guess at.
 */
@Composable
fun ToolRail(
    selection: ToolSelection,
    onSelect: (DesktopTool) -> Unit,
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
            ToolSlot(tool, active = tool == selection.current, onSelect = onSelect, side = TooltipSide.RIGHT)
        }
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
    selection: ToolSelection,
    onSelect: (DesktopTool) -> Unit,
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
            ToolSlot(tool, active = tool == selection.current, onSelect = onSelect, side = TooltipSide.BELOW)
        }
    }
}

/** Which side of its slot a strip's hover pill opens on: the rail has no room below it. */
private enum class TooltipSide { RIGHT, BELOW }

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
    side: TooltipSide,
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
                    TooltipSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
                    TooltipSide.BELOW -> IntOffset(0, slotPx + gapPx)
                },
            )
        }
    }
}

/** The subtle pill: the tool's name in the chrome's ink, its accelerator in the accent. */
@Composable
private fun TooltipPill(tool: DesktopTool, offset: IntOffset) {
    Popup(offset = offset) {
        Row(
            Modifier
                .background(DesktopTheme.panel, DesktopTheme.panelShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tool.label, color = DesktopTheme.ink, style = DesktopTheme.label)
            Spacer(Modifier.width(6.dp))
            Text(tool.shortcut, color = DesktopTheme.accent, style = DesktopTheme.numeric)
        }
    }
}
