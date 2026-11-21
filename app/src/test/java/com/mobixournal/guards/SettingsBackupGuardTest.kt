package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Every setting travels in the backup** (`AppSettingsBackup` on Android, `DesktopSettingsBackup`
 * on Desktop).
 *
 * Adding a preference to `AppSettings` without wiring it into `AppSettingsBackup.toJson` and
 * `AppSettingsBackup.fromNode` silently drops that preference on export/import (as happened with
 * `autoSave` and `ringSlots`). This guard mechanically verifies that every `val` property declared
 * in `data class AppSettings` is both serialized in `toJson` and restored in `fromNode`, and that
 * Desktop's backup continues to snapshot and restore all keys wholesale.
 */
class SettingsBackupGuardTest {

    @Test
    fun everyAppSettingsPropertyIsSerializedInToJson() {
        val props = appSettingsProperties()
        val toJsonBody = backupFunctionBody("fun toJson(")
        val missing = props.filter { prop ->
            !toJsonBody.contains("\\\"$prop\\\":")
        }
        assertTrue(
            "AppSettings properties missing from AppSettingsBackup.toJson(): $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun everyAppSettingsPropertyIsRestoredInFromNode() {
        val props = appSettingsProperties()
        val fromNodeBody = backupFunctionBody("fun fromNode(")
        val assignmentPattern = { prop: String -> Regex("""\b${Regex.escape(prop)}\s*=""") }
        val missing = props.filter { prop ->
            !assignmentPattern(prop).containsMatchIn(fromNodeBody)
        }
        assertTrue(
            "AppSettings properties missing from AppSettingsBackup.fromNode(): $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun desktopBackupSnapshotsAndRestoresAllProperties() {
        val root = repoRoot()
        val desktopBackup = File(
            root,
            "desktop/src/main/java/com/mobixournal/desktop/settings/DesktopSettingsBackup.kt",
        ).readText()
        val desktopSettings = File(
            root,
            "desktop/src/main/java/com/mobixournal/desktop/settings/DesktopSettings.kt",
        ).readText()

        assertTrue(
            "DesktopSettingsBackup.write must export store.snapshot() wholesale",
            desktopBackup.contains("store.snapshot()"),
        )
        assertTrue(
            "DesktopSettings.restore must restore all backup keys wholesale",
            desktopSettings.contains("fun restore(values: Map<String, String>)") &&
                desktopSettings.contains("for ((key, value) in values) properties.setProperty(key, value)"),
        )
    }

    private fun appSettingsProperties(): List<String> {
        val file = File(repoRoot(), "app/src/main/java/com/mobixournal/ui/AppSettings.kt")
        val source = file.readText()
        val header = Regex("""data class AppSettings\s*\((.*?)\)\s*\{""", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("Could not parse data class AppSettings constructor in ${file.path}")
        val props = Regex("""\bval\s+(\w+)\s*:""").findAll(header).map { it.groupValues[1] }.toList()
        assertTrue("AppSettings constructor parser returned too few properties: $props", props.size >= 50)
        return props
    }

    private fun backupFunctionBody(signature: String): String {
        val file = File(repoRoot(), "app/src/main/java/com/mobixournal/ui/AppSettingsBackup.kt")
        val source = file.readText()
        val start = source.indexOf(signature)
        assertTrue("Could not find '$signature' in ${file.path}", start >= 0)
        val end = source.indexOf("\n    }", start)
        return if (end < 0) source.substring(start) else source.substring(start, end)
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
