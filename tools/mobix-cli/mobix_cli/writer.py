"""The minimal writer: a new one-page document, and a title change that touches nothing else.

Two commands, one promise each.

`new` produces a document whose element families are exactly the ones this tool can author — a
stroke and an image with one bundled payload. It is not a STEM renderer: the other seven families
(`text`, `texImage`, `table`, `plot`, `parametricShape`, `circuit`, `mathBlock`) are only ever
**preserved** by `retitle`, never produced or rewritten.

`retitle` changes `title` in `manifest.json` and copies every other entry as the bytes the archive
already holds — pages, chunks, assets, the bridge sidecar and any entry this tool has no model for —
so an element family it cannot read is not touched, an unknown JSON key is not dropped, and an asset
byte it does not use is not re-encoded.

Both are deterministic: fixed ZIP timestamps, `mimetype` first and STORED, every other entry sorted
by name, and no clock or random identity in the output, so the same input writes the same bytes.
"""

from __future__ import annotations

import base64
import hashlib
import json
import uuid
import zipfile
import zlib
from dataclasses import dataclass
from pathlib import Path

from .container import MAX_ENTRY_BYTES, name_problem, open_document
from .errors import Refused

MIMETYPE = b"application/vnd.mobixournal+zip"
MIMETYPE_ENTRY = "mimetype"
MANIFEST_ENTRY = "manifest.json"
CANVAS_ENTRY = "canvas.json"
PAGE_ENTRY = "pages/page-0001.json"

PAGE_WIDTH_PT = 595.275591
PAGE_HEIGHT_PT = 841.889764

# Fixed so a created document is reproducible: the format permits 0 for both stamps, and the
# identity comes from the title, not from a random source.
CREATED_MS = 0
MODIFIED_MS = 0
MIN_APP_VERSION = "2.0.0"
DOCUMENT_ID_NAMESPACE = uuid.UUID("6f1a4f2e-0f4b-5b8d-9f2f-7f0e6a1b2c3d")

FAMILIES = (
    "stroke",
    "text",
    "image",
    "texImage",
    "table",
    "plot",
    "parametricShape",
    "circuit",
    "mathBlock",
)

# What `new` can author. Everything else in FAMILIES is preserved only.
PRODUCED = ("stroke", "image")

# A 1x1 PNG, so the bundled asset is a real picture rather than arbitrary bytes.
ASSET_BYTES = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)

ZIP_TIME = (1980, 1, 1, 0, 0, 0)


@dataclass(frozen=True)
class Written:
    """What a write command did, in the terms the task has to state."""

    path: str
    title: str
    produced: list[str]
    preserved: list[str]
    rewritten: list[str]
    unknown_entries: list[str]
    assets: int = 0

    def as_json(self) -> dict:
        return {
            "path": self.path,
            "title": self.title,
            "produced": list(self.produced),
            "preserved": list(self.preserved),
            "rewritten": list(self.rewritten),
            "unknownEntries": list(self.unknown_entries),
            "assets": self.assets,
        }

    def render(self) -> str:
        lines = [f"wrote {self.path} — title {self.title!r}"]
        if self.produced:
            lines.append(f"  produced families: {', '.join(self.produced)}")
        lines.append(
            f"  preserved families (never rewritten): {', '.join(self.preserved)}"
        )
        if self.rewritten:
            lines.append(f"  rewritten: {', '.join(self.rewritten)}")
        if self.unknown_entries:
            lines.append(
                f"  unknown entries kept: {len(self.unknown_entries)} "
                f"({', '.join(self.unknown_entries)})"
            )
        return "\n".join(lines)


def create_document(out: str | Path, title: str, force: bool = False) -> Written:
    """Write a new one-page document with one stroke, one image and its bundled asset."""
    if not title:
        raise Refused("a title is required (--title)")
    asset_name = f"assets/img_{hashlib.sha256(ASSET_BYTES).hexdigest()}.png"
    stroke = {
        "type": "stroke",
        "id": "str_0001",
        "tool": "PEN",
        "color": "#000000ff",
        "lineWidth": 1.0,
        "lineStyle": "PLAIN",
        "points": [{"x": 100.0, "y": 120.0}, {"x": 240.0, "y": 170.0}],
        "bounds": {"left": 99.5, "top": 119.5, "right": 240.5, "bottom": 170.5},
    }
    image = {
        "type": "image",
        "id": "img_0001",
        "bounds": {"left": 300.0, "top": 140.0, "right": 364.0, "bottom": 204.0},
        "asset": asset_name,
    }
    page = {
        "widthPt": PAGE_WIDTH_PT,
        "heightPt": PAGE_HEIGHT_PT,
        "background": {"type": "solid", "color": "#ffffffff", "style": "plain"},
        "layers": [{"elements": [stroke, image]}],
    }
    page_bytes = _json_bytes(page)
    document_id = str(uuid.uuid5(DOCUMENT_ID_NAMESPACE, f"mobix-cli:{title}"))
    manifest = {
        "formatVersion": 1,
        "minAppVersion": MIN_APP_VERSION,
        "documentId": document_id,
        "canvasMode": "PAGINATED",
        "created": CREATED_MS,
        "modified": MODIFIED_MS,
        "title": title,
        "pages": [
            {
                "id": "pg_0001",
                "entry": PAGE_ENTRY,
                "modified": MODIFIED_MS,
                "sha256": hashlib.sha256(page_bytes).hexdigest(),
            }
        ],
    }
    canvas = {
        "mode": "PAGINATED",
        "widthPt": PAGE_WIDTH_PT,
        "heightPt": PAGE_HEIGHT_PT,
    }
    entries = {
        MIMETYPE_ENTRY: MIMETYPE,
        MANIFEST_ENTRY: _json_bytes(manifest),
        CANVAS_ENTRY: _json_bytes(canvas),
        PAGE_ENTRY: page_bytes,
        asset_name: ASSET_BYTES,
    }
    _write_archive(Path(out), entries, force=force)
    return Written(
        path=str(out),
        title=title,
        produced=list(PRODUCED),
        preserved=[family for family in FAMILIES if family not in PRODUCED],
        rewritten=sorted(entries),
        unknown_entries=[],
        assets=1,
    )


def retitle(
    source: str | Path,
    title: str,
    out: str | Path | None = None,
    force: bool = False,
) -> Written:
    """Change only `title`, copying every other entry as the bytes the source holds."""
    if not title:
        raise Refused("a title is required (--title)")
    document = open_document(source)
    blobs = _read_entries(Path(source))
    manifest = dict(document.manifest)
    manifest["title"] = title
    entries = dict(blobs)
    entries[MANIFEST_ENTRY] = _json_bytes(manifest)

    destination = Path(out) if out is not None else Path(source)
    in_place = destination == Path(source)
    _write_archive(destination, entries, force=force or in_place)
    unknown = sorted(name for name in blobs if _is_unknown_entry(name))
    return Written(
        path=str(destination),
        title=title,
        produced=[],
        preserved=list(FAMILIES),
        rewritten=[MANIFEST_ENTRY],
        unknown_entries=unknown,
        assets=len(document.assets),
    )


def _json_bytes(value: object) -> bytes:
    return json.dumps(value, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def _read_entries(source: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(source) as archive:
        out: dict[str, bytes] = {}
        for info in archive.infolist():
            if info.filename.endswith("/"):
                continue
            try:
                with archive.open(info) as stream:
                    data = stream.read(MAX_ENTRY_BYTES + 1)
            except (RuntimeError, NotImplementedError, zipfile.BadZipFile, zlib.error, EOFError, OSError) as exc:
                raise Refused(f"entry {info.filename!r} cannot be read: {exc}") from None
            if len(data) > MAX_ENTRY_BYTES:
                raise Refused(
                    f"entry {info.filename!r} is larger than the {MAX_ENTRY_BYTES}-byte cap"
                )
            out[info.filename] = data
    return out


def _write_archive(destination: Path, entries: dict[str, bytes], force: bool) -> None:
    """Write a deterministic archive: mimetype first and STORED, everything else sorted."""
    if MIMETYPE_ENTRY not in entries:
        raise Refused("an archive must carry a 'mimetype' entry")
    if destination.exists():
        if destination.is_dir():
            raise Refused(f"{destination} is a directory, not a file to write")
        if not force:
            raise Refused(f"{destination} already exists; pass --force to overwrite it")
    try:
        destination.parent.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        raise Refused(f"destination {destination} cannot be created: {exc}") from None

    order = [MIMETYPE_ENTRY] + sorted(name for name in entries if name != MIMETYPE_ENTRY)
    temporary = destination.with_name(f".{destination.name}.part")
    try:
        with zipfile.ZipFile(temporary, "w") as archive:
            for name in order:
                info = zipfile.ZipInfo(name, date_time=ZIP_TIME)
                info.create_system = 0
                info.external_attr = 0
                if name == MIMETYPE_ENTRY:
                    info.compress_type = zipfile.ZIP_STORED
                    archive.writestr(info, entries[name])
                else:
                    info.compress_type = zipfile.ZIP_DEFLATED
                    archive.writestr(info, entries[name], compresslevel=6)
        temporary.replace(destination)
    except OSError as exc:
        _discard(temporary)
        raise Refused(f"cannot write {destination}: {exc}") from None


def _discard(path: Path) -> None:
    try:
        path.unlink()
    except OSError:
        pass


def _is_unknown_entry(name: str) -> bool:
    """An entry the format does not document, so a rewrite may only copy it."""
    problem = name_problem(name)
    if problem:
        return True
    return not (
        name in (MIMETYPE_ENTRY, MANIFEST_ENTRY, CANVAS_ENTRY)
        or name.startswith("pages/")
        or name.startswith("chunks/")
        or name.startswith("assets/")
        or name.startswith("xopp-bridge/")
    )
