package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.SettingsSelectMenu
import com.mobixournal.desktop.tools.GlassPopup
import com.mobixournal.desktop.tools.glassSurface
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Dropdown coverage is measured in actual Compose pixels, including the first native popup frame. */
@OptIn(ExperimentalTestApi::class)
class DesktopPopupMaterialTest {
    @get:Rule val compose = createComposeRule()
    @After fun restoreTheme() { DesktopTheme.use(false) }

    private fun verifyCoverage(light: Boolean) {
        DesktopTheme.use(light)
        var under by mutableStateOf(Color.Red)
        compose.setContent {
            DesktopTheme.Apply {
                Box(Modifier.size(240.dp, 160.dp).background(under)) {
                    Box(Modifier.width(150.dp).testTag("menu")) {
                        SettingsSelectMenu(listOf("Off", "Automatic", "On hold"), "Automatic", { it }, 150.dp) {}
                    }
                }
            }
        }
        val node = compose.onNodeWithTag("menu")
        assertEquals(92f, node.fetchSemanticsNode().boundsInRoot.height, 0.5f)
        fun check() {
            val pixels = node.captureToImage().toPixelMap()
            for (y in listOf(18, 46, 74)) {
                assertEquals("the body covers contrasting controls underneath", DesktopTheme.popupSurface.toArgb(), pixels[140, y].toArgb())
            }
        }
        check()
        compose.runOnIdle { under = Color.Cyan }
        check()
    }

    @Test fun darkDropdownCoversUnderlyingControls() = verifyCoverage(false)
    @Test fun lightDropdownCoversUnderlyingControls() = verifyCoverage(true)

    @Test fun nativePopupIsFullSizeAndOpaqueOnItsFirstFrameAndOwnsInput() {
        compose.mainClock.autoAdvance = false
        var underlyingClicks = 0
        var picks = 0
        compose.setContent {
            DesktopTheme.Apply {
                Box(Modifier.size(300.dp, 200.dp).background(Color.Red).clickable { underlyingClicks++ }) {
                    GlassPopup(visible = true, onDismissRequest = {}, offset = IntOffset(20, 20)) {
                        Box(Modifier.width(150.dp).testTag("native")) {
                            SettingsSelectMenu(listOf("Off", "Automatic", "On hold"), "Automatic", { it }, 150.dp) { picks++ }
                        }
                    }
                }
            }
        }
        val popup = compose.onNodeWithTag("native")
        assertEquals(150f, popup.fetchSemanticsNode().boundsInRoot.width, 0.5f)
        assertEquals(DesktopTheme.popupSurface.toArgb(), popup.captureToImage().toPixelMap()[140, 46].toArgb())
        popup.performMouseInput { click(Offset(50f, 18f)) }
        compose.runOnIdle {
            assertEquals(1, picks)
            assertEquals("popup clicks cannot reach the page", 0, underlyingClicks)
        }
    }
}
