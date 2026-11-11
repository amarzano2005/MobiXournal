package com.mobixournal.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The auto-save rule, as the two shells read it (`AutoSavePolicy`).
 *
 * Each case is a document that must **not** be written by itself, because writing it would do something the
 * reader did not ask for: a clean file rewritten, a read-only document overwritten, a notebook given a name
 * the app chose, or a file taken while a transfer was already using it.
 */
class AutoSavePolicyTest {

    @Test
    fun writesOnlyWhenEveryConditionHolds() {
        assertTrue(
            AutoSavePolicy.due(
                enabled = true, edited = true, readOnly = false, hasTarget = true, busy = false,
            ),
        )
    }

    @Test
    fun isOffUntilTheReaderAsksForIt() {
        assertFalse(
            AutoSavePolicy.due(
                enabled = false, edited = true, readOnly = false, hasTarget = true, busy = false,
            ),
        )
    }

    @Test
    fun neverRewritesACleanDocument() {
        assertFalse(
            AutoSavePolicy.due(
                enabled = true, edited = false, readOnly = false, hasTarget = true, busy = false,
            ),
        )
    }

    @Test
    fun leavesAReadOnlyDocumentAlone() {
        assertFalse(
            AutoSavePolicy.due(
                enabled = true, edited = true, readOnly = true, hasTarget = true, busy = false,
            ),
        )
    }

    @Test
    fun neverChoosesAFileName() {
        // No file of its own: only Save may ask where the document lives.
        assertFalse(
            AutoSavePolicy.due(
                enabled = true, edited = true, readOnly = false, hasTarget = false, busy = false,
            ),
        )
    }

    @Test
    fun standsDownWhileATransferOwnsTheFile() {
        assertFalse(
            AutoSavePolicy.due(
                enabled = true, edited = true, readOnly = false, hasTarget = true, busy = true,
            ),
        )
    }
}
