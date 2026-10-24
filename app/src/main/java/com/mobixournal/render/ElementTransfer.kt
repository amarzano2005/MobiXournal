package com.mobixournal.render

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixAssetSource
import com.mobixournal.format.mobix.MobixElementCodec
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import java.util.Base64

/**
 * The **multi-flavoured payload** of a selection: one JSON text a copy, a drag or another app can
 * carry, and the decoder that turns it back into elements on the receiving page.
 *
 * The element encoding is the `.mobix` codec itself ([MobixElementCodec]), not a second schema: a
 * copied stroke is the same stroke the document would write, so a paste cannot quietly lose a field
 * the file keeps (per-point pressure, telemetry, a living entity's parameters). Binary payloads an
 * element carries — the pixels of an [ImageElement] — ride in the `assets` object as base64, keyed by
 * the asset name the codec minted, because a clipboard has no archive directory beside it.
 *
 * The format is a versioned envelope (`{"version":1,"elements":[...],"assets":{...}}`) so a future
 * expansion can refuse an old build rather than half-read it. Decoding is deliberately forgiving in
 * the same way a document open is: a malformed payload, an unknown version or one unreadable element
 * yields no elements (or skips that element) rather than a crash on somebody else's clipboard.
 */
object ElementTransfer {

    /** The custom MIME type this whole payload is published under. */
    const val MIME_TYPE: String = "application/vnd.mobixournal.element"

    /** The only envelope version this build writes and reads. */
    const val VERSION: Int = 1

    private const val VERSION_KEY = "version"
    private const val ELEMENTS_KEY = "elements"
    private const val ASSETS_KEY = "assets"

    /**
     * [elements] as the clipboard payload, in their order. Images' bytes are collected by the codec's
     * asset sink and written as base64 beside the element JSON.
     */
    fun encode(elements: List<Element>): String {
        val sink = MobixAssetSink()
        val encoded = elements.map { MobixElementCodec.encode(it, sink) }
        val assets = JsonNode.Obj(
            LinkedHashMap<String, JsonNode>().also { out ->
                for (entry in sink.entries()) {
                    val bytes = runCatching { entry.payload.open().use { it.readBytes() } }.getOrNull() ?: continue
                    out[entry.name] = JsonNode.of(Base64.getEncoder().encodeToString(bytes))
                }
            },
        )
        val root = JsonNode.Obj(
            linkedMapOf(
                VERSION_KEY to JsonNode.of(VERSION),
                ELEMENTS_KEY to JsonNode.Arr(encoded),
                ASSETS_KEY to assets,
            ),
        )
        return JsonWriter.write(root)
    }

    /**
     * The elements [text] carries, in their original order. A malformed envelope, an unknown version
     * or an element this build cannot read contributes nothing; nothing here throws.
     */
    fun decode(text: String): List<Element> {
        val root = runCatching { JsonParser.parse(text) as? JsonNode.Obj }.getOrNull() ?: return emptyList()
        if (root.getInt(VERSION_KEY, -1) != VERSION) return emptyList()
        val array = root.getArr(ELEMENTS_KEY) ?: return emptyList()
        val assets = root.getObj(ASSETS_KEY)?.map.orEmpty().mapNotNull { (name, value) ->
            val encoded = (value as? JsonNode.Str)?.value ?: return@mapNotNull null
            val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return@mapNotNull null
            name to bytes
        }.toMap()
        val source = MobixAssetSource { name -> assets[name] }
        return array.list.mapNotNull { node ->
            val obj = node as? JsonNode.Obj ?: return@mapNotNull null
            runCatching { MobixElementCodec.decode(obj, source) }.getOrNull()
        }
    }

    /** Whether [clipText] carries at least one element this build can read. */
    fun hasPayload(clipText: String?): Boolean = !clipText.isNullOrBlank() && decode(clipText).isNotEmpty()

    /**
     * A short human-readable summary for a text target (another app, or a text field): a text box's or
     * formula's content, a stroke's tool, a living element's family. One line per element, in order.
     */
    fun plainText(elements: List<Element>): String = elements.mapNotNull { element ->
        when (element) {
            is Stroke -> element.tool.name.lowercase()
            is TextElement -> element.content.trim().takeIf { it.isNotEmpty() }
            is MathBlockElement -> element.source.trim().takeIf { it.isNotEmpty() }
            is TexImageElement -> element.latex.trim().takeIf { it.isNotEmpty() }
            is ImageElement -> "image"
            is TableElement -> "table"
            is com.mobixournal.format.model.PlotElement -> "plot"
            is com.mobixournal.format.model.CircuitElement -> "circuit"
            is com.mobixournal.format.model.ParametricShapeElement -> "shape"
            is RawElement -> element.name
        }
    }.joinToString("\n")
}
