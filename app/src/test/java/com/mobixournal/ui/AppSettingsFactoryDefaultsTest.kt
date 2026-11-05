package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Settings -> Reset` is a reset of the **preferences**, not a reinstall: the welcome tour was
 * answered, and it must not run again because someone put the pen's pressure curve back.
 *
 * The rule lives in [AppSettings.factoryDefaults] rather than in the settings screen, so this suite is
 * what stops it drifting back to `AppSettings()` at the call site.
 */
class AppSettingsFactoryDefaultsTest {

    @Test
    fun resetKeepsTheWelcomeTourAnsweredAndClearsTheRest() {
        val edited = AppSettings(
            hasSeenOnboarding = true,
            dynamicColor = true,
            pressureEnabled = false,
        )

        val reset = edited.factoryDefaults()

        assertTrue("the tour stays answered across a reset", reset.hasSeenOnboarding)
        assertEquals("a boolean the user changed goes back", AppSettings().dynamicColor, reset.dynamicColor)
        assertEquals("a boolean the user changed goes back", AppSettings().pressureEnabled, reset.pressureEnabled)
        assertNotEquals("the edited settings are not their own reset", edited, reset)
        assertEquals(
            "every other field is the factory value",
            AppSettings(),
            reset.copy(hasSeenOnboarding = false),
        )
    }

    @Test
    fun resetOnAnInstallThatNeverSawTheTourStillAsksIt() {
        assertFalse(
            "a reset must not answer the tour on the user's behalf",
            AppSettings().factoryDefaults().hasSeenOnboarding,
        )
    }
}
