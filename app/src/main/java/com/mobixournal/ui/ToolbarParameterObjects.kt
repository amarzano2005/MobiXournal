package com.mobixournal.ui

import com.mobixournal.format.model.LineStyle
import com.mobixournal.render.GuideKind
import com.mobixournal.render.LayerInfo

/**
 * Grouped layer-management callbacks for the toolbar. Replaces the 10 individual layer parameters
 * in [SideToolbar].
 */
data class ToolbarLayerCallbacks(
    val layers: List<LayerInfo>,
    val hasSelection: Boolean,
    val onAddLayer: () -> Unit,
    val onDeleteLayer: (Int) -> Unit,
    val onMergeLayerDown: (Int) -> Unit,
    val onRenameLayer: (Int, String) -> Unit,
    val onMoveLayer: (Int, Int) -> Unit,
    val onActivateLayer: (Int) -> Unit,
    val onToggleLayerHidden: (Int, Boolean) -> Unit,
    val onMoveSelectionToLayer: (Int) -> Unit,
)

/**
 * Grouped page-management callbacks for the toolbar. Replaces the individual page parameters in
 * [SideToolbar].
 */
data class ToolbarPagesCallbacks(
    val pageCount: Int,
    val currentPage: Int,
    val onAddPage: () -> Unit,
    val onRemovePage: () -> Unit,
    val onGoToPage: (Int) -> Unit,
    val pageSize: Pair<Double, Double>?,
    val onPageSize: (Double, Double) -> Unit,
    /** The app's default page size (Settings ▸ Editor); backs the page-size dialog's **Default** tab. */
    val defaultPageSize: Pair<Double, Double>,
    val pageColumns: Int,
    val onPageColumns: (Int) -> Unit,
    val pagesEditMode: Boolean,
    val onPagesEditMode: (Boolean) -> Unit,
    val selectedPages: Int,
    val onDeleteSelectedPages: () -> Unit,
    val onClearPageSelection: () -> Unit,
    val copiedPages: Int,
    val onCopySelectedPages: () -> Unit,
    val onPastePages: () -> Unit,
    val onSaveAsDefault: (Double, Double) -> Unit,
)

/**
 * The rail's Favourites slot: the two ternas and the two things a tap or a long-press does. Grouped
 * like the other toolbar callbacks, so [SideToolbar] does not grow four more parameters.
 *
 * Only one terna is ever drawn — [favoriteToolFor] picks it from the tool in play — so both are
 * carried here while just one reaches the screen.
 */
data class ToolbarFavoritesCallbacks(
    /** The three pen colours behind the row, in slot order. */
    val penFavorites: List<Int>,
    /** The three highlighter colours behind the row, in slot order. */
    val highlighterFavorites: List<Int>,
    /** Tap a dot: switch to [colorOwner] in that colour. */
    val onPick: (colorOwner: EditorTool, color: Int) -> Unit,
    /** Long-press a dot and pick a colour: rewrite that slot of [colorOwner]'s terna. */
    val onAssign: (colorOwner: EditorTool, index: Int, color: Int) -> Unit,
)

/**
 * Grouped style callbacks for the toolbar. Replaces the 12 individual style parameters in
 * [SideToolbar].
 */
data class ToolbarStyleCallbacks(
    val color: Int,
    val onColor: (Int) -> Unit,
    val palette: ColorPaletteState,
    val onRedefineCustom: (Int) -> Unit,
    /** Append a colour to the pen palette from the pop-up's **add colour** swatch. */
    val onAddColor: (Int) -> Unit,
    val width: Float,
    val onWidth: (Float) -> Unit,
    val widthSlots: List<Float>,
    val onRedefineSlot: (Int, Float) -> Unit,
    val lineStyle: LineStyle,
    val onLineStyle: (LineStyle) -> Unit,
)
