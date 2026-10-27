package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import com.mobixournal.render.PressureCurve
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The pen's **pressure presets**: the tablet's named pairs, saved and applied from the menu's Pen block
 * and from Settings → Stylus.
 *
 * The claims a reader would notice are the ones tested here: a saved preset is the two live numbers under
 * a name, the next launch still offers it and still has it selected, applying one feeds the same shared
 * filter the pen reads, and neither door can leave the list with no entry or the highlight on a preset that
 * no longer exists.
 */
class PenPresetTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String = "pen-presets.properties") = File(temp.root, name)

    private fun policy(name: String = "pen-presets.properties") =
        DesktopSettingsState(DesktopSettings(file(name)))

    @Test
    fun `a fresh install has the factory preset and it is live`() {
        val state = policy()
        assertEquals(listOf(DesktopPenPreset.DEFAULT_NAME), state.penPresets.map { it.name })
        assertEquals(DesktopPenPreset.DEFAULT_ID, state.selectedPenPresetId)
        assertEquals(PressureCurve.MINIMUM_PRESSURE_DEFAULT, state.minimumPressure, 1e-6f)
        assertEquals(1f, state.pressureMultiplier, 1e-6f)
        assertNull("no name field is open", state.presetDraft)
    }

    @Test
    fun `a saved preset keeps the live numbers and survives a restart as the live one`() {
        val target = file("save.properties")
        val state = DesktopSettingsState(DesktopSettings(target))
        state.chooseMinimumPressure(0.2f)
        state.choosePressureMultiplier(1.5f)
        state.beginPresetSave()
        state.typePresetName("Light hand")
        state.commitPresetSave()

        assertEquals(listOf("Default", "Light hand"), state.penPresets.map { it.name })
        assertEquals("the handle is the name's slug", "light-hand", state.penPresets.last().id)
        assertEquals("saving selects the new preset", "light-hand", state.selectedPenPresetId)
        assertNull("and puts the field away", state.presetDraft)

        val reopened = DesktopSettingsState(DesktopSettings(target))
        assertEquals(listOf("Default", "Light hand"), reopened.penPresets.map { it.name })
        assertEquals("light-hand", reopened.selectedPenPresetId)
        assertEquals(0.2f, reopened.minimumPressure, 1e-6f)
        assertEquals(1.5f, reopened.pressureMultiplier, 1e-6f)
    }

    @Test
    fun `applying a preset writes both numbers and the next stroke reads them`() {
        val state = policy("apply.properties")
        state.beginPresetSave()
        state.typePresetName("Light hand")
        state.commitPresetSave()
        // The sliders rewrite the live preset, which is the one just saved.
        state.chooseMinimumPressure(0.2f)
        state.choosePressureMultiplier(1.5f)

        state.applyPenPreset(DesktopPenPreset.DEFAULT_ID)
        assertEquals(PressureCurve.MINIMUM_PRESSURE_DEFAULT, state.minimumPressure, 1e-6f)
        assertEquals(1f, state.pressureMultiplier, 1e-6f)

        state.applyPenPreset("light-hand")
        assertEquals(0.2f, state.minimumPressure, 1e-6f)
        assertEquals(1.5f, state.pressureMultiplier, 1e-6f)
        assertEquals("light-hand", state.selectedPenPresetId)
        // The shared filter the pen's samples go through: the floor, then the multiplier.
        assertEquals("the floor holds a light press", 0.2f, PressureFeel.shape(0.01f), 1e-6f)
        assertEquals("the multiplier thickens a firm one", 0.6f, PressureFeel.shape(0.4f), 1e-6f)
        assertEquals("the selection is the file's answer too", "light-hand", DesktopSettings(file("apply.properties")).selectedPenPresetId)
    }

    @Test
    fun `applying a preset never rewrites the one it leaves behind`() {
        val state = policy("leave.properties")
        state.chooseMinimumPressure(0.4f)
        state.choosePressureMultiplier(2f)
        state.beginPresetSave()
        state.typePresetName("Firm")
        state.commitPresetSave()
        state.chooseMinimumPressure(0.45f)

        state.applyPenPreset(DesktopPenPreset.DEFAULT_ID)

        val firm = state.penPresets.first { it.id == "firm" }
        assertEquals("the preset left behind kept its own floor", 0.45f, firm.minimumPressure, 1e-6f)
        assertEquals(2f, firm.pressureMultiplier, 1e-6f)
    }

    @Test
    fun `moving a slider rewrites the live preset`() {
        val state = policy("live.properties")
        state.beginPresetSave()
        state.typePresetName("Firm")
        state.commitPresetSave()

        state.chooseMinimumPressure(0.3f)
        state.choosePressureMultiplier(3f)

        val stored = DesktopSettings(file("live.properties")).penPresets.first { it.id == "firm" }
        assertEquals("the chip describes the pen it names", 0.3f, stored.minimumPressure, 1e-6f)
        assertEquals(3f, stored.pressureMultiplier, 1e-6f)
    }

    @Test
    fun `saving an existing name rewrites it in place`() {
        val state = policy("overwrite.properties")
        state.chooseMinimumPressure(0.2f)
        state.beginPresetSave()
        state.typePresetName("Light hand")
        state.commitPresetSave()

        state.chooseMinimumPressure(0.6f)
        state.beginPresetSave()
        state.typePresetName("light hand")
        state.commitPresetSave()

        assertEquals("one preset, not two spellings", 2, state.penPresets.size)
        assertEquals(1, state.penPresets.count { it.id == "light-hand" })
        assertEquals("the handle survived", 0.6f, state.penPresets.first { it.id == "light-hand" }.minimumPressure, 1e-6f)
    }

    @Test
    fun `a blank name saves nothing and keeps the field open`() {
        val state = policy("blank.properties")
        state.beginPresetSave()
        state.typePresetName("   ")
        state.commitPresetSave()

        assertEquals(1, state.penPresets.size)
        assertNotNull("the reader was not thrown out of the field", state.presetDraft)
    }

    @Test
    fun `the last preset cannot be deleted and deleting the live one falls back`() {
        val state = policy("delete.properties")
        state.chooseMinimumPressure(0.2f)
        state.choosePressureMultiplier(1.5f)
        state.beginPresetSave()
        state.typePresetName("Light hand")
        state.commitPresetSave()

        state.deletePreset(DesktopPenPreset.DEFAULT_ID)
        assertEquals("the last one stays", listOf("light-hand"), state.penPresets.map { it.id })
        state.deletePreset("light-hand")
        assertEquals("and cannot be deleted", listOf("light-hand"), state.penPresets.map { it.id })
        assertEquals("light-hand", state.selectedPenPresetId)
        assertEquals(0.2f, state.minimumPressure, 1e-6f)
    }

    @Test
    fun `deleting the live preset hands the pen the next preset's numbers`() {
        val state = policy("delete-live.properties")
        state.beginPresetSave()
        state.typePresetName("Light hand")
        state.commitPresetSave()

        // Give Default numbers of its own, then go back to the preset that is about to be deleted.
        state.applyPenPreset(DesktopPenPreset.DEFAULT_ID)
        state.chooseMinimumPressure(0.4f)
        state.choosePressureMultiplier(2f)
        state.applyPenPreset("light-hand")

        state.deletePreset("light-hand")

        assertEquals("only Default is left", listOf(DesktopPenPreset.DEFAULT_ID), state.penPresets.map { it.id })
        assertEquals(DesktopPenPreset.DEFAULT_ID, state.selectedPenPresetId)
        assertEquals("and the filter is Default's", 0.4f, state.minimumPressure, 1e-6f)
        assertEquals(2f, state.pressureMultiplier, 1e-6f)
    }

    @Test
    fun `deleting a non-live preset leaves the filter alone`() {
        val state = policy("delete-other.properties")
        state.beginPresetSave()
        state.typePresetName("Light hand")
        state.commitPresetSave()
        state.applyPenPreset(DesktopPenPreset.DEFAULT_ID)

        state.deletePreset("light-hand")

        assertEquals(DesktopPenPreset.DEFAULT_ID, state.selectedPenPresetId)
        assertEquals(PressureCurve.MINIMUM_PRESSURE_DEFAULT, state.minimumPressure, 1e-6f)
    }

    @Test
    fun `a selected id the file no longer carries falls back to the first preset`() {
        val target = file("ghost.properties")
        DesktopSettings(target).selectedPenPresetId = "ghost"

        val state = DesktopSettingsState(DesktopSettings(target))
        assertEquals(DesktopPenPreset.DEFAULT_ID, state.selectedPenPresetId)
    }

    @Test
    fun `escape closes the name field before it closes the surface`() {
        val state = policy("escape.properties")
        state.openAt(DesktopSettingSection.STYLUS)
        state.beginPresetSave()

        assertEquals(SettingsKeyAction.CANCEL_PRESET, state.keyAction(Key.Escape))
        assertTrue(state.press(Key.Escape))
        assertNull(state.presetDraft)
        assertTrue("the page is still up", state.open)
    }

    @Test
    fun `reset puts the live preset back on the factory numbers`() {
        val state = policy("reset.properties")
        state.chooseMinimumPressure(0.4f)
        state.choosePressureMultiplier(2f)

        state.resetPressureToDefaults()

        assertEquals(PressureCurve.MINIMUM_PRESSURE_DEFAULT, state.minimumPressure, 1e-6f)
        assertEquals(1f, state.pressureMultiplier, 1e-6f)
        val stored = DesktopSettings(file("reset.properties")).penPresets.first()
        assertEquals(
            "the live preset's own entry is rewritten, so the chip names these numbers",
            PressureCurve.MINIMUM_PRESSURE_DEFAULT,
            stored.minimumPressure,
            1e-6f,
        )
        assertEquals(1f, stored.pressureMultiplier, 1e-6f)
        // The shared filter reads the reset values on the next sample.
        assertEquals(PressureCurve.MINIMUM_PRESSURE_DEFAULT, PressureFeel.shape(0f), 1e-6f)
    }

    @Test
    fun `the list round-trips through the stored line`() {
        val presets = listOf(
            DesktopPenPreset("default", "Default", 0.05f, 1f),
            DesktopPenPreset("light-hand", "Light hand", 0.2f, 1.5f),
        )
        assertEquals(presets, decodePenPresets(encodePenPresets(presets)))
    }

    @Test
    fun `one unreadable entry costs that entry, not the list`() {
        val raw = "default;Default;0.05;1.0|broken|light-hand;Light hand;nope;1.5|firm;Firm;0.3;2.0"
        val decoded = decodePenPresets(raw)
        assertEquals(listOf("default", "firm"), decoded.map { it.id })
    }

    @Test
    fun `nothing usable falls back to the factory preset`() {
        assertEquals(DesktopPenPreset.DEFAULT_PRESETS, decodePenPresets(null))
        assertEquals(DesktopPenPreset.DEFAULT_PRESETS, decodePenPresets(""))
        assertEquals(DesktopPenPreset.DEFAULT_PRESETS, decodePenPresets("nonsense"))
    }

    @Test
    fun `stored numbers are clamped to the ranges the rows offer`() {
        val preset = decodePenPresets("wild;Wild;9;99").single()
        assertEquals(PressureCurve.MINIMUM_PRESSURE_MAX, preset.minimumPressure, 1e-6f)
        assertEquals(PressureCurve.MULTIPLIER_MAX, preset.pressureMultiplier, 1e-6f)
    }

    @Test
    fun `a name cannot smuggle a separator into the stored line`() {
        val preset = DesktopPenPreset("pipes", "A|B;C", 0.1f, 1.2f)
        assertEquals(listOf("A B C"), decodePenPresets(encodePenPresets(listOf(preset))).map { it.name })
    }

    @Test
    fun `a second preset with the same name still gets a handle of its own`() {
        val taken = listOf(DesktopPenPreset("light-hand", "Light hand", 0.1f, 1f))
        assertEquals("light-hand-2", uniquePenPresetId(taken, "Light hand"))
        assertEquals("light-hand", uniquePenPresetId(emptyList(), "Light hand"))
    }
}
