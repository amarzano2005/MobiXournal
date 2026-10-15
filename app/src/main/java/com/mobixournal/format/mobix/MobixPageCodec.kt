package com.mobixournal.format.mobix

import com.mobixournal.format.XoppColor
import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * The `.mobix` page codec: `pages/page-N.json` or `chunks/chunk_x_y.json` — a sheet's size, its
 * background, and its layers of elements (`docs/mobix-schema-spec.md`).
 *
 * The page's *identity* is not here: a page's stable id lives in the manifest's `pages[]` entry, which
 * already names the file. Repeating it in both would give the document two places to disagree about
 * which page this is.
 *
 * **The background is one shape everywhere.** A page's background and the manifest's `defaultPaper`
 * are the same JSON object and the same model type ([Background]), so "what a new page starts on" and
 * "what this page is on" cannot drift apart — and the ruling's own parameters travel verbatim in
 * `config`, the comma-separated string desktop Xournal++ writes (`format/BackgroundRuling`), rather
 * than being parsed into fields and re-serialised into a spellings mismatch.
 *
 * A pixmap or PDF background can be **bundled**: when the caller can supply its bytes (the app's own
 * `ImageStore`/`PdfStore` copy) an `asset` reference is written beside the original `domain`/
 * `filename`, so the document is self-contained *and* the bridge can still re-emit the reference the
 * `.xopp` had. Neither happens and the reference stands alone, exactly as it does in a gzip `.xopp`.
 *
 * A bundled background is addressed like every other payload — by the hash of its bytes, in `assets/`.
 * A fixed `background.pdf` name was the obvious alternative and is wrong: a document may cite several
 * PDFs (one per page's background), and a name that is not derived from the content would silently let
 * the last one replace the others.
 */
internal object MobixPageCodec {

    private const val ATTRS = "attrs"

    /**
     * [page] as JSON, registering bundled payloads in [assets] — a picture or PDF through
     * [backgroundBytes], a stroke's recording through [audioBytes].
     */
    fun encode(
        page: Page,
        assets: MobixAssetSink,
        backgroundBytes: (Background) -> ByteArray? = { null },
        audioBytes: (String) -> ByteArray? = { null },
    ): JsonNode.Obj = MobixJson.obj { out ->
        out["widthPt"] = JsonNode.of(page.width)
        out["heightPt"] = JsonNode.of(page.height)
        out["background"] = encodeBackground(page.background, assets, backgroundBytes)
        out["layers"] = JsonNode.arr(page.layers.map { encodeLayer(it, assets, audioBytes) })
        if (page.extraAttrs.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(page.extraAttrs)
    }

    /** The page [node] describes. */
    fun decode(node: JsonNode.Obj, assets: MobixAssetSource): Page = Page(
        width = node.getDouble("widthPt", 0.0),
        height = node.getDouble("heightPt", 0.0),
        background = node.getObj("background")?.let { decodeBackground(it, assets) }
            ?: Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = node.getArr("layers")?.list.orEmpty()
            .mapNotNull { (it as? JsonNode.Obj)?.let { l -> decodeLayer(l, assets) } },
        extraAttrs = MobixJson.readAttrs(node),
    )

    // ---- layers ---------------------------------------------------------------------------------
 
    internal fun encodeLayer(
        layer: Layer,
        assets: MobixAssetSink,
        audioBytes: (String) -> ByteArray? = { null },
    ): JsonNode.Obj = MobixJson.obj { out ->
        layer.name?.let { out["name"] = JsonNode.of(it) }
        out["elements"] = JsonNode.arr(layer.elements.map { MobixElementCodec.encode(it, assets, audioBytes) })
        if (layer.extraAttrs.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(layer.extraAttrs)
    }

    internal fun decodeLayer(node: JsonNode.Obj, assets: MobixAssetSource): Layer = Layer(
        elements = node.getArr("elements")?.list.orEmpty()
            .mapNotNull { (it as? JsonNode.Obj)?.let { e -> MobixElementCodec.decode(e, assets) } }
            .filterIsInstance<Element>(),
        name = node.getStrOrNull("name"),
        extraAttrs = MobixJson.readAttrs(node),
    )

    // ---- backgrounds ----------------------------------------------------------------------------

    /** [background] as JSON, bundling its bytes through [backgroundBytes] when the caller can supply them. */
    fun encodeBackground(
        background: Background,
        assets: MobixAssetSink,
        backgroundBytes: (Background) -> ByteArray? = { null },
    ): JsonNode.Obj = MobixJson.obj { out ->
        when (background) {
            is Background.Solid -> {
                out["type"] = JsonNode.of("solid")
                out["color"] = JsonNode.of(XoppColor.format(background.color))
                out["style"] = JsonNode.of(background.style)
                background.config?.let { out["config"] = JsonNode.of(it) }
            }
            is Background.Pixmap -> {
                out["type"] = JsonNode.of("pixmap")
                out["domain"] = JsonNode.of(background.domain)
                out["filename"] = JsonNode.of(background.filename)
                // The bytes when the caller has them, the name the document already used otherwise.
                (backgroundBytes(background)
                    ?.let { assets.add(it, MobixFormat.AssetKind.IMAGE) }
                    ?: background.bundledAsset)?.let { out["asset"] = JsonNode.of(it) }
            }
            is Background.Pdf -> {
                out["type"] = JsonNode.of("pdf")
                background.filename?.let { out["filename"] = JsonNode.of(it) }
                background.domain?.let { out["domain"] = JsonNode.of(it) }
                out["pageNo"] = JsonNode.of(background.pageNo)
                (backgroundBytes(background)
                    ?.let { assets.add(it, MobixFormat.AssetKind.IMAGE, suffix = "pdf") }
                    ?: background.bundledAsset)?.let { out["asset"] = JsonNode.of(it) }
            }
        }
        if (background.extraAttrs.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(background.extraAttrs)
    }

    /**
     * The background [node] describes; a plain white sheet when the type is not one this build knows.
     *
     * A bundled background keeps its `asset` name in the model ([Background.Pdf.bundledAsset]) even
     * when this pass never reads the bytes: dropping it would orphan the entry the document's own
     * bytes live in, so a rewrite from the model would write a background pointing at nothing while
     * the payload sat in the archive unreferenced. Adopting those bytes into the app's own stores is
     * the document layer's job (`docs/data-path.md`).
     */
    fun decodeBackground(node: JsonNode.Obj, assets: MobixAssetSource): Background {
        val extra = MobixJson.readAttrs(node)
        val bundled = node.getStrOrNull("asset")
        return when (node.getStrOrNull("type")) {
            "pixmap" -> Background.Pixmap(
                domain = node.getStr("domain", ""),
                filename = node.getStr("filename", ""),
                extraAttrs = extra,
                bundledAsset = bundled,
            )
            "pdf" -> Background.Pdf(
                filename = node.getStrOrNull("filename"),
                pageNo = node.getInt("pageNo", 0),
                domain = node.getStrOrNull("domain"),
                extraAttrs = extra,
                bundledAsset = bundled,
            )
            else -> Background.Solid(
                color = XoppColor.parse(node.getStrOrNull("color")),
                style = node.getStr("style", "plain"),
                config = node.getStrOrNull("config"),
                extraAttrs = extra,
            )
        }
    }

    /** Whether [node] names a bundled background an importer should adopt into its own store. */
    fun bundledBackgroundName(node: JsonNode.Obj): String? = node.getStrOrNull("asset")
}
