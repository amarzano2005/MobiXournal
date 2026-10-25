package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/**
 * The **glass picker** a grouped slot opens: its members as rows, each one the glyph it would put on the
 * slot, its name and the accelerator it already has.
 *
 * The picker is the tablet's chevron turned into a surface (`docs/interface-contract.md`: one material
 * class — the floating glass with its rim — and the five states a row owes the pointer: rest, hover,
 * picked, keyboard-open and dismissed). It **re-faces the slot and nothing else**: picking a member is the
 * same act as pressing that member's key, so the shortcut table of `docs/ui-modernization.md` §3 keeps one
 * meaning per key and a user who never opens a picker can still reach every tool.
 *
 * A [Popup] rather than a box inside the strip for the same reason the hover pill is one: the strip is a
 * narrow lane and a list laid out inside it would be clipped by it.
 */
@Composable
internal fun ToolGroupPicker(
    group: ToolGroup,
    selection: ToolSelection,
    onPick: (DesktopTool) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset,
) {
    Popup(offset = offset, onDismissRequest = onDismiss, properties = FloatingSurface) {
        Column(
            Modifier
                .glassSurface()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (member in group.members) {
                PickerRow(
                    tool = member,
                    picked = member == selection.current,
                    onPick = { onPick(member) },
                )
            }
        }
    }
}

/** One member of the picker: its face on the left, its name, its key on the right. */
@Composable
private fun PickerRow(tool: DesktopTool, picked: Boolean, onPick: () -> Unit) {
    val glyph = if (picked) DesktopTheme.accent else DesktopTheme.chromeInk
    Row(
        Modifier
            .hoverFill(DesktopTheme.railSlotShape, active = picked)
            .then(
                if (picked) Modifier.background(DesktopTheme.activeSlot, DesktopTheme.railSlotShape)
                else Modifier,
            )
            .pointerInput(tool) { detectTapGestures { onPick() } }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) { drawToolGlyph(tool, glyph) }
        Spacer(Modifier.width(8.dp))
        Text(tool.label, color = DesktopTheme.ink, style = DesktopTheme.label)
        Spacer(Modifier.width(12.dp))
        Text(tool.shortcut, color = DesktopTheme.accent, style = DesktopTheme.numeric)
    }
}

/**
 * The **chevron** on a grouped slot: the marker that says "there is more than one tool here".
 *
 * It is chrome, not a tool face, so it is not a catalogue glyph — `docs/interface-contract.md` §4 puts a
 * strip's own affordances beside the icon catalogue, exactly as the sidebar's layer eye is. Two strokes in
 * the chrome ink, at the slot's end, so a slot with a picker cannot be mistaken for a lone tool.
 */
@Composable
internal fun SlotChevron(active: Boolean) {
    Canvas(Modifier.size(CHEVRON_SIZE)) {
        val colour = if (active) DesktopTheme.accent else DesktopTheme.chromeInk
        val stroke = size.height * 0.16f
        // A shallow V: a downward caret reads as "a list opens here" at six dp, where an arrow would not.
        drawLine(
            color = colour,
            start = Offset(0f, size.height * 0.3f),
            end = Offset(size.width / 2, size.height * 0.7f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = colour,
            start = Offset(size.width / 2, size.height * 0.7f),
            end = Offset(size.width, size.height * 0.3f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** The chevron's own size on a slot — small enough to leave the glyph its room. */
internal val CHEVRON_SIZE = 7.dp

