package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drift guard `spec/schema/` exists for (`spec/README.md`): the published JSON Schemas are
 * validated against the golden corpus in one direction and against the **writer** in the other.
 *
 * A schema that nothing checks drifts the day a codec gains a field: the file would still parse, still
 * look authoritative, and be wrong. So every element family is decoded from a document that populates
 * every documented field, re-encoded, and the keys the writer emits are compared with the properties
 * the schema declares — a difference either way fails, which is what makes "the schema describes the
 * codec" a fact rather than a promise.
 */
class MobixSchemaGuardTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "spec/schema").isDirectory }
        ?: error("spec/schema not found above ${File("").absolutePath}")
    private val schemaDir = File(repoRoot, "spec/schema")
    private val fixturesDir = File(repoRoot, "mobix-core/src/test/resources/fixtures/mobix-v1")
    private val schemas = HashMap<String, JsonNode.Obj>()

    /** The families the specification models; every other `type` in a page is a retained artefact. */
    private val modelled = setOf(
        "stroke", "text", "image", "texImage", "table", "plot", "parametricShape", "circuit", "mathBlock",
    )

    // ---- reading the schemas ---------------------------------------------------------------------

    private fun schema(relative: String): JsonNode.Obj = schemas.getOrPut(relative) {
        val file = File(schemaDir, relative)
        assertTrue("${file.path} is missing", file.isFile)
        MobixJson.parseJson(file.readText(), relative) as JsonNode.Obj
    }

    private fun allSchemaFiles(): List<File> =
        schemaDir.listFiles().orEmpty().filter { it.extension == "json" } +
            File(schemaDir, "elements").listFiles().orEmpty().filter { it.extension == "json" }.sorted()

    // ---- validation ------------------------------------------------------------------------------

    /**
     * The subset of Draft 2020-12 these schemas use, and no more: `$ref` (local and across the files in
     * `spec/schema/`), `type`, `required`, `properties`, `additionalProperties` as a flag, `enum`,
     * `const`, `items`, `minItems`, `minimum`, `maximum`, `pattern`, `oneOf` and `not`.
     */
    private fun validate(value: JsonNode, relative: String, path: String): List<String> =
        validateAgainst(value, schema(relative), relative, path)

    private fun validateAgainst(value: JsonNode, node: JsonNode.Obj, relative: String, path: String): List<String> {
        node.getStrOrNull("\$ref")?.let { ref ->
            val hash = ref.indexOf('#')
            val file = if (hash < 0) ref else ref.substring(0, hash)
            val pointer = if (hash < 0 || hash == ref.length - 1) null else ref.substring(hash + 1)
            val where = if (file.isEmpty()) relative else file
            val target = if (pointer == null) schema(where) else resolve(schema(where), pointer, ref)
            return validateAgainst(value, target, where, path)
        }

        val errors = mutableListOf<String>()

        node.value("const")?.let { expected ->
            if (!sameValue(value, expected)) errors += "$path: expected ${expected.text()}"
            return errors
        }
        node.getArr("enum")?.let { allowed ->
            if (allowed.list.none { sameValue(value, it) }) {
                errors += "$path: ${value.text()} is not one of ${allowed.list.joinToString { it.text() }}"
            }
        }
        node.value("not")?.let { forbidden ->
            val inner = forbidden as? JsonNode.Obj ?: error("`not` must hold a schema")
            if (validateAgainst(value, inner, relative, path).isEmpty()) {
                errors += "$path: matches a shape the schema excludes"
            }
        }

        val types = when (val declared = node.value("type")) {
            null -> emptyList()
            is JsonNode.Str -> listOf(declared.value)
            is JsonNode.Arr -> declared.list.mapNotNull { (it as? JsonNode.Str)?.value }
            else -> error("`type` must be a string or an array of strings")
        }
        if (types.isNotEmpty() && types.none { matchesType(value, it) }) {
            errors += "$path: ${value.text()} is not ${types.joinToString(" or ")}"
            return errors
        }

        if (value is JsonNode.Obj) {
            node.getStrList("required")?.forEach { key ->
                if (!value.has(key)) errors += "$path: `$key` is required"
            }
            val properties = node.getObj("properties")?.map.orEmpty()
            for ((key, child) in value.map) {
                val childSchema = properties[key] as? JsonNode.Obj
                if (childSchema != null) {
                    errors += validateAgainst(child, childSchema, relative, "$path.$key")
                } else if (!node.getBool("additionalProperties", true)) {
                    errors += "$path: `$key` is not described by this schema"
                }
            }
        }

        if (value is JsonNode.Arr) {
            node.getIntOrNull("minItems")?.let { min ->
                if (value.list.size < min) errors += "$path: ${value.list.size} items, at least $min required"
            }
            (node.value("items") as? JsonNode.Obj)?.let { items ->
                value.list.forEachIndexed { index, item ->
                    errors += validateAgainst(item, items, relative, "$path[$index]")
                }
            }
        }

        if (value is JsonNode.Num) {
            node.getDoubleOrNull("minimum")?.let { min ->
                if (value.value < min) errors += "$path: ${value.value} is below $min"
            }
            node.getDoubleOrNull("maximum")?.let { max ->
                if (value.value > max) errors += "$path: ${value.value} is above $max"
            }
        }

        if (value is JsonNode.Str) {
            node.getStrOrNull("pattern")?.let { pattern ->
                if (!Regex(pattern).matches(value.value)) {
                    errors += "$path: \"${value.value}\" does not match $pattern"
                }
            }
        }

        node.getArr("oneOf")?.let { branches ->
            val passing = branches.list.count { branch ->
                validateAgainst(value, branch as JsonNode.Obj, relative, path).isEmpty()
            }
            if (passing != 1) errors += "$path: matches $passing of the shapes offered, exactly one must match"
        }

        return errors
    }

    private fun resolve(root: JsonNode.Obj, pointer: String, ref: String): JsonNode.Obj {
        var current: JsonNode = root
        for (token in pointer.removePrefix("/").split('/')) {
            if (token.isEmpty()) continue
            val key = token.replace("~1", "/").replace("~0", "~")
            current = (current as? JsonNode.Obj)?.map?.get(key)
                ?: (current as? JsonNode.Arr)?.list?.getOrNull(key.toIntOrNull() ?: -1)
                ?: error("$ref does not resolve: no `$key`")
        }
        return current as? JsonNode.Obj ?: error("$ref does not point at a schema")
    }

    private fun matchesType(value: JsonNode, type: String): Boolean = when (type) {
        "object" -> value is JsonNode.Obj
        "array" -> value is JsonNode.Arr
        "string" -> value is JsonNode.Str
        "number" -> value is JsonNode.Num
        "integer" -> value is JsonNode.Num && value.value == Math.floor(value.value)
        "boolean" -> value is JsonNode.Bool
        "null" -> value is JsonNode.Null
        else -> error("unknown type $type")
    }

    private fun sameValue(value: JsonNode, expected: JsonNode): Boolean = when {
        value is JsonNode.Str && expected is JsonNode.Str -> value.value == expected.value
        value is JsonNode.Num && expected is JsonNode.Num -> value.value == expected.value
        value is JsonNode.Bool && expected is JsonNode.Bool -> value.value == expected.value
        else -> value is JsonNode.Null && expected is JsonNode.Null
    }

    private fun JsonNode.text(): String = when (this) {
        is JsonNode.Str -> "\"$value\""
        is JsonNode.Num -> value.toString()
        is JsonNode.Bool -> value.toString()
        is JsonNode.Null -> "null"
        is JsonNode.Arr -> "an array"
        is JsonNode.Obj -> "an object"
    }

    // ---- the corpus ------------------------------------------------------------------------------

    @Test
    fun everyGoldenFixturePartValidatesAgainstItsSchema() {
        val fixtures = fixturesDir.listFiles().orEmpty().filter { it.extension == "mobix" }.sorted()
        assertTrue("the golden corpus is empty at ${fixturesDir.path}", fixtures.isNotEmpty())

        val errors = mutableListOf<String>()
        var retained = 0
        for (fixture in fixtures) {
            ZipFile(fixture).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                if ("manifest.json" in names) {
                    val manifest = MobixJson.parseJson(zip.readText("manifest.json"), "manifest")
                    errors += validate(manifest, "manifest.schema.json", "${fixture.name}:manifest")
                }
                if ("canvas.json" in names) {
                    val canvas = MobixJson.parseJson(zip.readText("canvas.json"), "canvas")
                    errors += validate(canvas, "canvas.schema.json", "${fixture.name}:canvas")
                }
                for (entry in names.filter { it.startsWith("pages/") || it.startsWith("chunks/") }) {
                    val page = MobixJson.parseJson(zip.readText(entry), entry) as JsonNode.Obj
                    val partSchema = if (entry.startsWith("chunks/")) "chunk.schema.json" else "page.schema.json"
                    errors += validate(page, partSchema, "${fixture.name}:$entry")
                    for (layer in page.getArr("layers")?.list.orEmpty()) {
                        for (element in (layer as JsonNode.Obj).getArr("elements")?.list.orEmpty()) {
                            val type = (element as JsonNode.Obj).getStrOrNull("type") ?: continue
                            if (type !in modelled) {
                                retained++
                                continue
                            }
                            errors += validate(element, "elements/$type.schema.json", "${fixture.name}:$entry:$type")
                        }
                    }
                }
            }
        }
        assertTrue("these corpus parts disagree with the published schemas:\n  " + errors.joinToString("\n  "), errors.isEmpty())
        assertTrue(
            "the corpus is supposed to carry a type this build does not model, and the guard must be seen " +
                "skipping it rather than silently having no case",
            retained > 0,
        )
    }

    // ---- the writer, in the other direction -------------------------------------------------------

    /** Every documented field of every family, so an omission on either side shows up as a difference. */
    private val samples: Map<String, String> = mapOf(
        "stroke" to """
            {"type":"stroke","id":"stk_1","tool":"PEN","color":"#11223344","lineStyle":"DASHED",
             "lineWidth":2.0,"capStyle":"round","fill":10,
             "audioRef":{"filename":"take.wav","assetId":"assets/aud_${"ab".repeat(32)}.wav","startMs":5},
             "bounds":{"left":1.0,"top":2.0,"right":3.0,"bottom":4.0},
             "points":[{"x":1.0,"y":2.0,"width":1.5,"pressure":0.5,"tilt":0.1,"azimuth":0.2,"dtMs":7}],
             "attrs":{"foo":"bar"}}
        """,
        "text" to """
            {"type":"text","id":"txt_1","font":"Sans 12","size":12.0,"x":10.0,"y":20.0,
             "color":"#000000ff","content":"hello","attrs":{"a":"b"}}
        """,
        "image" to """
            {"type":"image","id":"img_1","bounds":{"left":1.0,"top":2.0,"right":3.0,"bottom":4.0},
             "asset":"assets/img_${"cd".repeat(32)}.png","attrs":{"a":"b"}}
        """,
        "texImage" to """
            {"type":"texImage","id":"tex_1","bounds":{"left":1.0,"top":2.0,"right":3.0,"bottom":4.0},
             "latex":"x^2","color":"#000000ff","asset":"assets/img_${"ef".repeat(32)}.png",
             "latexInAttribute":false,"attrs":{"a":"b"}}
        """,
        "table" to """
            {"type":"table","id":"tbl_1","bounds":{"left":1.0,"top":2.0,"right":3.0,"bottom":4.0},
             "colWidths":[10.0,20.0],"rowHeights":[30.0],
             "cells":[[{"text":"a","align":"LEFT","bold":true,"fillColor":"#11223344"}]],
             "headerRow":true,"borderColor":"#212121ff","borderWidth":1.5}
        """,
        "plot" to """
            {"type":"plot","id":"plt_1","bounds":{"left":1.0,"top":2.0,"right":3.0,"bottom":4.0},
             "xMin":-1.0,"xMax":1.0,"samples":50,"showCriticalPoints":true,"includeDerivative":true,
             "shadeIntegral":{"from":0.0,"to":1.0},
             "curves":[{"kind":"PARAMETRIC","source":"cos(t)","sourceY":"sin(t)","color":"#000000ff",
                        "strokeWidth":1.5,"lineStyle":"DOTTED","tMin":0.0,"tMax":6.28}],
             "parameters":{"a":2.0},"axisColor":"#37474fff","summaryText":"cached","infoSide":"ABOVE"}
        """,
        "parametricShape" to """
            {"type":"parametricShape","id":"shp_1","shape":"pentagon",
             "bounds":{"left":1.0,"top":2.0,"right":3.0,"bottom":4.0},
             "vertices":[{"x":1.0,"y":2.0}],
             "strokeColor":"#000000ff","strokeWidth":1.5,"fillColor":"#11223344",
             "showAngles":true,"showSideLengths":true}
        """,
        "circuit" to """
            {"type":"circuit","id":"crc_1","symbol":"resistor","value":"R1","labelSide":"RIGHT",
             "position":{"x":10.0,"y":20.0},"rotation":90.0,
             "strokeColor":"#000000ff","strokeWidth":1.5,
             "pins":[{"id":"p1","relX":0.5,"relY":1.5,"connectedTo":"p2"}]}
        """,
        "mathBlock" to """
            {"type":"mathBlock","id":"mth_1","source":"\\frac{\\pi}{2}","flavor":"TYPST",
             "color":"#1f6febff","bounds":{"left":1.0,"top":2.0,"right":3.0,"bottom":4.0}}
        """,
    )

    @Test
    fun theWriterAndTheSchemaDescribeTheSameFields() {
        val source = MobixAssetSource { byteArrayOf(1, 2, 3) }
        val problems = mutableListOf<String>()
        for (family in modelled) {
            val json = samples[family] ?: error("no sample for $family")
            val node = MobixJson.parseJson(json, family) as JsonNode.Obj
            val decoded = MobixElementCodec.decode(node, source)
                ?: error("$family: the writer's own document does not decode")
            val written = MobixElementCodec.encode(decoded, MobixAssetSink()) as JsonNode.Obj

            // A `deprecated` field is read but no longer written, so one sample cannot stand for it. An
            // `x-conditional` field is written only for the shape it belongs to, and each sample below is
            // built as that shape, so those are compared like any other.
            val declared = (schema("elements/$family.schema.json").getObj("properties")?.map.orEmpty())
                .filterValues { it !is JsonNode.Obj || !it.getBool("deprecated", false) }
                .keys
            val emitted = written.map.keys
            if (declared != emitted) {
                problems += "$family: the schema declares ${declared.sorted()}, the writer emits ${emitted.sorted()}"
            }
            problems += validate(written, "elements/$family.schema.json", family)
        }
        assertTrue(
            "the schemas and the codecs disagree — a field added on one side only:\n  " +
                problems.joinToString("\n  "),
            problems.isEmpty(),
        )
    }

    // ---- the files themselves --------------------------------------------------------------------

    @Test
    fun everySchemaSaysWhereItStops() {
        val files = allSchemaFiles()
        assertTrue("spec/schema carries no schema", files.size >= 5)
        for (file in files) {
            val node = MobixJson.parseJson(file.readText(), file.name) as JsonNode.Obj
            assertEquals("${file.name} must declare the draft", "https://json-schema.org/draft/2020-12/schema", node.getStrOrNull("\$schema"))
            assertTrue("${file.name} must have an \$id", node.getStrOrNull("\$id")?.startsWith("https://mobixournal.com/spec/schema/") == true)
            val comment = node.getStrOrNull("\$comment").orEmpty()
            assertTrue(
                "${file.name} must open with the sentence that says where it stops, and say enough to be useful",
                comment.startsWith("Where the schema stops:") && comment.length > 80,
            )
        }
    }

    private fun ZipFile.readText(name: String): String = getInputStream(getEntry(name)).use { it.readBytes().toString(Charsets.UTF_8) }
}
