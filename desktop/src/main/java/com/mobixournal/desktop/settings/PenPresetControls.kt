package com.mobixournal.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.mobixournal.desktop.tools.DesktopTextField

/**
 * The **name field** a pen preset is saved under: the window's own [DesktopTextField].
 *
 * It is one field because it is one draft: the menu's Pen block and the Stylus page open the same name
 * (`DesktopSettingsState.presetDraft`), and a second copy would be a second place a name could be typed with
 * a different rule.
 *
 * It used to be drawn here — a `BasicTextField` on the drafting `panelField` tone with no hairline, which on
 * the dark theme was a field the reader could not see: no edge, no focus state, and a 13 sp body face beside
 * the 12 sp tabular figures every other value in the window is set in. The shared field brings all three
 * back: the `borderStrong` hairline at rest, the accent while it is being typed in, and the field face at
 * 12 sp. The text cursor is the window's.
 *
 * It takes the keyboard as soon as it appears — a door named *Save as preset* should not need a second click
 * on the field it opened — and `Enter` is the same commit as the Save word beside it, so a name can be
 * written and kept without leaving the keyboard. `Esc` puts the draft away (never the surface behind it): the
 * field owns the key while it is open, which is what the Stylus page's own note promises and what a menu that
 * takes the keyboard owes the reader.
 */
@Composable
internal fun PresetNameField(
    value: String,
    onValue: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
) {
    val commit = rememberUpdatedState(onCommit)
    val cancel = rememberUpdatedState(onCancel)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DesktopTextField(
        value = value,
        onValueChange = onValue,
        placeholder = "Preset name",
        // The keys are the field's before the shell's: a letter typed here is a letter, never a tool's
        // accelerator (`WindowKeys.focusField`).
        typing = true,
        focusRequester = focus,
        description = "Preset name",
        onSubmit = { commit.value() },
        modifier = modifier
            // A preview on the field's own ancestor sees the press before the text field does, which is what
            // makes Enter and Esc the field's answers rather than the shell's.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        commit.value()
                        true
                    }
                    Key.Escape -> {
                        cancel.value()
                        true
                    }
                    else -> false
                }
            }
            .pointerHoverIcon(PointerIcon.Text),
    )
}
