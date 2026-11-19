package com.mobixournal.desktop.tools

import androidx.compose.runtime.Composable

/**
 * A command of the properties lane, in the window's four-command hierarchy: the lane's commit is Primary,
 * every other answer Secondary.
 *
 * The lane used to rewrite one label in place, prefixing a plus to a single caller's words, so a component
 * decided what its callers said. The plus belongs to the command that adds a row, which spells it at its
 * own call site, and this file states nothing but the variant.
 */
@Composable
internal fun InspectorButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    DesktopButton(
        label = label,
        onClick = onClick,
        variant = if (primary) DesktopButtonVariant.Primary else DesktopButtonVariant.Secondary,
        enabled = enabled,
    )
}
