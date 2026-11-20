package com.mobixournal.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update card's states, as words and as actions: one primary per phase, a skip answer that disappears
 * once it is taken (or when a shell cannot remember one), and a progress line that only states what the
 * server declared. Both shells read this copy, so the platform is the only thing the words vary by.
 */
class UpdateCardCopyTest {

    private fun card(
        phase: UpdatePhase,
        failure: UpdateFailure? = null,
        downloaded: Boolean = false,
        skipped: String? = null,
        platform: UpdatePlatform = UpdatePlatform.ANDROID,
        canSkip: Boolean = true,
    ) = updateCardFor(
        phase = phase,
        version = "2.3.0",
        current = "2.2.0",
        failure = failure,
        downloaded = downloaded,
        skippedVersion = skipped,
        platform = platform,
        canSkip = canSkip,
    )

    @Test
    fun `the offer names the build, offers the skip, and has one primary`() {
        val copy = card(UpdatePhase.AVAILABLE)

        assertEquals("MobiXournal 2.3.0", copy.title)
        assertEquals("Update available · you have 2.2.0", copy.subtitle)
        assertNull("the offer needs no second sentence", copy.body)
        assertEquals("What's changed in 2.3.0", copy.notes)
        assertEquals(UPDATE_SKIP_LABEL, copy.skip)
        assertEquals(UpdateAction.UPDATE_NOW, copy.primary)
        assertEquals("Update now", copy.primary.label)
        assertEquals("Remind me later", copy.secondary)
    }

    @Test
    fun `a skipped build is not offered a skip it already took`() {
        val copy = card(UpdatePhase.AVAILABLE, skipped = "2.3.0")

        assertNull(copy.skip)
        assertEquals("the rest of the offer is unchanged", UpdateAction.UPDATE_NOW, copy.primary)
    }

    @Test
    fun `a shell that cannot remember a skip does not offer one`() {
        val copy = card(UpdatePhase.AVAILABLE, platform = UpdatePlatform.DESKTOP, canSkip = false)

        assertNull("the window has no skip to put away", copy.skip)
        assertEquals("the offer is otherwise the tablet's", "Update now", copy.primary.label)
        assertEquals("Remind me later", copy.secondary)
    }

    @Test
    fun `a download has one answer, and it is cancel`() {
        val copy = card(UpdatePhase.DOWNLOADING)

        assertEquals(UpdateAction.CANCEL, copy.primary)
        assertEquals("Cancel", copy.primary.label)
        assertNull("nothing competes with the one way out of a download", copy.secondary)
        assertNull("the notes do not sit beside a transfer", copy.notes)
        assertNull("a transfer in flight is not a build to skip", copy.skip)
        assertNull(copy.body)
        assertEquals("Downloading the update", copy.subtitle)
    }

    @Test
    fun `a finished download offers the installer, not a second download`() {
        val copy = card(UpdatePhase.READY, downloaded = true)

        assertEquals("Update downloaded", copy.title)
        assertEquals(UpdateAction.INSTALL, copy.primary)
        assertEquals("Install", copy.primary.label)
        assertEquals("Later", copy.secondary)
        assertEquals(updateDownloadedSentence(UpdatePlatform.ANDROID), copy.body)
        assertNull(copy.skip)
    }

    @Test
    fun `each shell names its own installer, never the other one`() {
        val tablet = card(UpdatePhase.READY, downloaded = true)
        val window = card(UpdatePhase.READY, downloaded = true, platform = UpdatePlatform.DESKTOP)

        assertTrue("the tablet finishes with Android's installer", tablet.body!!.contains("Android's installer"))
        assertFalse("and never promises Windows' one", tablet.body.contains("Windows"))
        assertTrue("the window finishes with Windows' installer", window.body!!.contains("Windows' installer"))
        assertFalse("and never promises Android's one", window.body.contains("Android"))
        assertEquals("the two shells agree on everything else", tablet.subtitle, window.subtitle)
    }

    @Test
    fun `a failed download retries it`() {
        val copy = card(UpdatePhase.FAILED, failure = UpdateFailure.DOWNLOAD)

        assertEquals(UpdateAction.RETRY, copy.primary)
        assertEquals("Try again", copy.primary.label)
        assertEquals("Remind me later", copy.secondary)
        assertEquals("What's changed in 2.3.0", copy.notes)
        assertEquals(updateFailureSentence(UpdateFailure.DOWNLOAD, UpdatePlatform.ANDROID), copy.body)
        assertEquals(UPDATE_SKIP_LABEL, copy.skip)
    }

    @Test
    fun `a refused installer offers the bytes already in hand`() {
        val copy = card(UpdatePhase.FAILED, failure = UpdateFailure.INSTALL, downloaded = true)

        assertEquals("Update downloaded", copy.title)
        assertEquals(UpdateAction.INSTALL, copy.primary)
        assertNull("there is nothing left to download", copy.notes)
        assertNull(copy.skip)
        assertEquals(updateFailureSentence(UpdateFailure.INSTALL, UpdatePlatform.ANDROID), copy.body)
    }

    @Test
    fun `the refused installer is worded for the shell that owns it`() {
        val tablet = updateFailureSentence(UpdateFailure.INSTALL, UpdatePlatform.ANDROID)
        val window = updateFailureSentence(UpdateFailure.INSTALL, UpdatePlatform.DESKTOP)

        assertTrue("the tablet's readers are told what Android needs", tablet.contains("Allow MobiXournal to install apps"))
        assertTrue("the window's readers are told Install asks again", window.startsWith("Windows did not start the installer"))
    }

    @Test
    fun `the About row and the card word a failure the same way`() {
        val copy = card(UpdatePhase.FAILED, failure = UpdateFailure.DOWNLOAD)
        val line = updateStatusLine(
            phase = UpdatePhase.FAILED,
            version = "2.3.0",
            current = "2.2.0",
            failure = UpdateFailure.DOWNLOAD,
            progress = 0f,
            totalBytes = 0L,
            platform = UpdatePlatform.ANDROID,
        )

        assertEquals(copy.body, line)
    }

    @Test
    fun `each shell's idle line describes its own command`() {
        val tablet = updateStatusLine(
            phase = UpdatePhase.IDLE,
            version = null,
            current = "2.2.0",
            failure = null,
            progress = 0f,
            totalBytes = 0L,
            platform = UpdatePlatform.ANDROID,
        )
        val window = updateStatusLine(
            phase = UpdatePhase.IDLE,
            version = null,
            current = "1.0.0",
            failure = null,
            progress = 0f,
            totalBytes = 0L,
            platform = UpdatePlatform.DESKTOP,
        )

        assertEquals("Tap to check for updates.", tablet)
        assertEquals("Checks the project's releases for a newer window.", window)
    }

    @Test
    fun `the launch check does not interrupt for a skipped build`() {
        assertFalse(updateShouldInterrupt(fromLaunch = true, version = "2.3.0", skippedVersion = "2.3.0"))
        assertTrue(updateShouldInterrupt(fromLaunch = true, version = "2.4.0", skippedVersion = "2.3.0"))
        assertTrue(updateShouldInterrupt(fromLaunch = true, version = "2.3.0", skippedVersion = null))
        assertTrue(
            "About's own command exists to reopen a skipped offer",
            updateShouldInterrupt(fromLaunch = false, version = "2.3.0", skippedVersion = "2.3.0"),
        )
    }

    @Test
    fun `progress reads as a percentage and the bytes the server declared`() {
        assertEquals(
            "42% · 12.4 MB of 45.0 MB",
            updateProgressLine(known = true, fraction = 0.42f, received = 13_002_342L, total = 47_185_920L),
        )
    }

    @Test
    fun `a response with no declared length prints no percentage`() {
        assertEquals(
            "Downloading…",
            updateProgressLine(known = false, fraction = 0f, received = 0L, total = 0L),
        )
        assertEquals(
            "Downloading…",
            updateStatusLine(
                phase = UpdatePhase.DOWNLOADING,
                version = "2.3.0",
                current = "2.2.0",
                failure = null,
                progress = 0f,
                totalBytes = 0L,
                platform = UpdatePlatform.DESKTOP,
            ),
        )
    }
}
