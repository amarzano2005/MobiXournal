package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text commands must use the desktop recipe; menu choices and content selection have different semantics. */
class DesktopTextFeedbackContractTest {
    private val root = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "AGENTS.md").isFile }
    private val source = File(root, "desktop/src/main/java/com/mobixournal/desktop")
    private val owners = mapOf(
        "tools/PanelButton.kt" to listOf("PanelButton"),
        "tools/InspectorButton.kt" to listOf("InspectorButton"),
        "tools/InspectorControls.kt" to listOf("ChoiceRow", "SectionCard"),
        "tools/ScaleneDialogs.kt" to listOf("StepButton", "PresetChip", "TriangleSumRow"),
        "settings/SettingsRows.kt" to listOf("SettingsTextAction", "SettingsCommand", "SettingsPill", "SettingsSectionRow"),
        "shell/PageSetup.kt" to listOf("SetupChip"),
        "sheet/PageSelection.kt" to listOf("PageSelectionBar"),
        "shell/HeaderAudioPill.kt" to listOf("SpeedButton"),
        "shell/DesktopStatus.kt" to listOf("BandDoor", "PdfTextBandNotice"),
        "shell/PdfContentsSidebar.kt" to listOf("SidebarViewSwitch", "ContentsHeaderAction", "ContentsRowAction"),
    )

    @Test fun textFactoriesUseTheSharedUnfilledRecipe() {
        owners.forEach { (file, names) ->
            val text = File(source, file).readText()
            names.forEach { name ->
                val start = Regex("fun (?:[A-Za-z]+\\.)?" + name + "\\(").find(text)!!.range.first
                val end = text.indexOf("\n}", start).let { if (it < 0) text.length else it }
                val body = text.substring(start, end)
                    .replace(".background(DesktopTheme.panelField)", "") // static selection-bar surface
                    .replace(".background(DesktopTheme.hairline)", "") // static group separator
                    .replace(".background(DesktopTheme.accent, CircleShape)", "") // unsaved document dot
                assertTrue("$file:$name must delegate to the text recipe",
                    body.contains("DesktopTextAction(") || body.contains("desktopTextFeedback("))
                listOf(".hoverFill(", ".hoverCapsule(", ".background(", ".border(").forEach { forbidden ->
                    assertTrue("$file:$name reintroduced a filled/bordered text state: $forbidden", !body.contains(forbidden))
                }
            }
        }
    }

    @Test fun requestedChromeExceptionsKeepTheirOriginalCapsules() {
        val menu = File(source, "shell/DesktopMenu.kt").readText()
        val entry = File(source, "shell/EntryScreen.kt").readText()
        val tabs = File(source, "shell/TabStrip.kt").readText()
        assertTrue("main-menu command chips retain their capsule", menu.contains(".hoverCapsule("))
        assertTrue("entry commands retain their button background", entry.contains("DesktopTheme.tabFillResting"))
        assertTrue("document tabs retain their original glass and edge", tabs.contains(".tabEdge(active)") && tabs.contains("DesktopTheme.tabFill"))
    }

    @Test fun noMobileButtonImplementationCanBypassTheDesktopRecipe() {
        val offenders = source.walkTopDown().filter { it.extension == "kt" }.filter {
            Regex("import androidx\\.compose\\.material3\\.(Button|TextButton|FilledTonalButton|OutlinedButton)\\b")
                .containsMatchIn(it.readText())
        }.map { it.relativeTo(source).path }.toList()
        assertTrue("use DesktopTextAction for desktop text commands: $offenders", offenders.isEmpty())
    }
    @Test fun directInteractiveTextCannotBypassTheSharedRecipe() {
        // These four nodes are respectively the shared implementation, a rounded popup entry,
        // an explicitly guarded tooltip-bearing command and a draggable window heading.
        val allowed = setOf("DesktopTextAction", "ContentsMenuRow", "BandDoor", "SettingsPanelHeader")
        val offenders = mutableListOf<String>()
        source.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            Regex("\\bText\\(").findAll(text).forEach { match ->
                val call = callBody(text, match.range.last + 1)
                if (Regex("\\.(clickable|pointerInput|hoverFill|hoverCapsule)(\\(|\\s*\\{)").containsMatchIn(call)) {
                    val owner = Regex("fun (?:[A-Za-z]+\\.)?([A-Za-z]+)\\(")
                        .findAll(text.substring(0, match.range.first)).lastOrNull()?.groupValues?.get(1)
                    if (owner !in allowed) offenders += "${file.relativeTo(source)}:$owner"
                }
            }
        }
        assertTrue("interactive text must use DesktopTextAction: $offenders", offenders.isEmpty())
    }

    private fun callBody(text: String, start: Int): String {
        var depth = 1
        var quoted = false
        var escaped = false
        var at = start
        while (at < text.length && depth > 0) {
            val c = text[at++]
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '(' -> depth++
                ')' -> depth--
            }
        }
        return text.substring(start, at)
    }

}
