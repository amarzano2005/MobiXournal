package com.mobixournal.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A **tappable card** wears its own shape: the clip comes before the click.
 *
 * The About page's two doors are whole cards — the shape a reader aims at — and the card is the only node
 * whose clip is its corner. The first version put the click on a column *inside* the card instead, and
 * because the two doors are stretched to a shared height the indication lit a rectangle that stopped at
 * the text's last line: a box inside a capsule, the mistake `docs/interface-contract.md` §6 names ("the
 * state layer follows the shape ... clips to its own shape before the click").
 *
 * The order is read off the source because it is invisible in a build and easy to "fix" by moving the
 * `clickable` onto the caller's modifier, where it would paint over the rounded corners. This is the same
 * shape of guard as `SettingsRowFullBleedTest`: the mistake compiles, and only a source check sees it.
 */
class SettingsCardSurfaceTest {

    @Test
    fun `a tappable card clips its shape before the click`() {
        val source = File(File(repoRoot(), "app/src/main/java/com/mobixournal/ui"), "SettingsLayout.kt").readText()
        val card = source.indexOf("internal fun SettingsCard(")
        assertTrue("SettingsCard is where the test expects it", card >= 0)

        val clip = source.indexOf(".clip(SettingsCardShape)", card)
        val click = source.indexOf(".clickable(onClick = onClick)", card)
        assertTrue("the card's surface is clipped to its shape", clip > card)
        assertTrue(
            "the card's own click is on the clipped surface — a row inside it cannot light a stretched card",
            click > clip,
        )
        assertTrue(
            "the click is applied to that surface, not to the caller's modifier",
            source.substring(clip, click).contains(".floatingRim("),
        )
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
