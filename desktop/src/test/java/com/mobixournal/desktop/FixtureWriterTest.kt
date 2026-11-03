package com.mobixournal.desktop

import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File
import org.junit.Test

/**
 * A throwaway: writes the two documents the tab-switch probe opens, under `build-logs/`, so the probe can
 * measure the case a blank tab hides — two real notebooks, one of them 40 archive-backed pages.
 *
 * Not a test of anything: it exists to produce fixtures for `scripts/desktop-tab-switch.ps1`, and it is
 * deleted once they are written.
 */
class FixtureWriterTest {

    @Test
    fun `write the probe's documents`() {
        val out = File("build-logs").apply { mkdirs() }
        SyntheticNotebook.write(
            File(out, "heavy.mobix"),
            (0 until 40).map { index ->
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "ruled"),
                    layers = listOf(Layer((0 until 120).map { SyntheticNotebook.stroke(30.0 + (index + it) % 520) })),
                    id = "pg_heavy_$index",
                )
            },
        )
        println("fixtures: " + File(out, "heavy.mobix").absolutePath)
    }
}
