package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    onPenDefault: () -> Unit = {},
    /** The window's settings, for the Pen block's pressure controls; null in a test with no window. */
    penPolicy: DesktopSettingsState? = null,
) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        HeaderGlyph(active = open, onClick = { open = !open }) { color ->
            drawChromeGlyph(ChromeGlyph.MENU, color)
        }
        if (open) {
            Popup(
                offset = with(density) { IntOffset(0, (DesktopTheme.headerHeight + 6.dp).roundToPx()) },
                // A draft that was being named goes with the panel: a name field reopened by the next
                // click would be a question the reader already walked away from.
                onDismissRequest = {
                    open = false
                    penPolicy?.cancelPresetSave()
                },
                properties = FloatingSurface,
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
                    onPenDefault = { open = false; onPenDefault() },
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
    onPenDefault: () -> Unit = {},
    /** Put the panel away — the preset chips close it, because they were reached mid-drawing. */
    onDismiss: () -> Unit = {},
    /** The pressure controls of the Pen block, when the shell has settings to show (see [MenuPenBlock]). */
    penPolicy: DesktopSettingsState? = null,
) {
    Column(
        Modifier
            .glassSurface()
            .width(MenuMetrics.width)
            .padding(MenuMetrics.padding),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MenuMetrics.tileGap),
        ) {
            MenuTile("New", ChromeGlyph.NEW, enabled = true, onClick = onNew)
            MenuTile("Open", ChromeGlyph.OPEN, enabled = true, onClick = onOpen)
            MenuTile("Save", ChromeGlyph.SAVE, enabled = canSave, onClick = onSave)
            MenuTile("Save As", ChromeGlyph.SAVE_AS, enabled = true, onClick = onSaveAs)
        }
        Spacer(Modifier.height(MenuMetrics.sectionGap))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MenuMetrics.chipGap),
        ) {
            // The tablet's rule for the two exports: the PDF chip is the document red, the page raster
            // stays in the accent. Colouring both the same made the PDF look like any other export.
            MenuChip("Export PDF", ChromeGlyph.PDF, canExport, Modifier.weight(1f), DesktopTheme.documentRed, onExportPdf)
            MenuChip("Export SVG", ChromeGlyph.PICTURE, canExport, Modifier.weight(1f), DesktopTheme.accent, onExportSvg)
        }
        // The tablet's **Pen block**: its heading with the door to the pen's parameters, and the reset
        // chip under it. *Factory ink* is the **ink**, not a pressure preset — its old name was
        // *Default*, which became a lie the moment the presets below had a Default of their own.
        Spacer(Modifier.height(MenuMetrics.sectionGap))
        MenuHeadingRow(label = "Pen", onClick = onPenParameters)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MenuMetrics.chipGap)) {
            MenuChip("Factory ink", null, enabled = true, Modifier, DesktopTheme.accent, onPenDefault)
            MenuChip("Parameters…", null, enabled = true, Modifier, DesktopTheme.inkSecondary, onPenParameters)
        }
        // The tablet's Pen block carries its pressure settings and not only a door to them: the two
        // numbers a drawing hand reaches for mid-sentence are here, where the hand already is — and under
        // them the same named presets the Stylus page edits, so a preset is one click from a stroke.
        if (penPolicy != null) MenuPenBlock(penPolicy, onDismiss)
        Spacer(Modifier.height(MenuMetrics.sectionGap))
        MenuDivider()
        MenuRow(
            label = if (sidebarVisible) "Hide page list" else "Show page list",
            shortcut = "Ctrl+B",
            enabled = true,
            onClick = onToggleSidebar,
        )
        // The tablet's Settings row, in the window: its own page rather than a card over the sheet, so it
        // is here with the other commands and not in the chrome it changes.
        MenuRow(
            label = "Settings",
            shortcut = "Ctrl+,",
            enabled = true,
            onClick = onSettings,
        )
    }
}

/**
 * **The Pen block's pressure controls**, the tablet's Stylus rows in the menu's own material.
 *
 * Sensitivity is a switch and the two numbers are the window's slider, each on a row of its own at the
 * menu's row height — so the block a reader opens mid-drawing answers the question it was opened for,
 * and *Parameters…* stays the door to the rest (the stream, the curve, the mapped area).
 *
 * Under the sliders are the **presets**, under a heading of their own: the tablet's scrollable chip row,
 * the live one written in the accent and washed with it, and *Save…* beside the heading. The heading is
 * what keeps them apart from the *Factory ink* chip above — a word on each row rather than two chips that
 * read the same. Picking a preset applies it and closes the panel (the hand was mid-drawing); *Save…*
 * opens the one name field both this block and the Stylus page share.
 */
@Composable
private fun MenuPenBlock(policy: DesktopSettingsState, onDismiss: () -> Unit) {
    Spacer(Modifier.height(MenuMetrics.sectionGap / 2))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MenuMetrics.rowHeight)
            .hoverFill(DesktopTheme.capsuleShape)
            .pointerInput(Unit) { detectTapGestures { policy.choosePressureEnabled(!policy.pressureEnabled) } }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Pressure", color = DesktopTheme.ink, style = DesktopTheme.body, modifier = Modifier.weight(1f))
        SettingsSwitch(policy.pressureEnabled)
    }
    MenuPressureSlider(
        label = "Min pressure",
        value = policy.minimumPressure,
        range = com.mobixournal.render.PressureCurve.MINIMUM_PRESSURE_MIN..
            com.mobixournal.render.PressureCurve.MINIMUM_PRESSURE_MAX,
        step = 0.01f,
        format = { java.lang.String.format(java.util.Locale.ROOT, "%.2f", it) },
        onValue = policy::chooseMinimumPressure,
    )
    MenuPressureSlider(
        label = "Multiplier",
        value = policy.pressureMultiplier,
        range = com.mobixournal.render.PressureCurve.MULTIPLIER_MIN..
            com.mobixournal.render.PressureCurve.MULTIPLIER_MAX,
        step = 0.05f,
        format = { java.lang.String.format(java.util.Locale.ROOT, "%.2f×", it) },
        onValue = policy::choosePressureMultiplier,
    )
    Spacer(Modifier.height(2.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Presets", color = DesktopTheme.inkSecondary, style = DesktopTheme.label, modifier = Modifier.weight(1f))
        SettingsTextAction(
            text = "Save…",
            enabled = policy.presetDraft == null,
            onClick = policy::beginPresetSave,
        )
    }
    if (policy.presetDraft == null) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (preset in policy.penPresets) {
                MenuPresetChip(
                    name = preset.name,
                    selected = preset.id == policy.selectedPenPresetId,
                    onClick = {
                        policy.applyPenPreset(preset.id)
                        policy.cancelPresetSave()
                        onDismiss()
                    },
                )
            }
        }
    } else {
        MenuPresetNameRow(policy)
    }
}

/**
 * One **preset chip** of [MenuPenBlock]: the tablet's glass chip, the live one washed with the accent.
 *
 * The wash and the accent word are the tablet's own statement of "this is the pen in hand" — the same two
 * states its overflow menu draws — so the chip a reader picks is the one they see picked.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuPresetChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(SettingsMetrics.CONTROL_HEIGHT)
            .glassCapsule(
                cornerRadius = MenuMetrics.chipRadius,
                fill = if (selected) DesktopTheme.accent.copy(alpha = MenuMetrics.presetWash) else DesktopTheme.glass,
            )
            .hoverFill(DesktopTheme.capsuleShape, active = selected)
            .pointerInput(name) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            color = if (selected) DesktopTheme.accent else DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
            maxLines = 1,
        )
    }
}

/** The **name field** of a preset being saved, at the menu's own row height. */
@Composable
private fun MenuPresetNameRow(policy: DesktopSettingsState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetNameField(
            value = policy.presetDraft.orEmpty(),
            onValue = policy::typePresetName,
            onCommit = policy::commitPresetSave,
            modifier = Modifier.weight(1f),
        )
        SettingsTextAction(
            text = "Save",
            enabled = !policy.presetDraft.isNullOrBlank(),
            onClick = policy::commitPresetSave,
        )
        SettingsTextAction(
            text = "Cancel",
            tone = DesktopTheme.inkSecondary,
            onClick = policy::cancelPresetSave,
        )
    }
}

/** One labelled slider of [MenuPenBlock]: the word on the left, the window's slider at the right. */
@Composable
private fun MenuPressureSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onValue: (Float) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MenuMetrics.rowHeight)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DesktopTheme.inkSecondary, style = DesktopTheme.label, modifier = Modifier.weight(1f))
        SettingsSlider(value = value, range = range, step = step, format = format, onValue = onValue)
    }
}

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

    /** How strongly the live pen-preset chip is washed with the accent — picked, not hovered. */
    const val presetWash = 0.16f

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
    /** The chip's mark, or null for a word-only chip (the pen preset). */
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
            .glassCapsule(
                cornerRadius = MenuMetrics.chipRadius,
                fill = DesktopTheme.glass,
                shadow = DesktopTheme.capsuleShadow,
            )
            .hoverFill(DesktopTheme.capsuleShape, active = !enabled)
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
 * A **heading inside the menu** — the tablet's *Pen* row: the word on the left, and the glyph that opens
 * what the words below configure on the right.
 *
 * It is not a command and not a label: the tablet draws it as the door to the pen's parameters, and a
 * heading that only names a block would have the block's door somewhere else.
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
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
