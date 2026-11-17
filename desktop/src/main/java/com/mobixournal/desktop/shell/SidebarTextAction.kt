package com.mobixournal.desktop.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme

/** A narrow keyboard-focus edge replaces the default filled indication. */
@Composable
internal fun Modifier.quietSidebarClick(
    enabled: Boolean = true,
    role: Role = Role.Button,
    interactions: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier {
    val focused by interactions.collectIsFocusedAsState()
    return drawBehind {
        if (enabled && focused) drawLine(DesktopTheme.accent,
            Offset(4.dp.toPx(), size.height - 1.dp.toPx()),
            Offset(size.width - 4.dp.toPx(), size.height - 1.dp.toPx()), 1.dp.toPx())
    }.clickable(interactionSource = interactions, indication = null, enabled = enabled,
        role = role, onClick = onClick)
}

/** Text navigation and commands have no filled state layer, including mouse press and keyboard focus. */
@Composable
internal fun SidebarTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    picked: Boolean = false,
    role: Role = Role.Button,
    description: String? = null,
    style: TextStyle = DesktopTheme.label,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Text(
        label,
        color = when {
            !enabled -> DesktopTheme.inkMuted
            picked || focused -> DesktopTheme.accent
            hovered || pressed -> DesktopTheme.ink
            else -> DesktopTheme.inkSecondary
        },
        style = style,
        modifier = modifier
            .drawBehind {
                if (picked || (enabled && focused)) {
                    val stroke = (if (picked) 2.dp else 1.dp).toPx()
                    drawLine(DesktopTheme.accent, Offset(8.dp.toPx(), size.height - stroke / 2),
                        Offset(size.width - 8.dp.toPx(), size.height - stroke / 2), stroke)
                }
            }
            .semantics {
                if (role == Role.Tab) selected = picked
                if (description != null) contentDescription = description
            }
            .clickable(interactionSource = interactions, indication = null, enabled = enabled,
                role = role, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}
