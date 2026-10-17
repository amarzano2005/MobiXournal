package com.mobixournal.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/**
 * Everything the [TabStrip] needs, bundled so [EditorScreen]'s parameter list stays readable.
 *
 * The tabs themselves live in `com.mobixournal.tabs` and are driven by the host activity — this is
 * only the flattened view of them the chrome draws.
 */
data class TabsUiState(
    /** Tab titles in strip order (the file name, or "Untitled"). */
    val titles: List<String> = emptyList(),
    /**
     * Per-tab dot colour (packed ARGB), or null for no dot — set only on tabs that are one of
     * several views of the *same* document, so duplicates are told apart from same-named files.
     * Same colour = same document. See `com.mobixournal.tabs.DocColors`.
     */
    val dotColors: List<Int?> = emptyList(),
    val activeIndex: Int = 0,
    /**
     * 0-based pane this session belongs to, and how many panes are on screen. In split view the
     * header draws one strip per pane and each one carries a badge with its number, so a chip is
     * never mistaken for the other half's document.
     */
    val paneIndex: Int = 0,
    val paneCount: Int = 1,
    /** Whether this pane is the one the top bar and the rail drive. */
    val focused: Boolean = true,
    /** Make this pane the focused one — a touch anywhere on its strip does it. */
    val onFocus: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onClose: (Int) -> Unit = {},
    val onNew: () -> Unit = {},
    /** Long-press action: hand this tab to the other pane, opening split view if it is closed. */
    val onMove: (Int) -> Unit = {},
    /** Long-press action: open a second copy of this tab in the other pane, keeping this one. */
    val onMirror: (Int) -> Unit = {},
    /** Drag action: the tab at the first index now sits at the second one. */
    val onReorder: (Int, Int) -> Unit = { _, _ -> },
    /**
     * The tab overview is opening: the live canvas is copied back into the showing tab, so its
     * preview isn't a stale snapshot taken at the last tab switch.
     */
    val onOverview: () -> Unit = {},
    /**
     * Rasterise the page tab [Int] is showing, [Int] pixels wide, and hand the bitmap to the callback
     * on the main thread — null if it can't be drawn. Asynchronous because a tab restored from a cold
     * start still has to be parsed (gzip + XML), which is far too slow to do in a tap.
     */
    val preview: (Int, Int, (Bitmap?) -> Unit) -> Unit = { _, _, done -> done(null) },
)

/**
 * The row of open documents above the editor. Selecting a tab swaps the whole document on the
 * canvas; the trailing "+" opens a fresh blank one.
 *
 * Always shown once a document is open — including the single-document case — so "new tab", "close"
 * and "switch" sit in the same place no matter how many documents are loaded. Only an empty session
 * (no tabs at all) draws nothing.
 */
@Composable
fun TabStrip(state: TabsUiState, modifier: Modifier = Modifier) {
    if (state.titles.isEmpty()) return
    // Drag-reorder state lives here, not in the chip: a drag walks the tab past its neighbours, so the
    // slot the gesture started in stops being the dragged tab after the first swap. [dragIndex] is
    // where the dragged tab sits *now*, and [dragOffset] is how far past its slot the finger is.
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scroll = rememberScrollState()
    // Where the selected chip sits inside the (scrollable) content, so a tab selected from elsewhere —
    // or one carried past the edge by a reorder drag — is scrolled back into view instead of stranded.
    var activeBounds by remember { mutableStateOf(0f to 0f) }
    var viewportWidth by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(activeBounds, viewportWidth, scroll.maxValue) {
        val (left, width) = activeBounds
        if (viewportWidth <= 0f || width <= 0f) return@LaunchedEffect
        val target = when {
            left < scroll.value -> left
            left + width > scroll.value + viewportWidth -> left + width - viewportWidth
            else -> return@LaunchedEffect
        }
        scroll.animateScrollTo(target.toInt().coerceIn(0, scroll.maxValue))
    }
    val isDark = chromeIsDark()
    // The pane badge and the "+" stay pinned while the chips scroll: in split view each half's
    // strip is narrow, and an "add" that can only be reached by scrolling is how a tab ends up in
    // the wrong pane.
    val transferLabels = tabTransferLabels(state.paneIndex, state.paneCount)
    Row(
        modifier = modifier
            .height(TAB_STRIP_HEIGHT)
            .background(Color.Transparent)
            // Focus the pane on the initial pass, before a chip, the "+" or the badge handles the
            // tap, so the action that follows (select, new, close) already belongs to this pane.
            .pointerInput(state.paneIndex) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    state.onFocus()
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.paneCount > 1) {
            PaneBadge(number = state.paneIndex + 1, focused = state.focused, onClick = state.onFocus)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged { viewportWidth = it.width.toFloat() }
                .horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.titles.forEachIndexed { index, title ->
                TabChip(
                    title = title,
                    dotColor = state.dotColors.getOrNull(index),
                    selected = index == state.activeIndex,
                    onSelect = { state.onSelect(index) },
                    onClose = { state.onClose(index) },
                    onMove = { state.onMove(index) },
                    onMirror = { state.onMirror(index) },
                    transferLabels = transferLabels,
                    dragOffset = if (index == dragIndex) dragOffset else 0f,
                    onDragStart = { dragIndex = index; dragOffset = 0f },
                    onDrag = { dx, width ->
                        if (dragIndex >= 0) {
                            dragOffset += dx
                            // One whole chip of travel = one slot; swap and keep the rest of the offset
                            // so a long drag walks the tab across several neighbours in one gesture.
                            while (dragOffset > width && dragIndex < state.titles.lastIndex) {
                                state.onReorder(dragIndex, dragIndex + 1)
                                dragIndex++
                                dragOffset -= width
                            }
                            while (dragOffset < -width && dragIndex > 0) {
                                state.onReorder(dragIndex, dragIndex - 1)
                                dragIndex--
                                dragOffset += width
                            }
                            dragOffset = dragOffset.coerceIn(-width, width)
                        }
                    },
                    onDragEnd = { dragIndex = -1; dragOffset = 0f },
                    onBounds = { left, width -> activeBounds = left to width },
                )
            }
        }
        AddTabButton(isDark = isDark, onNew = state.onNew)
    }
}

/** The strip's trailing "+", pinned outside the scrolling chips so it is always one tap away. */
@Composable
private fun AddTabButton(isDark: Boolean, onNew: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (isDark) Color(0x22FFFFFF) else Color(0x10000000),
        border = BorderStroke(1.dp, if (isDark) com.mobixournal.ui.theme.ModernTheme.hairlineBorder else Color(0x18000000)),
        modifier = Modifier.padding(start = 4.dp, end = 8.dp),
    ) {
        IconButton(onClick = onNew, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "New document",
                modifier = Modifier.size(TAB_NEW_ICON),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The pane's number, at the head of its strip, in split view: filled with the accent when this is
 * the pane the tools drive and hairline-outlined when it is the other one. It is the strip's answer
 * to "which half am I editing?" — the top bar and the rail are shared, so without it the only cue
 * was a tab row that silently swapped documents when the other canvas was touched.
 *
 * Tapping it focuses the pane, which is the same thing touching its canvas does.
 */
@Composable
private fun PaneBadge(number: Int, focused: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink = if (focused) colors.onPrimary else colors.onSurfaceVariant
    Box(
        modifier = Modifier
            .padding(start = 8.dp, end = 2.dp)
            .size(TAB_PANE_BADGE)
            .clip(CircleShape)
            .background(if (focused) colors.primary else Color.Transparent)
            .then(
                if (focused) Modifier
                else Modifier.border(1.dp, colors.outlineVariant, CircleShape),
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription =
                    if (focused) "Pane $number, focused" else "Pane $number, tap to focus"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
    }
}

/**
 * One tab: its (elided) title, and a close button offered on every tab, selected or not.
 *
 * A long press opens the pane menu — move this document to the other half of a split, or mirror a
 * second view of it there — so both live where the tab does rather than in the app bar. Dragging it
 * sideways instead reorders it within this strip. A [dotColor]
 * marks a tab that is one of several views of one document.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(
    title: String,
    dotColor: Int?,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onMove: () -> Unit,
    onMirror: () -> Unit,
    transferLabels: TabTransferLabels,
    dragOffset: Float,
    onDragStart: () -> Unit,
    onDrag: (dx: Float, chipWidth: Float) -> Unit,
    onDragEnd: () -> Unit,
    onBounds: (left: Float, width: Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val drag = rememberUpdatedState(Triple(onDragStart, onDrag, onDragEnd))
    var chipWidth by remember { mutableFloatStateOf(1f) }
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = TAB_CHIP_VERTICAL_PADDING)
            .heightIn(min = TAB_TOUCH_TARGET)
            .graphicsLayer { translationX = dragOffset }
            .onSizeChanged { chipWidth = it.width.toFloat().coerceAtLeast(1f) }
            .onGloballyPositioned {
                if (selected) onBounds(it.positionInParent().x, it.size.width.toFloat())
            }
            .tabChipInteractions(selected, onSelect, { menuOpen = true }, drag, chipWidth)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabChipMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            labels = transferLabels,
            onMove = onMove,
            onMirror = onMirror,
        )
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(TAB_DOT_SIZE)
                    .clip(CircleShape)
                    .background(Color(dotColor)),
            )
        }
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 64.dp, max = 180.dp),
        )
        TabCloseButton(title = title, selected = selected, onClose = onClose)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.tabChipInteractions(
    selected: Boolean,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    drag: androidx.compose.runtime.State<Triple<() -> Unit, (Float, Float) -> Unit, () -> Unit>>,
    chipWidth: Float,
): Modifier {
    val isDark = chromeIsDark()
    val shape = RoundedCornerShape(TAB_CHIP_CORNER)
    val glassColor = rememberGlassSurfaceColor()
    val hairlineColor = com.mobixournal.ui.theme.ModernTheme.hairlineBorder(isDark)
    val background = if (selected) {
        glassColor
    } else {
        glassColor.copy(alpha = if (isDark) 0.45f else 0.6f)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        hairlineColor
    }
    return this
        .border(BorderStroke(1.dp, borderColor), shape)
        .clip(shape)
        .background(background)
        .combinedClickable(onClick = onSelect, onLongClick = onLongClick)
        .pointerInput(selected) {
            if (!selected) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { drag.value.first },
                onDragEnd = { drag.value.third },
                onDragCancel = { drag.value.third },
            ) { change, dx ->
                change.consume()
                drag.value.second(dx, chipWidth)
            }
        }
}

@Composable
private fun TabChipMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    labels: TabTransferLabels,
    onMove: () -> Unit,
    onMirror: () -> Unit,
) {
    ToolbarMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(labels.move) },
            onClick = { onDismiss(); onMove() },
        )
        DropdownMenuItem(
            text = { Text(labels.mirror) },
            onClick = { onDismiss(); onMirror() },
        )
    }
}

@Composable
private fun TabCloseButton(
    title: String,
    selected: Boolean,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(TAB_TOUCH_TARGET).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Close $title",
            modifier = Modifier.size(TAB_CLOSE_ICON),
            tint = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

/** The mirrored-document dot: big enough to read as a colour, small enough not to crowd the title. */
private val TAB_DOT_SIZE = 6.dp

/** The pane badge at the head of a split strip: a number that still fits the 38dp row. */
private val TAB_PANE_BADGE = 20.dp

/**
 * The strip's tap-target size. Below Material's 48dp minimum on purpose: the strip is deliberately
 * compact so it steals as little canvas as it can, and every target here is a wide chip or an icon
 * with clear space around it rather than a dense cluster.
 */
private val TAB_TOUCH_TARGET = 36.dp

/** The close/new icon glyphs, scaled to sit inside the compact [TAB_TOUCH_TARGET]. */
private val TAB_CLOSE_ICON = 16.dp
private val TAB_NEW_ICON = 18.dp

/** Vertical breathing room above and below each chip. */
private val TAB_CHIP_VERTICAL_PADDING = 3.dp

/** The strip's height: a touch target's worth of tab, trimmed by two dp so the band stays slim. */
private val TAB_STRIP_HEIGHT = TAB_TOUCH_TARGET + TAB_CHIP_VERTICAL_PADDING * 2 - 4.dp

/** Corner of a tab chip — rounded enough to read as a pill, square enough to pack several. */
private val TAB_CHIP_CORNER = 14.dp
