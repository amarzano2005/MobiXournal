package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * One capsule button of a **floating surface**: the accent for the surface's own commit, the steel for
 * everything beside it, and a dimmed, hit-less body while it is disabled.
 *
 * It is shared by every dialog the window draws — the colour panel's Set and Cancel, the close
 * confirmation's three answers — so the family's smallest surface has one recipe instead of one per
 * panel (`docs/interface-contract.md` §1). A disabled button keeps its ink and loses its hit area: the
 * pointer handler is only installed when it is enabled.
 */
@Composable
internal fun PanelButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .background(
                if (primary) DesktopTheme.accent else DesktopTheme.activeSlot,
                DesktopTheme.capsuleShape,
            )
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (primary) DesktopTheme.desk else DesktopTheme.ink, style = DesktopTheme.body)
    }
}
