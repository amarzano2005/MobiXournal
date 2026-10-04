package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Fixed labels for the three configurable pen-width slots (the widths themselves live in [AppSettings]). */
val PEN_WIDTH_LABELS: List<String> = listOf("S", "M", "L")

/** Minimum allowed pen width in points for the size slider and slot editor. */
const val PEN_WIDTH_MIN: Float = 0.5f

/** Maximum allowed pen width in points for the size slider and slot editor. */
const val PEN_WIDTH_MAX: Float = 15f

/** Increment for the −/+ fine-adjust buttons in the slot-resize dialog, in points. */
const val PEN_WIDTH_STEP: Float = 0.1f

/** Format a pen width for display: at most two decimals, trailing zeros trimmed (e.g. 1.50 → "1.5"). */
fun ptLabel(pt: Float): String =
    String.format(java.util.Locale.US, "%.2f", pt).trimEnd('0').trimEnd('.')



/**
 * The three configurable width slots ([widthSlots]) as **one compact bar of small boxes** — the size
 * section of the Colour & size pop-up. Each slot is a [WidthDot] scaled to its width with its label
 * under it; a tap selects that width via [onWidth] and a **long-press** asks the caller to redefine
 * the slot via [onEditSlot] (which opens a [WidthSlotSliderDialog]).
 *
 * Deliberately tight: the boxes are short (a small dot, a single label line, little padding) so the
 * three of them read as one strip rather than as three blocks. One row instead of three also keeps
 * the menu short, which is the point of the compact pop-up — the tip sizes are read at a glance as
 * a size ladder rather than as three separate lines.
 *
 * The same slots size the **eraser** — its radius is derived from the pen width
 * (see [com.mobixournal.render.eraserRadiusPt]), so this bar is the whole tip-size story.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WidthSlotBar(
    width: Float,
    widthSlots: List<Float>,
    onWidth: (Float) -> Unit,
    onEditSlot: (Int) -> Unit,
) {
    val maxPt = (widthSlots + width).maxOrNull() ?: width
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        widthSlots.forEachIndexed { i, pt ->
            val selected = pt == width
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    )
                    .combinedClickable(
                        onClick = { onWidth(pt) },
                        onLongClick = { onEditSlot(i) },
                    )
                    .padding(vertical = 3.dp),
            ) {
                // A dot box a few dp smaller than the swatch one: this is a size ladder, not a
                // colour, so it doesn't need the same target or the same presence in the menu.
                WidthDot(pt, maxPt, MaterialTheme.colorScheme.onSurface, boxSize = 22.dp)
                Spacer(Modifier.height(1.dp))
                Text(
                    "${PEN_WIDTH_LABELS.getOrElse(i) { "${i + 1}" }} ${ptLabel(pt)} pt",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * A dialog (0.5 → 15 pt) that redefines one pen-width slot; opened by long-pressing the slot. Offers
 * three ways to dial in a width: the slider for a broad sweep, the −/+ buttons for fine
 * [PEN_WIDTH_STEP]-pt nudges, and a text field for typing an exact value.
 */
@Composable
internal fun WidthSlotSliderDialog(
    label: String,
    initial: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial.coerceIn(PEN_WIDTH_MIN, PEN_WIDTH_MAX)) }
    // The text field is edited freely as a string so mid-edit values ("", ".", "1.") don't fight the
    // user; a valid, in-range parse flows back into [value], and any programmatic change re-syncs it.
    var text by remember { mutableStateOf(ptLabel(value)) }
    fun setValue(pt: Float) {
        value = pt.coerceIn(PEN_WIDTH_MIN, PEN_WIDTH_MAX)
        text = ptLabel(value)
    }
    // Snap −/+ nudges to a clean [PEN_WIDTH_STEP] grid so repeated taps don't accumulate float drift.
    fun nudge(delta: Float) = setValue(((value + delta) / PEN_WIDTH_STEP).roundToInt() * PEN_WIDTH_STEP)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Slot $label width") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = { nudge(-PEN_WIDTH_STEP) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Thinner")
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { entered ->
                            text = entered
                            entered.toFloatOrNull()?.let { value = it.coerceIn(PEN_WIDTH_MIN, PEN_WIDTH_MAX) }
                        },
                        singleLine = true,
                        suffix = { Text("pt") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp),
                    )
                    IconButton(onClick = { nudge(PEN_WIDTH_STEP) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Thicker")
                    }
                }
                Slider(
                    value = value,
                    onValueChange = { setValue(it) },
                    valueRange = PEN_WIDTH_MIN..PEN_WIDTH_MAX,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
