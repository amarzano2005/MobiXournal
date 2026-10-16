package com.mobixournal.guards

import androidx.compose.ui.graphics.toArgb
import com.mobixournal.ui.theme.Amber
import com.mobixournal.ui.theme.DarkColors
import com.mobixournal.ui.theme.DeskDark
import com.mobixournal.ui.theme.DeskLight
import com.mobixournal.ui.theme.LightColors
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.OutlineDark
import com.mobixournal.ui.theme.OutlineVariantDark
import com.mobixournal.ui.theme.Purple
import com.mobixournal.ui.theme.PurpleDark
import com.mobixournal.ui.theme.TextDarkOnLight
import com.mobixournal.ui.theme.TextLightOnDark
import com.mobixournal.ui.theme.TextSecondaryOnDark
import com.mobixournal.ui.theme.TextSecondaryOnLight
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mechanical guard enforcing the UI colour palette and semantic theme contract
 * specified in `docs/colours.md` and `docs/ui-modernization.md`.
 *
 * Ensures that:
 *  1. Semantic tokens (DeskDark, DeskLight, OutlineDark, etc.) are strictly mapped to Theme roles.
 *  2. DarkColors enforces pure OLED black (#000000) for background and lowest surface container.
 *  3. LightColors enforces DeskLight (#F8F9FA) for background.
 *  4. ModernTheme glass surface and hairline border tokens are identical between the top dock and rail.
 *  5. No rogue hardcoded `Color(0x...)` or `Color(0f, ...)` appear in toolbar/chrome files.
 */
class ColorContractGuardTest {

    @Test
    fun semanticDeskTokensMatchSpecification() {
        assertEquals("DeskDark must be pure OLED black #000000", 0xFF000000.toInt(), DeskDark.toArgb())
        assertEquals("DeskLight must be paper neutral #F8F9FA", 0xFFF8F9FA.toInt(), DeskLight.toArgb())
        assertEquals("Purple brand accent", 0xFF4A2C7A.toInt(), Purple.toArgb())
        assertEquals("PurpleDark luminescent accent", 0xFFC084FC.toInt(), PurpleDark.toArgb())
        assertEquals("Amber guide accent", 0xFFFFB300.toInt(), Amber.toArgb())
    }

    @Test
    fun darkColorsEnforcesPureOledBlackAndSemanticTokens() {
        assertEquals("DarkColors.background must be DeskDark (#000000)", DeskDark, DarkColors.background)
        assertEquals("DarkColors.surfaceContainerLowest must be DeskDark", DeskDark, DarkColors.surfaceContainerLowest)
        assertEquals("DarkColors.onBackground must be TextLightOnDark", TextLightOnDark, DarkColors.onBackground)
        assertEquals("DarkColors.onSurface must be TextLightOnDark", TextLightOnDark, DarkColors.onSurface)
        assertEquals("DarkColors.onSurfaceVariant must be TextSecondaryOnDark", TextSecondaryOnDark, DarkColors.onSurfaceVariant)
        assertEquals("DarkColors.outline must be OutlineDark", OutlineDark, DarkColors.outline)
        assertEquals("DarkColors.outlineVariant must be OutlineVariantDark", OutlineVariantDark, DarkColors.outlineVariant)
        assertEquals("DarkColors.primary must be PurpleDark", PurpleDark, DarkColors.primary)
        assertEquals("DarkColors.secondary must be Amber", Amber, DarkColors.secondary)
    }

    @Test
    fun lightColorsEnforcesDeskLightAndSemanticTokens() {
        assertEquals("LightColors.background must be DeskLight (#F8F9FA)", DeskLight, LightColors.background)
        assertEquals("LightColors.onBackground must be TextDarkOnLight", TextDarkOnLight, LightColors.onBackground)
        assertEquals("LightColors.onSurface must be TextDarkOnLight", TextDarkOnLight, LightColors.onSurface)
        assertEquals("LightColors.onSurfaceVariant must be TextSecondaryOnLight", TextSecondaryOnLight, LightColors.onSurfaceVariant)
        assertEquals("LightColors.primary must be Purple", Purple, LightColors.primary)
        assertEquals("LightColors.secondary must be Amber", Amber, LightColors.secondary)
    }

    @Test
    fun modernThemeSharedTokensAreCoherent() {
        assertEquals(ModernTheme.glassDark, ModernTheme.glassSurface(isDark = true))
        assertEquals(ModernTheme.glassLight, ModernTheme.glassSurface(isDark = false))
        assertEquals(ModernTheme.hairlineBorder, ModernTheme.hairlineBorder(isDark = true))
        assertEquals(ModernTheme.hairlineBorderLight, ModernTheme.hairlineBorder(isDark = false))
    }

    @Test
    fun noAdHocHairlineLiteralsInToolbarChrome() {
        val root = repoRoot()
        val checkedFiles = listOf(
            "app/src/main/java/com/mobixournal/ui/EditorRegions.kt",
            "app/src/main/java/com/mobixournal/ui/SideToolbar.kt",
            "app/src/main/java/com/mobixournal/ui/TopBarIndicator.kt",
            "app/src/main/java/com/mobixournal/ui/StemRailSection.kt",
            "app/src/main/java/com/mobixournal/ui/TabStrip.kt",
        )
        for (relPath in checkedFiles) {
            val file = File(root, relPath)
            assertTrue("File must exist: $relPath", file.isFile)
            val text = file.readText()
            assertFalse(
                "$relPath contains ad-hoc Color(0f, 0f, 0f, 0.12f) — use ModernTheme.hairlineBorder(isDark)",
                text.contains("Color(0f, 0f, 0f, 0.12f)"),
            )
        }
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
