package com.mobixournal.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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

    /**
     * The bands' fill — the header, the sidebar, the status bar, the rail and every capsule — over the
     * desk, at the **window's** alpha rather than the tablet's.
     *
     * The same RGB, thinned: over a true-black desk `0.85` composites to `#131314`, which in a maximised
     * window on a 34" monitor read as a grey slab beside the phone's chrome. At
     * `DESKTOP_BAND_ALPHA` it is `#0D0D0E` — a blacker band, still a material (there is a fill, a rim and
     * a lift) and still the same colour family. The decision and its reasoning are
     * `docs/ui-modernization.md`'s; `DesktopThemeTest` pins the composite.
     */
    val glass: Color = tokenColour(DesignTokens.GLASS_DARK_RGB, DesignTokens.DESKTOP_BAND_ALPHA)

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

    /**
     * The **Settings** surface's fill: [panel] with a hair of the page showing through.
     *
     * The settings panel is read, not glanced at: over a sheet its rows are small text on a translucent
     * card, and at `floatingPanelAlpha` the page's own ink came through the labels. It is almost solid —
     * 0.97 leaves the material legible as glass at the rim, where the edge is, and makes the inside a
     * page that is read rather than looked through. Not fully [panel], because the panel still lies over
     * the document it is changing and a completely flat card reads as a second window.
     */
    val settingsPanel: Color = tokenColour(DesignTokens.GLASS_DARK_RGB, 0.97f)

    /**
     * The **tool strips'** fill: the same glass, at `STRIP_GLASS_ALPHA`.
     *
     * A strip lies on the page, so the band's 0.58 let the sheet's white through and the bar went grey —
     * the tools were read against its own ink, and the bar stopped reading as chrome at all. Nearly solid
     * is what makes it a bar on a desk: the page shows at its rim and nowhere else.
     */
    val stripGlass: Color = tokenColour(DesignTokens.GLASS_DARK_RGB, DesignTokens.STRIP_GLASS_ALPHA)

    /**
     * One edge, one pixel, 12 % white — the hairline a *control* wears (a swatch's ring), never a second
     * rule on a band. A floating surface takes [glassRim] instead: a flat border was what made the
     * window's panels look like boxes while the tablet's looked like glass.
     */
    val hairline: Color = tokenColour(DesignTokens.HAIRLINE_RGB, DesignTokens.HAIRLINE_ALPHA)

    /** The luminescent accent: what is active, never a wash. */
    val accent: Color = tokenColour(DesignTokens.ACCENT_DARK_RGB)

    /**
     * The pixel-document red: the colour of a command that is about a **PDF**.
     *
     * The tablet's header draws Export PDF in it and its menu draws both PDF rows in it, while Save wears
     * the accent (`docs/ui-modernization.md`). The window's header and menu had every glyph in the chrome
     * ink, so the one command that writes the document out as a different kind of file looked like any
     * other — the same convention has to hold on both screens.
     */
    val documentRed: Color = tokenColour(DesignTokens.DOCUMENT_RED_RGB)

    /** The steel a *picked* slot wears — a selected tab, a held toggle. */
    val activeSlot: Color = tokenColour(DesignTokens.ACTIVE_SLOT_DARK_RGB)

    /** The same steel at the hover step: what a slot under the pointer wears, short of picked. */
    val hoverSlot: Color = activeSlot.copy(alpha = DesignTokens.HOVER_SLOT_ALPHA)

    /** The chrome's ink on the glass. */
    val ink: Color = tokenColour(DesignTokens.INK_DARK_RGB)

    /** Secondary ink: status figures, layer names. */
    val inkSecondary: Color = tokenColour(DesignTokens.INK_SECONDARY_DARK_RGB)

    /** Muted ink: labels that explain and never invite. */
    val inkMuted: Color = tokenColour(DesignTokens.INK_MUTED_DARK_RGB)

    /** The ink a refusal is written in (a page that cannot be read). */
    val danger: Color = tokenColour(DesignTokens.DANGER_DARK_RGB)

    /** The corner every panel turns at (`panelCornerRadiusDp`), as a value the glass edge can take. */
    val panelRadius = DesignTokens.PANEL_CORNER_RADIUS_DP.dp

    /** The corner every panel turns at (`panelCornerRadiusDp`). */
    val panelShape: RoundedCornerShape = RoundedCornerShape(panelRadius)

    /** The corner a capsule turns at (`capsuleCornerRadiusDp`): the rail, tabs, chips, round buttons. */
    val capsuleRadius = DesignTokens.CAPSULE_CORNER_RADIUS_DP.dp

    /** The same corner as a shape. */
    val capsuleShape: RoundedCornerShape = RoundedCornerShape(capsuleRadius)

    /**
     * The cast shadow a band puts on what stands beside it — 4 dp, the tablet's own band lift.
     *
     * A band is not a panel: it takes this one lift and no rim (`docs/chrome.md`), which is what makes
     * the header read as chrome over the canvas rather than as a slab lying on the page.
     */
    val bandShadow = DesignTokens.BAND_SHADOW_DP.dp

    /** The cast shadow under a floating capsule — the rail, the ribbon, a header control: 6 dp. */
    val capsuleShadow = DesignTokens.CAPSULE_SHADOW_DP.dp

    /**
     * The ink a **glyph on the glass** is drawn in: pure white, the tablet's chrome ink.
     *
     * The window used to tint its tool glyphs with [inkSecondary] — a grey — while the tablet's rail
     * draws them white. Two toolbars of different brightness are two different materials, whatever the
     * fill underneath is the same, so the icons take the chrome's own ink on both shells.
     */
    val chromeInk: Color = tokenColour(DesignTokens.CHROME_INK_DARK_RGB)

    /** The rail's floating capsule: 52 dp, inside a [railLaneWidth] lane. */
    val railCapsuleWidth = DesignTokens.RAIL_CAPSULE_WIDTH_DP.dp

    /** The air between the rail capsule and its lane: 6 dp on each side. */
    val railLanePadding = DesignTokens.RAIL_LANE_PADDING_DP.dp

    /** The whole rail lane: capsule plus air, 64 dp — the tablet's own lane. */
    val railLaneWidth = DesignTokens.RAIL_LANE_WIDTH_DP.dp

    /** The capsule's top/bottom air inside the lane, so it is a capsule and not a band: 8 dp. */
    val railOuterPadding = DesignTokens.RAIL_OUTER_PADDING_DP.dp

    /** A tool slot's corner inside the rail: 10 dp, the tablet's `RoundedCornerShape(10)`. */
    val railSlotRadius = DesignTokens.RAIL_SLOT_CORNER_RADIUS_DP.dp

    /** The same slot corner as a shape. */
    val railSlotShape: RoundedCornerShape = RoundedCornerShape(railSlotRadius)

    /** A control in the header band: 36 dp, the tablet's icon buttons beside the dock. */
    val headerControlSize = DesignTokens.HEADER_CONTROL_SIZE_DP.dp

    /** The grid every tool glyph is drawn on, in design units: 24. */
    const val TOOL_GLYPH_GRID: Float = DesignTokens.TOOL_GLYPH_GRID

    /** A tool glyph's stroke, in grid units: 1.5 of 24. */
    const val TOOL_GLYPH_STROKE_UNITS: Float = DesignTokens.TOOL_GLYPH_STROKE_UNITS

    /**
     * The header band's height, in dp: the token plus 8.
     *
     * The tablet's header carries one row of glyphs; the window's carries the tab strip *and* the four
     * file commands beside it, on a band that has to hold a 36 dp capsule with air above and below it.
     * At the token's 40 dp the capsules sat 2 dp from either edge and read as a strip of controls glued
     * to the window's top — so the band grows, and every glyph it holds grows with it.
     */
    val headerHeight = (DesignTokens.HEADER_HEIGHT_DP + 8).dp

    /**
     * A header glyph's own box, in dp: 20, the size `docs/interface-contract.md` requires every glyph to
     * read at. The capsule around it is [headerControlSize].
     */
    val headerGlyphSize = 20.dp

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
     * The tablet's **Material 3 scale**, in Inter — the same mapping `ModernTypography` makes in the app
     * module (`ui/theme/Type.kt`), from the same three faces.
     *
     * This is what `MaterialTheme` gets, so a `Text` that names no style is still Inter at the size and
     * tracking the tablet would set: the window's own styles below are roles of this scale, not a second
     * opinion about type. Without it, a `Text` fell back to the framework's default face and the window
     * mixed two typefaces.
     */
    val typography: Typography = Typography().let { base ->
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = inter),
            displayMedium = base.displayMedium.copy(fontFamily = inter),
            displaySmall = base.displaySmall.copy(fontFamily = inter),
            headlineLarge = base.headlineLarge.copy(fontFamily = inter),
            headlineMedium = base.headlineMedium.copy(fontFamily = inter),
            headlineSmall = base.headlineSmall.copy(fontFamily = inter),
            titleLarge = base.titleLarge.copy(fontFamily = inter),
            titleMedium = base.titleMedium.copy(fontFamily = inter),
            titleSmall = base.titleSmall.copy(fontFamily = inter),
            bodyLarge = base.bodyLarge.copy(fontFamily = inter),
            bodyMedium = base.bodyMedium.copy(fontFamily = inter),
            bodySmall = base.bodySmall.copy(fontFamily = inter),
            labelLarge = base.labelLarge.copy(fontFamily = inter),
            labelMedium = base.labelMedium.copy(fontFamily = inter),
            labelSmall = base.labelSmall.copy(fontFamily = inter),
        )
    }

    /**
     * A figure: the tablet's small label with **tabular numerals**. Every number the window shows — page,
     * zoom, progress — uses this style, so the readout does not shuffle sideways as it changes.
     */
    val numeric = typography.labelSmall.copy(fontFeatureSettings = "tnum")

    /** The status bar's plain word: the same role, muted, prose rather than a figure. */
    val label = typography.labelSmall

    /** The empty state's title: the tablet's large title, in the regular face it actually ships. */
    val title = typography.titleLarge.copy(fontWeight = FontWeight.Medium)

    /** The empty state's explanation: the tablet's body, not a smaller face of its own. */
    val body = typography.bodyMedium

    /**
     * A document's name on its tab: the tablet's label role, at the weight its picked chip wears.
     *
     * Inter ships a **Medium**, not a SemiBold, so a 600 request is a weight the family does not have —
     * the renderer picks the neighbouring face and the label comes out heavier than the strip's other words.
     */
    val tab = typography.labelMedium.copy(fontWeight = FontWeight.Medium)

    /**
     * The band's **effective** colour: the glass composited over the desk.
     *
     * The bands are translucent, so their fill is not a colour until something is behind it — and behind
     * them is always the desk. This is that one number, used where a *single* opaque colour has to stand
     * for the band: the Windows title bar, which the system paints and cannot be given a fill with an
     * alpha.
     */
    val bandRgb: Int = blendRgb(DesignTokens.GLASS_DARK_RGB, DesignTokens.DESKTOP_BAND_ALPHA, DesignTokens.DESK_RGB)

    /** [bandRgb] in Win32's `COLORREF` order (`0x00BBGGRR`), which is what DWM attributes take. */
    val bandColourRef: Int = colourRef(bandRgb)

    /** The desk in `COLORREF` — the title bar's border, so the window has no light outline round it. */
    val deskColourRef: Int = colourRef(DesignTokens.DESK_RGB)

    /** The chrome ink in `COLORREF`: the title text and its glyphs are the band's own ink. */
    val inkColourRef: Int = colourRef(DesignTokens.INK_DARK_RGB)

    /** [rgb] (`0xRRGGBB`) painted at [alpha] over [over] (`0xRRGGBB`), as one opaque RGB. */
    fun blendRgb(rgb: Int, alpha: Float, over: Int): Int {
        fun channel(shift: Int): Int {
            val src = (rgb shr shift) and 0xFF
            val dst = (over shr shift) and 0xFF
            return (src * alpha + dst * (1f - alpha)).toInt().coerceIn(0, 255) shl shift
        }
        return channel(16) or channel(8) or channel(0)
    }

    /**
     * A **card inside a lane**: the steel at [CARD_ALPHA] over the band, so a group of fields reads as one
     * object on the lane instead of as another run of labels.
     *
     * The lane is a band, and a band takes no rim (`docs/chrome.md`) — so this is a *tone*, the way the
     * hover step is, not a second surface with an edge of its own.
     */
    val card: Color = activeSlot.copy(alpha = CARD_ALPHA)

    /** How much of the picked slot's steel a card wears: enough to separate, never a second grey block. */
    const val CARD_ALPHA: Float = 0.22f

    /**
     * The paper a **preview of a page** is drawn on: the app's white sheet.
     *
     * A graph's ink is black on paper; the same ink on the lane's dark glass is invisible, so a preview that
     * shows what will land on the sheet has to show the sheet. It is the one surface in the window that is
     * deliberately light, and it exists only inside a card, never as chrome.
     */
    val previewPaper: Color = tokenColour(0xFFFFFF)

    /**
     * A value inside a field: Inter at 12 sp with tabular figures — a size up from [numeric], because a
     * field the user types into is read at a glance, not scanned like a status figure.
     */
    val field = typography.bodySmall.copy(fontFeatureSettings = "tnum")

    /** A card's heading, and a section's: the lane's one step of hierarchy. */
    val eyebrow = typography.labelSmall.copy(fontWeight = FontWeight.Medium)

    /** A panel's title — a message, a card's own name: the tablet's small title, in Medium. */
    val heading = typography.titleSmall.copy(fontWeight = FontWeight.Medium)

    /** An `0xRRGGBB` colour in Win32's `COLORREF` order (`0x00BBGGRR`). */
    fun colourRef(rgb: Int): Int =
        ((rgb and 0xFF) shl 16) or (rgb and 0xFF00) or ((rgb shr 16) and 0xFF)

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

    /**
     * The window's theme wrapper: one scheme, one type scale, no dynamic colour, no light variant yet.
     *
     * The scale is the tablet's own ([typography]), so the two shells cannot disagree about a word's face
     * or its tracking — which is what made the PC read as a different product.
     */
    @Composable
    fun Apply(content: @Composable () -> Unit) =
        MaterialTheme(colorScheme = chromeScheme, typography = typography, content = content)

    /** A token's RGB (`0xRRGGBB`) and alpha as a Compose [Color]. */
    fun tokenColour(rgb: Int, alpha: Float = 1f): Color = Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = alpha,
    )
}
