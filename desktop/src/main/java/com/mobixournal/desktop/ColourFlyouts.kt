package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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
 * The key that picks [colour]: its favourite's digit, or all three digits when the colour is not one.
 *
 * The pill reads the same [PenPalette] values the keyboard handler does, so "what the strip says" and
 * "what the key does" cannot disagree.
 */
internal fun colourKeyLabel(favourites: List<Int>, colour: Int): String {
    val index = favourites.indexOf(colour)
    return if (index in 0 until PaletteCodec.FAVOURITES) "${index + 1}" else "1 2 3"
}

/**
 * The colour slot: the ink's colour in a capsule. A click opens the palette flyout, which is drawn in
 * the floating material with the swatches in one horizontally scrolling row — the compact shape
 * `docs/colours.md` asks for, so a long palette widens the flyout instead of growing it downward.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ColourSlot(
    ink: DesktopInk,
    palette: List<Int>,
    favourites: List<Int> = emptyList(),
    onPick: (Int) -> Unit,
    onEditCustom: () -> Unit = {},
    onAddColour: () -> Unit = {},
    side: StripSide,
) {
    var open by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    val anchor = flyoutOffset(side)
    Box {
        Box(
            Modifier
                .size(ToolStripMetrics.slotSize)
                .background(
                    if (open) DesktopTheme.activeSlot else Color.Transparent,
                    DesktopTheme.capsuleShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(Unit) { detectTapGestures { open = !open } },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(InkSlotMetrics.swatchSize)
                    .background(Color(ink.colour), DesktopTheme.capsuleShape)
                    // The ink can be black or white, and the glass is neither: one ring keeps a black
                    // ink visible on the dark strip instead of reading as a hole in it.
                    .border(1.dp, DesktopTheme.inkMuted, DesktopTheme.capsuleShape),
            )
        }
        if (hovered) {
            StripPill(label = "Colour", accent = colourKeyLabel(favourites, ink.colour), offset = anchor)
        }
        if (open) {
            Popup(offset = anchor, onDismissRequest = { open = false }) {
                PaletteFlyout(
                    palette = palette,
                    current = ink.colour,
                    custom = ink.custom,
                    onPick = { colour ->
                        onPick(colour)
                        open = false
                    },
                    onEditCustom = {
                        onEditCustom()
                        open = false
                    },
                    onAddColour = {
                        onAddColour()
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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SizeSlot(ink: DesktopInk, onPick: (Float) -> Unit, side: StripSide) {
    var open by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    val anchor = flyoutOffset(side)
    Box {
        Box(
            Modifier
                .size(ToolStripMetrics.slotSize)
                .background(
                    if (open) DesktopTheme.activeSlot else Color.Transparent,
                    DesktopTheme.capsuleShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
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
        if (hovered) {
            StripPill(
                label = "Width",
                accent = PenPalette.WIDTH_LABELS.getOrElse(PenPalette.widthSlot(ink.width)) { "?" },
                offset = anchor,
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

/**
 * The palette, as one scrolling row: the stored swatches, then the editable custom slot, then the
 * add-colour swatch — the three parts `docs/colours.md` describes, in that order.
 */
@Composable
internal fun PaletteFlyout(
    palette: List<Int>,
    current: Int,
    custom: Int?,
    onPick: (Int) -> Unit,
    onEditCustom: () -> Unit,
    onAddColour: () -> Unit,
) {
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
        CustomSwatch(custom = custom, onPick = onPick, onEdit = onEditCustom)
        AddSwatch(onAdd = onAddColour)
    }
}

/**
 * The editable custom slot: filled with the colour the user defined, or an empty ring carrying the pencil
 * when it holds nothing. A tap on a set slot picks it, a tap on the empty one opens the panel — and a
 * long press redefines it either way, the same gesture the tablet's palette teaches.
 */
@Composable
private fun CustomSwatch(custom: Int?, onPick: (Int) -> Unit, onEdit: () -> Unit) {
    val side = InkSlotMetrics.swatchSize + InkSlotMetrics.swatchRing * 2
    Box(
        Modifier
            .size(side)
            .background(custom?.let(::Color) ?: Color.Transparent, DesktopTheme.capsuleShape)
            .border(
                1.dp,
                if (custom == null) DesktopTheme.hairline else DesktopTheme.inkMuted,
                DesktopTheme.capsuleShape,
            )
            .pointerInput(custom) {
                detectTapGestures(
                    onTap = { if (custom == null) onEdit() else onPick(custom) },
                    onLongPress = { onEdit() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (custom == null) {
            Canvas(Modifier.size(12.dp)) {
                drawToolGlyph(DesktopTool.PEN, DesktopTheme.inkSecondary, DesktopTheme.glyphStroke.toPx())
            }
        }
    }
}

/** The add-colour swatch: a plus in a hairline ring, opening the panel on a colour to append. */
@Composable
private fun AddSwatch(onAdd: () -> Unit) {
    val side = InkSlotMetrics.swatchSize + InkSlotMetrics.swatchRing * 2
    Box(
        Modifier
            .size(side)
            .border(1.dp, DesktopTheme.hairline, DesktopTheme.capsuleShape)
            .pointerInput(Unit) { detectTapGestures { onAdd() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) {
            val colour = DesktopTheme.accent
            val stroke = DesktopTheme.glyphStroke.toPx()
            drawLine(colour, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke)
            drawLine(colour, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke)
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
