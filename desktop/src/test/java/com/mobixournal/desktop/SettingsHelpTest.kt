package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The **help line**: a row keeps its sentence to itself until the pointer is on it.
 *
 * A settings page draws one line per setting and sends the sentence that explains it to the panel's footer
 * (`LocalSettingsHelp`), which is what makes a page of twelve settings fit a window instead of running past
 * its bottom. That trade only works if the sentence actually arrives, and if crossing from one row to the
 * next replaces it rather than blanking it — a footer that emptied on the way to the second row would be a
 * worse page than the two-line rows it replaced.
 *
 * The rows are laid out in a 400 × 120 scene at density 1, so each is 40 px tall and the two probe points
 * are their middles. Nothing here reads the panel: this is the hand-off between a row and the line.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SettingsHelpTest {

    @Test
    fun `a row hands its sentence to the help line`() {
        val help = mutableStateOf("")
        val scene = ImageComposeScene(400, 120, Density(1f)) {
            CompositionLocalProvider(LocalSettingsHelp provides help) {
                Column(Modifier.fillMaxSize().background(DesktopTheme.desk)) {
                    SettingsRow(label = "Stream", note = "Wintab reads the driver's packets.") {}
                    SettingsRow(label = "Multiplier", note = "How far above 1.00 a light press reaches.") {}
                }
            }
        }
        try {
            Snapshot.sendApplyNotifications()
            assertEquals("nothing is hovered, so the line is empty", "", help.value)

            scene.sendPointerEvent(PointerEventType.Enter, Offset(200f, 20f))
            Snapshot.sendApplyNotifications()
            assertEquals("the first row's sentence", "Wintab reads the driver's packets.", help.value)

            scene.sendPointerEvent(PointerEventType.Move, Offset(200f, 60f))
            Snapshot.sendApplyNotifications()
            assertEquals(
                "the second row replaces it rather than being cleared by the first row's exit",
                "How far above 1.00 a light press reaches.",
                help.value,
            )

            scene.sendPointerEvent(PointerEventType.Exit, Offset(-10f, -10f))
            Snapshot.sendApplyNotifications()
            assertEquals("and the line empties when the pointer leaves", "", help.value)
        } finally {
            scene.close()
        }
    }
}
