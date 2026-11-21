package com.mobixournal.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.mobixournal.design.SettingsGlyph

/** High-level categories grouping the seven dynamic Settings destinations. */
internal enum class SettingsGroupCategory(val title: String) {
    DRAFTING("Drafting"),
    INTERFACE("Interface"),
    SYSTEM("System"),
}

/**
 * The seven consolidated Settings pages presented to the user, replacing the 14 static sections
 * while preserving all underlying [SettingsSection] search aliases and parity contracts.
 */
internal enum class SettingsDestination(
    val title: String,
    val subtitle: String,
    val category: SettingsGroupCategory,
    val icon: ImageVector,
) {
    PEN_INPUT(
        title = "Pen & Input",
        subtitle = "Pressure curve, barrel button, finger gestures",
        category = SettingsGroupCategory.DRAFTING,
        icon = SettingsGlyph.STYLUS.imageVector(),
    ),
    CANVAS_TOOLS(
        title = "Canvas & Tools",
        subtitle = "Default tool, snapping, figures, momentum",
        category = SettingsGroupCategory.DRAFTING,
        icon = SettingsGlyph.FIGURES.imageVector(),
    ),
    PALETTE_RING(
        title = "Palette & Ring",
        subtitle = "Pen-tip wheel and colour swatches",
        category = SettingsGroupCategory.DRAFTING,
        icon = SettingsGlyph.RING.imageVector(),
    ),
    WORKSPACE(
        title = "Workspace",
        subtitle = "Theme mode, toolbar side and visible buttons",
        category = SettingsGroupCategory.INTERFACE,
        icon = SettingsGlyph.APPEARANCE.imageVector(),
    ),
    SHORTCUTS(
        title = "Shortcuts",
        subtitle = "Hardware keyboard and ExpressKey bindings",
        category = SettingsGroupCategory.INTERFACE,
        icon = SettingsGlyph.SHORTCUTS.imageVector(),
    ),
    FILES_DATA(
        title = "Files & Data",
        subtitle = "Default format, cache limits, JSON backup",
        category = SettingsGroupCategory.SYSTEM,
        icon = SettingsGlyph.DOCUMENTS.imageVector(),
    ),
    ABOUT(
        title = "About",
        subtitle = "Version, updates, bug report, credits",
        category = SettingsGroupCategory.SYSTEM,
        icon = SettingsGlyph.ABOUT.imageVector(),
    ),
}

/** Maps any legacy/search [SettingsSection] onto the dynamic [SettingsDestination] that hosts it. */
internal fun SettingsSection.toDestination(): SettingsDestination = when (this) {
    SettingsSection.STYLUS, SettingsSection.GESTURES -> SettingsDestination.PEN_INPUT
    SettingsSection.EDITOR, SettingsSection.FIGURES, SettingsSection.NAVIGATION -> SettingsDestination.CANVAS_TOOLS
    SettingsSection.COLORS, SettingsSection.RING -> SettingsDestination.PALETTE_RING
    SettingsSection.APPEARANCE, SettingsSection.TOOLBAR -> SettingsDestination.WORKSPACE
    SettingsSection.SHORTCUTS -> SettingsDestination.SHORTCUTS
    SettingsSection.DOCUMENTS, SettingsSection.STORAGE, SettingsSection.BACKUP -> SettingsDestination.FILES_DATA
    SettingsSection.ABOUT -> SettingsDestination.ABOUT
}
