package com.mobixournal.desktop.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.tools.DesktopButton
import com.mobixournal.desktop.tools.DesktopButtonVariant
import com.mobixournal.desktop.tools.DesktopDialogFrame
import com.mobixournal.desktop.tools.DialogWidth
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.glassSpring
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.rememberClosingValue
import com.mobixournal.update.UpdateRelease
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The window's **update available** popup, over the page it arrived while the reader was using.
 *
 * A modal like every other question in this window ([DesktopDialogFrame]): the scrim, the opaque popup
 * surface, English copy, and the two answers under it — **Remind me later** closes the card and leaves the
 * offer on the About page, **Update now** downloads the MSI and starts Windows' installer. The link to what
 * changed is a **Ghost** command rather than a bare label, because it opens the release's own page and a
 * command that is not a button reads as decoration.
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

/**
 * The **bottom pill**: the answer to *Check for updates* when there is no new version to offer.
 *
 * The About row's note is the panel's hover help rather than a line of its own, so "this is the latest
 * version" had nowhere to appear and the command looked dead. This is the tablet's notification in the
 * window's material (`docs/updates.md`): one quiet sentence over the page, gone after a few seconds.
 */
@Composable
internal fun BoxScope.DesktopUpdateNotice() {
    val notice = DesktopUpdates.notice ?: return
    LaunchedEffect(notice) {
        delay(4_000)
        DesktopUpdates.clearNotice()
    }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 36.dp)
            .glassSpring()
            .glassSurface()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(notice, color = DesktopTheme.ink, style = DesktopTheme.body)
    }
}

/** The card itself: the newer version, its state, and the two answers. */
@Composable
private fun UpdateDialog(release: UpdateRelease) {
    val downloading = DesktopUpdates.phase == DesktopUpdatePhase.DOWNLOADING
    DesktopDialogFrame(
        title = "Update available",
        width = DialogWidth.Standard,
        subtitle = "MobiXournal ${release.version} is available. This window is $DESKTOP_VERSION.",
        onDismiss = { DesktopUpdates.dismiss() },
        footer = {
            PanelButton(
                label = "Remind me later",
                primary = false,
                enabled = !downloading,
                onClick = { DesktopUpdates.dismiss() },
            )
            PanelButton(
                label = if (downloading) "Downloading…" else "Update now",
                primary = true,
                enabled = !downloading,
                onClick = { DesktopUpdates.updateNow() },
            )
        },
    ) {
        val status = DesktopUpdates.message
        if (downloading) {
            Text(
                "Downloading ${(DesktopUpdates.progress * 100).roundToInt()}%",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.body,
            )
        } else if (!status.isNullOrBlank()) {
            Text(status, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        }
        Spacer(Modifier.height(6.dp))
        DesktopButton(
            label = "See what changed",
            onClick = { openPage(release.notesUrl) },
            variant = DesktopButtonVariant.Ghost,
        )
    }
}
