"""Open a `.mobix` archive and answer what the format says about it, without decoding elements.

This module is the reader's boundary: it is the only place that touches `zipfile`, and it turns each
failure into `CannotOpen` (the bytes are not an archive) or `Refused` (they are an archive, but not a
document this format accepts). `integrity=True` is the difference between the two commands: `inspect`
hashes the parts and the assets and parses each part within the JSON depth cap, while `list` never
inflates an entry besides the envelope and reads sizes and digests from the central directory.
"""

from __future__ import annotations

import hashlib
import re
import zipfile
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .errors import CannotOpen, Refused
from .jsonbytes import decode_json

MIMETYPE_ENTRY = "mimetype"
MANIFEST_ENTRY = "manifest.json"
CANVAS_ENTRY = "canvas.json"
BRIDGE_ENTRY = "xopp-bridge/metadata.json"

MIMETYPE = b"application/vnd.mobixournal+zip"
MIMETYPE_TYPE = MIMETYPE.decode("ascii")

CANVAS_MODES = (
    "PAGINATED",
    "INFINITE_VERTICAL",
    "INFINITE_HORIZONTAL",
    "INFINITE_2D",
)
SUPPORTED_FORMAT_VERSION = 1

# The cap policy of `docs/mobix-format.md`, *What a reader must refuse*, one threshold each.
MAX_ENTRIES = 20_000
MAX_ENTRY_BYTES = 256 * 1024 * 1024
MAX_PAYLOAD_BYTES = 1024 * 1024 * 1024
MAX_EXPANSION = 1000
MIMETYPE_PROBE_BYTES = 256

DEFAULT_CHUNK_SIZE_PT = 1024.0

# `<namespace>/<feature>:<version>` — docs/mobix-capabilities.md, the identifier's grammar.
CAPABILITY = re.compile(
    r"^[a-z0-9]+(?:-[a-z0-9]+)*(?:\.[a-z0-9]+(?:-[a-z0-9]+)*)+"
    r"/[a-z0-9]+(?:[.-][a-z0-9]+)*"
    r":[1-9][0-9]*$"
)

# `assets/<prefix>_<64 lowercase hex><suffix>` — docs/mobix-assets.md, *The name is the content*.
ASSET = re.compile(r"^assets/(img|aud)_([0-9a-f]{64})\.([A-Za-z0-9]+)$")

_CAPABILITY_LISTS = ("requiredCapabilities", "optionalCapabilities")


@dataclass(frozen=True)
class Part:
    """One entry of `pages[]` or `chunks[]`, in manifest order."""

    index: int
    kind: str
    id: str
    entry: str
    sha256: str
    size: int


@dataclass(frozen=True)
class Asset:
    """One `assets/` entry, with the digest its own name claims."""

    name: str
    prefix: str
    suffix: str
    digest: str
    size: int


@dataclass(frozen=True)
class Integrity:
    """What `inspect` actually read, so a report cannot imply more than it checked."""

    parts_checked: int
    assets_checked: int
    digests_verified: int
    first_part_width_pt: float
    first_part_height_pt: float


@dataclass(frozen=True)
class Document:
    """The envelope of one readable `.mobix` file."""

    path: str
    mimetype: str
    format_version: int
    document_id: str
    canvas_mode: str
    manifest: dict[str, Any]
    canvas: dict[str, Any] | None
    bridge: dict[str, Any] | None
    pages: list[Part]
    chunks: list[Part]
    assets: list[Asset]
    entry_count: int
    file_bytes: int
    payload_bytes: int
    required_capabilities: list[str]
    optional_capabilities: list[str]
    integrity: Integrity | None = field(default=None)

    @property
    def creator(self) -> str | None:
        """The `.xopp` root's creator, when the file came through the bridge sidecar."""
        if not self.bridge:
            return None
        creator = self.bridge.get("creator")
        return creator if isinstance(creator, str) else None

    @property
    def content_is_paginated(self) -> bool:
        return self.canvas_mode == "PAGINATED"

    def parts(self) -> list[Part]:
        """The content parts: pages in a paginated mode, chunks in an infinite one."""
        return self.pages if self.content_is_paginated else self.chunks


def open_document(path: str | Path, integrity: bool = False) -> Document:
    """Open and validate `path` as a `.mobix` document.

    Raises `CannotOpen` when the path is missing, unreadable or not a ZIP, and `Refused` when the
    archive is a ZIP this format's rules reject. `integrity=True` also reads every part and asset
    (see `Integrity`), which is what `inspect` reports; `list` leaves it off.
    """
    file = Path(path)
    try:
        file_bytes = file.stat().st_size
        archive = zipfile.ZipFile(file)
    except OSError as exc:
        raise CannotOpen(str(exc)) from None
    except (zipfile.BadZipFile, zipfile.LargeZipFile, ValueError) as exc:
        raise CannotOpen(str(exc)) from None

    with archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        _check_entry_names(names)
        if len(infos) > MAX_ENTRIES:
            raise Refused(
                f"the archive holds {len(infos)} entries, more than the {MAX_ENTRIES} cap"
            )
        by_name = {info.filename: info for info in infos}
        payload_bytes = _check_entry_caps(infos)
        _check_local_flags(archive, infos)
        mimetype = _read_mimetype(archive, by_name)

        manifest = _read_manifest(archive, by_name)
        values = _manifest_values(manifest)
        canvas = _read_canvas(archive, by_name, values["canvasMode"])
        bridge = _read_bridge(archive, by_name)
        pages, chunks = _read_parts(manifest, by_name)
        assets = _read_assets(infos)

        checked = None
        if integrity:
            checked = _verify(
                archive, by_name, pages + chunks, assets, values["canvasMode"], canvas
            )

    return Document(
        path=str(path),
        mimetype=mimetype,
        format_version=values["formatVersion"],
        document_id=values["documentId"],
        canvas_mode=values["canvasMode"],
        manifest=manifest,
        canvas=canvas,
        bridge=bridge,
        pages=pages,
        chunks=chunks,
        assets=assets,
        entry_count=len(infos),
        file_bytes=file_bytes,
        payload_bytes=payload_bytes,
        required_capabilities=values["requiredCapabilities"],
        optional_capabilities=values["optionalCapabilities"],
        integrity=checked,
    )


# ---- container rules -----------------------------------------------------------------------


def _check_entry_names(names: list[str]) -> None:
    """Refuse duplicate and unsafe names, naming the entry (container protocol, rule 3)."""
    seen: set[str] = set()
    for name in names:
        problem = name_problem(name)
        if problem:
            raise Refused(f"entry name {name!r} is not usable: {problem}")
        if name in seen:
            raise Refused(f"entry name {name!r} appears twice in the archive")
        seen.add(name)


def name_problem(name: str) -> str | None:
    if not name:
        return "is empty"
    if "\\" in name:
        return "contains a backslash"
    if name.startswith("/"):
        return "is an absolute path"
    if len(name) >= 2 and name[0].isalpha() and name[1] == ":":
        return "is an absolute Windows path"
    if any(ord(char) < 0x20 or ord(char) == 0x7F for char in name):
        return "contains a control character"
    if any(segment in (".", "..") for segment in name.split("/")):
        return "contains a '.' or '..' segment"
    return None


def _check_entry_caps(infos: list[zipfile.ZipInfo]) -> int:
    """Enforce the archived-size, expansion and total-payload caps; return the payload total."""
    total = 0
    for info in infos:
        if info.flag_bits & 0x1:
            raise Refused(f"entry {info.filename!r} is encrypted")
        if info.file_size > MAX_ENTRY_BYTES:
            raise Refused(
                f"entry {info.filename!r} inflates to {info.file_size} bytes, "
                f"more than the {MAX_ENTRY_BYTES}-byte cap"
            )
        if (
            info.file_size > 0
            and info.compress_size > 0
            and info.file_size / info.compress_size > MAX_EXPANSION
        ):
            raise Refused(
                f"entry {info.filename!r} expands {info.file_size // info.compress_size}x, "
                f"more than the {MAX_EXPANSION}x cap"
            )
        total += info.file_size
    if total > MAX_PAYLOAD_BYTES:
        raise Refused(
            f"the archive holds {total} bytes of payload, more than the "
            f"{MAX_PAYLOAD_BYTES}-byte cap"
        )
    return total


def _check_local_flags(archive: zipfile.ZipFile, infos: list[zipfile.ZipInfo]) -> None:
    """Refuse an entry whose **local** header says encrypted or is not a local header at all.

    A central directory is not the only place the flag can be set, and a file-backed reader opens
the entry at its local header: the hostile corpus carries a page whose flag exists only there, so
reading the central directory alone would accept a document this format refuses.
    """
    fp = archive.fp
    for info in infos:
        fp.seek(info.header_offset)
        header = fp.read(30)
        if len(header) < 30 or header[:4] != b"PK\x03\x04":
            raise Refused(f"entry {info.filename!r} has no readable local header")
        flags = int.from_bytes(header[6:8], "little")
        if flags & 0x1:
            raise Refused(f"entry {info.filename!r} is encrypted")


def _read_entry(
    archive: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    limit: int,
) -> bytes:
    """Read one entry, bounded by `limit`, turning ZIP failures into a refusal naming it."""
    try:
        with archive.open(info) as stream:
            data = stream.read(limit + 1)
    except (RuntimeError, NotImplementedError) as exc:  # encrypted, or a method this build cannot read
        raise Refused(f"entry {info.filename!r} cannot be read: {exc}") from None
    except (zipfile.BadZipFile, zlib.error, EOFError, OSError) as exc:
        raise Refused(f"entry {info.filename!r} failed its integrity check: {exc}") from None
    if len(data) > limit:
        raise Refused(f"entry {info.filename!r} is larger than the {limit}-byte cap")
    return data


def _read_mimetype(
    archive: zipfile.ZipFile, by_name: dict[str, zipfile.ZipInfo]
) -> str:
    """Read the type from the `mimetype` entry's bytes, never from the file's name."""
    info = by_name.get(MIMETYPE_ENTRY)
    if info is None:
        raise Refused(
            "no 'mimetype' entry: this archive declares no type and is not a .mobix document"
        )
    data = _read_entry(archive, info, MIMETYPE_PROBE_BYTES)
    if data != MIMETYPE:
        shown = data[:80].decode("ascii", "replace")
        raise Refused(
            f"'mimetype' is {shown!r}, not {MIMETYPE_TYPE!r}: this is not a .mobix document"
        )
    return MIMETYPE_TYPE


# ---- the envelope --------------------------------------------------------------------------


def _read_manifest(
    archive: zipfile.ZipFile, by_name: dict[str, zipfile.ZipInfo]
) -> dict[str, Any]:
    info = by_name.get(MANIFEST_ENTRY)
    if info is None:
        raise Refused("no 'manifest.json': an archive without it is not a document")
    manifest = decode_json(_read_entry(archive, info, MAX_ENTRY_BYTES), MANIFEST_ENTRY)
    if not isinstance(manifest, dict):
        raise Refused("manifest.json is not a JSON object")
    return manifest


def _manifest_values(manifest: dict[str, Any]) -> dict[str, Any]:
    """The three required fields, the two exclusive part lists and the declared capabilities."""
    format_version = manifest.get("formatVersion")
    if type(format_version) is not int:
        raise Refused("manifest.json has no integer 'formatVersion'")
    if format_version != SUPPORTED_FORMAT_VERSION:
        raise Refused(
            f"formatVersion {format_version} is not one this reader knows "
            f"({SUPPORTED_FORMAT_VERSION})"
        )

    document_id = manifest.get("documentId")
    if not isinstance(document_id, str) or not document_id:
        raise Refused("manifest.json has no non-empty 'documentId'")

    canvas_mode = manifest.get("canvasMode")
    if canvas_mode not in CANVAS_MODES:
        raise Refused(
            f"canvasMode {canvas_mode!r} is not one of {', '.join(CANVAS_MODES)}"
        )

    pages = _parts_list(manifest.get("pages"), "pages")
    chunks = _parts_list(manifest.get("chunks"), "chunks")
    if pages and chunks:
        raise Refused("manifest.json lists both 'pages' and 'chunks'")
    if canvas_mode == "PAGINATED" and chunks:
        raise Refused("a PAGINATED document lists 'chunks'")
    if canvas_mode != "PAGINATED" and pages:
        raise Refused(f"a {canvas_mode} document lists 'pages'")

    return {
        "formatVersion": format_version,
        "documentId": document_id,
        "canvasMode": canvas_mode,
        "requiredCapabilities": _capabilities(manifest, "requiredCapabilities"),
        "optionalCapabilities": _capabilities(manifest, "optionalCapabilities"),
    }


def _parts_list(value: Any, where: str) -> list[dict[str, Any]]:
    if value is None:
        return []
    if not isinstance(value, list):
        raise Refused(f"manifest.json '{where}' is not an array")
    for index, part in enumerate(value, start=1):
        if not isinstance(part, dict):
            raise Refused(f"{where}[{index}] is not a JSON object")
        for key in ("id", "entry"):
            field_value = part.get(key)
            if not isinstance(field_value, str) or not field_value:
                raise Refused(f"{where}[{index}] has no non-empty '{key}'")
        digest = part.get("sha256")
        if digest is not None and not isinstance(digest, str):
            raise Refused(f"{where}[{index}].sha256 is not a string")
    return value


def _capabilities(manifest: dict[str, Any], key: str) -> list[str]:
    value = manifest.get(key)
    if value is None:
        return []
    if not isinstance(value, list):
        raise Refused(f"manifest.json '{key}' is not an array")
    out: list[str] = []
    for index, entry in enumerate(value, start=1):
        if not isinstance(entry, str) or not CAPABILITY.match(entry):
            raise Refused(
                f"manifest.json '{key}[{index}]' is {entry!r}, not a capability "
                "identifier (<namespace>/<feature>:<version>)"
            )
        if entry in out:
            raise Refused(f"manifest.json '{key}' declares {entry!r} twice")
        out.append(entry)
    return out


def _read_canvas(
    archive: zipfile.ZipFile,
    by_name: dict[str, zipfile.ZipInfo],
    canvas_mode: str,
) -> dict[str, Any] | None:
    info = by_name.get(CANVAS_ENTRY)
    if info is None:
        return None
    canvas = decode_json(_read_entry(archive, info, MAX_ENTRY_BYTES), CANVAS_ENTRY)
    if not isinstance(canvas, dict):
        raise Refused("canvas.json is not a JSON object")
    mode = canvas.get("mode")
    if mode != canvas_mode:
        raise Refused(
            f"canvas.json says mode {mode!r} while the manifest says {canvas_mode!r}"
        )
    for key in ("widthPt", "heightPt", "chunkSizePt"):
        value = canvas.get(key)
        if value is not None and (isinstance(value, bool) or not isinstance(value, (int, float))):
            raise Refused(f"canvas.json '{key}' is not a number")
    return canvas


def _read_bridge(
    archive: zipfile.ZipFile, by_name: dict[str, zipfile.ZipInfo]
) -> dict[str, Any] | None:
    info = by_name.get(BRIDGE_ENTRY)
    if info is None:
        return None
    bridge = decode_json(_read_entry(archive, info, MAX_ENTRY_BYTES), BRIDGE_ENTRY)
    if not isinstance(bridge, dict):
        raise Refused(f"{BRIDGE_ENTRY} is not a JSON object")
    return bridge


def _read_parts(
    manifest: dict[str, Any],
    by_name: dict[str, zipfile.ZipInfo],
) -> tuple[list[Part], list[Part]]:
    pages = _parts(manifest.get("pages"), "page", by_name)
    chunks = _parts(manifest.get("chunks"), "chunk", by_name)
    return pages, chunks


def _parts(
    value: Any, kind: str, by_name: dict[str, zipfile.ZipInfo]
) -> list[Part]:
    if not isinstance(value, list):
        return []
    out: list[Part] = []
    for index, part in enumerate(value, start=1):
        entry = part["entry"]
        info = by_name.get(entry)
        if info is None:
            raise Refused(
                f"{kind} {index} ({part['id']!r}) names {entry!r}, "
                "which the archive does not hold"
            )
        out.append(
            Part(
                index=index,
                kind=kind,
                id=part["id"],
                entry=entry,
                sha256=part.get("sha256", ""),
                size=info.file_size,
            )
        )
    return out


def _read_assets(infos: list[zipfile.ZipInfo]) -> list[Asset]:
    out: list[Asset] = []
    for info in infos:
        name = info.filename
        if not name.startswith("assets/") or name.endswith("/"):
            continue
        match = ASSET.match(name)
        if match is None:
            raise Refused(
                f"asset entry {name!r} is not named "
                "assets/<img|aud>_<sha256>.<suffix>"
            )
        out.append(
            Asset(
                name=name,
                prefix=match.group(1),
                suffix=match.group(3),
                digest=match.group(2),
                size=info.file_size,
            )
        )
    return out


# ---- integrity (inspect only) --------------------------------------------------------------


def _verify(
    archive: zipfile.ZipFile,
    by_name: dict[str, zipfile.ZipInfo],
    parts: list[Part],
    assets: list[Asset],
    canvas_mode: str,
    canvas: dict[str, Any] | None,
) -> Integrity:
    """Hash and parse what was listed, so `inspect` reports what it checked.

    A part is read, compared with the digest the manifest records for it, and parsed within the JSON
    depth cap — reading it is what makes a deep-nesting page a refusal rather than a listing. An
    asset is read and compared with the digest its own name claims. Nothing is decoded.
    """
    digests = 0
    first_size = (0.0, 0.0)
    first_seen = False
    for part in parts:
        info = by_name[part.entry]
        data = _read_entry(archive, info, MAX_ENTRY_BYTES)
        if part.sha256:
            actual = hashlib.sha256(data).hexdigest()
            if actual != part.sha256:
                raise Refused(
                    f"{part.kind} {part.entry!r} hashes to {actual}, "
                    f"not the {part.sha256} its manifest entry records"
                )
            digests += 1
        page = decode_json(data, part.entry)
        if not first_seen and isinstance(page, dict):
            width = page.get("widthPt")
            height = page.get("heightPt")
            if (
                not isinstance(width, bool)
                and not isinstance(height, bool)
                and isinstance(width, (int, float))
                and isinstance(height, (int, float))
            ):
                first_size = (float(width), float(height))
                first_seen = True

    for asset in assets:
        info = by_name[asset.name]
        data = _read_entry(archive, info, MAX_ENTRY_BYTES)
        actual = hashlib.sha256(data).hexdigest()
        if actual != asset.digest:
            raise Refused(
                f"asset {asset.name!r} hashes to {actual}, "
                f"not the {asset.digest} its name claims"
            )
        digests += 1

    width_pt, height_pt = _page_size(canvas_mode, canvas, first_size)
    return Integrity(
        parts_checked=len(parts),
        assets_checked=len(assets),
        digests_verified=digests,
        first_part_width_pt=width_pt,
        first_part_height_pt=height_pt,
    )


def _page_size(
    canvas_mode: str,
    canvas: dict[str, Any] | None,
    first_part: tuple[float, float],
) -> tuple[float, float]:
    """The sheet a page is drawn on: the canvas size, a page's own size, or a chunk tile."""
    if canvas_mode != "PAGINATED":
        tile = DEFAULT_CHUNK_SIZE_PT
        if canvas is not None:
            declared = canvas.get("chunkSizePt")
            if isinstance(declared, (int, float)) and declared > 0:
                tile = float(declared)
        return tile, tile
    if canvas is not None:
        width = canvas.get("widthPt", 0.0)
        height = canvas.get("heightPt", 0.0)
        if isinstance(width, (int, float)) and isinstance(height, (int, float)):
            if width > 0 and height > 0:
                return float(width), float(height)
    return first_part
