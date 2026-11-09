package com.mobixournal.ui.entry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.DesignTokens
import com.mobixournal.ui.imageVector
import com.mobixournal.ui.theme.rememberGlassSurfaceColor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The **entry screen**, as the PC window has it: what the tablet shows with nothing open.
 *
 * Three doors: the commands (*New sheet*, *Open…*), the sheet itself drawn as the page it will be,
 * and the notebooks opened lately as cards whose face is their first page — one tap reopens one.
 * The arrangement is the desktop's: three columns when there is width for them, because a single
 * centred stack leaves a tablet's landscape as three small objects in the middle of a hole, and one
 * column when there is not, which is what a phone held upright gives.
 *
 * The language is the desktop's too, through the tokens both shells share: cards are the **sheet's
 * own rectangle** and carry **no rim and no fill** — only a lift, drawn in the theme's ink because a
 * shadow cannot be darker than the near-black floor under it — and every caption is inset by the
 * card's paper inset, so its first letter stands on the paper and not on a halo's width before it.
 */
@Composable
fun NotebookEntryScreen(
    recents: List<RecentNotebook>,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onOpenRecent: (RecentNotebook) -> Unit,
    onRemoveRecent: (RecentNotebook) -> Unit = {},
    onRestoreSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier.fillMaxSize().background(rememberGlassSurfaceColor()),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val wide = maxWidth >= WIDE_DESK_DP.dp
            // Read here, where the constraint is implicit: the scrollable box inside is another scope.
            val windowWidth = maxWidth
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                if (wide) {
                    Row(
                        modifier = Modifier.widthIn(max = WIDE_MAX_WIDTH_DP.dp).padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StartColumn(WIDE_START_COLUMN_DP, onNew, onOpen, ink, onRestoreSession)
                        // A step closer than the gap the grid gets: the sheet sits a little further left, and
                        // the recents gain that width at the window's edge.
                        Spacer(Modifier.width(WIDE_HERO_GAP_DP.dp))
                        NewSheetCard(WIDE_HERO_WIDTH_DP, ink, onNew)
                        Spacer(Modifier.width(WIDE_GAP_DP.dp))
                        RecentColumn(
                            recents, WIDE_GRID_DP, WIDE_RECENT_WIDTH_DP, WIDE_RECENT_COLUMNS, ink,
                            onOpenRecent, onRemoveRecent,
                        )
                    }
                } else {
                    // The narrow block is **as wide as the window allows**, and every part of it shares that
                    // width: a fixed-size grid put three cards in a 336 dp column a phone does not have, so
                    // the third was clipped and only two recent notebooks were ever visible.
                    val available = (windowWidth - 48.dp).value.coerceAtMost(ENTRY_MAX_WIDTH_DP)
                    Column(
                        modifier = Modifier.width((available + 48f).dp).padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StartColumn(available, onNew, onOpen, ink, onRestoreSession)
                        Spacer(Modifier.height(24.dp))
                        NewSheetCard(minOf(HERO_WIDTH_DP, available * HERO_SHARE), ink, onNew)
                        Spacer(Modifier.height(24.dp))
                        RecentColumn(
                            recents, available, MIN_RECENT_DP, RECENT_COLUMNS, ink,
                            onOpenRecent, onRemoveRecent,
                        )
                    }
                }
            }
        }
    }
}

/** The first column: what the reader can do. */
@Composable
private fun StartColumn(
    widthDp: Float,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    ink: Color,
    onRestoreSession: (() -> Unit)?,
) {
    Column(Modifier.width(widthDp.dp)) {
        Text(
            "Nothing open yet",
            color = ink,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Start a sheet, or pick up a notebook where you left it.",
            color = ink.copy(alpha = MUTED_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        EntryButton("New sheet", Icons.Outlined.NoteAdd, primary = true, ink = ink, onClick = onNew)
        // Directly under *New sheet*, because it is the same decision: start here, or carry on with the
        // tabs the app was closed on. It is absent when the last run closed them all (or crashed nowhere).
        if (onRestoreSession != null) {
            Spacer(Modifier.height(10.dp))
            EntryButton(
                "Restore last session",
                ChromeGlyph.RESTORE.imageVector(),
                primary = false,
                ink = ink,
                onClick = onRestoreSession,
            )
        }
        Spacer(Modifier.height(10.dp))
        EntryButton("Open…", Icons.Outlined.FolderOpen, primary = false, ink = ink, onClick = onOpen)
    }
}

/**
 * The third column: the notebooks opened lately, three to a line.
 *
 * With none, the column is one sentence rather than an empty grid: a heading over nothing reads as a
 * list that failed to load, and the reader's next move is *Open…* either way.
 */
@Composable
private fun RecentColumn(
    recents: List<RecentNotebook>,
    maxWidthDp: Float,
    minCardDp: Float,
    columns: Int,
    ink: Color,
    onOpenRecent: (RecentNotebook) -> Unit,
    onRemoveRecent: (RecentNotebook) -> Unit,
) {
    // How many cards fit side by side at a readable size, and how wide each one is when they share the
    // width they are given. The grid then holds **as many as the screen allows**, never a number chosen
    // for a desk: three at 312 dp instead of a fourth cut off at the edge.
    val capacity = ((maxWidthDp + RECENT_GAP_DP) / (minCardDp + RECENT_GAP_DP)).toInt().coerceIn(1, columns)
    val cardWidthDp = ((maxWidthDp - RECENT_GAP_DP * (capacity - 1)) / capacity).coerceAtLeast(minCardDp)
    // The grid is as wide as the cards it actually has: room for four with two notebooks leaves the block
    // off-centre by the width of the empty ones, which reads as a mistake.
    val perLine = minOf(capacity, recents.size).coerceAtLeast(1)
    val width = (cardWidthDp * perLine + RECENT_GAP_DP * (perLine - 1)).dp
    val context = LocalContext.current
    Column(Modifier.width(width)) {
        // The block's subtitle, not a caption, and its first letter on the first card's **paper**.
        Text(
            "Recents",
            color = ink,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(start = DesignTokens.PAGE_SHADOW_DP.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (recents.isEmpty()) {
            Text(
                "No notebook yet — Open… finds a .xopp or .mobix file.",
                color = ink.copy(alpha = MUTED_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        recents.chunked(perLine).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(RECENT_GAP_DP.dp)) {
                line.forEach { notebook ->
                    SheetCard(
                        // A session card is keyed by its tab: two untitled documents have no URI to tell them apart.
                        key = "recent:${notebook.sessionTab ?: notebook.uri}:$cardWidthDp",
                        caption = notebook.name,
                        widthDp = cardWidthDp,
                        ink = ink,
                        fromSession = notebook.fromPreviousSession,
                        load = { EntryPreviews.load(context, notebook.uri)?.asImage() },
                        onClick = { onOpenRecent(notebook) },
                        onRemove = { onRemoveRecent(notebook) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/** The sheet to start, first and largest, so the first thing a reader taps is the obvious one. */
@Composable
private fun NewSheetCard(widthDp: Float, ink: Color, onClick: () -> Unit) {
    SheetCard(
        key = "new-sheet",
        caption = "New sheet",
        widthDp = widthDp,
        ink = ink,
        load = { EntryPreviews.blankSheet()?.asImage() },
        onClick = onClick,
    )
}

/**
 * One card: the sheet's own picture, its name, and nothing else.
 *
 * The preview is taken **off the UI thread** and kept: a card is a page raster, and six of them on the
 * frame the screen appears in would be six renders before anything is readable. Until one lands the
 * card shows the empty sheet it will become, never a hole.
 */
@Composable
private fun SheetCard(
    key: String,
    caption: String,
    widthDp: Float,
    ink: Color,
    load: suspend () -> ImageBitmap?,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    fromSession: Boolean = false,
) {
    var preview by remember(key) { mutableStateOf(FACES[key]) }
    // Read here, not inside the canvas: the draw lambda is not a composable scope.
    val resting = MaterialTheme.colorScheme.surfaceVariant
    LaunchedEffect(key) {
        if (preview != null) return@LaunchedEffect
        val painted = withContext(Dispatchers.IO) { runCatching { load() }.getOrNull() }
        if (painted != null) {
            FACES[key] = painted
            preview = painted
        }
    }
    Column(
        modifier = Modifier.width(widthDp.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.width(widthDp.dp).aspectRatio(SHEET_ASPECT), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val spread = DesignTokens.PAGE_SHADOW_DP.dp.toPx()
                val sheet = Rect(spread, spread, size.width - spread, size.height - spread)
                if (sheet.width <= 1f || sheet.height <= 1f) return@Canvas
                drawCardHalo(sheet, ink)
                preview?.let { bitmap ->
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(spread.roundToInt(), spread.roundToInt()),
                        dstSize = IntSize(
                            sheet.width.roundToInt().coerceAtLeast(1),
                            sheet.height.roundToInt().coerceAtLeast(1),
                        ),
                    )
                } ?: drawRoundRect(
                    // Before the paint lands: the empty sheet it will become, not a glass slab.
                    color = resting,
                    topLeft = sheet.topLeft,
                    size = sheet.size,
                    cornerRadius = CornerRadius(SHEET_CORNER_DP.dp.toPx()),
                )
            }
            // The two actions that sit **on** the card, in the paper's own inset so they clear the halo:
            // the session badge at the top-left, the remove mark at the top-right.
            if (fromSession) {
                SessionBadge(ink, Modifier.align(Alignment.TopStart).padding(DesignTokens.PAGE_SHADOW_DP.dp))
            }
            onRemove?.let { remove ->
                // Centred on the paper's own corner, not laid inside it: half the disc hangs over the desk.
                RemoveButton(ink, remove, Modifier.align(Alignment.TopEnd).offset(x = REMOVE_OFFSET_DP.dp, y = -REMOVE_OFFSET_DP.dp))
            }
        }
        // Two dp, because the card's box already carries the halo's spread under the paper.
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            color = ink.copy(alpha = MUTED_ALPHA),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Left-aligned, but starting **where the paper starts**, not where the card does.
            modifier = Modifier.width(widthDp.dp).padding(start = DesignTokens.PAGE_SHADOW_DP.dp),
        )
    }
}

/**
 * The mark on a document the last run left unsaved: a clock turned back in floating glass, in the
 * card's top-left corner. It is the picture *Restore last session* wears — one mark, one idea — and
 * its description is what says the rest to a screen reader.
 */
@Composable
private fun SessionBadge(ink: Color, modifier: Modifier = Modifier) {
    val glass = rememberGlassSurfaceColor()
    Box(
        modifier = modifier
            .size(SESSION_BADGE_DP.dp)
            .background(glass, CircleShape)
            .border(DesignTokens.RIM_WIDTH_DP.dp, ink.copy(alpha = HAIRLINE_TINT), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            ChromeGlyph.RESTORE.imageVector(),
            contentDescription = "From the previous session — not saved",
            tint = ink,
            modifier = Modifier.size(SESSION_BADGE_GLYPH_DP.dp),
        )
    }
}

/**
 * A card's `×`: the document leaves the recents, marked in the danger red on the sheet's top-right
 * corner — half the disc over the desk, so the paper is never drawn through it.
 *
 * The finger gets a whole tool slot, and that slot is cut **before** the click, so the press lights the
 * circle the reader sees rather than the card behind it (`docs/interface-contract.md` §6).
 */
@Composable
private fun RemoveButton(ink: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glass = rememberGlassSurfaceColor()
    val danger = MaterialTheme.colorScheme.error
    Box(
        modifier = modifier
            .size(REMOVE_TOUCH_DP.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(REMOVE_BUTTON_DP.dp)
                .background(glass, CircleShape)
                .border(DesignTokens.RIM_WIDTH_DP.dp, ink.copy(alpha = HAIRLINE_TINT), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ChromeGlyph.CLOSE.imageVector(),
                contentDescription = "Remove from recents",
                tint = danger,
                modifier = Modifier.size(REMOVE_GLYPH_DP.dp),
            )
        }
    }
}

/** A card's door: the glyph the tablet uses for the same command, then the words. */
@Composable
private fun EntryButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    ink: Color,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .height(38.dp)
            .background(
                if (primary) accent.copy(alpha = BUTTON_TINT) else ink.copy(alpha = RESTING_TINT),
                CircleShape,
            )
            .border(1.dp, if (primary) accent else ink.copy(alpha = HAIRLINE_TINT), CircleShape)
            // Before the click, so the ripple is the capsule the reader sees: on a bare row a tap lit the
            // rectangle around it instead of the button itself (`docs/interface-contract.md` §6).
            .clip(CircleShape)
            // `clickable`, not a raw tap detector: the ripple **is** the pressed state, and the interface
            // contract asks every new control for one (`docs/interface-contract.md`).
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) ink else ink.copy(alpha = MUTED_ALPHA), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (primary) ink else ink.copy(alpha = MUTED_ALPHA),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * The lift under a card: a ring of one rectangle per step, falling off outward, in the theme's ink.
 *
 * The page's halo is the desk's own colour because a page is huge and its white edge does the lifting;
 * a card is a thumbnail on a near-black floor, where a near-black shadow is no shadow at all. The ink
 * is light on the dark theme and dark on the light one, so one call lifts a card on both. The falloff
 * is **squared** and made of many thin rings: rings of one alpha step each read as concentric outlines,
 * which is a frame drawn many times over. The reach is the **card's** share of its own width, capped by
 * the page's spread — ten dp around a thumbnail is an eighth of it, and a border again.
 */
private fun DrawScope.drawCardHalo(sheet: Rect, ink: Color) {
    val spread = minOf(DesignTokens.PAGE_SHADOW_DP.dp.toPx(), sheet.width * HALO_WIDTH_FRACTION)
    val thickness = spread / HALO_STEPS
    val corner = SHEET_CORNER_DP.dp.toPx()
    for (step in 0 until HALO_STEPS) {
        val distance = spread * (step + 0.5f) / HALO_STEPS
        val left = 1f - distance / spread
        drawRoundRect(
            color = ink.copy(alpha = CARD_HALO_ALPHA * left * left),
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

private fun android.graphics.Bitmap.asImage(): ImageBitmap = asImageBitmap()

/** Cards already painted, by notebook URI: a screen that comes back does not re-raster them. */
private val FACES = ConcurrentHashMap<String, ImageBitmap>()

/** Paper's proportions, so a card is the shape of the page it previews. */
private const val SHEET_ASPECT = 0.707f

/** A card's outline: the sheet's rectangle with a 3 dp corner, not a capsule. */
private const val SHEET_CORNER_DP = 3f

private const val CARD_HALO_ALPHA = 0.18f

private const val HALO_STEPS = 26

private const val CORNER_BLOOM = 0.4f

private const val HALO_WIDTH_FRACTION = 0.06f

private const val MUTED_ALPHA = 0.72f

private const val RESTING_TINT = 0.06f

private const val HAIRLINE_TINT = 0.16f

private const val BUTTON_TINT = 0.16f

/** The badge a previous-session card carries, and the mark inside it. */
private const val SESSION_BADGE_DP = 20f

private const val SESSION_BADGE_GLYPH_DP = 12f

/** A card's remove mark: a small disc sitting on the sheet's corner, in the danger red. */
private const val REMOVE_BUTTON_DP = 22f

private const val REMOVE_GLYPH_DP = 12f

/** Its tap target: a whole tool slot, placed so the disc's centre lands on the paper corner. */
private const val REMOVE_TOUCH_DP = 40f

/**
 * How far that target is nudged out of the card: half of it, less the paper's inset, so the disc is
 * centred on the corner and not merely touching it.
 */
private const val REMOVE_OFFSET_DP = (REMOVE_TOUCH_DP - 2 * DesignTokens.PAGE_SHADOW_DP) / 2f

/** The block's own bound: three columns that stay one block, and do not stretch on a wide tablet. */
private const val ENTRY_MAX_WIDTH_DP = 1000f

private const val WIDE_MAX_WIDTH_DP = 1600f

private const val START_COLUMN_WIDTH_DP = 264f

private const val HERO_WIDTH_DP = 236f

/** The narrowest a recent card may get before the grid shows fewer of them on a line. */
private const val MIN_RECENT_DP = 88f

/**
 * The sheet to start takes this share of a narrow block at most.
 *
 * Under half, not most of it: the card repeats a button that is right above it, while the recents
 * below are the only way back into work already started — so the space a phone has goes to them.
 */
private const val HERO_SHARE = 0.55f

private const val RECENT_COLUMNS = 3

private const val RECENT_GAP_DP = 12f

/** Above this the block steps up a size: a tablet on its side, not a phone. */
private const val WIDE_DESK_DP = 840f

private const val WIDE_GAP_DP = 72f

private const val WIDE_START_COLUMN_DP = 264f

private const val WIDE_HERO_WIDTH_DP = 236f

private const val WIDE_RECENT_WIDTH_DP = 148f

private const val WIDE_RECENT_COLUMNS = 5

/**
 * The gap between the commands and the sheet: a step closer than the grid's own, so the sheet sits a little
 * further left and the recents end inside the window's edge.
 */
private const val WIDE_HERO_GAP_DP = 28f

/** The wide grid's own width: its cards and their gaps, which the grid then fits itself into. */
private const val WIDE_GRID_DP = WIDE_RECENT_WIDTH_DP * WIDE_RECENT_COLUMNS + RECENT_GAP_DP * (WIDE_RECENT_COLUMNS - 1)
