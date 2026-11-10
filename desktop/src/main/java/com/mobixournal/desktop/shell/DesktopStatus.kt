package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.StripPill
import com.mobixournal.desktop.tools.bandSurface
import com.mobixournal.desktop.ink.DesktopTablet
import com.mobixournal.desktop.ink.FrameMeter
import com.mobixournal.desktop.ink.PenSource
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopAudioSession
import com.mobixournal.desktop.io.DesktopExportNotice
import com.mobixournal.desktop.io.DesktopPdfTextNotice
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.sheet.pageLabel
import com.mobixournal.desktop.sheet.zoomLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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

/**
 * The search's count on the status band, beside the page counter: both answer "where am I", and a reader who
 * pressed Enter wants to know how many matches are left without looking back up at the field.
 *
 * It mirrors the field's state instead of recomputing it: a query the walk has not answered yet reads as
 * *Searching…* here too, because `0 / 0 found` would be a count of matches that do not exist
 * (`DesktopTab.isSearchPending`).
 */
@Composable
private fun SearchCount(tab: DesktopTab) {
    if (tab.searchQuery.isBlank()) return
    Spacer(Modifier.width(10.dp))
    if (tab.isSearchPending) {
        Text("Searching…", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    } else {
        Text(
            "${tab.search.position} / ${tab.search.total} found",
            color = DesktopTheme.accent,
            style = DesktopTheme.numeric,
        )
    }
}

/**
 * What the band says with nothing on screen: the file an open is waiting for, or that there is nothing.
 *
 * Its own composable rather than three lines inside the bar: the bar is at its function budget, and this is
 * the one sentence on it that is about *waiting* instead of about the document.
 */
@Composable
private fun WaitingSentence(opening: String?) {
    Text(
        text = opening?.let { "opening $it…" } ?: "no document",
        color = if (opening != null) DesktopTheme.inkSecondary else DesktopTheme.inkMuted,
        style = DesktopTheme.label,
    )
}

/** The bottom bar: page, zoom, container and how far through the document the page is. */
@Composable
internal fun StatusBar(
    tab: DesktopTab?,
    tablet: DesktopTablet? = null,
    frameTime: Boolean = true,
    /** The window's audio, whose take indicator and one-sentence report live on this band. */
    audio: DesktopAudioSession? = null,
    /** A command that was refused, in one sentence — a page that belongs to another PDF, today. */
    refusal: String? = null,
    /**
     * The file an open is reading, by its name, or `null`.
     *
     * With nothing on screen the band says `no document`, which is true and useless while a read is in
     * flight: a 40-page notebook stops the frame loop for long enough that the click looks ignored, so the
     * band names what it is waiting for instead.
     */
    opening: String? = null,
    onCommand: (ShellCommand) -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.statusHeight)
            .bandSurface()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        audio?.let { session -> AudioStatus(session) }
        if (tab == null) {
            WaitingSentence(opening)
            return@Row
        }
        val zoom = tab.viewport.zoom
        // The label names the fit that is *in use*: `AUTO` is a question (is this page a sheet or a
        // strip?), and a status bar that answered it differently from the canvas would be lying about
        // what its own zoom readout means. The page is already in the reader's cache either way.
        val page = remember(tab, tab.page) { runCatching { tab.document.page(tab.page) }.getOrNull() }
        val fit = page?.let { tab.fitFor(it.width.toFloat(), it.height.toFloat()) } ?: tab.fit
        BandReading(pageLabel(tab.page, tab.pageCount), "The page in view", "PageDown / PageUp", numeric = true)
        SearchCount(tab)
        // A document the gate would not let this build write says so on the band, with the whole sentence
        // under the pointer: drawing, Save and Save As are off, and this is the answer that turned them off.
        tab.document.gateNote?.let { note ->
            BandGap()
            BandReading(if (tab.document.readOnly) "read-only" else "kept as written", note)
        }
        // A refused command says what happened and what to do, and sits here until the next command
        // replaces it (`docs/ui-modernization.md` — the error-copy rule).
        refusal?.let { sentence ->
            BandGap()
            Text(sentence, color = DesktopTheme.documentRed, style = DesktopTheme.label)
            return@Row
        }
        BandGap()
        // The band's one door: the percentage is also the way back to 100 % (`Ctrl+1`).
        BandDoor(zoomLabel(zoom, tab.widthFitZoom), "Back to 100 %", "Ctrl+1") {
            onCommand(ShellCommand.FIT_WIDTH)
        }
        BandGap()
        BandReading(fitLabel(fit), fitTip(fit))
        BandGap()
        PenReading(tablet, frameTime)
        Spacer(Modifier.weight(1f))
        // The PDF text slot's own sentence — `Copied 4 words`, or the reason it could select nothing — in
        // the accent, because it is news about what the press just did (`DesktopPdfTextNotice`).
        DesktopPdfTextNotice.text?.let { message ->
            Text(message, color = DesktopTheme.accent, style = DesktopTheme.label, maxLines = 1)
            Spacer(Modifier.width(14.dp))
        }
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

/** The gap two readings keep on the band: closer than a card's, because a band is one line of them. */
@Composable
private fun BandGap() = Spacer(Modifier.width(10.dp))

/**
 * One **reading** of the band — a number or a word to compare — with the sentence that explains it under the
 * pointer, in the window's own hover pill ([StripPill]).
 *
 * The band speaks in short words and keeps its jargon in the tip: `mouse` is what the pen stream is, and
 * `Windows Ink · 120 Hz · 3.2 ms` says nothing until the pointer asks. The hover answer is the window's own —
 * the ink steps up and the reading takes the hover slot — so the band is a band of the same controls as the
 * strips, one material lighter.
 */
@Composable
private fun BandReading(text: String, tip: String, accent: String = "", numeric: Boolean = false) {
    val pointer = remember { MutableInteractionSource() }
    val hovered by pointer.collectIsHoveredAsState()
    Box {
        Text(
            text = text,
            color = if (hovered) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = if (numeric) DesktopTheme.numeric else DesktopTheme.label,
            modifier = Modifier
                .clip(BAND_READING_SHAPE)
                .background(if (hovered) DesktopTheme.hoverSlot else Color.Transparent)
                .hoverable(pointer)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        if (hovered) BandTip(tip, accent)
    }
}

/**
 * The band's one **door**: a reading that is also a command — the zoom percentage, back to 100 %.
 *
 * It carries the window's three answers to the pointer (the accent at rest, the hover slot under it, the plate
 * while pressed) and no ripple: the band is 24 dp tall, and a ripple in it would read as a fault.
 */
@Composable
private fun BandDoor(text: String, tip: String, accent: String, onCommand: () -> Unit) {
    val pointer = remember { MutableInteractionSource() }
    val hovered by pointer.collectIsHoveredAsState()
    val pressed by pointer.collectIsPressedAsState()
    Box {
        Text(
            text = text,
            color = DesktopTheme.accent,
            style = DesktopTheme.numeric,
            modifier = Modifier
                .clip(BAND_READING_SHAPE)
                .background(
                    when {
                        pressed -> DesktopTheme.activeSlot
                        hovered -> DesktopTheme.hoverSlot
                        else -> Color.Transparent
                    },
                )
                .hoverable(pointer)
                .clickable(interactionSource = pointer, indication = null) { onCommand() }
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        if (hovered) BandTip(tip, accent)
    }
}

/** The tip itself: the window's hover pill, lifted clear of the band it belongs to. */
@Composable
private fun BandTip(label: String, accent: String) {
    val lift = with(LocalDensity.current) { BAND_TIP_LIFT.roundToPx() }
    StripPill(label = label, accent = accent, offset = IntOffset(0, -lift))
}

/**
 * The window's own two facts in one reading: which stream the pen is, and — while the reader keeps the frame
 * time on — the screen's rate against what the window reaches. The words behind them are [penTip].
 */
@Composable
private fun PenReading(tablet: DesktopTablet?, frameTime: Boolean) {
    val source = tablet?.source ?: PenSource.MOUSE
    val hz = remember { displayRefreshHz() }
    val paint = if (frameTime) FrameMeter.averageMs.takeIf { it > 0.0 } else null
    BandReading(
        text = listOfNotNull(source.label, bandMetrics(hz, paint)).joinToString(" · "),
        tip = penTip(source, tablet?.dial == true, hz, paint),
    )
}

/** `free zoom`, `fit width`, `fit page`: the mode the canvas is in, as the band's own three words. */
internal fun fitLabel(fit: FitMode): String = when (fit) {
    FitMode.NONE -> "free zoom"
    FitMode.WIDTH -> "fit width"
    FitMode.PAGE, FitMode.AUTO -> "fit page"
}

/** What that mode means, in one sentence — the band's tip rather than a second label on it. */
internal fun fitTip(fit: FitMode): String = when (fit) {
    FitMode.NONE -> "Free zoom — the scale you set last"
    FitMode.WIDTH -> "The page's width fills the window"
    FitMode.PAGE, FitMode.AUTO -> "The whole page is in view"
}

/**
 * The band's compact pair — `120 Hz · 3.2 ms` — the rate alone, or nothing when the host cannot name one: the
 * numbers a reader compares at a glance, with the words kept for the tip ([penTip]).
 */
internal fun bandMetrics(hz: Int?, paintMs: Double?): String? {
    if (hz == null || hz <= 0) return null
    if (paintMs == null || paintMs <= 0.0) return "$hz Hz"
    return String.format(Locale.ROOT, "%d Hz · %.1f ms", hz, paintMs)
}

/**
 * The sentence behind [PenReading]: the pen's own claim, and — when the host can name one — the display's
 * ceiling with what the window reaches of it. A machine that cannot say its rate says nothing about it rather
 * than `refresh unknown`: a tip is a sentence about the window, not a field of one.
 */
internal fun penTip(source: PenSource, dial: Boolean, hz: Int?, paintMs: Double?): String = buildString {
    append(penLabel(source, dial))
    if (hz != null && hz > 0) {
        append(" · ").append(refreshLabel(hz))
        frameLabel(paintMs ?: 0.0, true)?.let { append(" · ").append(it) }
    }
}

/** The hollow a reading's hover sits in, and how far above the band its tip stands. */
private val BAND_READING_SHAPE = RoundedCornerShape(4.dp)
private val BAND_TIP_LIFT = 26.dp

/**
 * The recording indicator: a record dot in the document red, the take's own `mm:ss`, and a thin level
 * bar — or, when nothing is being recorded, the one sentence the audio had to say (a recording this
 * machine does not have, a microphone that would not open).
 *
 * It is here rather than only on the pill because a take can start while the pill is scrolled out of
 * sight on a narrow window, and "is the microphone on" must never be a guess.
 */
@Composable
private fun AudioStatus(session: DesktopAudioSession) {
    val message = session.message
    if (session.recording) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(DesktopTheme.documentRed))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (session.recordingPaused) "recording paused" else "recording ${formatTime(session.recordedMs)}",
            color = DesktopTheme.documentRed,
            style = DesktopTheme.numeric,
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DesktopTheme.activeSlot)) {
            Box(
                Modifier
                    .width((40f * session.level.coerceIn(0f, 1f)).dp)
                    .height(4.dp)
                    .background(DesktopTheme.accent),
            )
        }
        Spacer(Modifier.width(14.dp))
    } else if (message != null) {
        Text(message, color = DesktopTheme.accent, style = DesktopTheme.label, maxLines = 1)
        Spacer(Modifier.width(14.dp))
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
