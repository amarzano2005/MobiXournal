package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * **Shortcuts**: the window's three switch keys, then every tool and command with the key it holds.
 *
 * The table is [ShortcutTable]'s — the same rows the keyboard resolves through — so the page cannot list a
 * command the window does not have. Renaming a row is **two clicks**: click the row, then press the key. The
 * arm is [DesktopSettingsState.armedShortcut]'s, and the press is bound by `DesktopShellState.onKey`: the
 * window reads a press before any row can, so a page that kept the arm and the press to itself drew a
 * waiting row and never bound anything. While a row waits it says so in the accent, and `Esc` puts it away
 * without touching the table.
 *
 * Every group is a **heading over rows**; nothing repeats the heading as a row of its own.
 */
@Composable
internal fun ShortcutsSettings(policy: DesktopSettingsState) {
    Column(Modifier.fillMaxWidth()) {
        policy.shortcutConflict?.let { lost ->
            SettingsGroup("Binding") {
                SettingsRow(
                    label = "Conflict",
                    note = "One key runs one command: the row that held it lost it.",
                ) {
                    Text(
                        "${lost.label} lost its key.",
                        color = DesktopTheme.danger,
                        style = DesktopTheme.label,
                    )
                }
            }
        }

        ToggleGroup(policy)
        ShortcutGroup(
            title = "Tools",
            entries = ShortcutTable.entries.filter { it.tool != null },
            policy = policy,
        )
        ShortcutGroup(
            title = "Commands",
            entries = ShortcutTable.entries.filter { it.command != null && it.command !in TOGGLE_COMMANDS },
            policy = policy,
        )

        SettingsGroup("Factory settings") {
            // A command row, like the other sections' way back to the factory values: one label, one
            // shape, wherever the window offers a reset (`Reset to defaults`).
            SettingsCommand(
                label = "Reset to defaults",
                note = "Every row back to the key it ships with.",
                onClick = {
                    policy.cancelArmed()
                    policy.restoreShortcuts()
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * **Toggle**: the three switches a stylus button presses, before the tables that hold every other key.
 *
 * A toggle is a command, not a tool, so it lives in [ShortcutTable] beside the rest of the key table; what
 * this group adds is the **variant**. The eraser and the selection are families of two on the page
 * (`ToolToggle`), and which member a press picks is a choice — the cascade under each row names it, and the
 * choice is stored per family, so the toggle has one answer rather than a state to guess.
 */
@Composable
private fun ToggleGroup(policy: DesktopSettingsState) {
    SettingsGroup("Toggle") {
        for (entry in ShortcutTable.toggleEntries) {
            ShortcutRow(entry, policy)
            when (entry.command) {
                ShellCommand.TOGGLE_ERASER -> SettingsSelect(
                    label = "Eraser",
                    note = "Which eraser a toggle press picks.",
                    options = ERASER_TOGGLES,
                    picked = policy.toggleEraserTool,
                    optionLabel = { if (it == DesktopTool.ERASER_WHOLE) "Whole stroke" else "Partial" },
                    onPick = policy::chooseToggleEraser,
                )
                ShellCommand.TOGGLE_SELECTION -> SettingsSelect(
                    label = "Selection",
                    note = "Which selection a toggle press picks.",
                    options = SELECTION_TOGGLES,
                    picked = policy.toggleSelectionTool,
                    optionLabel = { if (it == DesktopTool.LASSO) "Lasso" else "Marquee" },
                    onPick = policy::chooseToggleSelection,
                )
                else -> Unit
            }
        }
    }
}

/** The two erasers a toggle can pick, in the strip's own order. */
private val ERASER_TOGGLES = listOf(DesktopTool.ERASER, DesktopTool.ERASER_WHOLE)

/** The two selections a toggle can pick. */
private val SELECTION_TOGGLES = listOf(DesktopTool.MARQUEE, DesktopTool.LASSO)

/** One group of rows — a tool's or a command's — under its own heading, with no second copy of the name. */
@Composable
private fun ShortcutGroup(title: String, entries: List<ShortcutEntry>, policy: DesktopSettingsState) {
    SettingsGroup(title) {
        for (entry in entries) ShortcutRow(entry, policy)
    }
}

/** One command's row: its name, and the key it holds — click it, then press the key it should answer to. */
@Composable
private fun ShortcutRow(entry: ShortcutEntry, policy: DesktopSettingsState) {
    val armed = policy.armedShortcut == entry.id
    SettingsRow(label = entry.label, onClick = { policy.armShortcut(entry.id) }) {
        if (armed) {
            Text("Press a key…", color = DesktopTheme.accent, style = DesktopTheme.label)
        } else {
            val key = ShortcutTable.key(policy.shortcutTable, entry.id)
            if (key.isEmpty()) {
                Text("None", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            } else {
                ShortcutKeyChip(key)
            }
        }
    }
}

/** A key, drawn as the capsule the strips use for a slot's own words. */
@Composable
private fun ShortcutKeyChip(key: String) {
    Box(
        Modifier
            .background(DesktopTheme.activeSlot, DesktopTheme.capsuleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(key, color = DesktopTheme.ink, style = DesktopTheme.numeric)
    }
}
