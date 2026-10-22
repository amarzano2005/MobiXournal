package com.mobixournal.desktop

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(DesignTokens.SPRING_STIFFNESS, number("springStiffness").toFloat(), 1e-4f)
        assertEquals(DesignTokens.SPRING_DAMPING, number("springDamping").toFloat(), 1e-4f)
        assertEquals(DesignTokens.FADE_TRANSITION_MS, number("fadeTransitionMs").toInt())
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
        assertEquals(DesignTokens.HEADER_HEIGHT_DP.dp, DesktopTheme.headerHeight)
        assertEquals(DesignTokens.STATUS_HEIGHT_DP.dp, DesktopTheme.statusHeight)
        assertEquals(DesignTokens.GLYPH_STROKE_DP, DesktopTheme.glyphStroke.value, 1e-4f)
        assertEquals(DesignTokens.PAGE_SHADOW_DP.dp, DesktopTheme.pageShadow)
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
