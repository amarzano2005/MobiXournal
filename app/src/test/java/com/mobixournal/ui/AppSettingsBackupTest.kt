package com.mobixournal.ui

import com.mobixournal.format.model.LineStyle
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.BarrelDoubleAction
import com.mobixournal.render.MomentumCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsBackupTest {

    @Test
    fun `round-trips default settings`() {
        val original = AppSettings()
        val json = AppSettingsBackup.toJson(original)
        val restored = AppSettingsBackup.fromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun `round-trips customized settings`() {
        val original = AppSettings(
            fingerDraws = true,
            barrelAction = BarrelAction.SELECT,
            barrelDoubleAction = BarrelDoubleAction.REDO,
            showHover = false,
            pressureEnabled = false,
            pressureMultiplier = 1.8f,
            minimumPressure = 0.1f,
            strokePrecision = com.mobixournal.render.StrokePrecision.HIGH,
            recognizeShapes = false,
            pageColumns = 2,
            snapToGrid = true,
            snapRotation = true,
            guideKind = com.mobixournal.render.GuideKind.SETSQUARE,
            penWidths = listOf(1.0f, 2.0f, 4.0f),
            customColor = 0xFF123456.toInt(),
            defaultTool = EditorTool.HIGHLIGHTER,
            penEraserToggleKey = "E",
            handToggleKey = "P",
            momentum = 0.5f,
            momentumCurve = MomentumCurve.LINEAR,
            panSensitivity = 1.5f,
            toolbarPosition = ToolbarPosition.TOP,
            showToolsInTopBar = true,
            penColors = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt()),
            lastColor = 0xFFFF0000.toInt(),
            lastWidth = 2.0f,
            highlighterColor = 0xFF00FF00.toInt(),
            highlighterWidth = 8.5f,
            shapeWidth = 3.0f,
            defaultShapeSlot = 2,
            tableRows = 5,
            tableCols = 4,
            tableHeader = true,
            triangleKind = com.mobixournal.render.TriangleKind.SCALENE,
            scaleneAngleA = 35.0f,
            scaleneAngleB = 55.0f,
            scaleneAngleC = 90.0f,
            shapeOrder = listOf("SQUARE", "TRIANGLE"),
            shapeHidden = setOf("HEXAGON"),
            toolGroupSelections = mapOf("shape" to EditorTool.HEXAGON),
            railOrder = listOf("pen", "color", "eraser"),
            railHidden = setOf("audio"),
            topBarOrder = listOf("line", "spline", "table"),
            topBarHidden = setOf("guides"),
            audioFolderUri = "content://test/folder",
            pageCounterVertical = PageCounterVertical.TOP,
            pageCounterHorizontal = PageCounterHorizontal.LEFT,
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            toolShortcutKeys = mapOf(EditorTool.PEN to "p", EditorTool.ERASER to "e"),
            colorShortcutKeys = mapOf(0xFF000000.toInt() to "1"),
            presets = listOf(ToolPreset("p1", "My Pen", EditorTool.PEN, 0xFF000000.toInt(), 1.0f, LineStyle.DASHED)),
            textImportLimitMb = 10,
            pdfCacheLimitMb = 50,
            hasSeenOnboarding = true,
        )

        val json = AppSettingsBackup.toJson(original)
        val restored = AppSettingsBackup.fromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun `empty json yields default settings`() {
        val restored = AppSettingsBackup.fromJson("{}")
        assertEquals(AppSettings(), restored)
    }

    @Test
    fun `partial json from older version leaves new features as default`() {
        val partialJson = """
            {
                "version": 1,
                "fingerDraws": true,
                "defaultTool": "HAND"
            }
        """.trimIndent()

        val restored = AppSettingsBackup.fromJson(partialJson)
        assertTrue(restored.fingerDraws)
        assertEquals(EditorTool.HAND, restored.defaultTool)
        // All other features remain their defaults:
        assertEquals(AppSettings().showHover, restored.showHover)
        assertEquals(AppSettings().pressureEnabled, restored.pressureEnabled)
        assertEquals(AppSettings().penColors, restored.penColors)
        assertEquals(AppSettings().penWidths, restored.penWidths)
        assertEquals(AppSettings().presets, restored.presets)
    }

    @Test
    fun `future unknown keys are ignored without error`() {
        val futureJson = """
            {
                "version": 99,
                "futureAiAssistant": true,
                "futureHologramMode": "ENABLED",
                "fingerDraws": true,
                "themeMode": "LIGHT"
            }
        """.trimIndent()

        val restored = AppSettingsBackup.fromJson(futureJson)
        assertTrue(restored.fingerDraws)
        assertEquals(ThemeMode.LIGHT, restored.themeMode)
    }

    @Test
    fun `malformed enum falls back to default without crash`() {
        val badEnumJson = """
            {
                "themeMode": "SUPER_DARK_INVALID",
                "defaultTool": "LASER_POINTER"
            }
        """.trimIndent()

        val restored = AppSettingsBackup.fromJson(badEnumJson)
        assertEquals(ThemeMode.SYSTEM, restored.themeMode)
        assertEquals(EditorTool.PEN, restored.defaultTool)
    }

    @Test
    fun `corrupt json string returns fallback without throwing`() {
        val fallback = AppSettings(fingerDraws = true)
        val restored = AppSettingsBackup.fromJson("{ this is not valid json :;;;", fallback)
        assertEquals(fallback, restored)
    }
}
