package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.PillTextInset
import com.mobixournal.desktop.ink.of

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.TOGGLE_COMMANDS

/**
 * **Shortcuts**: a search row, the three switch keys, then every tool and command with the key it holds.
 *
 * The table is [ShortcutTable]'s — the same rows the keyboard resolves through — so the page cannot list a
 * command the window does not have. What this page adds to it is **reading order**: [ShortcutFamilies]
 * sorts the flat table into the families the strips already use (ink, selection and text, shapes, STEM,
 * guides, document, editing, view, the pen), so *Undo* no longer stands between *Show the pages* and *Logic
 * gates*, and a **search** row narrows all of them at once — by name, by the chord in hand, or by the tool a
 * key picks. Sixty rows in one column is a list sorted by the day each entry was written; nine short lists
 * and a search field is a table.
 *
 * Renaming is still **two clicks**: click the row, then press the key. The arm is
 * [DesktopSettingsState.armedShortcut]'s, and the press is bound by `DesktopShellState.onKey`: the window
 * reads a press before any row can. While a row waits it says so in the accent, and `Esc` puts it away
 * without touching the table.
 *
 * The eraser and the selection are **families of two**, and which member a toggle press picks is answered
 * **in the toggle's own row** — the choice capsule sits beside the key it belongs to, so the answer to
 * "which eraser?" is on the line that asks it rather than in a cascade two rows below.
 */
@Composable
internal fun ShortcutsSettings(policy: DesktopSettingsState) {
    val query = policy.query
    val families = remember { ShortcutFamilies.of(ShortcutTable.entries) }
    val shown = families
        .map { family -> family.copy(entries = family.entries.filter { matches(policy, it, query) }) }
        .filter { it.entries.isNotEmpty() }
    val found = shown.sumOf { it.entries.size }

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

        if (query.isNotBlank()) {
            SettingsGroup("Filter") {
                SettingsReadout(
                    label = "Search filter",
                    value = "$found of ${ShortcutTable.entries.size} matching \"${query.trim()}\"",
                )
            }
        }

        for (family in shown) {
            SettingsGroup(family.title) {
                for (entry in family.entries) {
                    // The switches carry their member's choice in the row itself; every other row is a key.
                    if (entry.command in TOGGLE_COMMANDS) {
                        ShortcutRow(entry, policy, variant = { ToggleVariant(entry, policy) })
                    } else {
                        ShortcutRow(entry, policy)
                    }
                }
            }
        }
        if (found == 0) {
            SettingsNote("No key matches “${query.trim()}”.")
        }

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

/** [entry] as the page should show it for [query] — the live binding, so a remapped chord is searchable. */
private fun matches(policy: DesktopSettingsState, entry: ShortcutEntry, query: String): Boolean =
    ShortcutFamilies.matches(entry, query, ShortcutTable.key(policy.shortcutTable, entry.id))

/**
 * **Which member a toggle press picks**, on the toggle's own row: *Partial / Whole stroke* for the eraser,
 * *Lasso / Marquee* for the selection.
 *
 * The hand has no siblings, so its row draws no capsule at all. The answer is stored per family
 * (`interface.toggleEraser`, `interface.toggleSelection`), which is what lets the row print the choice
 * instead of a state to guess.
 */
@Composable
private fun ToggleVariant(entry: ShortcutEntry, policy: DesktopSettingsState) {
    when (entry.command) {
        ShellCommand.TOGGLE_ERASER -> SettingsChoiceChip(
            options = ERASER_TOGGLES,
            picked = policy.toggleEraserTool,
            optionLabel = { if (it == DesktopTool.ERASER_WHOLE) "Whole stroke" else "Partial" },
            onPick = policy::chooseToggleEraser,
        )
        ShellCommand.TOGGLE_SELECTION -> SettingsChoiceChip(
            options = SELECTION_TOGGLES,
            picked = policy.toggleSelectionTool,
            optionLabel = { if (it == DesktopTool.LASSO) "Lasso" else "Marquee" },
            onPick = policy::chooseToggleSelection,
        )
        else -> Unit
    }
}

/** The two erasers a toggle can pick, in the strip's own order. */
private val ERASER_TOGGLES = listOf(DesktopTool.ERASER, DesktopTool.ERASER_WHOLE)

/** The two selections a toggle can pick. */
private val SELECTION_TOGGLES = listOf(DesktopTool.MARQUEE, DesktopTool.LASSO)

/**
 * One key's row: its name, its family's own variant when it has one, and the key it holds — click it, then
 * press the key it should answer to.
 *
 * The variant is a control of its own, so a click on the capsule opens its list and **does not arm** the
 * row: the tap is the capsule's, and the row's own handler never sees it.
 */
@Composable
private fun ShortcutRow(
    entry: ShortcutEntry,
    policy: DesktopSettingsState,
    variant: (@Composable () -> Unit)? = null,
) {
    val armed = policy.armedShortcut == entry.id
    SettingsRow(label = entry.label, onClick = { policy.armShortcut(entry.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (variant != null) {
                variant()
                Spacer(Modifier.width(8.dp))
            }
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
}

/** A key, drawn as the capsule the strips use for a slot's own words — the Ring page's row too. */
@Composable
internal fun ShortcutKeyChip(key: String) {
    Box(
        Modifier
            .background(DesktopTheme.activeSlot, DesktopTheme.capsuleShape)
            .padding(horizontal = PillTextInset, vertical = 4.dp),
    ) {
        Text(key, color = DesktopTheme.ink, style = DesktopTheme.numeric)
    }
}
