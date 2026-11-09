package com.mobixournal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundPainterConcurrencyTest {
    @Test fun concurrentWorkersKeepTheirOwnSheetColors() {
        val workers = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()).map { color ->
                workers.submit {
                    val page = Page(100.0, 100.0, Background.Solid(color, "plain"), listOf(Layer(emptyList())))
                    val box = PageStacker.stack(listOf(page), 100, 0f).boxes.single()
                    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        repeat(300) {
                            BackgroundRenderer.draw(canvas, box, 0f, 0f)
                            assertEquals(color, bitmap.getPixel(50, 50))
                        }
                    } finally { bitmap.recycle() }
                }
            }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally { workers.shutdownNow() }
    }
}
