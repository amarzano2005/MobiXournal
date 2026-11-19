package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop command hierarchy: one filled button, one glyph control, one field, one text label.
 *
 * A window that answers "which control is this?" with six paradigms makes the reader learn six looks for
 * one idea. `docs/interface-contract.md` §2 and §6 name the four button variants, and this test holds the
 * source to them: the factories delegate, no file imports a Material button, and a command cannot paint a
 * fill of its own beside the primitive's recipe.
 */
class DesktopTextFeedbackContractTest {
    private val root = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "AGENTS.md").isFile }
    private val source = File(root, "desktop/src/main/java/com/mobixournal/desktop")

    /** Textual labels, tabs, presets and status links: ink and one underline, never a filled box. */
    private val textOwners = mapOf(
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
        textOwners.forEach { (file, names) ->
            val text = File(source, file).readText()
            names.forEach { name ->
                val start = Regex("fun (?:[A-Za-z]+\\.)?" + name + "\\(").find(text)!!.range.first
                val end = text.indexOf("\n}", start).let { if (it < 0) text.length else it }
                val body = text.substring(start, end)
                    .replace(".background(DesktopTheme.panelField)", "") // static selection-bar surface
                    .replace(".background(DesktopTheme.hairline)", "") // static group separator
                    .replace(".background(DesktopTheme.accent, CircleShape)", "") // unsaved document dot
                assertTrue(
                    "$file:$name must delegate to the text recipe",
                    body.contains("DesktopTextAction(") || body.contains("desktopTextFeedback("),
                )
                listOf(".hoverFill(", ".hoverCapsule(", ".background(", ".border(").forEach { forbidden ->
                    assertTrue("$file:$name reintroduced a filled/bordered text state: $forbidden", !body.contains(forbidden))
                }
            }
        }
    }

    @Test fun theTwoCommandsDelegateToTheFourVariantPrimitive() {
        val buttons = File(source, "tools/DesktopButtons.kt").readText()
        val variants = Regex("enum class DesktopButtonVariant\\s*\\{([^}]*)\\}")
            .find(buttons)?.groupValues?.get(1) ?: ""
        listOf("Primary", "Secondary", "Ghost", "Destructive").forEach { variant ->
            assertTrue("$variant is one of the four variants", variants.contains(variant))
        }
        assertTrue("the primitive paints its states in one place", buttons.contains("DesktopTheme.hoverSlot"))
        assertTrue("a focused command wears the accent ring", buttons.contains("border = DesktopTheme.accent") && buttons.contains("borderWidth = 2.dp"))

        val panel = File(source, "tools/PanelButton.kt").readText()
        assertTrue("a dialog's answer is the primitive", panel.contains("DesktopButton("))
        assertTrue("its destructive answer is the refusal red", panel.contains("DesktopButtonVariant.Destructive"))
        assertTrue("the document red is not a destructive button", !panel.contains("documentRed"))

        val inspector = File(source, "tools/InspectorButton.kt").readText()
        assertTrue("the lane's answer is the primitive", inspector.contains("DesktopButton("))
        assertTrue("no label-specific copy is rewritten in the factory", !inspector.contains("if (label =="))

        listOf(panel, inspector).forEachIndexed { index, text ->
            val name = if (index == 0) "PanelButton" else "InspectorButton"
            listOf(".background(", ".border(", ".hoverFill(", ".hoverCapsule(").forEach { forbidden ->
                assertTrue("$name re-paints the primitive's own state: $forbidden", !text.contains(forbidden))
            }
        }
    }

    @Test fun requestedChromeExceptionsKeepTheirOriginalRecipes() {
        val menu = File(source, "shell/DesktopMenu.kt").readText()
        val entry = File(source, "shell/EntryScreen.kt").readText()
        val tabs = File(source, "shell/TabStrip.kt").readText()
        assertTrue("main-menu command chips retain their capsule", menu.contains(".hoverCapsule("))
        assertTrue("entry commands retain their button background", entry.contains("DesktopTheme.tabFillResting"))
        // The strip is the flat band's now: a tab is a control with the panel tone and the 4 dp corner, not
        // a chip of the tablet's glass with an accent rim (`docs/desktop-ui-layout.md` section 1).
        assertTrue(
            "document tabs wear the flat control surface",
            tabs.contains("DesktopTheme.popupSurface") && tabs.contains("DesktopTheme.controlShape"),
        )
        assertTrue(
            "and no longer the tablet's chip glass",
            !tabs.contains("tabEdge(active)") && !tabs.contains("DesktopTheme.tabFill"),
        )
    }

    @Test fun noDesktopCommandUsesAMaterialButtonOrACapsuleState() {
        val offenders = source.walkTopDown().filter { it.extension == "kt" }.filter {
            Regex("import androidx\\.compose\\.material3\\.(Button|TextButton|FilledTonalButton|OutlinedButton|IconButton)\\b")
                .containsMatchIn(it.readText())
        }.map { it.relativeTo(source).path }.toList()
        assertTrue("use DesktopButton/DesktopIconButton for desktop commands: $offenders", offenders.isEmpty())

        listOf(
            "tools/DesktopButtons.kt",
            "tools/DesktopInputs.kt",
            "tools/PanelButton.kt",
            "tools/InspectorButton.kt",
        ).forEach { file ->
            val text = File(source, file).readText()
            assertTrue("$file paints a capsule state layer", !text.contains(".hoverCapsule("))
            assertTrue("$file takes a Material icon instead of the catalogue", !text.contains("material.icons"))
        }
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
