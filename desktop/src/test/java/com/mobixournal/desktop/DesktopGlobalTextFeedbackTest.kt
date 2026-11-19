package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.DesktopButton
import com.mobixournal.desktop.tools.DesktopButtonVariant
import com.mobixournal.desktop.tools.DesktopIconButton
import com.mobixournal.desktop.tools.DesktopTextAction
import com.mobixournal.desktop.tools.DesktopTextField
import com.mobixournal.desktop.tools.PanelButton
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * Real pixels of the command hierarchy: four filled variants, one glyph control, one field, one label.
 *
 * The window used to draw a dialog's commit as underlined words and a header control as a capsule, so the
 * same two answers had two materials. This reads the primitives back out of the render: the accent commit,
 * the control-toned answer, the ghost that rests on nothing, the refusal red, the pointer's step on each of
 * them, the field's tone and its accent focus, and a label that never grows a box.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopGlobalTextFeedbackTest {
    @get:Rule val compose = createComposeRule()
    @After fun restoreTheme() { DesktopTheme.use(false) }

    private fun verify(light: Boolean) {
        DesktopTheme.use(light)
        compose.setContent {
            DesktopTheme.Apply {
                Column(Modifier.width(320.dp).background(DesktopTheme.desk)) {
                    PanelButton("Commit", primary = true, enabled = true, onClick = {})
                    Spacer(Modifier.height(6.dp))
                    PanelButton("Cancel", primary = false, enabled = true, onClick = {})
                    Spacer(Modifier.height(6.dp))
                    DesktopButton("Discard", {}, variant = DesktopButtonVariant.Ghost)
                    Spacer(Modifier.height(6.dp))
                    PanelButton("Delete", primary = false, enabled = true, destructive = true, onClick = {})
                    Spacer(Modifier.height(6.dp))
                    PanelButton("Unavailable", primary = true, enabled = false, onClick = {})
                    Spacer(Modifier.height(6.dp))
                    DesktopIconButton("Close", {}, tooltip = null) {}
                    Spacer(Modifier.height(6.dp))
                    DesktopTextField(value = "42", onValueChange = {})
                    Spacer(Modifier.height(6.dp))
                    DesktopTextAction("A label", {})
                }
            }
        }

        assertTrue("the commit is a filled accent", near(fill("Commit"), DesktopTheme.accent))
        assertTrue("the ordinary answer takes the control tone", near(fill("Cancel"), DesktopTheme.control))
        assertTrue("a ghost rests on nothing", none(fill("Discard")))
        // The wash the contract names: the refusal red at 14 % over the desk it stands on.
        val wash = DesktopTheme.danger.copy(alpha = 0.14f).compositeOver(DesktopTheme.desk)
        assertTrue("a destructive answer is the refusal wash, not the accent", near(fill("Delete"), wash))
        assertTrue("a disabled commit is the accent thinned", !near(fill("Unavailable"), DesktopTheme.accent))

        val resting = fill("Cancel")
        compose.onNodeWithText("Cancel").performMouseInput { enter(); moveTo(center) }
        compose.waitForIdle()
        val hovered = fill("Cancel")
        assertNotEquals("the answer shifts under the pointer", resting, hovered)
        compose.onNodeWithText("Cancel").performMouseInput { press() }
        compose.waitForIdle()
        assertNotEquals("a held answer is one step further than a hovered one", hovered, fill("Cancel"))
        compose.onNodeWithText("Cancel").performMouseInput { release(); exit() }

        compose.onNodeWithText("Unavailable").assertIsNotEnabled()

        // A label is not a box: the pointer changes its ink and its underline, never the space around it.
        val label = compose.onNodeWithText("A label")
        val beforeLabel = label.captureToImage().toPixelMap()
        label.performMouseInput { enter(); moveTo(center) }
        compose.waitForIdle()
        val afterLabel = label.captureToImage().toPixelMap()
        for (x in 2 until afterLabel.width - 2) for (y in 2..5) {
            assertTrue("a label never acquires a filled state layer at $x,$y", beforeLabel[x, y] == afterLabel[x, y])
        }
        label.performMouseInput { exit() }
    }

    /** The top of a command's own padding: inside its fill, above its words. */
    private fun fill(label: String): Color {
        val pixels = compose.onNodeWithText(label).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, 3]
    }

    @Test fun darkThemeCommandsWearTheFlatHierarchy() = verify(false)
    @Test fun lightThemeCommandsWearTheFlatHierarchy() = verify(true)

    @Test fun theFieldWearsTheFieldToneAndTheAccentWhileTypedIn() {
        DesktopTheme.use(false)
        compose.setContent {
            DesktopTheme.Apply {
                Column(Modifier.width(320.dp).background(DesktopTheme.desk)) {
                    DesktopTextField(value = "", onValueChange = {}, placeholder = "Name")
                }
            }
        }
        val input = compose.onNode(hasSetTextAction())
        val rest = compose.onRoot().captureToImage().toPixelMap()
        // The field is the only surface of its tone on the desk, and the only thing that can wear the accent.
        assertTrue("a field sits on the panel's field tone", count(rest) { near(it, DesktopTheme.panelField) } > 200)
        val restingAccent = count(rest) { it.blue > 0.7f && it.red < 0.55f }
        assertTrue("a resting field draws no accent at all ($restingAccent px)", restingAccent < 20)
        compose.onNodeWithText("Name").assertIsDisplayed()

        input.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        input.assertIsFocused()
        val focusedAccent = count(compose.onRoot().captureToImage().toPixelMap()) { it.blue > 0.7f && it.red < 0.55f }
        assertTrue("focus is spoken by the accent border ($focusedAccent px)", focusedAccent > restingAccent + 40)
    }

    /** How many pixels of the window answer [matches]. */
    private fun count(image: androidx.compose.ui.graphics.PixelMap, matches: (Color) -> Boolean): Int {
        var found = 0
        for (x in 0 until image.width) for (y in 0 until image.height) if (matches(image[x, y])) found++
        return found
    }

    @Test fun anIconControlAnswersThePointerAndStaysTransparentAtRest() {
        DesktopTheme.use(false)
        var clicks = 0
        compose.setContent {
            DesktopTheme.Apply {
                Column(Modifier.width(120.dp).background(DesktopTheme.desk)) {
                    DesktopIconButton("Close", { clicks++ }, tooltip = null) {}
                }
            }
        }
        val icon = compose.onNodeWithContentDescription("Close")
        val resting = icon.captureToImage().toPixelMap()[2, 2]
        icon.performMouseInput { enter(); moveTo(center) }
        compose.waitForIdle()
        assertNotEquals("the glyph control answers the pointer", resting, icon.captureToImage().toPixelMap()[2, 2])
        icon.performClick()
        assertTrue("and still runs its command", clicks == 1)
    }

    private fun near(colour: Color, target: Color): Boolean =
        abs(colour.red - target.red) < 0.06f && abs(colour.green - target.green) < 0.06f &&
            abs(colour.blue - target.blue) < 0.06f

    /** A fill that is not painted at all: either transparent or the desk showing through. */
    private fun none(colour: Color): Boolean = colour.alpha < 0.02f || near(colour, DesktopTheme.desk)

}
