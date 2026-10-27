package com.mobixournal.desktop

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * **Stylus → Presets**: the named pairs of the pen's two pressure numbers, as the tablet keeps them.
 *
 * A preset is a *name* for the floor and the multiplier, and nothing else — applying one writes the two
 * numbers the rows above write, so a preset cannot mean anything a hand could not have set. The list is
 * live: a chip in the menu and a row here read the same [DesktopSettingsState.penPresets], and moving
 * either slider rewrites the live entry, so the list never describes a pen the window is not drawing with.
 *
 * *Save as preset* opens **one** name field — the menu's Pen block opens the same draft — and the last
 * preset stays: a list with no entry would be a filter with no name, and the pen is always drawing with one
 * of them. Deleting the live preset is therefore two steps — pick another, then delete it — because the
 * right edge of the live row says what it is rather than offering to remove the pen's current numbers.
 */
@Composable
internal fun StylusPresetGroup(policy: DesktopSettingsState) {
    SettingsGroup("Presets") {
        for (preset in policy.penPresets) {
            val live = preset.id == policy.selectedPenPresetId
            // One word at the right edge, not two: the live row says it *is* the pen, every other row
            // offers the one action it has. A row that said "in use" in its note **and** at its edge read
            // as a state the reader had to reconcile with itself.
            SettingsRow(
                label = preset.name,
                note = preset.values,
                onClick = { policy.applyPenPreset(preset.id) },
            ) {
                if (live) {
                    Text("In use", color = DesktopTheme.accent, style = DesktopTheme.label)
                } else {
                    SettingsTextAction(
                        text = "Delete",
                        tone = DesktopTheme.danger,
                        onClick = { policy.deletePreset(preset.id) },
                    )
                }
            }
        }
        if (policy.presetDraft == null) {
            SettingsRow(
                label = "Save as preset",
                note = "Keeps these two numbers under a name the menu offers.",
                onClick = policy::beginPresetSave,
            ) {
                Text("Save", color = DesktopTheme.accent, style = DesktopTheme.label)
            }
        } else {
            SettingsRow(
                label = "Preset name",
                note = "Enter saves it, Esc puts the field away.",
            ) {
                PresetNameField(
                    value = policy.presetDraft.orEmpty(),
                    onValue = policy::typePresetName,
                    onCommit = policy::commitPresetSave,
                    modifier = Modifier.width(170.dp),
                )
                Spacer(Modifier.width(6.dp))
                SettingsTextAction(
                    text = "Save",
                    enabled = !policy.presetDraft.isNullOrBlank(),
                    onClick = policy::commitPresetSave,
                )
                SettingsTextAction(
                    text = "Cancel",
                    tone = DesktopTheme.inkSecondary,
                    onClick = policy::cancelPresetSave,
                )
            }
        }
    }
    SettingsNote("Applying a preset writes the two numbers above; moving a slider rewrites the live one.")
}
