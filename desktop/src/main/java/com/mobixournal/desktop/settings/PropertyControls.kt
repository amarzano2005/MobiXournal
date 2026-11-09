package com.mobixournal.desktop.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.design.DesignTokens
import com.mobixournal.desktop.shell.WindowKeys
import com.mobixournal.design.ChromeGlyph

/** Restricted to desktop property panels; toolbar and floating-dialog shapes stay unchanged. */
internal val PropertyControlShape = RoundedCornerShape(DesignTokens.PROPERTY_CONTROL_CORNER_DP.dp)

/** Indicator only: its owning row supplies the accessible hit target and checked semantics. */
@Composable internal fun PropertyCheckmark(checked: Boolean) {
    Box(Modifier.size(18.dp)
        .background(if (checked) DesktopTheme.accent else Color.Transparent, PropertyControlShape)
        .border(1.dp, if (checked) DesktopTheme.accent else DesktopTheme.inkMuted, PropertyControlShape)) {
        if (checked) Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            drawChromeGlyph(ChromeGlyph.CHECK, DesktopTheme.desk)
        }
    }
}

@Composable internal fun PropertyCheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp)
        .background(if (pressed) DesktopTheme.activeSlot else Color.Transparent, PropertyControlShape)
        .hoverFill(PropertyControlShape)
        .border(1.dp, if (focused) DesktopTheme.accent else Color.Transparent, PropertyControlShape)
        .onFocusChanged { focused = it.isFocused; WindowKeys.focusControl(owner, focused) }
        .toggleable(checked, interactionSource = interactions, indication = null, role = Role.Checkbox, onValueChange = onChange)
        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PropertyCheckmark(checked)
        Spacer(Modifier.width(10.dp))
        Text(label, color = DesktopTheme.ink, style = DesktopTheme.body)
    }
}
