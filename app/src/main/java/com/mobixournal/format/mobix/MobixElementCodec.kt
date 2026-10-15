package com.mobixournal.format.mobix

import com.mobixournal.format.XoppColor
import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.toJson
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.audio.audioRef

/**
 * The `.mobix` element codec: one JSON object per element of the app's own model
 * (`docs/mobix-schema-spec.md` owns the schema; this is its code).
 *
 * Four rules run through every type here, and they are what make the format worth having:
 *
 *  1. **An element type this build does not know is preserved, not dropped.** It comes back as a
 *     [RawElement] in JSON syntax (`format/model/Element.kt`) holding its own text, so a `.mobix`
 *     written by a newer app — a `3dPlotElement` nobody has written yet, a vendor extension — survives
 *     an open-and-save here untouched. That is the whole reason the format has a `type` discriminator
 *     instead of a fixed element list. The living entities this build *does* model (table, plot,
 *     parametric shape, circuit, `docs/mobix-elements.md`) reach their own codecs and keep this same
 *     answer for the case that matters more: a known family this build cannot read **whole** is
 *     preserved verbatim rather than half-read into a model that would save back a different element.
 *  2. **Enums are written as the names the app uses** (`PEN`, `HIGHLIGHTER`, `PLAIN`, `DASHED`, …) so
 *     the file reads as the model does, and a value that is not a known name is refused rather than
 *     silently mapped to a default — a stroke tool we do not recognise is a *reading* bug we want to
 *     hear about, not a pen stroke drawn as an eraser.
 *  3. **Binary payloads go to `assets/`, named by content hash** ([MobixAssetSink]) — an image element
 *     carries an `asset` reference, never base64.
 *  4. **What we cannot interpret is kept.** `attrs` holds the `.xopp` attributes the bridge must not
 *     lose (`ts`, `fn`, anything a newer desktop writes), so a `.xopp` through a phone, or through a
 *     `.mobix`, comes back with them. What is **not** preserved is an unknown *field* inside a known
 *     element type: the model has no room for one, and inventing a channel for it would put a second,
 *     ambiguous copy of the schema in `attrs`. That is what `formatVersion` is for — a breaking
 *     addition is a version bump, and the version is refused rather than half-read. The envelope
 *     (manifest, canvas, parts) does preserve unknown fields; see those codecs.
 *
 * `bounds` is written when the element's geometry determines it (a stroke's extents, an image's box)
 * and is **never read back**: it is a cache for a reader that does not want to parse the element, so
 * trusting it would let a stale value become the truth.
 */
internal object MobixElementCodec {

    /** The `type` values this build models; anything else is preserved as raw JSON. */
    const val STROKE: String = "stroke"
    const val TEXT: String = "text"
    const val IMAGE: String = "image"
    const val TEX_IMAGE: String = "texImage"

    private const val TYPE = "type"
    private const val ID = "id"

    /** The field recording that an element's body is Xournal++ markup rather than this format's JSON. */
    private const val SYNTAX = "syntax"

    /** The `syntax` value marking an element carried over from `.xopp`, which has no native shape. */
    private const val SYNTAX_XML = "xml"
    private const val BOUNDS = "bounds"
    private const val ATTRS = "attrs"

    // ---- encode ---------------------------------------------------------------------------------

    /**
     * [element] as JSON, registering any binary payload it carries in [assets].
     *
     * [audioBytes] supplies the bytes of the recording a stroke's `fn` names, when the caller's stores
     * hold them: a `.mobix` carries its audio inside itself, so a recording the document references and
     * the app can read is bundled by content hash and named by the stroke ([encodeStroke]). A caller
     * with no audio (a format-only test, the metadata block) passes nothing and the sidecar name stands
     * alone, exactly as it does in a `.xopp`.
     */
    fun encode(
        element: Element,
        assets: MobixAssetSink,
        audioBytes: (String) -> ByteArray? = { null },
    ): JsonNode.Obj = when (element) {
        is Stroke -> encodeStroke(element, assets, audioBytes)
        is TextElement -> encodeText(element)
        is ImageElement -> encodeImage(element, assets)
        is TexImageElement -> encodeTexImage(element, assets)
        // The living entities: what `.xopp` can only bake into dead ink, spelled field by field by
        // their own codecs (`docs/mobix-elements.md`).
        is TableElement -> MobixTableCodec.encode(element)
        is PlotElement -> MobixPlotCodec.encode(element)
        is ParametricShapeElement -> MobixShapeCodec.encode(element)
        is CircuitElement -> MobixCircuitCodec.encode(element)
        is RawElement -> encodeRaw(element)
    }

    private fun encodeStroke(
        s: Stroke,
        assets: MobixAssetSink,
        audioBytes: (String) -> ByteArray?,
    ): JsonNode.Obj = MobixJson.obj { out ->
        out[TYPE] = JsonNode.of(STROKE)
        s.id?.let { out[ID] = JsonNode.of(it) }
        out["tool"] = JsonNode.of(s.tool.name)
        out["color"] = JsonNode.of(XoppColor.format(s.color))
        out["lineStyle"] = JsonNode.of(s.lineStyle.name)
        // A constant width is a `lineWidth`; per-point widths (a pressure stroke) are in the points,
        // exactly as `.xopp` tells the two apart by the presence of its `width` attribute.
        if (s.uniformWidth) out["lineWidth"] = JsonNode.of(s.points.firstOrNull()?.width ?: 0.0)
        s.capStyle?.let { out["capStyle"] = JsonNode.of(it) }
        val audio = s.audioRef()
        if (audio != null) {
            // Both names are written, and they are different things: `assetId` is the entry the bytes
            // are in — the archive that makes this file self-contained — and `filename` is the sidecar
            // the stroke replayed from, which the `.xopp` export has to write a file for.
            val bundled = audioBytes(audio.filename)
                ?.let { assets.add(it, MobixFormat.AssetKind.AUDIO) }
                ?: s.bundledAudio
                ?: audio.filename.takeIf { MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.AUDIO) }
            out["audioRef"] = MobixJson.obj { refOut ->
                refOut["filename"] = JsonNode.of(audio.filename)
                bundled?.let { refOut["assetId"] = JsonNode.of(it) }
                refOut["startMs"] = JsonNode.of(audio.startMs)
            }
        }
        boundsOf(s.points)?.let { out[BOUNDS] = it }
        out["points"] = JsonNode.arr(s.points.map(::encodePoint))
        val attrsToWrite = if (audio != null) {
            s.extraAttrs.filterKeys { it != "fn" && it != "ts" }
        } else {
            s.extraAttrs
        }
        if (attrsToWrite.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(attrsToWrite)
    }

    private fun encodePoint(p: StrokePoint): JsonNode = MobixJson.obj { out ->
        out["x"] = JsonNode.of(p.x)
        out["y"] = JsonNode.of(p.y)
        out["width"] = JsonNode.of(p.width)
        p.pressure?.let { out["pressure"] = JsonNode.of(it.toDouble()) }
        p.tilt?.let { out["tilt"] = JsonNode.of(it.toDouble()) }
        p.azimuth?.let { out["azimuth"] = JsonNode.of(it.toDouble()) }
        p.dtMs?.let { out["dtMs"] = JsonNode.of(it) }
    }

    private fun encodeText(t: TextElement): JsonNode.Obj = MobixJson.obj { out ->
        out[TYPE] = JsonNode.of(TEXT)
        t.id?.let { out[ID] = JsonNode.of(it) }
        out["font"] = JsonNode.of(t.font)
        out["size"] = JsonNode.of(t.size)
        out["x"] = JsonNode.of(t.x)
        out["y"] = JsonNode.of(t.y)
        out["color"] = JsonNode.of(XoppColor.format(t.color))
        out["content"] = JsonNode.of(t.content)
        if (t.extraAttrs.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(t.extraAttrs)
    }

    private fun encodeImage(image: ImageElement, assets: MobixAssetSink): JsonNode.Obj =
        MobixJson.obj { out ->
            out[TYPE] = JsonNode.of(IMAGE)
            image.id?.let { out[ID] = JsonNode.of(it) }
            out[BOUNDS] = MobixJson.box(image.left, image.top, image.right, image.bottom)
            out["asset"] = JsonNode.of(assets.add(image.data, MobixFormat.AssetKind.IMAGE))
            if (image.extraAttrs.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(image.extraAttrs)
        }

    private fun encodeTexImage(tex: TexImageElement, assets: MobixAssetSink): JsonNode.Obj =
        MobixJson.obj { out ->
            out[TYPE] = JsonNode.of(TEX_IMAGE)
            tex.id?.let { out[ID] = JsonNode.of(it) }
            out[BOUNDS] = MobixJson.box(tex.left, tex.top, tex.right, tex.bottom)
            out["latex"] = JsonNode.of(tex.latex)
            out["color"] = JsonNode.of(XoppColor.format(tex.color))
            // The rendered PNG is optional (a source may carry only the LaTeX), and so is where the
            // LaTeX lived — a spelling detail of `.xopp` kept so the bridge can re-emit a file as it
            // was authored rather than silently converting it.
            tex.data?.let { out["asset"] = JsonNode.of(assets.add(it, MobixFormat.AssetKind.IMAGE, "png")) }
            if (!tex.latexInAttribute) out["latexInAttribute"] = JsonNode.of(false)
            if (tex.extraAttrs.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(tex.extraAttrs)
        }

    /** Re-emit an element we don't model exactly as it was read (see [RawElement]). */
    private fun encodeRaw(raw: RawElement): JsonNode.Obj =
        if (raw.syntax == RawElement.Syntax.JSON) {
            // The body is the whole object, verbatim: parse it so a rewrite normalises formatting while
            // keeping every key, its order and its value.
            JsonParser.parse(raw.body) as? JsonNode.Obj
                ?: MobixJson.obj { it[TYPE] = JsonNode.of(raw.name) }
        } else {
            // An XML element with no `.mobix` spelling. It is written with an explicit `syntax` marker,
            // because otherwise it would come back indistinguishable from a native element whose type
            // happens to be the same word — and a `.xopp`-only element that got re-read as a native one
            // would then be *skipped* by the next `.xopp` export, which is exactly the round-trip loss
            // this preservation channel exists to prevent.
            MobixJson.obj { out ->
                out[TYPE] = JsonNode.of(raw.name)
                out[SYNTAX] = JsonNode.of(SYNTAX_XML)
                if (raw.attrs.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(raw.attrs)
                if (raw.body.isNotEmpty()) out["body"] = JsonNode.of(raw.body)
            }
        }

    // ---- decode ---------------------------------------------------------------------------------

    /**
     * The element [node] describes: a modelled one when this build knows its `type`, a preserved
     * [RawElement] when it does not, and null when the object has no type at all (which is not an
     * element anyone can interpret).
     *
     * [mint] supplies an id to an element that carries none — a `.xopp` import, or a `.mobix` from a
     * build that predates identity. Injectable so a test can assert a whole round trip without
     * random ids in the way.
     */
    fun decode(
        node: JsonNode.Obj,
        assets: MobixAssetSource,
        mint: (String) -> String = ElementId::mint,
    ): Element? {
        val type = node.getStrOrNull(TYPE) ?: return null
        // The marker is checked first: an element carried over from `.xopp` is not "an element whose
        // type is called xoppElement", it is one whose *body* is a different language.
        if (node.getStrOrNull(SYNTAX) == SYNTAX_XML) return decodeXmlCarried(node, type)
        return when (type) {
            STROKE -> decodeStroke(node, mint)
            TEXT -> decodeText(node, mint)
            IMAGE -> decodeImage(node, assets, mint)
            TEX_IMAGE -> decodeTexImage(node, assets, mint)
            // A known living family is modelled; one this build cannot read *whole* falls through to the
            // same verbatim preservation an unknown type gets, so nothing is ever half-read.
            MobixTableCodec.TYPE -> MobixTableCodec.decode(node, mint) ?: preserve(type, node)
            MobixPlotCodec.TYPE -> MobixPlotCodec.decode(node, mint) ?: preserve(type, node)
            MobixShapeCodec.TYPE -> MobixShapeCodec.decode(node, mint) ?: preserve(type, node)
            MobixCircuitCodec.TYPE -> MobixCircuitCodec.decode(node, mint) ?: preserve(type, node)
            else -> preserve(type, node)
        }
    }

    /**
     * An element that came from a `.xopp` and has no native shape: its tag, its attributes and its
     * markup are rebuilt from the marker object [encodeRaw] wrote, so it exports back as itself.
     */
    private fun decodeXmlCarried(node: JsonNode.Obj, type: String): RawElement = RawElement(
        name = type,
        attrs = MobixJson.readAttrs(node),
        body = node.getStr("body", ""),
        syntax = RawElement.Syntax.XML,
    )

    private fun decodeStroke(node: JsonNode.Obj, mint: (String) -> String): Stroke {
        val toolName = node.getStrOrNull("tool") ?: STROKE
        val tool = Tool.entries.firstOrNull { it.name == toolName }
            ?: throw MobixContainer.MalformedMobixException("stroke tool \"$toolName\" is not one this build knows")
        val styleName = node.getStr("lineStyle", LineStyle.PLAIN.name)
        val style = LineStyle.entries.firstOrNull { it.name == styleName }
            ?: throw MobixContainer.MalformedMobixException("stroke lineStyle \"$styleName\" is not one this build knows")
        val extraAttrs = MobixJson.readAttrs(node).toMutableMap()
        // A recording the container carries: the entry it lives in, plus — when the writer kept one —
        // the sidecar name the stroke replayed from. A file that names only an asset (the shape this
        // codec wrote before) is read as both, so its `fn` is the entry and the adoption step knows it.
        var bundledAudio: String? = null
        node.getObj("audioRef")?.let { refObj ->
            val assetId = refObj.getStrOrNull("assetId")
            val filename = refObj.getStrOrNull("filename")
            val reference = filename ?: assetId
            if (reference != null) {
                extraAttrs["fn"] = reference
                extraAttrs["ts"] = refObj.getInt("startMs", 0).toString()
            }
            // Only when the entry and the reference are two different names: a stroke that names an
            // `aud_…` asset as its `fn` has one name, and reading it back as two would make a round trip
            // through the codec change the model. The adoption step reads that shape off `fn` itself.
            bundledAudio = assetId?.takeIf {
                it != reference && MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.AUDIO)
            }
        }
        return Stroke(
            tool = tool,
            color = XoppColor.parse(node.getStrOrNull("color")),
            capStyle = node.getStrOrNull("capStyle"),
            points = node.getArr("points")?.list.orEmpty().mapNotNull { decodePoint(it) },
            uniformWidth = node.has("lineWidth"),
            lineStyle = style,
            fill = node.getIntOrNull("fill"),
            extraAttrs = extraAttrs,
            id = node.getStrOrNull(ID) ?: mint(ElementId.STROKE),
            bundledAudio = bundledAudio,
        )
    }

    private fun decodePoint(node: JsonNode): StrokePoint? {
        val obj = node as? JsonNode.Obj ?: return null
        val x = obj.getDoubleOrNull("x") ?: return null
        val y = obj.getDoubleOrNull("y") ?: return null
        return StrokePoint(
            x = x,
            y = y,
            // A point's own width is the pressure-resolved one; absent, the stroke's lineWidth is what
            // the reader would fall back to anyway, and 0 would draw nothing.
            width = obj.getDoubleOrNull("width") ?: 1.0,
            pressure = obj.getFloatOrNull("pressure"),
            tilt = obj.getFloatOrNull("tilt"),
            azimuth = obj.getFloatOrNull("azimuth"),
            dtMs = obj.getIntOrNull("dtMs"),
        )
    }

    private fun decodeText(node: JsonNode.Obj, mint: (String) -> String): TextElement = TextElement(
        font = node.getStr("font", ""),
        size = node.getDouble("size", 0.0),
        x = node.getDouble("x", 0.0),
        y = node.getDouble("y", 0.0),
        color = XoppColor.parse(node.getStrOrNull("color")),
        content = node.getStr("content", ""),
        extraAttrs = MobixJson.readAttrs(node),
        id = node.getStrOrNull(ID) ?: mint(ElementId.TEXT),
    )

    private fun decodeImage(node: JsonNode.Obj, assets: MobixAssetSource, mint: (String) -> String): ImageElement {
        val box = node.getObj(BOUNDS) ?: throw MobixContainer.MalformedMobixException("an image element has no bounds")
        val name = node.getStrOrNull("asset")
        return ImageElement(
            left = box.getDouble("left", 0.0),
            top = box.getDouble("top", 0.0),
            right = box.getDouble("right", 0.0),
            bottom = box.getDouble("bottom", 0.0),
            data = name?.let(assets::bytes)
                ?: throw MobixContainer.MalformedMobixException("an image element's asset is missing from the archive"),
            extraAttrs = MobixJson.readAttrs(node),
            id = node.getStrOrNull(ID) ?: mint(ElementId.IMAGE),
        )
    }

    private fun decodeTexImage(
        node: JsonNode.Obj,
        assets: MobixAssetSource,
        mint: (String) -> String,
    ): TexImageElement {
        val box = node.getObj(BOUNDS) ?: throw MobixContainer.MalformedMobixException("a texImage element has no bounds")
        return TexImageElement(
            left = box.getDouble("left", 0.0),
            top = box.getDouble("top", 0.0),
            right = box.getDouble("right", 0.0),
            bottom = box.getDouble("bottom", 0.0),
            latex = node.getStr("latex", ""),
            color = XoppColor.parse(node.getStrOrNull("color")),
            data = node.getStrOrNull("asset")?.let(assets::bytes),
            latexInAttribute = node.getBool("latexInAttribute", true),
            extraAttrs = MobixJson.readAttrs(node),
            id = node.getStrOrNull(ID) ?: mint(ElementId.LATEX),
        )
    }

    // ---- shared helpers -------------------------------------------------------------------------

    /**
     * An element this build could not model, kept whole: its own JSON, verbatim, re-emitted untouched.
     * A *known* type that failed to decode lands here too, which is the honest answer — the model has
     * no channel for a field it did not interpret, so a half-read table would be a table silently
     * rewritten into a different one.
     */
    private fun preserve(type: String, node: JsonNode.Obj): RawElement =
        RawElement(name = type, body = node.toJson(), syntax = RawElement.Syntax.JSON)

    /** A stroke's own extents: the polyline, widened by half the ink at each end. */
    private fun boundsOf(points: List<StrokePoint>): JsonNode? {
        if (points.isEmpty()) return null
        val half = (points.maxOfOrNull { it.width } ?: 0.0) / 2.0
        return MobixJson.box(
            left = points.minOf { it.x } - half,
            top = points.minOf { it.y } - half,
            right = points.maxOf { it.x } + half,
            bottom = points.maxOf { it.y } + half,
        )
    }

}
