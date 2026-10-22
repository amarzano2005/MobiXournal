package com.mobixournal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette
import com.mobixournal.ui.theme.DialogPresence

/**
 * The colour state every picker in the app shares — the editable custom slot, both persisted in
 * [AppSettings]. Holding it in one object is what makes a colour picked for a text box, a selection,
 * or the pen read the *same* custom slot.
 */
@Stable
class ColorPaletteState(
    private val settings: AppSettings,
    private val onSettingsChange: (AppSettings) -> Unit,
) {
    /** The user-defined colour behind the palette's editable slot, or null while it is still empty. */
    val custom: Int? get() = settings.customColor

    /** The pen palette the swatches are drawn from — [AppSettings.penColors], user-editable. */
    val colors: List<Int> get() = settings.penColors

    /** Persist a new colour for the editable custom slot (null clears the slot back to empty). */
    fun redefineCustom(color: Int?) = onSettingsChange(settings.copy(customColor = color))

    /**
     * Append [color] to the pen palette — what the toolbar pop-up's **add colour** swatch does. The
     * rules are [PenPalette]'s, shared with the desktop window: opaque, de-duplicated (re-adding a
     * colour that is already there selects it rather than making a twin) and capped at
     * [PenPalette.MAX_COLORS], the same ceiling Settings → Colors enforces.
     */
    fun addColor(color: Int) = onSettingsChange(
        settings.copy(penColors = PenPalette.add(settings.penColors, color)),
    )
}

@Composable
fun rememberColorPaletteState(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
): ColorPaletteState = remember(settings, onSettingsChange) {
    ColorPaletteState(settings, onSettingsChange)
}

/**
 * The one colour picker: the user's palette ([ColorPaletteState.colors], editable under
 * **Settings → Colors**) followed by the editable **custom** slot (marked with a pencil, long-press
 * to redefine via [CustomColorPickerDialog]). Used by the toolbar's pen palette, the text-box dialog
 * and the selection recolour menu, so all three offer the same affordances and share one custom
 * slot. There is no recents row: the palette *is* the list.
 *
 * A tap reports the colour through [onPick]; the host decides what it means (set the pen, restyle
 * the selection, colour the text). A long-press on the custom slot reports [onEditCustom] — the host
 * closes whatever menu it is in and shows the [CustomColorEditor], which must sit *outside* that menu
 * so dismissing the menu doesn't take the dialog with it.
 *
 * [compact] switches the swatches to a **single horizontally scrolling row** (no hint line, tighter
 * padding) — the shape the toolbar's Colour & size pop-up uses. A fixed row is what keeps the menu's
 * height constant however many colours the palette holds: a wrapping grid would grow the pop-up with
 * every swatch added. The dialogs keep the wrapping grid and the hint.
 *
 * [onAdd] appends a trailing **add colour** swatch, so a new palette colour can be made from the
 * picker itself rather than only from Settings → Colors.
 */
@Composable
fun ColorPaletteRows(
    selected: Int?,
    palette: ColorPaletteState,
    onPick: (Int) -> Unit,
    onEditCustom: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onAdd: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        if (!compact) PaletteHint("Tap to pick · long-press ✎ to edit")
        SwatchRow(compact) {
            for (c in palette.colors) {
                ColorSwatch(color = c, selected = c == selected, onClick = { onPick(c) })
            }
            ColorSwatch(
                color = palette.custom,
                selected = palette.custom != null && palette.custom == selected,
                // An empty slot has no colour to pick, so a tap sets one instead of selecting nothing.
                onClick = { palette.custom?.let(onPick) ?: onEditCustom() },
                onLongClick = onEditCustom,
                editable = true,
            )
            if (onAdd != null) {
                ColorSwatch(color = null, selected = false, onClick = onAdd, editable = false, add = true)
            }
        }
    }
}

/**
 * The HSV dialog behind the palette's custom slot, shown while [visible]. Confirming reports the new
 * colour through [onRedefine] — by default persisting it as *the* custom slot for every picker.
 */
@Composable
fun CustomColorEditor(
    visible: Boolean,
    palette: ColorPaletteState,
    onDismiss: () -> Unit,
    onRedefine: (Int) -> Unit = { palette.redefineCustom(it) },
) {
    DialogPresence(visible) {
        CustomColorPickerDialog(
            initial = palette.custom ?: AppSettings.DEFAULT_CUSTOM_COLOR,
            palette = palette.colors,
            onConfirm = { newColor -> onRedefine(newColor); onDismiss() },
            onDismiss = onDismiss,
        )
    }
}

/**
 * The swatches, wrapping onto further lines when the host is narrow. A dialog is much tighter than
 * the toolbar's drop-down, and a single fixed row silently clipped the custom slot off its end.
 * [compact] pulls the padding and gaps in for the toolbar's drop-down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(compact: Boolean = false, content: @Composable () -> Unit) {
    if (compact) {
        // One scrolling row of fixed height: adding swatches scrolls, it does not grow the menu.
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
        return
    }
    FlowRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun PaletteHint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * [keys] with [from]'s shortcut moved onto [to] — a palette row's key follows the swatch when the
 * user redefines its colour, rather than staying behind on a colour the palette no longer offers.
 *
 * Lives beside the palette because it is a palette edit, not a shortcut-codec concern: the shortcut
 * list is derived from the palette, so the two move together. A slot with no key, or a redefine to
 * the same colour, comes back unchanged.
 */
fun rekeyColorShortcut(keys: Map<Int, String>, from: Int, to: Int): Map<Int, String> {
    if (from == to) return keys
    val key = keys[from] ?: return keys
    return (keys - from) + ((to or 0xFF000000.toInt()) to key)
}


