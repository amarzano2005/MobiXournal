package com.mobixournal.format.mobix.migration

import com.mobixournal.format.mobix.migration.FormatMigration.Checkpoint
import com.mobixournal.format.mobix.migration.FormatMigration.Evidence
import com.mobixournal.format.mobix.migration.FormatMigration.Precondition
import com.mobixournal.format.mobix.migration.FormatMigration.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan model is data, so every claim here is decided without opening a document: what a registry
 * answers, how a chain composes, and that a refused precondition is a diagnostic rather than a crash.
 */
class FormatMigrationTest {

    private fun step(version: Int) = Step(
        toVersion = version,
        description = "rewrite the manifest at $version",
        checkpoint = Checkpoint(version, "the document reopens and every digest matches"),
    )

    private fun migration(
        from: Int,
        to: Int,
        preconditions: List<Precondition> = emptyList(),
        revertible: Boolean = true,
    ) = FormatMigration(
        fromVersion = from,
        toVersion = to,
        preconditions = preconditions,
        steps = (from + 1..to).map(::step),
        revertible = revertible,
    )

    @Test
    fun nothingMovesWhenTheVersionsAreEqual() {
        assertNull("1 -> 1 is not a migration", MigrationRegistry.CURRENT.plan(1, 1))
    }

    @Test
    fun nothingIsRegisteredWhileTheFormatIsAtItsBaseline() {
        assertTrue("the baseline ships no migration", MigrationRegistry.CURRENT.migrations.isEmpty())
        assertNull(
            "1 -> 2 with no registered step is a bump nobody wrote, not silent work",
            MigrationRegistry.CURRENT.plan(1, 2),
        )
        assertNull("an unregistered jump is not invented either", MigrationRegistry.CURRENT.plan(1, 9))
    }

    @Test
    fun aRegisteredMigrationExposesItsStepsInOrder() {
        val registry = MigrationRegistry(listOf(migration(1, 2)))
        val plan = registry.plan(1, 2)

        assertEquals("the plan crosses one version", 1, plan?.fromVersion)
        assertEquals(2, plan?.toVersion)
        assertEquals("one step, producing 2", listOf(2), plan?.steps?.map { it.toVersion })
        assertEquals("its checkpoint follows the step", listOf(2), plan?.steps?.map { it.checkpoint?.version })
        assertTrue("the fake migration is revertible", plan?.revertible == true)
    }

    @Test
    fun twoMigrationsComposeIntoOnePlan() {
        val registry = MigrationRegistry(listOf(migration(1, 2), migration(2, 3, revertible = false)))
        val plan = registry.plan(1, 3)

        assertEquals("both hops in order", listOf(2, 3), plan?.steps?.map { it.toVersion })
        assertFalse("a chain is revertible only when every hop is", plan?.revertible == true)
        assertNull("a target inside a hop is not a plan", registry.plan(1, 4))
    }

    @Test
    fun aRefusedCapabilityIsOneDiagnosticNamingIt() {
        val plan = migration(1, 2, listOf(Precondition.Capability("mobix/table:1")))

        val diagnostics = plan.diagnostics(Evidence())

        assertEquals("one diagnostic per refused precondition", 1, diagnostics.size)
        assertTrue(
            "the diagnostic names the capability: ${diagnostics.single().message}",
            "mobix/table:1" in diagnostics.single().message,
        )
        assertEquals(Precondition.Capability("mobix/table:1"), diagnostics.single().precondition)
    }

    @Test
    fun aForbiddenConstructIsOneDiagnosticNamingIt() {
        val plan = migration(1, 2, listOf(Precondition.Construct("circuit")))

        val diagnostics = plan.diagnostics(Evidence(constructs = setOf("circuit", "stroke")))

        assertEquals(1, diagnostics.size)
        assertTrue(
            "the diagnostic names the construct: ${diagnostics.single().message}",
            "circuit" in diagnostics.single().message,
        )
    }

    @Test
    fun satisfiedPreconditionsProduceNoDiagnostics() {
        val plan = migration(
            1,
            2,
            listOf(
                Precondition.Capability("mobix/table:1"),
                Precondition.Construct("circuit"),
            ),
        )

        val diagnostics = plan.diagnostics(
            Evidence(capabilities = setOf("mobix/table:1"), constructs = setOf("stroke", "text")),
        )

        assertTrue("nothing refused, nothing reported", diagnostics.isEmpty())
    }

    @Test
    fun aPlanWithAGapInItsStepsIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            FormatMigration(
                fromVersion = 1,
                toVersion = 3,
                preconditions = emptyList(),
                steps = listOf(step(2)),
                revertible = true,
            )
        }
    }

    @Test
    fun aCheckpointMustFollowTheStepThatProducedIt() {
        assertThrows(IllegalArgumentException::class.java) {
            FormatMigration(
                fromVersion = 1,
                toVersion = 2,
                preconditions = emptyList(),
                steps = listOf(Step(2, "rewrite", Checkpoint(1, "wrong version"))),
                revertible = true,
            )
        }
    }

    @Test
    fun twoMigrationsOutOfOneVersionAreRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            MigrationRegistry(listOf(migration(1, 2), migration(1, 3)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MigrationRegistry(listOf(migration(1, 3), migration(2, 4)))
        }
    }
}
