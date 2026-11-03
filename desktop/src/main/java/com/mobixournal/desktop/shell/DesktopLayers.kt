package com.mobixournal.desktop.shell
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.format.model.Page
import com.mobixournal.render.ElementRef
import com.mobixournal.render.LayerOps
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionOps
import com.mobixournal.render.rectRegion

/** One row of the window's **Layers** menu: the layer's place in the page, its name and its two states. */
data class LayerRowInfo(
    val index: Int,
    val label: String,
    /** False when the reader switched the layer off in this window — the eye, not the document. */
    val visible: Boolean,
    /** True for the layer new ink lands on. */
    val active: Boolean,
)

/**
 * What the window's **Layers** door can do, in one value the header's document capsule hands its menu.
 *
 * The set is the tablet's own (`docs/drawing-tools.md`, *Layer management*): add, remove, rename, reorder,
 * merge down, move a selection between layers, pick the active layer and switch one off. The four edits go
 * through the shared, pure [LayerOps]; visibility and the active layer are **view state**, exactly as they
 * are on the tablet, so neither is ever written to the file.
 *
 * The commands act on the **active layer** rather than on a row's own buttons: the window has no touch
 * targets to spare in a 236 dp menu, and a menu of rows is the shape its other panels already wear. What
 * can be done is unchanged — the reader picks the layer, then acts on it.
 */
data class LayerMenuActions(
    val layers: List<LayerRowInfo> = emptyList(),
    /** The layer the commands act on: the picked one, resolved to the top layer when none is picked. */
    val activeIndex: Int = 0,
    val addLayer: () -> Unit = {},
    val activate: (Int) -> Unit = {},
    /** [toggleHidden] takes the layer and whether it is visible *now*. */
    val toggleHidden: (Int, Boolean) -> Unit = { _, _ -> },
    val rename: (Int) -> Unit = {},
    val mergeDown: () -> Unit = {},
    val moveUp: () -> Unit = {},
    val moveDown: () -> Unit = {},
    val delete: () -> Unit = {},
    val moveSelectionHere: () -> Unit = {},
    /** No page in view is no layer to manage. */
    val canManage: Boolean = false,
    /** The bottom layer has nothing below it, and one layer cannot be merged, moved or removed. */
    val canMergeDown: Boolean = false,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    val canDelete: Boolean = false,
    /** True when the page has a selection the **Move selection to this layer** row can carry. */
    val hasSelection: Boolean = false,
)

/**
 * The window's **layer manager**: the page's layers and every edit the tablet's rail offers.
 *
 * Every document edit records **one** undo step before it changes anything, so `Ctrl+Z` takes a merge, a
 * rename or a reorder back the way it takes a stroke back. The two view-state commands — picking the
 * active layer and switching one off — record nothing, because the file does not learn about them.
 *
 * The name being typed lives here too: a rename asks its question in the window's floating card
 * (`LayersPanel.kt`), and the draft has to survive the menu closing behind it.
 */
internal class DesktopLayers(
    private val tabs: DesktopTabs,
    private val edited: (DesktopTab, Int) -> Unit,
) {

    /** The layer whose name is being typed, or null while no rename is up. */
    var renaming by mutableStateOf<Int?>(null)
        private set

    /** What has been typed into the rename card so far. */
    var draft by mutableStateOf("")
        private set

    /** The page in view, or null when nothing is open — every command asks this first. */
    private fun page(): Page? {
        val tab = tabs.active ?: return null
        return runCatching { tab.document.page(tab.page) }.getOrNull()
    }

    /** One page edit, one undo step, one repaint — the shape every layer command has. */
    private fun editPage(change: (Page) -> Page): Boolean {
        val tab = tabs.active ?: return false
        val index = tab.page
        val page = runCatching { tab.document.page(index) }.getOrNull() ?: return false
        val next = change(page)
        if (next === page) return false
        val before = tab.document.snapshot()
        if (tab.document.editPage(index) { next } == null) return false
        tab.record(before)
        edited(tab, index)
        return true
    }

    /** The menu the header's Layers door opens, read from the tab in view. */
    fun actions(): LayerMenuActions {
        val tab = tabs.active
        val page = page()
        val layers = page?.layers.orEmpty()
        val active = if (tab != null && page != null) tab.inkLayer(page) else 0
        return LayerMenuActions(
            layers = layers.indices.map { index ->
                LayerRowInfo(
                    index = index,
                    label = page?.let { LayerOps.label(it, index) } ?: "",
                    visible = tab?.let { !it.isLayerHidden(it.page, index) } ?: true,
                    active = index == active,
                )
            },
            activeIndex = active,
            addLayer = { addLayer() },
            activate = { index -> tab?.pickActiveLayer(index) },
            toggleHidden = { index, _ -> tab?.let { it.toggleLayer(it.page, index) } },
            rename = { index -> beginRename(index) },
            mergeDown = { mergeLayerDown(active) },
            moveUp = { moveLayer(active, active + 1) },
            moveDown = { moveLayer(active, active - 1) },
            delete = { removeLayer(active) },
            moveSelectionHere = { moveSelectionToLayer(active) },
            canManage = page != null,
            canMergeDown = layers.size > 1 && active > 0,
            canMoveUp = active in 0 until layers.lastIndex,
            canMoveDown = active > 0,
            canDelete = layers.size > 1,
            hasSelection = tab?.selection?.isEmpty == false,
        )
    }

    /**
     * Add an empty layer above the stack — the tablet's **Add layer** — and make it the one new ink lands
     * on, which "no layer picked" already resolves to.
     */
    fun addLayer(): Boolean {
        val tab = tabs.active ?: return false
        if (!editPage { page -> LayerOps.add(page).first }) return false
        tab.pickActiveLayer(-1)
        return true
    }

    /** Drop layer [index]; a page keeps at least one layer, so the last one is refused. */
    fun removeLayer(index: Int): Boolean {
        val tab = tabs.active ?: return false
        if (!editPage { page -> LayerOps.remove(page, index) }) return false
        if (tab.activeLayer == index) tab.pickActiveLayer(-1)
        return true
    }

    /** Give layer [index] a name — blank clears it back to *Layer n*. */
    fun renameLayer(index: Int, name: String): Boolean =
        editPage { page -> LayerOps.rename(page, index, name) }

    /**
     * Fold layer [index] into the one below it: the upper layer's ink lands after the lower one's, the
     * lower keeps its name, and the emptied layer goes — one undo step.
     */
    fun mergeLayerDown(index: Int): Boolean {
        val tab = tabs.active ?: return false
        if (!editPage { page -> LayerOps.mergeDown(page, index) }) return false
        if (tab.activeLayer == index || tab.activeLayer == index - 1) tab.pickActiveLayer(index - 1)
        return true
    }

    /**
     * Move layer [from] to [to] — the z-order's two arrows.
     *
     * A picked layer follows its own ink: moving the active layer keeps it active at its new index, and
     * moving another layer past it shifts it by the one place that moved.
     */
    fun moveLayer(from: Int, to: Int): Boolean {
        val tab = tabs.active ?: return false
        val page = page() ?: return false
        if (from !in page.layers.indices || to !in page.layers.indices) return false
        if (!editPage { p -> LayerOps.move(p, from, to) }) return false
        val active = tab.activeLayer
        if (active in page.layers.indices) {
            tab.pickActiveLayer(
                when {
                    active == from -> to
                    from < active && to >= active -> active - 1
                    from > active && to <= active -> active + 1
                    else -> active
                },
            )
        }
        return true
    }

    /**
     * Move what the page has **selected** onto layer [index], and keep it selected there.
     *
     * The refs are rewritten by the move (`ElementRef` names a layer), so the handles follow the ink onto
     * its new layer instead of pointing at an index it has left.
     */
    fun moveSelectionToLayer(index: Int): Boolean {
        val tab = tabs.active ?: return false
        val selection = tab.selection ?: return false
        if (selection.isEmpty) return false
        var landed: Set<ElementRef> = emptySet()
        val moved = editPage { current ->
            val (next, refs) = LayerOps.moveElementsToLayer(current, selection.refs, index)
            landed = refs
            next
        }
        if (!moved || landed.isEmpty()) return false
        val box = SelectionOps.boundsOf(SelectionOps.elementsAt(tab.document.page(tab.page), landed))
            ?: return true
        tab.selection = PageSelection(rectRegion(box.left, box.top, box.right, box.bottom), landed)
        return true
    }

    /** Open the rename card on layer [index], its field already holding the name it has. */
    fun beginRename(index: Int) {
        val page = page() ?: return
        if (index !in page.layers.indices) return
        renaming = index
        draft = LayerOps.label(page, index)
    }

    /** Retype the name — the card's field, one keystroke at a time. */
    fun editDraft(text: String) {
        draft = text
    }

    /** Put the card away without touching the layer. */
    fun cancelRename() {
        renaming = null
    }

    /** Write the name and close the card; false when there was nothing to rename. */
    fun commitRename(): Boolean {
        val index = renaming ?: return false
        renaming = null
        return renameLayer(index, draft)
    }
}

/** The window's layer commands, as one value the header's document capsule hands its menu. */
internal val DesktopShellState.layerActions: LayerMenuActions
    get() = layersSection.actions()
