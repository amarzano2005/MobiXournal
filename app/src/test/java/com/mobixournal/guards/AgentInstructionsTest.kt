package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the bytes and discoverable paths actually consumed by agents, not just document lines. */
class AgentInstructionsTest {
    private val rootBudget = 16 * 1024L
    private val chainBudget = 32 * 1024L
    private val skipped = setOf(".git", ".gradle", ".gradle-cache", "build", "node_modules", ".idea")

    @Test
    fun rootGuidanceLeavesSpaceForNestedInstructions() {
        val root = repoRoot()
        assertTrue("AGENTS.md must leave half the default Codex budget free; move detail to its owner",
            File(root, "AGENTS.md").length() <= rootBudget)
        val files = root.walkTopDown().onEnter { it.name !in skipped }
            .filter { it.isFile && it.name in setOf("AGENTS.md", "AGENTS.override.md") }.toList()
        for (directory in files.map { it.parentFile }.distinct()) {
            val chain = generateSequence(directory) { if (it == root) null else it.parentFile }
                .toList().reversed().mapNotNull { folder ->
                    listOf("AGENTS.override.md", "AGENTS.md").map { File(folder, it) }
                        .firstOrNull { it.isFile && it.length() > 0 }
                }
            assertTrue("Instruction chain at $directory exceeds Codex's default 32 KiB budget",
                chain.sumOf { it.length() } <= chainBudget)
        }
    }

    @Test
    fun localWorkflowsAreDiscoverableAndTheirLinksResolve() {
        val root = repoRoot()
        for (name in listOf("todo", "new-living-element", "close-task")) {
            val skill = File(root, ".agents/skills/$name/SKILL.md")
            assertTrue("Missing discoverable skill: $skill", skill.isFile)
            assertFalse("Remove the second instruction owner skills/$name", File(root, "skills/$name").exists())
            val text = skill.readText()
            val frontMatter = text.substringAfter("---\n").substringBefore("\n---")
            assertTrue("Skill $name needs a selection description", Regex("(?m)^description:").containsMatchIn(frontMatter))
            assertEquals("Skill name must match its discovery directory", name,
                Regex("(?m)^name: (.+)$").find(frontMatter)?.groupValues?.get(1)?.trim())
            for (match in Regex("\\[[^]\\n]+]\\(([^)]+)\\)").findAll(text)) {
                val target = match.groupValues[1].substringBefore('#')
                if (target.isBlank() || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(target)) continue
                assertTrue("Skill $name has a broken local link: $target", File(skill.parentFile, target).exists())
            }
        }
    }

    @Test
    fun taskInstructionRegressionsRunInCi() {
        val workflow = File(repoRoot(), ".github/workflows/build.yml").readLines()
        assertTrue("CI must execute the operational task checks, not only validate current task data",
            workflow.any { it.trim() == "python3 scripts/todo/test_operational.py" })
        assertTrue("CI must execute the plan coherence rules regression suite",
            workflow.any { it.trim() == "python3 scripts/todo/test_plan_rules.py" })
        assertTrue("CI must execute the todo doctor regression suite",
            workflow.any { it.trim() == "python3 scripts/todo/test_doctor.py" })
    }

    private fun repoRoot(): File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .firstOrNull { File(it, "AGENTS.md").isFile && File(it, "docs").isDirectory }
        ?: error("Cannot find the repository; instruction guards must not pass without reading it")
}
