package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * The **name field** a pen preset is saved under, in the window's glass capsule.
 *
 * It is one composable because it is one field: the menu's Pen block and the Stylus page open the same
 * draft (`DesktopSettingsState.presetDraft`), and a second copy would be a second place a name could be
 * typed with a different rule. It takes the keyboard as soon as it appears — a door named *Save as preset*
 * should not need a second click on the field it opened — and `Enter` is the same commit as the Save word
 * beside it, so a name can be written and kept without leaving the keyboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PresetNameField(
    value: String,
    onValue: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commit = rememberUpdatedState(onCommit)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier
            .height(28.dp)
            .background(DesktopTheme.glass, DesktopTheme.capsuleShape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text("Preset name", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = DesktopTheme.body.copy(color = DesktopTheme.ink),
            singleLine = true,
            cursorBrush = SolidColor(DesktopTheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        commit.value()
                        true
                    } else {
                        false
                    }
                },
        )
    }
}
