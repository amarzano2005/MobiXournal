package com.mobixournal.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File

/**
 * The document named on the command line, or null to start empty.
 *
 * `--args` splits its value on spaces, so a path that has one arrives in pieces: this machine keeps its
 * notebooks in `Il mio Drive/…`, and `--args="…/Il mio Drive/x.mobix"` handed `main` two arguments and a
 * `File("…/Il")` that does not exist. When the first piece is not itself a file, the pieces are put back
 * together and the joined path is tried — a real path with a space keeps working either way.
 */
internal fun initialDocument(args: Array<String>): File? {
    val first = args.firstOrNull() ?: return null
    val single = File(first)
    if (single.isFile) return single
    return File(args.joinToString(" ")).takeIf { it.isFile } ?: single
}

/** The argument that opens the tablet probe instead of the editor. */
internal const val PROBE_TABLET = "--probe-tablet"

/**
 * The probe window: pointer, wheel, key, pen and dial rows written to `tablet-probe.csv` beside the
 * running directory, with the keys observed at the window because that is where a tablet button arrives.
 *
 * Both Windows streams are opened here rather than only in the editor, so a reading can be attributed to
 * the device before any of it is wired into drawing: `ink` rows are `WM_POINTER` samples and `dial` rows
 * are the radial controller's rotations — or the step of the attempt that refused it.
 */
private fun runTabletProbe() {
    val probe = TabletProbe()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 900.dp, height = 640.dp),
            title = "MobiXournal tablet probe",
            onPreviewKeyEvent = { event -> probe.key(event); false },
        ) {
            // The streams this window's own event layer cannot see: Windows Ink's pen, and the dial as
            // the radial controller Windows shows it as. Opened after the window exists, closed with it.
            val devices = remember { DesktopTablet() }
            DisposableEffect(devices) {
                devices.open(
                    window = window,
                    onRotation = { degrees -> probe.dial("rotation " + degrees.asDegrees()) },
                    onClick = { probe.dial("click") },
                    log = { probe.note(it) },
                    // Every contact, written from the window procedure: the pen's own readings beside the
                    // pointer row the same contact produced, which is how a pressure claim is checked.
                    onSample = { sample -> probe.ink(sample.describe()) },
                    // And every pointer or wheel message Windows actually sent, so "the pen is a mouse"
                    // and "the dial sends nothing" are readings rather than conclusions.
                    onEvent = { line -> probe.ink(line) },
                )
                onDispose { devices.close() }
            }
            TabletProbeWindow(probe)
        }
    }
}

/** A signed degree reading, in the log's own notation. */
private fun Double.asDegrees(): String = String.format(java.util.Locale.ROOT, "%+.1f°", this)

/**
 * Runs the desktop preview: `./gradlew :desktop:run --args="udiff.xopp"` (or no argument to start
 * empty and pick a file from the toolbar). Packaging targets come from `desktop/build.gradle.kts`.
 *
 * Because `:desktop:run` executes the compiled class directories, a **build while the window is open**
 * replaces files under the live JVM; close the window before recompiling, and reopen the document in a
 * fresh one if it reports "The preview has fallen behind" ([describeOpenFailure]).
 */
fun main(args: Array<String>) {
    // The measuring window first: `--probe-tablet` is not a document, and the probe must not try to open
    // one (`desktop-tablet-pressure-spike`, `desktop-tablet-dial-spike`).
    if (args.contains(PROBE_TABLET)) {
        runTabletProbe()
        return
    }
    val initial = initialDocument(args)
    application {
        // The window's close request is a **question** first: the shell answers whether it may go, and a
        // dirty tab turns the X into the same Save / Discard / Cancel dialog the tab's own close glyph
        // raises. The X must not be able to bypass it, which is why the state is created here.
        val state = remember { DesktopShellState(recovery = DesktopRecovery()) }
        Window(
            onCloseRequest = { if (state.requestExit()) exitApplication() },
            state = rememberWindowState(width = 1100.dp, height = 1000.dp),
            title = "MobiXournal Desktop",
        ) {
            DesktopApp(initial = initial, state = state, onExit = ::exitApplication)
        }
    }
}
