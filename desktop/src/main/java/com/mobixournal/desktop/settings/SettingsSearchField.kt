package com.mobixournal.desktop.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.tools.DesktopTextField
import com.mobixournal.design.ChromeGlyph

/**
 * **The Settings search field**: the window's own field, in the panel's header.
 *
 * It filters sections and settings across the whole panel, including shortcut commands and keys. It was a
 * capsule of its own once — a fill and a bare `BasicTextField` — which on the dark desk drew its `panelField`
 * tone at an alpha the rows behind it still read through, so the one control the reader has to find looked
 * like a hole in the header. It is now [DesktopTextField]: the `panelField` fill with the `borderStrong`
 * hairline, the accent border while it is typed in, the 12 sp text and placeholder, the pointer's text
 * cursor, and a hoverable `×` that names itself *Clear search*.
 *
 * [DesktopTextField] also registers the field with the shell as a **typing** control, so a letter typed here
 * is a letter and not the window's accelerator for a tool.
 */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQuery: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clear: (() -> Unit)? = if (query.isEmpty()) null else { { onQuery("") } }
    DesktopTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = "Search settings…",
        modifier = modifier,
        height = SettingsMetrics.CONTROL_HEIGHT,
        typing = true,
        description = "Search settings",
        onFocusChange = onFocus,
        onClear = clear,
        clearDescription = "Clear search",
        leadingIcon = {
            // The magnifier wears the accent while it is narrowing something: the field's answer, at rest
            // and in the panel's own colour.
            Canvas(Modifier.size(14.dp)) {
                drawChromeGlyph(
                    ChromeGlyph.SEARCH,
                    if (query.isNotBlank()) DesktopTheme.accent else DesktopTheme.inkMuted,
                )
            }
        },
    )
}
