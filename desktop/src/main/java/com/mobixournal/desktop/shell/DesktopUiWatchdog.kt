package com.mobixournal.desktop.shell
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.number

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * **The window's hang detector**: a beat from the thread that draws, and a stack dump of *everything* when
 * the beat stops.
 *
 * A freeze cannot be blamed from the outside. "The interface hangs" is the same sentence whether the thread
 * that paints is blocked on a monitor, spinning in a loop of its own, or waiting on a lock a background lane
 * never released — and by the time a reader has described it, the moment is gone. So the window answers the
 * question itself: [beat] is called from a coroutine on the UI dispatcher (the AWT event thread Compose
 * draws and scrolls on), and a stall longer than [STALL_MS] writes one file with every thread's state, its
 * stack, and — for a thread that is blocked — **the monitor it is waiting for and the thread holding it**.
 * That last pair is the whole point: a dump that names a lock and its owner is a diagnosis, while a dump of
 * one stack is a guess.
 *
 * It is on in every run, not behind `--diagnose` ([DesktopDiagnostics]): the freeze this exists for happens
 * to someone using the window normally, and asking them to relaunch with a flag is asking them to lose the
 * occurrence. The cost of watching is one volatile write every [BEAT_MS] and no file at all until something
 * actually stalls.
 *
 * The dump lands beside the working directory as `mobix-ui-hang-<timestamp>.txt` — the same place
 * `mobix-diagnose.log` goes, so one folder holds both halves of a report — and falls back to the temp
 * directory when the window was started somewhere it may not write. The path is printed to `stderr` as well,
 * so a run from a console says where to look.
 */
internal object DesktopUiWatchdog {

    /** How long the UI thread may go without a beat before it counts as stalled. */
    internal const val STALL_MS = 4_000L

    /** How often the beat is written. Well under [STALL_MS], and cheap enough to be free. */
    internal const val BEAT_MS = 200L

    /** The wait between dumps of one continuing stall: a hang that lasts a minute is one report, not fifteen. */
    internal const val REPEAT_MS = 30_000L

    /** Dumps per run. A window stuck for an hour must not fill the disk with the same stacks. */
    internal const val MAX_DUMPS = 5

    /** When the UI thread last beat. Written from the event thread, read from the watcher's. */
    private val lastBeat = AtomicLong(System.nanoTime())

    /** The thread that beats, recorded once, so the report can mark the line to read first. */
    @Volatile private var uiThread: String? = null

    @Volatile private var started = false

    /** One thread as the dump needs it: identity, state, the lock it waits for, and its stack. */
    internal class Snapshot(
        val name: String,
        val state: String,
        val lock: String?,
        val lockOwner: String?,
        val frames: List<String>,
    )

    /**
     * Start watching, once per process.
     *
     * A daemon thread, so it never holds a closing window open, and it is never stopped: the window lasts as
     * long as the process does.
     */
    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        Thread(::watch, "mobix-ui-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    /** The UI thread saying it is alive. Called from a coroutine on the window's own dispatcher. */
    fun beat() {
        uiThread = uiThread ?: Thread.currentThread().name
        lastBeat.set(System.nanoTime())
    }

    /**
     * Whether a stall of [stalledMs] should be written, given the wait since the last dump and how many were
     * written already. Pure, so the policy is a test and not a habit.
     */
    internal fun due(stalledMs: Long, sinceDumpMs: Long, dumps: Int): Boolean =
        stalledMs >= STALL_MS && dumps < MAX_DUMPS && (dumps == 0 || sinceDumpMs >= REPEAT_MS)

    /**
     * The dump's text: a header naming the stall, then every thread — the one that beats **first**, because
     * the reader's question is what *it* was doing — with its state, the monitor it waits for and who holds
     * it, then its frames.
     */
    internal fun report(stalledMs: Long, uiThread: String?, threads: List<Snapshot>, at: String): String =
        buildString {
            append("# MobiXournal desktop: the window thread stopped advancing\n")
            append("# written ").append(at).append(", stalled ")
                .append(String.format(java.util.Locale.ROOT, "%.1f", stalledMs / 1000.0)).append(" s")
                .append(" (threshold ").append(STALL_MS / 1000).append(" s)\n")
            append("# the thread that paints and scrolls: ").append(uiThread ?: "not yet seen").append("\n")
            append("# blocked-on / held-by is the pair to read: a monitor and the thread that owns it\n")
            val ordered = threads.sortedBy { it.name != uiThread }
            for (thread in ordered) {
                append("\n\"").append(thread.name).append("\" ").append(thread.state)
                if (thread.lock != null) append(" on ").append(thread.lock)
                if (thread.lockOwner != null) append(" held by \"").append(thread.lockOwner).append("\"")
                append("\n")
                for (frame in thread.frames) append("    at ").append(frame).append("\n")
            }
        }

    /** Every thread the JVM knows, as the dump needs it. Java's own bean, so locks come with their owners. */
    internal fun snapshot(): List<Snapshot> {
        val bean = ManagementFactory.getThreadMXBean()
        // Lock info and stacks both, in one walk: `getAllStackTraces` would cost a second one and would not
        // say who owns the monitor the dump is about.
        val infos: Array<ThreadInfo> = runCatching { bean.getThreadInfo(bean.allThreadIds, true, true) }
            .getOrNull() ?: return emptyList()
        return infos.filterNotNull().map { info ->
            Snapshot(
                name = info.threadName,
                state = info.threadState.name,
                lock = info.lockName ?: info.lockInfo?.className,
                lockOwner = info.lockOwnerName,
                frames = info.stackTrace.map { it.toString() },
            )
        }
    }

    /** The watcher: one sleep, one question, and a dump when the answer is a stall. */
    private fun watch() {
        var dumps = 0
        var lastDumpAt = 0L
        while (true) {
            runCatching { Thread.sleep(BEAT_MS) }
            val now = System.nanoTime()
            val stalledMs = (now - lastBeat.get()) / 1_000_000L
            val sinceDumpMs = if (lastDumpAt == 0L) Long.MAX_VALUE else (now - lastDumpAt) / 1_000_000L
            if (!due(stalledMs, sinceDumpMs, dumps)) continue
            dumps++
            lastDumpAt = now
            runCatching { write(stalledMs) }
        }
    }

    /** One file per stall window, beside the working directory — the folder `mobix-diagnose.log` uses. */
    private fun write(stalledMs: Long) {
        val written = runCatching { dumpInto(File(System.getProperty("user.dir")), stalledMs) }.getOrNull()
            ?: runCatching { dumpInto(File(System.getProperty("java.io.tmpdir")), stalledMs) }.getOrNull()
        System.err.println("MobiXournal: the window thread stalled for ${stalledMs / 1000} s; stacks in " +
            (written?.absolutePath ?: "(not written)"))
    }

    /**
     * Write one dump into [dir]: the file the reader is pointed at. Named to the second, so a stalled window's
     * files sort as they happened and none overwrites another.
     */
    internal fun dumpInto(dir: File, stalledMs: Long, now: LocalDateTime = LocalDateTime.now()): File {
        val at = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(dir, "mobix-ui-hang-$stamp.txt")
        file.writeText(report(stalledMs, uiThread, snapshot(), at))
        return file
    }
}

/**
 * The beat, as the window calls it: one composable, so the detector starts with the window and is fed from the
 * thread that draws.
 *
 * It rides `delay`, not the frame clock: a window nobody is touching produces no frames, and an idle window
 * must not be reported as a hung one.
 */
@Composable
internal fun UiWatchdogBeats() {
    LaunchedEffect(Unit) {
        DesktopUiWatchdog.start()
        while (true) {
            DesktopUiWatchdog.beat()
            delay(DesktopUiWatchdog.BEAT_MS)
        }
    }
}
