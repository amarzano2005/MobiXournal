package com.mobixournal.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.design.DesignTokens

/**
 * The desktop window's theme: [DesignTokens] turned into Compose colours, shapes and type.
 *
 * The window is **dark-first** (`docs/ui-modernization.md`): a true-black desk, the app's frosted glass
 * for the bands, one luminescent accent for what is active. Nothing here invents a colour — a chrome
 * value the tokens do not name is a value the two shells would disagree about, and `DesktopThemeTest`
 * reads this file as the only place a chrome colour may be written.
 *
 * The typeface is **Inter**, the same three weights the Android module ships, packaged beside this
 * module's resources (`desktop/src/main/resources/fonts/`) so the window and the tablet set the same
 * letters. Figures are **tabular** ([numeric]): a page counter that changes width as it counts is a
 * counter you notice.
 */
object DesktopTheme {

    /** The desk the sheet lies on: true OLED black, never a charcoal. */
    val desk: Color = tokenColour(DesignTokens.DESK_RGB)

    /** The bands' fill — the header, the sidebar and the status bar — over the desk. */
    val glass: Color = tokenColour(DesignTokens.GLASS_DARK_RGB, DesignTokens.GLASS_DARK_ALPHA)

    /**
     * What floats over the page: the bands' own RGB with the see-through turned down to
     * `floatingPanelAlpha`, so a message or a menu is the same material as the bar it belongs to.
     */
    val floating: Color = glass.copy(alpha = DesignTokens.FLOATING_PANEL_ALPHA)

    /**
     * The failed-open card's fill: [glass] at **full** opacity.
     *
     * It is the one deliberate departure from the floating material, and it is about geometry rather
     * than taste: the card sits at the top of the canvas and therefore straddles the edge where the desk
     * meets the sheet, so a translucent fill came out a tone lighter over the paper than over the black
     * and the panel read as two materials split down its middle. A message to be read once is solid.
     */
    val panel: Color = tokenColour(DesignTokens.GLASS_DARK_RGB)

    /** One edge, one pixel, 12 % white — the rim of a floating surface, never a second rule on a band. */
    val hairline: Color = tokenColour(DesignTokens.HAIRLINE_RGB, DesignTokens.HAIRLINE_ALPHA)

    /** The luminescent accent: what is active, never a wash. */
    val accent: Color = tokenColour(DesignTokens.ACCENT_DARK_RGB)

    /** The steel a *picked* slot wears — a selected tab, a held toggle. */
    val activeSlot: Color = tokenColour(DesignTokens.ACTIVE_SLOT_DARK_RGB)

    /** The chrome's ink on the glass. */
    val ink: Color = tokenColour(DesignTokens.INK_DARK_RGB)

    /** Secondary ink: status figures, layer names. */
    val inkSecondary: Color = tokenColour(DesignTokens.INK_SECONDARY_DARK_RGB)

    /** Muted ink: labels that explain and never invite. */
    val inkMuted: Color = tokenColour(DesignTokens.INK_MUTED_DARK_RGB)

    /** The ink a refusal is written in (a page that cannot be read). */
    val danger: Color = tokenColour(DesignTokens.DANGER_DARK_RGB)

    /** The corner every panel turns at (`panelCornerRadiusDp`). */
    val panelShape: RoundedCornerShape = RoundedCornerShape(DesignTokens.PANEL_CORNER_RADIUS_DP.dp)

    /** The corner a capsule turns at (`capsuleCornerRadiusDp`): tabs, chips, round buttons. */
    val capsuleShape: RoundedCornerShape = RoundedCornerShape(DesignTokens.CAPSULE_CORNER_RADIUS_DP.dp)

    /** The header band's height, in dp. */
    val headerHeight = DesignTokens.HEADER_HEIGHT_DP.dp

    /** The status bar's height, in dp. */
    val statusHeight = DesignTokens.STATUS_HEIGHT_DP.dp

    /** Every chrome glyph is drawn with this stroke — monoline, no filled icons. */
    val glyphStroke = DesignTokens.GLYPH_STROKE_DP.dp

    /** The one shadow in the window: the soft halo under the sheet (`pageShadowDp`). */
    val pageShadow = DesignTokens.PAGE_SHADOW_DP.dp

    /** The halo's colour — above black, because a shadow cannot be darker than a true-black desk. */
    val pageShadowColor: Color = tokenColour(DesignTokens.PAGE_SHADOW_RGB)

    /** Inter, the app's own geometric neutral face (the three weights the Android module ships). */
    val inter: FontFamily = FontFamily(
        Font("fonts/inter_regular.ttf", FontWeight.Normal, FontStyle.Normal),
        Font("fonts/inter_medium.ttf", FontWeight.Medium, FontStyle.Normal),
        Font("fonts/inter_bold.ttf", FontWeight.Bold, FontStyle.Normal),
    )

    /**
     * A figure: Inter with **tabular numerals**. Every number the window shows — page, zoom, progress —
     * uses this style, so the readout does not shuffle sideways as it changes.
     */
    val numeric = TextStyle(fontFamily = inter, fontSize = 11.sp, fontFeatureSettings = "tnum")

    /** The status bar's plain word: same face, muted, prose rather than a figure. */
    val label = TextStyle(fontFamily = inter, fontSize = 11.sp)

    /** The empty state's title. */
    val title = TextStyle(fontFamily = inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

    /** The empty state's explanation, and a tab label. */
    val body = TextStyle(fontFamily = inter, fontSize = 13.sp)

    /** The one Material scheme the window uses: the same black desk and charcoal ramp as the tablet. */
    val chromeScheme = darkColorScheme(
        primary = accent,
        onPrimary = desk,
        secondary = accent,
        onSecondary = desk,
        background = desk,
        onBackground = ink,
        surface = tokenColour(DesignTokens.GLASS_DARK_RGB),
        onSurface = ink,
        surfaceVariant = tokenColour(DesignTokens.GLASS_DARK_RGB),
        onSurfaceVariant = inkSecondary,
        surfaceContainer = tokenColour(DesignTokens.GLASS_DARK_RGB),
        surfaceContainerHigh = activeSlot,
        surfaceContainerHighest = activeSlot,
        outline = activeSlot,
        outlineVariant = tokenColour(DesignTokens.GLASS_DARK_RGB),
    )

    /** The window's theme wrapper: one scheme, no dynamic colour, no light variant yet. */
    @Composable
    fun Apply(content: @Composable () -> Unit) = MaterialTheme(colorScheme = chromeScheme, content = content)

    /** A token's RGB (`0xRRGGBB`) and alpha as a Compose [Color]. */
    fun tokenColour(rgb: Int, alpha: Float = 1f): Color = Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = alpha,
    )
}
