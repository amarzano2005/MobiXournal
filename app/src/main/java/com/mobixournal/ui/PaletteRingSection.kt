package com.mobixournal.ui

import androidx.compose.runtime.Composable

/**
 * Unified **Palette & Ring** section (`Ring` + `Colors`) combining the interactive pen-tip
 * radial wheel with the favourite colours and pen palette swatches.
 */
@Composable
fun PaletteRingSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    RingSection(settings, onChange)
    ColorsSection(settings, onChange)
}
