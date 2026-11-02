package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.DismissGuard
import com.mobixournal.desktop.tools.FloatingSurface
import com.mobixournal.desktop.tools.bandSurface
import com.mobixournal.desktop.tools.glassCapsule
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopAudioSession
import com.mobixournal.desktop.settings.DesktopSettingsState
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.glyph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph

/**
 * The window's **header**: the menu, the tab strip, and the file commands on its right.
 *
 * The band carries the two things the mobile bar does, in the same order — the overflow menu on the
 * left and the actions that are used constantly on the right — and the strip between them. What the
 * mobile bar puts there is **undo, redo and the file actions** (its photo is the reference); the window
 * has the same four, with Save drawn as a floppy and the PDF export as a page of words, because a
 * window with no touch targets has to say what a button is by its picture alone.
 *
 * The *placement* chooser this band used to end with is gone (`docs/desktop-workstation.md`): moving the
 * strips is a setting, and a setting belongs in the Interface section rather than in the chrome it moves.
 */
/** The gap **either side** of the history pair: the field's side and the capsule's, the same one. */
private val HEADER_GROUP_GAP = 12.dp

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
    /** The menu's **Export PNG** chip — one raster per page, beside the document. */
    onExportPng: () -> Unit = {},
    /** The menu's **Import PDF** chip — the tablet's command, now that the window can carry a PDF. */
    onImportPdf: () -> Unit = {},
    onToggleSidebar: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    /** Open the Settings surface — the menu's row, and the window behind `Ctrl+,`. */
    onSettings: () -> Unit,
    /** The menu's Pen block: the door to the Stylus page, behind its heading's glyph. */
    onPenParameters: () -> Unit = {},
    /** The settings the menu's Pen block changes, so its pressure rows act on the open window. */
    penPolicy: DesktopSettingsState? = null,
    /**
     * The window's audio, or null in a band that has no audio — the render tests draw the pre-audio
     * header, so their coordinates stay the four file and history capsules.
     */
    audio: DesktopAudioSession? = null,
    /**
     * The pill's Play also picks the tablet's **Play object**, since the tool strip carries no slot for
     * it (`docs/audio.md`): a tap on the page then replays from the point touched.
     */
    onAudioTap: () -> Unit = {},
    /** The search field's text, its two steps and its dismissal — the field stands on the band, always. */
    onSearch: (String) -> Unit = {},
    onSearchNext: () -> Unit = {},
    onSearchPrevious: () -> Unit = {},
    onSearchClose: () -> Unit = {},
    /**
     * The pages' menu on the band, immediately left of the search field: the tablet's page section, with
     * the rows [PageMenuPanel] carries — the window's one door to them, now that the strip ends with its
     * last tool.
     */
    pages: PageMenuActions = PageMenuActions(),
) {
    val tab = tabs.active
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.headerHeight)
            .bandSurface()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderMenu(
            sidebarVisible = sidebarVisible,
            canSave = tab?.isDirty == true,
            canExport = tab != null,
            onNew = onNew,
            onOpen = onOpen,
            onSave = onSave,
            onSaveAs = onSaveAs,
            onExportPdf = onExportPdf,
            onExportSvg = onExportSvg,
            onExportPng = onExportPng,
            onImportPdf = onImportPdf,
            onToggleSidebar = onToggleSidebar,
            onSettings = onSettings,
            onPenParameters = onPenParameters,
            penPolicy = penPolicy,
        )
        Spacer(Modifier.width(10.dp))
        TabStrip(
            tabs = tabs,
            onActivate = onActivate,
            onClose = onClose,
            onMove = onMove,
            onNew = onNew,
            modifier = Modifier.weight(1f),
        )
        HeaderSearchSlot(tab, pages, onSearch, onSearchNext, onSearchPrevious, onSearchClose)
        // The history pair sits **halfway** between the field and the audio capsule: one gap on each
        // side, the same one, so the two steps read as their own group rather than as the field's tail.
        Spacer(Modifier.width(HEADER_GROUP_GAP))
        HeaderHistory(tab, onUndo, onRedo)
        Spacer(Modifier.width(HEADER_GROUP_GAP))
        HeaderAudioSlot(audio, tabs, onAudioTap)
        HeaderFileCommands(tab, onSave, onExportPdf)
    }
}

/**
 * The two **file commands** on the band's right, in the tablet's colours: Save in the accent and the PDF
 * export in the document red, because they were both chrome ink here and the one command that writes a PDF
 * then looked like the history steps beside it (`docs/ui-modernization.md`).
 */
@Composable
private fun HeaderFileCommands(tab: DesktopTab?, onSave: () -> Unit, onExportPdf: () -> Unit) {
    HeaderGlyph(
        active = false,
        enabled = tab?.isDirty == true,
        colour = DesktopTheme.accent,
        onClick = onSave,
    ) { color -> drawChromeGlyph(ChromeGlyph.SAVE, color) }
    Spacer(Modifier.width(6.dp))
    HeaderGlyph(
        active = false,
        enabled = tab != null,
        colour = DesktopTheme.documentRed,
        onClick = onExportPdf,
    ) { color -> drawChromeGlyph(ChromeGlyph.PDF, color) }
}

/**
 * The band's two document controls: the **pages' menu**, then the **search field**, which stands there
 * always.
 *
 * The field used to be a magnifier that opened it, and a magnifier is a second thing to learn for a door
 * `Ctrl+F` already names; a field that is simply on the band takes the band's spare width beside the tabs
 * and moves neither the tabs nor the file commands when it is used (`docs/desktop-ui-layout.md`). The
 * pages' menu stands on its left, because the page is what the reader is looking at: adding, removing or
 * stepping one is a command of the frame, and the strip keeps the tools.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderSearchSlot(
    tab: DesktopTab?,
    pages: PageMenuActions,
    onSearch: (String) -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrevious: () -> Unit,
    onSearchClose: () -> Unit,
) {
    if (tab != null) {
        Spacer(Modifier.width(10.dp))
        HeaderPagesSlot(pages)
        Spacer(Modifier.width(6.dp))
        HeaderSearchField(tab, onSearch, onSearchNext, onSearchPrevious, onSearchClose)
    }
}

/**
 * The **Pages** door: the tablet's page section button on the band, opening the six rows of
 * [PageMenuPanel] under it — the page's commands from the place the reader looks for the document rather
 * than for the tools.
 */
@Composable
private fun HeaderPagesSlot(pages: PageMenuActions) {
    var open by remember { mutableStateOf(false) }
    // The click that closes the menu must not open it again (see [DismissGuard]).
    val guard = remember { DismissGuard() }
    val density = LocalDensity.current
    val drop = with(density) { (DesktopTheme.headerControlSize + 4.dp).roundToPx() }
    Box {
        HeaderGlyph(
            active = open,
            enabled = pages.canManage,
            onClick = {
                if (open) open = false else if (guard.mayOpen(Unit)) open = true
            },
        ) { color -> drawChromeGlyph(ChromeGlyph.DESCRIPTION, color) }
        if (open) {
            Popup(
                offset = IntOffset(0, drop),
                onDismissRequest = {
                    guard.closed(Unit)
                    open = false
                },
                properties = FloatingSurface,
            ) {
                PageMenuPanel(pages) { open = false }
            }
        }
    }
}

/** The two history steps, which answer "what did I just do" rather than "where does this go". */
@Composable
private fun HeaderHistory(tab: DesktopTab?, onUndo: () -> Unit, onRedo: () -> Unit) {
    HeaderGlyph(
        active = false,
        enabled = tab?.canUndo == true,
        onClick = onUndo,
    ) { color -> drawChromeGlyph(ChromeGlyph.UNDO, color) }
    Spacer(Modifier.width(6.dp))
    HeaderGlyph(
        active = false,
        enabled = tab?.canRedo == true,
        onClick = onRedo,
    ) { color -> drawChromeGlyph(ChromeGlyph.REDO, color) }
}

/**
 * The tablet's audio capsule, in its own place: immediately **left of Save**, because the three buttons
 * act on the document the file command is about to write. A band with no session draws nothing.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderAudioSlot(
    audio: DesktopAudioSession?,
    tabs: DesktopTabs,
    onAudioTap: () -> Unit,
) {
    audio ?: return
    HeaderAudioPill(audio = audio, tab = { tabs.active }, onAudioTap = onAudioTap)
    Spacer(Modifier.width(6.dp))
}

/**
 * A header control: one of the catalogue's glyphs on a **capsule of the header's own glass** — the
 * tablet's icon buttons beside the dock, at the desktop's size.
 *
 * [enabled] is what an empty history or a clean document looks like: the capsule stays (the band is not
 * redesigned by state) and the glyph goes muted, the same tone the menu's disabled rows wear. A disabled
 * control takes no pointer at all, so a click on it is not a command that did nothing.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HeaderGlyph(
    active: Boolean,
    enabled: Boolean = true,
    /** A command that wears one of the **named** colours (Save, PDF); null keeps the chrome's ink. */
    colour: Color? = null,
    onClick: () -> Unit,
    draw: DrawScope.(androidx.compose.ui.graphics.Color) -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    // Two states, two fills: the picked steel for an open menu, the **hover step** for a resting
    // pointer, never the picked fill for a mere hover (`docs/interface-contract.md` §6).
    val fill = when {
        active && enabled -> DesktopTheme.activeSlot
        hovered && enabled -> DesktopTheme.hoverSlot
        else -> DesktopTheme.control
    }
    // On paper an unavailable command is **its glyph alone**: a capsule that cannot be pressed is a
    // shape on the band rather than an object, and a pair of them read as the grey patch the owner
    // saw where the history arrows are (2026-11-02). On black the capsule stays — there its drawn rim
    // is the only thing that marks a disabled control at all, and the fill is the band's own tone.
    val surface = if (!enabled && DesktopTheme.isLight) {
        Modifier.clip(RoundedCornerShape(DesktopTheme.headerControlSize / 2))
    } else {
        Modifier.glassCapsule(
            cornerRadius = DesktopTheme.headerControlSize / 2,
            fill = fill,
            shadow = DesktopTheme.bandShadow,
        )
    }
    Box(
        Modifier
            .size(DesktopTheme.headerControlSize)
            .then(surface)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerInput(enabled, active) { if (enabled) detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(DesktopTheme.headerGlyphSize)) {
            // The chrome ink, lit or not: on the tablet a header glyph is white and only its capsule
            // changes tone when it is active.
            draw(if (enabled) colour ?: DesktopTheme.chromeInk else DesktopTheme.inkMuted)
        }
    }
}
