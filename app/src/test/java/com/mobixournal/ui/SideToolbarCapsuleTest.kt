package com.mobixournal.ui

import androidx.compose.ui.unit.dp
import com.mobixournal.render.CanvasChrome
import com.mobixournal.ui.theme.ModernTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that the floating rail's dimensions and styling adhere strictly to [ModernTheme]
 * tokens (16dp capsule corner radius, 1px hairline border at 10-15% alpha, 40dp compact buttons,
 * 85-90% glass opacity).
 */
class SideToolbarCapsuleTest {

    @Test
    fun railDimensionsMatchModernTokens() {
        assertEquals("Rail button slot must match ModernTheme compact button size", 40.dp, SideToolbarModernSlot)
        assertEquals("ModernTheme compact button size must be 40dp", 40.dp, ModernTheme.compactButtonSizeDp)
        assertEquals("Rail width must be 52dp (40dp button + 2x6dp padding)", 52.dp, SideToolbarModernWidth)
        assertEquals("Rail horizontal margin must be 6dp", 6.dp, SideToolbarModernPadding)
        assertEquals("Total rail width must match 64dp indicator width", 64.dp, SideToolbarModernTotalWidth)
    }

    @Test
    fun glassTokensMatchModernThemeSpecification() {
        assertEquals("Capsule corner radius must be 16dp", 16.dp, ModernTheme.capsuleCornerRadiusDp)
        assertEquals("Hairline border width must be 1dp", 1.0.dp, ModernTheme.hairlineStrokeWidthDp)
        assertEquals("Dark glass opacity must be 85%", 0.85f, ModernTheme.glassDark.alpha, 0.01f)
        assertEquals("Light glass opacity must be 88%", 0.88f, ModernTheme.glassLight.alpha, 0.01f)
        assertEquals("Hairline border alpha must be 12%", 0.12f, ModernTheme.hairlineBorder.alpha, 0.01f)
    }

    @Test
    fun canvasChromeDefaultBackdropIsOledBlack() {
        assertEquals(0xFF000000.toInt(), CanvasChrome.BACKDROP)
        assertEquals(0x26FFFFFF, CanvasChrome.PAGE_OUTLINE)
    }

    /**
     * The rail docks to a **vertical edge** only, and the two values are the two hands. The `TOP`/`BOTTOM`
     * centre dockings that used to sit beside them are retired (2026-10-13): a toolbar along the bottom is
     * what the Compact *layout* means, and the header owns the top band. Pinning the list rather than a
     * count is what makes a third position a deliberate edit here, instead of a value nobody can place.
     */
    @Test
    fun toolbarPositionOffersTheTwoVerticalEdges() {
        assertEquals("Left", ToolbarPosition.LEFT.label)
        assertEquals("Right", ToolbarPosition.RIGHT.label)
        assertEquals(
            "the rail docks to a vertical edge only",
            listOf(ToolbarPosition.LEFT, ToolbarPosition.RIGHT),
            ToolbarPosition.entries.toList(),
        )
    }
}
