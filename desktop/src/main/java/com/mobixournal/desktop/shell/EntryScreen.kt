package com.mobixournal.desktop.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.desktop.io.DesktopDocuments
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.tools.hoverFill
import java.io.File
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
 * width while staying one block on a laptop's 1100.
 *
 * The cards are the **sheet's own rectangle**, not a capsule: a rounded chip around rectangular paper
 * misreads the shape of the thing it shows. They keep the family's rim and hover step, so they are still
 * objects of this window and not a second language.
 */
@Composable
internal fun EntryScreen(state: DesktopShellState, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    // The recents live in the settings file, which is not Compose state: the counter a landed open bumps is
    // what tells this screen that the list it remembers has changed.
    val revision = DesktopOpens.recentsRevision
    val recents = remember(revision, state) { state.settings.recents.files }
    val sheet = remember(state) {
        DesktopDocuments.blankPage(state.settings.pageWidthPt, state.settings.pageHeightPt)
    }
    // The screen wears the **band's own fill**: with nothing open there is no page and no desk to show, so
    // the window is one surface from the header to the status bar, and only the cards sit on it. A black
    // floor there read as a hole between two bands.
    Box(Modifier.fillMaxSize().background(DesktopTheme.glass), contentAlignment = Alignment.Center) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // The block grows with the desk: a 3440 px monitor is not a reason to read laptop-sized cards
            // in the middle of it, and a 1100 px window is not a reason to overflow.
            val wide = maxWidth > WIDE_DESK_DP.dp
            val gap = if (wide) WIDE_GAP_DP else COLUMN_GAP_DP
            val hero = if (wide) WIDE_HERO_WIDTH_DP else HERO_WIDTH_DP
            val recent = if (wide) WIDE_RECENT_WIDTH_DP else RECENT_WIDTH_DP
            Row(
                modifier = Modifier
                    .widthIn(max = if (wide) WIDE_MAX_WIDTH_DP.dp else ENTRY_MAX_WIDTH_DP.dp)
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StartColumn(
                    widthDp = if (wide) WIDE_START_COLUMN_DP else START_COLUMN_WIDTH_DP,
                    onNew = { state.newDocument() },
                    onOpen = onOpen,
                )
                Spacer(Modifier.width(gap.dp))
                SheetCard(
                    key = "new-sheet:${sheet.width}x${sheet.height}",
                    widthDp = hero,
                    caption = "New sheet",
                    load = { renderPageBitmap(sheet, hero / sheet.width.toFloat()) },
                    onClick = { state.newDocument() },
                )
                Spacer(Modifier.width(gap.dp))
                RecentColumn(
                    recents,
                    cardWidthDp = recent,
                    columns = if (wide) WIDE_RECENT_COLUMNS else RECENT_COLUMNS,
                    onOpenRecent = { file -> state.openInBackground(file, scope) },
                )
            }
        }
    }
}

/** The first column: what the reader can do, above the keys that do it. */
@Composable
private fun StartColumn(widthDp: Float, onNew: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.width(widthDp.dp)) {
        Text("Nothing open yet", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(8.dp))
        Text(
            "Start a sheet, or pick up a notebook where you left it.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(18.dp))
        EntryButton("New sheet", ChromeGlyph.NEW, primary = true, onClick = onNew)
        Spacer(Modifier.height(10.dp))
        EntryButton("Open…", ChromeGlyph.OPEN, primary = false, onClick = onOpen)
        Spacer(Modifier.height(10.dp))
        Text("Ctrl+N · Ctrl+T", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    }
}

/**
 * The third column: the notebooks opened lately, three to a line.
 *
 * With none, the column is one sentence rather than an empty grid: a heading over nothing reads as a list
 * that failed to load, and the reader's next move is the Open button either way.
 */
@Composable
private fun RecentColumn(
    recents: List<File>,
    cardWidthDp: Float,
    columns: Int,
    onOpenRecent: (File) -> Unit,
) {
    // The grid is as wide as the cards it actually has: reserving four columns for two notebooks left the
    // block off-centre by the width of the two empty ones, which read as a mistake rather than as a grid.
    val perLine = minOf(columns, recents.size).coerceAtLeast(1)
    Column(Modifier.width((cardWidthDp * perLine + RECENT_GAP_DP * (perLine - 1)).dp)) {
        Text("Recent", color = DesktopTheme.ink, style = DesktopTheme.label)
        Spacer(Modifier.height(10.dp))
        if (recents.isEmpty()) {
            Text(
                "No notebook yet — Open… finds a .xopp or .mobix file.",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.body,
            )
            return@Column
        }
        recents.chunked(perLine).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(RECENT_GAP_DP.dp)) {
                line.forEach { file ->
                    SheetCard(
                        key = "recent:${file.absolutePath}:${file.lastModified()}:$cardWidthDp",
                        widthDp = cardWidthDp,
                        caption = file.nameWithoutExtension,
                        load = { firstPageOf(file, cardWidthDp) },
                        onClick = { onOpenRecent(file) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/**
 * One card: the sheet's own picture, its name, and the desk's glass under both.
 *
 * The preview is painted **off the UI thread** and kept — a card is a full page render, and six of them
 * inside composition would be six page paints on the frame the screen appears in. Until one lands the card
 * keeps the bare glass, which is what an empty sheet of a tile looks like, not a hole.
 */
@Composable
private fun SheetCard(
    key: String,
    widthDp: Float,
    caption: String,
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
    Column(
        modifier = Modifier
            .width(widthDp.dp)
            .pointerInput(key, onClick) { detectTapGestures { onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            // No fill and no outline: the card is the **sheet and its halo**. Anything painted behind the
            // paper would be the dark band around it that this is not supposed to have.
            modifier = Modifier
                .width(widthDp.dp)
                .aspectRatio(SHEET_ASPECT),
            contentAlignment = Alignment.Center,
        ) {
            // Drawn at the box's own size rather than through `Image`'s `ContentScale`: the preview is a
            // bitmap of exactly the sheet's proportions, and fitting it let the picture arrive as a strip in
            // a box twice its width instead of filling the card.
            Canvas(Modifier.fillMaxSize()) {
                // The paper sits **inside** the card by the halo's own spread, and the halo fills the ring
                // that leaves: the same `drawPageShadow` the canvas puts round a page, so a card's sheet and
                // a page on the desk are lifted by one recipe. Its colour is the theme's own
                // (`pageShadowColor` — a glow above black on the dark theme, a soft grey on paper), which is
                // why this needs no line: a frame at the paper's edge vanished into the desk on one side and
                // into the sheet on the other.
                val spread = DesktopTheme.pageShadow.toPx()
                val sheet = Rect(spread, spread, size.width - spread, size.height - spread)
                if (sheet.width > 1f && sheet.height > 1f) {
                    drawCardHalo(sheet)
                    if (preview == null) {
                        // Before the paint lands the card shows the **empty sheet it will become**, not a
                        // glass slab that would be the band again.
                        drawRoundRect(
                            color = DesktopTheme.tabFillResting,
                            topLeft = sheet.topLeft,
                            size = sheet.size,
                            cornerRadius = CornerRadius(SHEET_CORNER_DP.dp.toPx()),
                        )
                    }
                    preview?.let { bitmap ->
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(spread.roundToInt(), spread.roundToInt()),
                            dstSize = IntSize(
                                sheet.width.roundToInt().coerceAtLeast(1),
                                sheet.height.roundToInt().coerceAtLeast(1),
                            ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = caption,
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            // Left-aligned, but starting **where the paper starts**: the card keeps the halo's spread on
            // every side, so a label flush with the card began a halo's width before the sheet it names.
            modifier = Modifier.width(widthDp.dp).padding(start = DesktopTheme.pageShadow),
        )
    }
}

/** A card's door: the glyph the header wears for the same command, then the words. */
@Composable
private fun EntryButton(label: String, glyph: ChromeGlyph, primary: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .background(
                if (primary) DesktopTheme.accent.copy(alpha = BUTTON_TINT) else DesktopTheme.tabFillResting,
                DesktopTheme.capsuleShape,
            )
            .hoverFill(DesktopTheme.capsuleShape)
            .border(
                1.dp,
                if (primary) DesktopTheme.accent else DesktopTheme.hairline,
                DesktopTheme.capsuleShape,
            )
            .pointerInput(label, onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            drawChromeGlyph(glyph, if (primary) DesktopTheme.ink else DesktopTheme.inkSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (primary) DesktopTheme.ink else DesktopTheme.inkSecondary, style = DesktopTheme.tab)
    }
}

/** The first page of [file], at [widthDp] wide — the archive is opened, rendered and closed again. */
private suspend fun firstPageOf(file: File, widthDp: Float): ImageBitmap? {
    val document = runCatching { DocumentLoader.load(file) }.getOrNull() ?: return null
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

/**
 * A card's outline: the sheet's rectangle with a 3 dp corner, not the capsule the controls wear.
 *
 * A rounded chip around rectangular paper misreads the shape of the thing it shows; three dp is the least
 * that keeps the rim from looking cut with a knife, and it is a rectangle at any size.
 */
private const val SHEET_CORNER_DP = 3f

/**
 * The lift under a card: a ring of four rectangles per step, falling off outward — the shape of the page's own
 * halo, in a tone that is **the theme's ink** instead of the page shadow's near-black.
 *
 * The page's halo can be the desk's own colour because a page is huge and its white edge does the lifting; a
 * card is a thumbnail on a near-black floor, and there a near-black shadow is no shadow at all — three passes
 * of it measured `#101011` against a `#0D0D0E` desk. The ink is light on the dark theme and dark on the light
 * one, so the same call lifts a card on both, and the strength is a card's own because the object is a
 * thumbnail, not a sheet of paper.
 */
private fun DrawScope.drawCardHalo(sheet: Rect) {
    val spread = DesktopTheme.pageShadow.toPx()
    val thickness = spread / HALO_STEPS
    val corner = SHEET_CORNER_DP.dp.toPx()
    for (step in 0 until HALO_STEPS) {
        // Distance from the paper of this ring's centre; the falloff is read from it, so the halo is strongest
        // where it meets the sheet and gone where the spread ends.
        val distance = spread * (step + 0.5f) / HALO_STEPS
        val left = 1f - distance / spread
        drawRoundRect(
            color = DesktopTheme.ink.copy(alpha = CARD_HALO_ALPHA * left * left),
            topLeft = Offset(sheet.left - distance - thickness / 2f, sheet.top - distance - thickness / 2f),
            size = Size(
                sheet.width + (distance + thickness / 2f) * 2f,
                sheet.height + (distance + thickness / 2f) * 2f,
            ),
            cornerRadius = CornerRadius(corner + distance * CORNER_BLOOM),
            style = Stroke(width = thickness),
        )
    }
}

/**
 * The halo's strength where it leaves the paper, before the falloff thins it: a lift, not a glow.
 *
 * Squared rather than linear. Rings of one alpha step each read as concentric outlines — a frame drawn four
 * times over — and the square drops the outer ones away fast enough that the edge stays soft; it also puts
 * the weight at the paper, where a lift belongs.
 */
private const val CARD_HALO_ALPHA = 0.18f

/** Steps in the falloff: many and thin, because a visible step is a visible outline. */
private const val HALO_STEPS = 26

/** How much the corner radius grows per pixel of spread, so a corner blooms instead of mitering. */
private const val CORNER_BLOOM = 0.4f

/** The block's own bound: three columns that stay one block on a laptop and do not stretch on a 3440 px desk. */
private const val ENTRY_MAX_WIDTH_DP = 1000f

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

/** The primary door's tint: the accent as a fill, not as a block of colour. */
private const val BUTTON_TINT = 0.16f
