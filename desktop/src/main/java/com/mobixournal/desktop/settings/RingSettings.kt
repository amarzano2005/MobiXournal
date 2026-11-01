package com.mobixournal.desktop.settings

import com.mobixournal.desktop.sheet.RingContents
import com.mobixournal.desktop.shell.ShellCommand

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * **Ring**: what the pen-tip ring holds, and the key that opens it.
 *
 * The page is the ring's own, beside *Colors* rather than inside *Shortcuts*, because the ring is a
 * writing surface and not another list of keys: its contents are chosen here, while its key stays one row
 * of the table the Shortcuts page owns ([ShortcutTable]) and is armed from here through the same
 * `armedShortcut` the window binds.
 *
 * **The pen button a driver turns into a key** is why the key row is on this page: a tablet whose buttons
 * arrive as keyboard keys — Windows maps them in the vendor's software, not as a barrel bit — still opens
 * the ring, because all it has to send is the key this row holds. The barrel itself remains *Stylus → Pen
 * button*'s row, for the pens that really do send one.
 *
 * Every row is the window's own grammar (`docs/desktop-settings.md`): a toggle ends in the switch, a
 * command has nothing at its end, and a shortcut ends in the capsule with the key it holds.
 */
@Composable
internal fun RingSettings(policy: DesktopSettingsState) {
    val contents = policy.ring.contents
    SettingsGroup("On the ring") {
        SettingsRow(
            label = "Swatches",
            note = "The palette's colours, with the custom slot last.",
            onClick = { policy.ring.chooseSwatches(!contents.swatches) },
        ) {
            SettingsSwitch(contents.swatches)
        }
        SettingsRow(
            label = "Widths",
            note = "The pen's three widths, as the strip's own.",
            onClick = { policy.ring.chooseWidths(!contents.widths) },
        ) {
            SettingsSwitch(contents.widths)
        }
        for (tool in RingContents.TOOL_FACES) {
            SettingsRow(
                label = tool.label,
                note = ringToolNote(tool),
                onClick = { policy.ring.chooseTool(tool, tool !in contents.tools) },
            ) {
                SettingsSwitch(tool in contents.tools)
            }
        }
    }
    RingSummonGroup(policy)
    SettingsGroup("Factory settings") {
        SettingsCommand(
            label = "Reset to defaults",
            note = "Every part back on the ring.",
            onClick = policy.ring::reset,
        )
    }
    SettingsNote("The ring opens only when it has something to offer; a second press puts it away.")
}

/** The sentence under one tool's row — what that face puts in hand. */
private fun ringToolNote(tool: com.mobixournal.desktop.tools.DesktopTool): String = when (tool) {
    com.mobixournal.desktop.tools.DesktopTool.PEN -> "The pen, with the colour it holds."
    com.mobixournal.desktop.tools.DesktopTool.HIGHLIGHTER -> "The marker, with its own colour."
    else -> "The eraser in use."
}

/**
 * **Opening it**: the key that summons the ring, armed on the spot, and the pen's own door.
 *
 * The key row is the Shortcuts page's row, reached from here: one table, one binding, two places it is
 * read from — a second field for the same answer is how two surfaces come to disagree about it.
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
        SettingsRow(
            label = "Side button",
            note = "A pen that sends a barrel bit: erase, or the ring, on the Stylus page.",
            onClick = { policy.show(DesktopSettingSection.STYLUS) },
        ) {
            Text(policy.penButton.label, color = DesktopTheme.ink, style = DesktopTheme.label)
        }
    }
}
