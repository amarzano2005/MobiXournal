package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/**
 * The window's **hamburger menu** — the tablet's overflow menu, ported: a glass button in the header
 * that opens one panel of file, page and view commands.
 *
 * The header used to spend this glyph on the sidebar toggle alone, which left the window with no visible
 * door to New, Open, Save or the page commands the tablet offers. The menu now holds them, in the same
 * order the tablet's does (file actions first, then the page, then the view), and the sidebar toggle is a
 * row of its own rather than the whole button.
 */
@Composable
internal fun HeaderMenu(
    sidebarVisible: Boolean,
    canSave: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onPageBackground: () -> Unit,
    onPageFormat: () -> Unit,
    onToggleSidebar: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        HeaderGlyph(
            active = open,
            onClick = { open = !open },
            draw = { color, stroke ->
                drawLine(color, androidx.compose.ui.geometry.Offset(0f, 2f), androidx.compose.ui.geometry.Offset(size.width, 2f), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height / 2f), androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height - 2f), androidx.compose.ui.geometry.Offset(size.width, size.height - 2f), stroke)
            },
        )
        if (open) {
            Popup(
                offset = with(density) { IntOffset(0, (DesktopTheme.headerHeight + 6.dp).roundToPx()) },
                onDismissRequest = { open = false },
            ) {
                Column(
                    Modifier
                        .glassSurface()
                        .width(250.dp)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MenuRow("New page", enabled = true) { open = false; onNew() }
                    MenuRow("Open…", enabled = true) { open = false; onOpen() }
                    MenuRow("Save", enabled = canSave) { open = false; onSave() }
                    MenuRow("Save As…", enabled = true) { open = false; onSaveAs() }
                    MenuDivider()
                    MenuRow("Page background…", enabled = true) { open = false; onPageBackground() }
                    MenuRow("Page format…", enabled = true) { open = false; onPageFormat() }
                    MenuDivider()
                    MenuRow(
                        label = if (sidebarVisible) "Hide sidebar" else "Show sidebar",
                        shortcut = "Ctrl+B",
                        enabled = true,
                    ) { open = false; onToggleSidebar() }
                }
            }
        }
    }
}

/** One row of the menu: a label, an optional accelerator, and the disabled tone when it cannot act. */
@Composable
private fun MenuRow(
    label: String,
    shortcut: String? = null,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .width(234.dp)
            .heightIn(min = 32.dp)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape, active = false)
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.body,
            modifier = Modifier.weight(1f),
        )
        if (shortcut != null) {
            Text(shortcut, color = DesktopTheme.accent, style = DesktopTheme.numeric)
        }
    }
}

/** A hairline between two parts of the menu, in the band's own rim ink. */
@Composable
private fun MenuDivider() {
    Spacer(Modifier.height(4.dp))
    Box(Modifier.width(234.dp).height(1.dp).background(DesktopTheme.hairline))
    Spacer(Modifier.height(4.dp))
}
