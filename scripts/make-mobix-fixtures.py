#!/usr/bin/env python3
"""Generate the golden `.mobix` v1 fixture corpus.

The corpus is committed under
`mobix-core/src/test/resources/fixtures/mobix-v1/`, and this script is how it is
regenerated: dependency-free, deterministic (fixed ZIP timestamps, no locale or
clock), so a re-run on any machine reproduces the same bytes and a fixture can be
reviewed as a diff.

Each fixture exercises one compatibility construct; a companion `README.md` in the
output directory says which. The tests that drive them live in
`mobix-core/src/test/java/com/mobixournal/format/mobix/MobixV1FixturesTest.kt`.

Usage:
    python3 scripts/make-mobix-fixtures.py
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "mobix-core", "src", "test", "resources", "fixtures", "mobix-v1")

MIMETYPE = b"application/vnd.mobixournal+zip"
DOCUMENT_ID = "0f6a3f2e-6b0c-4f43-9c1a-2d7f3f7c1a10"
CREATED = 1_775_952_000_000
EDITED = 1_776_038_400_000
FIXED_TIME = (1980, 1, 1, 0, 0, 0)


def j(value) -> bytes:
    """The corpus writes compact JSON, the shape the app's own writer emits."""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write_mobix(filename: str, entries: list[tuple[str, bytes]]) -> None:
    """Write the archive with `mimetype` first and STORED, every other entry DEFLATED."""
    path = os.path.join(OUT, filename)
    with zipfile.ZipFile(path, "w") as archive:
        info = zipfile.ZipInfo("mimetype", date_time=FIXED_TIME)
        info.compress_type = zipfile.ZIP_STORED
        info.external_attr = 0o644 << 16
        archive.writestr(info, MIMETYPE)
        for name, data in entries:
            info = zipfile.ZipInfo(name, date_time=FIXED_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, data)
    print(f"wrote {os.path.relpath(path, ROOT)}")


def solid(color="#ffffffff", style="ruled", config="0.50,0.5,0.5,0.10,1.0,4"):
    return {"type": "solid", "color": color, "style": style, "config": config}


def page(width=595.27559100, height=841.88976400, background=None, layers=None):
    return {
        "widthPt": width,
        "heightPt": height,
        "background": background if background is not None else solid(),
        "layers": layers if layers is not None else [{"elements": []}],
    }


def layer(elements, name=None):
    out = {"elements": elements}
    if name is not None:
        out = {"name": name, "elements": elements}
    return out


def point(x, y, width=1.2, pressure=None, tilt=None, azimuth=None, dt_ms=None):
    p = {"x": x, "y": y, "width": width}
    if pressure is not None:
        p["pressure"] = pressure
    if tilt is not None:
        p["tilt"] = tilt
    if azimuth is not None:
        p["azimuth"] = azimuth
    if dt_ms is not None:
        p["dtMs"] = dt_ms
    return p


def stroke(element_id, points, tool="PEN", color="#1a237eff", line_style="PLAIN",
           cap_style="round", line_width=None, attrs=None, **extra):
    xs = [p["x"] for p in points]
    ys = [p["y"] for p in points]
    half = max(p["width"] for p in points) / 2.0
    element = {
        "type": "stroke",
        "id": element_id,
        "tool": tool,
        "color": color,
        "lineStyle": line_style,
    }
    if cap_style is not None:
        element["capStyle"] = cap_style
    if line_width is not None:
        element["lineWidth"] = line_width
    element["bounds"] = {
        "left": min(xs) - half,
        "top": min(ys) - half,
        "right": max(xs) + half,
        "bottom": max(ys) + half,
    }
    element["points"] = points
    if attrs:
        element["attrs"] = attrs
    element.update(extra)
    return element


def manifest(page_parts, *, title="Golden v1 fixture", canvas_mode="PAGINATED",
             min_app="2.0.0", created=CREATED, modified=CREATED, default_paper=None,
             extra=None):
    out = {
        "formatVersion": 1,
        "minAppVersion": min_app,
        "documentId": DOCUMENT_ID,
        "canvasMode": canvas_mode,
        "created": created,
        "modified": modified,
        "title": title,
        "pages": page_parts,
    }
    if default_paper is not None:
        out["defaultPaper"] = default_paper
    if extra:
        out.update(extra)
    return out


def part(page_id, entry, data, modified=CREATED, extra=None):
    out = {"id": page_id, "entry": entry, "modified": modified, "sha256": sha(data)}
    if extra:
        out.update(extra)
    return out


def canvas(mode="PAGINATED", width=595.27559100, height=841.88976400, chunk=1024.0):
    return {"mode": mode, "widthPt": width, "heightPt": height, "chunkSizePt": chunk,
            "origin": {"pageNo": 1, "offsetPt": 0.0}}


# ---- the shared payloads the asset fixture bundles --------------------------------

PNG_1X1 = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=="
)
PDF_ONE_PAGE = (
    b"%PDF-1.4\n"
    b"1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n"
    b"2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n"
    b"3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>endobj\n"
    b"trailer<< /Root 1 0 R >>\n%%EOF\n"
)


def wav_tone(samples=8) -> bytes:
    """A tiny 8-bit mono WAV: the codec only needs the RIFF/WAVE container."""
    data = bytes([128] * samples)
    header = (
        b"RIFF" + (36 + len(data)).to_bytes(4, "little") + b"WAVE"
        + b"fmt " + (16).to_bytes(4, "little") + (1).to_bytes(2, "little")
        + (1).to_bytes(2, "little") + (8000).to_bytes(4, "little")
        + (8000).to_bytes(4, "little") + (1).to_bytes(2, "little")
        + (8).to_bytes(2, "little") + b"data" + len(data).to_bytes(4, "little") + data
    )
    return header


def asset_name(prefix, suffix, data):
    return f"assets/{prefix}_{sha(data)}.{suffix}"


# ---- 01: the minimal document ----------------------------------------------------


def fixture_01():
    page_bytes = j(page(layers=[layer([])]))
    write_mobix("01-minimal-single-page.mobix", [
        ("manifest.json", j(manifest([part("pg_1", "pages/page-0001.json", page_bytes)],
                                     min_app="1.0.0", title="Minimal"))),
        ("pages/page-0001.json", page_bytes),
    ])


# ---- 02: order comes from the manifest, not the names ----------------------------


def fixture_02():
    pages = {
        "pages/page-0001.json": j(page(layers=[layer([stroke("stk_01", [
            point(72.0, 96.0), point(180.0, 120.0)])], name="Ink")])),
        "pages/page-0002.json": j(page(layers=[layer([stroke("stk_02", [
            point(72.0, 140.0), point(220.0, 160.0)])])])),
        "pages/page-0003.json": j(page(layers=[layer([{"type": "text", "id": "txt_01",
                                                     "font": "Sans Bold", "size": 12.0,
                                                     "x": 72.0, "y": 200.0,
                                                     "color": "#000000ff",
                                                     "content": "third page"}])])),
    }
    order = [
        ("pg_7", "pages/page-0003.json"),
        ("pg_2", "pages/page-0001.json"),
        ("pg_9", "pages/page-0002.json"),
    ]
    parts = [part(pid, entry, pages[entry]) for pid, entry in order]
    write_mobix("02-multi-page-order.mobix", [
        ("manifest.json", j(manifest(parts, title="Three pages, spliced order"))),
        ("canvas.json", j(canvas())),
    ] + [(entry, pages[entry]) for _, entry in order])


# ---- 03: bundled image, PDF background and audio ---------------------------------


def fixture_03():
    image_name = asset_name("img", "png", PNG_1X1)
    pdf_name = asset_name("img", "pdf", PDF_ONE_PAGE)
    wav = wav_tone()
    audio_name = asset_name("aud", "wav", wav)

    image_element = {
        "type": "image", "id": "img_01",
        "bounds": {"left": 40.0, "top": 40.0, "right": 240.0, "bottom": 200.0},
        "asset": image_name,
    }
    audio_stroke = stroke(
        "stk_audio", [point(300.0, 320.0), point(360.0, 340.0)],
        line_width=1.4,
        attrs={"ts": "0", "fn": "recording.wav"},
    )
    audio_stroke["audioRef"] = {"filename": "recording.wav", "assetId": audio_name,
                                "startMs": 0}
    tex_element = {
        "type": "texImage", "id": "tex_01",
        "bounds": {"left": 100.0, "top": 300.0, "right": 280.0, "bottom": 330.0},
        "latex": "\\int_0^1 x^2 dx", "color": "#000000ff", "asset": image_name,
    }
    page_one = j(page(
        background={"type": "pdf", "domain": "absolute", "filename": "notes.pdf",
                    "pageNo": 2, "asset": pdf_name},
        layers=[layer([image_element, audio_stroke, tex_element])],
    ))
    page_two = j(page(layers=[layer([{"type": "image", "id": "img_02",
                                      "bounds": {"left": 10.0, "top": 10.0,
                                                 "right": 60.0, "bottom": 60.0},
                                      "asset": image_name}], name="Picture")]))
    write_mobix("03-bundled-assets.mobix", [
        ("manifest.json", j(manifest([
            part("pg_1", "pages/page-0001.json", page_one),
            part("pg_2", "pages/page-0002.json", page_two),
        ], title="Bundled payloads"))),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", page_one),
        ("pages/page-0002.json", page_two),
        (image_name, PNG_1X1),
        (pdf_name, PDF_ONE_PAGE),
        (audio_name, wav),
    ])


# ---- 04: a page edited and saved incrementally -----------------------------------


def fixture_04():
    art = b"one page of bundled artwork".ljust(64, b".")
    art_name = asset_name("img", "png", art)
    first = j(page(layers=[layer([stroke("stk_01", [point(72.0, 72.0),
                                                    point(120.0, 96.0)])])]))
    second = j(page(layers=[layer([stroke("stk_02", [point(72.0, 140.0),
                                                     point(160.0, 150.0)])])]))
    third = j(page(background={"type": "pixmap", "domain": "absolute",
                               "filename": "art.png", "asset": art_name},
                   layers=[layer([])]))
    write_mobix("04-incremental-save.mobix", [
        ("manifest.json", j(manifest([
            part("pg_1", "pages/page-0001.json", first, modified=CREATED),
            part("pg_2", "pages/page-0002.json", second, modified=EDITED),
            part("pg_3", "pages/page-0003.json", third, modified=CREATED),
        ], created=CREATED, modified=EDITED, title="Saved incrementally"))),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", first),
        ("pages/page-0002.json", second),
        ("pages/page-0003.json", third),
        (art_name, art),
    ])


# ---- 05: an entry from a future build --------------------------------------------


def fixture_05():
    page_bytes = j(page(layers=[layer([stroke("stk_01", [point(72.0, 72.0),
                                                         point(96.0, 96.0)])])]))
    write_mobix("05-unknown-entry.mobix", [
        ("manifest.json", j(manifest([part("pg_1", "pages/page-0001.json",
                                           page_bytes)], title="Future entry"))),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", page_bytes),
        ("future/thing.bin", b"\x00\x01future bytes\xff"),
        ("index/search.idx", b"not-a-real-index"),
    ])


# ---- 06: envelope fields this build does not model -------------------------------


def fixture_06():
    page_bytes = j(page(layers=[layer([stroke("stk_01", [point(72.0, 72.0),
                                                         point(96.0, 96.0)])])]))
    manifest_json = manifest(
        [part("pg_1", "pages/page-0001.json", page_bytes,
              extra={"fontHint": "serif", "ocr": {"status": "indexed"}})],
        title="Envelope extras",
        extra={"thumbnails": {"pages": ["a"]}, "futureFlag": True,
               "generator": {"name": "third-party-writer"}},
    )
    canvas_json = canvas()
    canvas_json["gridColor"] = "#eeeeeeff"
    canvas_json["origin"]["sheetEdgePt"] = 12.0
    write_mobix("06-unknown-envelope-fields.mobix", [
        ("manifest.json", j(manifest_json)),
        ("canvas.json", j(canvas_json)),
        ("pages/page-0001.json", page_bytes),
    ])


# ---- 07: an element type this build does not model -------------------------------


def fixture_07():
    unknown = {
        "type": "3dPlotElement",
        "id": "threed_01",
        "surface": "z = sin(x) * cos(y)",
        "bounds": {"left": 40.0, "top": 40.0, "right": 320.0, "bottom": 280.0},
        "strokeWidth": 1.25,
        "palette": ["#112233ff", "#445566ff"],
    }
    xml_carried = {"type": "image2", "syntax": "xml", "attrs": {"width": "12"},
                   "body": "<image2 width=\"12\"/>"}
    page_bytes = j(page(layers=[layer([unknown, xml_carried])]))
    write_mobix("07-unknown-element-type.mobix", [
        ("manifest.json", j(manifest([part("pg_1", "pages/page-0001.json",
                                           page_bytes)], title="Unknown types"))),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", page_bytes),
    ])


# ---- 08: an unknown top-level field on a known type ------------------------------


def fixture_08():
    extra_stroke = stroke("stk_01", [point(72.0, 72.0, 1.4, 0.55),
                                     point(120.0, 96.0, 1.6, 0.62)],
                          pressureSeed=0.42, tiltCurve="cubic")
    extra_text = {"type": "text", "id": "txt_01", "font": "Sans", "size": 12.0,
                  "x": 72.0, "y": 200.0, "color": "#000000ff",
                  "content": "line height", "lineHeight": 1.35,
                  "hyphenation": "auto"}
    page_bytes = j(page(layers=[layer([extra_stroke, extra_text])]))
    write_mobix("08-unknown-top-level-field.mobix", [
        ("manifest.json", j(manifest([part("pg_1", "pages/page-0001.json",
                                           page_bytes)], title="Unknown fields"))),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", page_bytes),
    ])


# ---- 09: unknown fields nested in a sub-object and an array item ------------------


def fixture_09():
    table = {
        "type": "table", "id": "tbl_01",
        "bounds": {"left": 72.0, "top": 400.0, "right": 372.0, "bottom": 500.0},
        "colWidths": [150.0, 150.0], "rowHeights": [50.0, 50.0],
        "headerRow": True, "borderColor": "#000000ff", "borderWidth": 1.0,
        "cells": [
            [{"text": "x", "align": "CENTER", "bold": True, "fillColor": None,
              "fontFamily": "serif", "paddingPt": 2.0},
             {"text": "y", "align": "CENTER", "bold": True, "fillColor": None}],
            [{"text": "1", "align": "CENTER", "bold": False, "fillColor": None},
             {"text": "2", "align": "CENTER", "bold": False, "fillColor": None}],
        ],
    }
    plot = {
        "type": "plot", "id": "plt_01",
        "bounds": {"left": 72.0, "top": 120.0, "right": 420.0, "bottom": 340.0},
        "xMin": -6.283185307179586, "xMax": 6.283185307179586, "samples": 400,
        "showCriticalPoints": True, "includeDerivative": False,
        "shadeIntegral": {"from": 0.0, "to": 3.141592653589793, "hatch": "diagonal"},
        "curves": [{"kind": "CARTESIAN", "source": "sin(x)", "color": "#00695cff",
                    "strokeWidth": 1.5, "lineStyle": "PLAIN",
                    "dashPattern": [4.0, 2.0], "label": "f(x)"}],
        "parameters": {"a": 1.0},
        "axisColor": "#424242ff",
    }
    page_bytes = j(page(layers=[layer([table, plot])]))
    write_mobix("09-unknown-nested-fields.mobix", [
        ("manifest.json", j(manifest([part("pg_1", "pages/page-0001.json",
                                           page_bytes)], title="Nested fields"))),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", page_bytes),
    ])


# ---- 10: a chunked, infinite-canvas document ------------------------------------


def fixture_10():
    first = {
        "cx": 0, "cy": 0, "widthPt": 1024.0, "heightPt": 1024.0,
        "background": solid(),
        "layers": [layer([stroke("stk_01", [point(100.0, 100.0),
                                            point(200.0, 140.0)])])],
    }
    second = {
        "cx": -1, "cy": 2, "widthPt": 1024.0, "heightPt": 1024.0,
        "layers": [layer([{"type": "text", "id": "txt_01", "font": "Sans",
                           "size": 14.0, "x": 20.0, "y": 40.0,
                           "color": "#000000ff", "content": "second tile"}])],
    }
    first_bytes = j(first)
    second_bytes = j(second)
    manifest_json = manifest(
        [], title="Infinite board", canvas_mode="INFINITE_VERTICAL",
    )
    manifest_json.pop("pages")
    manifest_json["chunks"] = [
        part("chk_0_0", "chunks/chunk_0_0.json", first_bytes),
        part("chk_-1_2", "chunks/chunk_-1_2.json", second_bytes),
    ]
    write_mobix("10-chunked-document.mobix", [
        ("manifest.json", j(manifest_json)),
        ("canvas.json", j(canvas("INFINITE_VERTICAL", width=1024.0, height=0.0))),
        ("chunks/chunk_0_0.json", first_bytes),
        ("chunks/chunk_-1_2.json", second_bytes),
    ])


# ---- 11: vocabulary values this build does not know ------------------------------


def fixture_11():
    tool_page = j(page(layers=[layer([stroke("stk_01", [point(72.0, 72.0)],
                                               tool="FOUNTAIN_PEN")])]))
    align_page = j(page(layers=[layer([{
        "type": "table", "id": "tbl_01",
        "bounds": {"left": 72.0, "top": 72.0, "right": 272.0, "bottom": 172.0},
        "colWidths": [200.0], "rowHeights": [100.0],
        "cells": [[{"text": "justified", "align": "JUSTIFY", "bold": False,
                    "fillColor": None}]],
    }])]))
    kind_page = j(page(layers=[layer([{
        "type": "plot", "id": "plt_01",
        "bounds": {"left": 72.0, "top": 72.0, "right": 372.0, "bottom": 272.0},
        "curves": [{"kind": "FOURIER", "source": "square(x)", "color": "#000000ff",
                    "strokeWidth": 1.0, "lineStyle": "PLAIN"}],
        "axisColor": "#000000ff",
    }])]))
    write_mobix("11-unknown-vocabulary-values.mobix", [
        ("manifest.json", j(manifest([
            part("pg_1", "pages/page-0001.json", tool_page),
            part("pg_2", "pages/page-0002.json", align_page),
            part("pg_3", "pages/page-0003.json", kind_page),
        ], title="Unknown vocabulary"))),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", tool_page),
        ("pages/page-0002.json", align_page),
        ("pages/page-0003.json", kind_page),
    ])


# ---- 12: a background type this build does not know ------------------------------


def fixture_12():
    background = {
        "type": "gradient",
        "color": "#ffffffff",
        "style": "isometric",
        "from": "#aabbccff",
        "to": "#ddeeffff",
        "angle": 30.0,
    }
    page_bytes = j(page(background=background, layers=[layer([])]))
    manifest_json = manifest([part("pg_1", "pages/page-0001.json", page_bytes)],
                             title="Unknown paper")
    manifest_json["defaultPaper"] = background
    write_mobix("12-unknown-background-type.mobix", [
        ("manifest.json", j(manifest_json)),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", page_bytes),
    ])


# ---- 13-17: the capability contract ----------------------------------------------


def table_element(element_id="tbl_01"):
    """A construct this build can read, used to show that the gate does not care about content."""
    return {
        "type": "table", "id": element_id,
        "bounds": {"left": 72.0, "top": 72.0, "right": 272.0, "bottom": 172.0},
        "colWidths": [200.0], "rowHeights": [100.0],
        "cells": [[{"text": "kept", "align": "CENTER", "bold": False,
                    "fillColor": None}]],
    }


def unknown_element(element_id="threed_01"):
    """A construct with no model — the case the retention channel exists for."""
    return {
        "type": "3dPlotElement", "id": element_id,
        "surface": "z = sin(x) * cos(y)",
        "bounds": {"left": 40.0, "top": 40.0, "right": 320.0, "bottom": 280.0},
    }


def capability_fixture(filename, *, title, elements, required=None, optional=None,
                       min_app="2.0.0", document_id=None):
    page_bytes = j(page(layers=[layer(elements)]))
    manifest_json = manifest([part("pg_1", "pages/page-0001.json", page_bytes)],
                             title=title, min_app=min_app)
    if document_id is not None:
        manifest_json["documentId"] = document_id
    if required:
        manifest_json["requiredCapabilities"] = required
    if optional:
        manifest_json["optionalCapabilities"] = optional
    if min_app is None:
        manifest_json.pop("minAppVersion", None)
    write_mobix(filename, [
        ("manifest.json", j(manifest_json)),
        ("canvas.json", j(canvas())),
        ("pages/page-0001.json", page_bytes),
    ])


def fixture_13():
    """A required capability from a namespace this build knows nothing about."""
    capability_fixture(
        "13-required-capability-unknown.mobix",
        title="Unknown required capability",
        elements=[table_element()],
        required=["example.org/stem.connector:1"],
    )


def fixture_14():
    """The same capability as an optional hint, over a construct this build keeps whole."""
    capability_fixture(
        "14-optional-capability-unknown.mobix",
        title="Unknown optional capability",
        elements=[unknown_element()],
        optional=["example.org/stem.connector:1"],
    )


def fixture_15():
    """A feature this build knows, at a version above the one it implements."""
    capability_fixture(
        "15-capability-higher-version.mobix",
        title="Capability above this build's version",
        elements=[table_element()],
        required=["org.mobixournal/stem.connector:2"],
    )


def fixture_16():
    """Both lists in a writer's own order, mixing known and unknown namespaces."""
    capability_fixture(
        "16-capability-additive-order.mobix",
        title="Declared in the writer's order",
        elements=[table_element(), unknown_element()],
        required=["org.mobixournal/audio.opus:1", "example.org/stem.connector:1"],
        optional=["example.org/future.notes:3", "org.mobixournal/paper.isometric:2"],
    )


def fixture_17():
    """A third-party writer: no minAppVersion, and a namespace that is not MobiXournal's."""
    capability_fixture(
        "17-third-party-writer.mobix",
        title="Written elsewhere",
        elements=[unknown_element()],
        optional=["com.example/lab.notebook:1"],
        min_app=None,
        document_id="5b1d0f4e-2a1c-4d3b-9f0e-7c6a5b4d3e2f",
    )


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    for stale in os.listdir(OUT):
        if stale.endswith(".mobix"):
            os.remove(os.path.join(OUT, stale))
    for fixture in (
        fixture_01, fixture_02, fixture_03, fixture_04, fixture_05, fixture_06,
        fixture_07, fixture_08, fixture_09, fixture_10, fixture_11, fixture_12,
        fixture_13, fixture_14, fixture_15, fixture_16, fixture_17,
    ):
        fixture()


if __name__ == "__main__":
    main()
