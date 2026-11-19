package com.mobixournal.desktop.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A dialog's answer, in the window's four-command hierarchy: the commit is Primary, the ordinary answer
 * Secondary, and the one that takes something away Destructive.
 *
 * It used to be an underlined label, which made a Save look like a menu entry and left the dialog with no
 * filled control at all. The shape, the five states and the sizes now belong to [DesktopButton]
 * (`docs/interface-contract.md` §2, §6) — this name remains because a dialog row reads as a row of
 * answers, and because the fourteen call sites already speak it.
 */
@Composable
internal fun PanelButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    /**
     * The **refusal red**, for the answer that takes something away: `DesktopTheme.danger`, never the
     * document red, which is the PDF convention (`docs/interface-contract.md` §2).
     */
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    DesktopButton(
        label = label,
        onClick = onClick,
        variant = when {
            destructive -> DesktopButtonVariant.Destructive
            primary -> DesktopButtonVariant.Primary
            else -> DesktopButtonVariant.Secondary
        },
        modifier = modifier,
        enabled = enabled,
    )
}
