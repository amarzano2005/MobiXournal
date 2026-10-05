package com.mobixournal.ui

import com.mobixournal.format.model.LineStyle
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.BarrelDoubleAction
import com.mobixournal.render.GuideKind
import com.mobixournal.render.MomentumCurve
import com.mobixournal.render.StrokePrecision
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind

/**
 * Pure Kotlin JSON serialization and deserialization for [AppSettings].
 *
 * Guarantees forward and backward compatibility across versions:
 * - Missing properties in older backups fall back to the default values of [AppSettings].
 * - Unknown or future properties from newer backups are ignored.
 * - Malformed values fall back gracefully without failing the entire import.
 * - [AppSettings.sanitized] is always applied to the parsed result.
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
        b.append("  \"fingerDraws\": ").append(settings.fingerDraws).append(",\n")
        b.append("  \"barrelAction\": \"").append(settings.barrelAction.name).append("\",\n")
        b.append("  \"barrelDoubleAction\": \"").append(settings.barrelDoubleAction.name).append("\",\n")
        b.append("  \"showHover\": ").append(settings.showHover).append(",\n")
        b.append("  \"pressureEnabled\": ").append(settings.pressureEnabled).append(",\n")
        b.append("  \"pressureMultiplier\": ").append(settings.pressureMultiplier).append(",\n")
        b.append("  \"minimumPressure\": ").append(settings.minimumPressure).append(",\n")
        b.append("  \"strokePrecision\": \"").append(settings.strokePrecision.name).append("\",\n")
        b.append("  \"recognizeShapes\": ").append(settings.recognizeShapes).append(",\n")
        b.append("  \"pageColumns\": ").append(settings.pageColumns).append(",\n")
        b.append("  \"snapToGrid\": ").append(settings.snapToGrid).append(",\n")
        b.append("  \"snapRotation\": ").append(settings.snapRotation).append(",\n")
        b.append("  \"guideKind\": \"").append(settings.guideKind.name).append("\",\n")
        b.append("  \"penWidths\": [")
            .append(settings.penWidths.joinToString(", "))
            .append("],\n")
        b.append("  \"customColor\": ").append(settings.customColor).append(",\n")
        b.append("  \"defaultTool\": \"").append(settings.defaultTool.name).append("\",\n")
        b.append("  \"penEraserToggleKey\": \"").append(escape(settings.penEraserToggleKey)).append("\",\n")
        b.append("  \"handToggleKey\": \"").append(escape(settings.handToggleKey)).append("\",\n")
        b.append("  \"momentum\": ").append(settings.momentum).append(",\n")
        b.append("  \"momentumCurve\": \"").append(settings.momentumCurve.name).append("\",\n")
        b.append("  \"panSensitivity\": ").append(settings.panSensitivity).append(",\n")
        b.append("  \"toolbarPosition\": \"").append(settings.toolbarPosition.name).append("\",\n")
        b.append("  \"showToolsInTopBar\": ").append(settings.showToolsInTopBar).append(",\n")
        b.append("  \"penColors\": [")
            .append(settings.penColors.joinToString(", "))
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
        b.append("  \"pageCounterVertical\": \"").append(settings.pageCounterVertical.name).append("\",\n")
        b.append("  \"pageCounterHorizontal\": \"").append(settings.pageCounterHorizontal.name).append("\",\n")
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
        b.append("  \"hasSeenOnboarding\": ").append(settings.hasSeenOnboarding).append("\n")
        b.append("}\n")
        return b.toString()
    }

    /** Parses [jsonStr] into [AppSettings], falling back to [fallback] for any missing or invalid fields. */
    fun fromJson(jsonStr: String, fallback: AppSettings = AppSettings()): AppSettings {
        val root = try {
            JsonParser.parse(jsonStr) as? JsonNode.Obj ?: return fallback
        } catch (_: Exception) {
            return fallback
        }

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

        return AppSettings(
            defaultPageWidthPt = root.getDouble("defaultPageWidthPt", fallback.defaultPageWidthPt),
            defaultPageHeightPt = root.getDouble("defaultPageHeightPt", fallback.defaultPageHeightPt),
            fingerDraws = root.getBool("fingerDraws", fallback.fingerDraws),
            barrelAction = readEnum("barrelAction", BarrelAction.values(), fallback.barrelAction),
            barrelDoubleAction = readEnum("barrelDoubleAction", BarrelDoubleAction.values(), fallback.barrelDoubleAction),
            showHover = root.getBool("showHover", fallback.showHover),
            pressureEnabled = root.getBool("pressureEnabled", fallback.pressureEnabled),
            pressureMultiplier = root.getFloat("pressureMultiplier", fallback.pressureMultiplier),
            minimumPressure = root.getFloat("minimumPressure", fallback.minimumPressure),
            strokePrecision = readEnum("strokePrecision", StrokePrecision.values(), fallback.strokePrecision),
            recognizeShapes = root.getBool("recognizeShapes", fallback.recognizeShapes),
            pageColumns = root.getInt("pageColumns", fallback.pageColumns),
            snapToGrid = root.getBool("snapToGrid", fallback.snapToGrid),
            snapRotation = root.getBool("snapRotation", fallback.snapRotation),
            guideKind = readEnum("guideKind", GuideKind.values(), fallback.guideKind),
            penWidths = root.getFloatList("penWidths") ?: fallback.penWidths,
            customColor = root.getInt("customColor", fallback.customColor),
            defaultTool = readEnum("defaultTool", EditorTool.values(), fallback.defaultTool),
            penEraserToggleKey = root.getStr("penEraserToggleKey", fallback.penEraserToggleKey),
            handToggleKey = root.getStr("handToggleKey", fallback.handToggleKey),
            momentum = root.getFloat("momentum", fallback.momentum),
            momentumCurve = readEnum("momentumCurve", MomentumCurve.values(), fallback.momentumCurve),
            panSensitivity = root.getFloat("panSensitivity", fallback.panSensitivity),
            toolbarPosition = readEnum("toolbarPosition", ToolbarPosition.values(), fallback.toolbarPosition),
            showToolsInTopBar = root.getBool("showToolsInTopBar", fallback.showToolsInTopBar),
            penColors = root.getIntList("penColors") ?: fallback.penColors,
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
            shapeOrder = root.getStrList("shapeOrder") ?: fallback.shapeOrder,
            shapeHidden = root.getStrList("shapeHidden")?.toSet() ?: fallback.shapeHidden,
            toolGroupSelections = toolGroupSelections,
            railOrder = root.getStrList("railOrder") ?: fallback.railOrder,
            railHidden = root.getStrList("railHidden")?.toSet() ?: fallback.railHidden,
            topBarOrder = root.getStrList("topBarOrder") ?: fallback.topBarOrder,
            topBarHidden = root.getStrList("topBarHidden")?.toSet() ?: fallback.topBarHidden,
            audioFolderUri = root.getStr("audioFolderUri", fallback.audioFolderUri),
            pageCounterVertical = readEnum("pageCounterVertical", PageCounterVertical.values(), fallback.pageCounterVertical),
            pageCounterHorizontal = readEnum("pageCounterHorizontal", PageCounterHorizontal.values(), fallback.pageCounterHorizontal),
            themeMode = readEnum("themeMode", ThemeMode.values(), fallback.themeMode),
            dynamicColor = root.getBool("dynamicColor", fallback.dynamicColor),
            toolShortcutKeys = toolShortcutKeys,
            colorShortcutKeys = colorShortcutKeys,
            presets = presets,
            penPresets = penPresets,
            selectedPenPresetId = root.getStr("selectedPenPresetId", fallback.selectedPenPresetId),
            textImportLimitMb = root.getInt("textImportLimitMb", fallback.textImportLimitMb),
            pdfCacheLimitMb = root.getInt("pdfCacheLimitMb", fallback.pdfCacheLimitMb),
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

/** Lightweight JSON representation. */
internal sealed interface JsonNode {
    data class Obj(val map: Map<String, JsonNode>) : JsonNode {
        fun getStr(key: String, fallback: String): String = (map[key] as? Str)?.value ?: fallback
        fun getStrOrNull(key: String): String? = (map[key] as? Str)?.value
        fun getBool(key: String, fallback: Boolean): Boolean = (map[key] as? Bool)?.value ?: fallback
        fun getInt(key: String, fallback: Int): Int = (map[key] as? Num)?.value?.toLong()?.toInt() ?: fallback
        fun getIntOrNull(key: String): Int? = (map[key] as? Num)?.value?.toLong()?.toInt()
        fun getFloat(key: String, fallback: Float): Float = (map[key] as? Num)?.value?.toFloat() ?: fallback
        fun getFloatOrNull(key: String): Float? = (map[key] as? Num)?.value?.toFloat()
        fun getDouble(key: String, fallback: Double): Double = (map[key] as? Num)?.value ?: fallback
        fun getObj(key: String): Obj? = map[key] as? Obj
        fun getArr(key: String): Arr? = map[key] as? Arr
        fun getStrList(key: String): List<String>? =
            getArr(key)?.list?.mapNotNull { (it as? Str)?.value }
        fun getFloatList(key: String): List<Float>? =
            getArr(key)?.list?.mapNotNull { (it as? Num)?.value?.toFloat() }
        fun getIntList(key: String): List<Int>? =
            getArr(key)?.list?.mapNotNull { (it as? Num)?.value?.toLong()?.toInt() }
    }
    data class Arr(val list: List<JsonNode>) : JsonNode
    data class Str(val value: String) : JsonNode
    data class Num(val value: Double) : JsonNode
    data class Bool(val value: Boolean) : JsonNode
    data object Null : JsonNode
}

/** Dependency-free JSON tokenizer and parser. */
internal object JsonParser {
    fun parse(input: String): JsonNode {
        var pos = 0
        val len = input.length

        fun skipWhitespace() {
            while (pos < len && input[pos].isWhitespace()) pos++
        }

        fun parseString(): String {
            pos++ // skip leading quote
            val sb = StringBuilder()
            while (pos < len) {
                val c = input[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\' && pos < len) {
                    when (val esc = input[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= len) {
                                val hex = input.substring(pos, pos + 4)
                                pos += 4
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            }
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        fun parseValue(): JsonNode {
            skipWhitespace()
            if (pos >= len) return JsonNode.Null
            return when (val c = input[pos]) {
                '{' -> {
                    pos++
                    val map = LinkedHashMap<String, JsonNode>()
                    skipWhitespace()
                    if (pos < len && input[pos] == '}') {
                        pos++
                        JsonNode.Obj(map)
                    } else {
                        while (pos < len) {
                            skipWhitespace()
                            if (pos >= len || input[pos] != '"') break
                            val key = parseString()
                            skipWhitespace()
                            if (pos < len && input[pos] == ':') pos++
                            val value = parseValue()
                            map[key] = value
                            skipWhitespace()
                            if (pos < len && input[pos] == ',') {
                                pos++
                            } else if (pos < len && input[pos] == '}') {
                                pos++
                                break
                            } else {
                                pos++
                            }
                        }
                        JsonNode.Obj(map)
                    }
                }
                '[' -> {
                    pos++
                    val list = mutableListOf<JsonNode>()
                    skipWhitespace()
                    if (pos < len && input[pos] == ']') {
                        pos++
                        JsonNode.Arr(list)
                    } else {
                        while (pos < len) {
                            list += parseValue()
                            skipWhitespace()
                            if (pos < len && input[pos] == ',') {
                                pos++
                            } else if (pos < len && input[pos] == ']') {
                                pos++
                                break
                            } else {
                                pos++
                            }
                        }
                        JsonNode.Arr(list)
                    }
                }
                '"' -> JsonNode.Str(parseString())
                't', 'f' -> {
                    if (input.startsWith("true", pos)) { pos += 4; JsonNode.Bool(true) }
                    else if (input.startsWith("false", pos)) { pos += 5; JsonNode.Bool(false) }
                    else { pos++; JsonNode.Null }
                }
                'n' -> {
                    if (input.startsWith("null", pos)) pos += 4
                    else pos++
                    JsonNode.Null
                }
                else -> {
                    val start = pos
                    if (c == '-' || c == '+' || c.isDigit()) {
                        pos++
                        while (pos < len && (input[pos].isDigit() || input[pos] == '.' || input[pos] == 'e' || input[pos] == 'E' || input[pos] == '-' || input[pos] == '+')) {
                            pos++
                        }
                        val numStr = input.substring(start, pos)
                        val num = numStr.toDoubleOrNull() ?: 0.0
                        JsonNode.Num(num)
                    } else {
                        pos++
                        JsonNode.Null
                    }
                }
            }
        }

        return parseValue()
    }
}
