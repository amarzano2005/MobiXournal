package com.mobixournal.desktop.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.mobixournal.desktop.sheet.DesktopHud
import com.mobixournal.desktop.sheet.DesktopHudState
import com.mobixournal.desktop.sheet.RingContents
import com.mobixournal.desktop.sheet.RingFace
import com.mobixournal.desktop.tools.DesktopTool

/**
 * The window's **open ring**: where it is, the face under the pointer, and the one path a face commits
 * through.
 *
 * It is a piece of the shell's state rather than a second one — the same shape `DesktopInkState` has — so
 * `Esc`, the strips and the canvas all read *one* answer about whether the ring is up, and the commits are
 * the entries the strips already use ([DesktopInkState.pickColour] / `pickWidth` and `selectTool`): a ring
 * choice can never mean something the user could not have set by hand.
 *
 * Its geometry is [DesktopHud]'s and `RadialHud`'s, its faces are [RingContents]'s (Settings → Ring), and
 * this class holds only the state and the three things a pointer can do to it — open, move, press. That is
 * what makes the whole ring a JVM test (`DesktopRingTest`) with no window and no scene.
 *
 * There is no held mode: the ring is opened by a **key** (`Alt + B`, or whatever the Shortcuts table holds),
 * and a pen button a driver turns into a keyboard key presses that same key. So a press on a face is the
 * commit, and the release of the press that opened the ring never is.
 *
 * The palette is read through lambdas because it is the ink's own state, restored from the settings file and
 * edited by the colour panel: an automatic ring must show what the flyout shows, at the moment it opens.
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
     * A lambda, like the palette below: the page's choices are read at the moment a ring opens, so a ring
     * summoned straight after a change carries the new faces without the window being restarted.
     */
    private val contents: () -> RingContents = { RingContents.Auto },
) {

    /** The open ring, or null while nothing is on screen — nothing exists until it is summoned. */
    var open by mutableStateOf<DesktopHudState?>(null)
        private set

    /**
     * Where the nib last was, in the canvas's pixels — the cell the ring opens in.
     *
     * Reported by the canvas ([com.mobixournal.desktop.sheet.PageCanvas]'s `onPointerAt`), from the pen's
     * own readings as well as the mouse's: a pen that hovers reports where it is before any button is
     * pressed, which is what lets the summon open the ring under the nib rather than somewhere else.
     */
    var anchor by mutableStateOf<Offset?>(null)
        private set

    /** The faces this ring draws, from the palette as it is now and the choices Settings stored. */
    val slots: List<RingFace> get() = contents().slots(palette(), custom())

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
     * The pen's packets are in pixels and the ring's geometry in dp, and a summon always follows a reading of
     * where the nib is — which is the reading this is remembered from — so the packets never carry it.
     */
    private var density = 1f

    /** Open the ring at the nib — or close it, when it is already open (the key's second press). */
    fun summon(): Boolean {
        if (open != null) {
            open = null
            return true
        }
        if (slots.isEmpty()) return false
        val at = anchor ?: Offset.Zero
        open = DesktopHud.openAt(at.x, at.y)
        return true
    }

    /** Put the ring away without committing anything — `Esc`, a press outside it. */
    fun dismiss(): Boolean {
        if (open == null) return false
        open = null
        return true
    }

    /** Light the face under ([x], [y]) — the pick a movement would commit. */
    fun track(x: Float, y: Float, density: Float) {
        val state = open ?: return
        val faces = slots
        if (faces.isEmpty()) return
        val next = DesktopHud.tracked(state, x, y, faces.size, density)
        if (next != state) open = next
    }

    /**
     * A **press** on the page while the ring is up. True when the ring took it, in which case the canvas
     * writes nothing: a face under it commits, a press inside the ring but off a face does nothing (the
     * press was aimed at the ring, not through it), and one well outside it closes the ring.
     */
    fun press(x: Float, y: Float, density: Float): Boolean {
        val state = open ?: return false
        val faces = slots
        val index = DesktopHud.indexAt(state, x, y, faces.size, density)
        if (index != null) {
            open = null
            commit(faces[index])
            return true
        }
        if (!DesktopHud.within(state, x, y, density)) open = null
        return true
    }

    /** The one commit path: a colour, a width or any tool, through the entries the strips already use. */
    fun commit(face: RingFace) {
        when (face) {
            is RingFace.Colour -> onColour(face.argb)
            is RingFace.Width -> onWidth(face.pt)
            is RingFace.Tool -> onTool(face.tool)
        }
    }
}
