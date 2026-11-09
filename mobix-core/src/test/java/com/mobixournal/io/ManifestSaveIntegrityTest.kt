package com.mobixournal.io

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.mobix.*
import com.mobixournal.format.model.*
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManifestSaveIntegrityTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun bothEncoderSavePathsPreserveEnvelope() {
        val source = temp.newFile("source.mobix")
        val model = Document(pages = listOf(Page(100.0, 100.0, Background.Solid(-1, "plain"), emptyList())))
        val original = MobixDocument.of(model, "9.0.0", 123)
        val manifest = original.manifest.copy(
            extra = mapOf("futureField" to JsonNode.Str("keep")),
            defaultPaper = Background.Solid(0xFFABCDEF.toInt(), "graph"),
        )
        val notebook = MobixDocument(manifest, original.canvas, original.pages, bridge = original.bridge)
        source.outputStream().use { MobixContainer.save(notebook.encode(123).entries, it) }
        for (incremental in listOf(false, true)) {
            val opened = source.inputStream().use { MobixDocument.open(MobixContainer.open(it)) }
            val out = ByteArrayOutputStream()
            DocumentEncoder.write(
                document = opened.toDocument().copy(title = "edited"), format = SaveFormat.MOBIX,
                target = DocumentEncoder.FileSaveTarget(temp.newFile()), out = out, appVersion = "1.0.0",
                shape = DocumentEncoder.SaveShape(carriedFrom = source, repackOver = source.takeIf { incremental }),
            )
            val back = MobixDocument.open(MobixContainer.open(out.toByteArray().inputStream())).manifest
            assertEquals(123L, back.created)
            assertEquals(manifest.extra, back.extra)
            assertEquals("9.0.0", back.minAppVersion)
            assertEquals("edited", back.title)
            assertEquals(manifest.defaultPaper, back.defaultPaper)
        }
    }

    @Test fun fullSaveCarriesAnAssetUsedOnlyByDefaultPaper() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val sink = MobixAssetSink()
        val asset = requireNotNull(sink.add(payload, MobixFormat.AssetKind.IMAGE))
        val model = Document(pages = listOf(Page(100.0, 100.0, Background.Solid(-1, "plain"), emptyList())))
        val native = MobixDocument.of(model, "1.0.0", 1)
        val paper = Background.Pixmap("absolute", "template.png", bundledAsset = asset)
        val source = temp.newFile("template.mobix")
        val entries = MobixDocument(native.manifest.copy(defaultPaper = paper), native.canvas, native.pages).encode(1).entries +
            MobixContainer.Entry(asset, payload)
        source.outputStream().use { MobixContainer.save(entries, it) }
        val opened = source.inputStream().use { MobixDocument.open(MobixContainer.open(it)) }
        val output = ByteArrayOutputStream()
        DocumentEncoder.write(opened.toDocument(), SaveFormat.MOBIX, null, output, "1.0.0",
            shape = DocumentEncoder.SaveShape(carriedFrom = source))
        val saved = MobixContainer.open(output.toByteArray().inputStream())
        assertArrayEquals(payload, saved.entries.first { it.name == asset }.bytes)
        assertEquals(paper, MobixDocument.open(saved).manifest.defaultPaper)
    }

    @Test fun conversionFromGzipAndFirstSaveNeedNoNativeManifest() {
        val model = Document(pages = listOf(Page(100.0, 100.0, Background.Solid(-1, "plain"), emptyList())))
        val gzip = temp.newFile("source.xopp")
        gzip.outputStream().use { com.mobixournal.format.Xopp.save(model, it) }
        for (source in listOf(gzip, java.io.File(temp.root, "not-created.xopp"))) {
            val output = ByteArrayOutputStream()
            DocumentEncoder.write(model, SaveFormat.MOBIX, null, output, "1.0.0",
                shape = DocumentEncoder.SaveShape(carriedFrom = source))
            assertEquals(1, MobixDocument.open(MobixContainer.open(output.toByteArray().inputStream())).pages.size)
        }
    }
}
