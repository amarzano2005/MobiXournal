package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph

/**
 * **Stylus → Pressure: the preset carousel** — the named pairs of the pen's two numbers, as one strip of
 * chips the reader scrolls through.
 *
 * A preset is a *name* for the floor and the multiplier and nothing else, so this strip is the page's
 * selection control, not a list of settings: one click on a chip applies that pair, and the two rows under
 * it then write the numbers **of the chip that is lit**. That is why the strip and those sliders share one
 * group — a reader should never have to work out which preset a slider edits.
 *
 * The strip's two marks are **on the thing they act on**: the red × sits beside the preset it removes, and
 * the `+` caps the last chip, where a new one will land. That replaced a *New preset* word and a *Delete
 * preset* row, both of which asked the reader to name a target in a list of six. The × is drawn only while
 * a second preset exists — the pen always draws with one, so the last chip has nothing to lose — and the
 * `+` is dimmed while a name is being written, because one name is written at a time.
 *
 * Picking a chip drops the draft: a name being typed belongs to the pair that was live when it was opened.
 * The chips are the window's own [SettingsPill], so the picked one wears the steel fill and the accent word,
 * Tab reaches it and `Enter`/`Space` takes it; the two marks keep the tab strip's glyph-button recipe
 * ([ChromeGlyph.ADD], [ChromeGlyph.CLOSE]) rather than the rows' five states, because they are chrome on a
 * control, not controls of their own.
 */
@Composable
internal fun StylusPresetCarousel(
    policy: DesktopSettingsState,
    /** The strip's left inset: the page's 12 dp, or the menu's own line inset. */
    inset: Dp = 12.dp,
    /** Called after a chip is applied — the menu puts itself away, the page stays where it is. */
    onApplied: () -> Unit = {},
) {
    Text(
        "Presets",
        color = DesktopTheme.inkSecondary,
        style = DesktopTheme.label,
        modifier = Modifier.padding(start = inset, top = 4.dp, bottom = 2.dp),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = inset),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val live = policy.selectedPenPresetId
        val deletable = policy.penPresets.size > 1
        for (preset in policy.penPresets) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsPill(text = preset.name, selected = preset.id == live) {
                    policy.applyPenPreset(preset.id)
                    policy.cancelPresetSave()
                    onApplied()
                }
                if (deletable) {
                    PresetDeleteMark(name = preset.name) { policy.deletePreset(preset.id) }
                }
            }
        }
        NewPresetMark(
            enabled = policy.presetDraft == null,
            onClick = policy::beginPresetSave,
        )
    }
    if (policy.presetDraft != null) {
        PresetNameBlock(policy, inset)
    }
}

/**
 * **The line a preset is named on**: the label and its note above, the field and its two words below.
 *
 * It is a block of its own rather than a [SettingsRow], because a row gives its control a *share* of the
 * line: the field plus *Save* and *Cancel* are wider than that share in a 320 dp menu — where this same
 * carousel is drawn — so the last word landed on top of the field. Full width, the field takes exactly
 * what the two words leave.
 */
@Composable
private fun PresetNameBlock(policy: DesktopSettingsState, inset: Dp) {
    Column(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 4.dp, bottom = 2.dp)) {
        Text("Preset name", color = DesktopTheme.inkSecondary, style = DesktopTheme.label)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PresetNameField(
                value = policy.presetDraft.orEmpty(),
                onValue = policy::typePresetName,
                onCommit = policy::commitPresetSave,
                modifier = Modifier.weight(1f),
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
        Text(
            "Enter saves it, Esc puts the field away.",
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The **red × beside one preset**: it removes that pair, and nothing else on the page could mean another.
 *
 * It sits tight against its chip (2 dp) so the pair reads as one entry in the strip, and it is gone while
 * only one preset is left — a control that refuses every click is a control that should not be drawn.
 */
@Composable
private fun PresetDeleteMark(name: String, onDelete: () -> Unit) {
    Box(
        Modifier
            .width(22.dp)
            .height(22.dp)
            .hoverFill(DesktopTheme.capsuleShape)
            .pointerInput(name) { detectTapGestures { onDelete() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) { drawChromeGlyph(ChromeGlyph.CLOSE, DesktopTheme.danger) }
    }
}

/**
 * The **`+` that caps the strip**: the one door to a new preset, and it stands where that preset will land.
 *
 * It wears the tab strip's add recipe — a hairline capsule and the monoline plus — so "a new one goes here"
 * looks the same in both places. While the name field is open it is drawn muted and takes no click.
 */
@Composable
private fun NewPresetMark(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(SettingsMetrics.CONTROL_HEIGHT)
            .height(SettingsMetrics.CONTROL_HEIGHT)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape)
            .border(1.dp, DesktopTheme.hairline, DesktopTheme.capsuleShape)
            .pointerInput(enabled) { if (enabled) detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawChromeGlyph(ChromeGlyph.ADD, if (enabled) DesktopTheme.inkSecondary else DesktopTheme.inkMuted)
        }
    }
}
