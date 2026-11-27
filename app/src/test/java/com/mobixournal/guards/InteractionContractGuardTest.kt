package com.mobixournal.guards

import com.mobixournal.render.ElementInteraction
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the unified [ElementInteraction] contract across `mobix-core`, Android, and Desktop:
 *
 * 1. **No tap-to-teleport on pins** (`beginPinConnect` / `joinSelectionToPin` must not be called).
 * 2. **Proximity-only pins** (pins stay hidden at rest and filter through `filterTargetsByProximity`).
 * 3. **Outline-only move for hollow figures** (both shells delegate single-element move grabs to `hitsMoveBody`).
 * 4. **Edge-handle span gating** (X/Y pill handles gate through `showEdgeHandlesForSpan` in both shells).
 * 5. **Exhaustive `Element` coverage** (every sealed `Element` subtype appears in `ElementInteraction.kt` and `ElementInteractionTest.kt`).
 */
class InteractionContractGuardTest {

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "mobix-core/src/main/java/com/mobixournal/render/ElementInteraction.kt").isFile) {
                return dir
            }
            dir = dir.parentFile
        }
        throw AssertionError("Could not locate repo root above ${File("").absolutePath}")
    }

    private fun read(relPath: String): String = File(repoRoot(), relPath).readText()

    @Test
    fun `no shell reintroduces tap-to-teleport on pins`() {
        val androidInput = read("app/src/main/java/com/mobixournal/render/DrawingSurfaceInput.kt")
        val desktopGesture = read("desktop/src/main/java/com/mobixournal/desktop/sheet/SelectionGesture.kt")
        assertTrue("DrawingSurfaceInput.kt must not call beginPinConnect", !androidInput.contains("beginPinConnect("))
        assertTrue("Desktop SelectionGesture.kt must not call joinSelectionToPin", !desktopGesture.contains("joinSelectionToPin("))
    }

    @Test
    fun `pins stay proximity-only and never render at rest`() {
        val androidOverlay = read("app/src/main/java/com/mobixournal/render/CircuitPinOverlay.kt")
        val desktopOverlay = read("desktop/src/main/java/com/mobixournal/desktop/sheet/PageCanvasOverlay.kt")

        assertTrue(
            "CircuitPinOverlay.kt must filter pins via ElementInteraction.filterTargetsByProximity",
            androidOverlay.contains("ElementInteraction.filterTargetsByProximity"),
        )
        assertTrue(
            "CircuitPinOverlay.kt must not render idle selection pins at rest",
            !androidOverlay.contains("drawSelectionPins("),
        )
        assertTrue(
            "Desktop PageCanvasOverlay.kt must filter pins via ElementInteraction.filterTargetsByProximity",
            desktopOverlay.contains("ElementInteraction.filterTargetsByProximity"),
        )
        val paintConnectionBlock = desktopOverlay.substringAfter("private fun DrawScope.paintConnectionMarks")
            .substringBefore("\n/**")
        assertTrue(
            "Desktop paintConnectionMarks must not call drawArmedMarks or drawSelectionPins",
            !paintConnectionBlock.contains("drawArmedMarks(") && !paintConnectionBlock.contains("drawSelectionPins("),
        )
    }

    @Test
    fun `both shells delegate single-element body move grabs to ElementInteraction hitsMoveBody`() {
        val androidController = read("app/src/main/java/com/mobixournal/render/SelectionGestureController.kt")
        val desktopGesture = read("desktop/src/main/java/com/mobixournal/desktop/sheet/SelectionGesture.kt")
        assertTrue(
            "SelectionGestureController.kt must call ElementInteraction.hitsMoveBody",
            androidController.contains("ElementInteraction.hitsMoveBody("),
        )
        assertTrue(
            "Desktop SelectionGesture.kt must call ElementInteraction.hitsMoveBody",
            desktopGesture.contains("ElementInteraction.hitsMoveBody("),
        )
    }

    @Test
    fun `both shells gate X and Y edge handles via ElementInteraction showEdgeHandlesForSpan`() {
        val androidPaint = read("app/src/main/java/com/mobixournal/render/SelectionHandlesPaint.kt")
        val androidResize = read("app/src/main/java/com/mobixournal/render/SelectionHandleResize.kt")
        val desktopOverlay = read("desktop/src/main/java/com/mobixournal/desktop/sheet/SelectionOverlay.kt")

        assertTrue(
            "SelectionHandlesPaint.kt must gate edge handles with ElementInteraction.showEdgeHandlesForSpan",
            androidPaint.contains("ElementInteraction.showEdgeHandlesForSpan("),
        )
        assertTrue(
            "SelectionHandleResize.kt must gate edge handle hits with ElementInteraction.showEdgeHandlesForSpan",
            androidResize.contains("ElementInteraction.showEdgeHandlesForSpan("),
        )
        assertTrue(
            "Desktop SelectionOverlay.kt must gate edge handles with ElementInteraction.showEdgeHandlesForSpan",
            desktopOverlay.contains("ElementInteraction.showEdgeHandlesForSpan("),
        )
    }

    @Test
    fun `every Element subtype is covered in ElementInteraction and its unit test`() {
        val modelDir = File(repoRoot(), "mobix-core/src/main/java/com/mobixournal/format/model")
        val declRegex = Regex("""(?:data\s+class|class|object)\s+([A-Z][A-Za-z0-9_]*)\b""")
        val subtypes = modelDir.listFiles { f -> f.isFile && f.extension == "kt" }.orEmpty()
            .flatMap { file ->
                val text = file.readText()
                Regex("""(?m)^\s*\)\s*:\s*Element\b""").findAll(text).mapNotNull { end ->
                    declRegex.findAll(text.substring(0, end.range.first)).lastOrNull()?.groupValues?.get(1)
                }.toList()
            }
            .distinct()
        assertTrue("Expected Element subtypes in format/model", subtypes.isNotEmpty())
        val contractText = read("mobix-core/src/main/java/com/mobixournal/render/ElementInteraction.kt")
        val testText = read("mobix-core/src/test/java/com/mobixournal/render/ElementInteractionTest.kt")
        val missingInContract = subtypes.filter { !contractText.contains(it) }
        val missingInTests = subtypes.filter { !testText.contains(it) }
        assertTrue("Element subtypes missing from ElementInteraction.kt: $missingInContract", missingInContract.isEmpty())
        assertTrue("Element subtypes missing from ElementInteractionTest.kt: $missingInTests", missingInTests.isEmpty())
        assertTrue(ElementInteraction.PIN_REVEAL_RADIUS_PX > 0f)
    }
}
