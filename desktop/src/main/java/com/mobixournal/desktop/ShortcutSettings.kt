package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * **Shortcuts**: every command the window has, the key it answers to, and a field that takes the next press.
 *
 * The table is [ShortcutTable]'s — the same rows the keyboard resolves through — so the page cannot list a
 * command the window does not have, and a row that is remapped here is remapped for the page underneath it
 * the moment the press lands. What this file adds is the **surface**: one row per command, the key it holds
 * on the right, a click that arms detection, and the sentence that names the command a taken key came from.
 *
 * The press is caught on the page's own container rather than on the row (`onPreviewKeyEvent`), because the
 * row a click lands on has no text field to keep the keyboard: arming a row hands the focus to the page, and
 * the page then reads exactly one press and gives the window back.
 */
@Composable
internal fun ShortcutsSettings(policy: DesktopSettingsState) {
    val focus = remember { FocusRequester() }
    var detecting by remember { mutableStateOf<String?>(null) }

    val onDetect: (String) -> Unit = remember(focus) {
        { id ->
            policy.clearShortcutConflict()
            detecting = id
            runCatching { focus.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val id = detecting ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                // `Esc` is the way out of a field that is waiting: binding it would need a second key that
                // means "never mind", and the window already has one.
                if (event.key == Key.Escape) {
                    detecting = null
                    return@onPreviewKeyEvent true
                }
                val name = ShortcutTable.name(
                    event.key,
                    ctrl = event.isCtrlPressed,
                    shift = event.isShiftPressed,
                    alt = event.isAltPressed,
                ) ?: return@onPreviewKeyEvent true
                policy.rebind(id, name)
                detecting = null
                true
            },
    ) {
        // The page leads with the switches, then the tables — a toggle is the key a reader looks for first.
        ToggleGroup(policy = policy, detecting = detecting, onDetect = onDetect)

        SettingsGroup("Key table") {
            SettingsRow(
                label = "Detect a key",
                note = "Click a row, then press the key it should answer to. One key runs one command: the " +
                    "command that held it loses it, and the line below says which.",
            ) {
                Text(
                    if (detecting == null) "Waiting for a row" else "Press a key…",
                    color = if (detecting == null) DesktopTheme.inkMuted else DesktopTheme.accent,
                    style = DesktopTheme.label,
                )
            }
            policy.shortcutConflict?.let { lost ->
                SettingsRow(label = "Conflict", note = null) {
                    Text(
                        "${lost.label} lost its key to the new binding.",
                        color = DesktopTheme.danger,
                        style = DesktopTheme.label,
                    )
                }
            }
        }

        ShortcutGroup(
            title = "Tools",
            note = "A tool's key picks its slot. Ctrl is the shell's own column, so the field refuses it here.",
            entries = ShortcutTable.entries.filter { it.tool != null },
            table = policy.shortcutTable,
            detecting = detecting,
            onDetect = onDetect,
        )
        ShortcutGroup(
            title = "Commands",
            note = "The window's own commands, from the file and the view.",
            entries = ShortcutTable.entries.filter { it.command != null && it.command !in TOGGLE_COMMANDS },
            table = policy.shortcutTable,
            detecting = detecting,
            onDetect = onDetect,
        )

        SettingsGroup("Factory settings") {
            SettingsRow(
                label = "Restore defaults",
                note = "Every row back to the key it ships with.",
                onClick = {
                    policy.restoreShortcuts()
                    detecting = null
                },
            ) {
                Text("Restore", color = DesktopTheme.accent, style = DesktopTheme.label)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * **Toggle**: the three switches a stylus button presses, above the tables that hold every other key.
 *
 * A toggle is a command, not a tool, so it lives in [ShortcutTable] beside the rest of the key table; what
 * this group adds is the **variant**. The eraser and the selection are families of two on the page
 * (`ToolToggle`), and which member a press picks is a choice — the cascade under each row names it, and the
 * choice is stored per family, so the toggle has one answer rather than a state to guess.
 */
@Composable
private fun ToggleGroup(policy: DesktopSettingsState, detecting: String?, onDetect: (String) -> Unit) {
    SettingsGroup("Toggle") {
        SettingsRow(
            label = "Toggle",
            note = "One press picks the tool, the next puts back the tool that was in hand.",
        ) { }
        for (entry in ShortcutTable.toggleEntries) {
            ShortcutShortcutRow(
                entry = entry,
                key = ShortcutTable.key(policy.shortcutTable, entry.id),
                armed = detecting == entry.id,
                onClick = { onDetect(entry.id) },
            )
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

/** One group of rows: a tool's, or a command's, with the same row shape in both. */
@Composable
private fun ShortcutGroup(
    title: String,
    note: String,
    entries: List<ShortcutEntry>,
    table: Map<String, String>,
    detecting: String?,
    onDetect: (String) -> Unit,
) {
    SettingsGroup(title) {
        SettingsRow(label = title, note = note) { }
        for (entry in entries) {
            ShortcutShortcutRow(
                entry = entry,
                key = ShortcutTable.key(table, entry.id),
                armed = detecting == entry.id,
                onClick = { onDetect(entry.id) },
            )
        }
    }
}

/** One command's row: its name, and the key it holds — the cell that is clicked to detect a new one. */
@Composable
private fun ShortcutShortcutRow(entry: ShortcutEntry, key: String, armed: Boolean, onClick: () -> Unit) {
    SettingsRow(label = entry.label, onClick = onClick) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (key.isEmpty()) {
                Text("None", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            } else {
                ShortcutKeyChip(key)
            }
            if (armed) Spacer(Modifier.width(2.dp))
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
