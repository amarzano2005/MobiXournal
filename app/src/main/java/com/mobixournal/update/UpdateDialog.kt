package com.mobixournal.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.BuildConfig
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.rememberClosingArgument

/**
 * The tablet's **update available** popup: the newer build, its own state, and the one answer that state
 * offers — *Update now*, *Cancel*, *Try again* or *Install* (`docs/updates.md`, *The popup and its states*).
 *
 * It is a **floating surface**, the app's one material for anything shown over the page
 * (`docs/interface-contract.md`): `FloatingDialog` gives it the glass fill, the family's rim and the
 * floating corner, and [DialogPresence] holds the window while the closing spring plays, so the card
 * arrives *and* leaves like every menu around it. [ColorContractGuardTest] fails the build if this one goes
 * back to a plain Material card.
 *
 * The card is **one primary at a time** and it says one thing at a time: the phase picks the action
 * ([UpdateAction]) and the label comes from it, the download is a real progress bar with the bytes it knows
 * (never a sentence and a disabled button saying the same fact twice), and while it runs the card cannot be
 * dismissed — an outside tap or Back does nothing, because a download that continues with no surface is a
 * download the reader cannot see, and Cancel is right there. **Skip this version** puts the build away for
 * good: the launch check remembers it and stops raising this card.
 */
@Composable
fun UpdateAvailableDialog() {
    DialogPresence(AppUpdates.dialog) {
        // Read through the closing value: the flag clears first, and the card still has to draw the release
        // it was opened with for the ~130 ms it takes to leave.
        val release = rememberClosingArgument(AppUpdates.release) ?: return@DialogPresence
        UpdateCard(release)
    }
}

/** The card itself: the build, its phase, and the answers that phase has. */
@Composable
private fun UpdateCard(release: UpdateRelease) {
    val context = LocalContext.current
    val copy = updateCardFor(
        phase = AppUpdates.phase,
        version = release.version,
        current = BuildConfig.VERSION_NAME,
        failure = AppUpdates.failure,
        downloaded = AppUpdates.downloadedFile != null,
        skippedVersion = AppUpdates.skippedVersion,
        platform = UpdatePlatform.ANDROID,
        canSkip = true,
    )
    // The phase chose the action, so the phase *is* the action: a download is the one state with no
    // way out of it but Cancel, and it must not be dismissed.
    val downloading = copy.primary == UpdateAction.CANCEL
    // Read once: `body` is another module's property, so it cannot be smart-cast where it is printed.
    val body = copy.body
    val secondary = copy.secondary
    val dismiss: (@Composable () -> Unit)? = if (secondary == null) {
        null
    } else {
        { TextButton(onClick = { AppUpdates.remindLater() }) { Text(secondary) } }
    }

    FloatingDialog(
        // The offer can be put off; a download in flight cannot be hidden, only cancelled.
        onDismissRequest = { if (!downloading) AppUpdates.remindLater() },
        modifier = Modifier.widthIn(max = 420.dp),
        properties = DialogProperties(
            dismissOnBackPress = !downloading,
            dismissOnClickOutside = !downloading,
        ),
        title = { Text(copy.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(copy.subtitle, style = MaterialTheme.typography.bodyMedium)
                if (downloading) {
                    UpdateProgress()
                } else if (!body.isNullOrBlank()) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                // The release's own page, read where it was written: the card never summarises the notes.
                copy.notes?.let { label ->
                    TextButton(
                        onClick = { openNotes(context, release) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
                // The quietest answer, and the only one that is permanent: it is a step below the link,
                // because a reader who wants the offer never has to find it.
                copy.skip?.let { label ->
                    TextButton(
                        onClick = { AppUpdates.skipThisVersion(context) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (copy.primary == UpdateAction.CANCEL) {
                TextButton(onClick = { perform(copy.primary, context) }) { Text(copy.primary.label) }
            } else {
                Button(onClick = { perform(copy.primary, context) }) { Text(copy.primary.label) }
            }
        },
        dismissButton = dismiss,
    )
}

/** The bar and its readout: determinate with a percentage and the bytes, indeterminate without a length. */
@Composable
private fun UpdateProgress() {
    val fraction = AppUpdates.progress.coerceIn(0f, 1f)
    val total = AppUpdates.totalBytes
    val known = total > 0L
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val color = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        if (known) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .progressSemantics(fraction),
                color = color,
                trackColor = track,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = track,
            )
        }
        Text(
            text = updateProgressLine(known, fraction, AppUpdates.receivedBytes, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What the card's answer does; the phase chose the action, so the label and the call cannot disagree. */
private fun perform(action: UpdateAction, context: Context) {
    when (action) {
        UpdateAction.UPDATE_NOW, UpdateAction.RETRY -> AppUpdates.updateNow(context)
        UpdateAction.INSTALL -> AppUpdates.installNow(context)
        UpdateAction.CANCEL -> AppUpdates.cancelDownload()
    }
}

/** The release's page in the reader's browser; a host with none is a no-op, not a crash. */
private fun openNotes(context: Context, release: UpdateRelease) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.notesUrl)))
    }
}
