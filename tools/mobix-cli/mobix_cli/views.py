"""What `inspect` and `list` print.

Both commands publish one object under `--json`, and the human form is a rendering of the same
object — so a script and a person cannot be told two different things about one file.
"""

from __future__ import annotations

from typing import Any

from .container import Document


def inspect_view(document: Document) -> dict[str, Any]:
    """One object describing the document's envelope and what `inspect` checked."""
    canvas = document.canvas or {}
    integrity = document.integrity
    if document.canvas_mode == "PAGINATED":
        width = _number(canvas.get("widthPt"))
        height = _number(canvas.get("heightPt"))
        if (width <= 0 or height <= 0) and integrity is not None:
            width = integrity.first_part_width_pt
            height = integrity.first_part_height_pt
    else:
        tile = _number(canvas.get("chunkSizePt"), default=1024.0)
        width = height = tile if tile > 0 else 1024.0

    return {
        "path": document.path,
        "mimetype": document.mimetype,
        "formatVersion": document.format_version,
        "documentId": document.document_id,
        "creator": document.creator,
        "canvasMode": document.canvas_mode,
        "pages": len(document.pages),
        "chunks": len(document.chunks),
        "entries": document.entry_count,
        "fileBytes": document.file_bytes,
        "payloadBytes": document.payload_bytes,
        "canvas": {
            "widthPt": _number(canvas.get("widthPt")),
            "heightPt": _number(canvas.get("heightPt")),
            "chunkSizePt": _number(canvas.get("chunkSizePt"), default=1024.0),
        },
        "pageSize": {"widthPt": width, "heightPt": height},
        "requiredCapabilities": list(document.required_capabilities),
        "optionalCapabilities": list(document.optional_capabilities),
        "integrity": {
            "partsChecked": integrity.parts_checked if integrity else 0,
            "assetsChecked": integrity.assets_checked if integrity else 0,
            "digestsVerified": integrity.digests_verified if integrity else 0,
        },
    }


def list_view(document: Document) -> dict[str, Any]:
    """One object listing the parts and assets, sizes and digests as the archive states them."""
    return {
        "path": document.path,
        "mimetype": document.mimetype,
        "formatVersion": document.format_version,
        "documentId": document.document_id,
        "canvasMode": document.canvas_mode,
        "pages": [_part_row(part) for part in document.pages],
        "chunks": [_part_row(part) for part in document.chunks],
        "assets": [
            {
                "name": asset.name,
                "size": asset.size,
                "digest": asset.digest,
            }
            for asset in document.assets
        ],
    }


def _part_row(part) -> dict[str, Any]:
    return {
        "index": part.index,
        "id": part.id,
        "entry": part.entry,
        "size": part.size,
        "digest": part.sha256 or None,
    }


def render_inspect(view: dict[str, Any]) -> str:
    page_size = view["pageSize"]
    lines = [
        view["path"],
        f"  type:          {view['mimetype']}",
        f"  format:        version {view['formatVersion']}",
        f"  document id:   {view['documentId']}",
        f"  creator:       {view['creator'] or 'not recorded'}",
        f"  canvas:        {view['canvasMode']}",
        f"  page size:     {_points(page_size['widthPt'])} x {_points(page_size['heightPt'])} pt",
        f"  content:       {view['pages']} page(s), {view['chunks']} chunk(s)",
        f"  archive:       {view['entries']} entries, {_bytes(view['fileBytes'])}, "
        f"{_bytes(view['payloadBytes'])} of payload",
        f"  capabilities:  required {_caps(view['requiredCapabilities'])}; "
        f"optional {_caps(view['optionalCapabilities'])}",
        f"  checked:       {view['integrity']['partsChecked']} part(s), "
        f"{view['integrity']['assetsChecked']} asset(s), "
        f"{view['integrity']['digestsVerified']} digest(s)",
    ]
    return "\n".join(lines)


def render_list(view: dict[str, Any]) -> str:
    lines = [view["path"]]
    lines.extend(_part_section("pages", view["pages"]))
    lines.extend(_part_section("chunks", view["chunks"]))
    if view["assets"]:
        lines.append(f"assets ({len(view['assets'])})")
        for asset in view["assets"]:
            lines.append(f"  {asset['name']}  {_bytes(asset['size'])}  sha256:{asset['digest']}")
    else:
        lines.append("assets (0)")
    return "\n".join(lines)


def _part_section(title: str, rows: list[dict[str, Any]]) -> list[str]:
    lines = [f"{title} ({len(rows)})"]
    for row in rows:
        digest = f"  sha256:{row['digest']}" if row["digest"] else ""
        lines.append(
            f"  {row['index']}. {row['entry']}  {row['id']}  {_bytes(row['size'])}{digest}"
        )
    return lines


def _number(value: Any, default: float = 0.0) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return default
    return float(value)


def _points(value: float) -> str:
    return f"{value:.2f}".rstrip("0").rstrip(".")


def _bytes(value: int) -> str:
    if value < 1024:
        return f"{value} B"
    if value < 1024 * 1024:
        return f"{value / 1024:.1f} kB"
    return f"{value / (1024 * 1024):.1f} MB"


def _caps(values: list[str]) -> str:
    return ", ".join(values) if values else "none"
