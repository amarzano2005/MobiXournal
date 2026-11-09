package com.mobixournal.desktop.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.PropertyControlShape
import com.mobixournal.desktop.shell.WindowKeys

/** Property-panel actions only; other dialogs keep PanelButton's original recipe. */
@Composable internal fun InspectorButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Box(Modifier.heightIn(min = 40.dp)
        .background(when { !enabled -> Color.Transparent; primary -> DesktopTheme.accent.copy(alpha = if (pressed) .75f else 1f); pressed -> DesktopTheme.activeSlot; else -> Color.Transparent }, PropertyControlShape)
        .hoverFill(PropertyControlShape, active = !enabled)
        .border(1.dp, if (focused) { if (primary) DesktopTheme.ink else DesktopTheme.accent } else Color.Transparent, PropertyControlShape)
        .onFocusChanged { focused = it.isFocused; WindowKeys.focusControl(owner, focused) }
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(if (label == "Add equation") "+ Add equation" else label,
            color = when { !enabled -> DesktopTheme.inkMuted; primary -> DesktopTheme.desk; else -> DesktopTheme.accent },
            style = DesktopTheme.body)
    }
}
