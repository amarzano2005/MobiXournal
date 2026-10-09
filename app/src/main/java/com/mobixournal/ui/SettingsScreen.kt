package com.mobixournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A macro-area of the settings: the four things the app's preferences are *about*, each owning the
 * [SettingsSection]s that belong to it.
 *
 * On a wide screen the areas are the permanent **side menu** (`SettingsSidebar`) with the selected
 * area's sections beside it; on a narrow one they are the pushed index. Either way an area owns its
 * sections, so the same list drives both shapes.
 *
 * Every area owns at least two sections: an area with a single child is a level of indirection that
 * buys nothing, so a new section is added to the closest existing area instead.
 */
enum class SettingsArea(
    val title: String,
    val summary: String,
    val sections: List<SettingsSection>,
) {
    /** Input devices and the keys that stand in for them: stylus behaviour and shortcuts. */
    INPUT(
        "Input",
        "Stylus pressure, barrel buttons, hover, and keyboard shortcuts.",
        listOf(SettingsSection.STYLUS, SettingsSection.SHORTCUTS),
    ),
    /** What the drawing tools do and what they draw with: tool behaviour, figures, palette. */
    DRAWING(
        "Drawing",
        "Default tool, snapping, figure sizes and shapes, and the pen palette.",
        listOf(SettingsSection.EDITOR, SettingsSection.FIGURES, SettingsSection.COLORS),
    ),
    /** How the app looks and how the canvas is navigated: toolbars, theme, panning. */
    INTERFACE(
        "Interface",
        "Toolbar layout, theme and page counter, momentum and panning.",
        listOf(SettingsSection.TOOLBAR, SettingsSection.APPEARANCE, SettingsSection.NAVIGATION),
    ),
    /** The app itself: how much it may store, how its settings move, what it is. */
    APP(
        "App & data",
        "Storage limits, settings backup, and version information.",
        listOf(SettingsSection.STORAGE, SettingsSection.BACKUP, SettingsSection.ABOUT),
    ),
}

/**
 * The settings sections: the page of controls for one topic, reached through its [SettingsArea].
 *
 * @property title The section's name, as shown in the side menu's neighbour list and the page bar.
 * @property summary One line saying what the section covers, shown under the title in the list.
 * @property keywords Search aliases, space-separated — the words a user types when they do not know
 *   what the app calls the thing (see [settingsSearchResults]). They are the reason search *finds*
 *   settings rather than filtering titles, so each list carries the everyday names for its controls
 *   (including the near-miss ones: "dark", "wallpaper", "megabytes"…). [SettingsSearchTest] pins a
 *   handful of them to their sections, which is what keeps a list from rotting as its page changes.
 */
enum class SettingsSection(val title: String, val summary: String, val keywords: String) {
    /** Stylus and finger input settings: pressure curve, barrel buttons, hover, finger drawing. */
    STYLUS(
        "Stylus",
        "Finger drawing, hover preview, barrel button, pressure feel.",
        "pressure multiplier minimum floor barrel button double tap hover preview palm rejection " +
            "finger drawing touch eraser tip stylus pen presets diagnostics thickness",
    ),
    /** Editor behaviour: default tool, shape recognition, snapping to grid or angles. */
    EDITOR(
        "Editor",
        "Default tool and snapping to the grid or to 15° rotations.",
        "default tool startup snapping snap grid ruling rotation angle degrees shape recognition " +
            "recognizer layer pages",
    ),
    /** Toolbar configuration: which buttons appear in the Main and Secondary Toolbars and their order. */
    TOOLBAR(
        "Toolbar",
        "Main Toolbar position and buttons, and Secondary Toolbar (figures) configuration.",
        "main toolbar position left right handed left-handed right-handed buttons order hide show " +
            "reorder rail secondary top bar figures",
    ),
    /** Figures: the default figure size and the Shapes submenu's figures and order. */
    FIGURES(
        "Figures",
        "Default figure size, and the Shapes submenu's figures and order.",
        "figure size width shapes submenu triangle trapezoid scalene angles rectangle ellipse " +
            "spline arrow table circuit logic guides",
    ),
    /** Colors: the pen palette every colour picker offers. */
    COLORS(
        "Colors",
        "Add, edit or remove the swatches every colour picker offers.",
        "colour color palette swatch favourite favorite custom hex pen highlighter add remove " +
            "restore default theme",
    ),
    /** Keyboard shortcuts: one key per tool and per pen colour. */
    SHORTCUTS(
        "Shortcuts",
        "One key each to jump straight to a tool or a pen colour.",
        "shortcut key keyboard hardware expresskey tablet remote button detect assignment",
    ),
    /** Navigation: momentum scrolling strength and panning sensitivity. */
    NAVIGATION(
        "Navigation",
        "Momentum scrolling and panning sensitivity.",
        "momentum scroll scrolling glide inertia pan panning sensitivity speed zoom full page " +
            "page counter scrollbar",
    ),
    /** Appearance: theme mode, wallpaper colours and page counter position. */
    APPEARANCE(
        "Appearance",
        "Theme, wallpaper colours, and page counter position.",
        "appearance interface theme dark light system dynamic color colour " +
            "material you wallpaper page counter badge position",
    ),
    /** Storage: text import limit and PDF cache budget. */
    STORAGE(
        "Storage",
        "How big a text file may be imported, and how much cache to keep.",
        "storage limit size text import markdown plain text megabytes cache pdf prune disk space",
    ),
    /** Backup: export or import settings to and from a JSON file. */
    BACKUP(
        "Backup",
        "Export or import your settings to and from a JSON file.",
        "backup export import json file restore reset settings transfer migrate",
    ),
    /** About: version, licence, source, and support links. */
    ABOUT(
        "About",
        "Version, licence, source code, and how to support the work.",
        "about version build number licence license gpl source code github support donate credits",
    ),
}

/**
 * Settings, shaped like a tablet's system settings: on a screen of at least
 * [SETTINGS_TWO_PANE_MIN_WIDTH] a permanent side menu lists the four [SettingsArea]s with the selected
 * area's [SettingsSection]s beside it — and whichever section is open replaces that list — while a
 * narrower screen keeps the pushed flow (index → area → section). Both shapes share one search:
 * typing filters to matching sections wherever the user is, and a result opens that section directly.
 *
 * Back pops one step of whichever shape is on screen (section → results → area → out of settings),
 * so the system back button and the title-bar arrow stay the same gesture.
 *
 * Edits are pushed live to the canvas and persisted via [SettingsStore] (see `MainActivity`). Reached
 * from the top-bar menu.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var area by remember { mutableStateOf<SettingsArea?>(null) }
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    var query by remember { mutableStateOf("") }
    // Recomputed only when the query changes: a filter over eleven keyword lists, not per recomposition.
    val results = remember(query) { settingsSearchResults(query) }

    BoxWithConstraints {
        val twoPane = maxWidth >= SETTINGS_TWO_PANE_MIN_WIDTH

        // Settings is composed after the editor, so this handler outranks the editor's: the system back
        // mirrors the title-bar arrow. On a wide screen the side menu *is* the index, so there is no
        // index level to pop — the area stays selected and the next back leaves settings.
        BackHandler(enabled = true) {
            when {
                section != null -> section = null
                query.isNotEmpty() -> query = ""
                twoPane -> onBack()
                area != null -> area = null
                else -> onBack()
            }
        }

        if (twoPane) {
            TwoPaneSettings(
                shownArea = area ?: SettingsArea.INPUT,
                onArea = { area = it; section = null; query = "" },
                query = query,
                // Typing is a search: close whatever section is open, or the pane would stay on the
                // page the user has just stopped looking at while the field fills beside it.
                onQuery = { query = it; if (it.isNotEmpty()) section = null },
                results = results,
                section = section,
                onSection = { section = it },
                settings = settings,
                onChange = onChange,
                onBack = onBack,
            )
        } else {
            StackedSettings(
                area = area,
                onArea = { area = it },
                query = query,
                onQuery = { query = it },
                results = results,
                section = section,
                onSection = { section = it },
                settings = settings,
                onChange = onChange,
                onBack = onBack,
            )
        }
    }
}

/** The wide shape: permanent side menu on the left, the area's sections or the open section beside it. */
@Composable
private fun TwoPaneSettings(
    shownArea: SettingsArea,
    onArea: (SettingsArea) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    results: List<SettingsSection>,
    section: SettingsSection?,
    onSection: (SettingsSection?) -> Unit,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    SettingsChrome(
        title = paneTitle(section, query, shownArea),
        onBack = {
            when {
                section != null -> onSection(null)
                query.isNotEmpty() -> onQuery("")
                else -> onBack()
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            SettingsSidebar(
                query = query,
                onQuery = onQuery,
                selected = shownArea,
                onArea = onArea,
                modifier = Modifier.width(260.dp).fillMaxHeight(),
            )
            // A hairline rule rather than a Material divider: the two panes are one screen, not two cards.
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when {
                    section != null -> SectionBody(section, settings, onChange)
                    query.isNotEmpty() -> SearchResultRows(query, results, onSection)
                    else -> SectionRows(shownArea, onSection)
                }
            }
        }
    }
}

/** The narrow shape: one pane, walked by pushing pages — index, then area, then the section itself. */
@Composable
private fun StackedSettings(
    area: SettingsArea?,
    /** Null means "back to the index": the narrow shape walks a level down and up, so it clears it. */
    onArea: (SettingsArea?) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    results: List<SettingsSection>,
    section: SettingsSection?,
    onSection: (SettingsSection?) -> Unit,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val openArea = area
    val openSection = section
    SettingsChrome(
        title = when {
            openSection != null -> openSection.title
            openArea != null -> openArea.title
            else -> "Settings"
        },
        onBack = {
            when {
                openSection != null -> onSection(null)
                openArea != null -> onArea(null)
                else -> onBack()
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                openSection != null -> SectionBody(openSection, settings, onChange)
                openArea != null -> SectionRows(openArea, onSection)
                else -> {
                    // The search lives on the index: it is the one page both shapes share, and the
                    // answer to "where is this setting" is a section, not an area.
                    SettingsSearchField(query, onQuery, Modifier.padding(bottom = 12.dp))
                    if (query.isNotEmpty()) {
                        SearchResultRows(query, results, onSection)
                    } else {
                        Surface(
                            shape = SettingsCardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                val areas = SettingsArea.values()
                                areas.forEachIndexed { index, entry ->
                                    SectionRow(entry.title, entry.summary) { onArea(entry) }
                                    if (index < areas.size - 1) {
                                        SettingsDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the pane's title bar says: the open section, the search, or the selected area. */
private fun paneTitle(section: SettingsSection?, query: String, area: SettingsArea): String = when {
    section != null -> section.title
    query.isNotEmpty() -> "Search results"
    else -> area.title
}

/** The controls of one section page: the `when` that maps a [SettingsSection] onto its composable. */
@Composable
private fun SectionBody(
    section: SettingsSection,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    when (section) {
        SettingsSection.STYLUS -> StylusSection(settings, onChange)
        SettingsSection.EDITOR -> EditorSection(settings, onChange)
        SettingsSection.TOOLBAR -> ToolbarSection(settings, onChange)
        SettingsSection.FIGURES -> FiguresSection(settings, onChange)
        SettingsSection.COLORS -> ColorsSection(settings, onChange)
        SettingsSection.SHORTCUTS -> ShortcutsSection(settings, onChange)
        SettingsSection.NAVIGATION -> NavigationSection(settings, onChange)
        SettingsSection.APPEARANCE -> AppearanceSection(settings, onChange)
        SettingsSection.STORAGE -> StorageSection(settings, onChange)
        SettingsSection.BACKUP -> BackupSection(settings, onChange)
        SettingsSection.ABOUT -> AboutSection()
    }
}

/** An area's own list: what it covers, then one card holding its sections. */
@Composable
private fun SectionRows(area: SettingsArea, onOpen: (SettingsSection) -> Unit) {
    SettingsNote(area.summary, Modifier.padding(start = 4.dp, bottom = 12.dp))
    Surface(
        shape = SettingsCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            area.sections.forEachIndexed { index, entry ->
                SectionRow(entry.title, entry.summary) { onOpen(entry) }
                if (index < area.sections.size - 1) {
                    SettingsDivider()
                }
            }
        }
    }
}

/**
 * The search's answer: the matching sections, each labelled with the area it lives in so a result
 * found by an alias ("wallpaper" → Appearance) still says where the setting belongs.
 */
@Composable
private fun SearchResultRows(
    query: String,
    results: List<SettingsSection>,
    onOpen: (SettingsSection) -> Unit,
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No setting matches “$query”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Surface(
        shape = SettingsCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            results.forEachIndexed { index, entry ->
                val where = areaOf(entry)?.title
                SectionRow(
                    title = entry.title,
                    summary = if (where == null) entry.summary else "$where · ${entry.summary}",
                ) { onOpen(entry) }
                if (index < results.size - 1) {
                    SettingsDivider()
                }
            }
        }
    }
}

/** The shared chrome of every settings page: a titled bar with the back arrow, and a body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsChrome(title: String, onBack: () -> Unit, body: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding -> body(padding) }
}

/** One tappable row on a list page: the entry's name, what it covers, and a chevron. */
@Composable
internal fun SectionRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
