package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale

/**
 * The instrument for `desktop-tablet-pressure-spike` and `desktop-tablet-dial-spike`: **what the JVM
 * actually receives** from the graphics tablet, written to a CSV so a claim about pressure or a dial can
 * be checked against a reading instead of a spec sheet.
 *
 * It is the desktop's counterpart of the tablet app's Pen diagnostics panel (`docs/stylus.md`), and the
 * same rule applies: it observes *before* anything interprets, so the numbers are the device's, not the
 * editor's opinion of them. One row per pointer event holds every field a Compose pointer carries —
 * position, `pressure`, `type`, the button bits, and how many coalesced historical samples came with it —
 * one row per key holds the code the key arrived on, because a tablet's ExpressKeys reach Windows as
 * keys, and the two device rows ([ink], [dial]) record what the *Windows* streams say beside it.
 *
 * Run it with `--probe-tablet` (`Main.kt`); the file is `tablet-probe.csv` beside the running directory,
 * rewritten at each start so one session is one table. Nothing here is on the editor's path: the probe
 * window is a diagnostic surface, and the ink still comes from `PageCanvasInput`.
 */
internal class TabletProbe(val file: File = File(System.getProperty("user.dir"), "tablet-probe.csv")) {

    /** True when the file could be written; a probe that cannot log says so on screen. */
    val writes: Boolean

    /** Rows written this session, so the window can show that the instrument is live. */
    var rows: Int by mutableStateOf(0)
        private set

    /** The last row, shown on screen: a reader can compare it with the pen in their hand. */
    var last: String by mutableStateOf("")
        private set

    init {
        writes = runCatching {
            file.writeText("kind,event,time,x,y,pressure,type,buttons,historical,detail\n")
        }.isSuccess
    }

    /** One pointer event: its type, the primary change's reading, the buttons and the batch size. */
    @OptIn(ExperimentalComposeUiApi::class)
    fun pointer(eventType: PointerEventType, event: androidx.compose.ui.input.pointer.PointerEvent) {
        val change = event.changes.firstOrNull() ?: return
        val pressure = String.format(Locale.ROOT, "%.4f", change.pressure)
        val x = String.format(Locale.ROOT, "%.1f", change.position.x)
        val y = String.format(Locale.ROOT, "%.1f", change.position.y)
        val buttons = "${event.buttons.isPrimaryPressed}/${event.buttons.isSecondaryPressed}/" +
            "${event.buttons.isTertiaryPressed}"
        val row = listOf(
            "pointer", "$eventType", "${change.uptimeMillis}", x, y, pressure,
            "${change.type}", buttons, "${change.historical.size}", "",
        ).joinToString(",")
        write(row)
    }

    /**
     * One scroll event: a rotary dial delivered as a wheel arrives here, so it is measured as one.
     *
     * The native event is logged with it because it is the only place a **dial** can be told from a
     * mouse wheel: Windows Ink's precision wheel (what a dial or a touchpad sends) is a
     * `MouseWheelEvent` whose precise rotation is **fractional**, while a wheel notch is ±1. The class
     * name and that rotation are what `desktop-tablet-dial-spike` has to decide on.
     */
    fun scroll(event: androidx.compose.ui.input.pointer.PointerEvent) {
        val change = event.changes.firstOrNull() ?: return
        val native = event.nativeEvent
        val wheel = native as? java.awt.event.MouseWheelEvent
        val rotation = wheel?.let {
            String.format(Locale.ROOT, " rotation=%.3f/%.3f", it.preciseWheelRotation, it.wheelRotation.toDouble())
        } ?: ""
        val detail = String.format(Locale.ROOT, "scroll %.2f/%.2f native=%s%s",
            change.scrollDelta.x, change.scrollDelta.y, native?.javaClass?.simpleName ?: "-", rotation)
        write(
            listOf(
                "scroll", "Scroll", "${change.uptimeMillis}",
                String.format(Locale.ROOT, "%.1f", change.position.x),
                String.format(Locale.ROOT, "%.1f", change.position.y),
                String.format(Locale.ROOT, "%.4f", change.pressure),
                "${change.type}", "", "", detail,
            ).joinToString(","),
        )
    }

    /**
     * One key event: a tablet button or a dial detent the driver sends as a key. The **native code** is
     * the column that matters — it names the physical key without the keyboard layout's opinion.
     */
    fun key(event: KeyEvent) {
        val type = if (event.type == KeyEventType.KeyDown) "KeyDown" else "KeyUp"
        val detail = String.format(
            Locale.ROOT,
            "%s native=%d ctrl=%b shift=%b alt=%b",
            event.key.toString(), event.key.nativeKeyCode,
            event.isCtrlPressed, event.isShiftPressed, event.isAltPressed,
        )
        write(listOf("key", type, "", "", "", "", "", "", "", detail).joinToString(","))
    }

    /** A line from the Windows Ink side: one `WM_POINTER` sample, already in the editor's units. */
    fun ink(line: String) = write(listOf("ink", "", "", "", "", "", "", "", "", line).joinToString(","))

    /** A line from the dial: its rotation in degrees, or the step of the attempt that refused it. */
    fun dial(line: String) = write(listOf("dial", "", "", "", "", "", "", "", "", line).joinToString(","))

    /** A note from the probe's own setup — a missing library, a step that was refused. */
    fun note(line: String) = write(listOf("note", "", "", "", "", "", "", "", "", line).joinToString(","))

    private fun write(row: String) {
        if (!writes) return
        runCatching { file.appendText(row + "\n") }
        rows++
        last = row
    }
}

/**
 * The probe's window: a black sheet that takes the pen, the wheel and the keys, and shows the live row
 * so the person at the tablet can see the instrument answering.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TabletProbeWindow(probe: TabletProbe) {
    DesktopTheme.Apply {
        Column(
            Modifier
                .fillMaxSize()
                .background(DesktopTheme.desk)
                .padding(24.dp)
                .onPointerEvent(PointerEventType.Press) { probe.pointer(PointerEventType.Press, it) }
                .onPointerEvent(PointerEventType.Move) { probe.pointer(PointerEventType.Move, it) }
                .onPointerEvent(PointerEventType.Release) { probe.pointer(PointerEventType.Release, it) }
                .onPointerEvent(PointerEventType.Scroll) { probe.scroll(it) },
        ) {
            Text("Tablet probe", color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (probe.writes) "Logging to ${probe.file.absolutePath}" else "Cannot write the log file",
                color = if (probe.writes) DesktopTheme.inkMuted else DesktopTheme.danger,
                style = DesktopTheme.body,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "1. Draw a slow stroke on this sheet: light, then hard.",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.body,
            )
            Text(
                "2. Roll the pen between your fingers, then tilt it flat and upright.",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.body,
            )
            Text(
                "3. Turn the tablet's rotary dial several full turns, both ways.",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.body,
            )
            Text(
                "   The dial reaches Windows as a wheel: its menu lists “MobiXournal” once the window opens.",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
            )
            Text(
                "4. Press each ExpressKey and the pen's barrel button.",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.body,
            )
            Spacer(Modifier.height(18.dp))
            Text("${probe.rows} rows", color = DesktopTheme.accent, style = DesktopTheme.numeric)
            Spacer(Modifier.height(8.dp))
            Text(probe.last, color = DesktopTheme.inkMuted, style = DesktopTheme.numeric)
        }
    }
}
