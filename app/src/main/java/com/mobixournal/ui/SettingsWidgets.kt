package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mobixournal.render.Momentum
import com.mobixournal.render.PanSensitivity
import com.mobixournal.render.PressureCurve

/**
 * Where a row picked up at [from] and dragged [offset] px lands: one place per whole [rowHeight]
 * crossed, rounded so the row commits to a slot once it is more than half way into it. Returns
 * [from] unchanged when nothing is being dragged or the row height isn't measured yet.
 */
fun dragTargetIndex(from: Int, offset: Float, rowHeight: Int, count: Int): Int {
    if (from < 0 || rowHeight <= 0) return from
    return (from + Math.round(offset / rowHeight)).coerceIn(0, count - 1)
}

/** A labelled switch with a title and subtitle. */
@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A titled radio group over an enum's values. */
@Composable
fun <T> OptionGroup(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
    options.forEach { option ->
        Row(
            modifier = Modifier.fillMaxWidth()
                .selectable(selected = option == selected, onClick = { onSelect(option) })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = option == selected, onClick = { onSelect(option) })
            Spacer(Modifier.width(8.dp))
            Text(label(option), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }
    }
}

/** A labelled drop-down menu over an enum's values. */
@Composable
fun <T> DropdownRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(optionLabel(selected)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = { open = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

/**
 * The momentum-strength control: a continuous slider from [Momentum.OFF] to [Momentum.MAX] whose
 * value is snapped to the [Momentum.STEP] grid. Left continuous (no discrete stops) so the wide
 * 0..10 range doesn't render a thicket of tick marks. 0 reads "Off" (a released pan stops dead);
 * every other value shows its factor.
 */
@Composable
fun MomentumSlider(value: Float, onChange: (Float) -> Unit) {
    Text("Momentum scrolling", style = MaterialTheme.typography.bodyLarge)
    Text(
        "How far a one-finger pan keeps gliding after you flick it — the faster you flick, the much " +
            "farther it coasts. 0 turns momentum off; 1 is normal. (Two-finger pans never glide.)",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    val label = if (value <= Momentum.OFF) "Off" else "%.1f×".format(value)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = { onChange(Momentum.snap(it)) },
            valueRange = Momentum.OFF..Momentum.MAX,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
    }
}

/**
 * The panning-sensitivity control: a continuous slider from [PanSensitivity.OFF] to
 * [PanSensitivity.MAX], snapped to the [PanSensitivity.STEP] grid. 1 tracks the finger one-to-one
 * (the default); below 1 the canvas pans slower than the finger, above 1 it pans faster. 0 reads
 * "Off" (a pan gesture moves nothing).
 */
@Composable
fun PanSensitivitySlider(value: Float, onChange: (Float) -> Unit) {
    Text("Panning sensitivity", style = MaterialTheme.typography.bodyLarge)
    Text(
        "How far the canvas moves as you pan. 1 matches your finger; lower is slower, higher is faster; 0 turns panning off.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    val label = if (value <= PanSensitivity.OFF) "Off" else "%.1f×".format(value)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = { onChange(PanSensitivity.snap(it)) },
            valueRange = PanSensitivity.OFF..PanSensitivity.MAX,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
    }
}

/**
 * The pen's minimum-pressure floor — desktop Xournal++'s "minimum pressure": the fraction of the
 * pen's width a stroke keeps even at zero pressure. 0.05 is the desktop default; raise it if a very
 * light touch should still draw a visible line. The range is the desktop slider's own
 * ([PressureCurve.MINIMUM_PRESSURE_MIN] … [PressureCurve.MINIMUM_PRESSURE_MAX]).
 *
 * [enabled] is false while **Pressure sensitivity** is off: the floor is one half of that filter, so
 * with pressure off the stroke is drawn at the size setting and this slider changes nothing. Disabled
 * rather than hidden, with the reason on screen — the same thing desktop Xournal++ does — so the
 * control never looks broken while it is inert.
 */
@Composable
fun MinimumPressureSlider(
    value: Float,
    onChange: (Float) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Text("Minimum pressure", style = MaterialTheme.typography.bodyLarge)
    Text(
        if (enabled) {
            "The lightest a stroke can get, as a fraction of the pen's width. 0.05 is desktop " +
                "Xournal++'s default; raise it if a very light touch should still draw a visible line."
        } else {
            PRESSURE_FILTER_OFF_HINT
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            valueRange = PressureCurve.MINIMUM_PRESSURE_MIN..PressureCurve.MINIMUM_PRESSURE_MAX,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text("%.2f×".format(value), modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
    }
}

/**
 * The pen's pressure-multiplier control: scales the raw pressure before the minimum floor, so a light
 * writer can thicken lines without changing the size setting. 1 leaves the pressure unchanged (the
 * default); higher thickens (and can push a stroke past its nominal width, as on the desktop). The
 * range is the desktop slider's own, up to [PressureCurve.MULTIPLIER_MAX] — the headroom that makes
 * the control worth reaching for.
 *
 * [enabled] is false while **Pressure sensitivity** is off, for the same reason as
 * [MinimumPressureSlider]: half of a filter that is switched off has nothing to act on.
 */
@Composable
fun PressureMultiplierSlider(
    value: Float,
    onChange: (Float) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Text("Pressure multiplier", style = MaterialTheme.typography.bodyLarge)
    Text(
        if (enabled) {
            "Scales the pen's pressure before it thickens the line. Raise it if you write lightly and " +
                "want thicker strokes (up to %.1f×); 1 leaves your pressure as the tablet reports it, and " +
                "above 1 a stroke can exceed its set width — as on the desktop.".format(PressureCurve.MULTIPLIER_MAX)
        } else {
            PRESSURE_FILTER_OFF_HINT
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            valueRange = AppSettings.PRESSURE_MULTIPLIER_MIN..AppSettings.PRESSURE_MULTIPLIER_MAX,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text("%.2f×".format(value), modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
    }
}

/**
 * Why a pressure-filter control is inert while **Pressure sensitivity** is off — desktop Xournal++'s
 * own wording for the same disabled state ("Enable pressure sensitivity … to change this setting!").
 */
const val PRESSURE_FILTER_OFF_HINT: String =
    "Inactive: turn on Pressure sensitivity (above) to change the pen's pressure filter — with it off " +
        "every stroke is drawn at its set width."

/**
 * A reorderable, toggleable list of rail-style rows — the shared body of the Toolbar section's rail
 * list and the Figures section's Shapes submenu: a drag handle, a label and a show/hide switch per
 * row, reordered by **long-press drag** one whole row at a time (see [dragTargetIndex]).
 *
 * [onOrder] receives the new full order when a drag lands; [onHidden] the new hidden set when a
 * switch flips.
 */
@Composable
fun ReorderableRowList(
    items: List<RailItem>,
    hidden: Set<String>,
    onOrder: (List<String>) -> Unit,
    onHidden: (Set<String>) -> Unit,
) {
    val ids = items.map { it.id }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(0) }

    val dropIndex = dragTargetIndex(dragIndex, dragOffset, rowHeightPx, items.size)
    items.forEachIndexed { index, item ->
        RailItemRow(
            item = item,
            shown = item.id !in hidden,
            dragging = dragIndex == index,
            dragOffset = when {
                dragIndex == index -> dragOffset
                dragIndex < 0 -> 0f
                index in (dragIndex + 1)..dropIndex -> -rowHeightPx.toFloat()
                index in dropIndex until dragIndex -> rowHeightPx.toFloat()
                else -> 0f
            },
            onShown = { shown ->
                onHidden(if (shown) hidden - item.id else hidden + item.id)
            },
            onHeight = { rowHeightPx = it },
            onDragStart = { dragIndex = index; dragOffset = 0f },
            onDrag = { dy -> dragOffset += dy },
            onDragEnd = {
                val to = dragTargetIndex(dragIndex, dragOffset, rowHeightPx, items.size)
                if (dragIndex >= 0 && to != dragIndex) onOrder(moveId(ids, dragIndex, to - dragIndex))
                dragIndex = -1
                dragOffset = 0f
            },
        )
    }
}

/**
 * One rail position in the Toolbar section: a drag handle, its name and a show/hide switch. The row
 * is grabbed by a **long-press anywhere on it** (not just the handle) and dragged up or down. Every
 * row is placed by [dragOffset]: the held one follows the finger, and the rows it is crossing shift
 * a place to preview where it will land.
 */
@Composable
fun RailItemRow(
    item: RailItem,
    shown: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    onShown: (Boolean) -> Unit,
    onHeight: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val height by rememberUpdatedState(onHeight)
    Surface(
        tonalElevation = if (dragging) 6.dp else 0.dp,
        shadowElevation = if (dragging) 6.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .onSizeChanged { height(it.height) }
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragStart() },
                    onDrag = { change, amount -> change.consume(); drag(amount.y) },
                    onDragEnd = { dragEnd() },
                    onDragCancel = { dragEnd() },
                )
            },
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag to reorder ${item.label}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = shown, onCheckedChange = onShown)
        }
    }
}
