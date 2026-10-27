package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Settings surface, held to the **window's** proportions and the contract's material rules.
 *
 * The page shipped once as the tablet's stacked blocks — a name, a sentence and a full-width control per
 * setting — and on a desktop that read as four bands of buttons per card with a thousand pixels of empty
 * card beside each. A comment cannot stop that coming back, so the claims are measured here:
 *
 *  - a row is a **row**: 40 dp tall, with a control no taller than 30 dp and no wider than its words;
 *  - the index is a **list**, not a column: 232 dp, so the page keeps the width;
 *  - the page is a **page**: capped at 720 dp, so an ultrawide window does not stretch a label;
 *  - every Interface setting is visible in a 700 px window without scrolling.
 *
 * The material rule is the contract's first one: the surface is a band, its rows take tokens, and a colour
 * or a font size spelled at a call site is a second design system. That is checked in source, the way
 * `DesktopThemeTest` sweeps for literals.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopSettingsStyleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 1000
    private val height = 700

    /**
     * The index column (232 dp), its padding and the page's own: everything right of this is the page.
     *
     * The panel is centred, so the column starts at the panel's left edge — half of the window's spare
     * width — rather than at the window's.
     */
    private val pageLeft = 320

    private fun notebook(): File = temp.newFile("style.mobix").also {
        SyntheticNotebook.write(it, SyntheticNotebook.pages(2))
    }

    private fun state(section: DesktopSettingSection): DesktopShellState {
        val state = DesktopShellState(DesktopSettings(File(temp.root, "style.properties")))
        state.open(notebook())
        state.prefs.openAt(section)
        return state
    }

    private fun pixels(state: DesktopShellState): IntArray {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        return try {
            Snapshot.sendApplyNotifications()
            val png = scene.render().encodeToData()!!.bytes
            File("build/desktop-render/settings-style.png").apply { parentFile?.mkdirs() }.writeBytes(png)
            val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(png))
            IntArray(image.width * image.height).also {
                image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
            }
        } finally {
            scene.close()
        }
    }

    private fun IntArray.rgb(x: Int, y: Int): Int = this[y * width + x] and 0xFFFFFF

    /**
     * The rows of a page that hold a picked pill: the steel fill, banded by y.
     *
     * The scan starts **under the header** and stops above the status bar: the tab strip wears the same
     * steel, and a tab is not a setting (`band 12..35` was the active tab, the first time this ran).
     */
    private fun pillBands(pixels: IntArray): List<IntRange> {
        val pill = pillRgb
        val top = DesktopTheme.headerHeight.value.toInt() + 1
        val bottom = height - DesktopTheme.statusHeight.value.toInt() - 1
        // A row belongs to a chip when the steel is on it at all — the answer's own letters are drawn
        // **over** the fill, so a row through the word has no long unbroken run. The narrow specks a
        // rounded corner leaves are dropped by the band height below, not by this test.
        val rows = (top..bottom).filter { y ->
            (pageLeft until width).any { x -> pixels.rgb(x, y) == pill }
        }
        val bands = mutableListOf<IntRange>()
        for (y in rows) {
            val last = bands.lastOrNull()
            if (last != null && y <= last.last + 1) {
                bands[bands.lastIndex] = last.first..y
            } else {
                bands += y..y
            }
        }
        // A chip is at least a control tall; a two-row speck is a corner's antialiasing.
        return bands.filter { it.count() >= 12 }
    }

    private val pillRgb: Int = DesktopTheme.activeSlot.toArgb() and 0xFFFFFF

    @Test
    fun `a row is a preference, not a touch target`() {
        val index = SettingsMetrics.INDEX_WIDTH.value
        val row = SettingsMetrics.ROW_HEIGHT.value
        val control = SettingsMetrics.CONTROL_HEIGHT.value
        val page = SettingsMetrics.PAGE_MAX_WIDTH.value
        assertEquals("the index is a list of names, and no wider", 232.0, index.toDouble(), 0.0)
        assertEquals("one setting is one 40 dp row", 40.0, row.toDouble(), 0.0)
        assertTrue("a control sits inside its row: $control dp", control in 24f..32f && control < row)
        assertTrue("the page column is capped so a 21:9 window keeps its line length: $page dp", page <= 760f)
    }

    /**
     * The page is **one screen**: the Toolbar section's choice rows, and the last one still above the
     * status bar. The stacked layout this replaces ran past the bottom of the same window.
     */
    @Test
    fun `every Toolbar setting is on the page at once`() {
        val bands = pillBands(pixels(state(DesktopSettingSection.TOOLBAR)))
        assertTrue("a pill band per choice row: ${bands.size}", bands.size >= 1)
        for (band in bands) {
            assertTrue(
                "a pill is its control's height, not a band of a button: ${band.count()} dd",
                band.count() in 20..40,
            )
        }
        val lowest = bands.maxOf { it.last }
        assertTrue(
            "the last setting is above the status bar: $lowest of $height",
            lowest < height - 32,
        )
    }

    /**
     * A pill is as wide as its word: the first picked answer is a chip, not a third of the page.
     *
     * Measured from the first to the last pixel of the steel fill in the band's middle row, so the
     * answer's own letters (drawn in the accent, over the fill) do not cut the measurement in three.
     */
    @Test
    fun `the picked answer hugs its word`() {
        val pixels = pixels(state(DesktopSettingSection.TOOLBAR))
        val band = pillBands(pixels).first()
        val middle = (band.first + band.last) / 2
        val painted = (pageLeft until width).filter { x -> pixels.rgb(x, middle) == pillRgb }
        assertTrue("the picked chip is drawn", painted.size >= 2)
        val chip = painted.last() - painted.first() + 1
        assertTrue(
            "the picked chip is wide enough to read and narrow enough to be a chip: $chip px " +
                "(band ${band.first}..${band.last}, x ${painted.first()}..${painted.last()})",
            chip in 40..260,
        )
    }

    /**
     * A hovered row lights **the whole card**, not a rounded band inside it.
     *
     * A row used to sit inside the card's 12 dp padding, so its hover was a capsule floating in the card —
     * a card inside a card, which reads as an element that lights by halves. The measurement is a diff of
     * the same group with and without the pointer: the lit region has to start at the card's own left edge
     * and end at its right one.
     */
    @Test
    fun `a hovered row lights its card edge to edge`() {
        val width = 320
        val height = 170
        fun frame(hovered: Boolean): IntArray {
            val scene = ImageComposeScene(width, height, Density(1f)) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier
                        .size(width.dp, height.dp)
                        .background(DesktopTheme.desk),
                ) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxWidth().padding(10.dp),
                    ) {
                        SettingsGroup("Presets") {
                            SettingsRow(label = "Default", note = "0.05 · 1.00×", onClick = {}) {
                                androidx.compose.material3.Text(
                                    "In use",
                                    color = DesktopTheme.accent,
                                    style = DesktopTheme.label,
                                )
                            }
                        }
                    }
                }
            }
            return try {
                Snapshot.sendApplyNotifications()
                if (hovered) {
                    scene.sendPointerEvent(
                        androidx.compose.ui.input.pointer.PointerEventType.Enter,
                        androidx.compose.ui.geometry.Offset(width / 2f, height / 2f),
                    )
                    Snapshot.sendApplyNotifications()
                }
                val image = javax.imageio.ImageIO.read(
                    java.io.ByteArrayInputStream(scene.render().encodeToData()!!.bytes),
                )
                IntArray(image.width * image.height).also {
                    image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
                }
            } finally {
                scene.close()
            }
        }

        val rest = frame(hovered = false)
        val lit = frame(hovered = true)

        // The card is the group inside the frame's own 10 dp inset, and the strongest row of the diff is
        // the one the pointer is on — measured rather than guessed, so a change of spacing cannot make the
        // test read the wrong row.
        val cardLeft = 10
        val cardRight = width - 10 - 1
        val changedRows = (0 until height).map { y ->
            y to (0 until width).count { x -> rest[y * width + x] != lit[y * width + x] }
        }
        val rowY = changedRows.maxByOrNull { it.second }!!.first
        val changed = (0 until width).filter { x -> rest[rowY * width + x] != lit[rowY * width + x] }
        assertTrue("the row answers the pointer: ${changed.size} px", changed.size > width / 3)
        assertTrue(
            "the light starts at the card's left edge ($cardLeft), not inside it: ${changed.first()}",
            changed.first() in cardLeft..(cardLeft + 1),
        )
        assertTrue(
            "and stops at its right edge ($cardRight): ${changed.last()}",
            changed.last() in (cardRight - 1)..cardRight,
        )
    }

    @Test
    fun `the settings add no colour and no type size of their own`() {
        val offenders = mutableListOf<String>()
        val files = listOf("SettingsRows.kt", "SettingsSections.kt", "SettingsSurface.kt")
        for (name in files) {
            val file = File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop/$name")
            file.readLines().forEachIndexed { index, line ->
                // A size is `12.sp`; `spacedBy` is not a font. The patterns are exact on purpose, so the
                // guard can be read as the rule it enforces.
                val offence = listOf(
                    Regex("Color\\(0x"),
                    Regex("\\d+(\\.\\d+)?\\.sp\\b"),
                    Regex("fontSize\\s*="),
                    Regex("uppercase\\("),
                ).firstOrNull { it.containsMatchIn(line) }
                if (offence != null) offenders += "$name:${index + 1} ${offence.pattern}"
            }
        }
        assertTrue("tokens only, on the contract's own surfaces: ${offenders.joinToString("; ")}", offenders.isEmpty())
    }

    /**
     * No section may reach for a Material card or a Material glyph: both shells wear one material.
     *
     * The panel is a **floating surface** — the glass at `floatingPanelAlpha`, the family's rim, the 20 dp
     * corner — and it lies over the page, which is the class the contract gives a surface that comes and
     * goes. A band it is not: a band borders the canvas and may never lie on it.
     */
    @Test
    fun `the surface is the window's floating material`() {
        val text = File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop/SettingsSurface.kt").readText() +
            File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop/SettingsRows.kt").readText()
        assertTrue(
            "the panel is the floating recipe, on the settings' own almost-solid fill",
            text.contains("glassSurface(fill = DesktopTheme.settingsPanel)"),
        )
        assertTrue("and never a band over the page", !text.contains("bandSurface()"))
        assertTrue("a row takes the contract's hover step", text.contains("hoverFill("))
        assertTrue("the picked answer is the steel slot", text.contains("DesktopTheme.activeSlot"))
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
