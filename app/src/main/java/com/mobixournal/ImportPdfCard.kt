package com.mobixournal

import androidx.compose.runtime.Composable
import com.mobixournal.ui.ImportPdfDialog

/**
 * **The import card**: which pages of the file just read, and where they go.
 *
 * It is drawn by the activity rather than by the editor because the staged file and its page count are
 * the activity's ([pendingPdfImport]), and it is up exactly while a decision is owed.
 */
@Composable
internal fun MainActivity.PendingPdfImportCard() {
    val prompt = pendingPdfImport ?: return
    ImportPdfDialog(
        request = prompt,
        merging = surface?.hasPdfBackground() == true,
        onEdit = { edit -> pendingPdfImport = pendingPdfImport?.let(edit) },
        onConfirm = { mode -> confirmPdfImport(mode) },
        onDismiss = { cancelPdfImport() },
    )
}
