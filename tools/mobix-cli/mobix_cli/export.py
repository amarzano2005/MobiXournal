"""`export-json`: one stable document dump, so two documents can be diffed.

The dump is built from the archive, never from a renderer: the manifest and canvas are the objects
the document carries, a page contributes its size and a flat list of its elements' ids and families,
and an asset contributes its name, size, digest and kind. Nothing is decoded — a picture is not
rasterised and a recording is not played — and the ordering is fixed (manifest order for content,
name order for assets), so the same document always prints the same bytes.
"""

from __future__ import annotations

import json
import zipfile
from pathlib import Path
from typing import Any

from .container import MAX_ENTRY_BYTES, Document, Part, open_document
from .errors import Refused
from .jsonbytes import decode_json

FORMAT = "mobix-document/1"
FAMILY_UNKNOWN = "unknown"


def export_document(path: str | Path) -> str:
    """The stable JSON text for `path`, ending in one newline."""
    document = open_document(path)
    with zipfile.ZipFile(path) as archive:
        by_name = {info.filename: info for info in archive.infolist()}
        dump = {
            "format": FORMAT,
            "formatVersion": document.format_version,
            "documentId": document.document_id,
            "canvasMode": document.canvas_mode,
            "title": document.manifest.get("title"),
            "minAppVersion": document.manifest.get("minAppVersion"),
            "requiredCapabilities": document.required_capabilities,
            "optionalCapabilities": document.optional_capabilities,
            "manifest": document.manifest,
            "canvas": document.canvas,
            "pages": [_part(archive, by_name, part) for part in document.pages],
            "chunks": [_part(archive, by_name, part) for part in document.chunks],
            "assets": [_asset(document, asset) for asset in sorted(document.assets, key=lambda a: a.name)],
        }
    return json.dumps(dump, indent=2, ensure_ascii=False) + "\n"


def _part(
    archive: zipfile.ZipFile, by_name: dict[str, zipfile.ZipInfo], part: Part
) -> dict[str, Any]:
    info = by_name[part.entry]
    page = decode_json(_read(archive, info, part.entry), part.entry)
    if not isinstance(page, dict):
        page = {}
    return {
        "index": part.index,
        "id": part.id,
        "entry": part.entry,
        "widthPt": _number(page.get("widthPt")),
        "heightPt": _number(page.get("heightPt")),
        "elements": _elements(page),
    }


def _elements(page: dict[str, Any]) -> list[dict[str, Any]]:
    """Every element's id and family, in layer and paint order — no field is interpreted further."""
    layers = page.get("layers")
    if not isinstance(layers, list):
        return []
    out: list[dict[str, Any]] = []
    for layer_index, layer in enumerate(layers):
        if not isinstance(layer, dict):
            continue
        elements = layer.get("elements")
        if not isinstance(elements, list):
            continue
        for element in elements:
            family = element.get("type") if isinstance(element, dict) else None
            identifier = element.get("id") if isinstance(element, dict) else None
            out.append(
                {
                    "layer": layer_index,
                    "id": identifier if isinstance(identifier, str) else None,
                    "family": family if isinstance(family, str) else FAMILY_UNKNOWN,
                }
            )
    return out


def _asset(document: Document, asset) -> dict[str, Any]:
    return {
        "name": asset.name,
        "size": asset.size,
        "digest": asset.digest,
        "kind": "image" if asset.prefix == "img" else "audio",
    }


def _read(archive: zipfile.ZipFile, info: zipfile.ZipInfo, entry: str) -> bytes:
    try:
        with archive.open(info) as stream:
            data = stream.read(MAX_ENTRY_BYTES + 1)
    except (RuntimeError, NotImplementedError, zipfile.BadZipFile, EOFError, OSError) as exc:
        raise Refused(f"entry {entry!r} cannot be read: {exc}") from None
    if len(data) > MAX_ENTRY_BYTES:
        raise Refused(f"entry {entry!r} is larger than the {MAX_ENTRY_BYTES}-byte cap")
    return data


def _number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return float(value)
