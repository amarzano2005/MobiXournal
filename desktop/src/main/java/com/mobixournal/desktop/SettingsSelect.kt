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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph

/**
 * **One answer of a list**, as the window's **select**: the answer in hand on a capsule with a chevron, and
 * the rest of the list in a menu under it.
 *
 * It is the window's **only** choice control, and it replaced two: a row of pills for two or three words
 * (*Left · Right · Top*) and, before that, nothing at all. Three document formats rendered as three lozenges
 * read as three big buttons, wrapped onto two lines to say one thing, and shouted louder than every real
 * control on the page; a segmented row of short answers was the same defect in a smaller size, and two shapes
 * for one kind of thing is the drift a grammar exists to stop (`docs/desktop-settings.md`).
 *
 * The menu is a **popup** in the window's own style: the panel's fill (a list laid over the page in floating
 * glass let the values behind it read through, which looks like a stain, not a menu), the flyouts' inner
 * air, and rows that light as capsules inside that air rather than as bands cut off at the popup's edge. Its
 * width is **measured** from the longest answer, so *Left · Right · Top* opens a narrow list and the three
 * formats open one wide enough to read `Xournal++ package (.xopp, zipped)` whole.
 *
 * Keyboard: Tab reaches it, Left/Right and Up/Down step through the answers (wrapping), Enter or Space opens
 * the list, Esc puts it away. The menu itself never takes the keyboard ([FloatingSurface]) — it closes on a
 * pick or a click outside, which is what a menu of a few answers is for.
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
    // The click that closes the list must not open it again (see [DismissGuard]): the popup's own dismissal
    // arrives on the same press the capsule takes.
    val guard = remember { DismissGuard() }
    val pick = rememberUpdatedState(onPick)
    val menuWidth = selectMenuWidth(options.map(optionLabel))
    SettingsRow(label = label, note = note) {
        Box {
            SettingsSelectCapsule(
                text = optionLabel(picked),
                open = open,
                onToggle = { open = !open && guard.mayOpen(Unit) },
                onStep = { delta -> pick.value(nextChoice(options, picked, delta)) },
                onSize = { anchor = it },
            )
            if (open) {
                // Under the capsule, its right edge on the capsule's: a list that grew rightwards would walk
                // out of a panel whose column ends at the page's edge.
                val shift = with(LocalDensity.current) { (menuWidth - anchor.width.toDp()).roundToPx() }
                Popup(
                    offset = IntOffset(-shift, anchor.height + 4),
                    onDismissRequest = {
                        guard.closed(Unit)
                        open = false
                    },
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

/**
 * The list a select opens: one row per answer, the picked one on the steel and in the accent.
 *
 * The rows are **inset capsules**, not bands, because the list is a popup: its own rounded air is the edge,
 * and a row that filled the popup would be cut by that edge on its first and last line — the "line of
 * selection with its end sliced off" that a full-bleed row draws in a floating surface.
 */
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
            .glassSurface(fill = DesktopTheme.settingsPanel)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        for (option in options) {
            val chosen = option == picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp)
                    .hoverFill(
                        DesktopTheme.railSlotShape,
                        base = if (chosen) DesktopTheme.activeSlot.copy(alpha = 0.35f) else Color.Transparent,
                    )
                    .pointerInput(option) { detectTapGestures { onPick(option) } }
                    .padding(horizontal = 12.dp),
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

/**
 * How wide the list opens: the longest answer plus the row's own two insets, inside a floor and a ceiling.
 *
 * Measured rather than fixed, because the two lists the window opens are nothing alike — three words, or
 * three phrases — and a menu is judged by whether its longest line fits whole.
 */
@Composable
private fun selectMenuWidth(labels: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, density) {
        val widest = labels.maxOfOrNull { measurer.measure(it, DesktopTheme.body).size.width } ?: 0
        selectMenuWidthFor(with(density) { widest.toDp() })
    }
}

/**
 * The list's width for its longest answer: that answer, the row's own two insets, inside a floor and a cap.
 *
 * The cap is what keeps a list of phrases from running into the page's edge — a menu wider than the panel
 * would have to be scrolled to read a name it exists to show whole.
 */
internal fun selectMenuWidthFor(widest: Dp): Dp =
    (widest + SELECT_ROW_INSET * 2 + SELECT_MENU_AIR * 2).coerceIn(SELECT_MIN_WIDTH, SELECT_MAX_WIDTH)

/** The answer [delta] places away from [picked] in [options], wrapping at both ends. */
internal fun <T> nextChoice(options: List<T>, picked: T, delta: Int): T {
    val index = options.indexOf(picked)
    if (options.isEmpty()) return picked
    if (index < 0) return options.first()
    val next = Math.floorMod(index + delta, options.size)
    return options[next]
}

/** A row's own horizontal inset inside the list: the air between the popup's edge and an answer's word. */
private val SELECT_ROW_INSET = 12.dp

/** The list's inner air around its rows, as every flyout of the window has. */
private val SELECT_MENU_AIR = 6.dp

/** A list of one word still opens wider than the word; a list of phrases stops short of the panel's edge. */
private val SELECT_MIN_WIDTH = 150.dp
private val SELECT_MAX_WIDTH = 340.dp
