package com.mobixournal.desktop.tools

import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.shell.WindowKeys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The window's **one command**, in the four variants a flat workstation needs.
 *
 * The desktop used to answer the same question three ways — a text button with an underline, a filled
 * Material button, a capsule chip — so two dialogs in the same window could disagree about what a commit
 * looks like. `docs/interface-contract.md` §2 and §6 now name exactly these four, and
 * [DesktopButton] is where every filled command lands: a dialog's answer, the inspector's commit, a page
 * operation. A label that is *navigation* rather than a command (a tab, a preset name, a status link)
 * stays a [DesktopTextAction].
 *
 * Every variant keeps one arrangement of the five states (§6): a pointer resting on the control is one
 * step past its rest fill, a press is two, a picked control keeps the steel, a disabled one keeps its
 * shape at 45 %, and the keyboard's focus is a 2 dp accent ring. The state is **one background**, never a
 * wash laid over another fill: two layers let a light and the shape it lights disagree.
 */
internal enum class DesktopButtonVariant {
    /** The commit: opaque accent, the on-colour's ink. */
    Primary,

    /** The ordinary answer: the control tone with a strong hairline. */
    Secondary,

    /** An answer that must not read as a box: transparent until the pointer arrives. */
    Ghost,

    /** The answer that takes something away: the refusal red, never the document red. */
    Destructive,
}

/**
 * A filled desktop command — dialog answers, the lane's commits, page operations.
 *
 * [minHeight] is 32 dp by default: one flat control, with the air that lets a dialog row breathe, and the
 * 4 dp [DesktopTheme.controlShape] corner the contract allows every control (§3). [picked] is the steel of
 * a held control, which the Ghost variant needs and a commit rarely does.
 */
@Composable
internal fun DesktopButton(
    label: String,
    onClick: () -> Unit,
    variant: DesktopButtonVariant = DesktopButtonVariant.Secondary,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    picked: Boolean = false,
    minHeight: Dp = 32.dp,
    style: TextStyle = DesktopTheme.body,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    // The lane's keyboard owns Enter, Space and the arrows while a control has focus, so every command
    // registers itself: a dialog walked with Tab must not have its keys taken by a page command.
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    val face = buttonFace(variant, enabled, hovered, pressed, picked, focused)
    Row(
        modifier
            .heightIn(min = minHeight)
            .clip(DesktopTheme.controlShape)
            .background(face.fill, DesktopTheme.controlShape)
            .then(if (face.borderWidth > 0.dp) Modifier.border(face.borderWidth, face.border, DesktopTheme.controlShape) else Modifier)
            .onFocusChanged { WindowKeys.focusControl(owner, it.isFocused) }
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.invoke()
        Text(
            label,
            color = face.ink,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A glyph command: the header's controls, a strip's commands, a field's clear.
 *
 * Transparent at rest so the band behind it is the object; [DesktopTheme.hoverSlot] under the pointer,
 * [DesktopTheme.activeSlot] while picked, and a 1 dp accent hairline for the keyboard. The tooltip is the
 * delayed pill the strips already speak — 350 ms, so a pointer crossing a row of icons is not narrated at.
 */
@Composable
internal fun DesktopIconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    enabled: Boolean = true,
    picked: Boolean = false,
    tooltip: String? = description,
    shortcut: String? = null,
    content: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    var tooltipUp by remember { mutableStateOf(false) }
    LaunchedEffect(hovered, tooltip) {
        tooltipUp = false
        if (hovered && tooltip != null) {
            delay(TOOLTIP_DELAY_MS)
            tooltipUp = true
        }
    }
    Box {
        Box(
            modifier
                .size(size)
                .clip(DesktopTheme.controlShape)
                .background(
                    when {
                        picked -> DesktopTheme.activeSlot
                        hovered || pressed -> DesktopTheme.hoverSlot
                        else -> Color.Transparent
                    },
                    DesktopTheme.controlShape,
                )
                .then(
                    if (focused) Modifier.border(1.dp, DesktopTheme.accent, DesktopTheme.controlShape) else Modifier,
                )
                .onFocusChanged { WindowKeys.focusControl(owner, it.isFocused) }
                .semantics { contentDescription = description }
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        if (tooltipUp && hovered && tooltip != null) {
            val gap = with(LocalDensity.current) { size.roundToPx() } + TOOLTIP_OFFSET_PX
            StripPill(label = tooltip, accent = shortcut ?: "", offset = IntOffset(0, gap))
        }
    }
}

/** A variant's colours for one state, so the recipe is one expression and not four call sites. */
private class ButtonFace(
    val fill: Color,
    val ink: Color,
    val border: Color,
    val borderWidth: Dp,
)

/**
 * The variant's answer to the pointer: rest, hover, press, picked, disabled and focus, in that order of
 * precedence. Disabled is applied last, so a disabled Ghost is the same transparent box as a resting one
 * with dimmed ink, and a disabled commit is the accent at 45 %.
 */
private fun buttonFace(
    variant: DesktopButtonVariant,
    enabled: Boolean,
    hovered: Boolean,
    pressed: Boolean,
    picked: Boolean,
    focused: Boolean,
): ButtonFace {
    var fill: Color
    var ink: Color
    var border: Color = Color.Transparent
    var borderWidth = 0.dp
    when (variant) {
        DesktopButtonVariant.Primary -> {
            // A tint of the on-colour: an opaque accent that answers the pointer must not turn grey, so
            // the step is toward its own ink rather than toward black or white.
            fill = when {
                pressed -> lerp(DesktopTheme.accent, DesktopTheme.onAccent, PRESSED_LIFT)
                hovered -> lerp(DesktopTheme.accent, DesktopTheme.onAccent, HOVER_LIFT)
                else -> DesktopTheme.accent
            }
            ink = DesktopTheme.onAccent
        }
        DesktopButtonVariant.Secondary -> {
            fill = when {
                pressed || picked -> DesktopTheme.activeSlot
                hovered -> DesktopTheme.hoverSlot.compositeOver(DesktopTheme.control)
                else -> DesktopTheme.control
            }
            ink = DesktopTheme.ink
            border = DesktopTheme.borderStrong
            borderWidth = 1.dp
        }
        DesktopButtonVariant.Ghost -> {
            fill = when {
                pressed || picked -> DesktopTheme.activeSlot
                hovered -> DesktopTheme.hoverSlot
                else -> Color.Transparent
            }
            ink = when {
                picked -> DesktopTheme.accent
                hovered || pressed -> DesktopTheme.ink
                else -> DesktopTheme.inkSecondary
            }
        }
        DesktopButtonVariant.Destructive -> {
            fill = DesktopTheme.danger.copy(
                alpha = when {
                    pressed -> DESTRUCTIVE_PRESSED
                    hovered -> DESTRUCTIVE_HOVER
                    else -> DESTRUCTIVE_REST
                },
            )
            ink = DesktopTheme.danger
            border = DesktopTheme.danger.copy(alpha = DESTRUCTIVE_BORDER)
            borderWidth = 1.dp
        }
    }
    if (focused && enabled) {
        border = DesktopTheme.accent
        borderWidth = 2.dp
    }
    if (!enabled) {
        fill = fill.copy(alpha = fill.alpha * DISABLED_ALPHA)
        ink = ink.copy(alpha = ink.alpha * DISABLED_ALPHA)
        if (border.alpha > 0f) border = border.copy(alpha = border.alpha * DISABLED_ALPHA)
    }
    return ButtonFace(fill, ink, border, borderWidth)
}

/** How long a glyph control waits under the pointer before its pill speaks (`docs/interface-contract.md` §6). */
private const val TOOLTIP_DELAY_MS = 350L

/** The air between a glyph control and its pill, in pixels. */
private const val TOOLTIP_OFFSET_PX = 6

/** A disabled control keeps its shape and loses 55 % of its ink. */
private const val DISABLED_ALPHA = 0.45f

/** The Primary's hover step toward its own ink. */
private const val HOVER_LIFT = 0.10f

/** The Primary's press step: one further, so a commit answers before the dialog closes. */
private const val PRESSED_LIFT = 0.18f

/** The destructive answer's wash at rest, under the pointer, and held down. */
private const val DESTRUCTIVE_REST = 0.14f
private const val DESTRUCTIVE_HOVER = 0.22f
private const val DESTRUCTIVE_PRESSED = 0.30f

/** The destructive answer's hairline. */
private const val DESTRUCTIVE_BORDER = 0.40f
