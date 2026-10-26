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
            "curve active area eraser tip thickness",
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
 * **Stylus**: the stream the pen is read from, the pressure filter, and the area it maps.
 *
 * The three pressure rows are the tablet's own (`docs/stylus.md`): *sensitivity* decides whether a press
 * changes the width at all, and below it the two numbers of desktop Xournal++'s filter — the **floor** under
 * a light press and the **multiplier** above it. They stay on screen while sensitivity is off, because that
 * is the state the numbers are remembered in, and the curve under them shapes the *reading* before either
 * number applies.
 */
@Composable
private fun StylusSettings(policy: DesktopSettingsState) {
    StylusPenGroup(policy)
    StylusAreaGroup(policy)
    SettingsNote(
        "The status bar names the stream actually open, so a preference this host cannot honour says so. " +
            "An area the driver cannot map keeps the whole surface rather than leaving a dead pen.",
    )
}

/** The pen itself: the stream it is read from, and the four numbers of the pressure filter. */
@Composable
private fun StylusPenGroup(policy: DesktopSettingsState) {
    SettingsGroup("Pen") {
        SettingsChoice(
            label = "Stream",
            note = "Wintab carries the pressure at the tablet's own rate; Windows Ink is the pointer stream.",
            options = PenStream.entries,
            picked = policy.penStream,
            optionLabel = { it.label },
            onPick = policy::choosePenStream,
        )
        SettingsRow(
            label = "Pressure sensitivity",
            note = "Off draws every stroke at its set width, so a pen stroke and a figure match exactly.",
            onClick = { policy.choosePressureEnabled(!policy.pressureEnabled) },
        ) {
            SettingsSwitch(policy.pressureEnabled)
        }
        SettingsRow(
            label = "Minimum pressure",
            note = "The floor under a light press: the thinnest line pressure can draw.",
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
            note = "Above 1.00 a light press draws thicker — how a light writer reaches the full width.",
        ) {
            SettingsSlider(
                value = policy.pressureMultiplier,
                range = PressureCurve.MULTIPLIER_MIN..PressureCurve.MULTIPLIER_MAX,
                step = 0.05f,
                format = { String.format(Locale.ROOT, "%.2f×", it) },
                onValue = policy::choosePressureMultiplier,
            )
        }
        SettingsRow(
            label = "Pressure curve",
            note = "Shapes the reading before the two numbers above: below 1.00 a light press draws " +
                "thicker, above it thinner. 1.00 is the model's own arithmetic, untouched.",
        ) {
            SettingsSlider(
                value = policy.pressureExponent,
                range = PressureCurve.EXPONENT_MIN..PressureCurve.EXPONENT_MAX,
                step = 0.05f,
                format = { String.format(Locale.ROOT, "%.2f×", it) },
                onValue = policy::choosePressureExponent,
            )
        }
    }
}

/** Where on the tablet's surface the window listens: the whole desk, or a rectangle of it. */
@Composable
private fun StylusAreaGroup(policy: DesktopSettingsState) {
    SettingsGroup("Active area") {
        SettingsChoice(
            label = "Mapped surface",
            note = "Full is the driver's own surface, the one every other program sees; Tablet maps a " +
                "centred rectangle of it, so a small movement of the hand covers the page.",
            options = TabletArea.entries,
            picked = policy.activeArea,
            optionLabel = { it.label },
            onPick = policy::chooseActiveArea,
        )
        if (policy.activeArea == TabletArea.TABLET) {
            SettingsRow(
                label = "Width",
                note = "How much of the tablet's surface the window listens to, across.",
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
                note = "The same span down the surface.",
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
                note = "Both spans are percentages of the surface, so an equal pair is the tablet's own " +
                    "shape: the lock moves the height with the width.",
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
        SettingsChoice(
            label = "Placement",
            note = "Left or Right is the tablet's rail, on the edge opposite your writing hand; Top puts " +
                "the same tools in a ribbon under the header.",
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
                note = group.members.joinToString(" · ") { it.label },
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
            SettingsRow(
                label = "Factory order",
                note = "Put every slot back where the window draws it on a fresh install.",
                onClick = policy::resetToolOrder,
            ) {
                Text("Reset", color = DesktopTheme.accent, style = DesktopTheme.label)
            }
        }
    }
    SettingsNote("Both strips read the same arrangement, and a picker's chevron follows its slot.")
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
        SettingsChoice(
            label = "Preset",
            note = "The pen's three slots, applied to every figure tool. Figures ignore pressure.",
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
            note = "Any width between the slots, in points.",
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
    SettingsNote(
        "The pen's own widths are in the strips' colour capsule, per tool; this one is only what a figure, " +
            "a circuit or a placed element is drawn with.",
    )
}

/** **Colors**: the palette lives in the strip, and this page says so rather than duplicating it. */
@Composable
private fun ColorsSettings() {
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
            note = "F11, or this row: the window takes the whole screen and asks the display for the " +
                "target rate.",
            onClick = policy::toggleFullScreen,
        ) {
            SettingsSwitch(policy.fullScreen)
        }
        // Present only while that mode is in force: a target rate a windowed window cannot apply would
        // be exactly the setting this section exists to avoid.
        if (policy.fullScreen) {
            SettingsChoice(
                label = "Target rate",
                note = "Asked of the display while the window is full screen. A panel without that " +
                    "rate keeps its own, and the readout below still says which.",
                options = TARGET_HZ,
                picked = policy.targetHz,
                optionLabel = { "$it Hz" },
                onPick = policy::chooseTargetHz,
            )
        }
        SettingsRow(
            label = "Frame time",
            note = "The paint time per frame, beside the display's own rate.",
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
        SettingsChoice(
            label = "New document",
            note = "What every new tab is written as. A .mobix keeps figures and tables as recipes; a " +
                "zipped .xopp carries its attachments inside one file. Save As switches either way.",
            options = DesktopNewDocumentFormat.entries,
            picked = policy.newDocumentFormat,
            optionLabel = { it.label },
            onPick = policy::chooseNewDocumentFormat,
        )
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
        SettingsRow(
            label = "Export settings…",
            note = "Writes every choice in this window to a file you pick.",
            onClick = { policy.pickBackupFile?.invoke(true) { file -> file?.let(policy::exportBackup) } },
        ) {
            Text("Choose…", color = DesktopTheme.accent, style = DesktopTheme.label)
        }
        SettingsRow(
            label = "Import settings…",
            note = "Reads a backup back. A file that is not one is refused, and nothing changes.",
            onClick = { policy.pickBackupFile?.invoke(false) { file -> file?.let(policy::importBackup) } },
        ) {
            Text("Choose…", color = DesktopTheme.accent, style = DesktopTheme.label)
        }
        policy.backupNotice?.let { SettingsNote(it) }
    }
    SettingsGroup("Unsaved work") {
        SettingsRow(
            label = "Recovery snapshot",
            note = "Written after each burst of edits, and offered back when the window reopens.",
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
        Readout("This window", "read-only for now")
    }
    SettingsNote(
        "MobiXournal is an independent, unofficial note-taking and scientific drafting app; " +
            "not affiliated with or endorsed by the Xournal++ authors.",
    )
}

/** The version the packaged window shows — the one `compose.desktop`'s packageVersion carries. */
internal const val DESKTOP_VERSION = "1.0.0"
