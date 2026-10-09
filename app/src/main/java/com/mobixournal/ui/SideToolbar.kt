package com.mobixournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mobixournal.render.GuideKind
import com.mobixournal.ui.theme.rememberToolbarColor

/**
 * The vertical control rail down its docked edge (left or right): the tool slots, the one
 * **Colour & size** slot (three favourite colours a tap away plus the chevron for colour, tip size
 * and line style), and a button per pop-up panel (zoom, pages, layers, …) — each opening a small
 * [DropdownMenu] anchored to its own button (which opens to the right of the rail). [EditorScreen]
 * pushes the picked value onto the [com.mobixournal.render.DrawingSurfaceView].
 *
 * The rail is only the shell and the dispatch: each slot's pop-up lives in its own
 * `Toolbar*Popup.kt` sibling file. The shell also owns the rail's **adaptive pitch**: see
 * [ToolbarShell].
 */
@Composable
fun SideToolbar(
    tool: EditorTool,
    onTool: (EditorTool) -> Unit,
    toolGroupSelections: Map<String, EditorTool>,
    /** A member picked from a group slot's menu: **both** re-face the slot and activate the tool. */
    onToolGroupPick: (ToolGroup, EditorTool) -> Unit,
    styleCallbacks: ToolbarStyleCallbacks,
    /** The Colour & size slot's two ternas of favourite colours and what a tap or a long-press does. */
    favoritesCallbacks: ToolbarFavoritesCallbacks,
    recognizeShapes: Boolean = true,
    onRecognizeShapes: (Boolean) -> Unit = {},
    guideKind: GuideKind,
    onGuideKind: (GuideKind) -> Unit,
    layerCallbacks: ToolbarLayerCallbacks,
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    pageCallbacks: ToolbarPagesCallbacks,
    backgroundStyle: String?,
    onBackgroundStyle: (String) -> Unit,
    audio: AudioUiState = AudioUiState(),
    railOrder: List<String> = emptyList(),
    railHidden: Set<String> = emptySet(),
    /** The Shapes submenu's members in display order and the ones hidden (Figures settings). */
    shapeOrder: List<String> = emptyList(),
    shapeHidden: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val items = visibleRailItems(railOrder, railHidden)
    ToolbarShell(slotCount = items.size, modifier = modifier) {
        for (item in items) {
            val group = toolGroupForRailItem(item.id)
            if (group != null) {
                ToolGroupButton(
                    group = group,
                    // Only the Shapes slot's submenu is user-ordered; if every member is hidden the
                    // slot falls back to the factory list so it can never become a dead button.
                    members = if (group.id == SHAPE_GROUP_ID) {
                        visibleShapeTools(shapeOrder, shapeHidden).ifEmpty { group.tools }
                    } else {
                        group.tools
                    },
                    selected = group.selected(toolGroupSelections),
                    active = tool in group.tools,
                    onTool = onTool,
                    // One callback for the whole pick: the slot's new face and the tool activation
                    // are a single settings write. Split in two they would both start from the same
                    // composition snapshot, and the second write would drop the first — the slot's
                    // face would revert and the picked member's tick would never appear.
                    onPick = { picked -> onToolGroupPick(group, picked) },
                )
            } else when (item.id) {
                // One slot, both errands: the three favourite colours for the tool in play, and the
                // chevron beside them for the full pop-up (colour, tip size, line style). This is
                // where the old "colour & size" slot went: two slots for one errand became one.
                "favorites" -> ColorSizeRailSlot(
                    tool = tool,
                    favorites = favoritesCallbacks,
                    style = styleCallbacks,
                )
                "shapes" -> ShapeRecognitionButton(recognizeShapes, onRecognizeShapes)
                "guides" -> GuidePopupButton(guideKind, onGuideKind)
                "layers" -> LayersPopupButton(layerCallbacks)
                "zoom" -> ZoomPopupButton(zoom, onZoomIn, onZoomOut, onZoomReset)
                "background" -> BackgroundPopupButton(backgroundStyle, onBackgroundStyle)
                "pages" -> PagesPopupButton(pageCallbacks)
                "audio" -> AudioPopupButton(audio)
            }
        }
    }
}

/** The floating rail's width: 44dp buttons + horizontal padding. */
val SideToolbarModernWidth = 56.dp
/** Margin around the modern floating rail. */
val SideToolbarModernPadding = 6.dp
/** The modern floating rail's vertical margin, top and bottom. */
private val SideToolbarModernOuterPadding = 8.dp

/** Total width occupied by the modern vertical rail including its margins. */
val SideToolbarModernTotalWidth = SideToolbarModernWidth + (SideToolbarModernPadding * 2)

/** One slot at full scale in each UI style: the modern dock's buttons are 4dp tighter than the rail's. */
private val SideToolbarModernSlot = 44.dp

/**
 * The rail's shell: a column of slots down whichever vertical edge it is docked to, sized so that it
 * never shows half a button.
 *
 * The column measures the height it has been given and hands [railContentScale] the number of slots: it
 * shows as many as fit **whole**, at their own size, spread over the space — so the next slot's top
 * edge sits exactly on the rail's bottom edge instead of being cut by it. The slots are never drawn
 * smaller than their own size (that is what would cost the rail its at-a-glance buttons); what does not
 * fit is a scroll away. A rail whose slots all fit is left untouched.
 */
@Composable
private fun ToolbarShell(
    /** The visible slot count — the adaptive pitch's one input it cannot read off the layout. */
    slotCount: Int,
    modifier: Modifier,
    buttons: @Composable () -> Unit,
) {
    val baseSlot = SideToolbarModernSlot
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(
                horizontal = SideToolbarModernPadding,
                vertical = SideToolbarModernOuterPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // maxHeight is already net of the margins above, so the slots fill the height they were
        // handed. The rail's own *width* is never touched: the top bar reserves it with a constant
        // (SideToolbarModernTotalWidth), and the slot size is capped to stay inside it.
        val scale = railContentScale(
            availableHeightDp = maxHeight.value,
            slotCount = slotCount,
            slotDp = baseSlot.value,
            spacingDp = RailSpacing.value,
            paddingDp = RailColumnPadding.value,
        )
        CompositionLocalProvider(
            LocalRailSlotSize provides baseSlot * scale,
            LocalRailSlotScale provides scale,
        ) {
            Surface(
                modifier = Modifier.width(SideToolbarModernWidth),
                shape = RoundedCornerShape(20.dp),
                // The rail is the app's implement colour; the canvas surround reads the same value, so
                // the desk and the tools are visibly one material. See `rememberToolbarColor`.
                color = rememberToolbarColor(),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                RailColumn(scale) { buttons() }
            }
        }
    }
}

/**
 * The rail's scrolling column. The gaps take the scale, since the whole-number fit is measured with
 * them; the padding does not, so the rail's edges stay where the dock puts them.
 */
@Composable
private fun RailColumn(scale: Float, buttons: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = RailColumnPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RailSpacing * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { buttons() }
}

/**
 * One tool slot on the rail. The face is the group's currently [selected] tool.
 *
 * A **tap** on a slot that isn't live yet activates the tool it shows; tapping a slot that is
 * **already the live tool** reopens the picker over the group's other members, and picking one both
 * re-faces the slot (persisted via [onPick]) and activates it. That is the "tap again to open"
 * gesture, so no long-press is needed — a **long-press** still opens the picker as a shortcut. A
 * single-member group has nothing to pick, so a tap just activates it and no menu ever opens.
 *
 * [active] tints the slot when the editor's live tool is in this group, which is what makes the rail
 * read as a row of radio buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolGroupButton(
    group: ToolGroup,
    /** The members the picker shows, in order — [group]'s own list unless the user reordered it. */
    members: List<EditorTool> = group.tools,
    selected: EditorTool,
    active: Boolean,
    onTool: (EditorTool) -> Unit,
    onPick: (EditorTool) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(12.dp)
    val buttonSize = LocalRailSlotSize.current
    Box {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(shape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    // First tap activates the shown tool; once this slot is the live one, the next
                    // tap opens the picker instead. An open menu's own dismiss handles closing it.
                    onClick = { if (active && members.size > 1) open = true else onTool(selected) },
                    onLongClick = { if (members.size > 1) open = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                selected.icon,
                contentDescription = "Tool: ${selected.label}",
                tint = tint,
                modifier = Modifier.size(buttonSize / 2),
            )
        }
        // Only a group with something to choose wears the chevron; a single-member slot is a plain
        // button and must not look like it opens a menu.
        if (members.size > 1) MenuChevron()
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ToolGroupPicker(group, members, selected) { picked -> onPick(picked); open = false }
        }
    }
}
