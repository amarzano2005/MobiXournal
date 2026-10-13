package com.mobixournal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mobixournal.R

/**
 * Modern geometric neutral typeface (Inter).
 *
 * Bundled under `app/src/main/res/font/` as static TrueType faces:
 *  - [R.font.inter_regular] (Weight 400)
 *  - [R.font.inter_medium] (Weight 500)
 *  - [R.font.inter_bold] (Weight 700)
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val defaultTypography = Typography()

/**
 * Modern Material 3 typography with Inter as the geometric neutral face.
 */
val ModernTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = Inter),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = Inter),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = Inter),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = Inter),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = Inter),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = Inter),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = Inter),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = Inter),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = Inter),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = Inter),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = Inter),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = Inter),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = Inter),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = Inter),
)

/**
 * OpenType tabular figures (`tnum`) style for coordinates, zoom indicators, and counters.
 *
 * Digits in this style occupy identical monospaced widths, preventing readout width jitter
 * during zoom gestures and cursor tracking.
 */
val TabularFiguresStyle = TextStyle(
    fontFamily = Inter,
    fontFeatureSettings = "tnum",
)
