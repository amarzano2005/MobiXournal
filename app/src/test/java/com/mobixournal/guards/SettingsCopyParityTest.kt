package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One section, **one spelling**: the names the tablet's Settings list and the window's Settings index
 * share must be the same words.
 *
 * Both shells keep their own list of sections — a page of controls is a different thing on a tablet and on
 * a desktop, and the window's list is deliberately the tablet's minus what a window cannot answer — but the
 * sections that appear in *both* are the same subjects, and a reader who uses both shells finds them under
 * the same headings. Nothing enforced that: the icons are pinned by `SettingsGlyphParityTest` and
 * `DesktopSettingsGlyphTest`, while the names were free to drift one reword at a time.
 *
 * The stronger version of this rule is code reuse, and it is where it can be had: the Appearance row draws
 * `SettingsSection.APPEARANCE`'s own title and summary rather than a second copy of them. This test covers
 * what cannot be one constant — a shell's list is its own — by reading both declarations and comparing the
 * names they share.
 */
class SettingsCopyParityTest {

    @Test
    fun `the sections both shells share are spelled the same`() {
        val tablet = sections("app/src/main/java/com/mobixournal/ui/SettingsScreen.kt")
        val window = sections("desktop/src/main/java/com/mobixournal/desktop/settings/SettingsSections.kt")

        // A parser that silently found nothing would make the comparison below vacuous.
        assertTrue("the tablet's section list did not parse: $tablet", tablet.size >= 8)
        assertTrue("the window's section list did not parse: $window", window.size >= 8)

        val shared = tablet.keys intersect window.keys
        assertTrue("the two shells share no section names at all: $shared", shared.size >= 5)

        val drifted = shared.filter { tablet[it] != window[it] }
        assertTrue(
            "one section, two spellings: " +
                drifted.joinToString { "$it is \"${tablet[it]}\" on the tablet and \"${window[it]}\" in the window" },
            drifted.isEmpty(),
        )

        assertEquals("Appearance", tablet["APPEARANCE"])
        assertEquals("Appearance", window["APPEARANCE"])
    }

    @Test
    fun `the appearance row wears its entry's words`() {
        // The row moved out of its own page and kept a reworded copy of the title and subtitle, which then
        // drifted from the entry's. It draws the entry's now; this pins that it still does.
        val row = File(repoRoot(), "app/src/main/java/com/mobixournal/ui/AppearanceSection.kt").readText()
        assertTrue(
            "the Appearance row carries its own title/subtitle again instead of SettingsSection.APPEARANCE's",
            row.contains("title = SettingsSection.APPEARANCE.title") &&
                row.contains("subtitle = SettingsSection.APPEARANCE.summary"),
        )
    }

    /**
     * `NAME(` on a line of its own, then the first one-string argument below it — the shape both lists are
     * written in. Comments and the enum's own parameters do not match either pattern, so they are skipped.
     */
    private fun sections(path: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val lines = File(repoRoot(), path).readLines()
        var i = 0
        while (i < lines.size) {
            val name = Regex("""^ {4}([A-Z][A-Z0-9_]*)\($""").find(lines[i])?.groupValues?.get(1)
            if (name != null) {
                var k = i + 1
                while (k < lines.size && !Regex("""^\s+"[^"]+",$""").containsMatchIn(lines[k])) k++
                if (k < lines.size) {
                    out[name] = Regex("\"([^\"]+)\"").find(lines[k])!!.groupValues[1]
                    i = k
                }
            }
            i++
        }
        return out
    }

    /** The repository root: the nearest ancestor that holds `AGENTS.md` and `docs/` (`CiCoverageTest`). */
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
