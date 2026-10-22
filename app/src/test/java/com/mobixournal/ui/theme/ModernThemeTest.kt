package com.mobixournal.ui.theme

import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit test pinning the modern design tokens in [ModernTheme] to the values
 * documented in `docs/ui-modernization.md`.
 *
 * Ensures that any silent drift between the documentation and the codebase fails
 * the build.
 */
class ModernThemeTest {

    @Test
    fun tokensMatchExplicitDocumentedValues() {
        assertEquals("rgba(22, 22, 24, 0.85)", ModernTheme.GLASS_DARK_RAW)
        assertEquals("rgba(255, 255, 255, 0.88)", ModernTheme.GLASS_LIGHT_RAW)
        assertEquals("rgba(255, 255, 255, 0.12)", ModernTheme.HAIRLINE_BORDER_RAW)
        assertEquals("0.88", ModernTheme.FLOATING_PANEL_ALPHA_RAW)

        // Color components (8-bit channel quantization tolerance: 1/255 is ~0.0039)
        val channelTolerance = 0.005f
        assertTrue(abs(ModernTheme.glassDark.red - (22f / 255f)) < channelTolerance)
        assertTrue(abs(ModernTheme.glassDark.green - (22f / 255f)) < channelTolerance)
        assertTrue(abs(ModernTheme.glassDark.blue - (24f / 255f)) < channelTolerance)
        assertTrue(abs(ModernTheme.glassDark.alpha - 0.85f) < channelTolerance)

        assertTrue(abs(ModernTheme.glassLight.red - 1f) < channelTolerance)
        assertTrue(abs(ModernTheme.glassLight.green - 1f) < channelTolerance)
        assertTrue(abs(ModernTheme.glassLight.blue - 1f) < channelTolerance)
        assertTrue(abs(ModernTheme.glassLight.alpha - 0.88f) < channelTolerance)

        assertTrue(abs(ModernTheme.hairlineBorder.red - 1f) < channelTolerance)
        assertTrue(abs(ModernTheme.hairlineBorder.green - 1f) < channelTolerance)
        assertTrue(abs(ModernTheme.hairlineBorder.blue - 1f) < channelTolerance)
        assertTrue(abs(ModernTheme.hairlineBorder.alpha - 0.12f) < channelTolerance)

        // Geometry dimensions
        assertEquals(24.dp, ModernTheme.blurRadiusDp)
        assertEquals(24, ModernTheme.BLUR_RADIUS_DP_VAL)

        assertEquals(16.dp, ModernTheme.capsuleCornerRadiusDp)
        assertEquals(16, ModernTheme.CAPSULE_CORNER_RADIUS_DP_VAL)

        assertEquals(40.dp, ModernTheme.compactButtonSizeDp)
        assertEquals(40, ModernTheme.COMPACT_BUTTON_SIZE_DP_VAL)

        assertEquals(1.0.dp, ModernTheme.hairlineStrokeWidthDp)
        assertEquals(1.0, ModernTheme.HAIRLINE_STROKE_WIDTH_DP_VAL, 0.0001)

        // Animation tokens
        assertEquals(380f, ModernTheme.springStiffness, 0.0001f)
        assertEquals(28f, ModernTheme.springDamping, 0.0001f)
        assertEquals(150, ModernTheme.fadeTransitionMs)
    }

    @Test
    fun derivedConstructsAndHelpersMatch() {
        assertEquals(ModernTheme.glassDark, ModernTheme.glassSurface(isDark = true))
        assertEquals(ModernTheme.glassLight, ModernTheme.glassSurface(isDark = false))

        // A floating surface is its bar's glass with only the alpha turned down: same RGB, less see-through.
        val panelDark = ModernTheme.floatingSurface(isDark = true)
        assertEquals(ModernTheme.glassDark.red, panelDark.red)
        assertEquals(ModernTheme.glassDark.green, panelDark.green)
        assertEquals(ModernTheme.glassDark.blue, panelDark.blue)
        // One 8-bit step of tolerance: these colours pack into 8 bits per channel (0.93f → 237/255).
        assertEquals(ModernTheme.floatingPanelAlpha, panelDark.alpha, 0.005f)
        assertEquals(ModernTheme.FLOATING_PANEL_ALPHA_VAL, panelDark.alpha.toDouble(), 0.005)
        val panelLight = ModernTheme.floatingSurface(isDark = false)
        assertEquals(ModernTheme.glassLight.red, panelLight.red)
        assertEquals(ModernTheme.glassLight.green, panelLight.green)
        assertEquals(ModernTheme.glassLight.blue, panelLight.blue)
        assertEquals(ModernTheme.floatingPanelAlpha, panelLight.alpha, 0.005f)

        // Every floating surface is cut with one corner, and it is a documented token.
        assertEquals(20.dp, ModernTheme.panelCornerRadiusDp)
        assertEquals(20, ModernTheme.PANEL_CORNER_RADIUS_DP_VAL)

        val spring = ModernTheme.springSpec<Float>()
        assertEquals(ModernTheme.springStiffness, spring.stiffness, 0.0001f)
        assertEquals(ModernTheme.springDampingRatio, spring.dampingRatio, 0.0001f)

        val fade = ModernTheme.fadeSpec<Float>()
        assertEquals(ModernTheme.fadeTransitionMs, fade.durationMillis)
    }

    @Test
    fun tokensMatchUiModernizationDocSpecification() {
        val docFile = File(repoRoot(), "docs/ui-modernization.md")
        assertTrue("docs/ui-modernization.md must exist", docFile.isFile)

        val text = docFile.readText()
        val jsonPattern = Regex("""(?s)## Design token specification.*?```json\s*(.*?)\s*```""")
        val match = jsonPattern.find(text)
        assertTrue("JSON specification block must be present in docs/ui-modernization.md", match != null)

        val jsonContent = match!!.groupValues[1]

        fun extractString(key: String): String {
            val r = Regex(""""$key"\s*:\s*"([^"]+)"""")
            val m = r.find(jsonContent)
            return m?.groupValues?.get(1) ?: error("Key $key not found in doc json: $jsonContent")
        }

        fun extractNumber(key: String): String {
            val r = Regex(""""$key"\s*:\s*([0-9.]+)""")
            val m = r.find(jsonContent)
            return m?.groupValues?.get(1) ?: error("Key $key not found in doc json: $jsonContent")
        }

        // Verify strings in doc match ModernTheme
        assertEquals(ModernTheme.GLASS_DARK_RAW, extractString("glassDark"))
        assertEquals(ModernTheme.GLASS_LIGHT_RAW, extractString("glassLight"))
        assertEquals(ModernTheme.HAIRLINE_BORDER_RAW, extractString("hairlineBorder"))
        assertEquals(ModernTheme.FLOATING_PANEL_ALPHA_RAW, extractNumber("floatingPanelAlpha"))
        assertEquals(ModernTheme.PANEL_CORNER_RADIUS_DP_VAL, extractNumber("panelCornerRadiusDp").toInt())

        // Verify numeric tokens in doc match ModernTheme
        assertEquals(ModernTheme.BLUR_RADIUS_DP_VAL, extractNumber("blurRadiusDp").toInt())
        assertEquals(ModernTheme.CAPSULE_CORNER_RADIUS_DP_VAL, extractNumber("capsuleCornerRadiusDp").toInt())
        assertEquals(ModernTheme.COMPACT_BUTTON_SIZE_DP_VAL, extractNumber("compactButtonSizeDp").toInt())
        assertEquals(ModernTheme.HAIRLINE_STROKE_WIDTH_DP_VAL, extractNumber("hairlineStrokeWidthDp").toDouble(), 0.0001)
        assertEquals(ModernTheme.springStiffness, extractNumber("springStiffness").toFloat(), 0.0001f)
        assertEquals(ModernTheme.springDamping, extractNumber("springDamping").toFloat(), 0.0001f)
        assertEquals(ModernTheme.fadeTransitionMs, extractNumber("fadeTransitionMs").toInt())

        // The raised glass and the rail geometry the desktop reads from the same block.
        assertEquals(DesignTokens.BAND_SHADOW_DP, extractNumber("bandShadowDp").toInt())
        assertEquals(DesignTokens.CAPSULE_SHADOW_DP, extractNumber("capsuleShadowDp").toInt())
        assertEquals(ModernTheme.SHEEN_ALPHA_TOP_VAL, extractNumber("sheenAlphaTop").toFloat(), 0.0001f)
        assertEquals(ModernTheme.SHEEN_ALPHA_BOTTOM_VAL, extractNumber("sheenAlphaBottom").toFloat(), 0.0001f)
        assertEquals(ModernTheme.railSlotCornerRadiusDp.value, extractNumber("railSlotCornerRadiusDp").toFloat(), 0.0001f)
        assertEquals(ModernTheme.railCapsuleWidthDp.value, extractNumber("railCapsuleWidthDp").toFloat(), 0.0001f)
        assertEquals(ModernTheme.railLanePaddingDp.value, extractNumber("railLanePaddingDp").toFloat(), 0.0001f)
        assertEquals(ModernTheme.railLaneWidthDp.value, extractNumber("railLaneWidthDp").toFloat(), 0.0001f)
        assertEquals(ModernTheme.railOuterPaddingDp.value, extractNumber("railOuterPaddingDp").toFloat(), 0.0001f)
        assertEquals(ModernTheme.headerControlSizeDp.value, extractNumber("headerControlSizeDp").toFloat(), 0.0001f)
        assertEquals(ModernTheme.TOOL_GLYPH_GRID_VAL, extractNumber("toolGlyphGrid").toFloat(), 0.0001f)
        assertEquals(ModernTheme.TOOL_GLYPH_STROKE_UNITS_VAL, extractNumber("toolGlyphStrokeUnits").toFloat(), 0.0001f)
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
