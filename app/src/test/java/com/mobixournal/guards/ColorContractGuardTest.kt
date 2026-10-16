package com.mobixournal.guards

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.TOP_BAR_DOCK_CORNER
import com.mobixournal.ui.theme.ActiveSlotDark
import com.mobixournal.ui.theme.ActiveSlotLight
import com.mobixournal.ui.theme.Amber
import com.mobixournal.ui.theme.AppShapes
import com.mobixournal.ui.theme.Blue
import com.mobixournal.ui.theme.BlueDark
import com.mobixournal.ui.theme.DarkColors
import com.mobixournal.ui.theme.DeskDark
import com.mobixournal.ui.theme.DeskLight
import com.mobixournal.ui.theme.LightColors
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.NeutralContainerDark
import com.mobixournal.ui.theme.NeutralContainerLight
import com.mobixournal.ui.theme.NeutralContainerLightHigh
import com.mobixournal.ui.theme.NeutralContainerLightHighest
import com.mobixournal.ui.theme.NeutralContainerLightLow
import com.mobixournal.ui.theme.OutlineDark
import com.mobixournal.ui.theme.OutlineLight
import com.mobixournal.ui.theme.OutlineVariantDark
import com.mobixournal.ui.theme.OutlineVariantLight
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
 *  3. LightColors enforces DeskLight (#F8F9FA) for background **and** a neutral container ramp, so a
 *     role left at Material 3's baseline cannot paint a lavender panel. See
 *     [noContainerInEitherSchemeCarriesAVioletWash].
 *  4. ModernTheme glass surface and hairline border tokens are identical between the top dock and rail,
 *     and the pop-up's fill is the bars' own glass ([thePopUpIsCutFromTheBarsOwnGlass]).
 *  5. No rogue hardcoded `Color(0x...)` or `Color(0f, ...)` appear in toolbar/chrome files.
 */
class ColorContractGuardTest {

    @Test
    fun semanticDeskTokensMatchSpecification() {
        assertEquals("DeskDark must be pure OLED black #000000", 0xFF000000.toInt(), DeskDark.toArgb())
        assertEquals("DeskLight must be paper neutral #F8F9FA", 0xFFF8F9FA.toInt(), DeskLight.toArgb())
        assertEquals("Blue brand accent", 0xFF2563EB.toInt(), Blue.toArgb())
        assertEquals("BlueDark luminescent accent", 0xFF60A5FA.toInt(), BlueDark.toArgb())
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
        assertEquals("DarkColors.primary must be BlueDark", BlueDark, DarkColors.primary)
        assertEquals("DarkColors.primaryContainer must be ActiveSlotDark", com.mobixournal.ui.theme.ActiveSlotDark, DarkColors.primaryContainer)
        assertEquals("DarkColors.surfaceContainer must be NeutralContainerDark", com.mobixournal.ui.theme.NeutralContainerDark, DarkColors.surfaceContainer)
        assertEquals("DarkColors.secondary must be BlueDark", BlueDark, DarkColors.secondary)
    }

    @Test
    fun lightColorsEnforcesDeskLightAndSemanticTokens() {
        assertEquals("LightColors.background must be DeskLight (#F8F9FA)", DeskLight, LightColors.background)
        assertEquals("LightColors.onBackground must be TextDarkOnLight", TextDarkOnLight, LightColors.onBackground)
        assertEquals("LightColors.onSurface must be TextDarkOnLight", TextDarkOnLight, LightColors.onSurface)
        assertEquals("LightColors.onSurfaceVariant must be TextSecondaryOnLight", TextSecondaryOnLight, LightColors.onSurfaceVariant)
        assertEquals("LightColors.primary must be Blue", Blue, LightColors.primary)
        assertEquals("LightColors.secondary must be Blue", Blue, LightColors.secondary)
    }

    /**
     * The light theme's *containers*, role by role.
     *
     * The dark scheme had every one of these pinned and the light one had almost none, so a light-theme
     * panel came out of Material 3's baseline instead: `surfaceContainer` `#F3EDF7` (the pop-up panel),
     * `primaryContainer` `#EADDFF` (a picked tool in a picker submenu) and `secondaryContainer`
     * `#E8DEF8` (a selected settings row) — all violet in a palette that has no violet. This is the
     * missing half of the contract, pinned by value so a re-introduced default fails the build.
     */
    @Test
    fun lightColorsPinTheWholeNeutralContainerRamp() {
        assertEquals("LightColors.surfaceContainer must be NeutralContainerLight", NeutralContainerLight, LightColors.surfaceContainer)
        assertEquals("LightColors.surfaceContainerLow", NeutralContainerLightLow, LightColors.surfaceContainerLow)
        assertEquals("LightColors.surfaceContainerHigh", NeutralContainerLightHigh, LightColors.surfaceContainerHigh)
        assertEquals("LightColors.surfaceContainerHighest", NeutralContainerLightHighest, LightColors.surfaceContainerHighest)
        assertEquals("LightColors.surfaceContainerLowest", Color.White, LightColors.surfaceContainerLowest)
        assertEquals("LightColors.surfaceVariant", NeutralContainerLightHigh, LightColors.surfaceVariant)
        assertEquals("LightColors.surfaceTint", NeutralContainerLight, LightColors.surfaceTint)
        assertEquals("LightColors.surfaceDim", NeutralContainerLightHighest, LightColors.surfaceDim)
        assertEquals("LightColors.primaryContainer must be the steel ActiveSlotLight", ActiveSlotLight, LightColors.primaryContainer)
        assertEquals("LightColors.secondaryContainer must be the steel ActiveSlotLight", ActiveSlotLight, LightColors.secondaryContainer)
        assertEquals("LightColors.onPrimaryContainer", TextDarkOnLight, LightColors.onPrimaryContainer)
        assertEquals("LightColors.outline", OutlineLight, LightColors.outline)
        assertEquals("LightColors.outlineVariant", OutlineVariantLight, LightColors.outlineVariant)
    }

    @Test
    fun darkColorsPinTheWholeNeutralContainerRamp() {
        assertEquals("DarkColors.surfaceContainerHigh", Color(0xFF2C2C30), DarkColors.surfaceContainerHigh)
        assertEquals("DarkColors.surfaceContainerLow", Color(0xFF18181A), DarkColors.surfaceContainerLow)
        assertEquals("DarkColors.surfaceTint must be neutral, never the accent", NeutralContainerDark, DarkColors.surfaceTint)
        assertEquals("DarkColors.secondaryContainer must be the steel ActiveSlotDark", ActiveSlotDark, DarkColors.secondaryContainer)
        assertEquals("DarkColors.tertiaryContainer", NeutralContainerDark, DarkColors.tertiaryContainer)
        assertEquals("DarkColors.onPrimary must be the true-black ink for the luminous accent", com.mobixournal.ui.theme.OnAccentDark, DarkColors.onPrimary)
    }

    /**
     * **No container, in either scheme, may carry a violet wash.**
     *
     * This is the guard whose absence let the bug through: the palette is black, grey and one blue
     * accent, but a Material 3 role nobody had pinned was free to be `#F3EDF7` — a *violet-tinted*
     * neutral that reads as a blue-grey panel. Asserting a handful of roles by hand (which is what
     * [lightColorsEnforcesDeskLightAndSemanticTokens] did) cannot see the roles nobody named; sweeping
     * every container can.
     *
     * "Neutral" is measured as a **green deficit**: in Material's violet neutrals the green channel
     * sits far below both neighbours (`#EADDFF` is 255 − 221 = 34 away), while the charcoal ramp this
     * design ships never leans past [MAX_GREEN_DEFICIT] (`#38383E` is 6). The blue accent is allowed by
     * name; the error family by being red-dominant, since error red is semantic and not chrome.
     */
    @Test
    fun noContainerInEitherSchemeCarriesAVioletWash() {
        assertContainersAreNeutral("LightColors", LightColors)
        assertContainersAreNeutral("DarkColors", DarkColors)
    }

    /**
     * The one menu material is cut from the bars' own glass, not from a role of its own.
     *
     * The pop-up painted `rememberToolbarColor` (`surfaceContainer`) while the rail, the header slab
     * and the dock painted the frosted glass — so in the light theme a menu hung from a near-white bar
     * as a lavender panel. Both now read one function, and this pins that: the fill is
     * `rememberGlassSurfaceColor()`, and the `surfaceContainer` import is gone. A colour assertion
     * cannot see a call site, so the source tree is read instead, exactly as `ToolbarMenuGuardTest`
     * reads it for the `DropdownMenu` call.
     */
    @Test
    fun thePopUpIsCutFromTheBarsOwnGlass() {
        val file = File(repoRoot(), "app/src/main/java/com/mobixournal/ui/ToolbarPopup.kt")
        assertTrue("File must exist: ${file.path}", file.isFile)
        val text = file.readText()
        assertTrue(
            "the pop-up's fill must be the bars' own glass at the floating alpha: " +
                "`val toolbar = rememberFloatingSurfaceColor()`",
            text.contains("val toolbar = rememberFloatingSurfaceColor()"),
        )
        assertFalse(
            "…and must not fall back to the surfaceContainer role it used to read",
            text.contains("import com.mobixournal.ui.theme.rememberToolbarColor"),
        )
    }

    /**
     * The panel is the **bar's colour** at a **higher** opacity — one material, read at two fade
     * levels — rather than a colour of its own.
     *
     * Both halves matter. Sharing the RGB is what makes a menu read as part of the bar it hangs from
     * (the lavender `surfaceContainer` failure); the higher alpha is what makes the rows on it
     * readable (see [thePopUpPanelKeepsItsInkLegibleOverAnyBackdrop]). A future "let me just make the
     * panel a bit lighter" edit has to break one of these two assertions first.
     */
    @Test
    fun thePanelIsTheBarsColourWithLessSeeThrough() {
        for (isDark in listOf(false, true)) {
            val where = if (isDark) "dark" else "light"
            val bar = ModernTheme.glassSurface(isDark)
            val panel = ModernTheme.floatingSurface(isDark)
            assertEquals("the $where panel must be the bar's colour (red)", bar.red, panel.red)
            assertEquals("the $where panel must be the bar's colour (green)", bar.green, panel.green)
            assertEquals("the $where panel must be the bar's colour (blue)", bar.blue, panel.blue)
            assertTrue(
                "the $where panel must be *less* transparent than the bar it hangs from",
                panel.alpha > bar.alpha,
            )
            assertTrue(
                "…but not opaque: the little that still comes through is what ties it to the bar",
                panel.alpha < 1f,
            )
        }
    }

    /**
     * A translucent panel is only usable if its own ink still reads over whatever is behind it.
     *
     * The pop-up hangs over the page, so the panel is composited over the two extremes that can sit
     * under it — a white sheet and heavy black ink — and both of its ink roles must clear 4.5:1, the
     * WCAG AA floor for body text. This is the measurement behind `ModernTheme.floatingPanelAlpha`: at
     * the bars' own 0.85 the dark theme's secondary ink comes out at 4.41:1 over a white page, i.e.
     * below the floor — "a bit too see-through" as a number instead of an impression.
     */
    @Test
    fun thePopUpPanelKeepsItsInkLegibleOverAnyBackdrop() {
        for (isDark in listOf(false, true)) {
            val where = if (isDark) "dark" else "light"
            val scheme = if (isDark) DarkColors else LightColors
            val panel = ModernTheme.floatingSurface(isDark)
            for ((backdropName, backdrop) in listOf("a white sheet" to Color.White, "black ink" to Color.Black)) {
                val composited = panel.compositeOver(backdrop)
                val inks = listOf(
                    "onSurface" to scheme.onSurface,
                    "onSurfaceVariant" to scheme.onSurfaceVariant,
                )
                for ((role, ink) in inks) {
                    val ratio = contrastRatio(ink, composited)
                    assertTrue(
                        "the $where menu's $role over $backdropName is %.2f:1 — below the 4.5:1 "
                            .format(ratio) + "body-text floor, so the panel is too see-through",
                        ratio >= 4.5,
                    )
                }
            }
        }
    }

    /**
     * A dialog and a menu turn at the **same** corner.
     *
     * Material's `AlertDialog` takes its shape from `Shapes.extraLarge` — `DialogTokens.ContainerShape`
     * is `ShapeKeyTokens.CornerExtraLarge`, read out of the material3 1.2.1 bytecode rather than assumed
     * — so re-cutting that one role in [AppShapes] is what makes *every* dialog in the app turn at the
     * floating corner, with no call site passing a shape. This pins the value, and pins it to the number
     * the menus and the dock cap already use, so the three cannot drift into three radii.
     */
    @Test
    fun dialogsAndMenusShareOneFloatingCorner() {
        assertEquals("the floating corner", 20.dp, ModernTheme.panelCornerRadiusDp)
        assertEquals(
            "…and the documented value",
            ModernTheme.PANEL_CORNER_RADIUS_DP_VAL,
            ModernTheme.panelCornerRadiusDp.value.toInt(),
        )
        assertEquals(
            "a dialog's corner must be the floating corner (Shapes.extraLarge)",
            CornerSize(ModernTheme.panelCornerRadiusDp),
            AppShapes.extraLarge.topStart,
        )
        assertEquals(
            "the menus and the dock cap must use that same corner",
            ModernTheme.panelCornerRadiusDp,
            TOP_BAR_DOCK_CORNER,
        )
    }

    /**
     * **Every** floating surface paints the one material.
     *
     * The palette guard above can only see the theme's roles; whether a *call site* asked for them is a
     * property of the source tree, exactly as with `ToolbarMenuGuardTest`. These are the surfaces the user
     * meets over the page — the menus and submenus, the action bars that come up with a selection
     * (cut/copy, table, spline, PDF text, region), the page/zoom/scroll badges, the transfer overlay, the
     * two hand-rolled dialogs and the search-indexing card — and each must read
     * `rememberFloatingSurfaceColor()` rather than a container role of its own.
     *
     * A presence check alone would pass a file that keeps one surface wired and adds a second one with an
     * ad-hoc fill, so the fills this replaced are banned by name in the same files (normalised to `\n`
     * first, since the tree is CRLF on Windows and LF elsewhere).
     */
    @Test
    fun everyFloatingSurfacePaintsTheFloatingMaterial() {
        val floating = mapOf(
            "ToolbarPopup.kt" to listOf("import com.mobixournal.ui.theme.rememberToolbarColor"),
            "EditorActionBars.kt" to listOf("color = MaterialTheme.colorScheme.surface,\n"),
            "PageCounter.kt" to listOf("surfaceContainerHighest.copy(alpha = 0.85f)"),
            "ScrollThumb.kt" to listOf("color = MaterialTheme.colorScheme.surface,\n"),
            "EditorOverlays.kt" to listOf("color = MaterialTheme.colorScheme.surface,\n"),
            "EditorRegions.kt" to listOf(
                "shape = RoundedCornerShape(16.dp),\n            color = MaterialTheme.colorScheme.surfaceContainerHigh,",
            ),
            "ShortcutsSection.kt" to listOf("color = MaterialTheme.colorScheme.surface,\n"),
            "OnboardingDialog.kt" to listOf("color = MaterialTheme.colorScheme.surface,\n"),
        )
        for ((fileName, retiredFills) in floating) {
            val file = File(repoRoot(), "app/src/main/java/com/mobixournal/ui/$fileName")
            assertTrue("File must exist: $fileName", file.isFile)
            val text = file.readText().replace("\r\n", "\n")
            assertTrue(
                "$fileName must paint its floating surface with rememberFloatingSurfaceColor()",
                text.contains("rememberFloatingSurfaceColor()"),
            )
            for (retired in retiredFills) {
                assertFalse(
                    "$fileName still carries a floating fill of its own: `${retired.trim()}` — a " +
                        "floating surface takes the one material, it does not pick a container role",
                    text.contains(retired),
                )
            }
        }
    }

    /** WCAG 2.1 relative-luminance contrast ratio. */
    private fun contrastRatio(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
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
            "app/src/main/java/com/mobixournal/ui/EditorOverflowMenu.kt",
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

    /** The container roles of one scheme, as (role name, colour) pairs, for the sweep below. */
    private fun containerRoles(scheme: ColorScheme): List<Pair<String, Color>> = listOf(
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "surfaceTint" to scheme.surfaceTint,
        "surfaceBright" to scheme.surfaceBright,
        "surfaceDim" to scheme.surfaceDim,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "primaryContainer" to scheme.primaryContainer,
        "secondaryContainer" to scheme.secondaryContainer,
        "tertiaryContainer" to scheme.tertiaryContainer,
        "inverseSurface" to scheme.inverseSurface,
        "outline" to scheme.outline,
        "outlineVariant" to scheme.outlineVariant,
    )

    /**
     * The sweep is the guard, so it is guarded: the Material 3 baseline neutrals it exists to reject
     * must actually be rejected, and the charcoal ramp the design ships must not be. Without this, a
     * threshold accidentally loosened (or a metric that measures nothing) would pass silently — the
     * failure mode that let the lavender panel onto the screen in the first place.
     */
    @Test
    fun theVioletWashMetricFiresOnTheMaterialBaselineAndPassesTheShippedRamp() {
        val materialBaselineNeutrals = listOf(
            0xFFF3EDF7.toInt(), // surfaceContainer
            0xFFF7F2FA.toInt(), // surfaceContainerLow
            0xFFECE6F0.toInt(), // surfaceContainerHigh
            0xFFE6E0E9.toInt(), // surfaceContainerHighest
            0xFFE7E0EC.toInt(), // surfaceVariant
            0xFFEADDFF.toInt(), // primaryContainer
            0xFFE8DEF8.toInt(), // secondaryContainer
            0xFFD0BCFF.toInt(), // inversePrimary
            0xFF79747E.toInt(), // outline
            0xFFCAC4D0.toInt(), // outlineVariant
            0xFF4A4458.toInt(), // dark secondaryContainer
        )
        for (argb in materialBaselineNeutrals) {
            assertTrue(
                "the metric must reject Material 3's baseline ${hex(argb)} " +
                    "(green deficit ${greenDeficit(argb)})",
                greenDeficit(argb) > MAX_GREEN_DEFICIT,
            )
        }

        val shippedRamp = listOf(
            0xFF000000.toInt(), 0xFF161618.toInt(), 0xFF18181A.toInt(), 0xFF252528.toInt(),
            0xFF2C2C30.toInt(), 0xFF38383E.toInt(), 0xFF424248.toInt(),
            0xFFF8F9FA.toInt(), 0xFFEAEDF0.toInt(), 0xFFF4F6F8.toInt(), 0xFFE3E7EB.toInt(),
            0xFFDCE1E6.toInt(), 0xFFDEE3E7.toInt(), 0xFF767B7F.toInt(), 0xFFC4C9CD.toInt(),
        )
        for (argb in shippedRamp) {
            assertTrue(
                "the shipped ramp must pass: ${hex(argb)} has green deficit ${greenDeficit(argb)}",
                greenDeficit(argb) <= MAX_GREEN_DEFICIT,
            )
        }
    }

    private fun assertContainersAreNeutral(schemeName: String, scheme: ColorScheme) {
        for ((role, color) in containerRoles(scheme)) {
            if (color == Blue || color == BlueDark) continue
            val argb = color.toArgb()
            assertTrue(
                "$schemeName.$role is not a neutral grey: its green channel sits ${greenDeficit(argb)} " +
                    "below the higher of red/blue, which is a violet wash (${hex(argb)}) — the palette is " +
                    "black, grey and blue",
                greenDeficit(argb) <= MAX_GREEN_DEFICIT,
            )
        }
    }

    /** How far a colour's green channel sits below the higher of its red/blue: the violet tell. */
    private fun greenDeficit(argb: Int): Int {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return maxOf(red, blue) - green
    }

    private fun hex(argb: Int): String = "#%08X".format(argb)

    private companion object {
        /**
         * How far the green channel may sit below the higher of red/blue and still read as grey.
         * Set to exactly the lean the shipped charcoal ramp already has (`#38383E`, `#424248`): the
         * guard can only get stricter from here, never looser without this number visibly moving.
         */
        const val MAX_GREEN_DEFICIT = 6
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
