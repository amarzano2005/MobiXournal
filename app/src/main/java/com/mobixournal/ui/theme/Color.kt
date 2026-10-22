package com.mobixournal.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The app's static brand palette.
 *
 * These are *fallback* colours only: [XoppTheme] prefers Material You dynamic colour, and reaches
 * for this palette when the device can't supply one (below Android 12). It seeds the light/dark
 * `ColorScheme`s in `Theme.kt`, which in turn is what `ChromeColors.kt` reads to derive the ARGB
 * ints the non-Compose `DrawingSurfaceView` paints its chrome with — so a change here can move the
 * canvas' selection and guide colours too.
 *
 * This is chrome only. Ink, page backgrounds and the pen palette are document data that lives in
 * the `.xopp` file, and are deliberately not defined here.
 */

/** Brand primary under a light scheme — rich modern royal blue (#2563EB). */
val Blue = Color(0xFF2563EB)

/** Brand primary & accent under a dark scheme — luminous modern accent blue (#60A5FA). */
val BlueDark = Color(0xFF60A5FA)

/** Legacy purple palette references. */
val Purple = Color(0xFF4A2C7A)
val PurpleDark = Color(0xFFC084FC)

/** Brand secondary / guide accent; default Amber (#FFFFB300). */
val Amber = Color(0xFFFFB300)

/**
 * The pixel-document red (#E53935) — the PDF actions' colour in the top-bar menu.
 *
 * The menu's file actions are blue like every other control; the two that are about a **PDF** (import
 * the pages of one, write the document out as one) wear this red instead, so the row reads as the
 * PDF row at a glance and not as two more ordinary buttons. It is the same red as the recording dot,
 * which is the one red the chrome already used.
 */
val DocumentRed = Color(0xFFE53935)

// --- Formal Semantic Chrome Palette (docs/ui-modernization.md) ---

/** True OLED Black canvas and desk background for dark mode (#000000). */
val DeskDark = Color(0xFF000000)

/** Clean warm paper light neutral desk background for light mode (#F8F9FA). */
val DeskLight = Color(0xFFF8F9FA)

/** Neutral dark text on light surfaces (#1B212F). */
val TextDarkOnLight = Color(0xFF1B212F)

/** High-legibility crisp light text on dark surfaces (#EDEDED). */
val TextLightOnDark = Color(0xFFEDEDED)

/** Secondary text on dark surfaces (#A0A0A0). */
val TextSecondaryOnDark = Color(0xFFA0A0A0)

/**
 * Secondary text on light surfaces: a **neutral** cool grey (#5E6266).
 *
 * It was a blue-grey (#596579), which put a colour cast on every caption, pop-up heading and hint in
 * the light theme — the palette is black, grey and the blue accent, so the secondary ink is grey like
 * its dark-theme twin ([TextSecondaryOnDark], #A0A0A0) with only a hint of cool, never a blue.
 */
val TextSecondaryOnLight = Color(0xFF5E6266)

/** Hairline outline border for dark surfaces (#383838). */
val OutlineDark = Color(0xFF383838)

/** Subtle hairline outline variant for dark surfaces (#262626). */
val OutlineVariantDark = Color(0xFF262626)

/** Hairline outline border for light surfaces (#767B7F). */
val OutlineLight = Color(0xFF767B7F)

/** Subtle hairline outline variant for light surfaces (#C4C9CD). */
val OutlineVariantLight = Color(0xFFC4C9CD)

/** Refined neutral grey container for submenus and dialogs (#252528). */
val NeutralContainerDark = Color(0xFF252528)

/**
 * The light theme's counterpart of [NeutralContainerDark]: the neutral grey the raised chrome —
 * dialogs, the function-plot chrome, section plates — is cut from (#EAEDF0).
 *
 * The role it fills (`surfaceContainer`) used to be left at Material 3's baseline, which in the light
 * theme is the **lavender** neutral `#F3EDF7`: every dialog and panel the chrome did not name by hand
 * came out violet-tinted in a palette that has no violet. One grey per theme, pinned by
 * `ColorContractGuardTest`.
 */
val NeutralContainerLight = Color(0xFFEAEDF0)

/** A half step below [NeutralContainerLight] (#F4F6F8) — the light `surfaceContainerLow`. */
val NeutralContainerLightLow = Color(0xFFF4F6F8)

/** One step below [NeutralContainerLight] (#E3E7EB) — the light `surfaceContainerHigh`. */
val NeutralContainerLightHigh = Color(0xFFE3E7EB)

/** Active tool slot highlight in dark mode: refined neutral steel grey (#38383E). */
val ActiveSlotDark = Color(0xFF38383E)

/**
 * Active tool slot highlight in light mode: the light counterpart of [ActiveSlotDark] (#DEE3E7) — one
 * step **down** from the light container so a picked tool and a selected row still read as picked.
 */
val ActiveSlotLight = Color(0xFFDEE3E7)

/** Highest light container: raised chips, the active-tool indicator (#DCE1E6). */
val NeutralContainerLightHighest = Color(0xFFDCE1E6)

/**
 * Ink for the **luminous** dark-theme accent ([BlueDark]): true black (#000000).
 *
 * `onPrimary` was left at Material 3's baseline in the dark theme — `#381E72`, a deep violet — so any
 * filled accent surface carried violet text. A near-white accent takes the desk's own true black.
 */
val OnAccentDark = Color(0xFF000000)

/**
 * **Signals** — the colours the chrome shows while something is happening: a recording, a paused
 * recording, a stored model, a warning that needs an action. They live here, not in the screen that
 * shows them: a state colour is a token like any other (`docs/interface-contract.md` §2).
 */
val SignalAmber = Color(0xFFF57C00)
val SignalGreen = Color(0xFF2E7D32)
val AlertOrange = Color(0xFFE65100)

/** The pen-diagnostics readout's ink: a terminal green, only ever on [PanelScrim]. */
val MeterGreen = Color(0xFF9BE79B)

/** A blocking overlay's scrim, and the darker one the diagnostics panel paints behind its text. */
val ModalScrim = Color(0x66000000)
val PanelScrim = Color(0xE6000000)

/** A dimmed glyph on a transparent control — the pause button while nothing is recording. */
val MutedTintDark = Color(0x33FFFFFF)
val MutedTintLight = Color(0x33000000)

/** The tab strip's "new document" chip: a tint over the glass, and its hairline in the light theme. */
val TabChipFillDark = Color(0x22FFFFFF)
val TabChipFillLight = Color(0x10000000)
val TabChipBorderLight = Color(0x18000000)

/** The lit edge of a menu row (the overflow menu's glass panes): bright top, faint bottom. */
fun menuSheenTop(isDark: Boolean): Color = Color.White.copy(alpha = if (isDark) 0.17f else 0.62f)
fun menuSheenBottom(isDark: Boolean): Color = Color.White.copy(alpha = if (isDark) 0.035f else 0.08f)

