package com.mobixournal.desktop.settings
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.tools.glassRim

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
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

    /**
     * Which palette is live: `false` is the window's own dark glass on a true-black desk, `true` is the
     * light one — the app's paper desk with near-black ink.
     *
     * A Compose **state**, and every colour below a **getter** over it: a surface that read
     * `DesktopTheme.glass` during composition is invalidated when the answer changes, so the window
     * repaints from one call and no second theme object exists to drift from this one.
     */
    private var light by mutableStateOf(false)

    /** Whether the light theme is live — the question the native title bar and the tests ask. */
    val isLight: Boolean get() = light

    /** Switch the window's theme; every colour in this file follows it. */
    fun use(lightTheme: Boolean) {
        light = lightTheme
    }

    /** The desk the sheet lies on: true OLED black, never a charcoal. */
    val desk: Color get() = tokenColour(if (light) DesignTokens.DESK_LIGHT_RGB else DesignTokens.DESK_RGB)

    /**
     * The bands' fill — the header, the sidebar, the status bar, the rail and the ribbon.
     *
     * Flat and **opaque**: the flat-workstation contract separates surfaces with a tonal step and one
     * crisp hairline, never with a translucent pane and a rim (`docs/interface-contract.md` §1). The
     * tablet keeps its frosted glass and never reads the `DESKTOP_*` tokens.
     */
    val glass: Color get() = tokenColour(
        if (light) DesignTokens.DESKTOP_BAND_LIGHT_RGB else DesignTokens.DESKTOP_BAND_DARK_RGB,
    )

    /**
     * The fill a chrome **control** wears — a header button, an input, a menu chip: one tone above the
     * band it stands on, opaque, so an object is read by its tone rather than by a rim.
     */
    val control: Color get() = tokenColour(
        if (light) DesignTokens.GLASS_LIGHT_RGB else DesignTokens.DESKTOP_CONTROL_DARK_RGB,
    )

    /**
     * A **field inside a floating panel** — a name box, a page range, a preset.
     *
     * A panel is the surface tone, so a field takes the control tone on dark and the band's tinted grey
     * on paper: a sunken field reads as one step either way, never as the panel it sits in.
     */
    val panelField: Color get() = tokenColour(
        if (light) DesignTokens.DESKTOP_BAND_LIGHT_RGB else DesignTokens.DESKTOP_CONTROL_DARK_RGB,
    )

    /** The rim's alpha at the top of a floating surface: lit on black, shaded on paper. */
    val rimAlphaTop: Float get() =
        if (light) DesignTokens.RIM_ALPHA_TOP_LIGHT else DesignTokens.RIM_ALPHA_TOP

    /** The rim's alpha at the bottom of a floating surface. */
    val rimAlphaBottom: Float get() =
        if (light) DesignTokens.RIM_ALPHA_BOTTOM_LIGHT else DesignTokens.RIM_ALPHA_BOTTOM

    /** A floating surface's fill — an opaque surface tone, never a translucency. */
    val floating: Color get() = tokenColour(
        if (light) DesignTokens.GLASS_LIGHT_RGB else DesignTokens.DESKTOP_SURFACE_DARK_RGB,
    )

    /**
     * A **document tab's** fill: the tablet's own chip glass, at full strength while it is the document in
     * front.
     *
     * The window's tab used to wear [activeSlot] — the steel that says *this tool slot is in hand* — so one
     * state drew as a solid grey capsule on the PC and as a pane of glass ringed by the accent on the
     * tablet. A tab is a document, not a slot: it takes the glass, and the accent edge says which one is
     * open.
     */
    val tabFill: Color get() = floating

    /** The same chip, untouched: the tablet thins its glass to 45 % on black and 60 % on paper. */
    val tabFillResting: Color get() =
        floating.copy(alpha = if (light) TAB_RESTING_ALPHA_LIGHT else TAB_RESTING_ALPHA)

    /** Opaque palette body for desktop popups, dialogs and failure cards. */
    val panel: Color get() = tokenColour(
        if (light) DesignTokens.GLASS_LIGHT_RGB else DesignTokens.DESKTOP_SURFACE_DARK_RGB,
    )

    /** Opaque desktop popup body, sharing the palette with the other shells. */
    val popupSurface: Color get() = panel
    val popupRadius = DesignTokens.DESKTOP_POPUP_CORNER_RADIUS_DP.dp
    val popupRowRadius = DesignTokens.DESKTOP_POPUP_ROW_CORNER_RADIUS_DP.dp

    /** [popupRowRadius] as a shape: every row, cell and swatch inside a popup's 8 dp corner. */
    val popupRowShape: RoundedCornerShape = RoundedCornerShape(popupRowRadius)
    val popupRowHeight = DesignTokens.DESKTOP_POPUP_ROW_HEIGHT_DP.dp
    val popupInset = DesignTokens.DESKTOP_POPUP_INSET_DP.dp

    /** Settings and nested menus cover underlying controls with the same opaque body. */
    val settingsPanel: Color get() = popupSurface

    /** The **tool strips'** fill: the surface tone, opaque, one step above the page's frame. */
    val stripGlass: Color get() = tokenColour(
        if (light) DesignTokens.GLASS_LIGHT_RGB else DesignTokens.DESKTOP_SURFACE_DARK_RGB,
    )

    /**
     * One edge, one pixel, 10 % — the hairline that separates two flat surfaces, and the whole edge of a
     * popup, a menu and a card.
     */
    val hairline: Color get() = tokenColour(
        if (light) DesignTokens.HAIRLINE_LIGHT_RGB else DesignTokens.HAIRLINE_RGB,
        DesignTokens.DESKTOP_HAIRLINE_ALPHA,
    )

    /** The stronger border a control, input or focused container wears. */
    val borderStrong: Color get() = tokenColour(
        if (light) DesignTokens.HAIRLINE_LIGHT_RGB else DesignTokens.HAIRLINE_RGB,
        if (light) DesignTokens.DESKTOP_BORDER_STRONG_LIGHT_ALPHA else DesignTokens.DESKTOP_BORDER_STRONG_ALPHA,
    )

    /** The ink a filled accent surface writes in — the tablet's own on-colour pairing. */
    val onAccent: Color get() = tokenColour(
        if (light) DesignTokens.DESKTOP_ON_ACCENT_LIGHT_RGB else DesignTokens.DESKTOP_ON_ACCENT_DARK_RGB,
    )

    /** The scrim under a modal: the desk on black, the slate on paper. */
    val scrim: Color get() = if (light) {
        tokenColour(DesignTokens.DESKTOP_SCRIM_LIGHT_RGB, DesignTokens.DESKTOP_SCRIM_LIGHT_ALPHA)
    } else {
        tokenColour(DesignTokens.DESK_RGB, DesignTokens.DESKTOP_SCRIM_DARK_ALPHA)
    }

    /** The luminescent accent: what is active, never a wash. */
    val accent: Color get() = tokenColour(
        if (light) DesignTokens.ACCENT_LIGHT_RGB else DesignTokens.ACCENT_DARK_RGB,
    )

    /**
     * The pixel-document red: the colour of a command that is about a **PDF**.
     *
     * The tablet's header draws Export PDF in it and its menu draws both PDF rows in it, while Save wears
     * the accent (`docs/ui-modernization.md`). The window's header and menu had every glyph in the chrome
     * ink, so the one command that writes the document out as a different kind of file looked like any
     * other — the same convention has to hold on both screens.
     */
    val documentRed: Color = tokenColour(DesignTokens.DOCUMENT_RED_RGB)

    /**
     * The amber a **drawing guide** is drawn in — the same instrument the tablet lays on its sheet.
     *
     * Deliberately not [accent]: an accent marks what is picked, while a setsquare is a physical thing
     * on the paper. The token is the one home (`DesignTokens.GUIDE_RGB`), so the two shells cannot
     * paint two ambers — `DesktopThemeTest` reads it back.
     */
    val guide: Color = tokenColour(DesignTokens.GUIDE_RGB)

    /** The faint wash inside the setsquare's body: [guide] at the shared `GUIDE_FILL_ALPHA`. */
    val guideFill: Color get() = guide.copy(alpha = DesignTokens.GUIDE_FILL_ALPHA / 255f)

    /**
     * The red a **connection pin** wears — the tablet's `chrome.pin`, from the one token (`PIN_RGB`), so
     * the two shells cannot paint two reds. Deliberately not [accent]: a pin is a join point on the sheet,
     * not something that is picked, which is what an accent blue made it look like.
     */
    val pin: Color = tokenColour(DesignTokens.PIN_RGB)

    /** The steel a *picked* slot wears — a selected tab, a held toggle. */
    val activeSlot: Color get() = tokenColour(
        if (light) DesignTokens.DESKTOP_ACTIVE_SLOT_LIGHT_RGB else DesignTokens.DESKTOP_ACTIVE_SLOT_DARK_RGB,
    )

    /** The hover step: the hairline's ink at 8 %, barely there, so a slot answers the pointer. */
    val hoverSlot: Color get() = tokenColour(
        if (light) DesignTokens.HAIRLINE_LIGHT_RGB else DesignTokens.HAIRLINE_RGB,
        if (light) DesignTokens.DESKTOP_HOVER_LIGHT_ALPHA else DesignTokens.DESKTOP_HOVER_DARK_ALPHA,
    )

    /** The chrome's ink on the glass. */
    val ink: Color get() = tokenColour(if (light) DesignTokens.INK_LIGHT_RGB else DesignTokens.INK_DARK_RGB)

    /** Secondary ink: status figures, layer names. */
    val inkSecondary: Color get() = tokenColour(
        if (light) DesignTokens.INK_SECONDARY_LIGHT_RGB else DesignTokens.INK_SECONDARY_DARK_RGB,
    )

    /** Muted ink: labels that explain and never invite. */
    val inkMuted: Color get() = tokenColour(
        if (light) DesignTokens.INK_MUTED_LIGHT_RGB else DesignTokens.INK_MUTED_DARK_RGB,
    )

    /** The ink a refusal is written in (a page that cannot be read). */
    val danger: Color get() = tokenColour(if (light) DesignTokens.DANGER_LIGHT_RGB else DesignTokens.DANGER_DARK_RGB)

    /** The corner every panel turns at (`panelCornerRadiusDp`), as a value the glass edge can take. */
    val panelRadius = DesignTokens.PANEL_CORNER_RADIUS_DP.dp

    /** The corner every panel turns at (`panelCornerRadiusDp`). */
    val panelShape: RoundedCornerShape = RoundedCornerShape(panelRadius)

    /**
     * The window's **one control corner**, `4 dp`: a button, an input, a tab, a tool slot, a popup row, a
     * tooltip. The flat-workstation contract allows exactly two corners and this is the smaller one
     * (`docs/interface-contract.md` §3).
     */
    val controlRadius = DesignTokens.DESKTOP_CONTROL_CORNER_DP.dp

    /** [controlRadius] as a shape. */
    val controlShape: RoundedCornerShape = RoundedCornerShape(controlRadius)

    /**
     * The window's **one surface corner**, `8 dp`: a popup, a menu, a dialog, Settings, a floating bar, a
     * card. A surface is never a capsule and a control is never a pill.
     */
    val surfaceRadius = DesignTokens.DESKTOP_POPUP_CORNER_RADIUS_DP.dp

    /** [surfaceRadius] as a shape. */
    val surfaceShape: RoundedCornerShape = RoundedCornerShape(surfaceRadius)

    /** The corner a capsule turns at (`capsuleCornerRadiusDp`): the rail, tabs, chips, round buttons. */
    val capsuleRadius = DesignTokens.CAPSULE_CORNER_RADIUS_DP.dp

    /** The same corner as a shape. */
    val capsuleShape: RoundedCornerShape = RoundedCornerShape(capsuleRadius)

    /**
     * The cast shadow a band puts on what stands beside it: **none**.
     *
     * A flat workstation has no drop shadows on its chrome — a band is separated from the canvas by its
     * tone and one crisp hairline (`docs/interface-contract.md` §1). The token stays so every `bandSurface`
     * call site keeps its shape.
     */
    val bandShadow: Dp get() = 0.dp

    /** The paper-facing shade: none. The flat contract replaced it with the band's own hairline. */
    val bandEdgeShadow: Color get() = Color.Transparent

    /** How far that shade reaches onto the paper. */
    val bandEdgeShadowWidth = DesignTokens.BAND_EDGE_SHADOW_DP.dp

    /** The cast shadow under a floating capsule — the rail, the ribbon, a header control: 6 dp. */
    val capsuleShadow = DesignTokens.CAPSULE_SHADOW_DP.dp

    /**
     * The ink a **glyph on the glass** is drawn in: pure white, the tablet's chrome ink.
     *
     * The window used to tint its tool glyphs with [inkSecondary] — a grey — while the tablet's rail
     * draws them white. Two toolbars of different brightness are two different materials, whatever the
     * fill underneath is the same, so the icons take the chrome's own ink on both shells.
     */
    val chromeInk: Color get() = tokenColour(
        if (light) DesignTokens.CHROME_INK_LIGHT_RGB else DesignTokens.CHROME_INK_DARK_RGB,
    )

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
     * The header band's height, in dp: the shared `40 dp`.
     *
     * A flat header holds a 28 dp button with air above and below it, so the band is the token itself and
     * not the token plus the old capsule's lift (`docs/desktop-ui-layout.md`).
     */
    val headerHeight = DesignTokens.HEADER_HEIGHT_DP.dp

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
    val pageShadowColor: Color get() = tokenColour(
        if (light) DesignTokens.PAGE_SHADOW_LIGHT_RGB else DesignTokens.PAGE_SHADOW_RGB,
    )

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
     * A figure: **12 sp Medium with tabular numerals** at 16 sp leading. Every number the window shows —
     * page, zoom, progress — uses this style, so the readout does not shuffle sideways as it changes.
     */
    val numeric = desktopStyle(12, FontWeight.Medium, 16, tabular = true)

    /** The status bar's plain word: 12 sp, regular, prose rather than a figure. */
    val label = desktopStyle(12, FontWeight.Normal, 16)

    /** The empty state's title: 18 sp Medium at 24 sp leading. */
    val title = desktopStyle(18, FontWeight.Medium, 24)

    /** The empty state's explanation: 13 sp regular at 18 sp leading. */
    val body = desktopStyle(13, FontWeight.Normal, 18)

    /**
     * A document's name on its tab: 12 sp Medium at 16 sp leading.
     *
     * Inter ships a **Medium**, not a SemiBold, so a 600 request is a weight the family does not have —
     * the renderer picks the neighbouring face and the label comes out heavier than the strip's other words.
     */
    val tab = desktopStyle(12, FontWeight.Medium, 16)

    /**
     * The band's **effective** colour: the glass composited over the desk.
     *
     * The bands are translucent, so their fill is not a colour until something is behind it — and behind
     * them is always the desk. This is that one number, used where a *single* opaque colour has to stand
     * for the band: the Windows title bar, which the system paints and cannot be given a fill with an
     * alpha.
     */
    val bandRgb: Int get() =
        if (light) DesignTokens.DESKTOP_BAND_LIGHT_RGB else DesignTokens.DESKTOP_BAND_DARK_RGB

    /** [bandRgb] in Win32's `COLORREF` order (`0x00BBGGRR`), which is what DWM attributes take. */
    val bandColourRef: Int get() = colourRef(bandRgb)

    /** The desk in `COLORREF` — the title bar's border, so the window has no light outline round it. */
    val deskColourRef: Int get() = colourRef(
        if (light) DesignTokens.DESK_LIGHT_RGB else DesignTokens.DESK_RGB,
    )

    /** The chrome ink in `COLORREF`: the title text and its glyphs are the band's own ink. */
    val inkColourRef: Int get() = colourRef(
        if (light) DesignTokens.INK_LIGHT_RGB else DesignTokens.INK_DARK_RGB,
    )

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
    val card: Color get() = activeSlot.copy(alpha = if (light) CARD_ALPHA_LIGHT else CARD_ALPHA)

    /** How much of the picked slot's steel a card wears: enough to separate, never a second grey block. */
    const val CARD_ALPHA: Float = 0.22f

    /**
     * How much of it a card wears **on paper**: more, because 22 % of a pale steel over a white panel is
     * a tone the eye cannot find, and a settings card that cannot be seen is not a card.
     */
    const val CARD_ALPHA_LIGHT: Float = 0.55f

    /**
     * The paper a **preview of a page** is drawn on: the app's white sheet.
     *
     * A graph's ink is black on paper; the same ink on the lane's dark glass is invisible, so a preview that
     * shows what will land on the sheet has to show the sheet. It is the one surface in the window that is
     * deliberately light, and it exists only inside a card, never as chrome.
     */
    /**
     * The pointer's own ink — the **dark** one, in both themes.
     *
     * What sits under the pointer is the sheet, and a sheet is white whichever theme the chrome wears, so a mark
     * that took [ink] would turn pale in the dark theme and disappear on paper. The eraser's pointer is drawn in
     * this, which is why it is the light theme's ink rather than a themed pair (`docs/desktop-tablet.md`).
     */
    val pointerInk: Color = tokenColour(DesignTokens.INK_LIGHT_RGB)

    val previewPaper: Color = tokenColour(0xFFFFFF)

    /** A value inside a field: 12 sp regular with tabular figures, at 16 sp leading. */
    val field = desktopStyle(12, FontWeight.Normal, 16, tabular = true)

    /** A card's eyebrow, and a section's: 11 sp Medium, 0.4 sp tracking, so a label reads as a mark. */
    val eyebrow = desktopStyle(11, FontWeight.Medium, 14).copy(letterSpacing = 0.4.sp)

    /** A panel's title — a message, a card's own name: 13 sp Medium at 18 sp leading. */
    val heading = desktopStyle(13, FontWeight.Medium, 18)

    /**
     * A block's subtitle: 15 sp Medium at 20 sp leading. For a label that introduces a group of objects
     * rather than titling a section — the entry screen's "Recents", which stands over the grid.
     */
    val subtitle = desktopStyle(15, FontWeight.Medium, 20)

    /** An `0xRRGGBB` colour in Win32's `COLORREF` order (`0x00BBGGRR`). */
    fun colourRef(rgb: Int): Int =
        ((rgb and 0xFF) shl 16) or (rgb and 0xFF00) or ((rgb shr 16) and 0xFF)

    /** The one Material scheme the window uses: the same black desk and charcoal ramp as the tablet. */
    /** The Material scheme the light theme hands `MaterialTheme`: the flat roles, on the light ramp. */
    private val lightScheme = lightColorScheme(
        primary = tokenColour(DesignTokens.ACCENT_LIGHT_RGB),
        onPrimary = tokenColour(DesignTokens.DESKTOP_ON_ACCENT_LIGHT_RGB),
        secondary = tokenColour(DesignTokens.ACCENT_LIGHT_RGB),
        onSecondary = tokenColour(DesignTokens.DESKTOP_ON_ACCENT_LIGHT_RGB),
        background = tokenColour(DesignTokens.DESK_LIGHT_RGB),
        onBackground = tokenColour(DesignTokens.INK_LIGHT_RGB),
        surface = tokenColour(DesignTokens.GLASS_LIGHT_RGB),
        onSurface = tokenColour(DesignTokens.INK_LIGHT_RGB),
        surfaceVariant = tokenColour(DesignTokens.DESKTOP_BAND_LIGHT_RGB),
        onSurfaceVariant = tokenColour(DesignTokens.INK_SECONDARY_LIGHT_RGB),
        surfaceContainer = tokenColour(DesignTokens.DESKTOP_ACTIVE_SLOT_LIGHT_RGB),
        surfaceContainerHigh = tokenColour(DesignTokens.DESKTOP_ACTIVE_SLOT_LIGHT_RGB),
        surfaceContainerHighest = tokenColour(DesignTokens.DESKTOP_ACTIVE_SLOT_LIGHT_RGB),
        outline = tokenColour(DesignTokens.HAIRLINE_LIGHT_RGB, DesignTokens.DESKTOP_BORDER_STRONG_LIGHT_ALPHA),
        outlineVariant = tokenColour(DesignTokens.HAIRLINE_LIGHT_RGB, DesignTokens.DESKTOP_HAIRLINE_ALPHA),
    )

    /** The Material scheme the dark theme hands `MaterialTheme`: four distinct flat roles, not one tone. */
    val chromeScheme = darkColorScheme(
        primary = tokenColour(DesignTokens.ACCENT_DARK_RGB),
        onPrimary = tokenColour(DesignTokens.DESKTOP_ON_ACCENT_DARK_RGB),
        secondary = tokenColour(DesignTokens.ACCENT_DARK_RGB),
        onSecondary = tokenColour(DesignTokens.DESKTOP_ON_ACCENT_DARK_RGB),
        background = tokenColour(DesignTokens.DESK_RGB),
        onBackground = tokenColour(DesignTokens.INK_DARK_RGB),
        surface = tokenColour(DesignTokens.DESKTOP_SURFACE_DARK_RGB),
        onSurface = tokenColour(DesignTokens.INK_DARK_RGB),
        surfaceVariant = tokenColour(DesignTokens.DESKTOP_BAND_DARK_RGB),
        onSurfaceVariant = tokenColour(DesignTokens.INK_SECONDARY_DARK_RGB),
        surfaceContainer = tokenColour(DesignTokens.DESKTOP_CONTROL_DARK_RGB),
        surfaceContainerHigh = tokenColour(DesignTokens.DESKTOP_ACTIVE_SLOT_DARK_RGB),
        surfaceContainerHighest = tokenColour(DesignTokens.DESKTOP_ACTIVE_SLOT_DARK_RGB),
        outline = tokenColour(DesignTokens.HAIRLINE_RGB, DesignTokens.DESKTOP_BORDER_STRONG_ALPHA),
        outlineVariant = tokenColour(DesignTokens.HAIRLINE_RGB, DesignTokens.DESKTOP_HAIRLINE_ALPHA),
    )

    /**
     * The window's theme wrapper: one scheme, one type scale, no dynamic colour, no light variant yet.
     *
     * The scale is the tablet's own ([typography]), so the two shells cannot disagree about a word's face
     * or its tracking — which is what made the PC read as a different product.
     */
    @Composable
    fun Apply(content: @Composable () -> Unit) = MaterialTheme(
        colorScheme = if (light) lightScheme else chromeScheme,
        typography = typography,
        content = content,
    )

    /**
     * The window's flat type scale: Inter at one size, one weight and one leading, with tabular figures
     * where the word is a number. Each role below is a call of this, so a size is written once.
     */
    private fun desktopStyle(size: Int, weight: FontWeight, line: Int, tabular: Boolean = false): TextStyle =
        TextStyle(
            fontFamily = inter,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = line.sp,
            fontFeatureSettings = if (tabular) "tnum" else null,
        )

    /** A token's RGB (`0xRRGGBB`) and alpha as a Compose [Color]. */
    fun tokenColour(rgb: Int, alpha: Float = 1f): Color = Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = alpha,
    )
}

/** The tablet's resting chip: its glass thinned to 45 % on black, 60 % on paper. */
private const val TAB_RESTING_ALPHA = 0.45f
private const val TAB_RESTING_ALPHA_LIGHT = 0.6f
