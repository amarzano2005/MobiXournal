package com.mobixournal.desktop.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.mobixournal.desktop.ink.PenBarrel
import com.mobixournal.desktop.sheet.DesktopHud
import com.mobixournal.desktop.sheet.DesktopHudState
import com.mobixournal.desktop.sheet.RingContents
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.render.HudSlot

/**
 * The window's **open ring**: where it is, the slot under the pointer, and the one path a slot commits
 * through.
 *
 * It is a piece of the shell's state rather than a second one — the same shape `DesktopInkState` has — so
 * `Esc`, the strips and the canvas all read *one* answer about whether the ring is up, and the commits are
 * the entries the strips already use ([DesktopInkState.pickColour] / `pickWidth` and `selectTool`): a ring
 * choice can never mean something the user could not have set by hand.
 *
 * Its geometry and its contents are [DesktopHud]'s and `RadialHud`'s; this class only holds the state and
 * the four things a pointer can do to it — open, move, press, dismiss. That is what makes the whole ring a
 * JVM test (`DesktopRingTest`) with no window and no scene.
 *
 * The palette is read through lambdas because it is the ink's own state, restored from the settings file and
 * edited by the colour panel: the ring must show what the flyout shows, at the moment it opens.
 */
internal class DesktopRing(
    private val palette: () -> List<Int>,
    private val custom: () -> Int?,
    private val onColour: (Int) -> Unit,
    private val onWidth: (Float) -> Unit,
    private val onTool: (DesktopTool) -> Unit,
    /**
     * What the ring carries, from Settings → Ring (`RingContents`).
     *
     * A lambda, like the palette below: the page's switches are read at the moment a ring opens, so a
     * ring summoned straight after a switch carries the new parts without the window being restarted.
     */
    private val contents: () -> RingContents = { RingContents.FACTORY },
) : PenBarrel {

    /** The open ring, or null while nothing is on screen — nothing exists until it is summoned. */
    var open by mutableStateOf<DesktopHudState?>(null)
        private set

    /**
     * Where the nib last was, in the canvas's pixels — the cell the ring opens in.
     *
     * Reported by the canvas ([com.mobixournal.desktop.sheet.PageCanvas]'s `onPointerAt`), from the pen's
     * own readings as well as the mouse's: a pen that hovers reports where it is before any button is
     * pressed, which is what lets a held button open the ring under the nib rather than somewhere else.
     */
    var anchor by mutableStateOf<Offset?>(null)
        private set

    /** The ring's contents, rebuilt from the palette as it is now and the parts Settings leaves on. */
    val slots: List<HudSlot> get() = contents().slots(palette(), custom())

    /**
     * The pointer's newest reading. Moves the pick while the ring is up, and remembers where the nib is so
     * the next summon opens there.
     */
    fun notePointer(at: Offset, density: Float) {
        anchor = at
        this.density = density
        track(at.x, at.y, density)
    }

    /**
     * The canvas's density, remembered from the nib's last reading.
     *
     * The pen's packets are in pixels and the ring's geometry in dp, and a barrel press always follows a
     * hover — which is the reading this is remembered from — so the packets never have to carry it.
     */
    private var density = 1f

    /**
     * Open the ring at the nib — or close it, when it is already open (the keyboard's second press).
     *
     * [picker] is how it will close: a ring a **key** opened has no held button to release, so a press
     * commits a slot ([press]); a ring the **pen's button** opened is held, and its release commits
     * ([release]). False when there is nothing to offer — a ring with no slots is not drawn at all.
     */
    fun summon(picker: Boolean = true): Boolean {
        if (open != null) {
            open = null
            return true
        }
        if (slots.isEmpty()) return false
        val at = anchor ?: Offset.Zero
        open = DesktopHud.openAt(at.x, at.y, picker = picker)
        return true
    }

    /** Put the ring away without committing anything — `Esc`, a click outside it, the pen leaving. */
    fun dismiss(): Boolean {
        if (open == null) return false
        open = null
        return true
    }

    /** Light the slot under ([x], [y]) — the pick a flick would commit. */
    fun track(x: Float, y: Float, density: Float) {
        val state = open ?: return
        val contents = slots
        if (contents.isEmpty()) return
        val next = DesktopHud.tracked(state, x, y, contents.size, density)
        if (next != state) open = next
    }

    /**
     * A **press** on the page while the ring is up. True when the ring took it, in which case the canvas
     * writes nothing: a slot under it commits, a press inside the ring but off a slot does nothing (the
     * press was aimed at the ring, not through it), and one well outside it closes the ring.
     */
    fun press(x: Float, y: Float, density: Float): Boolean {
        val state = open ?: return false
        val contents = slots
        val index = DesktopHud.indexAt(state, x, y, contents.size, density)
        if (index != null) {
            open = null
            commit(contents[index])
            return true
        }
        if (!DesktopHud.within(state, x, y, density)) open = null
        return true
    }

    /**
     * The pen's button, which the window reads as the ring's when `penButton` says so: a press opens the ring
     * at the nib, and the release commits — the tablet's own summon.
     */
    override fun down(at: Offset): Boolean {
        // A second press of a button that has no held state to speak of is a dismissal, never a commit.
        if (open != null) {
            open = null
            return false
        }
        if (slots.isEmpty()) return false
        open = DesktopHud.openAt(at.x, at.y, picker = false)
        return true
    }

    /** The pick following the pen while the button is held. */
    override fun move(at: Offset) = track(at.x, at.y, density)

    /** The release commits the slot under the pen, exactly as a held ring's release always has. */
    override fun up(at: Offset) {
        release(at.x, at.y, density)
    }

    /** The ring put away **without** committing: a tip that landed, or a pen that left the tablet. */
    override fun cancel() {
        open = null
    }

    /**
     * The **release** of a held ring (the pen's button): the slot under the pen runs, and a release into the
     * dead zone or off the band commits nothing. A picker ignores this — the release of the click that
     * opened it is that click's own release, which is exactly what made the tablet's ring flash open and
     * shut before a slot could be reached.
     */
    fun release(x: Float, y: Float, density: Float): Boolean {
        val state = open ?: return false
        if (state.picker) return true
        val contents = slots
        val index = DesktopHud.indexAt(state, x, y, contents.size, density)
        open = null
        if (index != null) commit(contents[index])
        return true
    }

    /** The one commit path: a colour, a width or a tool, through the entries the strips and flyouts use. */
    fun commit(slot: HudSlot) {
        when (slot) {
            is HudSlot.Colour -> onColour(slot.argb)
            is HudSlot.Width -> onWidth(slot.pt)
            is HudSlot.ToolFace -> DesktopHud.slotOf(slot.tool)?.let(onTool)
        }
    }
}
