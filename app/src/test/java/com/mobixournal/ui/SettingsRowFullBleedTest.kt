package com.mobixournal.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A settings row's highlight must be **the card's width**, never a rounded band inside it.
 *
 * The card pads its content by `SettingsRowInset`, and a Compose ripple is drawn inside the node it
 * belongs to — so a row laid out in that padded content could only ever light the middle of the card:
 * a smaller copy of the card's own shape, which reads as a box inside a box (the reader described it as
 * "the item lighting only part of its box"). `fullBleedRow` lays the row out into the padding instead,
 * content still inset by one `SettingsRowInset` and corners supplied by the card's clip.
 *
 * Two things are pinned here. The arithmetic, as plain integers, because it is what makes the row end
 * exactly on the card's edge. And the wiring, read off the sources, so a row added later cannot quietly
 * go back to drawing an inset highlight — or re-clip its own corners, which would put a second, smaller
 * rounded shape inside the card's.
 */
class SettingsRowFullBleedTest {

    @Test
    fun `a full-bleed row starts and ends one inset past the card's content`() {
        val cardContentWidth = 720
        val inset = 16

        val bounds = fullBleedBounds(maxWidth = cardContentWidth, reach = inset)

        assertEquals("the row reports the content width it was given", cardContentWidth, bounds.reportedWidth)
        assertEquals("its content spans both insets", cardContentWidth + inset * 2, bounds.contentWidth)
        assertEquals("and is placed one inset to the left", -inset, bounds.contentX)
        // The card's own edge: the row's left edge lands where the card's padding begins...
        assertEquals(0, inset + bounds.contentX)
        // ...and its right edge lands one inset past the content, on the other side.
        assertEquals(cardContentWidth + inset, bounds.contentX + bounds.contentWidth)
    }

    @Test
    fun `every settings card row is laid out full-bleed and leaves its corners to the card`() {
        val ui = File(repoRoot(), "app/src/main/java/com/mobixournal/ui")
        val rows = mapOf(
            "SettingsWidgets.kt" to listOf("fun SwitchRow(", "fun <T> OptionGroup(", "fun RailItemRow("),
            "SettingsScreen.kt" to listOf("fun SectionRow("),
            "AboutSection.kt" to listOf("fun LinkRow("),
        )
        val offenders = mutableListOf<String>()
        for ((name, signatures) in rows) {
            val source = File(ui, name).readText()
            for (signature in signatures) {
                val body = body(source, signature)
                if (body.isEmpty()) {
                    offenders += "$name → $signature is not where the test expects it"
                    continue
                }
                if (!body.contains("fullBleedRow(")) offenders += "$name → $signature does not reach the card's edge"
                if (body.contains("clip(RoundedCornerShape")) {
                    offenders += "$name → $signature clips its own corners (the card's clip is the row's shape)"
                }
            }
        }
        assertTrue("a settings row must light its whole card:\n  " + offenders.joinToString("\n  "), offenders.isEmpty())
    }

    /** The card is the thing that publishes the inset; without it every row above is a no-op. */
    @Test
    fun `the card publishes the inset it pads by`() {
        val source = File(repoRoot(), "app/src/main/java/com/mobixournal/ui/SettingsLayout.kt").readText()
        val card = body(source, "internal fun SettingsCard(")
        assertTrue("SettingsCard is where the test expects it", card.isNotEmpty())
        assertTrue(
            "the card must publish the inset its rows undo",
            card.contains("LocalSettingsCardInset provides SettingsRowInset"),
        )
        assertTrue(
            "and pad its own content by that same number",
            card.contains("padding(horizontal = SettingsRowInset"),
        )
    }

    /** A top-level declaration's body: from its signature to the next `}` in the first column. */
    private fun body(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val end = source.indexOf("\n}", start)
        return if (end < 0) source.substring(start) else source.substring(start, end)
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
