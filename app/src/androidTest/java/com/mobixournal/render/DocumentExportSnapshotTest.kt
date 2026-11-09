package com.mobixournal.render

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentExportSnapshotTest {
    @Test fun pngExportsTheSelectedPageWithIndependentResources() {
        val document = Document(pages = listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()).map { color ->
            Page(100.0, 100.0, Background.Solid(color, "plain"), listOf(Layer(emptyList())))
        })
        val snapshot = DocumentExportSnapshot(document, 1, null, emptyMap(), null) { null }
        val output = ByteArrayOutputStream()
        snapshot.writePng(output)
        val bytes = output.toByteArray()
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(200, image.width)
            assertEquals(200, image.height)
            assertEquals(0xFF0000FF.toInt(), image.getPixel(100, 100))
        } finally { image.recycle() }
    }
}
