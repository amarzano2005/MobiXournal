package com.mobixournal.desktop
import com.mobixournal.desktop.tools.InspectorSizing
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class DesktopInspectorSizingTest {
    @Test fun resizingIsBoundedAndKeepsCanvasSpaceOnNarrowWindows() {
        val sizing = InspectorSizing()
        assertEquals(380.dp, sizing.width)
        sizing.resize((-1000).dp); assertEquals(520.dp, sizing.width)
        sizing.resize(1000.dp); assertEquals(320.dp, sizing.width)
        assertTrue(sizing.effective(400.dp) <= 220.dp)
        sizing.reset(); assertEquals(380.dp, sizing.width)
    }
    @Test fun groupHeaderFoldsAndRestoresItsExistingControls() {
        val scene = androidx.compose.ui.ImageComposeScene(380, 180, androidx.compose.ui.unit.Density(1f)) {
            androidx.compose.foundation.layout.Column {
                com.mobixournal.desktop.tools.SectionCard("Domain") { androidx.compose.material3.Text("Visible field") }
            }
        }
        fun pixels(): IntArray {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            val image = scene.render().encodeToData()!!.bytes
            val bitmap = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(image))
            return bitmap.getRGB(0, 60, 380, 100, null, 0, 380)
        }
        fun click() {
            val at = androidx.compose.ui.geometry.Offset(80f, 30f)
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, at,
                buttons = androidx.compose.ui.input.pointer.PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, at,
                buttons = androidx.compose.ui.input.pointer.PointerButtons())
        }
        try {
            val expanded = pixels()
            click(); val collapsed = pixels()
            assertFalse(expanded.contentEquals(collapsed))
            click(); assertArrayEquals(expanded, pixels())
        } finally { scene.close() }
    }
    @Test fun focusedInspectorControlsOwnTheirNavigationKeysWithoutSwallowingOtherShortcuts() {
        val first = Any(); val second = Any()
        val keys = com.mobixournal.desktop.shell.WindowKeys
        try {
            keys.focusControl(first, true)
            assertTrue(keys.defersControlKey(androidx.compose.ui.input.key.Key.DirectionLeft))
            assertTrue(keys.defersControlKey(androidx.compose.ui.input.key.Key.Spacebar))
            assertFalse(keys.defersControlKey(androidx.compose.ui.input.key.Key.S))
            keys.focusControl(second, true); keys.focusControl(first, false)
            assertTrue(keys.defersControlKey(androidx.compose.ui.input.key.Key.Home))
        } finally { keys.focusControl(first, false); keys.focusControl(second, false) }
        assertFalse(keys.defersControlKey(androidx.compose.ui.input.key.Key.DirectionLeft))
    }
}
