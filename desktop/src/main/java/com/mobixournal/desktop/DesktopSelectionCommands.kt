package com.mobixournal.desktop

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.isLivingEntity
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionEdit
import com.mobixournal.render.SelectionOps
import com.mobixournal.render.rectRegion

/**
 * The window's **selection commands**: what the bar under the outline offers, and the two keys a
 * selection answers.
 *
 * Kept out of the shell (which is about the window, not about what a marquee caught) and out of the bar
 * (which draws buttons, not documents): every function here takes the tab, commits through the tab's own
 * page window as **one undo step**, and reports whether it did anything — the state a control greys
 * itself out on. The arithmetic is mobix-core's [SelectionEdit] and [SelectionOps], the same code the
 * tablet's action bar calls, so the two shells cannot disagree about what a duplicate or a recolour does.
 *
 * `docs/interface-contract.md` §5 asks a control to be *unavailable*, not a silent no-op: every command
 * below answers false when the selection (or the clipboard) cannot support it, and the bar draws that.
 */

/**
 * The window's clipboard: the elements a Copy took, in page points.
 *
 * One per shell rather than per tab, because a copy in one document is meant to be pasted into another —
 * that is the whole point of a clipboard, and the elements are self-contained model values (`.mobix`
 * keeps a living plot as its recipe, so a pasted plot is still a plot). [bounds] is kept beside them so a
 * paste can offset from where they came from without re-measuring on every press.
 */
class DesktopClipboard {

    var elements: List<Element> = emptyList()
        private set

    var bounds: Bounds? = null
        private set

    val isEmpty: Boolean get() = elements.isEmpty()

    fun put(taken: List<Element>) {
        elements = taken
        bounds = SelectionOps.boundsOf(taken)
    }

    fun clear() {
        elements = emptyList()
        bounds = null
    }
}

/**
 * One command of the selection bar.
 *
 * Recolour and width take their value from the **strip's own ink** — the colour and width the pen would
 * write with, visible in the same window — rather than opening a second colour panel: the tablet's bar
 * does the same thing (it recolours to the pencil's colour and steps through the same widths), and the
 * strips are already where those two are chosen.
 */
enum class SelectionAction {
    CUT, COPY, PASTE, DUPLICATE, RECOLOUR, WIDTH, DELETE,
    ALIGN_LEFT, ALIGN_H_CENTRE, ALIGN_RIGHT, ALIGN_TOP, ALIGN_V_MIDDLE, ALIGN_BOTTOM,
    DISTRIBUTE_H, DISTRIBUTE_V,

    /** Open the selected living entity's editor — never an edit by itself, so the shell answers it. */
    EDIT,
}

/** How far a paste lands from the elements it copied, in page points — a second paste must not hide. */
const val PASTE_OFFSET_PT = 14.0

/** `Delete`: the caught elements go, as one edit and one undo step. */
internal fun deleteSelection(tab: DesktopTab, onEdited: (Int) -> Unit = {}): Boolean {
    val selection = tab.selection ?: return false
    if (selection.isEmpty) return false
    val index = tab.page
    val before = tab.document.snapshot()
    val emptied = SelectionEdit.delete(tab.document.page(index), selection)
    if (tab.document.editPage(index) { emptied } == null) return false
    tab.record(before)
    tab.selection = null
    onEdited(index)
    return true
}

/** `Esc` while something is selected: the outline goes before anything else the key might mean. */
internal fun dismissSelection(tab: DesktopTab?): Boolean {
    if (tab?.selection == null) return false
    tab.selection = null
    return true
}

/** What a copy takes: the caught elements, in the page's own order (cut and paste agree on it). */
internal fun copySelection(tab: DesktopTab, clipboard: DesktopClipboard): Boolean {
    val selection = tab.selection ?: return false
    if (selection.isEmpty) return false
    val taken = SelectionOps.elementsAt(tab.document.page(tab.page), selection.refs)
    if (taken.isEmpty()) return false
    clipboard.put(taken)
    return true
}

/** `Ctrl+X`: the copy, then the deletion — the clipboard keeps them, the page loses them. */
internal fun cutSelection(
    tab: DesktopTab,
    clipboard: DesktopClipboard,
    onEdited: (Int) -> Unit = {},
): Boolean {
    if (!copySelection(tab, clipboard)) return false
    return deleteSelection(tab, onEdited)
}

/**
 * `Ctrl+V`: the clipboard lands on the page, offset by [PASTE_OFFSET_PT] from where it came from and
 * **selected**, so the next gesture — a drag, a recolour — acts on what was just pasted.
 */
internal fun pasteClipboard(
    tab: DesktopTab,
    clipboard: DesktopClipboard,
    onEdited: (Int) -> Unit = {},
): Boolean {
    if (clipboard.isEmpty) return false
    val elements = clipboard.elements
    val origin = clipboard.bounds?.let { (it.left + PASTE_OFFSET_PT) to (it.top + PASTE_OFFSET_PT) }
    return appendSelected(tab, elements, origin, onEdited)
}

/** `Ctrl+D`: a copy of the selection lands beside it, and the copy is what stays selected. */
internal fun duplicateSelection(tab: DesktopTab, onEdited: (Int) -> Unit = {}): Boolean {
    val selection = tab.selection ?: return false
    if (selection.isEmpty) return false
    val elements = SelectionOps.elementsAt(tab.document.page(tab.page), selection.refs)
    if (elements.isEmpty()) return false
    val origin = selection.bounds.let { (it.left + PASTE_OFFSET_PT) to (it.top + PASTE_OFFSET_PT) }
    return appendSelected(tab, elements, origin, onEdited)
}

/**
 * Append [elements] to the page's top layer, translating them so their own box starts at [origin] (or
 * where they are, when the caller has no origin), and select what landed.
 */
private fun appendSelected(
    tab: DesktopTab,
    elements: List<Element>,
    origin: Pair<Double, Double>?,
    onEdited: (Int) -> Unit,
): Boolean {
    val index = tab.page
    val page = tab.document.page(index)
    val box = SelectionOps.boundsOf(elements)
    val dx = if (box == null || origin == null) 0.0 else origin.first - box.left
    val dy = if (box == null || origin == null) 0.0 else origin.second - box.top
    val moved = if (dx == 0.0 && dy == 0.0) elements else elements.map { SelectionOps.translate(it, dx, dy) }
    val (appended: Page, refs: Set<ElementRef>) = SelectionOps.addToTopLayerOn(page, moved)
    if (refs.isEmpty()) return false
    val before = tab.document.snapshot()
    if (tab.document.editPage(index) { appended } == null) return false
    tab.record(before)
    val box2 = SelectionOps.boundsOf(SelectionOps.elementsAt(appended, refs)) ?: return false
    tab.selection = PageSelection(rectRegion(box2.left, box2.top, box2.right, box2.bottom), refs)
    onEdited(index)
    return true
}

/** Recolour and/or re-width what is caught; a null leaves that half alone. */
internal fun restyleSelection(
    tab: DesktopTab,
    colour: Int? = null,
    widthPt: Double? = null,
    onEdited: (Int) -> Unit = {},
): Boolean {
    val selection = tab.selection ?: return false
    if (selection.isEmpty) return false
    val index = tab.page
    val current = tab.document.page(index)
    val restyled = SelectionOps.restyle(listOf(current), 0, selection.refs, colour, widthPt).firstOrNull()
        ?: return false
    if (restyled === current) return false
    val before = tab.document.snapshot()
    if (tab.document.editPage(index) { restyled } == null) return false
    tab.record(before)
    onEdited(index)
    return true
}

/** Align what is caught on the selection's own extent (two or more elements, or nothing happens). */
internal fun alignSelection(
    tab: DesktopTab,
    alignment: SelectionOps.SelectionAlign,
    onEdited: (Int) -> Unit = {},
): Boolean = editSelection(tab, onEdited) { pages, index, refs ->
    SelectionOps.align(pages, index, refs, alignment)
}

/** Space what is caught evenly between the two outermost elements (three or more). */
internal fun distributeSelection(
    tab: DesktopTab,
    horizontal: Boolean,
    onEdited: (Int) -> Unit = {},
): Boolean = editSelection(tab, onEdited) { pages, index, refs ->
    SelectionOps.distribute(pages, index, refs, horizontal)
}

/** Run a page-list edit over the selection, as one undo step, and report whether anything changed. */
private inline fun editSelection(
    tab: DesktopTab,
    onEdited: (Int) -> Unit,
    op: (List<Page>, Int, Set<ElementRef>) -> List<Page>,
): Boolean {
    val selection = tab.selection ?: return false
    if (selection.isEmpty) return false
    val index = tab.page
    val current = tab.document.page(index)
    val edited = op(listOf(current), 0, selection.refs).firstOrNull() ?: return false
    if (edited === current) return false
    val before = tab.document.snapshot()
    if (tab.document.editPage(index) { edited } == null) return false
    tab.record(before)
    onEdited(index)
    return true
}

/**
 * The element the bar's pencil would open: the selection must name **exactly one** element and the window
 * must have an editor for it — a table, a plot, a figure, a component, a math block, or a text box (whose
 * face, size and ink the lane edits). Ink has none, so the pencil is unavailable for it rather than opening
 * an empty panel.
 */
internal fun livingTargetOf(tab: DesktopTab): ElementRef? {
    val selection = tab.selection ?: return null
    if (selection.refs.size != 1) return null
    val ref = selection.refs.first()
    val element = tab.document.page(tab.page).layers.getOrNull(ref.layerIndex)
        ?.elements?.getOrNull(ref.elementIndex) ?: return null
    return if (hasInspector(element)) ref else null
}

/**
 * What a bar button does: the dispatcher the canvas and the keyboard share, so a `Ctrl+D` and a
 * duplicate button cannot mean two different things. `EDIT` is the shell's (it opens a panel), so this
 * answers false for it and the caller handles that one itself.
 */
internal fun performSelection(
    tab: DesktopTab,
    action: SelectionAction,
    ink: DesktopInk,
    clipboard: DesktopClipboard,
    onEdited: (Int) -> Unit = {},
): Boolean = when (action) {
    SelectionAction.COPY -> copySelection(tab, clipboard)
    SelectionAction.CUT -> cutSelection(tab, clipboard, onEdited)
    SelectionAction.PASTE -> pasteClipboard(tab, clipboard, onEdited)
    SelectionAction.DUPLICATE -> duplicateSelection(tab, onEdited)
    SelectionAction.RECOLOUR -> restyleSelection(tab, colour = ink.colour, onEdited = onEdited)
    SelectionAction.WIDTH -> restyleSelection(tab, widthPt = ink.width.toDouble(), onEdited = onEdited)
    SelectionAction.DELETE -> deleteSelection(tab, onEdited)
    SelectionAction.ALIGN_LEFT -> alignSelection(tab, SelectionOps.SelectionAlign.LEFT, onEdited)
    SelectionAction.ALIGN_H_CENTRE -> alignSelection(tab, SelectionOps.SelectionAlign.H_CENTRE, onEdited)
    SelectionAction.ALIGN_RIGHT -> alignSelection(tab, SelectionOps.SelectionAlign.RIGHT, onEdited)
    SelectionAction.ALIGN_TOP -> alignSelection(tab, SelectionOps.SelectionAlign.TOP, onEdited)
    SelectionAction.ALIGN_V_MIDDLE -> alignSelection(tab, SelectionOps.SelectionAlign.V_MIDDLE, onEdited)
    SelectionAction.ALIGN_BOTTOM -> alignSelection(tab, SelectionOps.SelectionAlign.BOTTOM, onEdited)
    SelectionAction.DISTRIBUTE_H -> distributeSelection(tab, horizontal = true, onEdited = onEdited)
    SelectionAction.DISTRIBUTE_V -> distributeSelection(tab, horizontal = false, onEdited = onEdited)
    SelectionAction.EDIT -> false
}
