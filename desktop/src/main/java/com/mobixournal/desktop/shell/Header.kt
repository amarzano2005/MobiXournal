package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.DismissGuard
import com.mobixournal.desktop.tools.FloatingSurface
import com.mobixournal.desktop.tools.DesktopIconButton
import com.mobixournal.desktop.tools.bandDivider
import com.mobixournal.desktop.tools.bandSurface
import com.mobixournal.desktop.tools.BandEdge
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopAudioSession
import com.mobixournal.desktop.settings.DesktopSettingsState
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.tools.GlassPopup
import com.mobixournal.desktop.tools.GlassPresence

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.DesignTokens

/**
 * The window's **header**: the menu, the tab strip, and the file commands on its right.
 *
 * The band is a flat workstation's top rule (`docs/interface-contract.md` §1): one opaque tone, one
 * hairline under it and **no glass** — the capsule soup that used to stand here put a rim and a lift
 * around every glyph, which read as nine objects where the band should read as one tool. What stands on
 * it now is a row of 28 dp icon commands, grouped by what they act on and separated by a 1 dp hairline
 * rather than by a box: the document's doors, the search field, the history, the transport and the two
 * file commands.
 *
 * The *placement* chooser this band used to end with is gone (`docs/desktop-workstation.md`): moving the
 * strips is a setting, and a setting belongs in the Interface section rather than in the chrome it moves.
 */
internal val HEADER_EDGE = 8.dp

/** The air between two commands of one group: a pair reads as a pair, not as one wide control. */
internal val HEADER_ITEM_GAP = 4.dp

/** The air either side of a group's dividing hairline, so the line is a seam and not a border. */
internal val HEADER_GROUP_GAP = 8.dp

/** One header command's box: the token's 28 dp of the band's 40, with 6 dp of air above and below. */
internal val HEADER_CONTROL = DesignTokens.DESKTOP_HEADER_BUTTON_DP.dp

/** The glyph inside that box, drawn at the weight the catalogue's own paths carry. */
internal val HEADER_GLYPH = 16.dp

@Composable
internal fun Header(
    tabs: DesktopTabs,
    sidebarVisible: Boolean,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit,
    onExportSvg: () -> Unit,
    /** The menu's **Export As** chip: the card that offers PNG, SVG and `.pmobix` (`ExportAsDialog`). */
    onExportAs: () -> Unit = {},
    /** The card's **PNG** answer — one raster per page, into a folder you pick. */
    onExportPng: () -> Unit = {},
    /** The menu's **Import PDF** chip — the tablet's command, now that the window can carry a PDF. */
    onImportPdf: () -> Unit = {},
    /** **Save portable copy**: a second `.mobix` carrying the PDFs, pictures and recordings this one names. */
    onPortableCopy: () -> Unit = {},
    onToggleSidebar: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    /** Whether a save is writing right now — what turns the Save glyph into a turning arc (`SavingArc`). */
    saving: Boolean = false,
    /** Open the Settings surface — the menu's row, and the window behind `Ctrl+,`. */
    onSettings: () -> Unit,
    /** The menu's Pen block: the door to the Stylus page, behind its heading's glyph. */
    onPenParameters: () -> Unit = {},
    /** The settings the menu's Pen block changes, so its pressure rows act on the open window. */
    penPolicy: DesktopSettingsState? = null,
    /** The window's audio, or null in a band with none — the render tests draw the pre-audio header. */
    audio: DesktopAudioSession? = null,
    /** The pill's Play also arms the tablet's **Play object**, which the tool strip has no slot for. */
    onAudioTap: () -> Unit = {},
    /** The search field's text, its two steps and its dismissal — the field stands on the band, always. */
    onSearch: (String) -> Unit = {},
    onSearchNext: () -> Unit = {},
    onSearchPrevious: () -> Unit = {},
    onSearchClose: () -> Unit = {},
    /** The pages' menu on the band: the tablet's page section, through [PageMenuPanel]'s rows. */
    pages: PageMenuActions = PageMenuActions(),
    /** The **Layers** door beside Pages: the tablet's layer manager ([LayersPanel]), one click away. */
    layers: LayerMenuActions = LayerMenuActions(),
) {
    val tab = tabs.active
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.headerHeight)
            .bandSurface()
            .bandDivider(BandEdge.BOTTOM)
            .padding(horizontal = HEADER_EDGE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderMenu(
            sidebarVisible = sidebarVisible,
            canSave = tab?.canSave == true,
            canExport = tab != null,
            onNew = onNew,
            onOpen = onOpen,
            onSave = onSave,
            onSaveAs = onSaveAs,
            onPortableCopy = onPortableCopy,
            onExportAs = onExportAs,
            onExportPdf = onExportPdf,
            onExportSvg = onExportSvg,
            onExportPng = onExportPng,
            onImportPdf = onImportPdf,
            onToggleSidebar = onToggleSidebar, onSettings = onSettings,
            onPenParameters = onPenParameters, penPolicy = penPolicy,
        )
        HeaderDivider()
        TabStrip(
            tabs = tabs,
            onActivate = onActivate,
            onClose = onClose,
            onMove = onMove,
            onNew = onNew,
            modifier = Modifier.weight(1f),
        )
        if (tab != null) {
            HeaderDivider()
            HeaderDocumentDoors(pages, layers)
            HeaderDivider()
            HeaderSearchField(tab, onSearch, onSearchNext, onSearchPrevious, onSearchClose)
        }
        HeaderDivider()
        HeaderHistory(tab, onUndo, onRedo)
        if (audio != null) {
            HeaderDivider()
            HeaderAudioPill(audio = audio, tab = { tabs.active }, onAudioTap = onAudioTap)
        }
        HeaderDivider()
        HeaderFileCommands(tab, saving, onSave, onExportPdf)
    }
}

/**
 * The **one line** between two groups of the band: a 1 dp hairline in the band's own ink, standing the
 * height of a glyph, with the same air on either side.
 *
 * It replaces the capsule that used to hold a pair together: a group is a seam of the band now, and a
 * box around it was a second surface the flat contract does not have (`docs/interface-contract.md` §1).
 */
@Composable
private fun HeaderDivider() {
    Spacer(Modifier.width(HEADER_GROUP_GAP))
    Box(
        Modifier
            .width(1.dp)
            .height(16.dp)
            .background(DesktopTheme.hairline),
    )
    Spacer(Modifier.width(HEADER_GROUP_GAP))
}

/**
 * The band's two **document doors**: Pages — add, remove, step, format, paper — and, beside it, the
 * page's **Layers** manager.
 *
 * They were one glass capsule with no seam between the halves; on the flat band they are two icon
 * commands in one group, which is the tablet's arrangement read without a second surface: the group's
 * hairline says what they act on, and the 4 dp between the buttons says they are two commands, not one
 * wide one (`docs/interface-contract.md` §6).
 */
@Composable
private fun HeaderDocumentDoors(pages: PageMenuActions, layers: LayerMenuActions) {
    HeaderDoor(
        enabled = pages.canManage,
        glyph = ChromeGlyph.DESCRIPTION,
        description = "Pages",
    ) { close -> PageMenuPanel(pages, close) }
    Spacer(Modifier.width(HEADER_ITEM_GAP))
    HeaderDoor(
        enabled = layers.canManage,
        glyph = ChromeGlyph.LAYERS,
        description = "Layers",
    ) { close -> LayersPanel(layers, close) }
}

/**
 * One **door** of that group: an icon command with its own picked fill and its own menu, anchored under
 * the button so Pages opens where Pages is.
 *
 * A disabled door keeps its glyph in the muted ink and takes no pointer at all — a click on it is not a
 * command that did nothing.
 */
@Composable
private fun HeaderDoor(
    enabled: Boolean,
    glyph: ChromeGlyph,
    description: String,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // The click that closes the menu must not open it again (see [DismissGuard]).
    val guard = remember { DismissGuard() }
    val drop = with(LocalDensity.current) { (HEADER_CONTROL + 4.dp).roundToPx() }
    Box {
        DesktopIconButton(
            description = description,
            onClick = { if (open) open = false else if (guard.mayOpen(Unit)) open = true },
            size = HEADER_CONTROL,
            enabled = enabled,
            picked = open,
        ) {
            Canvas(Modifier.size(HEADER_GLYPH)) {
                drawChromeGlyph(glyph, if (enabled) DesktopTheme.chromeInk else DesktopTheme.inkMuted)
            }
        }
        GlassPresence(open) {
            GlassPopup(
                offset = IntOffset(0, drop),
                onDismissRequest = {
                    guard.closed(Unit)
                    open = false
                },
                properties = FloatingSurface,
            ) {
                menu { open = false }
            }
        }
    }
}

/** The two history steps, which answer "what did I just do" rather than "where does this go". */
@Composable
private fun HeaderHistory(tab: DesktopTab?, onUndo: () -> Unit, onRedo: () -> Unit) {
    HeaderCommand("Undo", tab?.canUndo == true, shortcut = "Ctrl+Z", onClick = onUndo) { color ->
        drawChromeGlyph(ChromeGlyph.UNDO, color)
    }
    Spacer(Modifier.width(HEADER_ITEM_GAP))
    HeaderCommand("Redo", tab?.canRedo == true, shortcut = "Ctrl+Shift+Z", onClick = onRedo) { color ->
        drawChromeGlyph(ChromeGlyph.REDO, color)
    }
}

/**
 * The two **file commands** on the band's right: Save in the accent and the PDF export in the document
 * red, because they are the two acts that write, and chrome ink made them look like the history beside
 * them (`docs/ui-modernization.md`).
 */
@Composable
private fun HeaderFileCommands(
    tab: DesktopTab?,
    saving: Boolean,
    onSave: () -> Unit,
    onExportPdf: () -> Unit,
) {
    // A write says so **on the control**: the floppy gives way to an arc going round once per
    // `SavingSpin.TURN_MS`, drawn in the glyph's own box so the command beside it does not move. It is
    // dimmed when the tab has nothing left to write, which is the tone every unavailable command wears.
    val turn = rememberSavingTurn(saving)
    HeaderCommand(
        "Save",
        tab?.canSave == true,
        shortcut = "Ctrl+S",
        colour = DesktopTheme.accent,
        onClick = onSave,
    ) { color ->
        if (saving) drawSavingArc(color, turn.value) else drawChromeGlyph(ChromeGlyph.SAVE, color)
    }
    Spacer(Modifier.width(HEADER_ITEM_GAP))
    HeaderCommand(
        "Export PDF",
        tab != null,
        shortcut = "Ctrl+E",
        colour = DesktopTheme.documentRed,
        onClick = onExportPdf,
    ) { color -> drawChromeGlyph(ChromeGlyph.PDF, color) }
}

/**
 * One **glyph command** of the band: a 28 dp icon button with its English tooltip and its accelerator,
 * and the contract's colour rule in one place — the named colour while the command is live, the chrome's
 * ink for everything else, and the muted ink when it cannot run.
 *
 * [colour] is what a command that wears one of the two named tones passes (Save's accent, the PDF
 * export's document red); null keeps the chrome's ink.
 */
@Composable
private fun HeaderCommand(
    description: String,
    enabled: Boolean,
    shortcut: String? = null,
    colour: Color? = null,
    onClick: () -> Unit,
    draw: DrawScope.(Color) -> Unit,
) {
    DesktopIconButton(
        description = description,
        onClick = onClick,
        size = HEADER_CONTROL,
        enabled = enabled,
        shortcut = shortcut,
    ) {
        Canvas(Modifier.size(HEADER_GLYPH)) {
            draw(if (enabled) colour ?: DesktopTheme.chromeInk else DesktopTheme.inkMuted)
        }
    }
}
