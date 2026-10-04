package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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
    /** The user-defined colour behind the palette's editable slot. */
    val custom: Int get() = settings.customColor

    /** The pen palette the swatches are drawn from — [AppSettings.penColors], user-editable. */
    val colors: List<Int> get() = settings.penColors

    /** Persist a new colour for the editable custom slot. */
    fun redefineCustom(color: Int) = onSettingsChange(settings.copy(customColor = color))
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
 * [compact] drops the hint line and tightens the swatch grid's padding — the shape the toolbar's
 * Colour & size pop-up uses, so the menu stays short. The dialogs keep the hints.
 */
@Composable
fun ColorPaletteRows(
    selected: Int?,
    palette: ColorPaletteState,
    onPick: (Int) -> Unit,
    onEditCustom: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier = modifier) {
        if (!compact) PaletteHint("Tap to pick · long-press ✎ to edit")
        SwatchRow(compact) {
            for (c in palette.colors) {
                ColorSwatch(color = c, selected = c == selected, onClick = { onPick(c) })
            }
            ColorSwatch(
                color = palette.custom,
                selected = palette.custom == selected,
                onClick = { onPick(palette.custom) },
                onLongClick = onEditCustom,
                editable = true,
            )
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
    if (!visible) return
    CustomColorPickerDialog(
        initial = palette.custom,
        onConfirm = { newColor -> onRedefine(newColor); onDismiss() },
        onDismiss = onDismiss,
    )
}

/**
 * The swatches, wrapping onto further lines when the host is narrow. A dialog is much tighter than
 * the toolbar's drop-down, and a single fixed row silently clipped the custom slot off its end.
 * [compact] pulls the padding and gaps in for the toolbar's drop-down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(compact: Boolean = false, content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(
            horizontal = if (compact) 8.dp else 12.dp,
            vertical = if (compact) 2.dp else 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
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


