package com.mobixournal.desktop.ink
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.EventQueue

/**
 * The window's **share of the machine**: what Windows schedules the pen's packets and the sheet's frames at.
 *
 * A tablet stroke is a stream of several hundred samples a second, and each one is a message the window
 * procedure must be running to receive — so the smoothness of writing is not a property of the drawing code
 * at all, it is how soon after the hardware speaks that thread is allowed to run. On an idle PC that is
 * immediate; with other processes busy, Windows is free to put this one behind them, and the same stroke
 * then arrives in clumps: the ink lags the hand and comes out jagged. (The owner sees the same on Xournal++,
 * for the same reason — it does nothing to ask for a share either.) Four independent levers exist, and this
 * object pulls all four, once, before the window opens:
 *
 *  - **`timeBeginPeriod(1)`** — the system timer resolves to a millisecond instead of the default ~15.6 ms.
 *    Everything that waits then wakes on time: the packet-staleness timer, the edge-scroll ticker, and
 *    Windows' own message pump for this process.
 *  - **`ABOVE_NORMAL_PRIORITY_CLASS`** — every thread of this process now outranks the threads of the
 *    normal-priority processes competing with it (browsers, sync clients, the IDE), while still yielding to
 *    a foreground-critical task elsewhere. Deliberately not `HIGH_PRIORITY_CLASS`: an editor must not be
 *    able to starve the machine it is running on.
 *  - **EcoQoS off** (`PROCESS_POWER_THROTTLING_EXECUTION_SPEED`, cleared) — Windows 11 may otherwise demote
 *    a process's threads' execution speed when it is not the one in front, which shows up exactly as "smooth
 *    until other windows are busy".
 *  - **The frame thread, boosted on itself** — `THREAD_PRIORITY_HIGHEST` plus an MMCSS *Audio* registration
 *    (`AvSetMmThreadCharacteristicsW`). Both are per-thread calls, so they are made from inside the AWT
 *    event thread: that is the thread that reflows a page and paints the frame the hand is waiting for.
 *
 * Each step is best-effort and independent: a machine that refuses one (a locked-down policy, a runtime
 * without `avrt`) keeps the others, and a failed call is not an error the reader has to see. Nothing here
 * draws, edits or decides anything — it only asks Windows for the time the input already needs.
 */
internal object WindowsScheduling {

    /** `ABOVE_NORMAL_PRIORITY_CLASS`: above the normal band, below the above-critical one. */
    internal const val ABOVE_NORMAL_PRIORITY_CLASS = 0x0000_8000

    /** `THREAD_PRIORITY_HIGHEST` (2): the top of the normal thread band, matched to the class above. */
    internal const val THREAD_PRIORITY_HIGHEST = 2

    /** `ProcessPowerThrottling`: the information class [PROCESS_POWER_THROTTLING_STATE] is read for. */
    internal const val PROCESS_POWER_THROTTLING = 4

    /** `PROCESS_POWER_THROTTLING_CURRENT_VERSION`. */
    internal const val POWER_THROTTLING_VERSION = 1

    /** `PROCESS_POWER_THROTTLING_EXECUTION_SPEED`: the control whose `StateMask` bit *enables* throttling. */
    internal const val POWER_THROTTLING_EXECUTION_SPEED = 0x1

    /** `sizeof(PROCESS_POWER_THROTTLING_STATE)`: three `ULONG`s. */
    private const val POWER_THROTTLING_SIZE = 12

    /** `TIMERR_NOERROR`: `timeBeginPeriod`'s "the resolution is now what you asked for". */
    internal const val TIMERR_NOERROR = 0

    /** The MMCSS task a paint thread belongs to — the band Windows already protects for audio. */
    internal const val MMCSS_TASK = "Audio"

    private var prepared = false

    /**
     * What the three process-wide levers did — reported rather than swallowed, so a **misdeclared native
     * call** (which throws, and would otherwise look like a machine that refused) is told apart from a policy
     * that said no: `WindowsSchedulingTest` asserts every lever lands on a Windows runner.
     */
    internal data class Report(
        val timerResolution: Boolean,
        val priorityClass: Boolean,
        val powerThrottling: Boolean,
    )

    /** Whether the event thread managed to raise itself once the toolkit started dispatching. */
    @Volatile
    internal var eventThreadBoosted: Boolean? = null
        private set

    /**
     * Ask Windows for the input path's time — once per process; null when it was already asked, or when this
     * is not Windows at all.
     *
     * Called from `main` before the window exists: the process class and the timer apply to every thread
     * created afterwards, and the event thread's own boost is queued on it as soon as the toolkit starts
     * dispatching.
     */
    fun prepare(): Report? {
        if (prepared) return null
        prepared = true
        if (!isWindows) return null
        val report = Report(
            timerResolution = runCatching { setTimerResolution(1) }.getOrDefault(false),
            priorityClass = runCatching { askAboveNormal() }.getOrDefault(false),
            powerThrottling = runCatching { disablePowerThrottling() }.getOrDefault(false),
        )
        // The event thread must boost itself: both calls act on the calling thread, and it is the one whose
        // lateness the hand feels (the page's reflow and the frame's paint both run here).
        runCatching { EventQueue.invokeLater { eventThreadBoosted = boostCurrentThread() } }
        return report
    }

    /** Raise **this** thread and join it to MMCSS — called on the thread to be raised. */
    internal fun boostCurrentThread(): Boolean {
        val raised = kernel32?.let { api ->
            runCatching { api.SetThreadPriority(api.GetCurrentThread(), THREAD_PRIORITY_HIGHEST) }.getOrDefault(false)
        } ?: false
        val schedulable = avrt?.let { api ->
            runCatching { api.AvSetMmThreadCharacteristicsW(WString(MMCSS_TASK), IntByReference()) != null }
                .getOrDefault(false)
        } ?: false
        return raised || schedulable
    }

    internal val isWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows")

    private fun setTimerResolution(millis: Int): Boolean {
        val api = winmm ?: return false
        return api.timeBeginPeriod(millis) == TIMERR_NOERROR
    }

    private fun askAboveNormal(): Boolean {
        val api = kernel32 ?: return false
        return api.SetPriorityClass(api.GetCurrentProcess(), ABOVE_NORMAL_PRIORITY_CLASS)
    }

    /**
     * Turn the execution-speed throttle **off** for this process.
     *
     * The state is three `ULONG`s written into native memory (`Version`, `ControlMask`, `StateMask`): a bit
     * set in `ControlMask` makes the matching `StateMask` bit meaningful, and a `StateMask` bit of zero means
     * *that control is not throttling us*.
     */
    private fun disablePowerThrottling(): Boolean {
        val api = kernel32 ?: return false
        val state = Memory(POWER_THROTTLING_SIZE.toLong())
        state.setInt(0, POWER_THROTTLING_VERSION)
        state.setInt(4, POWER_THROTTLING_EXECUTION_SPEED)
        state.setInt(8, 0)
        return api.SetProcessInformation(
            api.GetCurrentProcess(),
            PROCESS_POWER_THROTTLING,
            state,
            POWER_THROTTLING_SIZE,
        )
    }

    /** `kernel32`: the process and thread priorities, and the power-throttling switch. */
    internal interface Kernel32 : StdCallLibrary {
        fun GetCurrentProcess(): Pointer
        fun GetCurrentThread(): Pointer
        fun SetPriorityClass(process: Pointer, priorityClass: Int): Boolean
        fun SetThreadPriority(thread: Pointer, priority: Int): Boolean
        fun SetProcessInformation(process: Pointer, infoClass: Int, info: Pointer, size: Int): Boolean
    }

    /** `winmm`: the one call that sets the system timer's resolution. */
    internal interface Winmm : StdCallLibrary {
        fun timeBeginPeriod(period: Int): Int
    }

    /** `avrt`: MMCSS, the band Windows reserves for latency-sensitive work like audio. */
    internal interface Avrt : StdCallLibrary {
        fun AvSetMmThreadCharacteristicsW(taskName: WString, taskIndex: IntByReference): Pointer?
        fun AvRevertMmThreadCharacteristics(handle: Pointer): Boolean
    }

    private val kernel32: Kernel32? by lazy { load("kernel32", Kernel32::class.java) }
    private val winmm: Winmm? by lazy { load("winmm", Winmm::class.java) }
    private val avrt: Avrt? by lazy { load("avrt", Avrt::class.java) }

    private fun <T : StdCallLibrary> load(library: String, type: Class<T>): T? =
        runCatching { Native.load(library, type) as T }.getOrNull()
}
