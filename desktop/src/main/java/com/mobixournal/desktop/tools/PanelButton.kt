package com.mobixournal.desktop.tools
import com.mobixournal.desktop.tools.DesktopTextAction
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme

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

/** Dialog text commands share the desktop-wide unfilled ink/underline recipe. */
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
    DesktopTextAction(
        label, onClick, modifier = modifier, enabled = enabled, style = DesktopTheme.body,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        tone = when { destructive -> DesktopTheme.documentRed; primary -> DesktopTheme.accent; else -> DesktopTheme.inkSecondary },
    )

}
