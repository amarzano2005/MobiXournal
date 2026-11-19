package com.mobixournal.desktop.tools
import com.mobixournal.desktop.tools.desktopTextFeedback
import com.mobixournal.desktop.tools.DesktopTextAction
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.ROW_INSET
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.drawToolGlyph
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.shell.Header
import com.mobixournal.desktop.shell.search.find

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import com.mobixournal.desktop.shell.WindowKeys
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.PenPalette

/**
 * The **controls of the inspector lane** — the header, the cards, and the five field types.
 *
 * Kept apart from the lane's sections so the same control cannot be drawn two ways in two families (the
 * window had a field, a switch, a colour row and a close glyph, and the placement card re-spelled the
 * last one). Everything here is a *control*, not a document: it takes a value and reports the next one,
 * which is why the whole file is previewable without a tab.
 *
 * The five states of `docs/interface-contract.md` §6 belong to these controls: hover is
 * [Modifier.hoverFill], the picked step is the steel, a disabled button keeps its ink and loses its hit
 * area, and a focused field wears the accent's ring — the window had no visible focus at all, so a
 * keyboard user could not tell which value they were typing into.
 */

/** The lane's width: `docs/interface-contract.md`'s inspector column. */
internal val INSPECTOR_WIDTH = 380.dp

/** The lane's header: 52 dp, one glyph, the family's name, and the line that says *which* element. */
internal val LANE_HEADER_HEIGHT = 52.dp

/** The lane's footer: 44 dp, so a button row and the frame's bottom air are one measure. */
internal val LANE_FOOTER_HEIGHT = 44.dp

/**
 * The lane's **header** — its identity, not just its title.
 *
 * The window used to print the family alone (`Plot`), which says nothing when three plots are on the page
 * and nothing at all about which of them the fields below would edit. The glyph is the family's slot glyph
 * from the one catalogue, the title names the family and the summary line carries the element's own
 * content (`sin(x) · −6…6`), so the panel answers "which one am I editing" before the first field.
 */
@Composable
internal fun LaneHeader(
    tool: DesktopTool?,
    title: String,
    summary: String?,
    onClose: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(LANE_HEADER_HEIGHT)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(DesktopTheme.card, com.mobixournal.desktop.settings.PropertyControlShape),
            contentAlignment = Alignment.Center,
        ) {
            if (tool != null) {
                Canvas(Modifier.size(18.dp)) { drawToolGlyph(tool, DesktopTheme.chromeInk) }
            } else {
                // A lane with no tool is the palette itself: the add mark, the phone's own.
                Canvas(Modifier.size(18.dp)) { drawChromeGlyph(ChromeGlyph.ADD, DesktopTheme.inkSecondary) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = DesktopTheme.ink, style = DesktopTheme.heading)
            if (summary != null) {
                Text(
                    text = summary,
                    color = DesktopTheme.inkSecondary,
                    style = DesktopTheme.label,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        if (onClose != null) {
            Spacer(Modifier.width(6.dp))
            LaneClose(onClose)
        }
    }
}

/**
 * The lane's **footer**: one row of actions at the bottom of the frame, shared by the placement question
 * and the editor.
 *
 * It is the same bar in both states — Cancel/Place while a question is up, Done once the element exists —
 * because they are the same lane at two moments, and a card that ended in buttons while the editor below
 * ended in nothing was two frames wearing one width. The row keeps its height when it has no action, so
 * opening and closing the lane never moves the fields above it.
 *
 * The answers end on the **same edge every dialog's do** (`docs/interface-contract.md` §2): the ordinary
 * answer on the left, the commit on the right. The lane used to lead with Place, so the same two commands
 * sat in the opposite order on the two halves of the window.
 */
@Composable
internal fun LaneFooter(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LANE_FOOTER_HEIGHT)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        content()
    }
}

/** Flat property group. The keyboard-accessible title folds its existing controls without changing values. */
@Composable
internal fun SectionCard(title: String? = null, initiallyExpanded: Boolean = true, content: @Composable () -> Unit) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    var focused by remember(title) { mutableStateOf(false) }
    val interactions = remember(title) { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val focusOwner = remember { Any() }
    DisposableEffect(focusOwner) { onDispose { WindowKeys.focusControl(focusOwner, false) } }
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    if (title != null) {
        Row(Modifier.fillMaxWidth().height(40.dp)
            .desktopTextFeedback()
            .onFocusChanged { focused = it.isFocused; WindowKeys.focusControl(focusOwner, focused) }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
             .clickable(interactionSource = interactions, indication = null) {
                if (!focused) focusManager.clearFocus()
                expanded = !expanded
            }
            .padding(horizontal = ROW_INSET), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(14.dp)) {
                if (expanded) drawChromeGlyph(ChromeGlyph.CHEVRON_DOWN, DesktopTheme.inkSecondary)
                else rotate(-90f) { drawChromeGlyph(ChromeGlyph.CHEVRON_DOWN, DesktopTheme.inkSecondary) }
            }
            Spacer(Modifier.width(8.dp))
            Text(title, color = DesktopTheme.ink, style = DesktopTheme.heading)
        }
    }
    if (title == null || expanded) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ROW_INSET, vertical = 6.dp)) { content() }
    }
}

/** A value the panel only reports (a side's length, a component's symbol). */
@Composable
internal fun Readout(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        Spacer(Modifier.weight(1f))
        Text(value, color = DesktopTheme.ink, style = DesktopTheme.numeric)
    }
}

/** Two fields side by side — a range, a grid's two counts. */
@Composable
internal fun NumberRow(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

/**
 * A number that commits on `Enter` or when the caret leaves — never per keystroke, because every commit is
 * an undo step and a half-typed `1` is not a value the user asked for.
 */
@Composable
internal fun NumberField(label: String, value: Double, onCommit: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    Field(label = label) {
        ValueField(
            initial = text,
            numeric = true,
            onCommit = { entered ->
                parseNumber(entered)?.let { parsed ->
                    text = formatNumber(parsed)
                    onCommit(parsed)
                }
            },
        )
    }
}

/**
 * [entered] as a number, or null when it is not one.
 *
 * A comma is a decimal separator on an Italian keyboard and a full stop on an English one, and the value
 * in the field is written by the same user who types the next one — so both separators are read, instead
 * of a `26,5` that parsed to nothing and left the field silently ignoring `Enter`.
 */
internal fun parseNumber(entered: String): Double? =
    entered.trim().replace(',', '.').toDoubleOrNull()

/** A labelled control: the label above, the control under it. */
@Composable
internal fun Field(label: String, field: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        Spacer(Modifier.height(3.dp))
        field()
    }
}

/**
 * The one text control of the lane: a field that commits on `Enter` or focus loss and puts itself back on
 * `Esc`.
 *
 * It wears a **focus ring** — the accent at one pixel — because the lane is a keyboard surface too and a
 * field that looks identical focused and unfocused is a field the user types into by guessing. The rest is
 * one step brighter than the card it sits on, so it reads as the place the value lives.
 */
@Composable
internal fun ValueField(
    initial: String,
    numeric: Boolean = false,
    multiline: Boolean = false,
    /**
     * What the field says **while it is being typed**, for a caller that is not writing a document: the
     * placement card keeps its answers live so the graph preview can follow the keystrokes. A committed
     * edit still goes through [onCommit] only, so nothing of a document is written per letter.
     */
    onDraft: (String) -> Unit = {},
    onCommit: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    // The lane's field is the window's one field (`DesktopTextField`) with the lane's own rules on top: the
    // commit on `Enter` or when the caret leaves, and `Esc` putting the value back. `typing = true` keeps
    // the window's shortcut table out of the way, so a letter is text and not a tool that drops the
    // selection this lane belongs to.
    DesktopTextField(
        value = text,
        onValueChange = { entered ->
            if (!numeric || entered.isEmpty() || entered == "-" || entered == "." || parseNumber(entered) != null) {
                text = entered
                onDraft(entered)
            }
        },
        singleLine = !multiline,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                val had = focused
                focused = focus.isFocused
                if (had && !focus.isFocused) onCommit(text)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        text = initial
                        onDraft(initial)
                        true
                    }
                    else -> false
                }
            },
        typing = true,
        onSubmit = { onCommit(text) },
    )
}

/** A property checkbox with a full-row hit target and keyboard activation. */
@Composable
internal fun SwitchRow(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    com.mobixournal.desktop.settings.PropertyCheckboxRow(label, on, onToggle)
}

/** Text options share the desktop ink/underline states, with a stable callback on recomposition. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ChoiceRow(options: List<String>, picked: String, onPick: (String) -> Unit) {
    // A segmented group of the window's own chips: the 4 dp control corner and the Ghost fill, so these
    // read as the same tab row the page setup draws rather than as underlined words in a lane.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            DesktopTextAction(
                label = option,
                onClick = { onPick(option) },
                modifier = Modifier.weight(1f),
                picked = option.equals(picked, ignoreCase = true),
                role = androidx.compose.ui.semantics.Role.Tab,
                ghostFill = true,
                minHeight = 28.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * The palette, as a row of swatches: a colour field is a colour, not a hex box.
 *
 * The current colour wears the accent ring and a hovered swatch a muted one, so a row of eight tints still
 * answers "which one is set" and "which one is under the pointer" — the row used to answer neither.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ColourRow(current: Int? = null, onPick: (Int) -> Unit) {
    // And here: a swatch tapped after another field changed must write into the element as it is now.
    val answer = rememberUpdatedState(onPick)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        for (colour in PenPalette.COLORS.take(COLOUR_SWATCHES)) {
            var hovered by remember(colour) { mutableStateOf(false) }
            val picked = current != null && (colour or 0xFF000000.toInt()) == current
            Box(
                Modifier
                    .weight(1f)
                    .height(20.dp)
                    .background(Color(colour), com.mobixournal.desktop.settings.PropertyControlShape)
                    .border(
                        width = if (picked) 2.dp else 1.dp,
                        color = when {
                            picked -> DesktopTheme.accent
                            hovered -> DesktopTheme.inkMuted
                            else -> DesktopTheme.hairline
                        },
                        shape = com.mobixournal.desktop.settings.PropertyControlShape,
                    )
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .pointerInput(colour) { detectTapGestures { answer.value(colour) } },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** How many of the palette's swatches a colour row offers: the eight the factory palette starts with. */
private const val COLOUR_SWATCHES = 8

/** The lane's close glyph: two monoline strokes, the same one the placement card used. */
@Composable
internal fun LaneClose(onClick: () -> Unit) = CrossGlyph(
    ink = DesktopTheme.chromeInk,
    description = "Close inspector",
    onClick = onClick,
)

/**
 * A row's **remove** control — the equation row's ×.
 *
 * The same two strokes as the close glyph, drawn in the secondary ink: a removed equation is an ordinary
 * edit of the question, not a dismissal of the lane, and the two must not read as the same act.
 */
@Composable
internal fun RemoveGlyph(onClick: () -> Unit) = CrossGlyph(
    ink = DesktopTheme.inkSecondary,
    description = "Remove",
    onClick = onClick,
)

/**
 * The one × of the lane, at its one size and with its one focus ring.
 *
 * It is a real control ([DesktopIconButton] at 24 dp): reachable with Tab, wearing the window's accent ring
 * while it has the keyboard, instead of an unlabelled `detectTapGestures` box that only the mouse could
 * reach.
 */
@Composable
private fun CrossGlyph(ink: Color, description: String, onClick: () -> Unit) = DesktopIconButton(
    description = description,
    onClick = onClick,
    size = 24.dp,
    tooltip = null,
) {
    Canvas(Modifier.size(14.dp)) { drawChromeGlyph(ChromeGlyph.CLOSE, ink) }
}

/**
 * A number as the lane prints it — [com.mobixournal.render.decimalText]'s one-decimal rule, under the
 * lane's name for it.
 *
 * One decimal is the lane's whole precision, and rounding **first** matters: a size derived from a pen
 * width is 26.000000000000004, which used to print as `26.0` in a field that wanted `26`.
 */
internal fun formatNumber(value: Double): String = com.mobixournal.render.decimalText(value)

/** [value] at one decimal, for a readout — `Locale.ROOT`, so a readout never prints a comma. */
internal fun Double.roundToTenth(): String = String.format(java.util.Locale.ROOT, "%.1f", this)

/**
 * One **equation row** of a graph: the colour its curve is drawn in, the source field, its remove glyph —
 * and, under the row and only while its dot is open, the palette.
 *
 * A graph of two curves is read by colour, so an equation without its own ink is a second curve the eye
 * cannot tell from the first. The dot is the whole colour control until it is opened, which keeps six
 * equations readable in a 300 dp lane; the palette is the same [ColourRow] every other colour field uses,
 * so there is one set of swatches and one accent ring.
 */
@Composable
internal fun EquationRow(
    label: String,
    source: String,
    colour: Int,
    removable: Boolean,
    onDraft: (String) -> Unit,
    /** What `Enter` (or leaving the field) writes. Empty for a placement card, whose answers are not yet a document. */
    onCommit: (String) -> Unit = {},
    onColour: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var paletteOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColourDot(colour = colour, open = paletteOpen) { paletteOpen = !paletteOpen }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            Field(label) {
                ValueField(initial = source, onDraft = onDraft, onCommit = onCommit)
            }
        }
        if (removable) {
            Spacer(Modifier.width(6.dp))
            RemoveGlyph(onRemove)
        }
    }
    if (paletteOpen) {
        ColourRow(current = colour) { picked ->
            onColour(picked)
            paletteOpen = false
        }
    }
}

/** The dot an [EquationRow] wears: the curve's own ink, ringed in the accent while its palette is open. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ColourDot(colour: Int, open: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Box(
        Modifier
            .size(16.dp)
            .background(Color(colour), com.mobixournal.desktop.settings.PropertyControlShape)
            .border(
                width = if (open || focused) 2.dp else 1.dp,
                color = when {
                    focused || open -> DesktopTheme.accent
                    hovered -> DesktopTheme.inkMuted
                    else -> DesktopTheme.hairline
                },
                shape = com.mobixournal.desktop.settings.PropertyControlShape,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            // The swatch is the row's **control**: the accent ring says which one the keyboard is on, and
            // the palette it opens is the same control's answer, so it needs no second glyph beside it.
            .onFocusChanged {
                focused = it.isFocused
                WindowKeys.focusControl(owner, it.isFocused)
            }
            .clickable(role = Role.Button) { onClick() }
            .semantics { contentDescription = "Curve colour" },
    )
}
