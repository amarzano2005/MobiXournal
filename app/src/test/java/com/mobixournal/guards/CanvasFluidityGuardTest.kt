package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet canvas's **frame pacing**, pinned where a regression would actually happen.
 *
 * The window has three arnesi for this (`--diagnose`, `InkProbe`, `DesktopWritingCostTest`); the tablet has
 * the numbers now too ([com.mobixournal.render.FrameWatchdog], one line per second of painting in the
 * pen-diagnostics panel) but no device in CI to run them on. An instrumented timing assertion would therefore
 * measure an emulator, and an emulator's frame budget says nothing about a large tablet — so what is enforced
 * in the build are the **invariants** that hold on every device and whose loss is exactly what makes the
 * canvas stutter:
 *
 *  - **a pointer event never paints.** Painting from an input handler posts two or more buffers per vsync,
 *    and the compositor then shows whichever buffer is newest at each vsync, so a position walks back and
 *    forth instead of advancing — the flicker seen when zoomed in. Every repaint goes through
 *    `requestRender`/`render`, which asks the [android.view.Choreographer] for the next frame;
 *  - **that request is coalesced.** A burst of packets or tiles arriving inside one frame must produce one
 *    paint, which is what `paintPosted` is for; removing the guard re-creates the buffer walk above;
 *  - **a page raster is never produced on the drawing thread.** Rasterising a page costs tens of
 *    milliseconds, so `InkCache.draw` may only blit or say "not cached" — the raster is the worker's;
 *  - **the watchdog is fed from the paint**, with the cache's raster count, or the panel silently stops
 *    saying anything about frames (the same failure mode as a watchdog nobody calls).
 *
 * It reads the source tree because that is where these mistakes are made: none of them is visible at runtime
 * except as "it stutters".
 */
class CanvasFluidityGuardTest {

    private fun source(path: String): String {
        val file = File(repoRoot(), "app/src/main/java/com/mobixournal/render/$path")
        assertTrue("no source at $path — has the file been split?", file.isFile)
        return file.readText()
    }

    @Test
    fun `a pointer event never paints the surface`() {
        // The input half of the canvas: the router, the touch state machine, the hover/barrel paths.
        val input = listOf(
            "DrawingSurfaceInput.kt",
            "DrawingSurfaceRouter.kt",
            "DrawingSurfaceSelection.kt",
            "DrawingSurfaceBarrel.kt",
        )
        for (name in input) {
            val text = source(name)
            assertFalse(
                "$name paints the surface directly: a paint from an event handler posts several buffers per " +
                    "vsync and makes the ink walk back and forth (use requestRender/render)",
                Regex("""(?<![\w.])paint\(\)""").containsMatchIn(text),
            )
        }
    }

    @Test
    fun `a repaint is asked of the choreographer and coalesced to one per frame`() {
        val text = source("DrawingSurfacePaint.kt")
        assertTrue(
            "render() must ask the Choreographer for the next frame",
            text.contains("choreographer.postFrameCallback"),
        )
        assertTrue(
            "render() must return early while a paint is already posted, or a burst paints several buffers " +
                "in one vsync",
            Regex("""if \(paintPosted[^)]*\) return""").containsMatchIn(text),
        )
        assertTrue(
            "the repaint request must be coalesced too (requestRender's renderPosted guard)",
            text.contains("renderPosted.getAndSet(true)"),
        )
        assertTrue(
            "a frame must prefer the hardware canvas: a software canvas rasterises every pixel of the window",
            text.contains("lockHardwareCanvas()"),
        )
    }

    @Test
    fun `a page raster is never produced on the drawing thread`() {
        val text = source("InkCache.kt")
        val draw = functionBody(text, "fun draw(")
        assertFalse(
            "InkCache.draw must not rasterise: it blits or answers \"not cached\" and the worker follows",
            draw.contains("rasterise("),
        )
        assertTrue("and the cache must still be driven by a worker", text.contains("Executors."))
        assertTrue(
            "the raster count the watchdog reads must be published by the worker, not guessed at the frame",
            text.contains("produced.incrementAndGet()"),
        )
    }

    @Test
    fun `the frame watchdog is fed by the paint with the raster count`() {
        val text = source("DrawingSurfacePaint.kt")
        assertTrue(
            "paint() must feed FrameWatchdog, or the panel has no frame numbers at all",
            text.contains("frameWatchdog.painted("),
        )
        assertTrue(
            "and it must carry the ink cache's raster count, the number that shows a per-frame rebuild",
            text.contains("inkCache.rasters"),
        )
    }

    /** The body of the first [signature] in [text], up to the next top-level closing brace. */
    private fun functionBody(text: String, signature: String): String {
        val start = text.indexOf(signature)
        assertTrue("no $signature in the file", start >= 0)
        var depth = 0
        var index = text.indexOf('{', start)
        val opened = index
        while (index < text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(opened, index)
                }
            }
            index++
        }
        return text.substring(opened)
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
