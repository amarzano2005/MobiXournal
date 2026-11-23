package com.mobixournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.liquidGlassFadeTransform

/**
 * A macro-area of the settings: the four things the app's preferences are *about*, each owning the
 * [SettingsSection]s that belong to it.
 *
 * On a wide screen the areas are the permanent **side menu** (`SettingsSidebar`) with the selected
 * area's sections beside it; on a narrow one they are the pushed index. Either way an area owns its
 * sections, so the same list drives both shapes.
 *
 * Every area owns at least two sections: an area with a single child is a level of indirection that
 * buys nothing, so a new section is added to the closest existing area instead — unless that child is
 * **inline**, in which case the area draws the page itself and there is no tap to pay for at all.
 */
enum class SettingsArea(
    val title: String,
    val summary: String,
    val sections: List<SettingsSection>,
    /** The glyph on the area's row, so a list of five reads at a glance. */
    val icon: ImageVector,
) {
    /** Input devices and the keys that stand in for them: stylus behaviour and shortcuts. */
    INPUT(
        "Input",
        "Stylus pressure, pen side buttons, hover, finger gestures, and keyboard shortcuts.",
        listOf(SettingsSection.STYLUS, SettingsSection.GESTURES, SettingsSection.SHORTCUTS),
        SettingsGlyph.STYLUS.imageVector(),
    ),
    /** What the drawing tools do and what they draw with: tool behaviour, figures, palette. */
    DRAWING(
        "Drawing",
        "Default tool, snapping, figure sizes and shapes, and the pen palette.",
        listOf(
            SettingsSection.EDITOR,
            SettingsSection.FIGURES,
            SettingsSection.COLORS,
            SettingsSection.RING,
        ),
        SettingsGlyph.FIGURES.imageVector(),
    ),
    /** How the app looks and how the canvas is navigated: toolbars, panning, theme. */
    INTERFACE(
        "Interface",
        "Toolbar layout, momentum and panning, theme.",
        // Appearance after Navigation: the theme is the last thing looked up on this page, and the
        // two Canvas behaviours read together.
        listOf(SettingsSection.TOOLBAR, SettingsSection.NAVIGATION, SettingsSection.FOCUS_MODE, SettingsSection.APPEARANCE),
        SettingsGlyph.APPEARANCE.imageVector(),
    ),
    /** What the app writes and keeps: new-document format, storage limits, settings backup. */
    APP(
        "App & data",
        "The format of new documents, storage limits, and settings backup.",
        listOf(
            SettingsSection.DOCUMENTS,
            SettingsSection.STORAGE,
            SettingsSection.BACKUP,
        ),
        SettingsGlyph.STORAGE.imageVector(),
    ),
    /**
     * The app as an artefact rather than as a set of choices: which build is in hand, what it owes, and
     * where to tell the project about a problem or an idea — **one page**, drawn by its only section.
     *
     * It is its own area because those questions are asked at a different moment from the ones above —
     * *which version is this* and *where do I say something about it* are not settings, and a reader
     * looking for either should not walk through the document format to find them.
     */
    ABOUT(
        "About",
        "The build in hand, its update check, the credits it owes, and the doors to the project.",
        listOf(SettingsSection.ABOUT),
        SettingsGlyph.ABOUT.imageVector(),
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
enum class SettingsSection(
    val title: String,
    val summary: String,
    val keywords: String,
    /** The glyph the section's row carries, so a list of sections is read at a glance. */
    val icon: ImageVector,
    /**
     * The section's controls are drawn **on its area's page**, in place of the row that would open them: a
     * page holding one answer and one switch is a level of indirection the reader pays for twice, and they
     * still reach it by search — this flag only decides how the area list draws the entry (`SectionRows`).
     */
    val inline: Boolean = false,
) {
    /** Stylus and finger input settings: pressure curve, pen side buttons, hover, finger drawing. */
    STYLUS(
        "Stylus",
        "Finger drawing, hover preview, pen side button, pressure feel.",
        "pressure multiplier minimum floor pen side button barrel button double tap hover preview palm rejection " +
            "finger drawing touch eraser tip stylus pen presets diagnostics thickness",
        SettingsGlyph.STYLUS.imageVector(),
    ),
    /** Editor behaviour: default tool, snapping to grid or angles. */
    EDITOR(
        "Editor",
        "Default tool and snapping to the grid or to 15° rotations.",
        "default tool startup snapping snap grid ruling rotation angle degrees layer pages",
        SettingsGlyph.EDITOR.imageVector(),
    ),
    /** Toolbar configuration: which buttons appear in the Main and Secondary Toolbars and their order. */
    TOOLBAR(
        "Toolbar",
        "Main Toolbar position and buttons, and Secondary Toolbar (figures) configuration.",
        "main toolbar position left right handed left-handed right-handed buttons order hide show " +
            "reorder rail secondary top bar figures",
        SettingsGlyph.TOOLBAR.imageVector(),
    ),
    /** Figures: shape recognition, the default figure size and each figure tool's default variant. */
    FIGURES(
        "Figures",
        "Shape recognition, default figure size, and each figure tool's default variant.",
        "shape recognition recognize recognizer recogniser figure size width shapes submenu triangle " +
            "trapezoid scalene angles rectangle ellipse polygon sides arrow table circuit logic guides",
        SettingsGlyph.FIGURES.imageVector(),
    ),
    /** Colors: the pen palette every colour picker offers. */
    COLORS(
        "Colors",
        "Add, edit or remove the swatches every colour picker offers.",
        "colour color palette swatch favourite favorite custom hex pen highlighter add remove " +
            "restore default theme",
        SettingsGlyph.COLORS.imageVector(),
    ),
    /** Ring: the pen-tip HUD — the faces it carries and what each position holds. */
    RING(
        "Ring",
        "Arrange Ring faces at the pen tip.",
        "ring radial hud pen tip pen side button barrel button swatches colours color widths tools slot face position " +
            "favourite flick automatic reset",
        SettingsGlyph.RING.imageVector(),
    ),
    /** Finger gestures: what a still two- and three-finger tap on the canvas does. */
    GESTURES(
        "Finger gestures",
        "What a still two-finger tap and a still three-finger tap do.",
        "finger gestures touch two finger tap double tap three triple tap undo redo toggle eraser " +
            "select full page focus mode hand multitouch multi-touch tap",
        SettingsGlyph.GESTURES.imageVector(),
    ),
    /** Keyboard shortcuts: one key per tool and per pen colour. */
    SHORTCUTS(
        "Shortcuts",
        "One key each to jump straight to a tool or a pen colour.",            "shortcut key keyboard hardware expresskey tablet remote button detect assignment focus mode",
        SettingsGlyph.SHORTCUTS.imageVector(),
    ),
    /** Navigation: momentum scrolling strength and panning sensitivity. */
    NAVIGATION(
        "Navigation",
        "Momentum scrolling and panning sensitivity.",
        "momentum scroll scrolling glide inertia pan panning sensitivity speed zoom full page " +
            "scrollbar",
        SettingsGlyph.NAVIGATION.imageVector(),
    ),
    /** Focus mode: customizable Liquid Glass floating bar tools and placement. */
    FOCUS_MODE(
        "Focus mode",
        "Customize the Focus mode floating bar and its tools.",
        "focus mode floating bar full page liquid glass tools dock vertical horizontal",
        SettingsGlyph.FOCUS_MODE.imageVector(),
    ),
    /** Appearance: which colour scheme the app's chrome is painted with. */
    APPEARANCE(
        "Appearance",
        "The app's theme: light, dark or the system setting.",
        "appearance interface theme dark light system mode",
        SettingsGlyph.APPEARANCE.imageVector(),
        inline = true,
    ),
    /**
     * Documents: the format a new document is written in — the standing answer to the question the
     * onboarding tour asks once.
     */
    DOCUMENTS(
        "Documents",
        "The format a new document is written in.",
        "document format default format default new document mobix xopp xournal++ desktop " +
            "interchange file type extension save as living graph table flattened portable",
        SettingsGlyph.DOCUMENTS.imageVector(),
    ),
    /** Storage: text import limit and PDF cache budget. */
    STORAGE(
        "Storage",
        "How big a text file may be imported, and how much cache to keep.",            "storage limit size text import markdown plain text megabytes cache pdf prune disk space",
        SettingsGlyph.STORAGE.imageVector(),
    ),
    /** Backup: export or import settings to and from a JSON file. */
    BACKUP(
        "Backup",
        "Export or import your settings to and from a JSON file.",            "backup export import json file restore reset settings transfer migrate",
        SettingsGlyph.BACKUP.imageVector(),
    ),
    /**
     * About: the build in hand, its update check, the credits, the licence and the doors to the project.
     *
     * It is [inline] and it is its area's only child: the page *is* the area, so there is no row to tap
     * before reaching it — see [SettingsArea] and `inlineOnly`.
     */
    ABOUT(
        "About",
        "The build in hand, its update check, the credits, the licence, and where to report a problem or suggest an idea.",
        "about version build number licence license gpl credits xournal nexopp copy details update latest " +
            "feedback report bug problem issue crash suggest idea feature request github tracker help " +
            "support contact",
        SettingsGlyph.ABOUT.imageVector(),
        inline = true,
    ),
}

/**
 * Settings screen: on wide screens ([SETTINGS_TWO_PANE_MIN_WIDTH] and above) a permanent sidebar
 * lists the seven dynamic [SettingsDestination]s grouped under their [SettingsGroupCategory]
 * eyebrows beside the active destination; on narrower screens a flat 1-tap grouped index opens any
 * destination directly. Both shapes share live keyword search via [settingsSearchResults].
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDestination by remember { mutableStateOf<SettingsDestination?>(null) }
    var query by remember { mutableStateOf("") }
    val results = remember(query) { settingsSearchResults(query) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = maxWidth >= SETTINGS_TWO_PANE_MIN_WIDTH

        BackHandler(enabled = true) {
            if (twoPane) {
                if (query.isNotEmpty()) query = "" else onBack()
            } else {
                when {
                    selectedDestination != null -> selectedDestination = null
                    query.isNotEmpty() -> query = ""
                    else -> onBack()
                }
            }
        }

        if (twoPane) {
            TwoPaneSettings(
                query = query,
                onQuery = { query = it },
                results = results,
                selected = selectedDestination,
                onSelect = { selectedDestination = it },
                settings = settings,
                onChange = onChange,
                onBack = onBack,
            )
        } else {
            StackedSettings(
                query = query,
                onQuery = { query = it },
                results = results,
                destination = selectedDestination,
                onDestination = { selectedDestination = it },
                settings = settings,
                onChange = onChange,
                onBack = onBack,
            )
        }
    }
}

/** Wide master/detail layout: grouped searchable sidebar on the left, active destination on the right. */
@Composable
private fun TwoPaneSettings(
    query: String,
    onQuery: (String) -> Unit,
    results: List<SettingsSection>,
    selected: SettingsDestination?,
    onSelect: (SettingsDestination) -> Unit,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val visibleDestinations = remember(query, results) {
        if (query.isBlank()) SettingsDestination.entries
        else results.map { it.toDestination() }.distinct()
    }
    val activeDestination = when {
        query.isBlank() -> selected ?: SettingsDestination.PEN_INPUT
        selected in visibleDestinations -> selected
        else -> visibleDestinations.firstOrNull()
    }

    SettingsChrome(
        title = "Settings",
        onBack = { if (query.isNotEmpty()) onQuery("") else onBack() },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            SettingsSidebar(
                query = query,
                onQuery = onQuery,
                selected = activeDestination,
                visibleDestinations = visibleDestinations,
                onSelect = onSelect,
                modifier = Modifier
                    .width(248.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(ModernTheme.hairlineBorder(chromeIsDark())),
            )
            TwoPaneDetailPane(
                activeDestination = activeDestination,
                query = query,
                settings = settings,
                onChange = onChange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun TwoPaneDetailPane(
    activeDestination: SettingsDestination?,
    query: String,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewport = rememberSettingsScrollViewport()
    LaunchedEffect(activeDestination) { viewport.state.scrollTo(0) }
    Column(
        modifier = modifier
            .settingsViewport(viewport)
            .verticalScroll(viewport.state)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalSettingsScrollViewport provides viewport) {
                if (activeDestination == null) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No setting matches “$query”.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    AnimatedContent(
                        targetState = activeDestination,
                        transitionSpec = { liquidGlassFadeTransform() },
                        label = "TwoPaneSettingsContent",
                    ) { dest ->
                        Column(Modifier.fillMaxWidth()) {
                            TabletSectionHeader(dest)
                            DestinationBody(dest, settings, onChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletSectionHeader(destination: SettingsDestination) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)) {
        Text(
            text = destination.category.title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = destination.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Narrow single-pane layout: 1-tap grouped index (with live search) and dynamic destination pages. */
@Composable
private fun StackedSettings(
    query: String,
    onQuery: (String) -> Unit,
    results: List<SettingsSection>,
    destination: SettingsDestination?,
    onDestination: (SettingsDestination?) -> Unit,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    SettingsChrome(
        title = destination?.title ?: "Settings",
        onBack = {
            when {
                destination != null -> onDestination(null)
                query.isNotEmpty() -> onQuery("")
                else -> onBack()
            }
        },
    ) { padding ->
        val viewport = rememberSettingsScrollViewport()
        LaunchedEffect(destination) { viewport.state.scrollTo(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp)
                .settingsViewport(viewport)
                .verticalScroll(viewport.state),
        ) {
            CompositionLocalProvider(LocalSettingsScrollViewport provides viewport) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = { liquidGlassFadeTransform() },
                    label = "StackedSettingsPane",
                ) { openDest ->
                    if (openDest != null) {
                        Column(Modifier.fillMaxWidth()) {
                            DestinationBody(openDest, settings, onChange)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            SettingsSearchField(
                                query = query,
                                onQuery = onQuery,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                            if (query.isNotEmpty()) {
                                SearchResultRows(query, results) { onDestination(it) }
                            } else {
                                GroupedSettingsIndex(settings = settings, onOpenDestination = { onDestination(it) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationBody(
    destination: SettingsDestination,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    when (destination) {
        SettingsDestination.PEN_INPUT -> PenInputSection(settings, onChange)
        SettingsDestination.CANVAS_TOOLS -> CanvasToolsSection(settings, onChange)
        SettingsDestination.PALETTE_RING -> PaletteRingSection(settings, onChange)
        SettingsDestination.WORKSPACE -> WorkspaceSection(settings, onChange)
        SettingsDestination.FOCUS_MODE -> FocusModeSection(settings, onChange)
        SettingsDestination.SHORTCUTS -> ShortcutsSection(settings, onChange)
        SettingsDestination.FILES_DATA -> FilesDataSection(settings, onChange)
        SettingsDestination.ABOUT -> AboutSection()
    }
}

/** Shared scaffold for Settings screens on the theme background so panel cards elevate cleanly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsChrome(title: String, onBack: () -> Unit, body: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp, end = 4.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                SettingsDivider()
            }
        },
    ) { padding -> body(padding) }
}

/** Leading accent icon badge shared by About doors and inline choice triggers. */
@Composable
internal fun SectionGlyph(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun IndexSectionGlyph(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.width(14.dp))
}

/**
 * Tappable row inside a [SettingsCard]: optional compact glyph badge, title, optional summary,
 * optional live trailing readout, and a subtle disclosure chevron.
 */
@Composable
internal fun SectionRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    valueLabel: String? = null,
    onClick: () -> Unit,
) {
    val reach = LocalSettingsCardInset.current
    Row(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = reach, vertical = settingsCellInset(reach)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) IndexSectionGlyph(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (summary.isNullOrBlank()) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!valueLabel.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}
