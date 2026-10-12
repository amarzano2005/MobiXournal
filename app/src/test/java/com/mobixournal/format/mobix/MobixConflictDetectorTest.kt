package com.mobixournal.format.mobix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobixConflictDetectorTest {

    @Test
    fun syncthingConflictBeforeExtensionIsRecognised() {
        val filename = "physics_notes.sync-conflict-20261012-143022-7XABCD.mobix"
        val info = MobixConflictDetector.matchConflict(filename)

        assertNotNull(info)
        assertEquals("physics_notes.mobix", info!!.baseName)
        assertEquals(SyncClientKind.SYNCTHING, info.client)
        assertEquals(".sync-conflict-20261012-143022-7XABCD", info.rawConflictMarker)
    }

    @Test
    fun syncthingConflictAfterExtensionIsRecognised() {
        val filename = "lecture1.mobix.sync-conflict-20261012-143022-DEVICE1"
        val info = MobixConflictDetector.matchConflict(filename)

        assertNotNull(info)
        assertEquals("lecture1.mobix", info!!.baseName)
        assertEquals(SyncClientKind.SYNCTHING, info.client)
        assertEquals(".sync-conflict-20261012-143022-DEVICE1", info.rawConflictMarker)
    }

    @Test
    fun nextcloudConflictStandardFormatIsRecognised() {
        val filename = "biology (conflicted copy 2026-10-12 143022).mobix"
        val info = MobixConflictDetector.matchConflict(filename)

        assertNotNull(info)
        assertEquals("biology.mobix", info!!.baseName)
        assertEquals(SyncClientKind.NEXTCLOUD, info.client)
        assertEquals("(conflicted copy 2026-10-12 143022)", info.rawConflictMarker)
    }

    @Test
    fun nextcloudConflictNamedUserFormatIsRecognised() {
        val filename = "math_notes (conflicted copy tablet-amarz 2026-10-12).mobix"
        val info = MobixConflictDetector.matchConflict(filename)

        assertNotNull(info)
        assertEquals("math_notes.mobix", info!!.baseName)
        assertEquals(SyncClientKind.NEXTCLOUD, info.client)
        assertEquals("(conflicted copy tablet-amarz 2026-10-12)", info.rawConflictMarker)
    }

    @Test
    fun normalFilenamesAreNotFlaggedAsConflicts() {
        assertNull(MobixConflictDetector.matchConflict("notes.mobix"))
        assertNull(MobixConflictDetector.matchConflict("my_document.xopp"))
        assertNull(MobixConflictDetector.matchConflict("sync-conflict.mobix")) // missing timestamp & device
        assertNull(MobixConflictDetector.matchConflict("conflict_copy.mobix"))
        assertNull(MobixConflictDetector.matchConflict("copy (1).mobix"))
    }

    @Test
    fun isConflictForMatchesTargetCanonicalName() {
        val canonical = "chemistry.mobix"
        assertTrue(MobixConflictDetector.isConflictFor(canonical, "chemistry.sync-conflict-20261012-120000-ABC.mobix"))
        assertTrue(MobixConflictDetector.isConflictFor(canonical, "chemistry (conflicted copy 2026-10-12 120000).mobix"))

        // Does not match other files
        assertFalse(MobixConflictDetector.isConflictFor(canonical, "physics.sync-conflict-20261012-120000-ABC.mobix"))
        assertFalse(MobixConflictDetector.isConflictFor(canonical, "chemistry.mobix"))
    }

    @Test
    fun findConflictSiblingsFiltersCorrectly() {
        val canonical = "project_plan.mobix"
        val directoryContents = listOf(
            "project_plan.mobix",
            "project_plan.sync-conflict-20261012-090000-DEV1.mobix",
            "project_plan (conflicted copy 2026-10-12 090500).mobix",
            "project_plan_notes.mobix",
            "other.sync-conflict-20261012-090000-DEV1.mobix",
            "readme.txt",
        )

        val conflicts = MobixConflictDetector.findConflictSiblings(canonical, directoryContents)
        assertEquals(
            listOf(
                "project_plan.sync-conflict-20261012-090000-DEV1.mobix",
                "project_plan (conflicted copy 2026-10-12 090500).mobix",
            ),
            conflicts,
        )
    }
}
