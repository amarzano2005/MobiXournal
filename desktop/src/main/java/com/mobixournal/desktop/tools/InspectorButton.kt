package com.mobixournal.desktop.tools

import com.mobixournal.desktop.tools.DesktopTextAction
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

/** Inspector commands share the desktop text recipe and keep keyboard ownership in the lane. */
@Composable internal fun InspectorButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    DesktopTextAction(
        if (label == "Add equation") "+ Add equation" else label, onClick,
        enabled = enabled, style = DesktopTheme.body,
        tone = if (primary) DesktopTheme.accent else DesktopTheme.inkSecondary,
        modifier = Modifier.onFocusChanged { WindowKeys.focusControl(owner, it.isFocused) },
    )

}
