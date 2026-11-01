package com.mobixournal.desktop.settings

import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.Tool
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.Locale

/**
 * **Ring**: what each position of the pen-tip ring holds, and the key that opens it.
 *
 * A **position**, not a family: the ring is a circle of up to [RadialHud.MAX_SLOTS] faces and the reader
 * decides face by face — a colour, a width or a writing tool, or the position removed. That is why the page
 * is a list of rows where each row's control is a [SettingsSelect] over the same answers, and why the first
 * edit pins the ring: an automatic ring follows the palette, and one the reader arranged is theirs.
 *
 * There is no *side button* row: on a PC a pen's buttons arrive as keyboard keys (the vendor's software maps
 * them), so the button reaches the ring by pressing the **key** — which is the row above. A pen that sends a
 * real barrel bit nowadays gets the same treatment: the bit is read and reported by the tablet probe
 * ([`desktop-tablet.md`](../../../../../docs/desktop-tablet.md)), and no setting here hands it a job.
 *
 * Every row is the window's own grammar (`docs/desktop-settings.md`): a choice ends in a select, a command
 * has nothing at its end, and the key ends in the capsule with the key it holds.
 */
@Composable
internal fun RingSettings(policy: DesktopSettingsState) {
    val ring = policy.ring
    val slots = ring.slots
    val candidates = ring.candidates()
    SettingsGroup("Positions") {
        if (slots.isEmpty()) {
            SettingsRow(
                label = "Nothing on the ring",
                note = "Add a position below, or reset to the automatic ring.",
            ) {
                Text("Empty", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            }
        }
        for ((index, face) in slots.withIndex()) {
            SettingsSelect(
                label = "Position ${index + 1}",
                note = "Clockwise from the top: what the ring holds there.",
                options = candidates,
                picked = face,
                optionLabel = ::faceLabel,
                onPick = { choice -> ring.chooseFace(index, choice) },
            )
        }
        SettingsCommand(
            label = "Add a position",
            note = "The first face the ring does not carry yet, up to ${RadialHud.MAX_SLOTS}.",
            onClick = ring::addPosition,
        )
        SettingsCommand(
            label = "Reset to defaults",
            note = "The palette, the three widths and the three tools, following the palette again.",
            onClick = ring::reset,
        )
    }
    RingSummonGroup(policy)
    SettingsNote("A position answered with Empty leaves the ring; an empty ring does not open at all.")
}

/**
 * The face a select shows, in the words the rest of the window uses: the tool's own name, the width in
 * points, the colour's factory name with its hex — because a swatch's identity on a list is its number, not
 * its picture.
 */
private fun faceLabel(face: HudSlot?): String = when (face) {
    null -> "Empty"
    is HudSlot.Colour -> faceLabel(face.argb)
    is HudSlot.Width -> String.format(Locale.ROOT, "%.2f pt", face.pt)
    is HudSlot.ToolFace -> when (face.tool) {
        Tool.PEN -> "Pen"
        Tool.HIGHLIGHTER -> "Highlighter"
        Tool.ERASER -> "Eraser"
    }
}

/** The factory palette's own names, so a list of colours reads as colours and not as eight hex codes. */
private val SWATCH_NAMES: Map<Int, String> = mapOf(
    PenPalette.BLACK to "Black",
    PenPalette.RED to "Red",
    PenPalette.GREEN to "Green",
    PenPalette.BLUE to "Blue",
    PenPalette.ORANGE to "Orange",
    PenPalette.YELLOW to "Yellow",
    PenPalette.MAGENTA to "Magenta",
    PenPalette.WHITE to "White",
)

/** A swatch: its name when it is one of the factory eight, then its hex. */
private fun faceLabel(argb: Int): String {
    val hex = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    val name = SWATCH_NAMES[argb]
    return if (name == null) hex else "$name · $hex"
}

/**
 * **Opening it**: the key that summons the ring, armed on the spot.
 *
 * The row is the Shortcuts page's own (`ShellCommand.RADIAL_HUD`, one table, one binding), reached from here
 * because this is the page a reader comes to when the ring will not open. A pen button that a driver turns
 * into a keyboard key presses this key, which is how a PC pen reaches the ring at all.
 */
@Composable
private fun RingSummonGroup(policy: DesktopSettingsState) {
    val id = ShellCommand.RADIAL_HUD.name
    val armed = policy.armedShortcut == id
    val key = ShortcutTable.key(policy.shortcutTable, id)
    SettingsGroup("Opening it") {
        SettingsRow(
            label = "Key",
            note = "Click, then press a key — a pen button a driver turns into one works here too.",
            onClick = { policy.armShortcut(id) },
        ) {
            when {
                armed -> Text("Press a key…", color = DesktopTheme.accent, style = DesktopTheme.label)
                key.isEmpty() -> Text("None", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                else -> ShortcutKeyChip(key)
            }
        }
    }
}
