package com.mobixournal.ui

import com.mobixournal.render.PageStacker
import com.mobixournal.render.PressureCurve
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.TrapezoidKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `SettingsStore.load` needs an Android `Context`, so what is checked here is the clamping it
 * applies to whatever the pref file holds — the part that keeps a corrupt or legacy value from
 * reaching a palette slot (which requires an in-range width and throws otherwise) or from making
 * the pages popup unreachable.
 */
class SettingsSanitizeTest {

    @Test fun outOfRangePenWidthsAreClamped() {
        val s = AppSettings(penWidths = listOf(-3f, 0f, 999f)).sanitized()
        assertEquals(listOf(PEN_WIDTH_MIN, PEN_WIDTH_MIN, PEN_WIDTH_MAX), s.penWidths)
    }

    @Test fun outOfRangeLastWidthIsClamped() {
        assertEquals(PEN_WIDTH_MAX, AppSettings(lastWidth = 1e6f).sanitized().lastWidth)
        assertEquals(PEN_WIDTH_MIN, AppSettings(lastWidth = -1f).sanitized().lastWidth)
    }

    @Test fun outOfRangeShapeWidthIsClamped() {
        assertEquals(PEN_WIDTH_MAX, AppSettings(shapeWidth = 1e6f).sanitized().shapeWidth)
        assertEquals(PEN_WIDTH_MIN, AppSettings(shapeWidth = -1f).sanitized().shapeWidth)
    }

    @Test fun penAndFigureWidthsDefaultToTheMiddleSlot() {
        val middle = AppSettings.DEFAULT_PEN_WIDTHS[1]
        assertEquals(middle, AppSettings().lastWidth)
        assertEquals(middle, AppSettings().shapeWidth)
        assertEquals(1, AppSettings().defaultShapeSlot)
        // The highlighter is the one tool that keeps the wide slot: it lays a band, not a stroke.
        assertEquals(AppSettings.DEFAULT_PEN_WIDTHS.last(), AppSettings().highlighterWidth)
    }

    @Test fun outOfRangePressureMultiplierIsClamped() {
        assertEquals(
            AppSettings.PRESSURE_MULTIPLIER_MAX,
            AppSettings(pressureMultiplier = 1e6f).sanitized().pressureMultiplier,
        )
        assertEquals(
            AppSettings.PRESSURE_MULTIPLIER_MIN,
            AppSettings(pressureMultiplier = -1f).sanitized().pressureMultiplier,
        )
    }

    @Test fun defaultShapeSlotStaysWithinThePenWidthSlots() {
        assertEquals(0, AppSettings(defaultShapeSlot = -5).sanitized().defaultShapeSlot)
        assertEquals(
            AppSettings().penWidths.lastIndex,
            AppSettings(defaultShapeSlot = 99).sanitized().defaultShapeSlot,
        )
    }

    @Test fun outOfRangeMinimumPressureIsClamped() {
        // The desktop refuses a floor below 0.01 (setMinimumPressure), and above 1 is meaningless.
        assertEquals(
            PressureCurve.MINIMUM_PRESSURE_MIN,
            AppSettings(minimumPressure = 0f).sanitized().minimumPressure,
        )
        assertEquals(
            PressureCurve.MINIMUM_PRESSURE_MAX,
            AppSettings(minimumPressure = 9f).sanitized().minimumPressure,
        )
    }

    @Test fun outOfRangeTrapezoidAnglesAreClamped() {
        // A base angle at 0 or 180 would put a leg parallel to a base, which is no longer a trapezoid.
        val low = AppSettings(trapezoidAngleA = -20f, trapezoidAngleB = 0f).sanitized()
        assertEquals(ShapeBuilder.MIN_TRAPEZOID_ANGLE_DEG.toFloat(), low.trapezoidAngleA)
        assertEquals(ShapeBuilder.MIN_TRAPEZOID_ANGLE_DEG.toFloat(), low.trapezoidAngleB)

        val high = AppSettings(trapezoidAngleA = 999f, trapezoidAngleB = 180f).sanitized()
        assertEquals(ShapeBuilder.MAX_TRAPEZOID_ANGLE_DEG.toFloat(), high.trapezoidAngleA)
        assertEquals(ShapeBuilder.MAX_TRAPEZOID_ANGLE_DEG.toFloat(), high.trapezoidAngleB)
        // In-range angles are left exactly as the user set them.
        val kept = AppSettings(trapezoidAngleA = 70f, trapezoidAngleB = 55f).sanitized()
        assertEquals(70f, kept.trapezoidAngleA)
        assertEquals(55f, kept.trapezoidAngleB)
    }

    @Test fun theTrapezoidShipsAsTheIsoscelesOne() {
        assertEquals(TrapezoidKind.ISOSCELES, AppSettings().trapezoidKind)
    }

    @Test fun pageColumnsStayWithinTheChoicesTheUiOffers() {
        assertEquals(1, AppSettings(pageColumns = 0).sanitized().pageColumns)
        assertEquals(1, AppSettings(pageColumns = -7).sanitized().pageColumns)
        assertEquals(
            PageStacker.COLUMN_CHOICES.last(),
            AppSettings(pageColumns = 99).sanitized().pageColumns,
        )
    }

    @Test fun valuesAlreadyInRangeAreUntouched() {
        val s = AppSettings()
        assertEquals(s, s.sanitized())
    }

    @Test fun thePenPaletteStaysUsableWhateverThePrefHolds() {
        // A blanked or corrupt list must not leave the colour pickers with nothing to pick.
        assertEquals(PEN_COLORS, AppSettings(penColors = emptyList()).sanitized().penColors)
        // Duplicates are dead swatches, so they collapse; a colour of the user's own is **kept** —
        // pruning to the named desktop colours is what used to delete a custom swatch, and its entry
        // in a JSON backup, on the next launch.
        assertEquals(
            listOf(XOPP_BLACK, XOPP_RED, 0xFF445566.toInt()),
            AppSettings(
                penColors = listOf(0x00000000, XOPP_BLACK, XOPP_RED, XOPP_BLACK, 0xFF445566.toInt()),
            ).sanitized().penColors,
        )
        // Past the cap the tail is dropped, but the palette is still the user's own list.
        val many = (1..AppSettings.MAX_PEN_COLORS + 5).map { 0xFF000000.toInt() or it }
        assertEquals(
            many.take(AppSettings.MAX_PEN_COLORS),
            AppSettings(penColors = many).sanitized().penColors,
        )
    }

    @Test fun outOfRangeTableDimensionsAreClamped() {
        val low = AppSettings(tableRows = -5, tableCols = 0).sanitized()
        assertEquals(AppSettings.TABLE_DIMENSION_MIN, low.tableRows)
        assertEquals(AppSettings.TABLE_DIMENSION_MIN, low.tableCols)

        val high = AppSettings(tableRows = 100, tableCols = 999).sanitized()
        assertEquals(AppSettings.TABLE_DIMENSION_MAX, high.tableRows)
        assertEquals(AppSettings.TABLE_DIMENSION_MAX, high.tableCols)
    }

    @Test fun penPresetsAreSanitized() {
        val empty = AppSettings(penPresets = emptyList()).sanitized()
        assertEquals(PenPreset.DEFAULT_PRESETS, empty.penPresets)

        val outOfBounds = AppSettings(
            penPresets = listOf(
                PenPreset("p1", "", -1f, 100f),
            ),
        ).sanitized()
        assertEquals(1, outOfBounds.penPresets.size)
        assertEquals("p1", outOfBounds.penPresets[0].name)
        assertEquals(PressureCurve.MINIMUM_PRESSURE_MIN, outOfBounds.penPresets[0].minimumPressure)
        assertEquals(AppSettings.PRESSURE_MULTIPLIER_MAX, outOfBounds.penPresets[0].pressureMultiplier)
    }

    @Test fun selectedPenPresetIdIsSanitized() {
        val valid = AppSettings(
            penPresets = listOf(PenPreset("p1", "P1", 0.05f, 1.0f)),
            selectedPenPresetId = "p1",
        ).sanitized()
        assertEquals("p1", valid.selectedPenPresetId)

        val invalid = AppSettings(
            penPresets = listOf(PenPreset("p1", "P1", 0.05f, 1.0f)),
            selectedPenPresetId = "non-existent",
        ).sanitized()
        assertEquals("p1", invalid.selectedPenPresetId)
    }
}
