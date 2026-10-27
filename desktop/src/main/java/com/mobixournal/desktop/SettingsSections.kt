package com.mobixournal.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.render.PressureCurve
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The index's **four areas** — the tablet's own split, under the tablet's own words.
 *
 * Ten names in one column is a list read top to bottom; four short groups is a list scanned by its shape, and
 * a reader who uses both shells finds the same subjects under the same headings. The areas are the phone's
 * minus the pages this shell does not carry (`docs/desktop-settings.md`).
 */
enum class DesktopSettingArea(val title: String) {
    INPUT("Input"),
    DRAWING("Drawing"),
    INTERFACE("Interface"),
    APP("App & data"),
}

/**
 * The **sections** of the window's Settings: the page of controls for one topic.
 *
 * The list is the tablet's list **minus what a window cannot answer**: a phone's Editor, Gestures and
 * Storage pages configure a fingertip, a tool default and a cache that this shell has no row for, and a
 * page of "Tablet only" rows is a page the reader has to learn to skip. What remains is what a desktop can
 * actually decide — the pen, the strips, figures, the palette, keys, navigation, appearance, documents,
 * backup and the app — and every one of those rows has a mechanism behind it
 * (`docs/interface-contract.md` §9).
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
        "The pen stream, how hard it presses and the area it reads.",
        "tablet pen stylus wintab windows ink pressure stream driver sensitivity multiplier minimum " +
            "curve active area eraser tip thickness preset presets name save profile",
        SettingsGlyph.STYLUS,
    ),
    TOOLBAR(
        "Toolbar",
        "Where the tool strips stand and the order of their buttons.",
        "toolbar tools strip rail ribbon top side left right placement layout buttons order reorder",
        SettingsGlyph.TOOLBAR,
    ),
    FIGURES(
        "Figures",
        "The width the figure tools draw at.",
        "figures shapes size stroke width presets geometry",
        SettingsGlyph.FIGURES,
    ),
    COLORS(
        "Colors",
        "The pen's colour, width, line style and favourites.",
        "colours colors palette swatch favourites favorites width thickness line style dashed",
        SettingsGlyph.COLORS,
    ),
    SHORTCUTS(
        "Shortcuts",
        "The key table, remapped per command.",
        "shortcuts keys keyboard binding remap conflict reset",
        SettingsGlyph.SHORTCUTS,
    ),
    NAVIGATION(
        "Navigation",
        "How a document opens and how the page list behaves.",
        "navigation zoom fit page width panning scrolling sidebar page list",
        SettingsGlyph.NAVIGATION,
    ),
    APPEARANCE(
        "Appearance",
        "The theme, and what the status bar reads.",
        "appearance theme status bar readout frame time paint refresh display",
        SettingsGlyph.APPEARANCE,
    ),
    DOCUMENTS(
        "Documents",
        "The format a new document is written in, and its page.",
        "documents format mobix xopp zipped package new default page size paper",
        SettingsGlyph.DOCUMENTS,
    ),
    BACKUP(
        "Backup",
        "The recovery snapshot, and a file that carries your settings.",
        "backup recovery snapshot crash restore unsaved settings export import file transfer",
        SettingsGlyph.BACKUP,
    ),
    ABOUT(
        "About",
        "The version, the formats it reads, and what it is.",
        "about version build formats licence license independent unofficial",
        SettingsGlyph.ABOUT,
    ),
    ;

    /** The area of the index this section is listed under. */
    val area: DesktopSettingArea
        get() = when (this) {
            STYLUS, SHORTCUTS -> DesktopSettingArea.INPUT
            FIGURES, COLORS -> DesktopSettingArea.DRAWING
            TOOLBAR, APPEARANCE, NAVIGATION -> DesktopSettingArea.INTERFACE
            DOCUMENTS, BACKUP, ABOUT -> DesktopSettingArea.APP
        }

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
    // The title only: the section's one-line summary lives in the panel's help line, at rest, under the
    // page — a sentence under the title as well would spend a row saying the same thing twice.
    Text(section.title, color = DesktopTheme.ink, style = DesktopTheme.title)
    when (section) {
        DesktopSettingSection.STYLUS -> StylusSettings(policy)
        DesktopSettingSection.TOOLBAR -> ToolbarSettings(policy)
        DesktopSettingSection.FIGURES -> FiguresSettings(policy)
        DesktopSettingSection.COLORS -> ColorsSettings()
        DesktopSettingSection.SHORTCUTS -> ShortcutsSettings(policy)
        DesktopSettingSection.NAVIGATION -> NavigationSettings(policy)
        DesktopSettingSection.APPEARANCE -> AppearanceSettings(policy)
        DesktopSettingSection.DOCUMENTS -> DocumentsSettings(policy)
        DesktopSettingSection.BACKUP -> BackupSettings(policy)
        DesktopSettingSection.ABOUT -> AboutSettings()
    }
}

/**
 * **Stylus**: the pressure the pen draws with, and the area it maps.
 *
 * **One group for the filter** — the stream it is read from, whether a press changes the width, the preset
 * that names the two numbers, then those two numbers — because a preset *is* those numbers: a reader who
 * met the list and the sliders as two groups had to work out that they described one pen. The area the
 * device reads is the second subject: the hand decides how hard it presses, the surface it is read from
 * decides how far that hand travels.
 *
 * The rows are the tablet's own (`docs/stylus.md`): *sensitivity* decides whether a press changes the
 * width, and the floor and the multiplier are desktop Xournal++'s filter. They stay on screen while
 * sensitivity is off, because that is the state the numbers are remembered in.
 *
 * **The notes are one clause each.** A settings page is scanned, not read: a row that needs a paragraph is
 * a row whose name is wrong, and ten pages of paragraphs is how this list came to feel like a manual.
 */
@Composable
private fun StylusSettings(policy: DesktopSettingsState) {
    StylusPressureGroup(policy)
    StylusAreaGroup(policy)
    SettingsNote("The status bar names the stream actually open.")
}

/**
 * **Pressure**: what the pen is read from, the preset that names its two numbers, and those numbers.
 *
 * Order is the order the question is met in — the stream, whether pressure counts at all, then *which* pair
 * of numbers, then the pair itself — so the two sliders sit under the carousel that decides whose numbers
 * they are, and their notes say so. The carousel owns deleting and adding a preset (the × on the chip, the
 * `+` after the last one), and these two rows write that same live entry, so *Reset* is the only command
 * left down here: the tablet's own escape from a slider dragged far.
 */
@Composable
private fun StylusPressureGroup(policy: DesktopSettingsState) {
    SettingsGroup("Pressure") {
        SettingsSelect(
            label = "Stream",
            note = "Wintab reads the driver's packets; Windows Ink goes through the system.",
            options = PenStream.entries,
            picked = policy.penStream,
            optionLabel = { it.label },
            onPick = policy::choosePenStream,
        )
        SettingsRow(
            label = "Pressure sensitivity",
            note = "Off draws every stroke at its set width.",
            onClick = { policy.choosePressureEnabled(!policy.pressureEnabled) },
        ) {
            SettingsSwitch(policy.pressureEnabled)
        }
        StylusPresetCarousel(policy)
        SettingsRow(
            label = "Minimum pressure",
            note = "The thinnest line a light press can draw, on the preset in use.",
        ) {
            SettingsSlider(
                value = policy.minimumPressure,
                range = PressureCurve.MINIMUM_PRESSURE_MIN..PressureCurve.MINIMUM_PRESSURE_MAX,
                step = 0.01f,
                format = { String.format(Locale.ROOT, "%.2f", it) },
                onValue = policy::chooseMinimumPressure,
            )
        }
        SettingsRow(
            label = "Multiplier",
            note = "How far above 1.00 a light press can reach, on the preset in use.",
        ) {
            SettingsSlider(
                value = policy.pressureMultiplier,
                range = PressureCurve.MULTIPLIER_MIN..PressureCurve.MULTIPLIER_MAX,
                step = 0.05f,
                format = { String.format(Locale.ROOT, "%.2f×", it) },
                onValue = policy::choosePressureMultiplier,
            )
        }
        SettingsCommand(
            label = "Reset to defaults",
            note = "The factory floor and multiplier, back on the preset in use.",
            onClick = policy::resetPressureToDefaults,
        )
    }
}

/** Where on the tablet's surface the window listens: the whole desk, or a rectangle of it. */
@Composable
private fun StylusAreaGroup(policy: DesktopSettingsState) {
    SettingsGroup("Active area") {
        SettingsSelect(
            label = "Mapped surface",
            note = "Full is the driver's whole surface; Tablet maps a centred part of it.",
            options = TabletArea.entries,
            picked = policy.activeArea,
            optionLabel = { it.label },
            onPick = policy::chooseActiveArea,
        )
        if (policy.activeArea == TabletArea.TABLET) {
            SettingsRow(
                label = "Width",
                note = "Share of the surface the window listens to.",
            ) {
                SettingsSlider(
                    value = policy.areaWidthPercent.toFloat(),
                    range = DesktopSettings.AREA_PERCENT_RANGE.start.toFloat()..
                        DesktopSettings.AREA_PERCENT_RANGE.endInclusive.toFloat(),
                    step = 5f,
                    format = { String.format(Locale.ROOT, "%d%%", it.roundToInt()) },
                    onValue = { policy.chooseAreaWidth(it.roundToInt()) },
                )
            }
            SettingsRow(
                label = "Height",
                note = "The same, down the surface.",
            ) {
                SettingsSlider(
                    value = policy.areaHeightPercent.toFloat(),
                    range = DesktopSettings.AREA_PERCENT_RANGE.start.toFloat()..
                        DesktopSettings.AREA_PERCENT_RANGE.endInclusive.toFloat(),
                    step = 5f,
                    format = { String.format(Locale.ROOT, "%d%%", it.roundToInt()) },
                    onValue = { policy.chooseAreaHeight(it.roundToInt()) },
                )
            }
            SettingsRow(
                label = "Aspect lock",
                note = "Moves the height with the width, keeping the tablet's own shape.",
                onClick = { policy.chooseAspectLock(!policy.aspectLock) },
            ) {
                SettingsSwitch(policy.aspectLock)
            }
        }
    }
}

/**
 * **Toolbar**: where the strips stand, and the order of their buttons.
 *
 * One row of three places — the rail on either edge, or the ribbon under the header — because the side is
 * part of the placement, not a second question. The order below it is the tablet's own *Buttons* idea: each
 * slot of the strip is a row with a pair of chevrons, and the strips read the arrangement the moment it
 * changes ([DesktopSettingsState.toolGroups]).
 */
@Composable
private fun ToolbarSettings(policy: DesktopSettingsState) {
    SettingsGroup("Tool strips") {
        SettingsSelect(
            label = "Placement",
            note = "Left or Right is the rail; Top is a ribbon under the header.",
            options = StripPlacement.entries,
            picked = policy.stripPlacement,
            optionLabel = { it.label },
            onPick = policy::chooseStripPlacement,
        )
    }
    val groups = policy.toolGroups
    SettingsGroup("Buttons") {
        for ((index, group) in groups.withIndex()) {
            SettingsRow(
                label = group.face.label,
                // Only a slot that holds more than one tool says what it holds: "Pen — Pen" is a line
                // that teaches nothing and makes the list twice as tall as it needs to be.
                note = group.members
                    .takeIf { it.size > 1 }
                    ?.joinToString(" · ") { it.label }
                    .orEmpty(),
            ) {
                SettingsStepButtons(
                    upEnabled = index > 0,
                    downEnabled = index < groups.lastIndex,
                    onUp = { policy.moveToolSlot(index, -1) },
                    onDown = { policy.moveToolSlot(index, 1) },
                )
            }
        }
        if (policy.toolOrder.isNotEmpty()) {
            SettingsCommand(
                label = "Restore factory order",
                note = "Every slot back where a fresh install draws it.",
                onClick = policy::resetToolOrder,
            )
        }
    }
}

/**
 * **Figures**: the width the shape tools draw at.
 *
 * A figure has no pressure — it is drawn at its full width — so a rectangle would otherwise always be as
 * thick as the pen's *slot*, and a light pen stroke beside it reads thinner. Its width is therefore its own
 * value: the tablet's three S/M/L slots as presets, and a slider for anything between them.
 */
@Composable
private fun FiguresSettings(policy: DesktopSettingsState) {
    val widths = PenPalette.DEFAULT_WIDTHS
    val letters = listOf("S", "M", "L")
    SettingsGroup("Figure width") {
        SettingsSelect(
            label = "Preset",
            note = "The pen's three slots, for every figure tool — figures ignore pressure.",
            options = widths.indices.toList(),
            picked = PenPalette.widthSlot(policy.figureWidth),
            optionLabel = { index ->
                val name = letters.getOrNull(index) ?: "${index + 1}"
                "$name — ${String.format(Locale.ROOT, "%.1f", widths[index])} pt"
            },
            onPick = { index -> policy.chooseFigureWidth(widths[index]) },
        )
        SettingsRow(
            label = "Width",
            note = "Anything between the slots, in points.",
        ) {
            SettingsSlider(
                value = policy.figureWidth,
                range = widths.first()..widths.last(),
                step = 0.25f,
                format = { String.format(Locale.ROOT, "%.2f pt", it) },
                onValue = policy::chooseFigureWidth,
            )
        }
    }
    SettingsNote("The pen's own widths stay in the strip's colour capsule.")
}

/** **Colors**: the palette lives in the strip, and this page says so rather than duplicating it. */
@Composable
private fun ColorsSettings() {
    SettingsGroup("The palette") {
        SettingsRow(
            label = "Colour, width and line style",
            note = "The strip's colour capsule, and the keys 1–3.",
        ) {
            Text("In the strip", color = DesktopTheme.ink, style = DesktopTheme.label)
        }
    }
}

/** **Navigation**: how a document opens, and the page list that reaches the others. */
@Composable
private fun NavigationSettings(policy: DesktopSettingsState) {
    SettingsGroup("Opening a document") {
        SettingsSelect(
            label = "Zoom",
            note = "Automatic: a tall page to the width, the rest to the page.",
            options = FitOnOpen.entries,
            picked = policy.fitOnOpen,
            optionLabel = { it.label },
            onPick = policy::chooseFitOnOpen,
        )
    }
    SettingsGroup("Windows") {
        SettingsSelect(
            label = "Page list",
            note = "Automatic shows it once a document has more than one page.",
            options = SidebarPolicy.entries,
            picked = policy.sidebarPolicy,
            optionLabel = { it.label },
            onPick = policy::chooseSidebarPolicy,
        )
    }
}

/** **Appearance**: the theme, and what the status bar reads. */
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
        // The desk, and the one thing that can only be asked of a full-screen window: the panel's rate.
        SettingsRow(
            label = "Full screen",
            note = "F11, or this row. Asks the display for the target rate.",
            onClick = policy::toggleFullScreen,
        ) {
            SettingsSwitch(policy.fullScreen)
        }
        // Present only while that mode is in force: a target rate a windowed window cannot apply would
        // be exactly the setting this section exists to avoid.
        if (policy.fullScreen) {
            SettingsSelect(
                label = "Target rate",
                note = "Asked only while full screen; a panel without it keeps its own.",
                options = TARGET_HZ,
                picked = policy.targetHz,
                optionLabel = { "$it Hz" },
                onPick = policy::chooseTargetHz,
            )
        }
        SettingsRow(
            label = "Frame time",
            note = "Paint time per frame, beside the display's rate.",
            onClick = { policy.chooseFrameTime(!policy.frameTime) },
        ) {
            SettingsSwitch(policy.frameTime)
        }
        // What the panel is *really* at — never the target. A refused mode read back as applied is the one
        // lie a refresh readout must not tell.
        Readout("Display", refreshLabel(displayRefreshHz()))
        frameLabel(FrameMeter.averageMs, policy.frameTime)?.let { Readout("Painting", it) }
    }
}

/** **Documents**: the container a new file is written as, and the sheet it starts on. */
@Composable
private fun DocumentsSettings(policy: DesktopSettingsState) {
    SettingsGroup("The document") {
        // The three answers are phrases, which is what made a row of pills read as three big buttons; every
        // choice of the window is a select now (see `SettingsSelect`).
        SettingsSelect(
            label = "New document",
            note = ".mobix keeps figures and tables live; zipped .xopp carries its files.",
            options = DesktopNewDocumentFormat.entries,
            picked = policy.newDocumentFormat,
            optionLabel = { it.label },
            onPick = policy::chooseNewDocumentFormat,
        )
        SettingsRow(
            label = "Default page",
            note = "The size new sheets are born with (set it in Page format).",
        ) {
            Text(
                formatNumber(policy.pageWidthPt) + " × " + formatNumber(policy.pageHeightPt) + " pt",
                color = DesktopTheme.ink,
                style = DesktopTheme.numeric,
            )
        }
        Readout("Destination", "Documents\\MobiXournal\\Untitled n" + policy.newDocumentFormat.extension)
    }
}

/**
 * **Backup**: what keeps unsaved work, and a file that carries every setting to another machine.
 *
 * The two settings rows are the tablet's Backup section on a desktop: export writes the whole
 * configuration (`DesktopSettingsBackup`) and import reads one back, refusing anything this build cannot
 * understand with the reason. The answer is a sentence under the rows, not a dialog.
 */
@Composable
private fun BackupSettings(policy: DesktopSettingsState) {
    SettingsGroup("Settings backup") {
        SettingsCommand(
            label = "Export settings…",
            note = "Writes every choice in this window to a file.",
            onClick = { policy.pickBackupFile?.invoke(true) { file -> file?.let(policy::exportBackup) } },
        )
        SettingsCommand(
            label = "Import settings…",
            note = "Reads one back; anything else is refused.",
            onClick = { policy.pickBackupFile?.invoke(false) { file -> file?.let(policy::importBackup) } },
        )
        policy.backupNotice?.let { SettingsNote(it) }
    }
    SettingsGroup("Unsaved work") {
        SettingsRow(
            label = "Recovery snapshot",
            note = "Written after each burst of edits, offered on reopen.",
        ) {
            Text("After every edit", color = DesktopTheme.ink, style = DesktopTheme.label)
        }
    }
}

/** **About**: what the window is, in the words the README uses. */
@Composable
private fun AboutSettings() {
    SettingsGroup("MobiXournal Desktop") {
        Readout("Version", DESKTOP_VERSION)
        Readout("Reads and writes", ".xopp · .mobix")
    }
    SettingsNote("MobiXournal is an independent, unofficial note-taking and scientific drafting app.")
}

/** The version the packaged window shows — the one `compose.desktop`'s packageVersion carries. */
internal const val DESKTOP_VERSION = "1.0.0"
