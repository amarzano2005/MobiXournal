package com.mobixournal.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ink latency, measured through the **real** pipeline.
 *
 * `--probe-tablet` answers "what does the driver send"; this answers "how long after a packet arrives
 * does its ink appear". The number the owner can feel is the gap between the nib and the end of the
 * line, and `desktop-pen-lag-behind-the-nib` turns it into a target: at 144 Hz a sample must be painted
 * within one frame (6.9 ms) of its packet, on a light page and on a heavy one.
 *
 * The probe paints a page with the same [PageCanvas] the editor uses, feeds packets through the same
 * [PenPacketStroke] and records, per sample, the packet's time and the `nanoTime` of the frame that
 * first drew it. The samples are synthetic by design on a host without the tablet: a side effect of the
 * measurement should not depend on the pen being on the glass. When run on the owner's PC with the
 * Gaomon WH851, the same window draws whatever the real driver sends for the strokes drawn by hand.
 *
 * **What it writes**: `ink-probe.csv` beside the running directory, one row per painted sample —
 * `stroke,sample,packetMs,paintMs,deltaMs` — and a one-line summary on stdout with the median and
 * 95th percentile per stroke, which is what the task's report quotes.
 */
internal class InkProbe(val file: File = File(System.getProperty("user.dir"), "ink-probe.csv")) {

    private val out = file.bufferedWriter()
    private val deltas = mutableListOf<Double>()
    private var seen = 0
    private var sampleIndex = 0
    private var stroke = 0

    /** Summary rows, printed and returned so a test can assert the measurement wrote something. */
    val summary: MutableList<String> = mutableListOf()

    init {
        out.write("stroke,sample,packetMs,paintMs,deltaMs\n")
        out.flush()
    }

    var writes: Boolean = true
        private set

    /** The frames painted so far — the canvas's own count, for the summary. */
    var frames: Long = 0
        private set

    /** A stroke boundary: the next sample starts a new line in the table. */
    fun nextStroke() {
        stroke++
        sampleIndex = 0
    }

    /**
     * Called from the paint pass with the drafts it drew and the clock **at that paint**.
     *
     * [drafts] are snapshots in paint order; a sample is new when its index is past everything already
     * recorded. The probe window draws one packet path, so summing the lists is the right count.
     */
    @Synchronized
    fun painted(drafts: List<List<InkSample>>, paintNanos: Long) {
        frames++
        val all = drafts.flatten()
        if (all.size < seen) seen = 0
        for (i in seen until all.size) {
            val sample = all[i]
            val packetNanos = sample.pointer.timeMs * 1_000_000L
            val deltaMs = (paintNanos - packetNanos) / 1_000_000.0
            val row = "$stroke,${sampleIndex++},${sample.pointer.timeMs},${paintNanos / 1_000_000L}," +
                String.format(java.util.Locale.ROOT, "%.2f", deltaMs)
            runCatching { out.write(row + "\n") }
            deltas += deltaMs
        }
        seen = all.size
    }

    /** Close the table and report what it measured; safe to call more than once. */
    @Synchronized
    fun finish(): List<String> {
        if (summary.isNotEmpty()) return summary
        runCatching {
            out.flush()
            out.close()
        }
        if (deltas.isEmpty()) {
            summary += "no samples painted"
            return summary
        }
        val sorted = deltas.sorted()
        val median = sorted[sorted.size / 2]
        val p95 = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)]
        summary += String.format(
            java.util.Locale.ROOT,
            "%d samples over %d frames — median %.2f ms, p95 %.2f ms, max %.2f ms",
            sorted.size, frames, median, p95, sorted.last(),
        )
        return summary
    }

    /** A synthetic packet source: the probe's strokes at the tablet's own 400 Hz. */
    class SyntheticPackets : PenPacketSource {
        private val listeners = mutableListOf<(PenPacket) -> Unit>()

        override fun listen(listener: (PenPacket) -> Unit): () -> Unit {
            listeners += listener
            return { listeners -= listener }
        }

        fun send(x: Float, y: Float, pressure: Float, tip: Boolean = true, atNanos: Long = System.nanoTime()) {
            val packet = PenPacket(
                x = x,
                y = y,
                pressure = pressure,
                tiltRad = 0.5f,
                azimuthRad = 1.1f,
                rotationRad = 0f,
                tip = tip,
                eraser = false,
                atNanos = atNanos,
            )
            for (listener in listeners.toList()) listener(packet)
        }
    }
}

/** The probe's window: three synthetic strokes through the editor's own canvas. */
internal fun runInkProbe(document: File?) {
    val file = document ?: lightPageFile()
    val tabs = DesktopTabs()
    val tab = tabs.open(DocumentLoader.load(file))
    tab.fit = FitMode.PAGE
    val probe = InkProbe()
    println("MobiXournal ink probe: ${file.name} -> ${probe.file.absolutePath}")
    application {
        val (probeWidth, probeHeight) = probeWindowSize()
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = probeWidth.dp, height = probeHeight.dp),
            title = "MobiXournal ink probe",
        ) {
            val packets = InkProbe.SyntheticPackets()
            LaunchedEffect(Unit) {
                delay(1200) // layout + first paint
                val strokes = listOf<(Double) -> Pair<Float, Float>>(
                    { t -> (280f + 700f * t).toFloat() to 300f },
                    { t -> (280f + 700f * t).toFloat() to (300f + 500f * t).toFloat() },
                    { t -> (300f + 600f * t).toFloat() to (600f - 260f * sin(t * PI)).toFloat() },
                )
                for (shape in strokes) {
                    probe.nextStroke()
                    // The driver's own 400 Hz, stamped when the packet is sent: a fabricated timeline
                    // would measure the probe's own arithmetic instead of the canvas's latency.
                    for (i in 0 until 240) {
                        val t = i / 239.0
                        val (x, y) = shape(t)
                        packets.send(x, y, pressure = (0.15 + 0.85 * t).toFloat())
                        delay(2)
                    }
                    packets.send(1000f, 1000f, pressure = 0f, tip = false)
                    delay(400)
                }
                delay(800)
                probe.finish().forEach { println("ink probe: $it") }
                exitApplication()
            }
            PageCanvas(
                tab = tab,
                panMode = false,
                onViewport = {},
                onFit = {},
                ink = DesktopInk(width = 4f),
                packets = packets,
                paintProbe = probe::painted,
            )
        }
    }
    probe.finish().forEach { println("ink probe: $it") }
    tab.document.close()
}

/**
 * The probe window's size in dp, `-Dmobix.probe.size=3440x1400`, defaulting to the small window.
 *
 * The size matters to the measurement and is not cosmetic: the cached page is blitted **at the window's
 * own pixel size**, so the owner's 21:9 screen asks for four times the pixels this laptop's default
 * window does. A number measured at 1100 × 900 says nothing about a 3440 × 1440 window unless the same
 * window is measured, which is what this property makes possible.
 */
private fun probeWindowSize(): Pair<Int, Int> {
    val raw = System.getProperty("mobix.probe.size") ?: return 1100 to 900
    val parts = raw.lowercase().split('x')
    val width = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1100
    val height = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 900
    return width to height
}

/** A blank A4 page written once, so the light-page run needs no fixture on disk. */
private fun lightPageFile(): File {
    val file = File.createTempFile("ink-probe-light", ".xopp").apply { deleteOnExit() }
    Xopp.save(
        Document(
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(Layer(emptyList())),
                ),
            ),
        ),
        file.outputStream(),
    )
    return file
}
