package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.render.PressureCurve
import java.util.Locale

/**
 * The window's **overflow menu** — the mobile bar's menu, ported and laid out the same way.
 *
 * The tablet's menu (its photo is the reference) is four **tiles** of art over a word across the top,
 * the exports as a pair of **chips** under them, and the plain commands below a hairline. The window
 * keeps that shape with its own commands: New, Open, Save and Save As on the top row; the two exports
 * as chips, in the accent the tablet gives them; the page list under the rule.
 *
 * What is deliberately **not** here is the tablet's Import PDF, Page as PNG and Settings: the first two
 * are commands the window does not have yet, and a dead row in a menu is worse than one the window
 * grows into. The rows are added as the commands land (`TODO.toml`).
 *
 * The panel is [MenuPanel] rather than this `Popup`'s body, because a popup cannot be rendered in a
 * headless scene: the panel is what a test draws, clicks and files as a PNG, and this wrapper only adds
 * the button that opens it and the offset it opens at.
 */
@Composable
internal fun HeaderMenu(
    sidebarVisible: Boolean,
    canSave: Boolean,
    /** Whether a document is open at all — what makes the two export chips available. */
    canExport: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit,
    onExportSvg: () -> Unit,
    onToggleSidebar: () -> Unit,
    onSettings: () -> Unit,
    onPenParameters: () -> Unit = {},
    /** The window's settings, for the Pen block's pressure controls; null in a test with no window. */
    penPolicy: DesktopSettingsState? = null,
) {
    var open by remember { mutableStateOf(false) }
    // The guard that lets a second click on the hamburger put the menu away (see [DismissGuard]).
    val guard = remember { DismissGuard() }
    val density = LocalDensity.current
    Box {
        HeaderGlyph(active = open, onClick = { open = !open && guard.mayOpen(MENU_TRIGGER) }) { color ->
            drawChromeGlyph(ChromeGlyph.MENU, color)
        }
        if (open) {
            Popup(
                offset = with(density) { IntOffset(0, (DesktopTheme.headerHeight + 6.dp).roundToPx()) },
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
                    onExportPdf = { open = false; onExportPdf() },
                    onExportSvg = { open = false; onExportSvg() },
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

/** The menu's panel: four tiles, two chips, and the page list — the mobile menu, with its rules. */
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
    onExportSvg: () -> Unit,
    onToggleSidebar: () -> Unit,
    onSettings: () -> Unit,
    onPenParameters: () -> Unit = {},
    /** Put the panel away — the preset chips close it, because they were reached mid-drawing. */
    onDismiss: () -> Unit = {},
    /** The pressure controls of the Pen block, when the shell has settings to show (see [MenuPenBlock]). */
    penPolicy: DesktopSettingsState? = null,
) {
    Column(
        Modifier
            .glassSurface()
            .width(MenuMetrics.width)
            // Air above and below, but **none** at the sides: a row's highlight spans the panel, as a
            // menu's does. The commands keep their inset by padding themselves, so the light lands on
            // the whole row a reader points at instead of a rounded band inside it.
            .padding(vertical = MenuMetrics.padding),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MenuMetrics.padding),
            horizontalArrangement = Arrangement.spacedBy(MenuMetrics.tileGap),
        ) {
            MenuTile("New", ChromeGlyph.NEW, enabled = true, onClick = onNew)
            MenuTile("Open", ChromeGlyph.OPEN, enabled = true, onClick = onOpen)
            MenuTile("Save", ChromeGlyph.SAVE, enabled = canSave, onClick = onSave)
            MenuTile("Save As", ChromeGlyph.SAVE_AS, enabled = true, onClick = onSaveAs)
        }
        Spacer(Modifier.height(MenuMetrics.sectionGap))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MenuMetrics.padding),
            horizontalArrangement = Arrangement.spacedBy(MenuMetrics.chipGap),
        ) {
            // The tablet's rule for the two exports: the PDF chip is the document red, the page raster
            // stays in the accent. Colouring both the same made the PDF look like any other export.
            MenuChip("Export PDF", ChromeGlyph.PDF, canExport, Modifier.weight(1f), DesktopTheme.documentRed, onExportPdf)
            MenuChip("Export SVG", ChromeGlyph.PICTURE, canExport, Modifier.weight(1f), DesktopTheme.accent, onExportSvg)
        }
        // A rule between the **exports** and the **pen**: the two chips above it act on the document's
        // bytes, and everything under it acts on the pen or on the window. A reader scanning for "export"
        // stops at the line, which is what the tablet's own menu does.
        Spacer(Modifier.height(MenuMetrics.sectionGap))
        MenuDivider()
        // The tablet's **Pen block**, and only the pen's own answers in it, in the Stylus page's order:
        // the stream, the switch, the presets, their two numbers, the way back to the factory pair. Nothing
        // about the **ink** lives here — a colour and a width have their capsule in the strip.
        MenuHeadingRow(label = "Pen", onClick = onPenParameters)
        if (penPolicy != null) MenuPenBlock(penPolicy, onDismiss, onPenParameters)
        Spacer(Modifier.height(MenuMetrics.sectionGap))
        MenuDivider()
        MenuRow(
            label = if (sidebarVisible) "Hide page list" else "Show page list",
            glyph = ChromeGlyph.DESCRIPTION,
            shortcut = "Ctrl+B",
            enabled = true,
            onClick = onToggleSidebar,
        )
        // The tablet's Settings row, in the window: its own page rather than a card over the sheet, so it
        // is here with the other commands and not in the chrome it changes.
        MenuRow(
            label = "Settings",
            glyph = ChromeGlyph.SETTINGS,
            shortcut = "Ctrl+,",
            enabled = true,
            onClick = onSettings,
        )
    }
}

/**
 * **The Pen block**, in the Stylus page's own order — the pen's answers, scaled to the menu.
 *
 * The page asks its questions in one order and the menu keeps it: the stream it is read from, whether
 * pressure counts at all, *which* preset names the two numbers, then those numbers, then the way back to
 * the factory pair. A reader who learns one surface finds the same rows in the same places on the other
 * (`docs/desktop-settings.md`, *The Pen block*).
 *
 * The **stream is a readout here**, not a select: a select's list is a popup, and a popup opened inside the
 * menu's own popup takes the menu down with it. The row therefore states the stream in hand in the accent
 * and wears the page's door glyph, so the click that names it goes where it can be changed.
 *
 * The rows are [MenuSettingRow] — [SettingsRow] at the menu's 34 dp line and in the *label* role — because
 * this block sits in a menu, not on a page. The smaller line is also what stopped the old *Pressure* row
 * from reading as a heading over its own sliders.
 *
 * The **presets** are the page's own carousel, × and `+` included: one component on both surfaces, so a
 * preset deleted here is deleted there, and nothing about the pair can drift between the two. Picking one
 * applies it and puts the panel away — the hand was mid-drawing.
 */
@Composable
private fun MenuPenBlock(policy: DesktopSettingsState, onDismiss: () -> Unit, onPenParameters: () -> Unit) {
    MenuSettingRow(label = "Stream", onClick = onPenParameters) {
        Text(policy.penStream.label, color = DesktopTheme.accent, style = DesktopTheme.label)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(14.dp)) {
            drawSettingsGlyph(SettingsGlyph.EDITOR, DesktopTheme.inkSecondary)
        }
    }
    MenuSettingRow(
        label = "Pressure sensitivity",
        onClick = { policy.choosePressureEnabled(!policy.pressureEnabled) },
    ) {
        SettingsSwitch(policy.pressureEnabled)
    }
    StylusPresetCarousel(policy, inset = MenuMetrics.penInset, onApplied = onDismiss)
    MenuSettingRow(label = "Minimum pressure") {
        SettingsSlider(
            value = policy.minimumPressure,
            range = PressureCurve.MINIMUM_PRESSURE_MIN..PressureCurve.MINIMUM_PRESSURE_MAX,
            step = 0.01f,
            format = { String.format(Locale.ROOT, "%.2f", it) },
            onValue = policy::chooseMinimumPressure,
            width = MenuMetrics.sliderWidth,
        )
    }
    MenuSettingRow(label = "Multiplier") {
        SettingsSlider(
            value = policy.pressureMultiplier,
            range = PressureCurve.MULTIPLIER_MIN..PressureCurve.MULTIPLIER_MAX,
            step = 0.05f,
            format = { String.format(Locale.ROOT, "%.2f×", it) },
            onValue = policy::choosePressureMultiplier,
            width = MenuMetrics.sliderWidth,
        )
    }
    MenuSettingRow(
        label = "Reset to defaults",
        tone = DesktopTheme.accent,
        onClick = policy::resetPressureToDefaults,
    )
}

/**
 * **One small setting row of the menu**: its name on the left, its control at the end, on the menu's own
 * line with a capsule that lights under the pointer.
 *
 * It is the page's [SettingsRow] at the menu's scale — the same grammar, the same control — and the whole
 * row is the hit area when it has an action, so the switch at the end is reachable from a hand that is
 * holding a pen.
 */
@Composable
private fun MenuSettingRow(
    label: String,
    tone: Color = DesktopTheme.ink,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MenuMetrics.rowHeight)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .hoverFill(DesktopTheme.capsuleShape)
                        .pointerInput(label, onClick) { detectTapGestures { onClick() } }
                },
            )
            .padding(horizontal = MenuMetrics.penInset, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = tone, style = DesktopTheme.label, modifier = Modifier.weight(1f))
        control()
    }
}

/** What the hamburger's own [DismissGuard] remembers: the one menu, so one key. */
private val MENU_TRIGGER = Any()

/**
 * The panel's numbers, in one place: the layout reads them and so does `DesktopMenuTest`, which clicks
 * the panel by the same arithmetic the panel was laid out with. A test that guesses a coordinate is a
 * test that fails the day a gap changes for a good reason.
 */
internal object MenuMetrics {
    val width = 320.dp
    val padding = 10.dp
    val tileGap = 2.dp
    val tileHeight = 64.dp
    val chipGap = 8.dp
    val chipHeight = 38.dp
    val sectionGap = 10.dp

    /** The rule between the chips and the page list, with its own air above and below. */
    val dividerHeight = 11.dp
    val rowHeight = 34.dp
    val chipRadius = 12.dp

    /** The pen block's own inset: the page's 12 dp pulled in to the menu's 10 dp lines. */
    val penInset = 10.dp

    /**
     * The pen block's slider track: the panel is 320 dp wide, so the page's 150 dp track beside a label
     * would run out of the row — the number and this length still leave *Minimum pressure* its room.
     */
    val sliderWidth = 118.dp

    /** One tile's width: four of them, their gaps, and the panel's own padding. */
    val tileWidth: androidx.compose.ui.unit.Dp
        get() = (width - padding * 2 - tileGap * 3) / 4

    /** One chip's width: two of them and their gap. */
    val chipWidth: androidx.compose.ui.unit.Dp
        get() = (width - padding * 2 - chipGap) / 2
}

/**
 * One of the four top commands: its glyph over its word, the whole tile one target.
 *
 * The tile is the mobile menu's own shape — art above the label rather than beside it — because a row
 * of four words is a list, and a row of four pictures with words under them is a palette.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RowScope.MenuTile(label: String, glyph: ChromeGlyph, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = MenuMetrics.tileHeight)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(22.dp)) {
            drawChromeGlyph(glyph, if (enabled) DesktopTheme.chromeInk else DesktopTheme.inkMuted)
        }
        Text(
            label,
            color = if (enabled) DesktopTheme.chromeInk else DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            maxLines = 1,
        )
    }
}

/**
 * An export: art and word in the **accent**, on a chip of its own — the tablet's blue chips.
 *
 * The chip is a capsule rather than a fifth tile because exporting is not a file command (nothing about
 * the document changes): the tablet draws that difference and the window keeps it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuChip(
    label: String,
    /** The chip's mark, or null for a word-only chip. */
    glyph: ChromeGlyph?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** The command's own colour while it can act — red for a PDF, the accent for a page export. */
    tone: Color,
    onClick: () -> Unit,
) {
    val ink = if (enabled) tone else DesktopTheme.inkMuted
    Row(
        modifier
            .height(MenuMetrics.chipHeight)
            .hoverCapsule(
                cornerRadius = MenuMetrics.chipRadius,
                fill = DesktopTheme.glass,
                active = !enabled,
            )
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (glyph != null) {
            Canvas(Modifier.size(18.dp)) { drawChromeGlyph(glyph, ink) }
        }
        Text(label, color = ink, style = DesktopTheme.body, maxLines = 1)
    }
}

/**
 * A **heading inside the menu** — the tablet's *Pen* row: its pen glyph beside the word, and the glyph
 * that opens what the rows below configure at the row's end.
 *
 * The **pen mark is beside the word** rather than only at the far end: the catalogue's own `STYLUS` glyph,
 * the picture the tablet and the Stylus page use, so a reader sees what the block is about before reading
 * the word. The row is the door to that page as well — a heading that only names a block would have the
 * block's door somewhere else.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuHeadingRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MenuMetrics.rowHeight)
            .hoverFill(DesktopTheme.capsuleShape)
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            drawSettingsGlyph(SettingsGlyph.STYLUS, DesktopTheme.ink)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = DesktopTheme.ink, style = DesktopTheme.body, modifier = Modifier.weight(1f))
        Canvas(Modifier.size(16.dp)) {
            drawSettingsGlyph(SettingsGlyph.EDITOR, DesktopTheme.inkSecondary)
        }
    }
}

/** One plain row of the menu: a label, an optional accelerator, and the disabled tone when it cannot act. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuRow(
    label: String,
    /** The row's mark, from the chrome catalogue — the same picture the tablet's own menu row carries. */
    glyph: ChromeGlyph? = null,
    shortcut: String? = null,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MenuMetrics.rowHeight)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape, active = false)
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = MenuMetrics.penInset, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Canvas(Modifier.size(16.dp)) {
                drawChromeGlyph(glyph, if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted)
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.body,
            modifier = Modifier.weight(1f),
        )
        if (shortcut != null) {
            Text(shortcut, color = DesktopTheme.accent, style = DesktopTheme.numeric)
        }
    }
}

/** A hairline between two parts of the menu, in the band's own rim ink. */
@Composable
private fun MenuDivider() {
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    Spacer(Modifier.height(4.dp))
}
