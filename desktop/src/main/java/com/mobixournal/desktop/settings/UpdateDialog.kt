package com.mobixournal.desktop.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.glassSpring
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.update.UpdateRelease
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The window's **update available** popup, over the page it arrived while the reader was using.
 *
 * A floating surface like every question in this window (`docs/interface-contract.md`): the glass, the
 * family's corner, English copy, and the two answers under it — **Remind me later** closes the card and
 * leaves the offer on the About page, **Update now** downloads the MSI and starts Windows' installer. The
 * link to what changed is the release's own page, so the reader can read before deciding.
 *
 * It is hosted by the shell rather than by Settings (`docs/updates.md`): the launch check runs whether or
 * not the settings surface is open, and a popup that needed the panel would be an offer the reader only
 * sees if they happen to look for it.
 */
@Composable
internal fun BoxScope.DesktopUpdatesHost() {
    if (!DesktopUpdates.dialog) return
    val release = DesktopUpdates.release ?: return
    GlassPresence(visible = true) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            UpdateDialog(release)
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
    Column(Modifier.width(430.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text("Update available", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "MobiXournal ${release.version} is available. This window is $DESKTOP_VERSION.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        val status = DesktopUpdates.message
        if (downloading) {
            Text(
                "Downloading ${(DesktopUpdates.progress * 100).roundToInt()}%",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.body,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (!status.isNullOrBlank()) {
            Text(
                status,
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.body,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        SettingsTextAction(
            "See what changed",
            onClick = { openPage(release.notesUrl) },
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(
                label = "Remind me later",
                primary = false,
                enabled = !downloading,
                onClick = { DesktopUpdates.dismiss() },
            )
            Spacer(Modifier.width(8.dp))
            PanelButton(
                label = if (downloading) "Downloading…" else "Update now",
                primary = true,
                enabled = !downloading,
                onClick = { DesktopUpdates.updateNow() },
            )
        }
    }
}
