package com.mobixournal.format.mobix

import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The merge reads the manifest's parts, not the pages: `pages[]` already carries each page's `id` and
 * `sha256`, so a page only one side changed is adopted **by part** and a page both sides left alone
 * is never decoded at all. Only a page both sides changed is read — the one whose elements have to be
 * merged.
 *
 * A page-by-page side is the case that makes the difference visible: a `.mobix` opened through
 * [MobixDocument.openPaged] answers a page by inflating exactly that page, and a 200-page merge that
 * touches one page must inflate one page per side (`TrackedEntrySource`). The result is asserted
 * equal to what the eager path produces, so "cheaper" cannot quietly mean "different".
 */
class MobixMergeManifestPartsTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** 200 pages, page [edited] replaced by a version carrying one more stroke on the same layer. */
    private fun notebook(file: File, pageCount: Int, edited: Int? = null): File {
        val pages = SyntheticNotebook.pages(pageCount).toMutableList()
        if (edited != null) {
            val page = pages[edited]
            pages[edited] = page.copy(
                layers = listOf(
                    page.layers[0].copy(
                        elements = page.layers[0].elements + listOf(
                            SyntheticNotebook.stroke(500.0, id = "stk_edit"),
                        ),
                    ),
                ),
            )
        }
        SyntheticNotebook.write(file, pages)
        return file
    }

    private class Tracked(val reader: MobixPagedReader, val entries: TrackedEntrySource)

    /** Open [file] page by page, counting what the read actually inflates. */
    private fun openPaged(file: File): Tracked {
        val tracked = TrackedEntrySource(MobixEntrySource.fromFile(file))
        val reader = MobixPagedReader(tracked)
        return Tracked(reader, tracked)
    }

    private fun reads(tracked: Tracked, index: Int): Int =
        tracked.entries.openCount(SyntheticNotebook.entryOf(index))

    @Test
    fun aPageOnlyOneSideChangedIsAdoptedWithoutBeingDecoded() {
        val localFile = notebook(temp.newFile("local.mobix"), pageCount = 200)
        val remoteFile = notebook(temp.newFile("remote.mobix"), pageCount = 200, edited = 149)

        val local = openPaged(localFile)
        val remote = openPaged(remoteFile)
        local.reader.use { _ ->
            remote.reader.use { _ ->
                val result = MobixMergeEngine.merge(
                    local = MobixDocument.openPaged(local.reader),
                    remote = MobixDocument.openPaged(remote.reader),
                    nowMillis = 42L,
                )

                assertEquals(199, result.unchangedPages)
                assertEquals(1, result.elementMergedPages)
                assertEquals(0, result.adoptedLocalPages)
                assertEquals(0, result.adoptedRemotePages)
                assertTrue("the merge must report the remote edit", result.hasRemoteChanges)

                // The page both sides changed — the only one with elements to union — is read once
                // per side. Nothing else is inflated at all.
                assertEquals("local's edited page", 1, reads(local, 149))
                assertEquals("remote's edited page", 1, reads(remote, 149))
                for (index in listOf(0, 1, 100, 198, 199)) {
                    assertEquals("local page $index", 0, reads(local, index))
                    assertEquals("remote page $index", 0, reads(remote, index))
                }

                // The merged page carries both sides' ink …
                val mergedPage = result.mergedDocument.pages[149]
                assertEquals(2, mergedPage.layers[0].elements.size)
                // … and the merged document is the one the eager merge produces.
                val eager = eagerMerge(localFile, remoteFile, 42L).mergedDocument
                assertEquals("manifest", eager.manifest, result.mergedDocument.manifest)
                assertEquals("canvas", eager.canvas, result.mergedDocument.canvas)
                assertEquals("bridge", eager.bridge, result.mergedDocument.bridge)
                assertEquals("payloads", eager.payloads, result.mergedDocument.payloads)
                assertEquals("preserved", eager.preserved, result.mergedDocument.preserved)
                assertEquals("pages", eager.pages, result.mergedDocument.pages)
                assertEquals(200, result.mergedDocument.pages.size)
            }
        }
    }

    @Test
    fun pagesNeitherSideChangedAndPagesOnlyOneSideAddedAreNeverDecoded() {
        val localFile = notebook(temp.newFile("local.mobix"), pageCount = 200)
        // The remote side grew a page at the end and left every other page alone.
        val remotePages: List<Page> = SyntheticNotebook.pages(200) + SyntheticNotebook.pages(1)
            .map { it.copy(id = "pg_new") }
        val remoteFile = temp.newFile("remote.mobix")
        SyntheticNotebook.write(remoteFile, remotePages)

        val local = openPaged(localFile)
        val remote = openPaged(remoteFile)
        local.reader.use { _ ->
            remote.reader.use { _ ->
                val result = MobixMergeEngine.merge(
                    local = MobixDocument.openPaged(local.reader),
                    remote = MobixDocument.openPaged(remote.reader),
                    nowMillis = 42L,
                )

                assertEquals(200, result.unchangedPages)
                assertEquals(1, result.adoptedRemotePages)
                assertEquals(0, result.elementMergedPages)
                assertEquals(201, result.mergedDocument.pages.size)
                // Not one page was inflated by the merge itself: the manifest answered every branch
                // of the plan. (Reading a page out of the merged document is what inflates it, and
                // the count below is taken before the id is looked at.)
                assertEquals(0, reads(local, 0) + reads(local, 199) + reads(remote, 0) + reads(remote, 199))
                assertEquals(0, reads(remote, 200))
                assertEquals("pg_new", result.mergedDocument.pages[200].id)
                // The adopted page is the one the merged document reads from the remote side.
                assertEquals(1, reads(remote, 200))
            }
        }
    }

    /** The merge as it reads its sides from fully materialised documents — the reference result. */
    private fun eagerMerge(localFile: File, remoteFile: File, nowMillis: Long): MobixMergeResult {
        val local = localFile.inputStream().use { MobixDocument.open(MobixContainer.open(it.buffered())) }
        val remote = remoteFile.inputStream().use { MobixDocument.open(MobixContainer.open(it.buffered())) }
        return MobixMergeEngine.merge(local = local, remote = remote, nowMillis = nowMillis)
    }
}
