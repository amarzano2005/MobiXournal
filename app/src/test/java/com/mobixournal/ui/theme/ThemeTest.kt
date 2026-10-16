package com.mobixournal.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit test pinning the true-black dark mode colour scheme tokens to the values
 * documented in Section 3 of `docs/ui-modernization.md`.
 */
class ThemeTest {

    @Test
    fun darkColorsMatchTrueBlackAndCharcoalSpecification() {
        // True OLED black background backing
        assertEquals(Color(0xFF000000), DarkColors.background)

        // Neutral charcoal surfaces
        assertEquals(Color(0xFF161618), DarkColors.surface)
        assertEquals(NeutralContainerDark, DarkColors.surfaceVariant)
        assertEquals(NeutralContainerDark, DarkColors.surfaceContainer)
        assertEquals(ActiveSlotDark, DarkColors.primaryContainer)
        assertEquals(Color(0xFF18181A), DarkColors.surfaceContainerLow)
        assertEquals(Color(0xFF2C2C30), DarkColors.surfaceContainerHigh)
        assertEquals(Color(0xFF38383E), DarkColors.surfaceContainerHighest)
        assertEquals(Color(0xFF000000), DarkColors.surfaceContainerLowest)

        // Neutral onSurface / onBackground contrast
        assertEquals(Color(0xFFEDEDED), DarkColors.onBackground)
        assertEquals(Color(0xFFEDEDED), DarkColors.onSurface)
        assertEquals(Color(0xFFA0A0A0), DarkColors.onSurfaceVariant)

        // Subtle hairline borders
        assertEquals(Color(0xFF383838), DarkColors.outline)
        assertEquals(Color(0xFF262626), DarkColors.outlineVariant)

        // Luminescent micro-highlight primary accent
        assertEquals(Color(0xFFC084FC), DarkColors.primary)
        assertEquals(Color(0xFFFFB300), DarkColors.secondary)
    }

    @Test
    fun lightColorsPreserveExpectedBaselines() {
        assertEquals(Color(0xFFF8F9FA), LightColors.background)
        assertEquals(Color.White, LightColors.surface)
        assertEquals(Purple, LightColors.primary)
        assertEquals(Amber, LightColors.secondary)
    }
}
