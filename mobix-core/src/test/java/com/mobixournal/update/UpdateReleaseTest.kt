package com.mobixournal.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release the shells read: which asset is theirs, which text is their version, and when a release is
 * newer than the build in hand.
 *
 * The file is the shape GitHub actually answers with for this project's one release: a tag that names the
 * tablet's build, and two assets — the APK and the window's MSI, whose name carries the window's number.
 */
class UpdateReleaseTest {

    private val release = """
        {
          "tag_name": "v2.0.0",
          "html_url": "https://github.com/amarzano2005/MobiXournal/releases/tag/v2.0.0",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "MobiXournal-1.0.0.msi",
              "size": 72735853,
              "browser_download_url": "https://github.com/amarzano2005/MobiXournal/releases/download/v2.0.0/MobiXournal-1.0.0.msi"
            },
            {
              "name": "MobiXournal.apk",
              "size": 59141870,
              "browser_download_url": "https://github.com/amarzano2005/MobiXournal/releases/download/v2.0.0/MobiXournal.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `each shell reads its own asset and its own version`() {
        val android = parseRelease(release, UpdatePlatform.ANDROID)!!
        assertEquals("MobiXournal.apk", android.assetName)
        assertEquals("the tag is the tablet's version", "2.0.0", android.version)
        assertEquals("https://github.com/amarzano2005/MobiXournal/releases/tag/v2.0.0", android.notesUrl)

        val desktop = parseRelease(release, UpdatePlatform.DESKTOP)!!
        assertEquals("MobiXournal-1.0.0.msi", desktop.assetName)
        assertEquals("the asset's name is the window's version", "1.0.0", desktop.version)
        assertEquals(72735853L, desktop.sizeBytes)
    }

    @Test
    fun `a build in hand is not an update to itself`() {
        assertFalse(isNewer(candidate = "2.0.0", current = "2.0.0"))
        assertFalse(isNewer(candidate = "2.0", current = "2.0.0"))
        assertFalse(isNewer(candidate = "1.0.0", current = "1.0.1"))
    }

    @Test
    fun `a later build is newer place by place`() {
        assertTrue(isNewer(candidate = "2.0.10", current = "2.0.9"))
        assertTrue(isNewer(candidate = "2.1.0", current = "2.0.9"))
        assertTrue(isNewer(candidate = "3", current = "2.9.9"))
    }

    @Test
    fun `unreadable versions never become an offer`() {
        assertFalse(isNewer(candidate = "nightly", current = "2.0.0"))
        assertFalse(isNewer(candidate = "2.0.0", current = "last night"))
    }

    @Test
    fun `a draft, a prerelease or a release without our installer is not offered`() {
        assertNull(parseRelease(release.replace("\"draft\": false", "\"draft\": true"), UpdatePlatform.ANDROID))
        assertNull(parseRelease(release.replace("\"prerelease\": false", "\"prerelease\": true"), UpdatePlatform.DESKTOP))
        assertNull("no APK, no tablet update", parseRelease(release, UpdatePlatform.ANDROID)
            ?.takeIf { it.assetName.endsWith(".msi") })
        assertNull(parseRelease("""{"tag_name": "v2.0.0", "assets": []}""", UpdatePlatform.DESKTOP))
        assertNull(parseRelease("not json", UpdatePlatform.ANDROID))
    }

    @Test
    fun `the asset's name is where the window's number comes from`() {
        assertEquals("1.0.1", assetVersion("MobiXournal-1.0.1.msi"))
        assertNull(assetVersion("MobiXournal.msi"))
        assertTrue(installable(UpdatePlatform.DESKTOP, "MobiXournal-1.0.1.MSI"))
        assertFalse(installable(UpdatePlatform.DESKTOP, "MobiXournal.apk"))
    }
}
