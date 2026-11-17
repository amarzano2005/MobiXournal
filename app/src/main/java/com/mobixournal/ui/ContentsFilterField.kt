package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.DesignTokens

/** The chrome search treatment, sized to the neighbouring action rather than a floating form label. */
@Composable
internal fun ContentsFilter(query: String, onQuery: (String) -> Unit, onClear: () -> Unit, modifier: Modifier) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val shape = RoundedCornerShape(DesignTokens.CAPSULE_CORNER_RADIUS_DP.dp)
    val colors = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = DesignTokens.COMPACT_BUTTON_SIZE_DP.dp)
        .background(if (hovered) colors.surfaceContainerHighest else colors.surfaceContainerHigh, shape)
        .border(DesignTokens.RIM_WIDTH_DP.dp, if (focused) colors.primary else colors.outlineVariant.copy(alpha = .6f), shape)
        .hoverable(interactions).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(ChromeGlyph.SEARCH.imageVector(), null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary), interactionSource = interactions,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "Filter bookmarks" }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) Text("Filter bookmarks", style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant, maxLines = 1)
                    field()
                }
            },
        )
        if (query.isNotEmpty()) IconButton(onClick = onClear,
            modifier = Modifier.size(DesignTokens.COMPACT_BUTTON_SIZE_DP.dp)) {
            Icon(ChromeGlyph.CLOSE.imageVector(), "Clear filter", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        }
    }
}
