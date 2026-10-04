package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The touch-target square every rail slot occupies, tool buttons and popup buttons alike. Icons are
 * 24.dp, so the extra room is what keeps the rail from reading as a squished run of glyphs.
 */
internal val ToolbarButtonSize = 48.dp

/**
 * A tiny chevron in the bottom-right corner of a **tool-group slot** whose picker offers a choice —
 * a tap-again opens a menu over several tools. The *panel* pop-ups (Colour, Zoom, Layers, …) do
 * **not** wear it: they are a button that opens its own panel, not a submenu of alternatives, so the
 * chevron is reserved for "this slot can be re-faced".
 *
 * Call it inside the button's own [Box] (it aligns itself to the corner).
 */
@Composable
internal fun BoxScope.MenuChevron(tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 6.dp, bottom = 4.dp)
            .size(12.dp),
    )
}

/**
 * A small non-clickable section heading inside a dropdown menu.
 */
@Composable
internal fun MenuHeading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A standard toolbar popup button with an icon face. A click toggles its [DropdownMenu]: the first
 * click opens it, and clicking again closes it (an open menu also dismisses on any outside touch).
 *
 * @param icon The icon to show on the button face.
 * @param contentDescription Content description for the icon.
 * @param heading Optional heading text shown at the top of the menu.
 * @param active Whether the button is in an active state (tints the icon primary and shows background).
 * @param tint Optional explicit tint for the icon; defaults to [LocalContentColor.current].
 * @param onLongClick Optional long-click handler.
 * @param content The menu content, called with a `dismiss` lambda to close the menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ToolbarPopupButton(
    icon: ImageVector,
    contentDescription: String,
    heading: String? = null,
    active: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val iconTint = tint ?: if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current
    Box {
        Box(
            modifier = Modifier
                .size(ToolbarButtonSize)
                .clip(CircleShape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    onClick = { open = !open },
                    onLongClick = onLongClick,
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (heading != null) MenuHeading(heading)
            content { open = false }
        }
    }
}

/**
 * A toolbar popup button with a custom composable face (e.g. TextButton for Zoom, TipDot for Size).
 *
 * @param face The custom composable to use as the button face, called with a `toggle` lambda it must
 *   wire to its own click handler — that lambda is what opens or closes the menu.
 * @param heading Optional heading text shown at the top of the menu.
 * @param content The menu content, called with a `dismiss` lambda to close the menu.
 */
@Composable
internal fun ToolbarPopupButton(
    face: @Composable (open: () -> Unit) -> Unit,
    heading: String? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        face { open = !open }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (heading != null) MenuHeading(heading)
            content { open = false }
        }
    }
}
