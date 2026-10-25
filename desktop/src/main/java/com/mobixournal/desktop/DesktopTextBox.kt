package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.TextFieldValue
import com.mobixournal.format.model.TextElement
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.PageSelection
import com.mobixournal.render.rectRegion

/**
 * The window's **Text tool**, as a piece of the shell: the box being typed, and the one moment it becomes
 * an element.
 *
 * It is separate from [DesktopShellState] because it is a lifetime of its own — a draft that opens on a
 * press, follows the keys, and ends on `Esc`, a click elsewhere, or a tool change — and because the shell
 * file is at its size budget (`SourceStructureTest`). The shell holds one of these and passes its
 * [box] to the canvas, which draws it; the document is untouched until [commit].
 *
 * The rules that matter, and why:
 *  - **nothing is written while typing.** A box that is abandoned leaves no element and no undo step,
 *    which is what the tablet's text commit does too.
 *  - **a blank box commits nothing.** Pressing with the Text slot and changing your mind must not add an
 *    empty `<text>` to the file or a step to the history.
 *  - **one commit is one element and one undo step**, appended to the page's top layer through
 *    [placeLivingElement] and left selected, like every other placement.
 *  - **a box opened on existing words replaces them where they are.** The element keeps its layer, its
 *    index in it, its id, its font, its size and its colour; the retype is one edit and one undo step.
 *    Emptying it deletes the box (the tablet's rule), and closing it untouched records nothing.
 */
class DesktopTextBox {

    /** The box being typed, or null while the Text tool has no field up. */
    var box: TextBoxDraft? by mutableStateOf(null)
        private set

    /**
     * Open a box at ([x], [y]) on [tab]'s page — committing any box that was already up first, because a
     * press somewhere else is the previous box's `Esc`.
     */
    fun begin(
        tab: DesktopTab,
        x: Double,
        y: Double,
        pageW: Double,
        pageH: Double,
        ink: DesktopInk,
        onEdited: (DesktopTab, Int) -> Unit,
    ) {
        commit(tab, onEdited)
        box = textBoxAt(tab, x, y, pageW, pageH, ink)
    }

    /**
     * Take the field's answer — its text **and** its caret — into the draft. The page is still untouched.
     * The caret has to travel with the text: a field rebuilt from the string alone would put it back at
     * the end on every keystroke, which is what made correcting a word in the middle impossible.
     */
    fun change(value: TextFieldValue) {
        box = box?.withField(value)
    }

    /**
     * Take the lane's answer for the box's **face, size or ink**. The page is still untouched: a restyle
     * while typing changes what the commit will write, not what the document holds.
     */
    fun restyle(edited: TextElement) {
        box = box?.withStyle(edited)
    }

    /**
     * Write the box as **one** element and one undo step, then close it — or drop it when it is blank and
     * new, or when the page it was opened on is no longer the page being shown.
     *
     * [onEdited] is the shell's own after-edit hook (the thumbnail goes stale, the quiet period starts), so
     * a committed box is indistinguishable from a stroke in everything that follows one.
     */
    fun commit(tab: DesktopTab?, onEdited: (DesktopTab, Int) -> Unit) {
        val draft = box ?: return
        box = null
        if (tab == null || draft.tabKey != tab.key || draft.page != tab.page) return
        val existing = draft.editing
        if (existing == null) {
            if (draft.blank) return
            placeLivingElement(tab, draft.element()) { index -> onEdited(tab, index) }
            return
        }
        val fresh = if (draft.blank) null else draft.element()
        // A box closed exactly as it was opened is not an edit: no element, no undo step.
        if (fresh == existing) return
        rewrite(tab, existing, fresh, onEdited)
    }

    /**
     * Replace (or, with [fresh] null, remove) one text box in the place it held, as one edit and one undo
     * step, and leave the box selected the way a placement is.
     */
    private fun rewrite(
        tab: DesktopTab,
        existing: TextElement,
        fresh: TextElement?,
        onEdited: (DesktopTab, Int) -> Unit,
    ) {
        val index = tab.page
        val page = runCatching { tab.document.page(index) }.getOrNull() ?: return
        val rewritten = page.replaceTextBox(existing, fresh) ?: return
        val before = tab.document.snapshot()
        if (tab.document.editPage(index) { rewritten.page } == null) return
        tab.record(before)
        tab.selection = rewritten.ref?.let { ref ->
            val element = fresh ?: return@let null
            val bounds = ElementBounds.of(element)
            PageSelection(rectRegion(bounds.left, bounds.top, bounds.right, bounds.bottom), setOf(ref))
        }
        onEdited(tab, index)
    }

    /** Close the box without writing it — the page is exactly what it was. */
    fun drop() {
        box = null
    }

    /**
     * The key a live box hands the shell: `Esc` is the commit, and every other key belongs to the field
     * (a letter is a letter, `Space` is a space, `Delete` deletes a character). Null when no box is up, so
     * the rest of the keyboard is the shell's again.
     */
    fun command(key: Key): TextBoxKey? = if (box == null) null else textBoxCommand(key)
}
