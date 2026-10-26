package com.mobixournal.desktop

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.design.SettingsGlyph

/**
 * The **sections** of the window's Settings: the page of controls for one topic.
 *
 * The list is the **phone's list** (`SettingsSection` in `app/src/main/java/com/mobixournal/ui/
 * SettingsScreen.kt`): the same thirteen names, in the same order, each carrying the same glyph from the
 * shared catalogue — so the two shells' settings read as one product rather than a full list on the tablet
 * and three short pages on the PC (`docs/interface-contract.md` §9).
 *
 * What differs is how much of each section the window can answer. A row that has a mechanism behind it is
 * a real control and changes the open window; a row that does not is **drawn and disabled, with one
 * sentence saying what is missing** — never invented, and never silently dropped, because a list that
 * pretends a setting does not exist is how the parity drifted in the first place. The mechanism lands with
 * its own task (`TODO.toml`) and only then does the row start acting.
 *
 * A page is **rows, not columns**: the name of the setting on the left, its control at the right edge
 * ([SettingsRow]) — the shape a preference takes on a desktop, and the reason this file stays readable:
 * each row says *what* it changes in one line under its name.
 *
 * @property title The section's name, as the index on the left shows it.
 * @property summary One line saying what the section covers, under the title in the index.
 * @property keywords Search aliases — the words a reader types when they do not know what the window calls
 *   the thing, so the field **finds** a section rather than filtering titles.
 * @property glyph The section's mark, from the one catalogue both shells read.
 */
enum class DesktopSettingSection(
    val title: String,
    val summary: String,
    val keywords: String,
    val glyph: SettingsGlyph,
) {
    STYLUS(
        "Stylus",
        "The pen stream and how hard it presses.",
        "tablet pen stylus wintab windows ink pressure stream driver barrel hover eraser tip thickness",
        SettingsGlyph.STYLUS,
    ),
    EDITOR(
        "Editor",
        "The tool a document opens on, and snapping.",
        "default tool startup snapping snap grid rotation angle degrees shape recognition recognizer",
        SettingsGlyph.EDITOR,
    ),
    TOOLBAR(
        "Toolbar",
        "Where the tool strips stand and how they are laid out.",
        "toolbar tools strip rail ribbon top side left right placement layout buttons order hide show",
        SettingsGlyph.TOOLBAR,
    ),
    FIGURES(
        "Figures",
        "The size and behaviour of the figures you draw.",
        "figures shapes size stroke snapping presets geometry",
        SettingsGlyph.FIGURES,
    ),
    COLORS(
        "Colors",
        "The pen's colour, width, line style and favourites.",
        "colours colors palette swatch favourites favorites width thickness line style dashed",
        SettingsGlyph.COLORS,
    ),
    GESTURES(
        "Gestures",
        "What a still two-finger tap and a three-finger tap do.",
        "gestures finger tap two three undo redo palm rejection touch",
        SettingsGlyph.GESTURES,
    ),
    SHORTCUTS(
        "Shortcuts",
        "The key table, remapped per command.",
        "shortcuts keys keyboard binding remap conflict reset",
        SettingsGlyph.SHORTCUTS,
    ),
    NAVIGATION(
        "Navigation",
        "How a document opens and how the canvas moves.",
        "navigation zoom fit page width momentum panning scrolling sidebar page list",
        SettingsGlyph.NAVIGATION,
    ),
    APPEARANCE(
        "Appearance",
        "The theme, the columns, and what the status bar reads.",
        "appearance theme dark light status bar readout frame time paint refresh inspector columns",
        SettingsGlyph.APPEARANCE,
    ),
    DOCUMENTS(
        "Documents",
        "The format a new document is written in, and its page.",
        "documents format mobix xopp new default page size paper",
        SettingsGlyph.DOCUMENTS,
    ),
    STORAGE(
        "Storage",
        "Where the window keeps its own files.",
        "storage cache settings file disk path thumbnails",
        SettingsGlyph.STORAGE,
    ),
    BACKUP(
        "Backup",
        "What keeps unsaved work safe.",
        "backup recovery snapshot crash restore unsaved autosave",
        SettingsGlyph.BACKUP,
    ),
    ABOUT(
        "About",
        "The version, the formats it reads, and what it is.",
        "about version build formats licence license independent unofficial",
        SettingsGlyph.ABOUT,
    ),
    ;

    /** Whether this section answers [needle] — its title, its summary or one of its aliases. */
    fun matches(needle: String): Boolean {
        if (needle.isBlank()) return true
        val lower = needle.trim().lowercase()
        return title.lowercase().contains(lower) ||
            summary.lowercase().contains(lower) ||
            keywords.contains(lower)
    }

    companion object {
        /** The section a window opens on: the one the strips are moved from. */
        val DEFAULT: DesktopSettingSection = TOOLBAR

        /** The sections the search field leaves, in the index's own order. */
        fun search(query: String): List<DesktopSettingSection> = entries.filter { it.matches(query) }
    }
}

/**
 * The page of one section: its title and summary, then its groups.
 *
 * A null [section] is the search's own answer — the field left nothing to show — and it is a sentence
 * rather than a blank page, so the reader knows the word was the problem and not the window.
 */
@Composable
internal fun DesktopSettingPage(policy: DesktopSettingsState, section: DesktopSettingSection?) {
    if (section == null) {
        Text("No setting matches that.", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        return
    }
    Text(section.title, color = DesktopTheme.ink, style = DesktopTheme.title)
    Spacer(Modifier.height(2.dp))
    Text(section.summary, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    when (section) {
        DesktopSettingSection.STYLUS -> StylusSettings(policy)
        DesktopSettingSection.EDITOR -> EditorSettings()
        DesktopSettingSection.TOOLBAR -> ToolbarSettings(policy)
        DesktopSettingSection.FIGURES -> FiguresSettings()
        DesktopSettingSection.COLORS -> ColorsSettings(policy)
        DesktopSettingSection.GESTURES -> UnavailableSettings(
            "Gestures",
            "A still two-finger tap undoes and a three-finger tap redoes on the tablet's canvas. The " +
                "window is drawn with a pen or a mouse, so it has no finger gestures to bind.",
        )
        DesktopSettingSection.SHORTCUTS -> ShortcutsSettings()
        DesktopSettingSection.NAVIGATION -> NavigationSettings(policy)
        DesktopSettingSection.APPEARANCE -> AppearanceSettings(policy)
        DesktopSettingSection.DOCUMENTS -> DocumentsSettings(policy)
        DesktopSettingSection.STORAGE -> StorageSettings()
        DesktopSettingSection.BACKUP -> BackupSettings()
        DesktopSettingSection.ABOUT -> AboutSettings()
    }
}

/** A section with nothing the window can answer: its title, and one sentence saying why. */
@Composable
private fun UnavailableSettings(title: String, note: String) {
    SettingsGroup(title) {
        SettingsUnavailable(label = title, note = note)
    }
}

/** **Stylus**: which stream the pen is read from; the feel is the reader's, not a preference here. */
@Composable
private fun StylusSettings(policy: DesktopSettingsState) {
    SettingsGroup("Pen") {
        SettingsChoice(
            label = "Stream",
            note = "Wintab carries the pressure at the tablet's own rate; Windows Ink is the pointer stream.",
            options = PenStream.entries,
            picked = policy.penStream,
            optionLabel = { it.label },
            onPick = policy::choosePenStream,
        )
        SettingsUnavailable(
            "Pressure curve",
            "The tablet applies a pressure multiplier and a floor; the window's stroke width follows " +
                "desktop Xournal++'s filter (docs/stylus.md), so there is no curve to set here yet.",
        )
        SettingsUnavailable(
            "Barrel button",
            "The pen's buttons are read by the driver and reach the window as the eraser tip.",
        )
    }
    SettingsNote("The status bar names the stream actually open, so a preference this host cannot honour says so.")
}

/** **Editor**: what the tablet's default-tool and snapping rows would set. */
@Composable
private fun EditorSettings() {
    SettingsGroup("Tools and snapping") {
        SettingsUnavailable(
            "Default tool",
            "The window opens on the pen, and the strips' own keys pick the rest (B H E V S X).",
        )
        SettingsUnavailable(
            "Snapping",
            "Figures snap to the page's pins while a connector is armed; there is no grid or angle " +
                "snapping to configure in this build.",
        )
    }
}

/** **Toolbar**: the one section the window has owned since the strips became a setting. */
@Composable
private fun ToolbarSettings(policy: DesktopSettingsState) {
    SettingsGroup("Tool strips") {
        SettingsChoice(
            label = "Placement",
            note = "Side is the tablet's rail; Top puts the same tools under the header; Both keeps each lane.",
            options = StripPlacement.entries,
            picked = policy.stripPlacement,
            optionLabel = { it.label },
            onPick = policy::chooseStripPlacement,
        )
        if (policy.stripPlacement.showsRail) {
            SettingsChoice(
                label = "Side",
                note = "The edge opposite your writing hand: Left for a right hand.",
                options = RailSide.entries,
                picked = policy.railSide,
                optionLabel = { it.label },
                onPick = policy::chooseRailSide,
            )
        }
        SettingsUnavailable(
            "Buttons",
            "The strips carry the tablet's sixteen tools in a fixed order; reordering slots is the " +
                "tablet's Toolbar section and has not been ported yet.",
        )
    }
}

/** **Figures**: how a drawn figure takes its stroke. */
@Composable
private fun FiguresSettings() {
    SettingsGroup("Figures") {
        SettingsRow(label = "Figure stroke", note = "A figure is drawn with the pen's current width.") {
            Text("Follows the pen", color = DesktopTheme.ink, style = DesktopTheme.numeric)
        }
        SettingsUnavailable(
            "Figure presets",
            "The tablet keeps named sizes for its shapes; the window draws each figure at the width " +
                "the strip shows.",
        )
    }
}

/** **Colors**: the palette lives in the strip, and this page says so rather than duplicating it. */
@Composable
private fun ColorsSettings(policy: DesktopSettingsState) {
    SettingsGroup("The palette") {
        SettingsRow(
            label = "Colour, width and line style",
            note = "The strip's colour capsule, and the three favourites on keys 1–3.",
        ) {
            Text("In the strip", color = DesktopTheme.ink, style = DesktopTheme.label)
        }
    }
    SettingsNote("A palette editor of its own would be the tablet's Colors section; the strip already edits the same three values.")
}

/** **Shortcuts**: the table exists and is documented; remapping it is the task that is not here yet. */
@Composable
private fun ShortcutsSettings() {
    SettingsGroup("The key table") {
        SettingsUnavailable(
            "Remap a command",
            "Every command has a key and the table is in docs/ui-modernization.md §3; detecting a new " +
                "key per command, with its conflict rule and a reset, is the tablet's Shortcuts section.",
        )
        SettingsRow(label = "The table", note = "Keys for the tools, the file commands and the view.") {
            Text("Documented", color = DesktopTheme.ink, style = DesktopTheme.label)
        }
    }
}

/** **Navigation**: how a document opens, and the page list that reaches the others. */
@Composable
private fun NavigationSettings(policy: DesktopSettingsState) {
    SettingsGroup("Opening a document") {
        SettingsChoice(
            label = "Zoom",
            note = "Automatic fits a tall page to the width and anything else to the page.",
            options = FitOnOpen.entries,
            picked = policy.fitOnOpen,
            optionLabel = { it.label },
            onPick = policy::chooseFitOnOpen,
        )
        SettingsNote("The next document you open; the one on screen keeps its zoom.")
    }
    SettingsGroup("Windows") {
        SettingsChoice(
            label = "Page list",
            note = "Automatic shows it once a document has more than one page.",
            options = SidebarPolicy.entries,
            picked = policy.sidebarPolicy,
            optionLabel = { it.label },
            onPick = policy::chooseSidebarPolicy,
        )
        SettingsUnavailable("Momentum", "The window scrolls and pans one step per gesture; the tablet's fling is not ported.")
    }
}

/** **Appearance**: the theme, the inspector column, and what the status bar reads. */
@Composable
private fun AppearanceSettings(policy: DesktopSettingsState) {
    SettingsGroup("Theme") {
        SettingsUnavailable(
            "Light / dark",
            "The window is dark only in this build — the light tokens the tablet uses are the task " +
                "that follows (desktop-light-theme-and-header-extras).",
        )
    }
    SettingsGroup("Status bar") {
        SettingsRow(
            label = "Frame time",
            note = "The paint time per frame, beside the display's own rate.",
            onClick = { policy.chooseFrameTime(!policy.frameTime) },
        ) {
            SettingsSwitch(policy.frameTime)
        }
        Readout("Display", refreshLabel(displayRefreshHz()))
        frameLabel(FrameMeter.averageMs, policy.frameTime)?.let { Readout("Painting", it) }
    }
    SettingsGroup("Inspector") {
        SettingsChoice(
            label = "Column",
            note = "The column that edits the element in your hand. Never keeps it shut.",
            options = InspectorPolicy.entries,
            picked = policy.inspectorPolicy,
            optionLabel = { it.label },
            onPick = policy::chooseInspectorPolicy,
        )
    }
}

/** **Documents**: the container a new file is written as, and the sheet it starts on. */
@Composable
private fun DocumentsSettings(policy: DesktopSettingsState) {
    SettingsGroup("The document") {
        SettingsRow(
            label = "New document",
            note = "A new tab is written as .xopp; Save As switches the container by the name you give it.",
        ) {
            Text(".xopp", color = DesktopTheme.ink, style = DesktopTheme.label)
        }
        SettingsRow(
            label = "Default page",
            note = "Set as default, in Page format, is what every new sheet is born with.",
        ) {
            Text(
                formatNumber(policy.pageWidthPt) + " × " + formatNumber(policy.pageHeightPt) + " pt",
                color = DesktopTheme.ink,
                style = DesktopTheme.numeric,
            )
        }
        SettingsUnavailable(
            "Format policy",
            "A document remembers its own format and Save As converts; there is no default to choose " +
                "until the new-document row above becomes a choice.",
        )
    }
}

/** **Storage**: the one file the window writes for itself. */
@Composable
private fun StorageSettings() {
    SettingsGroup("This window") {
        SettingsRow(label = "Settings file", note = "Read once, rewritten on every change.") {
            Text("desktop.properties", color = DesktopTheme.ink, style = DesktopTheme.label)
        }
        SettingsUnavailable(
            "Cache",
            "Thumbnails and page pictures live in memory for the session; the window writes no cache " +
                "of its own, so there is no size to reclaim.",
        )
    }
}

/** **Backup**: the recovery snapshot the window already writes. */
@Composable
private fun BackupSettings() {
    SettingsGroup("Unsaved work") {
        SettingsRow(
            label = "Recovery snapshot",
            note = "Written after each burst of edits, and offered back when the window reopens.",
        ) {
            Text("After every edit", color = DesktopTheme.ink, style = DesktopTheme.label)
        }
        SettingsUnavailable(
            "Interval",
            "The snapshot follows the edit's own quiet period; the tablet's timed autosave is not " +
                "ported, so there is nothing to set.",
        )
    }
}

/** **About**: what the window is, in the words the README uses. */
@Composable
private fun AboutSettings() {
    SettingsGroup("MobiXournal Desktop") {
        Readout("Version", DESKTOP_VERSION)
        Readout("Reads and writes", ".xopp · .mobix")
        Readout("This window", "read-only for now")
    }
    SettingsNote(
        "MobiXournal is an independent, unofficial note-taking and scientific drafting app; " +
            "not affiliated with or endorsed by the Xournal++ authors.",
    )
}

/** The version the packaged window shows — the one `compose.desktop`'s packageVersion carries. */
internal const val DESKTOP_VERSION = "1.4.0"
