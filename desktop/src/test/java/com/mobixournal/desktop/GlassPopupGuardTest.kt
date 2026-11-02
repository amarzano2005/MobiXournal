package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's pop-ups **arrive and leave** ([docs/interface-contract.md] §7).
 *
 * A desktop `Popup` composed under `if (open)` is a surface that cannot animate out: the composition is
 * gone between two frames the instant the flag clears, so the panel pops in and vanishes — the report
 * "the tablet's menus spring and the window's blink" was exact. `GlassPopup` is the window's one window
 * (a `GlassPresence` hold plus the shared spring, applied *outside* the panel's fill), and this test is the
 * enforcement: a new flyout may not reintroduce the raw `Popup`.
 *
 * The one exception is the hover tooltip (`StripPill`): it is the pointer's own readout, not a surface the
 * user fired, so it lands with the pointer. A second exception is written down here rather than left to be
 * discovered.
 */
class GlassPopupGuardTest {

    /** The pointer's readout, and `GlassPopup`'s own window: the two places a raw `Popup(` may live. */
    private val allowedRawPopups = setOf("ToolStrips.kt", "GlassMotion.kt")

    private fun sources(): List<File> = File("src/main/java/com/mobixournal/desktop")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    @Test
    fun `every popup is opened through GlassPopup`() {
        val sources = sources()
        assertTrue("the desktop sources are where the test expects them", sources.isNotEmpty())

        // `GlassPopup(` itself contains the word, so the raw call is the one not preceded by `Glass`.
        val raw = Regex("""(?<!Glass)\bPopup\s*\(""")
        val offenders = sources
            .filter { raw.containsMatchIn(it.readText()) }
            .map { it.name }
            .filterNot { it in allowedRawPopups }
        assertTrue(
            "these open a raw Popup(: its panel pops in and vanishes with the flag, because the " +
                "composition is torn down before the closing half can run. Open it with " +
                "GlassPopup(visible = …, offset = …, onDismissRequest = …) { … }, or record the " +
                "readout here: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the one popup is a presence hold plus the shared spring`() {
        val sources = sources()
        assertTrue(
            "nothing in the window opens a pop-up through GlassPopup",
            sources.any { it.readText().contains("GlassPopup(") },
        )

        // The gate and the spring are the helper's own job — that is why the call sites need neither — so
        // this reads them out of `GlassPopup`'s body rather than out of every caller.
        val helper = sources.first { it.readText().contains("internal fun GlassPopup(") }.readText()
        val body = helper.substring(helper.indexOf("internal fun GlassPopup("))
        assertTrue(
            "GlassPopup must hold the window with GlassPresence(...), or the closing half has nowhere to play",
            body.contains("GlassPresence("),
        )
        assertTrue(
            "GlassPopup must apply glassSpring() outside the panel's fill",
            body.contains("Modifier.glassSpring()"),
        )
        assertTrue(
            "and it must still open a Popup( — the window itself is not replaced by a Box",
            body.lines().any { it.trimStart().startsWith("Popup(") },
        )
    }
}
