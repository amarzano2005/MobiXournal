package com.mobixournal.format.mobix

import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Stroke
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The other direction of the CLI's acceptance pair: a document the **command-line writer** produced
 * must open in this app, with the elements and the bundled payload it says it wrote.
 *
 * The generator is not part of this build — it is `python3 -m mobix_cli new` in `tools/mobix-cli/` —
 * so the test is gated rather than skipping silently:
 *
 * ```sh
 * cd tools/mobix-cli
 * python3 -m mobix_cli new /tmp/cli-document.mobix --title "CLI acceptance"
 * MOBIX_CLI_DOCUMENT=/tmp/cli-document.mobix ./gradlew :mobix-core:test --tests '*CliWrittenDocumentTest*'
 * ```
 *
 * The pair's first half lives in the CLI's own suite: `a file MobiXournal saved validates clean`
 * runs `validate` over a fixture this app wrote.
 */
class CliWrittenDocumentTest {

    @Test
    fun aDocumentTheCliWroteOpensHere() {
        val path = System.getenv("MOBIX_CLI_DOCUMENT")
        assumeTrue(
            "MOBIX_CLI_DOCUMENT is unset — generate one with " +
                "`python3 -m mobix_cli new /tmp/cli-document.mobix` and point the variable at that file",
            !path.isNullOrBlank(),
        )
        assertTrue("the generated document is missing: $path", File(path).isFile)

        val bytes = File(path).readBytes()
        val archive = MobixContainer.open(ByteArrayInputStream(bytes))
        val document = MobixDocument.open(archive)

        assertEquals("one page", 1, document.pages.size)
        val page = document.pages.single()
        assertEquals("the sheet's width", 595.275591, page.width, 1e-6)
        val elements = page.layers.flatMap { layer -> layer.elements }
        val stroke = elements.filterIsInstance<Stroke>().single()
        assertEquals("the stroke's two points", 2, stroke.points.size)
        val image = elements.filterIsInstance<ImageElement>().single()
        assertTrue("the bundled picture reached the element", image.data.isNotEmpty())
        assertEquals("one bundled payload", 1, document.assetNames.size)
    }
}
