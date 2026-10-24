package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strips' **click rule as a gesture**, not as a `when`: a click on a slot that is not picked arms it,
 * and the click *after* that opens the slot's submenu — the circuit family's palette of parts included.
 *
 * The rule is unit-tested in `ToolGroupsTest` and the failure this file exists for was one step further
 * along: `opensSubmenu` said a circuit slot had a palette, but the slot's own gesture reported it as a lone
 * tool, so a second click on Circuit did nothing at all. Only driving the scene proves the two agree.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ToolSlotClickTest {

    @Test
    fun `a second click on a circuit slot opens its palette`() {
        var selection by mutableStateOf(ToolSelection())
        var circuitPalettes = 0
        var groupPickers = 0
        val scene = ImageComposeScene(1400, 200, Density(1f)) {
            ToolRibbon(
                state = ToolStripState(
                    selection = selection,
                    onOpenGroup = { groupPickers++ },
                    onCircuitPicker = { family -> if (family != null) circuitPalettes++ },
                ),
                onSelect = { picked -> selection = selection.select(picked) },
            )
        }
        try {
            val index = DESKTOP_TOOL_GROUPS.indexOfFirst { DesktopTool.CIRCUIT in it.members }
            val x = ToolStripMetrics.ribbonSlotLeft.value +
                index * (ToolStripMetrics.slotSize.value + ToolStripMetrics.gap.value) +
                ToolStripMetrics.slotSize.value / 2
            val y = ToolStripMetrics.ribbonSlotTop.value + ToolStripMetrics.slotSize.value / 2

            click(scene, x, y, at = 1_000L)
            assertEquals("the first click only arms the slot", 0, circuitPalettes)
            assertEquals("and it is the circuit slot that is armed", DesktopTool.CIRCUIT, selection.current)

            click(scene, x, y, at = 1_200L)
            assertEquals("the second click opens the palette of parts", 1, circuitPalettes)
            assertEquals("a circuit palette is not a group picker", 0, groupPickers)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a second click on a grouped slot opens its group picker`() {
        var selection by mutableStateOf(ToolSelection())
        var groupPickers = 0
        val scene = ImageComposeScene(1400, 200, Density(1f)) {
            ToolRibbon(
                state = ToolStripState(
                    selection = selection,
                    onOpenGroup = { groupPickers++ },
                ),
                onSelect = { picked -> selection = selection.select(picked) },
            )
        }
        try {
            val index = DESKTOP_TOOL_GROUPS.indexOfFirst { DesktopTool.ERASER in it.members }
            val x = ToolStripMetrics.ribbonSlotLeft.value +
                index * (ToolStripMetrics.slotSize.value + ToolStripMetrics.gap.value) +
                ToolStripMetrics.slotSize.value / 2
            val y = ToolStripMetrics.ribbonSlotTop.value + ToolStripMetrics.slotSize.value / 2

            click(scene, x, y, at = 1_000L)
            assertEquals("the first click arms the eraser", DesktopTool.ERASER, selection.current)
            click(scene, x, y, at = 1_200L)
            assertEquals("the second click asks which eraser", 1, groupPickers)
        } finally {
            scene.close()
        }
    }

    /** A mouse press and release that never moves: one click, at [x], [y], at [at] ms. */
    private fun click(scene: ImageComposeScene, x: Float, y: Float, at: Long) {
        for (pressed in listOf(true, false)) {
            scene.sendPointerEvent(
                eventType = if (pressed) PointerEventType.Press else PointerEventType.Release,
                pointers = listOf(
                    ComposeScenePointer(
                        id = PointerId(1),
                        position = Offset(x, y),
                        pressed = pressed,
                        type = PointerType.Mouse,
                    ),
                ),
                buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
                keyboardModifiers = PointerKeyboardModifiers(),
                timeMillis = at,
            )
            Snapshot.sendApplyNotifications()
            scene.render()
        }
    }
}
