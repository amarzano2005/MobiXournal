package com.mobixournal.ui

import androidx.compose.runtime.Composable
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.rememberClosingArgument

/**
 * The **Manage pages** card's host: the floating flag on [EditorUiState.pageRange], the card itself,
 * and the two commands it commits.
 *
 * Its own file rather than four screens' worth of `if` inside the pane overlay: the card is a question
 * about the page *list*, and the pane only has to say the flag is up. `DialogPresence` keeps it composed
 * while its spring plays out, so Move, Delete and Done close it on the same animation every other
 * surface here leaves on.
 *
 * Both commands go through the canvas's `PageCommands`, so a typed range is **one** undo step — the same
 * `PageOps` rule the window's panel and the lateral page list use.
 */
@Composable
internal fun PageRangeHost(ui: EditorUiState, surface: DrawingSurfaceView?, pageCount: Int) {
    DialogPresence(ui.pageRange != null) {
        // Held while the card closes: the flag clears on the answer, and the window is still on screen.
        val request = rememberClosingArgument(ui.pageRange) ?: return@DialogPresence
        PageRangeDialog(
            request = request,
            pageCount = pageCount,
            onEdit = { ui.pageRange = it },
            onMove = { first, last, target ->
                surface?.pageCommands?.movePages((first..last).toSet(), target)
                ui.pageRange = null
            },
            onDelete = { first, last ->
                surface?.pageCommands?.removePages((first..last).toSet())
                ui.pageRange = null
            },
            onDismiss = { ui.pageRange = null },
        )
    }
}
