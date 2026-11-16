package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobixournal.MainActivity
import com.mobixournal.analyzePdfInBackground
import com.mobixournal.render.pdfSheetInView
import com.mobixournal.render.pdfTextAvailability
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

/**
 * **What Select text (PDF) can do on this page**, said where the reader is looking: a pill over the sheet with
 * the one sentence its state owes and — when a read broke — a **Try again**.
 *
 * It is scoped to the tool and the pane, which is what keeps it from being chrome: with another tool in hand
 * it draws nothing at all, and it reads the phase of *this* pane's file, so a document open in the second pane
 * does not explain itself on the first. A page with words says nothing either — the tool simply works, and a
 * sentence about working is noise.
 *
 * The copy and the states are `PdfTextAvailability`'s, shared with the window, so the tablet and the desktop
 * cannot drift into telling a reader two different things about the same file.
 */
@Composable
internal fun BoxScope.PdfTextReadinessNotice(ui: EditorUiState, pane: PaneState) {
    // The retired tool's own slot is gone from the rail, but `Shift+S`'s entry can still put it in hand — and a
    // long press that found no words asks for the same pill through `pdfTextEntryMissed`, so the direct entry
    // explains itself without a mode to pick first.
    if (ui.tool != EditorTool.TEXT_SELECT && !pane.pdfTextEntryMissed) return
    val surface = pane.surface ?: return
    val availability = surface.pdfTextAvailability()
    val sentence = availability.notice(pageShowsPdf = surface.pdfSheetInView() != null) ?: return
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 12.dp, start = 12.dp, end = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = rememberFloatingSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        // The pill's fill is the floating glass rather than a `colorScheme` role, so its ink is named here.
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sentence,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (availability.canRetry) {
                TextButton(
                    onClick = {
                        val file = surface.pdfSourceFile() ?: return@TextButton
                        (context as? MainActivity)?.analyzePdfInBackground(file, surface)
                    },
                ) { Text("Try again") }
            }
        }
    }
}
