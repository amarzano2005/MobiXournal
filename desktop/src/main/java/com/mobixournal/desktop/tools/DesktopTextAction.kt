package com.mobixournal.desktop.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.onPointerEvent
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

/**
 * A **label that acts** — a tab, a preset name, a status link, a step in a dialog's little row.
 *
 * It is not a button: nothing is drawn around the words, so the words stay where they are and the state is
 * spoken by the ink and one underline (`docs/interface-contract.md` §6). A command that needs a box is a
 * [DesktopButton] — its Ghost variant is this control with the fill.
 *
 * [ghostFill] gives the label the Ghost variant's 4 dp step — `hoverSlot` under the pointer, `activeSlot`
 * while picked — for a place that reads as a control rather than as navigation. It is opt-in because the
 * sidebar's tabs are ink-only by the contract, and a helpfully filled tab would be a second look for a
 * control the reader already knows.
 */
@Composable
internal fun DesktopTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    picked: Boolean = false,
    role: Role = Role.Button,
    description: String? = null,
    style: TextStyle = DesktopTheme.label,
    tone: androidx.compose.ui.graphics.Color = DesktopTheme.inkSecondary,
    minHeight: androidx.compose.ui.unit.Dp = 40.dp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ghostFill: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Text(
        label,
        color = when {
            !enabled -> DesktopTheme.inkMuted
            tone == DesktopTheme.documentRed || tone == DesktopTheme.danger -> tone
            picked || focused -> DesktopTheme.accent
            hovered || pressed -> DesktopTheme.ink
            else -> tone
        },
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier
            .background(
                when {
                    !ghostFill -> androidx.compose.ui.graphics.Color.Transparent
                    pressed || picked -> DesktopTheme.activeSlot
                    hovered -> DesktopTheme.hoverSlot
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
                DesktopTheme.controlShape,
            )
            .drawBehind {
                if (picked || (enabled && (focused || hovered || pressed))) {
                    val stroke = (if (picked) 2.dp else 1.dp).toPx()
                    drawLine(if (picked || focused) DesktopTheme.accent else tone, Offset(8.dp.toPx(), size.height - stroke / 2),
                        Offset(size.width - 8.dp.toPx(), size.height - stroke / 2), stroke)
                }
            }
            .semantics {
                if (role == Role.Tab) selected = picked
                if (description != null) contentDescription = description
            }
            .clickable(interactionSource = interactions, indication = null, enabled = enabled,
                role = role, onClick = onClick)
            .heightIn(min = minHeight)
            .padding(contentPadding),
    )
}

/** Compound text controls keep their gestures while sharing unfilled hover/press/focus/selection feedback. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.desktopTextFeedback(picked: Boolean = false, enabled: Boolean = true): Modifier {
    var hovered by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    return this
        .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { hovered = true }
        .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) { hovered = false; held = false }
        .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press) { held = true }
        .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release) { held = false }
        .onFocusChanged { focused = it.isFocused }
        .drawBehind {
            // A press is answered like a hover was — the label's own rule: the ink and the underline, never
            // a box the reader did not ask for.
            if (enabled && (picked || hovered || held || focused)) {
                val stroke = (if (picked) 2.dp else 1.dp).toPx()
                drawLine(if (picked || focused) DesktopTheme.accent else DesktopTheme.inkSecondary,
                    Offset(8.dp.toPx(), size.height - stroke / 2),
                    Offset(size.width - 8.dp.toPx(), size.height - stroke / 2), stroke)
            }
        }
}
