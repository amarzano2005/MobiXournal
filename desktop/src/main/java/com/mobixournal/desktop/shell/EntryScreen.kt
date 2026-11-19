package com.mobixournal.desktop.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.desktop.io.DesktopDocuments
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.tools.DesktopButton
import com.mobixournal.desktop.tools.DesktopButtonVariant
import com.mobixournal.desktop.tools.hoverFill
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the window shows with **nothing open** — at startup, and after the last tab is closed.
 *
 * Three doors in **three columns**: the commands (start a sheet, open one), the sheet itself drawn as the
 * page it will be — the reader's own size and ruling, through the painter the page list uses — and the
 * notebooks opened lately as a grid of cards whose face is their first page. A single centred stack left a
 * 3440 px desk as three small objects in the middle of a hole; the columns are what spreads them across the
 * width while staying one block on a laptop's 1100. Below [NARROW_DESK_DP] the three columns become one, so
 * a small window scrolls instead of clipping.
 *
 * The cards are **surfaces of this window** — the 8 dp [DesktopTheme.surfaceShape], the panel's own fill and
 * one hairline — not a picture floating in a halo. Twenty-six concentric white rings around every thumbnail
 * read as a sticker rather than a lift (`docs/desktop-ui-layout.md` §1), and they cost the paper 12 dp of its
 * own preview on every side.
 */
@Composable
internal fun EntryScreen(state: DesktopShellState, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    // The recents live in the settings file, which is not Compose state: the counter a landed open bumps is
    // what tells this screen that the list it remembers has changed.
    val revision = DesktopOpens.recentsRevision
    val cards = remember(revision, state) { state.entryCards() }
    // The last run's unsaved documents are **offered**, never adopted behind the reader's back: one
    // badged card each, and *Restore last session* under the button that starts a fresh sheet.
    val pendingSession = cards.any { it.fromPreviousSession }
    val sheet = remember(state) {
        DesktopDocuments.blankPage(state.settings.pageWidthPt, state.settings.pageHeightPt)
    }
    // The screen wears the **band's own fill**: with nothing open there is no page and no desk to show, so
    // the window is one surface from the header to the status bar, and only the cards sit on it. A black
    // floor there read as a hole between two bands.
    Box(Modifier.fillMaxSize().background(DesktopTheme.glass)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The block scrolls as a whole and is centred while it fits: a 700 dp-tall window could not
            // reach the third row of cards, because the grid's own overflow was clipped by the window.
            val room = maxHeight
            // Read once, outside the column's own scope: `maxWidth` there would be the column's name, not the
            // window's, and the two are not the same question.
            val deskWidth = maxWidth
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = room)
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The block grows with the desk: a 3440 px monitor is not a reason to read laptop-sized
                // cards in the middle of it, and a 1100 px window is not a reason to overflow.
                val wide = deskWidth > WIDE_DESK_DP.dp
                val gap = if (wide) WIDE_GAP_DP else COLUMN_GAP_DP
                val hero = if (wide) WIDE_HERO_WIDTH_DP else HERO_WIDTH_DP
                val recent = if (wide) WIDE_RECENT_WIDTH_DP else RECENT_WIDTH_DP
                val start = @Composable { modifier: Modifier ->
                    StartColumn(
                        modifier = modifier,
                        onNew = { state.startFreshSession() },
                        onOpen = onOpen,
                        onRestore = if (pendingSession) ({ state.restoreLastSession() }) else null,
                    )
                }
                val heroCard = @Composable { modifier: Modifier ->
                    SheetCard(
                        key = "new-sheet:${sheet.width}x${sheet.height}",
                        widthDp = hero,
                        caption = "New sheet",
                        modifier = modifier,
                        load = { renderPageBitmap(sheet, hero / sheet.width.toFloat()) },
                        onClick = { state.startFreshSession() },
                    )
                }
                val recents = @Composable {
                    EntryColumn(
                        cards,
                        cardWidthDp = recent,
                        columns = if (wide) WIDE_RECENT_COLUMNS else RECENT_COLUMNS,
                        onOpenCard = { card -> state.openEntry(card, scope) },
                        onRemoveCard = { card -> state.forgetEntry(card) },
                    )
                }
                if (deskWidth < NARROW_DESK_DP.dp) {
                    Column(
                        modifier = Modifier.widthIn(max = ENTRY_MAX_WIDTH_DP.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        start(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(24.dp))
                        heroCard(Modifier)
                        Spacer(Modifier.height(24.dp))
                        recents()
                    }
                } else {
                    Row(
                        modifier = Modifier.widthIn(max = if (wide) WIDE_MAX_WIDTH_DP.dp else ENTRY_MAX_WIDTH_DP.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        start(Modifier.width((if (wide) WIDE_START_COLUMN_DP else START_COLUMN_WIDTH_DP).dp))
                        Spacer(Modifier.width(gap.dp))
                        heroCard(Modifier)
                        Spacer(Modifier.width(gap.dp))
                        recents()
                    }
                }
            }
        }
    }
}

/**
 * The first column: what the reader can do, above the keys that do it.
 *
 * The commands are the window's one button ([DesktopButton]), full width so the stack reads as one column
 * and not as three chips of three widths — the entry screen used to grow its own capsule `EntryButton`,
 * which was the only place in the app where a commit wore the tablet's glass.
 */
@Composable
private fun StartColumn(
    modifier: Modifier,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onRestore: (() -> Unit)?,
) {
    Column(modifier) {
        Text("Nothing open yet", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(8.dp))
        Text(
            "Start a sheet, or pick up a notebook where you left it.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(18.dp))
        EntryCommand("New sheet", ChromeGlyph.NEW, DesktopButtonVariant.Primary, onNew)
        // Directly under *New sheet*, because it is the same decision: start here, or carry on with the
        // documents the last run left unsaved. Absent when there is nothing to restore.
        if (onRestore != null) {
            Spacer(Modifier.height(8.dp))
            EntryCommand("Restore last session", ChromeGlyph.RESTORE, DesktopButtonVariant.Secondary, onRestore)
        }
        Spacer(Modifier.height(8.dp))
        EntryCommand("Open…", ChromeGlyph.OPEN, DesktopButtonVariant.Secondary, onOpen)
        Spacer(Modifier.height(10.dp))
        Text("Ctrl+N · Ctrl+O", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    }
}

/** A door of the first column: the header's own glyph for the command, then the words. */
@Composable
private fun EntryCommand(
    label: String,
    glyph: ChromeGlyph,
    variant: DesktopButtonVariant,
    onClick: () -> Unit,
) {
    val ink = if (variant == DesktopButtonVariant.Primary) DesktopTheme.onAccent else DesktopTheme.inkSecondary
    DesktopButton(
        label = label,
        onClick = onClick,
        variant = variant,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 36.dp,
        style = DesktopTheme.tab,
        leadingIcon = {
            Canvas(Modifier.size(16.dp)) { drawChromeGlyph(glyph, ink) }
        },
    )
}

/**
 * The third column: the documents the last run left unsaved, then the notebooks opened lately, three to a
 * line.
 *
 * The session's cards come **first** because they carry the newest work there is, and a recent the same
 * snapshot already stands for is left out ([entryCards]). With none, the column is one sentence rather
 * than an empty grid: a heading over nothing reads as a list that failed to load, and the reader's next
 * move is the Open button either way.
 */
@Composable
private fun EntryColumn(
    cards: List<EntryCard>,
    cardWidthDp: Float,
    columns: Int,
    onOpenCard: (EntryCard) -> Unit,
    onRemoveCard: (EntryCard) -> Unit,
) {
    // The grid is as wide as the cards it actually has: reserving four columns for two notebooks left the
    // block off-centre by the width of the two empty ones, which read as a mistake rather than as a grid.
    val perLine = minOf(columns, cards.size).coerceAtLeast(1)
    Column(Modifier.width((cardWidthDp * perLine + RECENT_GAP_DP * (perLine - 1)).dp)) {
        // The block's subtitle, not a caption: the section heading's face, one step up from the labels under
        // the cards, and its first letter on the first card's **left edge** — the cards carry no spread any
        // more, so the heading and the grid share one inset instead of starting 12 dp apart.
        Text("Recents", color = DesktopTheme.ink, style = DesktopTheme.subtitle)
        // Four, not ten: at the subtitle's size the heading already stands apart from the cards, and ten left
        // it floating over the grid instead of introducing it.
        Spacer(Modifier.height(4.dp))
        if (cards.isEmpty()) {
            Text(
                "No notebook yet — Open… finds a .xopp or .mobix file.",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.body,
            )
            return@Column
        }
        cards.chunked(perLine).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(RECENT_GAP_DP.dp)) {
                line.forEach { card ->
                    SheetCard(
                        key = "${card.id}:${card.file.lastModified()}:$cardWidthDp",
                        widthDp = cardWidthDp,
                        caption = card.title,
                        fromSession = card.fromPreviousSession,
                        onRemove = { onRemoveCard(card) },
                        load = { firstPageOf(card, cardWidthDp) },
                        onClick = { onOpenCard(card) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/**
 * One card: the sheet's own picture, its name, and the window's surface under both.
 *
 * The preview is painted **off the UI thread** and kept — a card is a full page render, and six of them
 * inside composition would be six page paints on the frame the screen appears in. Until one lands the card
 * keeps the bare panel fill, which is what an empty sheet of a tile looks like, not a hole.
 *
 * The `×` is **not** painted until the pointer or the keyboard arrives: a red badge on every card made the
 * grid read as a row of errors, and the remove mark is not what a notebook is. Hovering lifts the rim to
 * [DesktopTheme.borderStrong] over a [DesktopTheme.hoverSlot] step, the same two states a control has.
 */
@Composable
private fun SheetCard(
    key: String,
    widthDp: Float,
    caption: String,
    /** A document the last run left unsaved: it wears the restore badge in its top-left corner. */
    fromSession: Boolean = false,
    /** The card's `×`, or null for the sheet to start — there is nothing to remove there. */
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    load: suspend () -> ImageBitmap?,
    onClick: () -> Unit,
) {
    var preview by remember(key) { mutableStateOf(PREVIEWS[key]) }
    LaunchedEffect(key) {
        if (preview != null) return@LaunchedEffect
        val painted = withContext(PREVIEW_PAINT) { runCatching { load() }.getOrNull() }
        if (painted != null) {
            PREVIEWS[key] = painted
            preview = painted
        }
    }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val rim = if (hovered || focused) DesktopTheme.borderStrong else DesktopTheme.hairline
    Column(modifier.width(widthDp.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(widthDp.dp)
                .aspectRatio(SHEET_ASPECT)
                .clip(DesktopTheme.surfaceShape)
                .background(DesktopTheme.popupSurface, DesktopTheme.surfaceShape)
                .background(
                    if (hovered) DesktopTheme.hoverSlot else Color.Transparent,
                    DesktopTheme.surfaceShape,
                )
                .border(1.dp, rim, DesktopTheme.surfaceShape)
                .hoverable(interactions)
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The paper fills the card: no inset for a halo, so a 100 dp recent shows a 100 dp page.
            preview?.let { bitmap ->
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        image = bitmap,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                }
            }
            // The two marks that sit **on** the card: the session badge at the top-left, the remove mark at
            // the top-right, and only while the card is under the pointer or the keyboard.
            if (fromSession && !hovered) {
                SessionBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            if (onRemove != null && (hovered || focused)) {
                RemoveButton(onRemove, Modifier.align(Alignment.TopEnd).padding(2.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = caption,
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Left-aligned with the paper's own left edge, which is the card's: there is no spread to clear.
            modifier = Modifier.width(widthDp.dp),
        )
    }
}

/**
 * The mark on a document the last run left unsaved: a clock turned back in floating glass, in the card's
 * top-left corner. It is the picture *Restore last session* wears — one mark, one idea.
 */
@Composable
private fun SessionBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(SESSION_BADGE_DP.dp)
            .background(DesktopTheme.floating, CircleShape)
            .border(1.dp, DesktopTheme.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(SESSION_BADGE_GLYPH_DP.dp)) {
            drawChromeGlyph(ChromeGlyph.RESTORE, DesktopTheme.ink)
        }
    }
}

/**
 * A card's `×` on the sheet's top-right corner: the notebook leaves the entry screen.
 *
 * Invisible until the card is hovered or focused, so nothing on this screen is red at rest; then it is one
 * 22 dp control with the 4 dp hover step, in the chrome's own ink rather than a warning colour.
 */
@Composable
private fun RemoveButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(REMOVE_TOUCH_DP.dp)
            .clip(DesktopTheme.controlShape)
            .hoverFill(DesktopTheme.controlShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(REMOVE_GLYPH_DP.dp)) {
            drawChromeGlyph(ChromeGlyph.CLOSE, DesktopTheme.inkSecondary)
        }
    }
}

/**
 * The card's first page, at [widthDp] wide — the **snapshot** for a document the last run left unsaved,
 * so the card shows the page as those edits left it. The archive is opened, rendered and closed again.
 */
private suspend fun firstPageOf(card: EntryCard, widthDp: Float): ImageBitmap? {
    val pending = card.recovery
    val document = runCatching {
        if (pending != null) DocumentLoader.loadRecovered(pending.snapshot, pending.origin)
        else DocumentLoader.load(card.file)
    }.getOrNull() ?: return null
    return try {
        val page = runCatching { document.page(0) }.getOrNull() ?: return null
        if (page.width <= 0.0) return null
        renderPageBitmap(page, widthDp / page.width.toFloat(), backdrop = document.backdrop)
    } finally {
        runCatching { document.close() }
    }
}

/**
 * The lane previews are painted on: one, because a page render walks the document it shares with whatever
 * else asks, and a card does not need two at once (`PageSidebar`'s thumbnails narrow the same pool).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val PREVIEW_PAINT = Dispatchers.Default.limitedParallelism(1)

/** Cards already painted, by file and modification time: a screen that comes back does not re-render them. */
private val PREVIEWS = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

/** The badge a previous-session card carries, and the mark inside it. */
private const val SESSION_BADGE_DP = 20f

private const val SESSION_BADGE_GLYPH_DP = 12f

/** A card's remove mark: one 22 dp control, and the glyph inside it. */
private const val REMOVE_TOUCH_DP = 22f

private const val REMOVE_GLYPH_DP = 12f

/** The block's own bound: three columns that stay one block on a laptop and do not stretch on a 3440 px desk. */
private const val ENTRY_MAX_WIDTH_DP = 1000f

/** Below this the three columns become one: a 976 dp window is where the block stops fitting side by side. */
private const val NARROW_DESK_DP = 976f

/** Between the commands and the sheet, and between the sheet and the grid. */
private const val COLUMN_GAP_DP = 44f

/** The commands' column: a title, one sentence and two buttons, so it is read as one paragraph. */
private const val START_COLUMN_WIDTH_DP = 264f

/** The sheet to start: the widest card on the screen, because it is the thing to click first. */
private const val HERO_WIDTH_DP = 236f

/** A recent notebook's card, and how many fit on a line of the third column. */
private const val RECENT_WIDTH_DP = 100f

private const val RECENT_COLUMNS = 3

private const val RECENT_GAP_DP = 12f

/**
 * Above this the block steps up a size: the 21:9 desk, not the laptop.
 *
 * In **dp**, not pixels — a 3440 px monitor at Windows' 175 % is ~1965 dp, and a threshold written in pixels
 * would have stepped up on a scaled laptop and not on the monitor it was chosen for.
 */
private const val WIDE_DESK_DP = 1400f

/** The wide block's bound: wide enough to fill a 21:9 desk, narrow enough not to read as a stretched row. */
private const val WIDE_MAX_WIDTH_DP = 1600f

private const val WIDE_GAP_DP = 72f

private const val WIDE_START_COLUMN_DP = 340f

private const val WIDE_HERO_WIDTH_DP = 340f

private const val WIDE_RECENT_WIDTH_DP = 148f

/** One more card to the line when the desk has the room. */
private const val WIDE_RECENT_COLUMNS = 4

/** Paper's proportions, so a card is the shape of the page it previews. */
private const val SHEET_ASPECT = 0.707f
