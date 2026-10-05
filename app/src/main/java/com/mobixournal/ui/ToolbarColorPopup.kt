package com.mobixournal.ui

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Xournal++ palette colour `#000000` (black) — the pen a fresh document starts with. */
internal val XOPP_BLACK: Int = 0xFF000000.toInt()

/** Xournal++ palette colour `#FF0000` (red) — what the "red pen" shortcut switches to. */
internal val XOPP_RED: Int = 0xFFFF0000.toInt()

/** Xournal++ palette colour `#008000` (green) — what the "green pen" shortcut switches to. */
internal val XOPP_GREEN: Int = 0xFF008000.toInt()

/** Xournal++ palette colour `#3333CC` (blue). */
internal val XOPP_BLUE: Int = 0xFF3333CC.toInt()

/** Xournal++ palette colour `#FF8000` (orange). */
internal val XOPP_ORANGE: Int = 0xFFFF8000.toInt()

/** Xournal++ palette colour `#FFFF00` (yellow) — the factory highlighter colour. */
internal val XOPP_YELLOW: Int = 0xFFFFFF00.toInt()

/** Xournal++ palette colour `#FF00FF` (magenta) — the desktop palette's own name for this swatch. */
internal val XOPP_MAGENTA: Int = 0xFFFF00FF.toInt()

/** Xournal++ palette colour `#FFFFFF` (white). */
internal val XOPP_WHITE: Int = 0xFFFFFFFF.toInt()

/**
 * The factory pen palette: eight swatches in the order the app ships with — **Black, Red, Green,
 * Blue, Orange, Yellow, Magenta, White** — each hex taken from desktop Xournal++'s palette so a colour
 * picked here writes the same ARGB the desktop app writes for that swatch.
 *
 * It seeds [AppSettings.penColors], and stays this list — including on **Restore default** — so an
 * untouched install and a reset one both start from the same eight. The user edits the *live*
 * palette under Settings → Colors, not this one; [PaletteColorsTest] pins the order and the hexes.
 *
 * Colours are opaque ARGB; the highlighter renders them translucent.
 */
val PEN_COLORS: List<Int> = listOf(
    XOPP_BLACK,   // Black (#000000)
    XOPP_RED,     // Red (#FF0000)
    XOPP_GREEN,   // Green (#008000)
    XOPP_BLUE,    // Blue (#3333CC)
    XOPP_ORANGE,  // Orange (#FF8000)
    XOPP_YELLOW,  // Yellow (#FFFF00)
    XOPP_MAGENTA, // Magenta (#FF00FF)
    XOPP_WHITE,   // White (#FFFFFF)
)

/**
 * Human-readable English names for the standard desktop Xournal++ palette colours, in palette
 * order.
 *
 * Display only: the palette accepts any opaque colour (see [decodePenColors]), so this map names a
 * swatch in the pickers and the Settings list — it does not gate what a palette may hold.
 */
val PREDEFINED_COLOR_NAMES: Map<Int, String> = mapOf(
    XOPP_BLACK to "Black",
    XOPP_RED to "Red",
    XOPP_GREEN to "Green",
    XOPP_BLUE to "Blue",
    XOPP_ORANGE to "Orange",
    XOPP_YELLOW to "Yellow",
    XOPP_MAGENTA to "Magenta",
    XOPP_WHITE to "White",
)

/**
 * Returns a human-friendly display label for a color:
 * "Name (#RRGGBB)" for predefined colors, or "#RRGGBB" for custom ones.
 */
fun colorDisplayName(argb: Int): String {
    val opaque = argb or 0xFF000000.toInt()
    val hex = "#%06X".format(argb and 0xFFFFFF)
    val name = PREDEFINED_COLOR_NAMES[opaque]
    return if (name != null) "$name ($hex)" else hex
}

/**
 * Returns the color name if predefined (e.g. "Black", "Red"), or null for custom colors.
 */
fun predefinedColorName(argb: Int): String? =
    PREDEFINED_COLOR_NAMES[argb or 0xFF000000.toInt()]

/**
 * The rail's stroke-appearance slot: colour, tip size and line style in one drop-down. The face is
 * the size dot from [WidthDot], scaled to the live width and filled with the live colour, so the
 * button shows both settings at a glance.
 *
 * The menu is deliberately **compact** — colour swatches in a tight grid, the three tip sizes as one
 * bar of dots ([WidthSlotBar]) and line style as one row of chips ([LineStyleChips]) — because
 * colour, size and style are the three things you change together, and a menu you have to scroll to
 * reach the bottom of is one you stop opening. Fill no longer has a control anywhere: it is off for
 * every stroke drawn from here on.
 */
@Composable
internal fun ColorSizePopupButton(callbacks: ToolbarStyleCallbacks) {
    var editing by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf(-1) }
    val maxPt = (callbacks.widthSlots + callbacks.width).maxOrNull() ?: callbacks.width
    ToolbarPopupButton(
        face = { open ->
            IconButton(onClick = open) {
                WidthDot(callbacks.width, maxPt, Color(callbacks.color), bordered = true)
            }
        },
    ) { dismiss ->
        MenuHeading("Colour")
        ColorPaletteRows(
            selected = callbacks.color,
            palette = callbacks.palette,
            onPick = { c -> callbacks.onColor(c); dismiss() },
            onEditCustom = { editing = true; dismiss() },
            compact = true,
        )
        MenuHeading("Size")
        WidthSlotBar(
            width = callbacks.width,
            widthSlots = callbacks.widthSlots,
            onWidth = { pt -> callbacks.onWidth(pt); dismiss() },
            onEditSlot = { i -> editingSlot = i; dismiss() },
        )
        MenuHeading("Line style")
        LineStyleChips(
            lineStyle = callbacks.lineStyle,
            onLineStyle = { callbacks.onLineStyle(it) },
        )
    }
    CustomColorEditor(
        visible = editing,
        palette = callbacks.palette,
        onDismiss = { editing = false },
        onRedefine = callbacks.onRedefineCustom,
    )
    if (editingSlot in callbacks.widthSlots.indices) {
        WidthSlotSliderDialog(
            label = PEN_WIDTH_LABELS[editingSlot],
            initial = callbacks.widthSlots[editingSlot],
            onConfirm = { newPt -> callbacks.onRedefineSlot(editingSlot, newPt); editingSlot = -1 },
            onDismiss = { editingSlot = -1 },
        )
    }
}
