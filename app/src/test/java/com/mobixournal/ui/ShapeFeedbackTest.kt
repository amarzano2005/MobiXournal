package com.mobixournal.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A rounded control's **feedback follows its shape**, never the rectangle it is measured in.
 *
 * A Compose indication is drawn by the node that owns it, so `.clickable()` hung on a bare box lights the
 * box: a 34 dp cell around a 28 dp swatch, a 32 dp square around a 18 dp glyph, a rectangle around a 10 dp
 * menu corner. The reader sees the *mismatch* — the press lights something the button is not — which is why
 * the fix is always the same one line: clip to the control's own shape **before** the click, so the ripple
 * and the hover state layer are cut by that shape. `FavoriteSwatch`'s [DotCellShape] is the same rule on a
 * cell that is wider than it is tall.
 *
 * The wiring is pinned by reading the sources, because the mistake is invisible in code that compiles and
 * reads correctly: nothing fails when a `clip` is one modifier too late. Each entry says where the control
 * lives, the clip that gives it its shape, and the call that draws its feedback; the guard fails if the
 * feedback is drawn before the clip, or if either has moved out from under the test.
 */
class ShapeFeedbackTest {

    /** A control, the clip that shapes it, and the call that draws its feedback. */
    private data class Control(
        val file: String,
        val signature: String,
        val clip: String,
        val feedback: String,
    )

    private val controls = listOf(
        // A colour swatch: the cell is 34 dp, the dot 24-28 dp, so the feedback is a circle centred in it.
        Control("Swatches.kt", "fun ColorSwatch(", ".clip(DotCellShape)", ".then(clickModifier)"),
        // The tab's close mark: a glyph in clear space, so its feedback is the target's own circle.
        Control(
            "TabStrip.kt",
            "private fun TabCloseButton(",
            ".clip(CircleShape)",
            ".clickable(onClick = onClose)",
        ),
        // A selection-bar button: a rounded slot's feedback, not the square 32 dp tap target's.
        Control(
            "EditorActionBars.kt",
            "private fun BarIconButton(",
            ".clip(RoundedCornerShape(8.dp))",
            ".clickable(onClick = onClick)",
        ),
        // A menu pane: the click is taken *inside* the pane's clip, so it lights the pane's corner.
        Control(
            "EditorOverflowMenu.kt",
            "private fun GlassPane(",
            ".clip(shape)",
            "Modifier.clickable(",
        ),
        // The entry screen's door: a capsule drawn inside a bare row, so the row has to be cut to the
        // capsule before the click or the press lights a rectangle around the button.
        Control(
            "entry/NotebookEntryScreen.kt",
            "private fun EntryButton(",
            ".clip(CircleShape)",
            ".clickable(onClick = onClick)",
        ),
    )

    @Test
    fun `a rounded control clips its feedback before it draws it`() {
        val offenders = mutableListOf<String>()
        for (control in controls) {
            val source = File(uiRoot(), control.file).readText()
            val body = body(source, control.signature)
            if (body.isEmpty()) {
                offenders += "${control.file} → ${control.signature} is not where the test expects it"
                continue
            }
            val clip = body.indexOf(control.clip)
            val feedback = body.indexOf(control.feedback)
            when {
                clip < 0 -> offenders += "${control.file} → ${control.signature} no longer clips its shape"
                feedback < 0 ->
                    offenders += "${control.file} → ${control.signature} draws its feedback somewhere else now"
                feedback < clip ->
                    offenders += "${control.file} → ${control.signature} draws its feedback before the clip: " +
                        "a tap there lights the box, not the button"
            }
        }
        assertTrue(
            "a rounded control's feedback must be its own shape:\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    /** A top-level declaration's body: from its signature to the next `}` in the first column. */
    private fun body(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val end = source.indexOf("\n}", start)
        return if (end < 0) source.substring(start) else source.substring(start, end)
    }

    private fun uiRoot(): File = File(repoRoot(), "app/src/main/java/com/mobixournal/ui")

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
