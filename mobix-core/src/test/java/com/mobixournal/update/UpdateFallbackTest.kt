package com.mobixournal.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The fallback both shells read when the API refuses the request (`docs/updates.md`): the newest tag from
 * the public feed, and the files the release page lists.
 *
 * The fixtures are the shapes GitHub answers with: an atom feed whose entries each link to their tag, and
 * the expanded asset list, whose links carry both the tag and the file's name.
 */
class UpdateFallbackTest {

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
          <id>tag:github.com,2008:/amarzano2005/MobiXournal/releases</id>
          <title>Release notes from MobiXournal</title>
          <entry>
            <id>tag:github.com,2008:Repository/1/v2.3.0</id>
            <updated>2026-11-17T20:41:00Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/amarzano2005/MobiXournal/releases/tag/v2.3.0"/>
            <title>Release v2.3.0</title>
          </entry>
          <entry>
            <id>tag:github.com,2008:Repository/1/v1.0.0</id>
            <updated>2026-09-02T09:00:00Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/amarzano2005/MobiXournal/releases/tag/v1.0.0"/>
            <title>Release v1.0.0</title>
          </entry>
        </feed>
    """.trimIndent()

    private val assetsPage = """
        <div class="Box Box--condensed">
          <div class="Box-header">
            <a href="/amarzano2005/MobiXournal/releases/tag/v2.3.0">v2.3.0</a>
          </div>
          <ul>
            <li class="Box-row">
              <a href="/amarzano2005/MobiXournal/releases/download/v2.3.0/MobiXournal-1.3.0.msi" rel="nofollow">
                MobiXournal-1.3.0.msi
              </a>
              <span class="text-bold">75.9 MB</span>
            </li>
            <li class="Box-row">
              <a href="/amarzano2005/MobiXournal/releases/download/v2.3.0/MobiXournal-2.3.0.apk" rel="nofollow">
                MobiXournal-2.3.0.apk
              </a>
              <span class="text-bold">56.7 MB</span>
            </li>
          </ul>
        </div>
    """.trimIndent()

    @Test
    fun `the feed's newest tag is the one read`() {
        assertEquals("v2.3.0", UpdateFallback.tagIn(feed))
        // The first entry the feed lists is the newest, which is the one an offer may name.
        assertEquals("v1.0.0", UpdateFallback.tagIn(feed.substringAfter("</entry>")))
        assertNull(UpdateFallback.tagIn("<feed></feed>"))
    }

    @Test
    fun `the release page names both installers and nothing else`() {
        assertEquals(
            listOf("MobiXournal-1.3.0.msi", "MobiXournal-2.3.0.apk"),
            UpdateFallback.assetNamesIn(assetsPage),
        )
        assertEquals(emptyList<String>(), UpdateFallback.assetNamesIn("<html></html>"))
    }

    @Test
    fun `each shell reads its own file and its own number`() {
        val tag = UpdateFallback.tagIn(feed)!!
        val names = UpdateFallback.assetNamesIn(assetsPage)

        val android = UpdateFallback.release(tag, names, UpdatePlatform.ANDROID)!!
        assertEquals("2.3.0", android.version)
        assertEquals("MobiXournal-2.3.0.apk", android.assetName)
        assertEquals(
            "https://github.com/amarzano2005/MobiXournal/releases/download/v2.3.0/MobiXournal-2.3.0.apk",
            android.downloadUrl,
        )
        assertEquals("https://github.com/amarzano2005/MobiXournal/releases/tag/v2.3.0", android.notesUrl)

        val desktop = UpdateFallback.release(tag, names, UpdatePlatform.DESKTOP)!!
        assertEquals("1.3.0", desktop.version)
        assertEquals("MobiXournal-1.3.0.msi", desktop.assetName)
        assertEquals(0L, desktop.sizeBytes)
    }

    @Test
    fun `an unreadable page is not an offer`() {
        assertNull(UpdateFallback.release("v2.3.0", emptyList(), UpdatePlatform.DESKTOP))
        assertNull("no APK, no tablet offer", UpdateFallback.release("v2.3.0", listOf("MobiXournal-1.3.0.msi"), UpdatePlatform.ANDROID))
        assertNull("a tag with no number", UpdateFallback.release("release", UpdateFallback.assetNamesIn(assetsPage), UpdatePlatform.ANDROID))
        assertNull("an asset with no number", UpdateFallback.release("v2.3.0", listOf("MobiXournal.msi"), UpdatePlatform.DESKTOP))
    }

    @Test
    fun `the fallback is the same build the tag names, so the comparison is unchanged`() {
        val found = UpdateFallback.release(
            "v2.3.0", UpdateFallback.assetNamesIn(assetsPage), UpdatePlatform.ANDROID,
        )!!
        assertEquals(false, isNewer(found.version, "2.3.0"))
        assertEquals(true, isNewer(found.version, "2.2.0"))
    }
}
