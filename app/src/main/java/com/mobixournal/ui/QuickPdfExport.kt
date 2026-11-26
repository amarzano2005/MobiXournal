package com.mobixournal.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.ui.theme.DocumentRed
import com.mobixournal.ui.theme.LocalChromeLayoutPolicy

/** The overflow menu retains PDF export on compact windows; the inline shortcut stays naked on wide headers. */
@Composable
internal fun QuickPdfExport(ui: EditorUiState, pane: PaneState) {
    if (!LocalChromeLayoutPolicy.current.showInlinePdfExport) return
    IconButton(
        onClick = { ui.exportPdfOpen = true },
        enabled = !pane.isReadOnly,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            ChromeGlyph.PDF.imageVector(),
            contentDescription = "Export PDF",
            tint = if (!pane.isReadOnly) DocumentRed else DocumentRed.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
    }
}
