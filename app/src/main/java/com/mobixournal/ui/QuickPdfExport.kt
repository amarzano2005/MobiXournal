package com.mobixournal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.ui.theme.DocumentRed
import com.mobixournal.ui.theme.LocalChromeLayoutPolicy
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/** The overflow menu retains PDF export on compact windows; the duplicate shortcut yields to navigation. */
@Composable
internal fun QuickPdfExport(ui: EditorUiState, pane: PaneState) {
    if (!LocalChromeLayoutPolicy.current.showInlinePdfExport) return
    Surface(
        shape = CircleShape, color = rememberGlassSurfaceColor(), tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.padding(horizontal = 2.dp).floatingRim(18.dp),
    ) {
        IconButton(
            onClick = { ui.exportPdfOpen = true }, enabled = !pane.isReadOnly, modifier = Modifier.size(36.dp),
        ) {
            Icon(
                ChromeGlyph.PDF.imageVector(), "Export PDF",
                tint = if (!pane.isReadOnly) DocumentRed else DocumentRed.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
