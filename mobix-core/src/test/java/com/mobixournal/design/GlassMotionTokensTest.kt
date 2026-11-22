package com.mobixournal.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the shared [DesignTokens.GlassMotion] role specs and anchor slide direction rules.
 */
class GlassMotionTokensTest {

    @Test
    fun `every surface role spec matches its constants and avoids splash bounce`() {
        val popover = DesignTokens.GlassMotion.spec(DesignTokens.GlassMotion.SurfaceRole.POPOVER)
        assertEquals(DesignTokens.GlassMotion.START_SCALE, popover.startScale, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.POPOVER_SLIDE_DP, popover.slideDp, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.OPEN_DAMPING_RATIO, popover.openDamping, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.OPEN_STIFFNESS, popover.openStiffness, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.FADE_IN_MS, popover.fadeInMs)

        val sheet = DesignTokens.GlassMotion.spec(DesignTokens.GlassMotion.SurfaceRole.SHEET)
        assertEquals(DesignTokens.GlassMotion.SHEET_START_SCALE, sheet.startScale, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.SHEET_SLIDE_DP, sheet.slideDp, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.SHEET_OPEN_DAMPING_RATIO, sheet.openDamping, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.SHEET_OPEN_STIFFNESS, sheet.openStiffness, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.SHEET_FADE_IN_MS, sheet.fadeInMs)

        val dialog = DesignTokens.GlassMotion.spec(DesignTokens.GlassMotion.SurfaceRole.DIALOG)
        assertEquals(DesignTokens.GlassMotion.DIALOG_START_SCALE, dialog.startScale, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.DIALOG_SLIDE_DP, dialog.slideDp, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.DIALOG_OPEN_DAMPING_RATIO, dialog.openDamping, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.DIALOG_OPEN_STIFFNESS, dialog.openStiffness, 1e-4f)
        assertEquals(DesignTokens.GlassMotion.DIALOG_FADE_IN_MS, dialog.fadeInMs)

        for (role in DesignTokens.GlassMotion.SurfaceRole.entries) {
            val s = DesignTokens.GlassMotion.spec(role)
            assertTrue("$role open damping must prevent splash bounce", s.openDamping >= 0.95f)
            assertTrue("$role close damping must be critically damped", s.closeDamping >= 1.0f)
        }
    }

    @Test
    fun `slide direction sign follows surface role and anchor pivot`() {
        assertEquals(
            1f,
            DesignTokens.GlassMotion.slideDirectionSign(DesignTokens.GlassMotion.SurfaceRole.SHEET, 0f),
            1e-4f,
        )
        assertEquals(
            0f,
            DesignTokens.GlassMotion.slideDirectionSign(DesignTokens.GlassMotion.SurfaceRole.DIALOG, 0.5f),
            1e-4f,
        )
        assertEquals(
            -1f,
            DesignTokens.GlassMotion.slideDirectionSign(DesignTokens.GlassMotion.SurfaceRole.POPOVER, 0f),
            1e-4f,
        )
        assertEquals(
            1f,
            DesignTokens.GlassMotion.slideDirectionSign(DesignTokens.GlassMotion.SurfaceRole.POPOVER, 1f),
            1e-4f,
        )
        assertEquals(
            0f,
            DesignTokens.GlassMotion.slideDirectionSign(DesignTokens.GlassMotion.SurfaceRole.POPOVER, 0.5f),
            1e-4f,
        )
    }
}
