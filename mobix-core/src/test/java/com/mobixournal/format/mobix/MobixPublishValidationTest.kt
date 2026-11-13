package com.mobixournal.format.mobix

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The reopen proof a publish runs before replacing a document: the archive opens, the manifest parses,
 * the capability verdict allows a write and every asset hashes to its name (`docs/document-session.md`).
 */
class MobixPublishValidationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun fixture(name: String): File {
        val bytes = javaClass.classLoader!!.getResourceAsStream("fixtures/mobix-v1/$name.mobix")
            ?: error("missing fixture $name")
        return temp.newFile("$name.mobix").apply { bytes.use { writeBytes(it.readBytes()) } }
    }

    @Test
    fun aCleanDocumentPasses() {
        MobixPublishValidation.validate(fixture("03-bundled-assets"), "9.9.9")
        MobixPublishValidation.validate(fixture("18-every-family"), "9.9.9")
    }

    @Test
    fun aFileThatIsNotAnArchiveIsRefused() {
        val file = temp.newFile("broken.mobix").apply { writeText("not a zip") }
        val failure = runCatching { MobixPublishValidation.validate(file, "9.9.9") }.exceptionOrNull()
        assertTrue("a non-archive must not be published: $failure", failure is MobixPublishValidation.Invalid)
    }

    @Test
    fun aDocumentACapabilityForbidsWritingIsRefused() {
        val failure = runCatching {
            MobixPublishValidation.validate(fixture("13-required-capability-unknown"), "9.9.9")
        }.exceptionOrNull()
        assertTrue(
            "a document whose required capability this build lacks must not replace the original: $failure",
            failure is MobixPublishValidation.Invalid,
        )
    }

    @Test
    fun aPortableCopyIsValidatedAsTheMobixItIs() {
        val source = fixture("03-bundled-assets")
        val copy = temp.newFile("handed-on.pmobix").apply { writeBytes(source.readBytes()) }
        MobixPublishValidation.validate(copy, "9.9.9")
    }
}
