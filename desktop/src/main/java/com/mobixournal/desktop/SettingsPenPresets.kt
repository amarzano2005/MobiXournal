package com.mobixournal.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * **Stylus → Pressure: the preset carousel** — the named pairs of the pen's two numbers, as one strip of
 * chips the reader scrolls through.
 *
 * A preset is a *name* for the floor and the multiplier and nothing else, so this strip is the page's
 * selection control, not a list of settings: one click on a chip applies that pair, and the two rows under
 * it then write the numbers **of the chip that is lit**. That is why the strip and those sliders share one
 * group — a reader should never have to work out which preset a slider edits.
 *
 * *New preset* sits beside the strip's heading rather than at the end of the strip: a list of presets
 * grows, and a button that scrolls off the right edge is a button half the window cannot find. It opens the
 * **one** name field this page and the menu's Pen block share (`PresetNameField`), and while that draft is
 * open the word is disabled — one name is written at a time. Picking a chip drops the draft, because a name
 * being typed belongs to the pair that was live when it was opened.
 *
 * The chips are the window's own [SettingsPill], so the picked one wears the steel fill and the accent
 * word, Tab reaches it and `Enter`/`Space` takes it — the five states of `docs/interface-contract.md`, not
 * a list that only answers the mouse.
 */
@Composable
internal fun StylusPresetCarousel(policy: DesktopSettingsState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Presets",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
            modifier = Modifier.weight(1f),
        )
        SettingsTextAction(
            text = "New preset",
            enabled = policy.presetDraft == null,
            onClick = policy::beginPresetSave,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val live = policy.selectedPenPresetId
        for (preset in policy.penPresets) {
            SettingsPill(text = preset.name, selected = preset.id == live) {
                policy.applyPenPreset(preset.id)
                policy.cancelPresetSave()
            }
        }
    }
    if (policy.presetDraft != null) {
        SettingsRow(label = "Preset name", note = "Enter saves it, Esc puts the field away.") {
            PresetNameField(
                value = policy.presetDraft.orEmpty(),
                onValue = policy::typePresetName,
                onCommit = policy::commitPresetSave,
                modifier = Modifier.width(170.dp),
                onCancel = policy::cancelPresetSave,
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
