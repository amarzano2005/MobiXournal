package com.mobixournal.desktop.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * **A readout**, as a settings row: the name at the row's own inset, the value at the row's right edge.
 *
 * It is deliberately *not* the inspector's `Readout` (`tools/InspectorControls.kt`): that one sits inside a
 * section card that pads itself, so it carries no inset of its own. A settings card pads its **rows** and
 * nothing else, which is why the Audio page's *Recording format* and *Recordings* started a dozen dp to the
 * left of every label above them — a ragged left edge under a perfectly straight one. Same words, the
 * row's own measure, drawn in the label and the numeric roles a row's two halves already use.
 */
@Composable
internal fun SettingsReadout(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsMetrics.ROW_HEIGHT)
            .padding(horizontal = ROW_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DesktopTheme.inkMuted, style = DesktopTheme.label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Text(value, color = DesktopTheme.ink, style = DesktopTheme.numeric, maxLines = 1)
    }
}

/**
 * **A search field**, as a row's control: a capsule wide enough for a word or two, its prompt inside it, and
 * a × that empties it.
 *
 * It is the window's capsule, not a text box borrowed from a platform: the same steel at rest, the accent
 * rim when it holds the caret, and the ink's own body role for what is typed — so a page that filters
 * itself reads as the same surface as the rows it filters.
 */
@Composable
internal fun SettingsSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .height(SettingsMetrics.CONTROL_HEIGHT)
            .width(SEARCH_FIELD_WIDTH)
            .background(DesktopTheme.activeSlot.copy(alpha = 0.35f), DesktopTheme.capsuleShape)
            .border(
                width = 1.dp,
                color = if (focused) DesktopTheme.accent else Color.Transparent,
                shape = DesktopTheme.capsuleShape,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = DesktopTheme.inkMuted, style = DesktopTheme.body, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = DesktopTheme.body.copy(color = DesktopTheme.ink),
                cursorBrush = SolidColor(DesktopTheme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                "×",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                modifier = Modifier.pointerInput(value) { detectTapGestures { onValueChange("") } },
            )
        }
    }
}

/** The search capsule: wide enough for a tool's name, never the whole row. */
private val SEARCH_FIELD_WIDTH = 220.dp
