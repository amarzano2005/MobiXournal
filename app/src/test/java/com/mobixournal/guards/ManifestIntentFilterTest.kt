package com.mobixournal.guards

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.io.MOBIX_MIME
import com.mobixournal.io.MOBIX_SUFFIX
import com.mobixournal.io.XOPP_MIME
import com.mobixournal.io.XOPP_SUFFIX
import com.mobixournal.io.saveMimeFor
import com.mobixournal.io.saveNameFor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest is the one place a document's type is written down that the compiler cannot check: it
 * cannot read Kotlin, and nothing at runtime notices that an intent filter and the constant it was
 * meant to match have drifted apart. The failure is silent and specific — the app stops appearing in
 * **Open with** for a document it writes itself, so the file it just saved cannot be tapped open.
 *
 * So the manifest is read here against the code's own constants, for every format the app offers: a
 * rename in `io/SaveTarget.kt` or `MobixFormat` fails this instead of quietly orphaning the
 * registration. Unit tests run with the module directory as their working directory (see
 * [SourceStructureTest]), hence the `src/main` path.
 */
class ManifestIntentFilterTest {

    @Test
    fun everyFormatTheAppWritesIsRegisteredAsADocument() {
        val manifest = manifestText()
        val missing = SaveFormat.entries.flatMap { format ->
            val suffix = saveNameFor("notes", format).substringAfterLast('.')
            listOf(
                "the type ${saveMimeFor(format)}" to ("""android:mimeType="${saveMimeFor(format)}""" in manifest),
                "the .$suffix extension" to declaresExtension(manifest, suffix),
            )
        }.filterNot { (_, declared) -> declared }

        assertTrue(
            "these documents could not be opened by tapping them — add the intent filter in" +
                " AndroidManifest.xml:\n  " + missing.joinToString("\n  ") { it.first },
            missing.isEmpty(),
        )
    }

    /** A registered type has to be the one the app actually writes, not merely a string that looks like it. */
    @Test
    fun theRegisteredTypesAreTheFormatsOwnTypes() {
        assertEquals("the `.mobix` type is the archive's own declared one", MobixFormat.MIMETYPE, MOBIX_MIME)
        assertEquals("and its suffix is the one the format writes", ".mobix", MOBIX_SUFFIX)
        assertEquals("a .xopp suffix is unchanged", ".xopp", XOPP_SUFFIX)
        assertNotEquals("the two document types are distinct", XOPP_MIME, MOBIX_MIME)
    }

    /** Whether some `pathPattern` in [manifest] ends in `.<suffix>`, whatever its escaping. */
    private fun declaresExtension(manifest: String, suffix: String): Boolean =
        Regex("""android:pathPattern="[^"]*\.${Regex.escape(suffix)}""").containsMatchIn(manifest)

    /** A save proposes a name that carries the suffix of the format it is about to write. */
    @Test
    fun theNameASaveProposesCarriesItsFormatsSuffix() {
        assertEquals("notes.mobix", saveNameFor("notes.xopp", SaveFormat.MOBIX))
        assertEquals("notes.xopp", saveNameFor("notes.mobix", SaveFormat.ORIGINAL))
        assertEquals("notes.xopp", saveNameFor("notes.mobix", SaveFormat.ZIPPED))
        assertEquals("Untitled.mobix", saveNameFor("   ", SaveFormat.MOBIX))
    }

    /** The manifest as the build sees it, from the module directory the tests run in. */
    private fun manifestText(): String {
        val candidates = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        val file = candidates.map(::File).firstOrNull { it.isFile }
            ?: error("no AndroidManifest.xml from ${File(".").absolutePath}")
        return file.readText()
    }
}
