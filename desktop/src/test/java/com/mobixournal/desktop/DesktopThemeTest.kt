package com.mobixournal.desktop

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import com.mobixournal.design.ToolGlyphCatalogue
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop's half of the token guard (`ModernThemeTest` is the Android half).
 *
 * The window used to carry its own greys: `Color(0xFF141418)` for the bands, `0xFF2A2A31` for a
 * selected tab, `0xFF60A5FA` for the accent — numbers that no doc mentioned and that had already
 * drifted from the app's. Two things are now enforced:
 *
 *  1. **One source.** `DesignTokens` (plain Kotlin, `mobix-core`) holds the documented values,
 *     `DesktopTheme` turns them into Compose types, and this test re-reads the JSON block in
 *     `docs/ui-modernization.md` so a token cannot move in code without moving in the doc.
 *  2. **No palette in the canvas's own code.** Every chrome file draws from [DesktopTheme]; a
 *     `Color(0x…)` literal creeping back into one of them fails the build by name.
 */
class DesktopThemeTest {

    @Test
    fun `tokens match the documented json block`() {
        val doc = File(repoRoot(), "docs/ui-modernization.md")
        assertTrue("docs/ui-modernization.md must exist", doc.isFile)
        val match = Regex("""(?s)## Design token specification.*?```json\s*(.*?)\s*```""")
            .find(doc.readText())
        assertNotNull("the token JSON block must be present", match)
        val json = match!!.groupValues[1]

        fun string(key: String): String =
            Regex("""\"$key\"\s*:\s*\"([^\"]+)\"""").find(json)?.groupValues?.get(1)
                ?: error("key $key missing from the doc's token json")

        fun number(key: String): Double =
            Regex("""\"$key\"\s*:\s*([0-9.]+)""").find(json)?.groupValues?.get(1)?.toDouble()
                ?: error("key $key missing from the doc's token json")

        assertEquals(DesignTokens.GLASS_DARK_RAW, string("glassDark"))
        assertEquals(DesignTokens.GLASS_LIGHT_RAW, string("glassLight"))
        assertEquals(DesignTokens.HAIRLINE_BORDER_RAW, string("hairlineBorder"))
        assertEquals(DesignTokens.FLOATING_PANEL_ALPHA_RAW, number("floatingPanelAlpha").toString())
        assertEquals(DesignTokens.PANEL_CORNER_RADIUS_DP, number("panelCornerRadiusDp").toInt())
        assertEquals(DesignTokens.BLUR_RADIUS_DP, number("blurRadiusDp").toInt())
        assertEquals(DesignTokens.CAPSULE_CORNER_RADIUS_DP, number("capsuleCornerRadiusDp").toInt())
        assertEquals(DesignTokens.COMPACT_BUTTON_SIZE_DP, number("compactButtonSizeDp").toInt())
        assertEquals(DesignTokens.HAIRLINE_STROKE_WIDTH_DP, number("hairlineStrokeWidthDp"), 1e-4)

        // The raised glass: the lift under a band and a capsule, and the chrome's own ink.
        assertEquals(DesignTokens.BAND_SHADOW_DP, number("bandShadowDp").toInt())
        assertEquals(DesignTokens.CAPSULE_SHADOW_DP, number("capsuleShadowDp").toInt())
        assertEquals(DesignTokens.CHROME_INK_DARK_RAW, string("chromeInkDark"))
        assertEquals(DesignTokens.HOVER_SLOT_ALPHA, number("hoverSlotAlpha").toFloat(), 1e-4f)
        // Compose packs a colour's alpha to 8 bits, so 0.45 comes back as 115/255.
        assertEquals(DesignTokens.HOVER_SLOT_ALPHA, DesktopTheme.hoverSlot.alpha, 0.005f)
        assertEquals(DesktopTheme.activeSlot.red, DesktopTheme.hoverSlot.red, 1e-6f)
        assertEquals(DesktopTheme.activeSlot.green, DesktopTheme.hoverSlot.green, 1e-6f)
        assertEquals(DesktopTheme.activeSlot.blue, DesktopTheme.hoverSlot.blue, 1e-6f)

        // The rail both shells draw: one capsule, one lane, one slot corner, one glyph grid.
        assertEquals(DesignTokens.RAIL_CAPSULE_WIDTH_DP, number("railCapsuleWidthDp").toInt())
        assertEquals(DesignTokens.RAIL_LANE_PADDING_DP, number("railLanePaddingDp").toInt())
        assertEquals(DesignTokens.RAIL_LANE_WIDTH_DP, number("railLaneWidthDp").toInt())
        assertEquals(DesignTokens.RAIL_OUTER_PADDING_DP, number("railOuterPaddingDp").toInt())
        assertEquals(DesignTokens.RAIL_SLOT_CORNER_RADIUS_DP, number("railSlotCornerRadiusDp").toInt())
        assertEquals(DesignTokens.HEADER_CONTROL_SIZE_DP, number("headerControlSizeDp").toInt())
        assertEquals(DesignTokens.TOOL_GLYPH_GRID, number("toolGlyphGrid").toFloat(), 1e-4f)
        assertEquals(
            DesignTokens.TOOL_GLYPH_STROKE_UNITS,
            number("toolGlyphStrokeUnits").toFloat(),
            1e-4f,
        )
        assertEquals(DesignTokens.SPRING_STIFFNESS, number("springStiffness").toFloat(), 1e-4f)
        assertEquals(DesignTokens.SPRING_DAMPING, number("springDamping").toFloat(), 1e-4f)
        assertEquals(DesignTokens.FADE_TRANSITION_MS, number("fadeTransitionMs").toInt())

        // The glass' arrival: the same numbers the tablet's panels move by.
        assertEquals(DesignTokens.GlassMotion.START_SCALE, number("startScale").toFloat(), 1e-4f)
        assertEquals(DesignTokens.GlassMotion.OPEN_DAMPING_RATIO, number("openDamping").toFloat(), 1e-4f)
        assertEquals(DesignTokens.GlassMotion.OPEN_STIFFNESS, number("openStiffness").toFloat(), 1e-4f)
        assertEquals(DesignTokens.GlassMotion.FADE_IN_MS, number("fadeInMs").toInt())
        assertEquals(DesignTokens.GlassMotion.CLOSE_DAMPING_RATIO, number("closeDamping").toFloat(), 1e-4f)
        assertEquals(DesignTokens.GlassMotion.CLOSE_STIFFNESS, number("closeStiffness").toFloat(), 1e-4f)
        assertEquals(DesignTokens.GlassMotion.FADE_OUT_MS, number("fadeOutMs").toInt())
        assertEquals(DesignTokens.GlassMotion.EXIT_HOLD_MS, number("exitHoldMs").toInt())
    }

    @Test
    fun `the window's colours are the tokens, not colours of their own`() {
        val tolerance = 0.005f
        assertEquals(0x16 / 255f, DesktopTheme.glass.red, tolerance)
        assertEquals(0x16 / 255f, DesktopTheme.glass.green, tolerance)
        assertEquals(0x18 / 255f, DesktopTheme.glass.blue, tolerance)
        assertEquals(DesignTokens.GLASS_DARK_ALPHA, DesktopTheme.glass.alpha, tolerance)

        assertEquals("the desk is true OLED black", 0f, DesktopTheme.desk.red, 0f)
        assertEquals(0f, DesktopTheme.desk.green, 0f)
        assertEquals(0f, DesktopTheme.desk.blue, 0f)

        // A floating surface is the bands' own glass with the see-through turned down.
        assertEquals(DesktopTheme.glass.red, DesktopTheme.floating.red)
        assertEquals(DesktopTheme.glass.green, DesktopTheme.floating.green)
        assertEquals(DesktopTheme.glass.blue, DesktopTheme.floating.blue)
        assertEquals(DesignTokens.FLOATING_PANEL_ALPHA, DesktopTheme.floating.alpha, tolerance)

        assertEquals(0x60 / 255f, DesktopTheme.accent.red, tolerance)
        assertEquals(0xA5 / 255f, DesktopTheme.accent.green, tolerance)
        assertEquals(0xFA / 255f, DesktopTheme.accent.blue, tolerance)

        assertEquals(0xFF / 255f, DesktopTheme.hairline.red, tolerance)
        assertEquals(DesignTokens.HAIRLINE_ALPHA, DesktopTheme.hairline.alpha, tolerance)
    }

    @Test
    fun `the window's geometry is the tokens`() {
        assertEquals(CornerSize(DesignTokens.PANEL_CORNER_RADIUS_DP.dp), DesktopTheme.panelShape.topStart)
        assertEquals(CornerSize(DesignTokens.CAPSULE_CORNER_RADIUS_DP.dp), DesktopTheme.capsuleShape.topStart)
        // The window's band is the token plus 8: the tablet's header carries one row of glyphs, the
        // window's carries the tab strip and four 36 dp capsules beside it (`DesktopTheme.headerHeight`).
        assertEquals(DesignTokens.HEADER_HEIGHT_DP.dp + 8.dp, DesktopTheme.headerHeight)
        assertEquals("a header glyph is drawn at the 20 dp it has to read at", 20.dp, DesktopTheme.headerGlyphSize)
        assertEquals(DesignTokens.STATUS_HEIGHT_DP.dp, DesktopTheme.statusHeight)
        assertEquals(DesignTokens.GLYPH_STROKE_DP, DesktopTheme.glyphStroke.value, 1e-4f)
        assertEquals(DesignTokens.PAGE_SHADOW_DP.dp, DesktopTheme.pageShadow)
        assertEquals(DesignTokens.BAND_SHADOW_DP.dp, DesktopTheme.bandShadow)
        assertEquals(DesignTokens.CAPSULE_SHADOW_DP.dp, DesktopTheme.capsuleShadow)
        assertEquals(DesignTokens.RAIL_CAPSULE_WIDTH_DP.dp, DesktopTheme.railCapsuleWidth)
        assertEquals(DesignTokens.RAIL_LANE_WIDTH_DP.dp, DesktopTheme.railLaneWidth)
        assertEquals(DesignTokens.RAIL_LANE_PADDING_DP.dp, DesktopTheme.railLanePadding)
        assertEquals(DesignTokens.RAIL_OUTER_PADDING_DP.dp, DesktopTheme.railOuterPadding)
        assertEquals(DesignTokens.RAIL_SLOT_CORNER_RADIUS_DP.dp, DesktopTheme.railSlotRadius)
        assertEquals(DesignTokens.HEADER_CONTROL_SIZE_DP.dp, DesktopTheme.headerControlSize)
        assertEquals("the chrome ink is white, as on the tablet", 1f, DesktopTheme.chromeInk.alpha, 1e-4f)
        assertEquals(1f, DesktopTheme.chromeInk.red, 1e-4f)
        assertEquals(1f, DesktopTheme.chromeInk.green, 1e-4f)
        assertEquals(1f, DesktopTheme.chromeInk.blue, 1e-4f)
    }

    /** The window sets Inter, and the three weights travel with the module (no OS font guess). */
    @Test
    fun `inter ships with the module`() {
        for (weight in listOf("regular", "medium", "bold")) {
            val resource = DesktopThemeTest::class.java.getResource("/fonts/inter_$weight.ttf")
            assertNotNull("desktop/src/main/resources/fonts/inter_$weight.ttf is missing", resource)
        }
        assertEquals("tabular figures", "tnum", DesktopTheme.numeric.fontFeatureSettings)
        assertEquals(DesktopTheme.inter, DesktopTheme.numeric.fontFamily)
    }

    /**
     * **Every** Material role is Inter, not just the window's named styles: a `Text` that names no style
     * reads the theme's own typography, and a role left at the framework's default face made the window
     * mix two typefaces — which is what made the PC's text look unlike the tablet's.
     */
    @Test
    fun `the theme's type scale is the tablet's, in Inter`() {
        val scale = DesktopTheme.typography
        val roles = listOf(
            scale.displayLarge, scale.displayMedium, scale.displaySmall,
            scale.headlineLarge, scale.headlineMedium, scale.headlineSmall,
            scale.titleLarge, scale.titleMedium, scale.titleSmall,
            scale.bodyLarge, scale.bodyMedium, scale.bodySmall,
            scale.labelLarge, scale.labelMedium, scale.labelSmall,
        )
        for (role in roles) {
            assertEquals("a Material role kept the framework's face", DesktopTheme.inter, role.fontFamily)
        }
        assertEquals(
            "the chrome's styles are roles of that scale",
            scale.labelSmall.fontSize,
            DesktopTheme.label.fontSize,
        )
        assertEquals(scale.bodyMedium.fontSize, DesktopTheme.body.fontSize)
        assertNotEquals(
            "a weight the family does not ship is not asked for",
            androidx.compose.ui.text.font.FontWeight.SemiBold,
            DesktopTheme.tab.fontWeight,
        )
    }

    /**
     * No chrome colour of its own — the sweep [ColorContractGuardTest] runs on the Android side, over
     * the files this window actually draws with.
     */
    @Test
    fun `no chrome file paints a colour of its own`() {
        val chrome = listOf(
            "DesktopShell.kt",
            "DesktopApp.kt",
            "TabStrip.kt",
            "PageSidebar.kt",
            "PageCanvas.kt",
            "OpenFailureCard.kt",
            "PageView.kt",
            "ToolStrips.kt",
            "ToolGlyphs.kt",
            "ColourFlyouts.kt",
            "DesktopInk.kt",
            "ColourPickerPanel.kt",
            "ColourPickerModel.kt",
        )
        val offenders = mutableListOf<String>()
        for (name in chrome) {
            val file = File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop/$name")
            assertTrue("File must exist: $name", file.isFile)
            for ((index, line) in file.readLines().withIndex()) {
                val code = line.substringBefore("//")
                if (code.contains("Color(0x") || code.contains("Color(0f")) {
                    offenders += "$name:${index + 1} ${line.trim()}"
                }
            }
        }
        assertTrue(
            "these lines carry a palette the tokens do not name — read DesktopTheme instead: " +
                offenders.joinToString("; "),
            offenders.isEmpty(),
        )
    }

    /**
     * The window's tool glyphs are the tablet's, by identity.
     *
     * `ToolGlyphCatalogue` is the one definition; a tool added to the window without a catalogue entry
     * does not compile (the `when` in `DesktopTool.glyph` is exhaustive), and two tools sharing one
     * picture would be a copy-paste bug the eye might not catch.
     */
    @Test
    fun `every tool draws a shared glyph of its own`() {
        val glyphs = DesktopTool.entries.map { it.glyph }
        assertEquals("two tools draw the same glyph", glyphs.size, glyphs.toSet().size)
        for (glyph in glyphs) {
            assertTrue("$glyph has no art in the shared catalogue", ToolGlyphCatalogue.parts(glyph).isNotEmpty())
        }
    }

    /**
     * No Material icon in the window.
     *
     * The desktop chrome draws its own monoline art (`docs/ui-modernization.md`), and the toolbars draw
     * the shared catalogue. A `material.icons` import creeping back in is the first step towards the two
     * shells' toolbars drifting apart again, so it fails here by name.
     */
    @Test
    fun `the window imports no Material icon`() {
        val offenders = mutableListOf<String>()
        File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop").listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (line.contains("androidx.compose.material.icons")) {
                        offenders += "${file.name}:${index + 1} ${line.trim()}"
                    }
                }
            }
        assertTrue("Material icons in the window: ${offenders.joinToString("; ")}", offenders.isEmpty())
    }

    /** The theme itself must read the tokens, not repeat them. */
    @Test
    fun `the theme is built from tokens`() {
        val theme = File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop/DesktopTheme.kt")
        val text = theme.readText()
        assertTrue("DesktopTheme must read DesignTokens", text.contains("import com.mobixournal.design.DesignTokens"))
        assertFalse(
            "DesktopTheme must not hard-code a palette of its own",
            text.contains("Color(0x"),
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
