package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.SettingsPill
import com.mobixournal.desktop.settings.SettingsTextAction
import com.mobixournal.desktop.tools.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real production factories, including primary commits, must not paint mobile state pills. */
@OptIn(ExperimentalTestApi::class)
class DesktopGlobalTextFeedbackTest {
    @get:Rule val compose = createComposeRule()
    @After fun restoreTheme() { DesktopTheme.use(false) }

    private fun verify(light: Boolean) {
        DesktopTheme.use(light)
        compose.setContent {
            DesktopTheme.Apply {
                Column(Modifier.width(300.dp).background(DesktopTheme.desk)) {
                    PanelButton("Commit", primary = true, enabled = true, onClick = {})
                    PanelButton("Delete", primary = false, enabled = true, destructive = true, onClick = {})
                    InspectorButton("Place", primary = true, enabled = true, onClick = {})
                    SettingsTextAction("Reset") {}
                    SettingsPill("Preset", selected = true) {}
                    ChoiceRow(listOf("Sans", "Serif"), "Sans") {}
                    DesktopTextAction("Disabled", {}, enabled = false)
                }
            }
        }
        for (label in listOf("Commit", "Delete", "Place", "Reset", "Preset", "Sans", "Serif")) {
            val node = compose.onNodeWithText(label)
            val before = node.captureToImage().toPixelMap()
            fun verifyBlankPadding() {
                val after = node.captureToImage().toPixelMap()
                for (x in 2 until after.width - 2) for (y in 2..5) {
                    assertEquals("$label must not fill its padding", before[x, y], after[x, y])
                }
            }
            node.performMouseInput { enter(); moveTo(center) }
            verifyBlankPadding()
            node.performMouseInput { press() }
            verifyBlankPadding()
            node.performMouseInput { release(); exit() }
            node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            node.assertIsFocused()
            verifyBlankPadding()
        }
        compose.onNodeWithText("Disabled").assertIsNotEnabled()
        compose.onNodeWithText("Sans").assertIsSelected()
        compose.onNodeWithText("Preset").assertIsSelected()
    }

    @Test fun darkThemeTextCommandsStayUnfilled() = verify(false)
    @Test fun lightThemeTextCommandsStayUnfilled() = verify(true)
}
