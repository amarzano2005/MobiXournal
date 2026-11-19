package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.DesktopButton
import com.mobixournal.desktop.tools.DesktopButtonVariant
import com.mobixournal.desktop.tools.DesktopIconButton
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopPngExport
import com.mobixournal.desktop.io.ImportPdfDialog
import com.mobixournal.desktop.settings.DesktopSettingsState
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.PropertyCheckboxRow
import com.mobixournal.desktop.settings.SettingsSlider
import com.mobixournal.desktop.settings.StylusPresetCarousel
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.drawSettingsGlyph
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.tools.DismissGuard
import com.mobixournal.desktop.tools.MenuSurface
import com.mobixournal.desktop.tools.GlassPopup
import com.mobixournal.desktop.tools.GlassPresence

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.render.PressureCurve
import java.util.Locale

/**
 * The window's **overflow menu**: one flat list of commands, grouped by what they act on.
 *
 * The menu used to be a collage — four 64 dp tiles of art over a word, two rows of embossed red and blue
 * chips, and plain rows under them — so the same popup asked a reader to learn five looks for one idea.
 * `docs/interface-contract.md` §1 and §6 settle on the flat workstation: a menu is a surface with one
 * hairline, and every command in it is the **same row** — a 16 dp catalogue glyph, its name, its
 * accelerator in the muted ink — lighting a 4 dp corner under the pointer. What changes between the
 * groups is the hairline between them and nothing else.
 *
 * The groups are the document's bytes (New, Open, Save, Save As), the PDFs and exports, and the pen and
 * the window. The **Pen** block folds: its pressure answers are reached mid-drawing, not every time a
 * reader wants Hide page list, so the row opens them and stays shut at rest.
 *
 * The panel is [MenuPanel] rather than this `Popup`'s body, because a popup cannot be rendered in a
 * headless scene: the panel is what a test draws, clicks and files as a PNG, and this wrapper only adds
 * the button that opens it and the offset it opens at.
 */
@Composable
internal fun HeaderMenu(
    sidebarVisible: Boolean,
    canSave: Boolean,
    /** Whether a document is open at all — what makes the two export rows available. */
    canExport: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit,
    /** The **Export As** row: the card it raises owns the three answers (`ExportAsDialog`). */
    onExportAs: () -> Unit = {},
    onExportSvg: () -> Unit,
    onExportPng: () -> Unit = {},
    /**
     * **Import PDF**: a picked `.pdf` becomes pages to write on (`ImportPdfDialog` asks where). Its row
     * wears the document red on its glyph, as on the tablet: the same file, coming in rather than out.
     */
    onImportPdf: () -> Unit = {},
    onToggleSidebar: () -> Unit,
    onSettings: () -> Unit,
    onPenParameters: () -> Unit = {},
    /** The window's settings, for the Pen block's pressure controls; null in a test with no window. */
    penPolicy: DesktopSettingsState? = null,
    /** The row that writes a copy carrying what this notebook only references ([MenuPanel]). */
    onPortableCopy: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    // The menu is the one popup that takes the keyboard (`MenuSurface`): while it is up the window's table
    // stands down, so the Pen block's name field types rather than switching tools.
    SideEffect { WindowKeys.menuOpen = open }
    DisposableEffect(Unit) { onDispose { WindowKeys.menuOpen = false } }
    // The guard that lets a second click on the hamburger put the menu away (see [DismissGuard]).
    val guard = remember { DismissGuard() }
    val density = LocalDensity.current
    Box {
        // The band's flat command, not a capsule: the menu is the first icon of the header's row
        // (`Header`), so it wears the same 28 dp box and answers the pointer the same way.
        DesktopIconButton(
            description = "Menu",
            onClick = { open = !open && guard.mayOpen(MENU_TRIGGER) },
            size = HEADER_CONTROL,
            picked = open,
        ) {
            Canvas(Modifier.size(HEADER_GLYPH)) {
                drawChromeGlyph(ChromeGlyph.MENU, if (open) DesktopTheme.accent else DesktopTheme.chromeInk)
            }
        }
        GlassPresence(open) {
            GlassPopup(
                // Air under the **glyph**, not under the band: `headerHeight` left the menu hanging a
                // whole control's worth of space below the button (the band is taller than the button).
                offset = with(density) { IntOffset(0, (HEADER_CONTROL + 4.dp).roundToPx()) },
                // A draft that was being named goes with the panel: a name field reopened by the next
                // click would be a question the reader already walked away from.
                onDismissRequest = {
                    guard.closed(MENU_TRIGGER)
                    open = false
                    penPolicy?.cancelPresetSave()
                },
                properties = MenuSurface,
            ) {
                // Every command closes the menu first: the panel is a set of commands, not a toolbox.
                MenuPanel(
                    sidebarVisible = sidebarVisible,
                    canSave = canSave,
                    canExport = canExport,
                    onNew = { open = false; onNew() },
                    onOpen = { open = false; onOpen() },
                    onSave = { open = false; onSave() },
                    onSaveAs = { open = false; onSaveAs() },
                    onPortableCopy = { open = false; onPortableCopy() },
                    onExportAs = { open = false; onExportAs() },
                    onExportPdf = { open = false; onExportPdf() },
                    onExportSvg = { open = false; onExportSvg() },
                    onExportPng = { open = false; onExportPng() },
                    onImportPdf = { open = false; onImportPdf() },
                    onToggleSidebar = { open = false; onToggleSidebar() },
                    onSettings = { open = false; onSettings() },
                    onPenParameters = { open = false; onPenParameters() },
                    onDismiss = { open = false },
                    penPolicy = penPolicy,
                )
            }
        }
    }
}

/**
 * The menu's **panel**: one column of rows, three groups and two rules.
 *
 * The panel owns no look of its own beyond the flat surface: every row is [MenuRow], so the only
 * difference between "Save" and "Settings" is the word and the glyph — which is the point.
 */
@Composable
internal fun MenuPanel(
    sidebarVisible: Boolean,
    canSave: Boolean,
    canExport: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit,
    /** **Export As**: raises the card over the sheet, where the three answers are asked. */
    onExportAs: () -> Unit = {},
    onExportSvg: () -> Unit,
    /** **Export As ▸ PNG**: one raster file per page, into a folder you pick (`DesktopPngExport`). */
    onExportPng: () -> Unit = {},
    /** **Import PDF**: a picked `.pdf` becomes pages to write on (`ImportPdfDialog` asks where). */
    onImportPdf: () -> Unit = {},
    onToggleSidebar: () -> Unit,
    onSettings: () -> Unit,
    onPenParameters: () -> Unit = {},
    /** Put the panel away — the preset rows close it, because they were reached mid-drawing. */
    onDismiss: () -> Unit = {},
    /** The pressure controls of the Pen block, when the shell has settings to show (see [MenuPenBlock]). */
    penPolicy: DesktopSettingsState? = null,
    /** **Export As ▸ .pmobix**: one file carrying the bytes this notebook only names. */
    onPortableCopy: () -> Unit = {},
) {
    Column(
        Modifier
            .glassSurface()
            .width(MenuMetrics.width)
            // The popup's own inset: the rows' light lands inside this air, never on the rim.
            .padding(DesktopTheme.popupInset),
    ) {
        MenuFileRows(canSave, onNew, onOpen, onSave, onSaveAs)
        MenuDivider()
        MenuPdfRows(canExport, onImportPdf, onExportPdf, onExportAs)
        MenuDivider()
        MenuWorkspaceRows(sidebarVisible, penPolicy, onPenParameters, onDismiss, onToggleSidebar, onSettings)
    }
}

/** The document's own four commands: the top group of the flat list. */
@Composable
private fun MenuFileRows(
    canSave: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
) {
    MenuRow("New", ChromeGlyph.NEW, shortcut = "Ctrl+N", onClick = onNew)
    MenuRow("Open\u2026", ChromeGlyph.OPEN, shortcut = "Ctrl+T", onClick = onOpen)
    MenuRow("Save", ChromeGlyph.SAVE, enabled = canSave, shortcut = "Ctrl+S", onClick = onSave)
    MenuRow("Save As\u2026", ChromeGlyph.SAVE_AS, shortcut = "Ctrl+Shift+S", onClick = onSaveAs)
}

/**
 * The PDFs and the exports: the group whose two PDF rows wear the **document red on their glyph alone**.
 *
 * A red label would make the row read as a warning; the mark is enough to say "this row is about a PDF",
 * and the word keeps the same ink a reader scans in every other row (`docs/interface-contract.md` §2).
 */
@Composable
private fun MenuPdfRows(
    canExport: Boolean,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportAs: () -> Unit,
) {
    MenuRow("Import PDF\u2026", ChromeGlyph.PDF, iconTone = DesktopTheme.documentRed, onClick = onImportPdf)
    MenuRow(
        "Export PDF\u2026",
        ChromeGlyph.PDF,
        enabled = canExport,
        shortcut = "Ctrl+E",
        iconTone = DesktopTheme.documentRed,
        onClick = onExportPdf,
    )
    MenuRow("Export As\u2026", ChromeGlyph.EXPORT, enabled = canExport, onClick = onExportAs)
}

/** The pen and the window: the block that folds, then the two commands that only live here. */
@Composable
private fun MenuWorkspaceRows(
    sidebarVisible: Boolean,
    penPolicy: DesktopSettingsState?,
    onPenParameters: () -> Unit,
    onDismiss: () -> Unit,
    onToggleSidebar: () -> Unit,
    onSettings: () -> Unit,
) {
    MenuPenSection(penPolicy, onPenParameters, onDismiss)
    MenuRow(
        label = if (sidebarVisible) "Hide page list" else "Show page list",
        glyph = ChromeGlyph.DESCRIPTION,
        shortcut = "Ctrl+B",
        onClick = onToggleSidebar,
    )
    MenuRow("Settings", ChromeGlyph.SETTINGS, shortcut = "Ctrl+,", onClick = onSettings)
}

/**
 * The **Pen** row and, under it, the pen's own answers when the row is open.
 *
 * Folded at rest: a reader opening the menu for *Hide page list* should not have to walk a pressure
 * sliders block first. The gear at the row's end is the door to the **Stylus page**, where the same
 * answers live at full size — it stands beside the row rather than inside it, so a click on the gear
 * cannot also fold the block.
 */
@Composable
private fun MenuPenSection(
    policy: DesktopSettingsState?,
    onPenParameters: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            MenuRow(
                label = "Pen",
                onClick = { expanded = !expanded },
            ) {
                Canvas(Modifier.size(MenuMetrics.glyph)) {
                    drawSettingsGlyph(SettingsGlyph.STYLUS, DesktopTheme.ink)
                }
            }
        }
        DesktopIconButton(
            description = "Stylus settings",
            onClick = onPenParameters,
            size = PEN_GEAR_SIZE,
            tooltip = "Stylus settings",
        ) {
            Canvas(Modifier.size(14.dp)) {
                drawSettingsGlyph(SettingsGlyph.EDITOR, DesktopTheme.inkSecondary)
            }
        }
        Spacer(Modifier.width(2.dp))
    }
    if (expanded && policy != null) MenuPenBlock(policy, onDismiss)
}

/**
 * **The Pen block**, open: how a stroke is drawn, at the menu's scale.
 *
 * It carries the pen's **drawing** answers only: whether pressure counts at all, *which* preset names the
 * two numbers, then those numbers, then the way back to the factory pair. Which **stream** the pen is
 * read from stays on the Stylus page — that is a fact about the driver on this machine, not about the
 * stroke being made. The presets are the page's own carousel, so a preset deleted here is deleted there.
 */
@Composable
private fun MenuPenBlock(policy: DesktopSettingsState, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = MenuMetrics.penInset, end = MenuMetrics.rowInset, bottom = 4.dp),
    ) {
        PropertyCheckboxRow("Pressure sensitivity", policy.pressureEnabled) { on ->
            policy.choosePressureEnabled(on)
        }
        StylusPresetCarousel(policy, inset = 0.dp, onApplied = onDismiss)
        MenuSliderRow(
            label = "Minimum pressure",
            value = policy.minimumPressure,
            range = PressureCurve.MINIMUM_PRESSURE_MIN..PressureCurve.MINIMUM_PRESSURE_MAX,
            step = 0.01f,
            format = { String.format(Locale.ROOT, "%.2f", it) },
            onValue = policy::chooseMinimumPressure,
        )
        MenuSliderRow(
            label = "Multiplier",
            value = policy.pressureMultiplier,
            range = PressureCurve.MULTIPLIER_MIN..PressureCurve.MULTIPLIER_MAX,
            step = 0.05f,
            format = { String.format(Locale.ROOT, "%.2f×", it) },
            onValue = policy::choosePressureMultiplier,
        )
        Spacer(Modifier.height(4.dp))
        DesktopButton(
            label = "Reset to defaults",
            onClick = policy::resetPressureToDefaults,
            variant = DesktopButtonVariant.Ghost,
            minHeight = 28.dp,
            style = DesktopTheme.label,
        )
    }
}

/** One of the Pen block's two numbers: its name on the left, the slider that sets it at the end. */
@Composable
private fun MenuSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onValue: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(MenuMetrics.rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DesktopTheme.ink, style = DesktopTheme.label, modifier = Modifier.weight(1f))
        SettingsSlider(
            value = value,
            range = range,
            step = step,
            format = format,
            onValue = onValue,
            width = MenuMetrics.sliderWidth,
        )
    }
}

/**
 * **One command of the menu**, and the only shape a command has in it: a 16 dp glyph slot, the name, and
 * the accelerator at the end in the **muted** ink.
 *
 * The accelerator is a reminder, not a control — it never takes the accent, because the accent on this
 * surface means "this row is picked or is the one door" and a menu row is neither. [iconTone] is what the
 * two PDF rows use to keep their glyph red while their word stays the ink a reader scans.
 */
@Composable
private fun MenuRow(
    label: String,
    glyph: ChromeGlyph? = null,
    enabled: Boolean = true,
    shortcut: String? = null,
    iconTone: Color? = null,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    val ink = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted
    Row(
        Modifier
            .fillMaxWidth()
            .height(MenuMetrics.rowHeight)
            .hoverFill(DesktopTheme.controlShape)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(horizontal = MenuMetrics.rowInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            glyph != null -> Canvas(Modifier.size(MenuMetrics.glyph)) {
                drawChromeGlyph(glyph, if (enabled) iconTone ?: DesktopTheme.ink else DesktopTheme.inkMuted)
            }
            icon != null -> icon()
        }
        Spacer(Modifier.width(MenuMetrics.glyphGap))
        Text(
            label,
            color = ink,
            style = DesktopTheme.body,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (shortcut != null) {
            Text(shortcut, color = DesktopTheme.inkMuted, style = DesktopTheme.numeric)
        }
    }
}

/** The rule between two groups, with the same 4 dp of air above and below it. */
@Composable
private fun MenuDivider() {
    Spacer(Modifier.height(MenuMetrics.dividerAir))
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    Spacer(Modifier.height(MenuMetrics.dividerAir))
}

/** What the hamburger's own [DismissGuard] remembers: the one menu, so one key. */
private val MENU_TRIGGER = Any()

/** The Pen row's gear: a 22 dp command at the end of a 30 dp row. */
private val PEN_GEAR_SIZE = 22.dp

/**
 * The panel's numbers, in one place: the layout reads them and so does `DesktopMenuTest`, which clicks
 * the panel by the same arithmetic the panel was laid out with. A test that guesses a coordinate is a
 * test that fails the day a gap changes for a good reason.
 */
internal object MenuMetrics {
    /** The panel's own width: a menu, not a page — one command per line reads at 272. */
    val width = 272.dp

    /** The popup's inset, and the air a row's light keeps from the panel's edge. */
    val inset = DesktopTheme.popupInset
    val rowInset = 8.dp

    /** One command's line, its glyph slot and the gap between the two. */
    val rowHeight = 30.dp
    val glyph = 16.dp
    val glyphGap = 8.dp

    /** A group's rule: one 1 dp line with its air, which is the height a divider costs the column. */
    val dividerAir = 4.dp
    val dividerHeight = dividerAir * 2 + 1.dp

    /** The Pen block's inset: under the row's glyph, so its answers line up with the names above. */
    val penInset = rowInset + glyph + glyphGap

    /** The Pen block's slider track: what is left of the 272 dp panel beside a label. */
    val sliderWidth = 104.dp
}
