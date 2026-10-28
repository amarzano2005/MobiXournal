package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * One capsule button of a **floating surface**: the accent for the surface's own commit, the steel for
 * everything beside it, and a dimmed, hit-less body while it is disabled.
 *
 * It is shared by every dialog the window draws — the colour panel's Set and Cancel, the close
 * confirmation's three answers, the inspector lane's Place and Done — so the family's smallest surface
 * has one recipe instead of one per panel (`docs/interface-contract.md` §1). A disabled button keeps its
 * ink and loses its hit area: the pointer handler is only installed when it is enabled.
 *
 * The tap goes through a holder for the reason the lane's controls do (`InspectorControls.kt`): a
 * `pointerInput` keeps the lambda it was built with, so a **Place** or an **Add equation** built against
 * one element used to answer with that element long after the card had moved on.
 */
@Composable
internal fun PanelButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    /**
     * The **document red**, for the answer that takes something away. A destructive control keeps the same
     * shape and the same five states; only its ink says what it does (`docs/interface-contract.md`).
     */
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val answer = rememberUpdatedState(onClick)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .background(
                when {
                    primary -> DesktopTheme.accent
                    destructive -> DesktopTheme.documentRed.copy(alpha = 0.16f)
                    else -> DesktopTheme.activeSlot
                },
                DesktopTheme.capsuleShape,
            )
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { answer.value() }
            }
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = when {
                primary -> DesktopTheme.desk
                destructive -> DesktopTheme.documentRed
                else -> DesktopTheme.ink
            },
            style = DesktopTheme.body,
        )
    }
}
