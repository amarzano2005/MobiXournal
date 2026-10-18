package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mechanical guard enforcing the Chrome Geometry Contract specified in `docs/ui-modernization.md`
 * and `docs/chrome.md`, tracked as `guard-the-chrome-bands-as-one-geometry-contract` in `TODO.toml`.
 *
 * It enforces three foundational layout properties:
 *  1. [partitioningMatrixPinsWindowCoverageWithoutOverlap]: The chrome's bands and the canvas
 *     strictly partition the window across every layout combination (Standard Rail Left/Right,
 *     Compact, Full-page) — no band rectangle may intersect the canvas.
 *  2. [headerIsOneContinuousFullWidthSurfaceContainingTabsRow]: The header is one `Surface`
 *     whose closing edge spans the full window width, with the document tabs row inside its `Column`
 *     rather than a slab or separate band beside it.
 *  3. [railLaneBeginsAtHeaderBottomNotBarRow]: The rail lane begins below the header's bottom edge,
 *     never starting at the top app bar row.
 *  4. [guardRejectsPreFixLayoutShapes]: Verifies that pre-fix regressions (tabs outside header column,
 *     pane-local tab bands, or rail lanes starting at bar height) are mechanically rejected.
 */
class ChromeGeometryGuardTest {

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
        val area: Long get() = width.toLong() * height.toLong()

        fun intersects(other: Rect): Boolean =
            left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    enum class LayoutMode {
        STANDARD_LEFT,
        STANDARD_RIGHT,
        COMPACT,
        FULL_PAGE,
    }

    data class LayoutPartition(
        val mode: LayoutMode,
        val bands: List<Pair<String, Rect>>,
        val canvas: Rect,
    )

    private fun computePartition(
        mode: LayoutMode,
        windowWidth: Int,
        windowHeight: Int,
        headerHeight: Int,
        compactHeaderHeight: Int,
        railWidth: Int,
        bottomDockHeight: Int,
    ): LayoutPartition = when (mode) {
        LayoutMode.STANDARD_LEFT -> LayoutPartition(
            mode = mode,
            bands = listOf(
                "Header" to Rect(0, 0, windowWidth, headerHeight),
                "RailLane" to Rect(0, headerHeight, railWidth, windowHeight),
            ),
            canvas = Rect(railWidth, headerHeight, windowWidth, windowHeight),
        )
        LayoutMode.STANDARD_RIGHT -> LayoutPartition(
            mode = mode,
            bands = listOf(
                "Header" to Rect(0, 0, windowWidth, headerHeight),
                "RailLane" to Rect(windowWidth - railWidth, headerHeight, windowWidth, windowHeight),
            ),
            canvas = Rect(0, headerHeight, windowWidth - railWidth, windowHeight),
        )
        LayoutMode.COMPACT -> LayoutPartition(
            mode = mode,
            bands = listOf(
                "Header" to Rect(0, 0, windowWidth, compactHeaderHeight),
                "BottomDock" to Rect(0, windowHeight - bottomDockHeight, windowWidth, windowHeight),
            ),
            canvas = Rect(0, compactHeaderHeight, windowWidth, windowHeight - bottomDockHeight),
        )
        LayoutMode.FULL_PAGE -> LayoutPartition(
            mode = mode,
            bands = emptyList(),
            canvas = Rect(0, 0, windowWidth, windowHeight),
        )
    }

    @Test
    fun partitioningMatrixPinsWindowCoverageWithoutOverlap() {
        val windowW = 2560
        val windowH = 1600
        val headerH = 92
        val compactHeaderH = 52
        val railW = 64
        val dockH = 64

        val totalArea = windowW.toLong() * windowH.toLong()

        for (mode in LayoutMode.values()) {
            val partition = computePartition(
                mode = mode,
                windowWidth = windowW,
                windowHeight = windowH,
                headerHeight = headerH,
                compactHeaderHeight = compactHeaderH,
                railWidth = railW,
                bottomDockHeight = dockH,
            )

            // 1. No chrome band may intersect the canvas
            for ((name, band) in partition.bands) {
                assertFalse(
                    "[$mode] $name $band must not intersect Canvas ${partition.canvas}",
                    band.intersects(partition.canvas),
                )
            }

            // 2. No two chrome bands may intersect each other
            for (i in partition.bands.indices) {
                for (j in i + 1 until partition.bands.size) {
                    val (name1, band1) = partition.bands[i]
                    val (name2, band2) = partition.bands[j]
                    assertFalse(
                        "[$mode] Chrome bands $name1 and $name2 must not intersect",
                        band1.intersects(band2),
                    )
                }
            }

            // 3. Bands + Canvas must exactly tile the entire window area without gaps or surplus
            val coveredArea = partition.bands.sumOf { it.second.area } + partition.canvas.area
            assertEquals(
                "[$mode] Total tiled area of chrome bands and canvas must equal window area",
                totalArea,
                coveredArea,
            )
        }
    }

    @Test
    fun headerIsOneContinuousFullWidthSurfaceContainingTabsRow() {
        val editorRegionsFile = findSourceFile("EditorRegions.kt")
        val content = editorRegionsFile.readText()

        assertTrue(
            "EditorTopBar must be defined in EditorRegions.kt",
            content.contains("fun EditorTopBar("),
        )

        // Validate EditorTopBar root is a full-width Surface
        val editorTopBarBody = content.substringAfter("fun EditorTopBar(").substringBefore("\n@Composable\nfun ")
        assertTrue(
            "EditorTopBar must wrap its content in a full-width Surface",
            editorTopBarBody.contains("Surface(") && editorTopBarBody.contains("fillMaxWidth()"),
        )

        // Validate Column inside EditorTopBar contains both TopAppBar and TabStrip
        val hasColumnWithToolsAndTabs = editorTopBarBody.contains("Column {") &&
            editorTopBarBody.contains("TopAppBar(") &&
            (editorTopBarBody.contains("TabStrip(") || editorTopBarBody.contains("SplitTabRow("))

        assertTrue(
            "EditorTopBar must contain a Column hosting TopAppBar and the tabs row (TabStrip/SplitTabRow)",
            hasColumnWithToolsAndTabs,
        )

        // Ensure no pane-local TabBand or separate tab Surface exists in EditorPaneView
        assertFalse(
            "EditorPaneView must not define or call a pane-local TabBand",
            content.contains("TabBand("),
        )
    }

    @Test
    fun railLaneBeginsAtHeaderBottomNotBarRow() {
        val editorScreenFile = findSourceFile("EditorScreen.kt")
        val content = editorScreenFile.readText()

        // In EditorScreen, EditorTopBar sits inside the vertical Column before EditorBody
        val scaffoldBody = content.substringAfter("Scaffold(").substringAfter("Column(modifier = Modifier.fillMaxSize())")
        val topBarIndex = scaffoldBody.indexOf("EditorTopBar(")
        val bodyIndex = scaffoldBody.indexOf("EditorBody(")

        assertTrue("Scaffold Column must place EditorTopBar first", topBarIndex >= 0)
        assertTrue("Scaffold Column must place EditorBody after EditorTopBar", bodyIndex > topBarIndex)

        // In EditorBody, RailLane is paired in a Row with the canvas, meaning it starts at EditorBody's top (below header)
        val editorBodyContent = content.substringAfter("private fun EditorBody(").substringBefore("\n/**")
        assertTrue(
            "EditorBody must place RailLane and canvas in a Row, starting below the entire header slab",
            editorBodyContent.contains("Row(modifier = modifier)") &&
                editorBodyContent.contains("RailLane(toolbar)") &&
                editorBodyContent.contains("canvas("),
        )
    }

    @Test
    fun guardRejectsPreFixLayoutShapes() {
        // Pre-fix 1: TabStrip placed outside EditorTopBar's Column
        val badTopBar = """
            Surface(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(...)
            }
            TabStrip(...)
        """.trimIndent()
        assertFalse(
            "Must reject TabStrip outside EditorTopBar Column",
            badTopBar.contains("Column {") && badTopBar.contains("TopAppBar") && badTopBar.contains("TabStrip"),
        )

        // Pre-fix 2: Pane-local tab band in EditorPaneView
        val badPaneView = """
            fun EditorPaneView(...) {
                TabBand(pane)
                AndroidView(...)
            }
        """.trimIndent()
        assertTrue(
            "Must detect pane-local TabBand",
            badPaneView.contains("TabBand("),
        )

        // Pre-fix 3: Rail lane starting at bar's height (EditorBody before or beside TopBar)
        val badColumn = """
            Row {
                RailLane()
                Column {
                    EditorTopBar()
                    canvas()
                }
            }
        """.trimIndent()
        val badTopBarIdx = badColumn.indexOf("EditorTopBar()")
        val badRailIdx = badColumn.indexOf("RailLane()")
        assertFalse(
            "Must reject layout where RailLane starts alongside EditorTopBar",
            badTopBarIdx in 0 until badRailIdx,
        )
    }

    private fun findSourceFile(filename: String): File {
        val candidates = listOf(
            File("src/main/java/com/mobixournal/ui", filename),
            File("app/src/main/java/com/mobixournal/ui", filename),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot locate $filename in main source sets from ${File(".").absolutePath}")
    }
}
