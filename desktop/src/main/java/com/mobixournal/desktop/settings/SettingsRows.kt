package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.DesktopButton
import com.mobixournal.desktop.tools.DesktopButtonVariant
import com.mobixournal.desktop.tools.DesktopTextAction
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.shell.search.find

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph
import kotlin.math.roundToInt

/**
 * The Settings surface's own **rows**: the glue between a setting and the control that changes it.
 *
 * The controls are the window's — the same steel, the same accent and the same hover step as the lane's
 * (`InspectorControls.kt`) — but the **shape of a row is a desktop's, not a phone's**: the name of the
 * setting and its one line of explanation on the left, the control itself at the right edge of the row,
 * hugging its own words. The first version of this page stacked a label, an explanation and a full-width
 * segment row per setting, which is how a settings *screen* is built on a 6-inch pointer: on a 1440-wide
 * window it read as four bands of buttons per card and pushed the last setting of Interface below the
 * fold. A row that fills its width is a touch target; a row that ends in a control is a preference.
 *
 * A row is therefore also the **hit area** — 40 dp, the contract's smallest control — so the switch at its
 * end can be the slim 32 × 18 pill and still be reachable from the keyboard and the pointer.
 */

/** The surface's measures, in one place so a row cannot quietly grow back into a touch target. */
internal object SettingsMetrics {

    /** The index column: a section's name and its one-line summary, and no wider. */
    val INDEX_WIDTH = 210.dp

    /** One setting is one row: 40 dp, the contract's smallest interactive measure. */
    val ROW_HEIGHT = 40.dp

    /** The tallest a control draws — inside a row, never taller than it. */
    val CONTROL_HEIGHT = 30.dp

    /**
     * The page's own column, capped: an ultrawide window keeps a readable line length instead of
     * stretching one setting over 3 440 px. The index owns the rest.
     */
    val PAGE_MAX_WIDTH = 720.dp

    /**
     * The index's row: a glyph and a name, tighter than a page row because it is a list.
     *
     * 38 dp and not 44: the index now carries four eyebrows over its ten names, and at 44 the last two
     * sections fell below a 640 dp panel — the one list in the window that must show *all* of itself at
     * once.
     */
    val INDEX_ROW_HEIGHT = 38.dp
}

/**
 * Desktop property group: a heading, its **one** sentence about the whole group, and a hairline — no nested
 * glass or rounded card.
 *
 * [summary] is the group's grammar, not a row's: a list of eight swatches shares one sentence about dragging
 * and clicking, instead of repeating it under every row and turning a palette into a manual
 * (`docs/desktop-settings.md`). A group without one draws nothing extra.
 */
@Composable
internal fun SettingsGroup(title: String, summary: String? = null, content: @Composable () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(title, color = DesktopTheme.ink, style = DesktopTheme.heading,
        modifier = Modifier.padding(horizontal = ROW_INSET))
    if (!summary.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(summary, color = DesktopTheme.inkSecondary, style = DesktopTheme.label,
            modifier = Modifier.padding(horizontal = ROW_INSET))
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    Column(Modifier.fillMaxWidth()) { content() }
}

/** A desktop property row: readable label/help on the left and a stable control column on the right. */
@Composable
internal fun SettingsRow(
    label: String,
    note: String? = null,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    control: @Composable () -> Unit,
) {
    val press = rememberUpdatedState(onClick)
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsMetrics.ROW_HEIGHT)
            .border(1.dp, if (focused) DesktopTheme.accent else Color.Transparent, PropertyControlShape)
            .onFocusChanged { focused = it.isFocused }
            .reportHelp(note)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        // The same 4 dp corner as the focus ring below: a row that lit as a rectangle and
                        // framed as a rounded control was two shapes for one hover.
                        .hoverFill(DesktopTheme.controlShape)
                        .then(if (checked != null) Modifier.toggleable(checked,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            role = Role.Checkbox, onValueChange = { press.value?.invoke() })
                        else Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { press.value?.invoke() })
                },
            )
            .padding(horizontal = ROW_INSET, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(label, color = DesktopTheme.ink, style = DesktopTheme.body)
            if (!note.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(note, color = DesktopTheme.inkSecondary, style = DesktopTheme.label,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(16.dp))
        // The answer takes **its own** width and nothing more, hugging its words at the row's right edge.
        // The old split gave the control a fixed 45 % of the row whatever it held, which wrapped a two-line
        // explanation beside an 18 dp checkbox and left a short select floating in air.
        Box(contentAlignment = Alignment.CenterEnd) { control() }
    }
}

/**
 * **A command**, as a row: the whole width lights up and the word is in the accent, with **no control at
 * the end**.
 *
 * The page's grammar is one sentence per kind of thing (`docs/desktop-settings.md`): a *value* ends in a
 * slider with its number, a *choice* in a select, a *toggle* in a switch, and a **command** in nothing at all —
 * the row itself is the button. Before that split, *Reset to defaults* and *Export settings…* wore a label, a
 * sentence and a word at the edge, exactly like the settings around them, and the only thing marking them as
 * actions was the accent of one word.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsCommand(
    label: String,
    note: String? = null,
    /**
     * The air above and below this command. 4 dp on a page whose commands are one of a kind; a group of
     * several (About's doors) asks for 6 dp so the stack reads as answers rather than as a wall.
     */
    spacing: Dp = 4.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().reportHelp(note).padding(vertical = spacing),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The button's own 12 dp inset *is* the rows' ROW_INSET, so a command's label starts on the same
        // column as the names above it; the text action's narrower inset had it four dp to their left.
        DesktopButton(label, onClick, variant = DesktopButtonVariant.Secondary, style = DesktopTheme.body)
    }
}

/**
 * The panel's **help line**: the sentence of whatever the pointer is over, shown in the footer.
 *
 * A composition local rather than a parameter because the sentence belongs to the *row*, and threading it up
 * through every page and group would make the layout of a settings page about plumbing. `null` (the default)
 * is a page rendered outside the surface — a test fixture, a preview — and the rows then simply keep their
 * sentences to themselves.
 */
internal val LocalSettingsHelp = compositionLocalOf<MutableState<String>?> { null }

/**
 * Hands this row's sentence to the help line while the pointer is on it, and takes it back when it leaves.
 *
 * The leaving half only clears the line if it still holds *this* row's sentence: crossing from one row to
 * the next can deliver the new row's enter before the old row's exit, and the footer must not blank what it
 * has just been given.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.reportHelp(note: String?): Modifier {
    val help = LocalSettingsHelp.current ?: return this
    val sentence = note.orEmpty()
    return this
        .onPointerEvent(PointerEventType.Enter) { help.value = sentence }
        .onPointerEvent(PointerEventType.Exit) { if (help.value == sentence) help.value = "" }
}

/**
 * **A setting this build cannot answer yet** — drawn, disabled, and honest about it.
 *
 * The tablet has the row and the window does not have the mechanism behind it, so the choice is between a
 * dead control of the same name and no row at all; neither is right. The row states what the tablet does
 * and stops, which is the only version a reader can act on (`docs/interface-contract.md` §5: say what
 * happened, never imply a control that is not there).
 */
@Composable
internal fun SettingsUnavailable(label: String, note: String) {
    SettingsRow(label = label, note = note) {
        Text("Tablet only", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    }
}

/**
 * **An answer under the command that asked**, on the button's own label column.
 *
 * The update check and *Copy app details* both answer a click; a sentence that lived only in the hover
 * help was one a reader could click and never see. It is a line of the page rather than a toast — the panel
 * that asked is the panel that answers, and it stays until the next answer replaces it.
 */
@Composable
internal fun SettingsAnswer(text: String) {
    Text(
        text,
        color = DesktopTheme.inkSecondary,
        style = DesktopTheme.body,
        modifier = Modifier.padding(start = ROW_INSET + 12.dp, end = ROW_INSET, top = 2.dp, bottom = 2.dp),
    )
}

/** A sentence under a group: what an answer does, or what it deliberately does not. */
@Composable
internal fun SettingsNote(text: String) {
    Spacer(Modifier.height(6.dp))
    // The same column as a group's heading and the rows' names: a sentence about a card is read as part of
    // it, so it starts where the card's labels start.
    Text(
        text,
        color = DesktopTheme.inkMuted,
        style = DesktopTheme.label,
        modifier = Modifier.padding(horizontal = ROW_INSET),
    )
}

/** A row's own horizontal inset inside its card, shared by its readouts too. */
internal val ROW_INSET = 12.dp

/** The steel under the selected index row: quiet enough that the accent stripe is the indicator. */
private const val SELECTED_ROW_ALPHA = 0.50f

/** How wide a slider draws inside a row — long enough to aim at, never the whole row. */
private val SLIDER_WIDTH = 150.dp

/**
 * **A choice along a range** — the window's slider: a 4 dp steel track, the accent filling it to the
 * value, a knob, and the number in the window's numeric face beside it.
 *
 * It wears the five states of `docs/interface-contract.md`: the resting steel, the hover step under the
 * pointer, a **pressed** state (the knob grows while it is dragged), the accent ring when it holds the
 * keyboard — Left/Right step it, Home/End take the ends — and *disabled*, which is not drawn here at all:
 * a range a host cannot answer is written as [SettingsUnavailable] instead.
 *
 * [step] is the quantum, so a row's answer is always one of the values its own note describes: the
 * pressure curve in hundredths, for instance, never 1.0371.
 */
@Composable
internal fun SettingsSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onValue: (Float) -> Unit,
    /** The track's length — [SLIDER_WIDTH] on a page, shorter in a 320 dp menu. */
    width: Dp = SLIDER_WIDTH,
) {
    val set = rememberUpdatedState(onValue)
    var trackPx by remember { mutableStateOf(1f) }
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    fun pickAt(px: Float) {
        val t = (px / trackPx).coerceIn(0f, 1f)
        val raw = range.start + (range.endInclusive - range.start) * t
        val snapped = range.start + ((raw - range.start) / step).roundToInt() * step
        set.value(snapped.coerceIn(range.start, range.endInclusive))
    }
    fun nudge(direction: Int) {
        set.value((value + direction * step).coerceIn(range.start, range.endInclusive))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            format(value),
            color = DesktopTheme.ink,
            style = DesktopTheme.numeric,
            maxLines = 1,
            // A tabular floor: 5 % and 200 % are the same width, so the track beside the number never
            // slides under the hand that is dragging it.
            modifier = Modifier.widthIn(min = 48.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(width)
                .height(SettingsMetrics.CONTROL_HEIGHT)
                .onSizeChanged { trackPx = it.width.toFloat().coerceAtLeast(1f) }
                .hoverFill(PropertyControlShape)
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight, Key.DirectionUp -> {
                            nudge(1); true
                        }
                        Key.DirectionLeft, Key.DirectionDown -> {
                            nudge(-1); true
                        }
                        Key.MoveHome -> {
                            set.value(range.start); true
                        }
                        Key.MoveEnd -> {
                            set.value(range.endInclusive); true
                        }
                        else -> false
                    }
                }
                .pointerInput(range, step) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            pressed = true
                            pickAt(it.x)
                        },
                        onDragEnd = { pressed = false },
                        onDragCancel = { pressed = false },
                    ) { change, _ -> pickAt(change.position.x) }
                }
                .pointerInput(range, step) { detectTapGestures { pickAt(it.x) } },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawSliderTrack(
                    fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f),
                    focused = focused,
                    pressed = pressed,
                )
            }
        }
    }
}

/**
 * The track, the accent fill and the knob.
 *
 * The knob's centre stops short of both ends by its own radius: at 0 % and 100 % it used to hang half
 * outside the track, cut by the row it sits in.
 */
private fun DrawScope.drawSliderTrack(fraction: Float, focused: Boolean, pressed: Boolean) {
    val centreY = size.height / 2f
    val trackHeight = 4.dp.toPx()
    val radius = trackHeight / 2f
    drawRoundRect(
        DesktopTheme.activeSlot,
        topLeft = Offset(0f, centreY - radius),
        size = Size(size.width, trackHeight),
        cornerRadius = CornerRadius(radius),
    )
    drawRoundRect(
        DesktopTheme.accent,
        topLeft = Offset(0f, centreY - radius),
        size = Size(size.width * fraction, trackHeight),
        cornerRadius = CornerRadius(radius),
    )
    val knob = if (pressed) 6.dp.toPx() else 5.dp.toPx()
    drawCircle(
        color = if (focused) DesktopTheme.accent else DesktopTheme.inkSecondary,
        radius = knob,
        center = Offset((size.width * fraction).coerceIn(knob, (size.width - knob).coerceAtLeast(knob)), centreY),
    )
}

/**
 * A **preset name**, as the window's one chip: 28 dp, the 4 dp control corner, a hairline at rest and the
 * accent's rim and wash while it is the preset in hand.
 *
 * It is the text control's `chip` recipe ([DesktopTextAction]), so the Stylus page and the menu's Pen block —
 * which draws the same carousel in a 272 dp panel — cannot disagree about what a choice looks like.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsPill(text: String, selected: Boolean, onPick: () -> Unit) {
    DesktopTextAction(
        text, onPick, picked = selected, role = androidx.compose.ui.semantics.Role.Tab,
        style = DesktopTheme.body, minHeight = 28.dp, chip = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** The square indicator; SettingsRow owns the hit target and checked semantics. */
@Composable
internal fun SettingsSwitch(on: Boolean) {
    PropertyCheckmark(on)
}

/** A textual command uses ink and a narrow underline rather than a filled state layer. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsTextAction(
    text: String,
    enabled: Boolean = true,
    tone: Color = DesktopTheme.accent,
    onClick: () -> Unit,
) {
    DesktopTextAction(text, onClick, enabled = enabled, tone = tone)

}

/**
 * **Two chevrons that move a row one place** — how a strip's slots are reordered.
 *
 * A pair rather than drag-and-drop: the settings page is read with the keyboard as often as the mouse (`Tab`
 * reaches a chevron, `Enter` moves it), and a list that only answers a drag would be a list half the window
 * cannot use. The ends are dimmed rather than silent, so the first slot says it has nothing above it.
 */
@Composable
internal fun SettingsStepButtons(
    upEnabled: Boolean,
    downEnabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(ChromeGlyph.CHEVRON_UP, "Move up", upEnabled, onUp)
        StepButton(ChromeGlyph.CHEVRON_DOWN, "Move down", downEnabled, onDown)
    }
}

/** One chevron of [SettingsStepButtons]: the window's own button states, at a row's height. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StepButton(
    glyph: ChromeGlyph,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val click = rememberUpdatedState(onClick)
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(SettingsMetrics.CONTROL_HEIGHT)
            .background(DesktopTheme.activeSlot.copy(alpha = if (enabled) 0.35f else 0.15f), PropertyControlShape)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) DesktopTheme.accent else Color.Transparent,
                shape = PropertyControlShape,
            )
            .hoverFill(PropertyControlShape, active = !enabled)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (!enabled) {
                    false
                } else if (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar) {
                    click.value()
                    true
                } else {
                    false
                }
            }
            .pointerInput(label, enabled) { detectTapGestures { if (enabled) click.value() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            drawChromeGlyph(glyph, if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted)
        }
    }
}

/** Settings navigation keeps its catalogue glyph and marks selection without a filled row. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsSectionRow(
    section: DesktopSettingSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsMetrics.INDEX_ROW_HEIGHT)
            // **One** indicator: the accent stripe on the left edge is the selection, and the quiet steel
            // under the row is its step. The picked state also carried the text recipe's underline, so a
            // selected section drew an L around its own name.
            .background(
                if (selected) DesktopTheme.activeSlot.copy(alpha = SELECTED_ROW_ALPHA) else Color.Transparent,
                PropertyControlShape,
            )
            // The index is names only, so its sentence is the help line's too: what the section covers.
            .drawBehind {
                if (selected) drawLine(DesktopTheme.accent, Offset(2.dp.toPx(), 8.dp.toPx()),
                    Offset(2.dp.toPx(), size.height - 8.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
            .border(
                width = 1.dp,
                color = if (focused) DesktopTheme.accent else Color.Transparent,
                shape = PropertyControlShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .reportHelp(section.summary)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The section's own mark, from the catalogue both shells read: the same picture the tablet's
        // settings list shows, at the size the chrome's ink is drawn.
        Canvas(Modifier.size(16.dp)) {
            drawSettingsGlyph(
                section.glyph,
                if (selected) DesktopTheme.accent else DesktopTheme.inkSecondary,
            )
        }
        Spacer(Modifier.width(10.dp))
        // The name and its mark, and nothing else: the index is a list to scan, and the section's one
        // sentence belongs on the page it opens rather than under all ten names at once.
        Text(
            section.title,
            color = if (selected) DesktopTheme.accent else DesktopTheme.ink,
            style = DesktopTheme.body,
            modifier = Modifier.weight(1f),
        )
    }
}

