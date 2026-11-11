package com.mobixournal.desktop.settings
import com.mobixournal.design.BuildVersions
import com.mobixournal.desktop.ink.FrameMeter
import com.mobixournal.desktop.ink.TabletArea
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.AudioDevices
import com.mobixournal.desktop.io.DesktopAudioSession
import com.mobixournal.desktop.io.save
import com.mobixournal.desktop.shell.TARGET_HZ
import com.mobixournal.desktop.shell.displayRefreshHz
import com.mobixournal.desktop.shell.frameLabel
import com.mobixournal.desktop.shell.refreshLabel
import com.mobixournal.desktop.tools.FiguresSettings
import com.mobixournal.desktop.tools.formatNumber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.render.PressureCurve
import java.util.Locale
import kotlin.math.roundToInt

/** Desktop task areas, in their visible navigation order; section IDs remain stable. */
enum class DesktopSettingArea(val title: String) {
    INTERFACE("Workspace"),
    INPUT("Input"),
    DRAWING("Drawing"),
    APP("Files & app"),
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
        "The width the figure tools draw at, and each figure's own variant.",
        "figures shapes size stroke width presets geometry triangle trapezoid polygon sides " +
            "variants angles recognize recognition",
        SettingsGlyph.FIGURES,
    ),
    COLORS(
        "Colors",
        "The palette, the pen's colour and width, the line style and the favourites.",
        "colours colors palette swatch favourites favorites add remove delete redefine restore reset " +
            "width thickness line style dashed",
        SettingsGlyph.COLORS,
    ),
    RING(
        "Ring",
        "What the pen-tip ring holds, and the key that opens it.",
        "ring radial hud pen-tip wheel summon key swatches widths tools pen highlighter eraser",
        SettingsGlyph.RING,
    ),
    SHORTCUTS(
        "Shortcuts",
        "The key table, remapped per command.",
        "shortcuts keys keyboard binding remap conflict reset",
        SettingsGlyph.SHORTCUTS,
    ),
    AUDIO(
        "Audio",
        "The microphone it records from and the output it plays through.",
        "audio microphone mic input output device speaker headphones level meter record playback",
        SettingsGlyph.AUDIO,
    ),
    NAVIGATION(
        "Navigation",
        "Page layout, zoom steps and smooth scrolling.",
        "navigation zoom fit page width panning scrolling sidebar page list smooth scroll distance wheel notch",
        SettingsGlyph.NAVIGATION,
    ),
    APPEARANCE(
        "Appearance",
        "The theme, and what the status bar reads.",
        "appearance theme dark light system status bar readout frame time paint refresh display",
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
        "Export, import and reset your application settings.",
        "backup restore settings export import file transfer reset",
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
            STYLUS, SHORTCUTS, RING -> DesktopSettingArea.INPUT
            FIGURES, COLORS -> DesktopSettingArea.DRAWING
            TOOLBAR, APPEARANCE, NAVIGATION -> DesktopSettingArea.INTERFACE
            DOCUMENTS, AUDIO, BACKUP, ABOUT -> DesktopSettingArea.APP
        }

    /** Whether this section answers [needle] — its title, its summary, aliases or commands. */
    fun matches(needle: String): Boolean {
        if (needle.isBlank()) return true
        val haystack = "$title $summary $keywords ${area.title}".lowercase()
        return needle.trim().lowercase().split(Regex("\\s+")).all { term ->
            term in haystack || (this == SHORTCUTS && ShortcutTable.entries.any { ShortcutFamilies.matches(it, term) })
        }
    }

    companion object {
        /** One order for the visible index, searches and their fallback page. */
        val ORDERED: List<DesktopSettingSection> = listOf(
            APPEARANCE, TOOLBAR, NAVIGATION, STYLUS, SHORTCUTS, RING,
            FIGURES, COLORS, DOCUMENTS, AUDIO, BACKUP, ABOUT,
        )
        /** The section a window opens on: the **pen**, the page every session starts from. */
        val DEFAULT: DesktopSettingSection = STYLUS

        /** The sections the search field leaves, in the index's own order. */
        fun search(query: String): List<DesktopSettingSection> = ORDERED.filter { it.matches(query) }
    }
}

/**
 * The page of one section: its title and summary, then its groups.
 *
 * A null [section] is the search's own answer — the field left nothing to show — and it is a sentence
 * rather than a blank page, so the reader knows the word was the problem and not the window.
 */
@Composable
internal fun DesktopSettingPage(
    policy: DesktopSettingsState,
    palette: PaletteActions,
    section: DesktopSettingSection?,
) {
    if (section == null) {
        Text("No settings found. Try theme, pressure or scrolling.", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        return
    }
    // The title only: the section's one-line summary lives in the panel's help line, at rest, under the
    // page — a sentence under the title as well would spend a row saying the same thing twice.
    Text(section.title, color = DesktopTheme.ink, style = DesktopTheme.title)
    Spacer(Modifier.height(6.dp))
    Text(section.summary, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
    when (section) {
        DesktopSettingSection.STYLUS -> StylusSettings(policy)
        DesktopSettingSection.TOOLBAR -> ToolbarSettings(policy)
        DesktopSettingSection.FIGURES -> FiguresSettings(policy)
        DesktopSettingSection.COLORS -> ColorsSettings(palette)
        DesktopSettingSection.RING -> RingSettings(policy)
        DesktopSettingSection.SHORTCUTS -> ShortcutsSettings(policy)
        DesktopSettingSection.AUDIO -> AudioSettings(policy)
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
            note = "Wintab uses direct driver packets; Windows Ink uses system pointer input.",
            options = PenStream.entries,
            picked = policy.penStream,
            optionLabel = { it.label },
            onPick = policy::choosePenStream,
        )
        SettingsRow(
            label = "Pressure sensitivity",
            note = "Vary line width by pen pressure; off draws uniform strokes.",
            checked = policy.pressureEnabled,
            onClick = { policy.choosePressureEnabled(!policy.pressureEnabled) },
        ) {
            SettingsSwitch(policy.pressureEnabled)
        }
        StylusPresetCarousel(policy)
        SettingsRow(
            label = "Minimum pressure",
            note = "Floor threshold: the thinnest stroke drawn under light contact.",
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
            note = "Pressure sensitivity curve multiplier for light and medium strokes.",
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
            note = "Restore factory pressure floor and multiplier for the active preset.",
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
            note = "Full uses the complete tablet surface; Tablet maps a centered portion.",
            options = TabletArea.entries,
            picked = policy.activeArea,
            optionLabel = { it.label },
            onPick = policy::chooseActiveArea,
        )
        if (policy.activeArea == TabletArea.TABLET) {
            SettingsRow(
                label = "Width",
                note = "Horizontal percentage of the tablet surface mapped to the screen.",
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
                note = "Vertical percentage of the tablet surface mapped to the screen.",
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
                note = "Maintain the tablet's physical aspect ratio when resizing.",
                checked = policy.aspectLock,
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
 * slot of the strip is a row a reader can **drag by its handle** or step with a pair of chevrons, and the
 * strips read the arrangement the moment it changes ([DesktopSettingsState.toolGroups]).
 */
@Composable
private fun ToolbarSettings(policy: DesktopSettingsState) {
    SettingsGroup("Tool strips") {
        SettingsSelect(
            label = "Placement",
            note = "Left or Right docks a rail; Top positions a ribbon under the header.",
            options = StripPlacement.entries,
            picked = policy.stripPlacement,
            optionLabel = { it.label },
            onPick = policy::chooseStripPlacement,
        )
    }
    SettingsGroup("Buttons") {
        // The rows are the strip's own contents in the strip's own order ([stripItems]), and each tool
        // slot carries the drag handle beside its chevrons (`ToolbarOrderRows`).
        ToolbarOrderRows(policy)
        if (policy.toolOrder.isNotEmpty()) {
            SettingsCommand(
                label = "Reset to defaults",
                note = "Restore tool slot positions to factory layout.",
                onClick = policy::resetToolOrder,
            )
        }
    }
}

/** **Appearance**: the theme, and what the status bar reads. */
@Composable
private fun AppearanceSettings(policy: DesktopSettingsState) {
    SettingsGroup("Workspace") {
        SettingsSelect(
            label = "Theme",
            note = "Application theme. System matches Windows appearance.",
            options = DesktopThemeMode.entries,
            picked = policy.appearance.theme,
            optionLabel = { it.label },
            onPick = policy.appearance::chooseTheme,
        )
    }
    SettingsGroup("Display") {
        // The desk, and the one thing that can only be asked of a full-screen window: the panel's rate.
        SettingsRow(
            label = "Full screen",
            note = "F11 or toggle here. Enables high-refresh display modes.",
            checked = policy.fullScreen,
            onClick = policy::toggleFullScreen,
        ) {
            SettingsSwitch(policy.fullScreen)
        }
        // Present only while that mode is in force: a target rate a windowed window cannot apply would
        // be exactly the setting this section exists to avoid.
        if (policy.fullScreen) {
            SettingsSelect(
                label = "Target rate",
                note = "Requested display refresh rate while in full screen mode.",
                options = TARGET_HZ,
                picked = policy.targetHz,
                optionLabel = { "$it Hz" },
                onPick = policy::chooseTargetHz,
            )
        }
        SettingsReadout("Display", refreshLabel(displayRefreshHz()))
    }
    SettingsGroup("Status bar") {
        SettingsRow(
            label = "Frame time",
            note = "Display render time per frame alongside display refresh rate.",
            checked = policy.frameTime,
            onClick = { policy.chooseFrameTime(!policy.frameTime) },
        ) {
            SettingsSwitch(policy.frameTime)
        }
        // What the panel is *really* at — never the target. A refused mode read back as applied is the one
        // lie a refresh readout must not tell.
        frameLabel(FrameMeter.averageMs, policy.frameTime)?.let { SettingsReadout("Painting", it) }
    }
}

/** **Documents**: the container a new file is written as, and the sheet it starts on. */
@Composable
private fun DocumentsSettings(policy: DesktopSettingsState) {
    SettingsGroup("Default document") {
        // The three answers are phrases, which is what made a row of pills read as three big buttons; every
        // choice of the window is a select now (see `SettingsSelect`).
        SettingsSelect(
            label = "New document",
            note = ".mobix preserves editable figures and tables; zipped .xopp bundles files.",
            options = DesktopNewDocumentFormat.entries,
            picked = policy.newDocumentFormat,
            optionLabel = { it.label },
            onPick = policy::chooseNewDocumentFormat,
        )
        SettingsRow(
            label = "Default page",
            note = "Starting dimensions for new pages (configured in Page format).",
        ) {
            Text(
                formatNumber(policy.pageWidthPt) + " × " + formatNumber(policy.pageHeightPt) + " pt",
                color = DesktopTheme.ink,
                style = DesktopTheme.numeric,
            )
        }
        // Off until asked, and never a way to name a document: one with no file waits for Save, because where
        // it lives is the reader's answer (`AutoSavePolicy`).
        SettingsRow(
            label = "Save automatically",
            note = "Writes the document after you stop writing. Only for one that already has a file.",
            checked = policy.autoSave,
            onClick = { policy.pickAutoSave(!policy.autoSave) },
        ) {
            SettingsSwitch(policy.autoSave)
        }
        SettingsReadout("Destination", "Documents\\MobiXournal\\Untitled n" + policy.newDocumentFormat.extension)
    }
}

/**
 * **Audio**: the microphone a take comes from, the output a clip sounds through, and the meter that shows
 * whether the microphone is really hearing anything.
 *
 * Both rows are the platform's **own device names** ([AudioDevices]) with the system default as the first
 * answer, because that is the answer a fresh install has. A device that has disappeared is dropped to the
 * default **here**, on the page that shows it, and the note under the row says which name went missing —
 * the recorder is then never asked to open something the window already knows is gone
 * (`DesktopSettingsState.refreshAudioDevices`).
 *
 * The meter is the take's own peak ([DesktopAudioSession.level]), so the page answers the one question a
 * device list cannot: is this microphone picking anything up? It reads zero until a take runs, and the
 * note says so rather than showing a dead bar with no explanation.
 */
@Composable
private fun AudioSettings(policy: DesktopSettingsState) {
    // Read again on the way in: a headset plugged in while the window runs is in this list.
    LaunchedEffect(policy) { policy.audio.refresh() }
    val audio = policy.audio
    SettingsGroup("Devices") {
        SettingsSelect(
            label = "Microphone",
            note = deviceNote(
                picked = audio.input,
                fellBackFrom = audio.inputFellBackFrom,
                missing = "Previously selected microphone is unavailable; using system default.",
            ),
            options = listOf("") + audio.devices.inputs,
            picked = audio.input,
            optionLabel = ::deviceLabel,
            onPick = audio::chooseInput,
        )
        SettingsSelect(
            label = "Output",
            note = deviceNote(
                picked = audio.output,
                fellBackFrom = audio.outputFellBackFrom,
                missing = "Previously selected output is unavailable; using system default.",
            ),
            options = listOf("") + audio.devices.outputs,
            picked = audio.output,
            optionLabel = ::deviceLabel,
            onPick = audio::chooseOutput,
        )
    }
    SettingsGroup("Recording") {
        SettingsRow(
            label = "Input level",
            note = if (DesktopAudioSession.recording) {
                "Live microphone input level."
            } else {
                "Start a recording from the toolbar to monitor input level."
            },
        ) {
            PillLevelMeter(if (DesktopAudioSession.recording) DesktopAudioSession.level else 0f)
        }
        SettingsReadout("Recording format", "44.1 kHz · mono · 16-bit WAV")
        SettingsReadout("Recordings", "%APPDATA%\\MobiXournal\\audio")
    }
    // The tablet's *Save separate audio*: the take leaves the window's own store, so another program
    // (or another machine) can play it. On by the reader's choice, never by itself.
    SettingsGroup("Export recordings") {
        SettingsRow(
            label = "Save separate audio",
            note = "Export standalone audio copies for playback outside the app.",
            checked = audio.saveExternally,
            onClick = { audio.chooseSaveExternally(!audio.saveExternally) },
        ) {
            SettingsSwitch(audio.saveExternally)
        }
        SettingsRow(
            label = "Folder",
            note = audio.exportFolder.ifBlank {
                "Choose a destination directory to enable standalone audio export."
            },
            onClick = { policy.audio.pickAudioFolder?.invoke() },
        ) {
            Text(
                if (audio.exportFolder.isBlank()) "Choose…" else "Change…",
                color = DesktopTheme.accent,
                style = DesktopTheme.label,
            )
        }
    }
}

/** `System default` for the empty answer — the name the store keeps for "let the platform choose". */
private fun deviceLabel(name: String): String = name.ifEmpty { "System default" }

/** The row's note: the ordinary sentence, or the one that says which device went missing. */
private fun deviceNote(picked: String, fellBackFrom: String?, missing: String): String = when {
    fellBackFrom != null -> missing
    picked.isEmpty() -> "System default device."
    else -> "Active device for recordings and playback."
}

/** A thin level bar for the Audio page — the take's peak, drawn as the capsule's own bar is. */
@Composable
private fun PillLevelMeter(level: Float) {
    Box(
        Modifier
            .width(120.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(DesktopTheme.activeSlot),
    ) {
        Box(
            Modifier
                .width((120f * level.coerceIn(0f, 1f)).dp)
                .height(6.dp)
                .background(DesktopTheme.accent),
        )
    }
}

/**
 * **Backup**: a file that carries every setting to another machine, and the door back to the defaults.
 *
 * The two settings rows are the tablet's Backup section on a desktop: export writes the whole
 * configuration (`DesktopSettingsBackup`) and import reads one back, refusing anything this build cannot
 * understand with the reason. The answer is a sentence under the rows, not a dialog.
 */
@Composable
private fun BackupSettings(policy: DesktopSettingsState) {
    SettingsGroup("Settings transfer") {
        SettingsCommand(
            label = "Export settings…",
            note = "Save all settings to a backup file.",
            onClick = { policy.pickBackupFile?.invoke(true) { file -> file?.let(policy::exportBackup) } },
        )
        SettingsCommand(
            label = "Import settings…",
            note = "Restore settings from a previous backup file.",
            onClick = { policy.pickBackupFile?.invoke(false) { file -> file?.let(policy::importBackup) } },
        )
        policy.backupNotice?.let { SettingsNote(it) }
    }
    // The one command that answers "start over": it is the backup page's own subject — a whole
    // configuration, in or out — and it is drawn as a command, not as a row that reports a state.
    SettingsGroup("Reset settings") {
        SettingsCommand(
            label = "Reset all settings…",
            note = "Restore all settings to factory defaults. Document files are not affected.",
            onClick = policy::resetAllSettings,
        )
    }
}

/**
 * **About**: what the window is, the credits it owes, and the door out to the project's own page.
 *
 * The credits are the tablet's, in the window's words: MobiXournal continues NeXopp, and it is built on and
 * for Xournal++, which owns the `.xopp` format. The first group ends with the **door out** — the project's own
 * repository, opened in the machine's browser — because a version number is not what a reader wants when they
 * look for the other shell: its releases are.
 */
@Composable
private fun AboutSettings() {
    SettingsGroup("MobiXournal Desktop") {
        SettingsReadout("Version", DESKTOP_VERSION)
        SettingsReadout("Reads and writes", ".xopp · .mobix")
        // The door **out**, in place of a row that reported the other shell's version: a reader who wants the
        // Android build wants its releases, and the project's page is where both shells' work lives.
        SettingsCommand(
            label = "Open project on GitHub",
            note = "Source code, release downloads, and issue tracker.",
            onClick = ::openProjectPage,
        )
        // The check's own row. Its note is the panel's hover help, so the answer a reader needs after a tap
        // — "you are on the latest version" — is the bottom pill the shell hosts, not this line
        // (`docs/updates.md` owns the channel, the pill and both installers).
        SettingsCommand(
            label = "Check for updates",
            note = DesktopUpdates.statusLine(),
            onClick = { DesktopUpdates.offerOrCheck() },
        )
    }
    SettingsGroup("Credits & License") {
        SettingsNote(
            "Continues NeXopp by Brian Monroe (bamonroe), licensed under GNU GPL v2 or later. " +
                "Original portions copyright Brian Monroe.",
        )
        SettingsNote(
            "Interoperable with Xournal++, which defines the .xopp format. " +
                "Unofficial; not affiliated with or endorsed by Xournal++ authors.",
        )
        SettingsNote("Free software licensed under GNU General Public License v2 or later. Provided without warranty.")
    }
}

/** The version the packaged window shows — one home for both shells (`BuildVersions`). */
internal const val DESKTOP_VERSION = BuildVersions.DESKTOP
