package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph

/**
 * **One answer of a list**, as a desktop **select**: the answer in hand on a capsule with a chevron, and the
 * rest of the list in a menu under it.
 *
 * The row of pills ([SettingsChoice]) is the window's other choice shape, and it is the right one for two or
 * three *words* — *Left · Right · Top*. It is the wrong one for answers that are phrases: three document
 * formats rendered as three lozenges read as three big buttons, wrapped onto two lines to say one thing, and
 * shouted louder than every real control on the page. A select says the same in the width of the answer it
 * currently holds, so the page keeps one column of quiet controls.
 *
 * Keyboard: Tab reaches it, Left/Right and Up/Down step through the answers (wrapping), Enter or Space opens
 * the list, Esc puts it away. The menu itself never takes the keyboard ([FloatingSurface]) — it closes on a
 * pick or a click outside, which is what a menu of two or three answers is for.
 */
@Composable
internal fun <T> SettingsSelect(
    label: String,
    note: String? = null,
    options: List<T>,
    picked: T,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntSize.Zero) }
    val pick = rememberUpdatedState(onPick)
    SettingsRow(label = label, note = note) {
        Box {
            SettingsSelectCapsule(
                text = optionLabel(picked),
                open = open,
                onToggle = { open = !open },
                onStep = { delta -> pick.value(nextChoice(options, picked, delta)) },
                onSize = { anchor = it },
            )
            if (open) {
                // Under the capsule, its right edge on the capsule's: a list that grew rightwards would walk
                // out of a panel whose column ends at the page's edge.
                val menuWidth = SELECT_MENU_WIDTH
                val shift = with(LocalDensity.current) { (menuWidth - anchor.width.toDp()).roundToPx() }
                Popup(
                    offset = IntOffset(-shift, anchor.height + 4),
                    onDismissRequest = { open = false },
                    properties = FloatingSurface,
                ) {
                    SettingsSelectMenu(
                        options = options,
                        picked = picked,
                        optionLabel = optionLabel,
                        width = menuWidth,
                    ) { option ->
                        pick.value(option)
                        open = false
                    }
                }
            }
        }
    }
}

/** The answer in hand, on a capsule: click or `Enter` lists the others, Left/Right step them in place. */
@Composable
private fun SettingsSelectCapsule(
    text: String,
    open: Boolean,
    onToggle: () -> Unit,
    onStep: (Int) -> Unit,
    onSize: (IntSize) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val resting = if (open) DesktopTheme.activeSlot else DesktopTheme.activeSlot.copy(alpha = 0.35f)
    Row(
        Modifier
            .height(SettingsMetrics.CONTROL_HEIGHT)
            .onSizeChanged(onSize)
            .hoverFill(DesktopTheme.capsuleShape, active = open, base = resting)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) DesktopTheme.accent else Color.Transparent,
                shape = DesktopTheme.capsuleShape,
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        onToggle()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionUp -> {
                        onStep(-1)
                        true
                    }
                    Key.DirectionRight, Key.DirectionDown -> {
                        onStep(1)
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) { detectTapGestures { onToggle() } }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = DesktopTheme.ink, style = DesktopTheme.body, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(12.dp)) { drawChromeGlyph(ChromeGlyph.CHEVRON_DOWN, DesktopTheme.inkSecondary) }
    }
}

/** The list a select opens: one row per answer, the picked one in the accent. */
@Composable
private fun <T> SettingsSelectMenu(
    options: List<T>,
    picked: T,
    optionLabel: (T) -> String,
    width: Dp,
    onPick: (T) -> Unit,
) {
    Column(
        Modifier
            .width(width)
            // The panel's own fill, not the floating glass: a list of answers laid over the page let the
            // values behind it read through the rows, which is a menu that looks like a stain.
            .glassSurface(fill = DesktopTheme.settingsPanel)
            .padding(vertical = 4.dp),
    ) {
        for (option in options) {
            val chosen = option == picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp)
                    .hoverFill(RectangleShape)
                    .pointerInput(option) { detectTapGestures { onPick(option) } }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    optionLabel(option),
                    color = if (chosen) DesktopTheme.accent else DesktopTheme.ink,
                    style = DesktopTheme.body,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The answer [delta] places away from [picked] in [options], wrapping at both ends. */
internal fun <T> nextChoice(options: List<T>, picked: T, delta: Int): T {
    val index = options.indexOf(picked)
    if (options.isEmpty()) return picked
    if (index < 0) return options.first()
    val next = Math.floorMod(index + delta, options.size)
    return options[next]
}

/**
 * How wide a select's list opens: wide enough for the longest answer the window stores
 * (*Xournal++ package (.xopp, zipped)*), and no wider.
 */
private val SELECT_MENU_WIDTH = 300.dp
