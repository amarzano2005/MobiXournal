package com.mobixournal.desktop.tools
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.PaletteCodec
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.drawToolGlyph

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
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.LineStyle

/**
 * The two ink slots at the tail of each strip — the colour in use and the width it draws at — and the
 * glass flyouts they open.
 *
 * They are **slots, not extra bands**: each is a 40 dp capsule in the strip, exactly like a tool slot, and
 * the flyout is the floating material ([DesktopTheme.floating], the 20 dp panel corner) opening beside the
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

/** The diameter a width is drawn at **in the strip's slot**: 3 dp per point, so S and L differ. */
internal fun dotSize(width: Float): Dp = (width * 3f).coerceIn(2f, 14f).dp

/**
 * The diameter a width is **offered** at in the stroke panel: twice the slot's scale, so the three dots
 * are read at a glance rather than measured. A picker is looked at, a slot is glanced at.
 */
internal fun widthOptionDotSize(width: Float): Dp = (width * 6f).coerceIn(4f, 20f).dp

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
 * The **stroke's own settings**, as one slot: the ink in a capsule, drawn at the width it will draw at.
 *
 * It used to be two slots — a colour and a width — parked at the tail of the strip, away from the tool
 * that spends them. It stands now **under the highlighter whose ink it answers for**, and its panel asks
 * the three questions the tablet's pop-up asks together: the colour, the line style and the width
 * (`docs/colours.md`). Picking a swatch, a pattern or a width leaves the panel up, because the three are
 * one decision; the reader closes it on the slot again, or on the page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StrokeSlot(
    ink: DesktopInk,
    palette: List<Int>,
    /** The palette's editable custom slot — shared by both tools, hence not part of [ink]. */
    custom: Int? = null,
    favourites: List<Int> = emptyList(),
    onPick: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onLineStyle: (LineStyle) -> Unit = {},
    onEditCustom: () -> Unit = {},
    onAddColour: () -> Unit = {},
    side: StripSide,
) {
    var open by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    // A second click on the slot closes its panel instead of reopening it (see [DismissGuard]).
    val guard = remember { DismissGuard() }
    val anchor = flyoutOffset(side)
    Box {
        Box(
            Modifier
                .size(ToolStripMetrics.slotSize)
                .background(
                    if (open) DesktopTheme.activeSlot else Color.Transparent,
                    DesktopTheme.railSlotShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(Unit) { detectTapGestures { open = !open && guard.mayOpen(Unit) } },
            contentAlignment = Alignment.Center,
        ) {
            // The ink at its own width: one picture for the colour and the size, as the tablet's stroke
            // button draws it. A black or white ink would read as a hole in the glass, so the dot keeps
            // the ring the palette's swatches wear.
            Box(
                Modifier
                    .size(dotSize(ink.width))
                    .background(Color(ink.colour), CircleShape)
                    .border(1.dp, DesktopTheme.inkMuted, CircleShape),
            )
        }
        if (hovered && !open) {
            StripPill(label = "Stroke", accent = colourKeyLabel(favourites, ink.colour), offset = anchor)
        }
        if (open) {
            Popup(
                offset = anchor,
                onDismissRequest = {
                    guard.closed(Unit)
                    open = false
                },
                properties = FloatingSurface,
            ) {
                StrokeFlyout(
                    ink = ink,
                    palette = palette,
                    custom = custom,
                    onPick = onPick,
                    onWidth = onWidth,
                    onLineStyle = onLineStyle,
                    // The two panels a swatch opens are the window's own surfaces, so they dismiss this
                    // one: a second floating panel over the first would ask which one a key belongs to.
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

/** The panel behind that slot: the swatches, the four patterns and the three widths, in that order. */
@Composable
internal fun StrokeFlyout(
    ink: DesktopInk,
    palette: List<Int>,
    custom: Int?,
    onPick: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onLineStyle: (LineStyle) -> Unit,
    onEditCustom: () -> Unit,
    onAddColour: () -> Unit,
) {
    Column(Modifier.glassSurface().padding(horizontal = 8.dp, vertical = 6.dp)) {
        SwatchRow(
            palette = palette,
            current = ink.colour,
            custom = custom,
            onPick = onPick,
            onEditCustom = onEditCustom,
            onAddColour = onAddColour,
        )
        LineStylePicker(lineStyle = ink.lineStyle, onPick = onLineStyle)
        WidthRow(ink = ink, onPick = onWidth)
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
    lineStyle: LineStyle = LineStyle.PLAIN,
    onPick: (Int) -> Unit,
    onLineStyle: (LineStyle) -> Unit = {},
    onEditCustom: () -> Unit,
    onAddColour: () -> Unit,
) {
    Column(Modifier.glassSurface().padding(horizontal = 8.dp, vertical = 6.dp)) {
        SwatchRow(
            palette = palette,
            current = current,
            custom = custom,
            onPick = onPick,
            onEditCustom = onEditCustom,
            onAddColour = onAddColour,
        )
        LineStylePicker(lineStyle = lineStyle, onPick = onLineStyle)
    }
}

/**
 * The swatches, as one scrolling row: the stored palette, the editable custom slot, then the add-colour
 * ring — the three parts `docs/colours.md` describes, in that order.
 *
 * Shared by the palette flyout and the stroke panel, so the window cannot drift into two palettes.
 */
@Composable
private fun SwatchRow(
    palette: List<Int>,
    current: Int,
    custom: Int?,
    onPick: (Int) -> Unit,
    onEditCustom: () -> Unit,
    onAddColour: () -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
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
 * The pen's **line style**, as the tablet's colour-and-size pop-up offers it: four chips under the
 * swatches, the live one wearing the steel. It belongs to the ink, like the colour and the width, so the
 * next stroke — and the next figure — is drawn with the pattern the chip names.
 */
@Composable
private fun LineStylePicker(lineStyle: LineStyle, onPick: (LineStyle) -> Unit) {
    Row(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(InkSlotMetrics.swatchGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((style, label) in LINE_STYLE_LABELS) {
            val picked = style == lineStyle
            Box(
                Modifier
                    .background(
                        if (picked) DesktopTheme.activeSlot else Color.Transparent,
                        DesktopTheme.capsuleShape,
                    )
                    .pointerInput(style) { detectTapGestures { onPick(style) } }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    label,
                    color = if (picked) DesktopTheme.ink else DesktopTheme.inkSecondary,
                    style = DesktopTheme.label,
                )
            }
        }
    }
}

/** The four patterns the pen can write, and the words the chip row prints. */
private val LINE_STYLE_LABELS: List<Pair<LineStyle, String>> = listOf(
    LineStyle.PLAIN to "Solid",
    LineStyle.DASHED to "Dashed",
    LineStyle.DASH_DOT to "Dash-dot",
    LineStyle.DOTTED to "Dotted",
)

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
                drawToolGlyph(DesktopTool.PEN, DesktopTheme.inkSecondary)
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
        Canvas(Modifier.size(14.dp)) { drawChromeGlyph(ChromeGlyph.ADD, DesktopTheme.accent) }
    }
}

/** The three width slots, as the row the stroke panel prints under the patterns. */
@Composable
private fun WidthRow(ink: DesktopInk, onPick: (Float) -> Unit) {
    val picked = PenPalette.widthSlot(ink.width)
    Row(
        Modifier.padding(top = 2.dp),
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
                        .size(widthOptionDotSize(width))
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
