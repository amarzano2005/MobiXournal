package com.mobixournal.desktop.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.tools.DesktopButton
import com.mobixournal.desktop.tools.DesktopButtonVariant
import com.mobixournal.desktop.tools.DesktopDialogFrame
import com.mobixournal.desktop.tools.DialogWidth
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.glassSpring
import com.mobixournal.desktop.tools.rememberClosingValue
import com.mobixournal.update.UpdateAction
import com.mobixournal.update.UpdatePlatform
import com.mobixournal.update.UpdateRelease
import com.mobixournal.update.updateCardFor
import com.mobixournal.update.updateProgressLine

/**
 * The window's **update available** popup, over the page it arrived while the reader was using.
 *
 * The words and the action each phase offers come from the shared card (`mobix-core`'s `UpdateCardCopy`),
 * the same ones the tablet wears, so the two shells cannot say two things about one update. This file only
 * puts them in the window's material: a modal like every other question ([DesktopDialogFrame], the scrim,
 * the opaque popup surface), the notes as a **Ghost** command because they open the release's own page, and
 * the answers as [PanelButton]s — the commit filled, **Cancel** an ordinary answer while a transfer runs.
 *
 * A download is drawn as a **bar and a number**: determinate with the bytes the server declared, sliding
 * without a percentage when it declared no length. It cannot be walked away from: the frame's scrim is the
 * blocker, and the only answer on the card is **Cancel**, which stops the transfer and puts the offer back.
 * A finished download keeps its MSI, so **Install** asks Windows' installer again instead of downloading it
 * twice, and a failure says so and offers **Try again**. **Skip this version** is the one answer that
 * outlives the start: the build goes into `desktop.properties` and the launch check stops raising it.
 *
 * It is hosted by the shell rather than by Settings (`docs/updates.md`): the launch check runs whether or
 * not the settings surface is open, and a popup that needed the panel would be an offer the reader only
 * sees if they happen to look for it.
 *
 * The gate is [GlassPresence], not an early return: the release is held past the flag so the card can draw
 * itself back on the family's spring instead of being torn down between two frames.
 */
@Composable
internal fun BoxScope.DesktopUpdatesHost() {
    val release = rememberClosingValue(DesktopUpdates.release)
    GlassPresence(visible = DesktopUpdates.dialog) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (release != null) UpdateDialog(release)
        }
    }
}

/** The card itself: the build, its phase, and the answers that phase has. */
@Composable
private fun UpdateDialog(release: UpdateRelease) {
    val copy = updateCardFor(
        phase = DesktopUpdates.phase,
        version = release.version,
        current = DESKTOP_VERSION,
        failure = DesktopUpdates.failure,
        downloaded = DesktopUpdates.downloadedFile != null,
        skippedVersion = DesktopUpdates.skippedVersion,
        platform = UpdatePlatform.DESKTOP,
        canSkip = true,
    )
    // The phase chose the action, so the phase *is* the action: a download is the one state with no
    // answer but Cancel, and Cancel is not this card's commit.
    val downloading = copy.primary == UpdateAction.CANCEL
    DesktopDialogFrame(
        title = copy.title,
        width = DialogWidth.Standard,
        subtitle = copy.subtitle,
        onDismiss = { if (!downloading) DesktopUpdates.dismiss() },
        footer = {
            copy.secondary?.let { label ->
                PanelButton(
                    label = label,
                    primary = false,
                    enabled = true,
                    onClick = { DesktopUpdates.dismiss() },
                )
            }
            PanelButton(
                label = copy.primary.label,
                primary = !downloading,
                enabled = true,
                onClick = { perform(copy.primary) },
            )
        },
    ) {
        if (downloading) {
            UpdateProgress()
        } else {
            copy.body?.takeIf { it.isNotBlank() }?.let { sentence ->
                Text(sentence, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
                Spacer(Modifier.height(8.dp))
            }
        }
        copy.notes?.let { label ->
            DesktopButton(
                label = label,
                onClick = { openPage(release.notesUrl) },
                variant = DesktopButtonVariant.Ghost,
            )
        }
        // The quietest answer, and the only one that is permanent: the launch check keeps it in
        // `desktop.properties` and stops raising this build, while About still reopens the offer.
        copy.skip?.let { label ->
            Spacer(Modifier.height(6.dp))
            DesktopButton(
                label = label,
                onClick = { DesktopUpdates.skipThisVersion() },
                variant = DesktopButtonVariant.Ghost,
            )
        }
    }
}

/**
 * The bar and its readout: determinate with a percentage and the bytes, indeterminate without a length.
 *
 * A response with no `Content-Length` has no honest percentage, so the bar slides a segment instead of
 * sitting at zero and the line says only that it is downloading.
 */
@Composable
private fun UpdateProgress() {
    val fraction = DesktopUpdates.progress.coerceIn(0f, 1f)
    val total = DesktopUpdates.totalBytes
    val known = total > 0L
    val shift by rememberInfiniteTransition(label = "download").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "shift",
    )
    val accent = DesktopTheme.accent
    val track = DesktopTheme.panelField
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(DesktopTheme.controlShape)
                .background(track)
                .drawBehind {
                    val segment = if (known) size.width * fraction else size.width * 0.3f
                    val left = if (known) 0f else (size.width + segment) * shift - segment
                    drawRect(color = accent, topLeft = Offset(left, 0f), size = Size(segment, size.height))
                },
        )
        Text(
            text = updateProgressLine(known, fraction, DesktopUpdates.receivedBytes, total),
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
        )
    }
}

/** What the card's answer does; the phase chose the action, so the label and the call cannot disagree. */
private fun perform(action: UpdateAction) {
    when (action) {
        UpdateAction.UPDATE_NOW, UpdateAction.RETRY -> DesktopUpdates.updateNow()
        UpdateAction.INSTALL -> DesktopUpdates.installNow()
        UpdateAction.CANCEL -> DesktopUpdates.cancelDownload()
    }
}
