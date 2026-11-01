package com.mobixournal.desktop.settings
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.GuideStrip
import com.mobixournal.desktop.sheet.circuitFamilyOf
import com.mobixournal.desktop.tools.CircuitKindPicker
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.FIGURE_TOOLS
import com.mobixournal.desktop.tools.FigureKindPicker
import com.mobixournal.desktop.tools.FigureStrip
import com.mobixournal.desktop.tools.ToolGroup
import com.mobixournal.desktop.tools.ToolGroupPicker
import com.mobixournal.desktop.tools.ToolSelection

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import com.mobixournal.render.CircuitFamily
import com.mobixournal.render.ShapeKind

/**
 * The lists a slot can open, in one place: the group's members, a circuit family's component palette and
 * a figure's own variants. Which of the three belongs to which slot is the window's rule, so it lives
 * beside the slot rather than in each strip.
 */
@Composable
internal fun SlotPickerStack(
    group: ToolGroup,
    selection: ToolSelection,
    tool: DesktopTool,
    pickerOpen: Boolean,
    circuitKinds: Map<CircuitFamily, ShapeKind>,
    circuitPickerOpen: CircuitFamily?,
    figures: FigureStrip,
    guides: GuideStrip,
    onSelect: (DesktopTool) -> Unit,
    onClosePicker: () -> Unit,
    /** The group picker was put away by a press outside it. */
    onDismissPicker: () -> Unit,
    /** The circuit palette was put away by a press outside it. */
    onDismissCircuit: () -> Unit,
    /** A figure's variant menu was put away by a press outside it. */
    onDismissFigure: () -> Unit,
    onCircuitKind: (CircuitFamily, ShapeKind) -> Unit,
    onCircuitPicker: (CircuitFamily?) -> Unit,
    offset: IntOffset,
) {
    if (pickerOpen) {
        ToolGroupPicker(
            group = group,
            selection = selection,
            onPick = { member ->
                onSelect(member)
                onClosePicker()
            },
            onDismiss = onDismissPicker,
            offset = offset,
            // The guides' picker is the tablet's own menu: *Off* above the two instruments.
            onOff = if (group.isGuides()) guides.onOff else null,
        )
    }
    val figure = tool.takeIf { it in FIGURE_TOOLS }
    if (figure != null && figures.menu == figure) {
        FigureKindPicker(
            tool = figure,
            shape = figures.shape,
            // A pick lands on the figure it answers: a menu opened from the chevron of a slot that was
            // not picked takes that slot's tool in the same gesture, as the tablet's count picker does.
            onPick = { shape ->
                onSelect(figure)
                figures.onShape(shape)
            },
            onEdit = figures.onOpenDialog,
            onDismiss = onDismissFigure,
            offset = offset,
        )
    }
    val family = circuitFamilyOf(tool)
    if (family != null && circuitPickerOpen == family) {
        CircuitKindPicker(
            family = family,
            current = circuitKinds[family] ?: family.face,
            onPick = { kind ->
                onCircuitKind(family, kind)
                onCircuitPicker(null)
            },
            onDismiss = onDismissCircuit,
            offset = offset,
        )
    }
}
