package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** All native popup windows use the shared immediate desktop presentation and input policy. */
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
            "open native menus through GlassPopup: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `native popup content is never scaled or faded inside its window`() {
        val helper = sources().first { it.readText().contains("internal fun GlassPopup(") }.readText()
        val start = helper.indexOf("internal fun GlassPopup(")
        val body = helper.substring(start, helper.indexOf("\n}", start))
        assertTrue("visibility must gate the actual window", body.contains("if (visible)"))
        assertTrue("the body must open a native Popup", body.contains("Popup(offset"))
        assertTrue("no transparent or undersized opening frame", !body.contains("glassSpring(") && !body.contains("GlassPresence("))
    }
}
