package com.mobixournal.desktop.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph

/**
 * **The Settings search field**: a single unified search capsule in the panel's header.
 *
 * It filters sections and settings across the entire panel (including shortcut commands and keys).
 * An explicit text hover icon ensures the mouse pointer arrow does not disappear when moving over
 * the field, and an inline clear glyph allows dismissing the filter with a single click.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsSearchField(
    query: String,
    onQuery: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    Row(
        modifier
            .height(SettingsMetrics.CONTROL_HEIGHT)
            .background(DesktopTheme.panelField, PropertyControlShape)
            .pointerHoverIcon(PointerIcon.Default)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawChromeGlyph(
                ChromeGlyph.SEARCH,
                if (query.isNotBlank()) DesktopTheme.accent else DesktopTheme.inkMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).pointerHoverIcon(PointerIcon.Text),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    "Search settings…",
                    color = DesktopTheme.inkMuted,
                    style = DesktopTheme.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                textStyle = DesktopTheme.body.copy(color = DesktopTheme.ink),
                singleLine = true,
                cursorBrush = SolidColor(DesktopTheme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(18.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(10.dp)) {
                    drawChromeGlyph(ChromeGlyph.CLOSE, DesktopTheme.inkMuted)
                }
            }
        }
    }
}
