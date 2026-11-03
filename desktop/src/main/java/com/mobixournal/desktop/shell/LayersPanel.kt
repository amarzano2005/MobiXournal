package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.glassSpring
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph

/**
 * The **Layers** menu: the page's stack, top first, and every edit the tablet's own manager offers.
 *
 * It is the window's half of `LayersPopupButton`, in the window's idiom: the rows are the page's layers —
 * the picked one wears the steel, which is the layer new ink lands on — and the commands below act on that
 * picked layer. The tablet hangs five icon buttons on every row because a finger has no cursor; a menu on a
 * desk has one pointer and one pick, so the actions are rows, exactly as [PageMenuPanel] answers the page's
 * own questions.
 *
 * Every row wears a catalogue glyph in its leading slot ([ChromeGlyph.EDIT], [ChromeGlyph.MINUS] for the
 * merge that takes the upper layer away, the two chevrons for the z-order, [ChromeGlyph.DELETE],
 * [ChromeGlyph.ADD] and [ChromeGlyph.PASTE] for the selection move), so a row is recognised before it is
 * read, and no two commands share a picture (`docs/interface-contract.md` §4).
 */
@Composable
internal fun LayersPanel(actions: LayerMenuActions, onClose: () -> Unit) {
    val active = actions.activeIndex
    Column(
        Modifier
            .glassSurface()
            .width(LayerMenuMetrics.width)
            .padding(LayerMenuMetrics.padding),
        verticalArrangement = Arrangement.spacedBy(LayerMenuMetrics.gap),
    ) {
        LayerMenuHeading("Layers (top first)")
        if (actions.layers.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(LayerMenuMetrics.rowHeight)
                    .padding(horizontal = LayerMenuMetrics.rowInset),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("No layers", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            }
        }
        for (info in actions.layers.asReversed()) {
            LayerListRow(
                info = info,
                onActivate = { actions.activate(info.index) },
                onToggle = { actions.toggleHidden(info.index, info.visible) },
            )
        }
        LayerMenuRule()
        LayerMenuHeading("This layer")
        LayerMenuRow(ChromeGlyph.EDIT, "Rename layer…", enabled = actions.canManage) {
            onClose()
            actions.rename(active)
        }
        LayerMenuRow(ChromeGlyph.MINUS, "Merge layer down", enabled = actions.canMergeDown) {
            onClose()
            actions.mergeDown()
        }
        LayerMenuRow(ChromeGlyph.CHEVRON_UP, "Move layer up", enabled = actions.canMoveUp) {
            onClose()
            actions.moveUp()
        }
        LayerMenuRow(ChromeGlyph.CHEVRON_DOWN, "Move layer down", enabled = actions.canMoveDown) {
            onClose()
            actions.moveDown()
        }
        LayerMenuRow(ChromeGlyph.DELETE, "Delete layer", enabled = actions.canDelete) {
            onClose()
            actions.delete()
        }
        LayerMenuRule()
        LayerMenuRow(ChromeGlyph.ADD, "Add layer", enabled = actions.canManage) {
            onClose()
            actions.addLayer()
        }
        if (actions.hasSelection) {
            LayerMenuRow(ChromeGlyph.PASTE, "Move selection to this layer", enabled = actions.canManage) {
                onClose()
                actions.moveSelectionHere()
            }
        }
    }
}

/**
 * One **layer** of the list: the eye that switches it off, and the name that makes it the layer new ink
 * lands on. The picked one wears the steel — the same state a picked tab or slot wears, not a second look
 * invented for a list — and a switched-off layer stays listed in the muted ink, switched off and not gone.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LayerListRow(info: LayerRowInfo, onActivate: () -> Unit, onToggle: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val fill = when {
        info.active -> DesktopTheme.activeSlot
        hovered -> DesktopTheme.hoverSlot
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(LayerMenuMetrics.rowHeight)
            .background(fill, DesktopTheme.capsuleShape)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerInput(info.index, info.active) { detectTapGestures { onActivate() } }
            .padding(horizontal = LayerMenuMetrics.rowInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LayerMenuMetrics.eyeButton)
                .pointerInput(info.index, info.visible) { detectTapGestures { onToggle() } }
                .semantics { contentDescription = if (info.visible) "Hide layer" else "Show layer" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(LayerMenuMetrics.rowGlyph)) {
                drawChromeGlyph(
                    if (info.visible) ChromeGlyph.VISIBILITY else ChromeGlyph.VISIBILITY_OFF,
                    if (info.visible) DesktopTheme.inkSecondary else DesktopTheme.inkMuted,
                )
            }
        }
        Spacer(Modifier.width(LayerMenuMetrics.glyphGap))
        Text(
            info.label,
            color = if (info.visible) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.body,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A section's name in the panel, at the eyebrow role — the tablet's `MenuHeading`. */
@Composable
private fun LayerMenuHeading(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(LayerMenuMetrics.headingHeight)
            .padding(start = LayerMenuMetrics.rowInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = DesktopTheme.inkMuted, style = DesktopTheme.eyebrow, maxLines = 1)
    }
}

/**
 * One command of the panel: its glyph, its label, and the muted tone when the page cannot do it.
 *
 * The leading slot is the phone's own arrangement — a menu item is read by its picture first — and it is
 * [LayerMenuMetrics.glyphSlot] wide for every row, so the labels start on one line whatever picture stands
 * beside them. A disabled row keeps its ink and loses its hit area, the same rule the band's own controls
 * follow.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LayerMenuRow(glyph: ChromeGlyph, label: String, enabled: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(LayerMenuMetrics.rowHeight)
            .background(
                if (hovered && enabled) DesktopTheme.hoverSlot else Color.Transparent,
                DesktopTheme.capsuleShape,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(horizontal = LayerMenuMetrics.rowInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(LayerMenuMetrics.glyphSlot), contentAlignment = Alignment.CenterStart) {
            Canvas(Modifier.size(LayerMenuMetrics.rowGlyph)) {
                drawChromeGlyph(glyph, if (enabled) DesktopTheme.inkSecondary else DesktopTheme.inkMuted)
            }
        }
        Spacer(Modifier.width(LayerMenuMetrics.glyphGap))
        Text(
            label,
            color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.body,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A hairline between the stack and the commands that act on the picked layer. */
@Composable
private fun LayerMenuRule() {
    Box(
        Modifier.fillMaxWidth().height(LayerMenuMetrics.ruleHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    }
}

/**
 * **Rename layer** — the one question the layer menu asks in a card instead of a row.
 *
 * A name is typed, not picked, so it needs the keyboard: the field takes it, `Enter` names the layer and
 * `Esc` puts the card away, and the two capsules under it are the same answers for the pointer. The card is
 * the family's floating surface (`docs/interface-contract.md` §1) on the family's spring.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LayerRenamePanel(
    draft: String,
    onDraft: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.width(360.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text("Rename layer", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "Leave it empty to go back to the layer's number.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .width(300.dp)
                .height(32.dp)
                .background(DesktopTheme.panelField, DesktopTheme.capsuleShape)
                .semantics { contentDescription = "Layer name" }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                singleLine = true,
                textStyle = DesktopTheme.body.copy(color = DesktopTheme.ink),
                cursorBrush = SolidColor(DesktopTheme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter -> {
                                onCommit()
                                true
                            }
                            Key.Escape -> {
                                onCancel()
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            PanelButton(label = "Rename", primary = true, enabled = true, onClick = onCommit)
        }
    }
}

/**
 * The shell's overlay for that card: the invisible blocker — no stroke lands on the page behind a question —
 * and the card itself, on the family's spring.
 */
@Composable
internal fun BoxScope.LayerRenameHost(state: DesktopShellState) {
    val layers = state.layersSection
    val renaming = layers.renaming
    GlassPresence(visible = renaming != null) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            if (renaming != null) {
                LayerRenamePanel(
                    draft = layers.draft,
                    onDraft = layers::editDraft,
                    onCommit = { layers.commitRename() },
                    onCancel = { layers.cancelRename() },
                )
            }
        }
    }
}

/**
 * The panel's numbers, in one place — the layout reads them, and a test that clicks a row by the same
 * arithmetic the panel was laid out with does too (the rule [PageMenuMetrics] follows).
 */
internal object LayerMenuMetrics {
    val width = 236.dp
    val padding = 6.dp
    val rowHeight = 30.dp
    val headingHeight = 22.dp
    val ruleHeight = 7.dp
    val gap = 1.dp

    /** A row's leading picture and the column it stands in: the phone's menu-item geometry. */
    val rowGlyph = 16.dp
    val glyphSlot = 20.dp

    /** The air between that picture and the label. */
    val glyphGap = 2.dp

    /** The eye's target inside a layer row — small beside the row, so the row's own tap still activates. */
    val eyeButton = 22.dp

    /** A row's own horizontal inset. */
    val rowInset = 8.dp

    /** The top edge of the *n*-th row, from the panel's own padding and the gaps between what stands above. */
    fun rowTop(index: Int, heightOf: (Int) -> Dp = { rowHeight }): Dp {
        var y = padding
        repeat(index) { at -> y += heightOf(at) + gap }
        return y
    }
}
