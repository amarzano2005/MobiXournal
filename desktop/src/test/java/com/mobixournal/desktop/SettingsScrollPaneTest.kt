package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.SettingsScrollPane
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class SettingsScrollPaneTest {
    @Test fun changingSectionReturnsToItsHeadingAfterScrolling() {
        val section = mutableStateOf(1)
        val scene = ImageComposeScene(200, 100, Density(1f)) {
            SettingsScrollPane(Modifier.fillMaxSize(), section.value, Modifier) {
                Box(Modifier.fillMaxWidth().height(60.dp).background(Color.Blue))
                Box(Modifier.fillMaxWidth().height(400.dp).background(Color.Red))
            }
        }
        var clock = System.nanoTime()
        fun color(): Int {
            Snapshot.sendApplyNotifications()
            clock += 16_000_000L
            val bytes = scene.render(clock).encodeToData()!!.bytes
            return javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)).getRGB(50, 10)
        }
        try {
            assertEquals(0xFF0000FF.toInt(), color())
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(50f, 50f), scrollDelta = Offset(0f, 100f))
            repeat(30) { color() }
            assertEquals("the long page has scrolled", 0xFFFF0000.toInt(), color())
            section.value = 2
            assertEquals("the new page starts at its heading", 0xFF0000FF.toInt(), color())
        } finally { scene.close() }
    }
}
