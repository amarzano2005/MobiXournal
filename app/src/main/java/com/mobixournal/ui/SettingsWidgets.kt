package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mobixournal.render.Momentum
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
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

/** A labelled switch with a title and subtitle; the whole row toggles it, not just the thumb. */
@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val reach = LocalSettingsCardInset.current
    Row(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = reach, vertical = 10.dp + settingsCellAir(reach)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ControlHeader(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        // The row owns the toggle; the switch is only its picture (null callback), so a tap is
        // never handled twice.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A titled choice over a list of answers, drawn as a **select**: the answer in hand at the end of one
 * row with a chevron, and the rest of the list in a menu under it.
 *
 * It used to draw one radio row per answer, and that was the wrong instrument for a settings card: the
 * answers nobody is using took a column of dots and lines, every page asked the reader to scan four
 * rows to find the one already set, and the answer *in hand* had no line of its own to be read on. A
 * choice is one line — the name, the answer, the chevron that says there are others — which is the
 * grammar the window's Settings already keeps (`docs/desktop-settings.md`), and what stops a card
 * holding four choices from running off a phone's screen.
 *
 * The whole row is the hit area, subtitle included, as a switch row's is ([OptionTriggerRow]). The list
 * is [ToolbarMenu] — the app's one pop-up panel, its rim and its spring — of one [OptionMenuItem] per
 * answer, placed under the row it belongs to ([SettingsSelectPositionProvider]) and capped so a long
 * list scrolls inside the window. [icon] draws each answer's own glyph beside its word, and the answer
 * in hand's glyph beside it on the row.
 */
@Composable
fun <T> OptionGroup(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: ((T) -> ImageVector)? = null,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var rowWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val placement = remember(density) { SettingsSelectPositionProvider(density) }
    // The screen less the list's air at each end — the same margin the placement keeps from the window's
    // edges, so a capped panel always has room where it is put.
    val maxMenuHeight = (LocalConfiguration.current.screenHeightDp.dp - SettingsSelectMargin * 2)
        .coerceAtLeast(160.dp)
    // As wide as its longest answer and never wider than the row: a list stretched to the card is a sheet
    // across four words, and one squeezed to its padding wraps every answer out of the panel.
    val measurer = rememberTextMeasurer()
    val answerStyle = MaterialTheme.typography.bodyLarge
    val menuWidth = if (rowWidthPx > 0) {
        remember(options, rowWidthPx, answerStyle, label) {
            val widest = options.maxOfOrNull { measurer.measure(label(it), answerStyle).size.width } ?: 0
            settingsSelectWidthFor(
                widest = with(density) { widest.toDp() },
                rowWidth = with(density) { rowWidthPx.toDp() },
            )
        }
    } else {
        null
    }
    Box(Modifier.onSizeChanged { rowWidthPx = it.width }) {
        OptionTriggerRow(
            title = title,
            subtitle = subtitle,
            value = label(selected),
            icon = icon?.invoke(selected),
            onClick = { open = !open },
        )
        ToolbarMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = maxMenuHeight),
            positionProvider = placement,
            // The list is the row's own width and wears the settings panel's fill: a panel squeezed to its
            // intrinsic width wrapped every answer onto a second line, and floating glass over a card let
            // the rows behind it read through — a stain, not a menu. The window's select opens on the same
            // fill for the same reason (`docs/desktop-settings.md`).
            contentWidthPx = menuWidth?.let { with(density) { it.roundToPx() } },
            // Opaque: the list lands over a settings card, and the floating glass there let the row behind
            // it read through its own answers.
            fill = ModernTheme.settingsPanel(chromeIsDark())
                .compositeOver(MaterialTheme.colorScheme.background),
        ) {
            options.forEach { option ->
                OptionMenuItem(
                    label = label(option),
                    icon = icon?.invoke(option),
                    chosen = option == selected,
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The row a select shows: the choice's name and why it matters at the start, the answer in hand and the
 * chevron that says there are others at the end. The whole row opens the list, subtitle included.
 */
@Composable
private fun OptionTriggerRow(
    title: String,
    subtitle: String,
    value: String,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    val reach = LocalSettingsCardInset.current
    Row(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .clickable(role = Role.DropdownList, onClick = onClick)
            .padding(horizontal = reach, vertical = 10.dp + settingsCellAir(reach)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlHeader(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * One answer in a select's list: in the accent with a check when it is the one in hand.
 *
 * A row of our own rather than a `DropdownMenuItem`, whose label carries a weight — with a weight its
 * intrinsic width is nothing, so a list measured on intrinsics collapsed onto its padding and the answers
 * wrapped out of the panel. These rows fill the width the list is given instead.
 */
@Composable
private fun OptionMenuItem(label: String, icon: ImageVector?, chosen: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chosen) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
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
    ControlHeader(
        "Momentum scrolling",
        "How far a one-finger pan glides after a flick. 0 is off; 1 is normal.",
        Modifier.padding(top = 8.dp, bottom = 4.dp),
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
    ControlHeader(
        "Panning sensitivity",
        "How far the canvas moves as you pan. 1 matches your finger; 0 turns panning off.",
        Modifier.padding(top = 8.dp, bottom = 4.dp),
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
    ControlHeader(
        "Minimum pressure",
        if (enabled) {
            "The lightest a stroke can get, as a fraction of the pen's width."
        } else {
            PRESSURE_FILTER_OFF_HINT
        },
        Modifier.padding(top = 8.dp, bottom = 4.dp),
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
    ControlHeader(
        "Pressure multiplier",
        if (enabled) {
            "Scales pressure before it thickens the line. Above 1 a stroke can exceed its set width."
        } else {
            PRESSURE_FILTER_OFF_HINT
        },
        Modifier.padding(top = 8.dp, bottom = 4.dp),
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

 /** Why a pressure-filter control is inert while **Pressure sensitivity** is off: it says what to do. */
     const val PRESSURE_FILTER_OFF_HINT: String = "Inactive: turn on Pressure sensitivity to change this."

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
    val reach = LocalSettingsCardInset.current
    Surface(
        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        // The lifted row is the card's full width; the card's own rounded clip supplies the corners.
        shape = RectangleShape,
        tonalElevation = if (dragging) 6.dp else 0.dp,
        shadowElevation = if (dragging) 6.dp else 0.dp,
        modifier = Modifier
            .fullBleedRow(reach)
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
            modifier = Modifier.padding(horizontal = reach, vertical = 8.dp + settingsCellAir(reach)),
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
