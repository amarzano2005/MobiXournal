package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import java.util.Locale

/**
 * The window's two **read-only** bands: the status bar under the canvas, and the notice that owns the
 * empty window.
 *
 * They live here rather than in `DesktopShell.kt` for the module's structural ratchet
 * (`SourceStructureTest`): both are plain functions of the state they are given, with no decisions to
 * make about it, while the shell file holds the state and the commands.
 */

/**
 * What the window's pen is, in one label: `Windows Ink` when the device's own stream is open, `mouse`
 * when it is not, and `dial` when a radial controller is bound to the window.
 *
 * A separate function because it is a **claim** and a test can read it: the window must never say
 * `Windows Ink` on the strength of a driver that reported nothing.
 */
internal fun penLabel(source: PenSource, dial: Boolean): String = buildString {
    append("pen: ").append(source.label)
    if (dial) append(" · dial")
}

/**
 * The display's own rate, and the budget one frame of it allows: `120 Hz · 8.3 ms frame`.
 *
 * The window had no way to answer "is the refresh unlocked": the only number was the watchdog's
 * `paints/s`, which is written only under `--diagnose`. A refresh rate is the ceiling the loop cannot
 * beat, so it belongs on screen beside the pen source — a capped loop or a panel left at 60 Hz then
 * reads as a number instead of as a feeling. The budget is the same figure the ink-latency target is
 * stated in (`desktop-tablet.md`), so the two can be compared at a glance.
 *
 * A host that cannot say (headless, or a display mode that reports no rate) says so in one word rather
 * than showing a zero.
 */
internal fun refreshLabel(hz: Int?): String {
    if (hz == null || hz <= 0) return "refresh unknown"
    val budget = String.format(Locale.ROOT, "%.1f", 1_000.0 / hz)
    return "$hz Hz · $budget ms frame"
}

/**
 * **How long a frame takes**, as the status bar's own short label — or null when there is nothing to say.
 *
 * [refreshLabel] is the ceiling and [averageMs] is what the window reaches: a loop painting every other
 * frame on a 120 Hz panel reads `60.0 ms paint` next to `120 Hz · 8.3 ms frame`, which is the whole point
 * of putting the two side by side. A readout the reader has switched off, or a window that has not painted
 * a second frame yet, says nothing at all rather than `0.0` — a zero would be a claim about the frame.
 */
internal fun frameLabel(averageMs: Double, enabled: Boolean = true): String? =
    if (!enabled || averageMs <= 0.0) null else String.format(Locale.ROOT, "%.1f ms paint", averageMs)

/**
 * The refresh rate of the screen this JVM draws on, or null when the host cannot name one.
 *
 * Guarded twice on purpose: a headless run has no device to ask, and a mode can report `0` — neither is
 * an error, and a status bar that threw over it would take the window with it.
 */
internal fun displayRefreshHz(): Int? {
    if (GraphicsEnvironment.isHeadless()) return null
    return runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate
    }.getOrNull()?.takeIf { it > 0 }
}

/** The bottom bar: page, zoom, container and how far through the document the page is. */
@Composable
internal fun StatusBar(tab: DesktopTab?, tablet: DesktopTablet? = null, frameTime: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.statusHeight)
            .bandSurface()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab == null) {
            Text("no document", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            return@Row
        }
        val zoom = tab.viewport.zoom
        // The label names the fit that is *in use*: `AUTO` is a question (is this page a sheet or a
        // strip?), and a status bar that answered it differently from the canvas would be lying about
        // what its own zoom readout means. The page is already in the reader's cache either way.
        val page = remember(tab, tab.page) { runCatching { tab.document.page(tab.page) }.getOrNull() }
        val fit = page?.let { tab.fitFor(it.width.toFloat(), it.height.toFloat()) } ?: tab.fit
        Text(pageLabel(tab.page, tab.pageCount), color = DesktopTheme.inkSecondary, style = DesktopTheme.numeric)
        Spacer(Modifier.width(14.dp))
        Text(zoomLabel(zoom), color = DesktopTheme.inkSecondary, style = DesktopTheme.numeric)
        Spacer(Modifier.width(14.dp))
        Text(
            text = when (fit) {
                FitMode.NONE -> "free zoom"
                FitMode.WIDTH -> "fit width"
                FitMode.PAGE, FitMode.AUTO -> "fit page"
            },
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
        )
        Spacer(Modifier.width(14.dp))
        // The pen's source, not the pen's absence: a window with no Windows Ink says `mouse`, which is
        // what the canvas is actually reading. The dial is named only while one is bound.
        Text(
            text = penLabel(tablet?.source ?: PenSource.MOUSE, tablet?.dial == true),
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
        )
        Spacer(Modifier.width(14.dp))
        // What the loop can reach, not what it did: the refresh rate is the display's, read once because
        // it changes only when the mode does (the readout is not a per-frame probe).
        val hz = remember { displayRefreshHz() }
        Text(refreshLabel(hz), color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        // And what the window reaches of that budget — the measured half of the same sentence.
        frameLabel(FrameMeter.averageMs, frameTime)?.let { painted ->
            Spacer(Modifier.width(14.dp))
            Text(painted, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        Spacer(Modifier.weight(1f))
        // The last export's own sentence — `Exported 12 pages to notes.pdf` — in the accent, because it is
        // news rather than a reading of the document. A write that worked is worth one line, not a card.
        DesktopExportNotice.text?.let { message ->
            Text(message, color = DesktopTheme.accent, style = DesktopTheme.label, maxLines = 1)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = "${tab.document.container.label} · ${tab.file.name}",
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        ProgressBar(pageProgress(tab.page, tab.pageCount))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.width(90.dp).height(3.dp).background(DesktopTheme.activeSlot)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(DesktopTheme.accent),
        )
    }
}

/** The empty window's message, or the card standing in for it: a title and a sentence that says what to do. */
@Composable
internal fun Notice(title: String, detail: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        }
    }
}
