package com.mobixournal.desktop.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 *
 * [chip] is the **choice** recipe — a preset's name in the Stylus strip: one 4 dp outline, the accent's rim
 * and its 12 % wash while that chip is the answer in hand, and no underline, because a chip is a control
 * rather than a link. It is the one place the ink-and-underline rule gives way, and it is opt-in so every
 * other label stays a label (`docs/desktop-settings.md`).
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
    chip: Boolean = false,
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
            else -> if (chip) DesktopTheme.ink else tone
        },
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier
            .background(
                when {
                    // A chip is a choice, not a slot: the accent's own wash while it is the answer in
                    // hand, never the steel a picked tool wears — a filled preset reads as a button.
                    chip -> when {
                        picked -> DesktopTheme.accent.copy(alpha = CHIP_WASH_ALPHA)
                        hovered || pressed -> DesktopTheme.hoverSlot
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    }
                    !ghostFill -> androidx.compose.ui.graphics.Color.Transparent
                    pressed || picked -> DesktopTheme.activeSlot
                    hovered -> DesktopTheme.hoverSlot
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
                DesktopTheme.controlShape,
            )
            .then(
                if (chip) {
                    Modifier.border(
                        1.dp,
                        if (picked || (enabled && focused)) DesktopTheme.accent else DesktopTheme.hairline,
                        DesktopTheme.controlShape,
                    )
                } else {
                    Modifier
                },
            )
            .drawBehind {
                // A chip draws its state as a rim, so the label's underline would be a second indicator.
                if (!chip && (picked || (enabled && (focused || hovered || pressed)))) {
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

/** How much of the accent a picked chip wears: a choice the eye finds on the panel's own tone. */
private const val CHIP_WASH_ALPHA = 0.12f

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
