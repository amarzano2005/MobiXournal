package com.mobixournal.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.mobixournal.desktop.settings.PropertyCheckboxRow
import org.junit.Assert.*
import org.junit.Test

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class PropertyControlsTest {
    @Test fun checkboxLabelTogglesTheCurrentValueOncePerClick() {
        val selected = mutableStateOf(false)
        var changes = 0
        val scene = ImageComposeScene(320, 60, Density(1f)) {
            PropertyCheckboxRow("Critical points", selected.value) { selected.value = it; changes++ }
        }
        fun click() {
            val position = Offset(170f, 20f)
            scene.sendPointerEvent(PointerEventType.Press, position, buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, position, buttons = PointerButtons())
            Snapshot.sendApplyNotifications(); scene.render()
        }
        try {
            scene.render()
            click(); assertTrue(selected.value); assertEquals(1, changes)
            click(); assertFalse(selected.value); assertEquals(2, changes)
        } finally { scene.close() }
    }
}
