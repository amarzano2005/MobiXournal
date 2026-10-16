package com.mobixournal.ui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Both toolbars draw the **same** `Icons.Outlined` tool set, so they must draw it at the **same size**.
 *
 * These vectors scale their stroke with the icon, which means a smaller glyph is *thinner ink*, not a
 * lighter style: the Secondary Toolbar's compact slots rendered 18dp against the rail's 20dp, and the
 * figures' dock read as a row of thin lines beside the rail's buttons. Everything now takes
 * [ToolIconSize] — half a rail slot — and this test is what keeps a new button from quietly picking its
 * own number.
 *
 * Two halves, because the two failure modes differ: the *constant* must stay half a slot (a value that
 * could otherwise drift), and the *call sites* must read it (a size that could otherwise be written by
 * hand). A `Modifier.size(...)` argument is invisible to any behavioural assertion, so the call sites
 * are read from the source tree, exactly as `ToolbarMenuGuardTest` reads it for `DropdownMenu(`.
 */
class ToolGlyphWeightTest {

    @Test
    fun theToolGlyphIsHalfARailSlot() {
        assertEquals("the rail's slot must stay the token's 40dp", 40.dp, SideToolbarModernSlot)
        assertEquals("half a rail slot", 20.dp, ToolIconSize)
        assertEquals(
            "…and must be derived from the slot, not chosen independently: a second number here is " +
                "how the two bars drifted apart in the first place",
            SideToolbarModernSlot / 2,
            ToolIconSize,
        )
    }

    @Test
    fun everyChromeToolButtonDrawsItsGlyphAtThatSize() {
        val expected = mapOf(
            "EditorRegions.kt" to listOf(
                "CompactSingleToolButton",
                "CompactShapeKindButton",
                "CompactTopBarToolButton",
            ),
            "DrawingGuidePopup.kt" to listOf("CompactGuidePopupButton"),
            "TopBarIndicator.kt" to listOf("ToolIndicatorFace"),
        )
        for ((fileName, functions) in expected) {
            val file = mainSources().singleOrNull { it.name == fileName }
            assertTrue("$fileName must exist in the main source set", file != null)
            val source = file!!.readText()
            for (function in functions) {
                val body = bodyOf(source, function, fileName)
                assertTrue(
                    "$fileName#$function must draw its glyph at ToolIconSize, so the secondary " +
                        "toolbar cannot be thinner than the rail beside it",
                    body.contains("Modifier.size(ToolIconSize)"),
                )
                assertFalse(
                    "$fileName#$function must not hard-code a glyph size of its own",
                    body.contains("Modifier.size(18.dp)"),
                )
            }
        }
    }

    /**
     * The text of `fun [function]`, from its declaration to the next declaration in the file — a
     * function body is what the check needs and the file's top-level functions are separated by a KDoc
     * or a decorator, so cutting at the next `@Composable` / `fun` is exact here. It fails loudly
     * rather than returning an empty slice, which would pass every `assertFalse` vacuously.
     */
    private fun bodyOf(source: String, function: String, fileName: String): String {
        val start = source.indexOf("fun $function(")
        assertTrue("$fileName must define `fun $function(`", start >= 0)
        val rest = source.substring(start)
        val next = listOf("\n@Composable", "\nprivate fun ", "\ninternal fun ", "\nfun ")
            .map { rest.indexOf(it, 1) }
            .filter { it > 0 }
            .minOrNull() ?: rest.length
        val body = rest.substring(0, next)
        assertTrue("$fileName#$function must be non-empty", body.length > 50)
        return body
    }

    /** Every Kotlin source file of the app's main source set. */
    private fun mainSources(): List<File> {
        val root = listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error(
                "no main source set from ${File(".").absolutePath}: this test must run with the " +
                    "module directory (app/) as its working directory",
            )
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
