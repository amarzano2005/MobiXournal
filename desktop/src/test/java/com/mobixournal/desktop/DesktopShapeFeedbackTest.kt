package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's light **is its shape**, never a box around it.
 *
 * The tablet keeps the rule by clipping a control before its indication (`ShapeFeedbackTest` in the app);
 * the window keeps the same rule the other way round — `hoverFill(shape)` and `hoverCapsule` draw the light
 * *with* the shape, so one background cannot cover more than the control it fills. Two things therefore have
 * to stay true here, and this test reads the sources for both:
 *
 * - a hover step is never asked for without a shape (`hoverFill()` would light the bounds);
 * - a **rectangular** hover exists only on the settings rows, whose group card clips them
 *   (`SettingsGroup` → `glassSurface`), which is the one place a rectangle is right: the row is the card's
 *   width and the card's own corner rounds the first and last of them.
 *
 * The step chevrons of the header's search field are pinned by name because they were the one interactive
 * control in the window with no answer to the pointer at all — the gap the shared rule found.
 */
class DesktopShapeFeedbackTest {

    @Test
    fun `a hover step is always drawn with a shape`() {
        val shapeless = sources()
            .filter { it.readText().contains("hoverFill()") }
            .map { it.relativeTo(root()).invariantSeparatorsPath }
        assertTrue(
            "hoverFill without a shape lights the bounds, not the control: $shapeless",
            shapeless.isEmpty(),
        )
    }

    @Test
    fun `a rectangular hover belongs to flat desktop settings rows`() {
        val rect = sources()
            .filter { Regex("""hoverFill\(\s*RectangleShape""").containsMatchIn(it.readText()) }
            .map { it.relativeTo(root()).invariantSeparatorsPath }
        assertEquals(
            "only the settings rows draw a rectangle, and only because their card supplies the corners",
            listOf("settings/SettingsRows.kt"),
            rect,
        )
        val rows = File(root(), "settings/SettingsRows.kt").readText()
        assertTrue(
            "the group card must clip the rows whose rectangle it rounds (SettingsGroup → glassSurface)",
            body(rows, "internal fun SettingsGroup(").let { !it.contains("glassSurface(") && it.contains("DesktopTheme.hairline") },
        )
    }

    @Test
    fun `the search field's steps answer the pointer with their own shape`() {
        val header = File(root(), "shell/HeaderSearch.kt").readText()
        val body = body(header, "private fun SearchStepButton(")
        assertTrue("SearchStepButton is where the test expects it", body.isNotEmpty())
        assertTrue(
            "the step must light its own capsule, and had no hover answer at all before",
            body.contains("hoverFill(DesktopTheme.capsuleShape)"),
        )
    }

    /** Every Kotlin source of the window. */
    private fun sources(): List<File> = File(root(), "")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    /** A top-level declaration's body: from its signature to the next `}` in the first column. */
    private fun body(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val end = source.indexOf("\n}", start)
        return if (end < 0) source.substring(start) else source.substring(start, end)
    }

    /** `desktop/src/main/java/com/mobixournal/desktop`, found from wherever the test is run. */
    private fun root(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) {
                return File(dir, "desktop/src/main/java/com/mobixournal/desktop")
            }
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
