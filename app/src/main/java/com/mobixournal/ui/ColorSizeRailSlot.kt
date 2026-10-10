package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mobixournal.render.DrawingSurfaceView

/**
 * The rail's **Colour & size** slot: the whole stroke story in one place, in the two parts a stroke
 * actually gets chosen in.
 *
 * - The **three favourite colours** of the tool in play, stacked **vertically** down the slot: one tap
 *   takes a colour without opening anything. The terna belongs to the tool — [favoriteToolFor] picks
 *   the highlighter's while the highlighter is live and the pen's otherwise — so the slot never shows
 *   both and never spends two rail positions on colour. A **long-press** on one opens the palette to
 *   redefine it.
 * - The **chevron** beside them opens the full [ColorSizePopup] — the palette, the tip sizes and the
 *   line style.
 *
 * The two used to be two rail slots (**Colour & size** and **Favourite colours**) for what is one
 * errand, which cost the rail a position each and made the rail taller than the screen. They are one
 * slot now: it draws at exactly one slot size ([LocalRailSlotSize]), so the rail's sizing and the
 * vertical stack agree on how much room a stroke's settings take.
 *
 * **Proportions.** The slot's width is split by [DOT_STRIP_FRACTION] / [STRIP_GAP_FRACTION] /
 * [CHEVRON_STRIP_FRACTION]: the dots get a little over half — three cells of a third of the slot's
 * height each, each dot inset by [DOT_INSET] so the three read as a strip rather than as three blobs
 * that touch — and the chevron a centred square of the rest, sized and clipped as a button of its own.
 */
@Composable
fun ColorSizeRailSlot(
    /** The tool the rail is on; the dots show *its* terna, never both. */
    tool: EditorTool,
    favorites: ToolbarFavoritesCallbacks,
    /** Colour, tip size and line style — what the chevron's pop-up edits. */
    style: ToolbarStyleCallbacks,
) {
    val colorOwner = favoriteToolFor(tool)
    val colors = favoritesFor(colorOwner, favorites)
    val slot = LocalRailSlotSize.current
    // Which dot's palette is open (-1 = none), and whether the shared custom-colour editor is up.
    var editing by remember { mutableStateOf(-1) }
    var customEditing by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier.height(slot),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Column(verticalArrangement = Arrangement.Center) {
                    val cellHeight = slot / AppSettings.FAVORITE_COUNT
                    colors.forEachIndexed { index, color ->
                        FavoriteSwatch(
                            color = color,
                            selected = color == style.color,
                            width = slot * DOT_STRIP_FRACTION,
                            height = cellHeight,
                            contentDescription =
                                "${colorOwner.label} favourite ${index + 1}: ${colorDisplayName(color)}",
                            onClick = { favorites.onPick(colorOwner, color) },
                            onLongClick = { editing = index },
                        )
                    }
                }
                Spacer(Modifier.width(slot * STRIP_GAP_FRACTION))
                ColorSizePopup(style) { open -> SlotChevron(open, slot) }
            }
        }
        ToolbarMenu(expanded = editing in colors.indices, onDismissRequest = { editing = -1 }) {
            val index = editing
            if (index in colors.indices) {
                MenuHeading("${colorOwner.label} favourite ${index + 1}")
                ColorPaletteRows(
                    selected = colors[index],
                    palette = style.palette,
                    onPick = { picked -> favorites.onAssign(colorOwner, index, picked); editing = -1 },
                    onEditCustom = { customEditing = true; editing = -1 },
                    compact = true,
                )
            }
        }
    }
    CustomColorEditor(visible = customEditing, palette = style.palette, onDismiss = { customEditing = false })
}

/**
 * The three parts of the slot's width: the dots, the gap beside them, and the chevron. They add up to
 * less than one slot on purpose, so the row reads as one control with air at its edges rather than as
 * three things touching. The dots get the larger share — the colour is the errand, the chevron the
 * doorway.
 */
private const val DOT_STRIP_FRACTION = 0.52f
private const val STRIP_GAP_FRACTION = 0.04f
private const val CHEVRON_STRIP_FRACTION = 0.40f

/** How much smaller a dot is than the cell around it, and the floor that keeps a tiny slot visible. */
private val DOT_INSET = 2.dp
private val MIN_DOT = 5.dp

/**
 * The slot's chevron: the affordance onto the full Colour & size pop-up, the same "there is more
 * behind this slot" glyph the tool-group slots wear ([MenuChevron]) — but a **target of its own**
 * rather than a corner decoration, since this slot's main surface belongs to the dots.
 *
 * It is a square button of [CHEVRON_STRIP_FRACTION] of the slot, centred on the row, not a full-height
 * strip: a column as tall as the slot but only wide enough for a glyph reads as empty space that
 * happens to have an arrow in it, and puts a target where the hand expects none.
 */
@Composable
private fun SlotChevron(open: () -> Unit, slot: Dp) {
    val button = slot * CHEVRON_STRIP_FRACTION
    Box(
        modifier = Modifier
            .size(button)
            .clip(RoundedCornerShape(button * 0.3f))
            .clickable(onClick = open)
            .semantics { contentDescription = "Colour, tip size and line style" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(button * 0.7f),
        )
    }
}

/**
 * The shape the dots' own feedback is drawn in: a **true circle centred** in whatever rectangle the tap
 * cell happens to be, which is not what `CircleShape` gives — that inscribes an *oval*, and the cells here
 * are wider than they are tall (three of them share one rail slot's height).
 *
 * The point is the indication: a tap's ripple and a hovering stylus' state layer are clipped by the node
 * they are drawn on, so on a bare rectangular cell they light up a grey box around a round dot. With the
 * cell clipped to this shape the highlight is the dot's own circle — the dot, ringed — while the cell keeps
 * its rectangle for the *touch target*, which a clip does not shrink.
 */
internal val DotCellShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = minOf(size.width, size.height) / 2f
        val square = Rect(
            offset = Offset(size.width / 2f - radius, size.height / 2f - radius),
            size = Size(radius * 2f, radius * 2f),
        )
        return Outline.Rounded(RoundRect(square, radiusX = radius, radiusY = radius))
    }
}

/** One favourite: a filled dot, ringed while it is the colour in use. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteSwatch(
    color: Int,
    selected: Boolean,
    width: Dp,
    height: Dp,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width, height)
            // Before the clickable, so the ripple and the hover layer are drawn inside it: the feedback
            // belongs to the dot, not to the cell the dot is tapped in.
            .clip(DotCellShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size((minOf(width, height) - DOT_INSET).coerceAtLeast(MIN_DOT))
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                    },
                    shape = CircleShape,
                ),
        )
    }
}

/**
 * Whose favourites the slot shows when the rail is on [tool]: the **highlighter**'s while the
 * highlighter is live, otherwise the **pen**'s. One rule, so the dots can never show both ternas
 * (which is what would make them cost the rail two positions) and so a non-inking tool like the
 * eraser still offers the pen's colours rather than an empty strip.
 */
fun favoriteToolFor(tool: EditorTool): EditorTool =
    if (tool == EditorTool.HIGHLIGHTER) EditorTool.HIGHLIGHTER else EditorTool.PEN

/** The terna [colorOwner] draws its dots from. */
fun favoritesFor(colorOwner: EditorTool, favorites: ToolbarFavoritesCallbacks): List<Int> =
    (if (colorOwner == EditorTool.HIGHLIGHTER) favorites.highlighterFavorites else favorites.penFavorites)
        .take(AppSettings.FAVORITE_COUNT)

/**
 * This settings with favourite [index] of [colorOwner]'s terna set to [color] — the long-press
 * reassignment. An out-of-range index is ignored rather than resizing the terna: the slot draws a
 * fixed [AppSettings.FAVORITE_COUNT], so the list must stay that long.
 */
fun assignFavorite(
    settings: AppSettings,
    colorOwner: EditorTool,
    index: Int,
    color: Int,
): AppSettings {
    val opaque = color or 0xFF000000.toInt()
    return if (colorOwner == EditorTool.HIGHLIGHTER) {
        settings.copy(highlighterFavorites = settings.highlighterFavorites.replacedAt(index, opaque))
    } else {
        settings.copy(penFavorites = settings.penFavorites.replacedAt(index, opaque))
    }
}

/**
 * This settings with the favourite colour written into the **owning tool's** slot — the pen's
 * `lastColor` or the highlighter's own colour, which is what a later switch back to that tool
 * restores. The same write the Colour & size pop-up makes, so a favourite and the palette cannot
 * disagree about where a colour lives.
 */
fun settingsWithFavoriteColor(settings: AppSettings, colorOwner: EditorTool, color: Int): AppSettings =
    if (colorOwner == EditorTool.HIGHLIGHTER) settings.copy(highlighterColor = color)
    else settings.withColorUsed(color)

/**
 * A tap on a favourite: take the chosen colour without switching the active tool back to pen.
 *
 * The chosen colour is applied directly to [ui.color] and [DrawingSurfaceView.colorArgb], without
 * switching tools away from whichever tool is currently active. The owning tool's settings slot
 * is persisted so that switching tools later retains the chosen colour.
 */
internal fun pickFavoriteColor(
    surface: DrawingSurfaceView?,
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    colorOwner: EditorTool,
    color: Int,
) {
    val next = settingsWithFavoriteColor(settings, colorOwner, color)
    if (colorOwner == EditorTool.HIGHLIGHTER) {
        ui.highlighterColor = color
        if (ui.tool == EditorTool.HIGHLIGHTER) {
            ui.color = color
            surface?.colorArgb = color
        }
    } else {
        ui.color = color
        surface?.colorArgb = color
    }
    onSettingsChange(next)
}

/** [this] with the entry at [index] replaced by [value]; out-of-range indices are a no-op. */
private fun List<Int>.replacedAt(index: Int, value: Int): List<Int> =
    if (index in indices) toMutableList().also { it[index] = value } else this
