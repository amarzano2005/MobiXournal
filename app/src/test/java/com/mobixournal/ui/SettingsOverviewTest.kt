package com.mobixournal.ui

import com.mobixournal.BuildConfig
import com.mobixournal.render.QuickAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsOverviewTest {

    @Test
    fun `sectionLiveValue reports accurate readouts including Figures and Gestures`() {
        val defaults = AppSettings()
        assertEquals("1.0x", sectionLiveValue(SettingsSection.STYLUS, defaults))
        assertEquals("Undo / Redo", sectionLiveValue(SettingsSection.GESTURES, defaults))
        assertEquals(defaults.defaultTool.label, sectionLiveValue(SettingsSection.EDITOR, defaults))
        assertEquals("Left", sectionLiveValue(SettingsSection.TOOLBAR, defaults))
        assertEquals("On lift", sectionLiveValue(SettingsSection.FIGURES, defaults))
        assertEquals("${defaults.penColors.size} colors", sectionLiveValue(SettingsSection.COLORS, defaults))
        assertEquals("${ringFaces(defaults).size} faces", sectionLiveValue(SettingsSection.RING, defaults))
        assertEquals("4 bound", sectionLiveValue(SettingsSection.SHORTCUTS, defaults))
        assertEquals("Smooth", sectionLiveValue(SettingsSection.NAVIGATION, defaults))
        assertEquals("System", sectionLiveValue(SettingsSection.APPEARANCE, defaults))
        assertEquals(".mobix", sectionLiveValue(SettingsSection.DOCUMENTS, defaults))
        assertEquals("${defaults.pdfCacheLimitMb} MB", sectionLiveValue(SettingsSection.STORAGE, defaults))
        assertNull(sectionLiveValue(SettingsSection.BACKUP, defaults))
        assertEquals("v${BuildConfig.VERSION_NAME}", sectionLiveValue(SettingsSection.ABOUT, defaults))
    }

    @Test
    fun `sectionLiveValue tracks state changes across Stylus Figures Gestures and Format`() {
        val customized = AppSettings(
            pressureEnabled = false,
            twoFingerTapAction = QuickAction.NONE,
            threeFingerTapAction = QuickAction.TOGGLE_ERASER,
            recognizeShapes = false,
            recognizeOnHold = true,
            newDocumentFormat = NewDocumentFormat.XOPP,
            themeMode = ThemeMode.DARK,
            momentum = 0f,
            toolbarPosition = ToolbarPosition.RIGHT,
        )
        assertEquals("Fixed", sectionLiveValue(SettingsSection.STYLUS, customized))
        assertEquals("Toggle eraser", sectionLiveValue(SettingsSection.GESTURES, customized))
        assertEquals("On hold", sectionLiveValue(SettingsSection.FIGURES, customized))
        assertEquals(".xopp", sectionLiveValue(SettingsSection.DOCUMENTS, customized))
        assertEquals("Dark", sectionLiveValue(SettingsSection.APPEARANCE, customized))
        assertEquals("Crisp", sectionLiveValue(SettingsSection.NAVIGATION, customized))
        assertEquals("Right", sectionLiveValue(SettingsSection.TOOLBAR, customized))

        val noRecognition = customized.copy(recognizeShapes = false, recognizeOnHold = false)
        assertEquals("Off", sectionLiveValue(SettingsSection.FIGURES, noRecognition))
    }
}
