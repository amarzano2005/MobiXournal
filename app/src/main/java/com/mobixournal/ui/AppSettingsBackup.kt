package com.mobixournal.ui

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.model.LineStyle
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.QuickAction
import com.mobixournal.render.GuideKind
import com.mobixournal.render.MomentumCurve
import com.mobixournal.render.PageSizeStep
import com.mobixournal.render.StrokePrecision
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind

/**
 * Pure Kotlin JSON serialization and deserialization for [AppSettings].
 *
 * Guarantees backward compatibility across versions:
 * - Missing properties in older backups fall back to the default values of [AppSettings].
 * - Malformed values fall back gracefully without failing the entire import.
 * - [AppSettings.sanitized] is always applied to the parsed result.
 *
 * [fromJson] stays deliberately tolerant — it is the reader for a file already known to be a backup.
 * [validate] is the gate in front of it, which **refuses** anything that isn't a settings backup this
 * build can read (see [BackupCheck]), so an import can never quietly load the wrong file or silently
 * drop the settings a newer version wrote.
 *
 * The JSON value model and parser are **not** here: they live in `format/json/` ([JsonNode]), shared
 * with the native `.mobix` container, which is JSON throughout. The writer below is this file's own
 * because a settings backup is written field by field, deliberately pretty and in a fixed order.
 */
object AppSettingsBackup {

    const val CURRENT_VERSION: Int = 1

    /** Serializes [settings] to a formatted JSON string. */
    fun toJson(settings: AppSettings): String {
        val b = StringBuilder()
        b.append("{\n")
        b.append("  \"version\": ").append(CURRENT_VERSION).append(",\n")
        b.append("  \"defaultPageWidthPt\": ").append(settings.defaultPageWidthPt).append(",\n")
        b.append("  \"defaultPageHeightPt\": ").append(settings.defaultPageHeightPt).append(",\n")
        b.append("  \"pageSizeStepPx\": ").append(settings.pageSizeStepPx).append(",\n")
        b.append("  \"fingerDraws\": ").append(settings.fingerDraws).append(",\n")
        b.append("  \"barrelAction\": \"").append(settings.barrelAction.name).append("\",\n")
        b.append("  \"barrelDoubleAction\": \"").append(settings.barrelDoubleAction.name).append("\",\n")
        b.append("  \"showHover\": ").append(settings.showHover).append(",\n")
        b.append("  \"pressureEnabled\": ").append(settings.pressureEnabled).append(",\n")
        b.append("  \"pressureMultiplier\": ").append(settings.pressureMultiplier).append(",\n")
        b.append("  \"minimumPressure\": ").append(settings.minimumPressure).append(",\n")
        b.append("  \"strokePrecision\": \"").append(settings.strokePrecision.name).append("\",\n")
        b.append("  \"recognizeShapes\": ").append(settings.recognizeShapes).append(",\n")
        b.append("  \"snapToGrid\": ").append(settings.snapToGrid).append(",\n")
        b.append("  \"snapRotation\": ").append(settings.snapRotation).append(",\n")
        b.append("  \"guideKind\": \"").append(settings.guideKind.name).append("\",\n")
        b.append("  \"guidePose\": ").append(GuidePoseCodec.toJson(settings.guidePose)).append(",\n")
        b.append("  \"penWidths\": [")
            .append(settings.penWidths.joinToString(", "))
            .append("],\n")
        b.append("  \"customColor\": ").append(settings.customColor?.toString() ?: "null").append(",\n")
        b.append("  \"defaultTool\": \"").append(settings.defaultTool.name).append("\",\n")
        b.append("  \"penEraserToggleKey\": \"").append(escape(settings.penEraserToggleKey)).append("\",\n")
        b.append("  \"handToggleKey\": \"").append(escape(settings.handToggleKey)).append("\",\n")
        b.append("  \"momentum\": ").append(settings.momentum).append(",\n")
        b.append("  \"momentumCurve\": \"").append(settings.momentumCurve.name).append("\",\n")
        b.append("  \"panSensitivity\": ").append(settings.panSensitivity).append(",\n")
        b.append("  \"toolbarPosition\": \"").append(settings.toolbarPosition.name).append("\",\n")

        b.append("  \"stemSatelliteOffsetX\": ").append(settings.stemSatelliteOffsetX).append(",\n")
        b.append("  \"stemSatelliteOffsetY\": ").append(settings.stemSatelliteOffsetY).append(",\n")
        b.append("  \"newDocumentFormat\": \"").append(settings.newDocumentFormat.name).append("\",\n")
        b.append("  \"penColors\": [")
            .append(settings.penColors.joinToString(", "))
            .append("],\n")
        b.append("  \"penFavorites\": [")
            .append(settings.penFavorites.joinToString(", "))
            .append("],\n")
        b.append("  \"highlighterFavorites\": [")
            .append(settings.highlighterFavorites.joinToString(", "))
            .append("],\n")
        b.append("  \"lastColor\": ").append(settings.lastColor).append(",\n")
        b.append("  \"lastWidth\": ").append(settings.lastWidth).append(",\n")
        b.append("  \"highlighterColor\": ").append(settings.highlighterColor).append(",\n")
        b.append("  \"highlighterWidth\": ").append(settings.highlighterWidth).append(",\n")
        b.append("  \"shapeWidth\": ").append(settings.shapeWidth).append(",\n")
        b.append("  \"defaultShapeSlot\": ").append(settings.defaultShapeSlot).append(",\n")
        b.append("  \"tableRows\": ").append(settings.tableRows).append(",\n")
        b.append("  \"tableCols\": ").append(settings.tableCols).append(",\n")
        b.append("  \"tableHeader\": ").append(settings.tableHeader).append(",\n")
        b.append("  \"triangleKind\": \"").append(settings.triangleKind.name).append("\",\n")
        b.append("  \"scaleneAngleA\": ").append(settings.scaleneAngleA).append(",\n")
        b.append("  \"scaleneAngleB\": ").append(settings.scaleneAngleB).append(",\n")
        b.append("  \"scaleneAngleC\": ").append(settings.scaleneAngleC).append(",\n")
        b.append("  \"trapezoidKind\": \"").append(settings.trapezoidKind.name).append("\",\n")
        b.append("  \"trapezoidAngleA\": ").append(settings.trapezoidAngleA).append(",\n")
        b.append("  \"trapezoidAngleB\": ").append(settings.trapezoidAngleB).append(",\n")
        b.append("  \"polygonSides\": ").append(settings.polygonSides).append(",\n")
        b.append("  \"shapeOrder\": [")
            .append(settings.shapeOrder.joinToString(", ") { "\"${escape(it)}\"" })
            .append("],\n")
        b.append("  \"shapeHidden\": [")
            .append(settings.shapeHidden.joinToString(", ") { "\"${escape(it)}\"" })
            .append("],\n")
        b.append("  \"toolGroupSelections\": {")
            .append(settings.toolGroupSelections.entries.joinToString(", ") { "\"${escape(it.key)}\": \"${it.value.name}\"" })
            .append("},\n")
        b.append("  \"railOrder\": [")
            .append(settings.railOrder.joinToString(", ") { "\"${escape(it)}\"" })
            .append("],\n")
        b.append("  \"railHidden\": [")
            .append(settings.railHidden.joinToString(", ") { "\"${escape(it)}\"" })
            .append("],\n")
        b.append("  \"topBarOrder\": [")
            .append(settings.topBarOrder.joinToString(", ") { "\"${escape(it)}\"" })
            .append("],\n")
        b.append("  \"topBarHidden\": [")
            .append(settings.topBarHidden.joinToString(", ") { "\"${escape(it)}\"" })
            .append("],\n")
        b.append("  \"audioFolderUri\": \"").append(escape(settings.audioFolderUri)).append("\",\n")
        b.append("  \"themeMode\": \"").append(settings.themeMode.name).append("\",\n")
        b.append("  \"dynamicColor\": ").append(settings.dynamicColor).append(",\n")
        b.append("  \"toolShortcutKeys\": {")
            .append(settings.toolShortcutKeys.entries.joinToString(", ") { "\"${it.key.name}\": \"${escape(it.value)}\"" })
            .append("},\n")
        b.append("  \"colorShortcutKeys\": {")
            .append(settings.colorShortcutKeys.entries.joinToString(", ") { "\"${it.key}\": \"${escape(it.value)}\"" })
            .append("},\n")
        b.append("  \"presets\": [\n")
        settings.presets.forEachIndexed { i, p ->
            b.append("    {")
                .append("\"id\": \"").append(escape(p.id)).append("\", ")
                .append("\"name\": \"").append(escape(p.name)).append("\", ")
                .append("\"tool\": \"").append(p.tool.name).append("\", ")
                .append("\"colorArgb\": ").append(p.colorArgb).append(", ")
                .append("\"widthPt\": ").append(p.widthPt).append(", ")
                .append("\"lineStyle\": \"").append(p.lineStyle.name).append("\"")
                .append("}")
            if (i < settings.presets.lastIndex) b.append(",")
            b.append("\n")
        }
        b.append("  ],\n")
        b.append("  \"penPresets\": [\n")
        settings.penPresets.forEachIndexed { i, p ->
            b.append("    {")
                .append("\"id\": \"").append(escape(p.id)).append("\", ")
                .append("\"name\": \"").append(escape(p.name)).append("\", ")
                .append("\"minimumPressure\": ").append(p.minimumPressure).append(", ")
                .append("\"pressureMultiplier\": ").append(p.pressureMultiplier)
                .append("}")
            if (i < settings.penPresets.lastIndex) b.append(",")
            b.append("\n")
        }
        b.append("  ],\n")
        b.append("  \"selectedPenPresetId\": \"").append(escape(settings.selectedPenPresetId)).append("\",\n")
        b.append("  \"textImportLimitMb\": ").append(settings.textImportLimitMb).append(",\n")
        b.append("  \"pdfCacheLimitMb\": ").append(settings.pdfCacheLimitMb).append(",\n")
        b.append("  \"twoFingerTapAction\": \"").append(settings.twoFingerTapAction.name).append("\",\n")
        b.append("  \"threeFingerTapAction\": \"").append(settings.threeFingerTapAction.name).append("\",\n")
        b.append("  \"hasSeenOnboarding\": ").append(settings.hasSeenOnboarding).append("\n")
        b.append("}\n")
        return b.toString()
    }

    /** Parses [jsonStr] into [AppSettings], falling back to [fallback] for any missing or invalid fields. */
    fun fromJson(jsonStr: String, fallback: AppSettings = AppSettings()): AppSettings {
        val node = try {
            JsonParser.parse(jsonStr)
        } catch (_: Exception) {
            return fallback
        }
        return fromNode(node, fallback)
    }

    /**
     * Checks a **picked file** before importing it: only a backup this build understands is accepted, so
     * a wrong file — or a newer one whose settings this build would silently drop — is refused with a
     * reason the UI can show ([BackupCheck.message]).
     *
     * A backup is compatible when it is a JSON object carrying this format's version stamp and that
     * version is no newer than what this build writes ([CURRENT_VERSION]). Older and partial backups are
     * welcome: from there the parse is the tolerant one [fromJson] already does, so a missing field falls
     * back to its default instead of failing the import.
     */
    fun validate(jsonStr: String): BackupCheck {
        val node = try {
            JsonParser.parse(jsonStr)
        } catch (_: Exception) {
            return BackupCheck.NotABackup
        }
        val root = node as? JsonNode.Obj ?: return BackupCheck.NotABackup
        val version = (root.map["version"] as? JsonNode.Num)?.value?.toInt()
            ?: return BackupCheck.NotABackup
        if (version > CURRENT_VERSION) return BackupCheck.TooNew(version)
        if (version < 1) return BackupCheck.NotABackup
        return BackupCheck.Ok(fromNode(root))
    }

    /** [fromNode] for an already-parsed JSON value; a non-object root degrades to [fallback]. */
    internal fun fromNode(node: JsonNode, fallback: AppSettings = AppSettings()): AppSettings {
        val root = node as? JsonNode.Obj ?: return fallback

        fun <E : Enum<E>> readEnum(key: String, values: Array<E>, default: E): E {
            val s = root.getStrOrNull(key) ?: return default
            return values.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: default
        }

        val toolGroupSelections = root.getObj("toolGroupSelections")?.map?.mapNotNull { (k, v) ->
            val toolName = (v as? JsonNode.Str)?.value ?: return@mapNotNull null
            val tool = EditorTool.entries.firstOrNull { it.name.equals(toolName, ignoreCase = true) } ?: return@mapNotNull null
            k to tool
        }?.toMap() ?: fallback.toolGroupSelections

        val toolShortcutKeys = root.getObj("toolShortcutKeys")?.map?.mapNotNull { (k, v) ->
            val tool = EditorTool.entries.firstOrNull { it.name.equals(k, ignoreCase = true) } ?: return@mapNotNull null
            val key = (v as? JsonNode.Str)?.value?.take(1) ?: return@mapNotNull null
            if (key.isNotEmpty()) tool to key else null
        }?.toMap() ?: fallback.toolShortcutKeys

        val colorShortcutKeys = root.getObj("colorShortcutKeys")?.map?.mapNotNull { (k, v) ->
            val color = k.toIntOrNull() ?: return@mapNotNull null
            val key = (v as? JsonNode.Str)?.value?.take(1) ?: return@mapNotNull null
            if (key.isNotEmpty()) color to key else null
        }?.toMap() ?: fallback.colorShortcutKeys

        val presets = root.getArr("presets")?.list?.mapNotNull { item ->
            val obj = item as? JsonNode.Obj ?: return@mapNotNull null
            val id = obj.getStrOrNull("id")?.ifEmpty { null } ?: return@mapNotNull null
            val name = obj.getStr("name", id)
            val toolName = obj.getStrOrNull("tool") ?: return@mapNotNull null
            val tool = EditorTool.entries.firstOrNull { it.name.equals(toolName, ignoreCase = true) } ?: return@mapNotNull null
            val color = obj.getIntOrNull("colorArgb") ?: return@mapNotNull null
            val width = obj.getFloatOrNull("widthPt") ?: return@mapNotNull null
            val lineStyleName = obj.getStr("lineStyle", LineStyle.PLAIN.name)
            val lineStyle = LineStyle.entries.firstOrNull { it.name.equals(lineStyleName, ignoreCase = true) } ?: LineStyle.PLAIN
            ToolPreset(id, name, tool, color, width, lineStyle)
        } ?: fallback.presets

        val penPresets = root.getArr("penPresets")?.list?.mapNotNull { item ->
            val obj = item as? JsonNode.Obj ?: return@mapNotNull null
            val id = obj.getStrOrNull("id")?.ifEmpty { null } ?: return@mapNotNull null
            val name = obj.getStr("name", id)
            val minP = obj.getFloatOrNull("minimumPressure") ?: return@mapNotNull null
            val mult = obj.getFloatOrNull("pressureMultiplier") ?: return@mapNotNull null
            PenPreset(id, name, minP, mult)
        } ?: fallback.penPresets

        // A backup from before the taps were configurable carries one on/off switch instead of the
        // two actions: its "off" imports as both taps off, its "on" (or absence) as the fallback.
        val legacyFingerTaps = root.getBool("multiFingerShortcuts", true)

        return AppSettings(
            defaultPageWidthPt = root.getDouble("defaultPageWidthPt", fallback.defaultPageWidthPt),
            defaultPageHeightPt = root.getDouble("defaultPageHeightPt", fallback.defaultPageHeightPt),
            pageSizeStepPx = root.getIntOrNull("pageSizeStepPx")?.let(PageSizeStep::clampPx)
                ?: fallback.pageSizeStepPx,
            fingerDraws = root.getBool("fingerDraws", fallback.fingerDraws),
            barrelAction = readEnum("barrelAction", BarrelAction.values(), fallback.barrelAction),
            barrelDoubleAction = readEnum("barrelDoubleAction", QuickAction.values(), fallback.barrelDoubleAction),
            showHover = root.getBool("showHover", fallback.showHover),
            pressureEnabled = root.getBool("pressureEnabled", fallback.pressureEnabled),
            pressureMultiplier = root.getFloat("pressureMultiplier", fallback.pressureMultiplier),
            minimumPressure = root.getFloat("minimumPressure", fallback.minimumPressure),
            strokePrecision = readEnum("strokePrecision", StrokePrecision.values(), fallback.strokePrecision),
            recognizeShapes = root.getBool("recognizeShapes", fallback.recognizeShapes),
            snapToGrid = root.getBool("snapToGrid", fallback.snapToGrid),
            snapRotation = root.getBool("snapRotation", fallback.snapRotation),
            guideKind = readEnum("guideKind", GuideKind.values(), fallback.guideKind),
            // Presence, not fallback, like `customColor`: an explicit null is "no instrument is
            // posed", which must clear whatever pose the importing device had rather than keep it.
            guidePose = if (root.map.containsKey("guidePose")) {
                GuidePoseCodec.fromNode(root.getObj("guidePose"))
            } else {
                fallback.guidePose
            },
            penWidths = root.getFloatList("penWidths") ?: fallback.penWidths,
            // Presence, not fallback: a backup that carries an explicit null is "the custom slot is
            // empty", which must clear the slot rather than leave the importing device's colour in it.
            customColor = if (root.map.containsKey("customColor")) {
                root.getIntOrNull("customColor")
            } else {
                fallback.customColor
            },
            defaultTool = readEnum("defaultTool", EditorTool.values(), fallback.defaultTool),
            penEraserToggleKey = root.getStr("penEraserToggleKey", fallback.penEraserToggleKey),
            handToggleKey = root.getStr("handToggleKey", fallback.handToggleKey),
            momentum = root.getFloat("momentum", fallback.momentum),
            momentumCurve = readEnum("momentumCurve", MomentumCurve.values(), fallback.momentumCurve),
            panSensitivity = root.getFloat("panSensitivity", fallback.panSensitivity),
            // A backup written while the rail could also dock to the top or the bottom carries one of
            // those retired names, and a *present* retired value has to land on LEFT — the position's own
            // home — rather than on [fallback], which is the position this install happens to use. A file
            // that says nothing about the setting still keeps what it was restored over.
            toolbarPosition = root.getStrOrNull("toolbarPosition")?.let { saved ->
                ToolbarPosition.entries.firstOrNull { it.name.equals(saved, ignoreCase = true) }
                    ?: ToolbarPosition.LEFT
            } ?: fallback.toolbarPosition,

            stemSatelliteOffsetX = root.getFloat("stemSatelliteOffsetX", fallback.stemSatelliteOffsetX),
            stemSatelliteOffsetY = root.getFloat("stemSatelliteOffsetY", fallback.stemSatelliteOffsetY),
            newDocumentFormat = readEnum("newDocumentFormat", NewDocumentFormat.values(), fallback.newDocumentFormat),
            penColors = root.getIntList("penColors") ?: fallback.penColors,
            penFavorites = root.getIntList("penFavorites") ?: fallback.penFavorites,
            highlighterFavorites = root.getIntList("highlighterFavorites") ?: fallback.highlighterFavorites,
            lastColor = root.getInt("lastColor", fallback.lastColor),
            lastWidth = root.getFloat("lastWidth", fallback.lastWidth),
            highlighterColor = root.getInt("highlighterColor", fallback.highlighterColor),
            highlighterWidth = root.getFloat("highlighterWidth", fallback.highlighterWidth),
            shapeWidth = root.getFloat("shapeWidth", fallback.shapeWidth),
            defaultShapeSlot = root.getInt("defaultShapeSlot", fallback.defaultShapeSlot),
            tableRows = root.getInt("tableRows", fallback.tableRows),
            tableCols = root.getInt("tableCols", fallback.tableCols),
            tableHeader = root.getBool("tableHeader", fallback.tableHeader),
            triangleKind = readEnum("triangleKind", TriangleKind.values(), fallback.triangleKind),
            scaleneAngleA = root.getFloat("scaleneAngleA", fallback.scaleneAngleA),
            scaleneAngleB = root.getFloat("scaleneAngleB", fallback.scaleneAngleB),
            scaleneAngleC = root.getFloat("scaleneAngleC", fallback.scaleneAngleC),
            trapezoidKind = readEnum("trapezoidKind", TrapezoidKind.values(), fallback.trapezoidKind),
            trapezoidAngleA = root.getFloat("trapezoidAngleA", fallback.trapezoidAngleA),
            trapezoidAngleB = root.getFloat("trapezoidAngleB", fallback.trapezoidAngleB),
            polygonSides = root.getInt("polygonSides", fallback.polygonSides),
            shapeOrder = root.getStrList("shapeOrder") ?: fallback.shapeOrder,
            shapeHidden = root.getStrList("shapeHidden")?.toSet() ?: fallback.shapeHidden,
            toolGroupSelections = toolGroupSelections,
            railOrder = root.getStrList("railOrder") ?: fallback.railOrder,
            railHidden = root.getStrList("railHidden")?.toSet() ?: fallback.railHidden,
            topBarOrder = root.getStrList("topBarOrder") ?: fallback.topBarOrder,
            topBarHidden = root.getStrList("topBarHidden")?.toSet() ?: fallback.topBarHidden,
            audioFolderUri = root.getStr("audioFolderUri", fallback.audioFolderUri),
            themeMode = readEnum("themeMode", ThemeMode.values(), fallback.themeMode),
            dynamicColor = root.getBool("dynamicColor", fallback.dynamicColor),
            toolShortcutKeys = toolShortcutKeys,
            colorShortcutKeys = colorShortcutKeys,
            presets = presets,
            penPresets = penPresets,
            selectedPenPresetId = root.getStr("selectedPenPresetId", fallback.selectedPenPresetId),
            textImportLimitMb = root.getInt("textImportLimitMb", fallback.textImportLimitMb),
            pdfCacheLimitMb = root.getInt("pdfCacheLimitMb", fallback.pdfCacheLimitMb),
            twoFingerTapAction = readEnum(
                "twoFingerTapAction",
                QuickAction.values(),
                if (legacyFingerTaps) fallback.twoFingerTapAction else QuickAction.NONE,
            ),
            threeFingerTapAction = readEnum(
                "threeFingerTapAction",
                QuickAction.values(),
                if (legacyFingerTaps) fallback.threeFingerTapAction else QuickAction.NONE,
            ),
            hasSeenOnboarding = root.getBool("hasSeenOnboarding", fallback.hasSeenOnboarding),
        ).sanitized()
    }

    private fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }
}

/**
 * The result of checking a picked file before importing it: whether it is a settings backup this build
 * can read, and why not when it isn't. [AppSettingsBackup.validate] is the only producer.
 */
sealed interface BackupCheck {
    /** A compatible backup, already parsed (tolerantly, like [AppSettingsBackup.fromJson]). */
    data class Ok(val settings: AppSettings) : BackupCheck

    /** Not a settings backup at all: unreadable JSON, a non-object root, or no version stamp. */
    data object NotABackup : BackupCheck

    /** A backup written by a **newer** app version, whose settings this build can't read faithfully. */
    data class TooNew(val version: Int) : BackupCheck

    /** A user-facing one-liner for a refused import; empty when [Ok]. */
    val message: String
        get() = when (this) {
            is Ok -> ""
            NotABackup -> "Not a MobiXournal settings backup"
            is TooNew ->
                "Backup from a newer MobiXournal (v$version); " +
                    "this build reads up to v${AppSettingsBackup.CURRENT_VERSION}"
        }
}
