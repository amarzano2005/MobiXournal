package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.PenPalette

/**
 * The two ink slots at the tail of each strip — the colour in use and the width it draws at — and the
 * glass flyouts they open.
 *
 * They are **slots, not extra bands**: each is a 40 dp capsule in the strip, exactly like a tool slot, and
 * the flyout is the floating material ([DesktopTheme.panel], the 20 dp panel corner) opening beside the
 * slot, so nothing is ever laid over the page. A rail opens its flyouts to the right and a ribbon below,
 * for the reason the tooltips do: the strip has no room on the other side.
 *
 * The palette comes from [PenPalette] through the shell's settings, and picking a swatch only sets the
 * ink ([DesktopInk]) — what a pen does with it arrives with the desktop editing phase. The editable
 * custom slot and the add-colour swatch are the picker's own task
 * (`desktop-custom-colour-picker`); this file offers the stored palette and the three width slots.
 */
internal object InkSlotMetrics {

    /** One swatch inside a flyout, and the size of the colour capsule in the strip. */
    val swatchSize = 22.dp

    /** The gap between swatches, and the padding that turns the picked one into a ring. */
    val swatchGap = 6.dp

    /** The ring the picked swatch wears: the slot's padding, in dp. */
    val swatchRing = 2.dp
}

/** The diameter a width is drawn at in a slot or a flyout: 3 dp per point, so S and L are tellable apart. */
internal fun dotSize(width: Float): Dp = (width * 3f).coerceIn(2f, 14f).dp

/**
 * The colour slot: the ink's colour in a capsule. A click opens the palette flyout, which is drawn in
 * the floating material with the swatches in one horizontally scrolling row — the compact shape
 * `docs/colours.md` asks for, so a long palette widens the flyout instead of growing it downward.
 */
@Composable
internal fun ColourSlot(
    ink: DesktopInk,
    palette: List<Int>,
    onPick: (Int) -> Unit,
    side: StripSide,
) {
    var open by remember { mutableStateOf(false) }
    val anchor = flyoutOffset(side)
    Box {
        Box(
            Modifier
                .size(ToolStripMetrics.slotSize)
                .background(
                    if (open) DesktopTheme.activeSlot else Color.Transparent,
                    DesktopTheme.capsuleShape,
                )
                .pointerInput(Unit) { detectTapGestures { open = !open } },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(InkSlotMetrics.swatchSize)
                    .background(Color(ink.colour), DesktopTheme.capsuleShape)
                    // The ink can be black or white, and the glass is neither: one hairline keeps a
                    // black ink visible on the dark strip instead of a hole in it.
                    .border(1.dp, DesktopTheme.inkMuted, DesktopTheme.capsuleShape),
            )
        }
        if (open) {
            Popup(offset = anchor, onDismissRequest = { open = false }) {
                PaletteFlyout(
                    palette = palette,
                    current = ink.colour,
                    onPick = { colour ->
                        onPick(colour)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * The size slot: a dot at the current width, in the current colour. Its flyout offers the three width
 * slots (`S`/`M`/`L` from [PenPalette]), the one in use wearing the steel.
 */
@Composable
internal fun SizeSlot(ink: DesktopInk, onPick: (Float) -> Unit, side: StripSide) {
    var open by remember { mutableStateOf(false) }
    val anchor = flyoutOffset(side)
    Box {
        Box(
            Modifier
                .size(ToolStripMetrics.slotSize)
                .background(
                    if (open) DesktopTheme.activeSlot else Color.Transparent,
                    DesktopTheme.capsuleShape,
                )
                .pointerInput(Unit) { detectTapGestures { open = !open } },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(dotSize(ink.width))
                    .background(Color(ink.colour), CircleShape)
                    .border(1.dp, DesktopTheme.inkMuted, CircleShape),
            )
        }
        if (open) {
            Popup(offset = anchor, onDismissRequest = { open = false }) {
                SizeFlyout(
                    ink = ink,
                    onPick = { width ->
                        onPick(width)
                        open = false
                    },
                )
            }
        }
    }
}

/** The palette, as one scrolling row of swatches; the picked one wears the steel ring. */
@Composable
private fun PaletteFlyout(palette: List<Int>, current: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier
            .background(DesktopTheme.panel, DesktopTheme.panelShape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSlotMetrics.swatchGap),
    ) {
        for (colour in palette) {
            Box(
                Modifier
                    .size(InkSlotMetrics.swatchSize + InkSlotMetrics.swatchRing * 2)
                    .background(
                        if (colour == current) DesktopTheme.activeSlot else Color.Transparent,
                        DesktopTheme.capsuleShape,
                    )
                    .padding(InkSlotMetrics.swatchRing)
                    .background(Color(colour), DesktopTheme.capsuleShape)
                    .border(1.dp, DesktopTheme.inkMuted, DesktopTheme.capsuleShape)
                    .pointerInput(colour) { detectTapGestures { onPick(colour) } },
            )
        }
    }
}

/** The three width slots, each a dot at its own width and its label under it. */
@Composable
private fun SizeFlyout(ink: DesktopInk, onPick: (Float) -> Unit) {
    val picked = PenPalette.widthSlot(ink.width)
    Row(
        Modifier
            .background(DesktopTheme.panel, DesktopTheme.panelShape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSlotMetrics.swatchGap),
    ) {
        PenPalette.DEFAULT_WIDTHS.forEachIndexed { index, width ->
            val active = index == picked
            Column(
                Modifier
                    .background(
                        if (active) DesktopTheme.activeSlot else Color.Transparent,
                        DesktopTheme.capsuleShape,
                    )
                    .pointerInput(width) { detectTapGestures { onPick(width) } }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(dotSize(width))
                        .background(Color(ink.colour), CircleShape)
                        .border(1.dp, DesktopTheme.inkMuted, CircleShape),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = PenPalette.WIDTH_LABELS.getOrElse(index) { "${index + 1}" },
                    color = if (active) DesktopTheme.ink else DesktopTheme.inkSecondary,
                    style = DesktopTheme.numeric,
                )
            }
        }
    }
}

/** Where a flyout opens: past the slot on the strip's free side, in pixels from the slot's origin. */
@Composable
private fun flyoutOffset(side: StripSide): IntOffset {
    val density = LocalDensity.current
    val slotPx = with(density) { ToolStripMetrics.slotSize.roundToPx() }
    val gapPx = with(density) { ToolStripMetrics.tooltipGap.roundToPx() }
    return when (side) {
        StripSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
        StripSide.BELOW -> IntOffset(0, slotPx + gapPx)
    }
}
