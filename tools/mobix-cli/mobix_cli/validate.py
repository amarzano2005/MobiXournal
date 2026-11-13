"""`validate`: every rule the conformance checklist states, reported as findings.

`inspect` stops at the first reason a document cannot be read; a validator has to keep going, because
a file can break three rules at once and a person fixing it wants all three. So this module collects
`Finding`s instead of raising, and each finding carries the **row number of `spec/CONFORMANCE.md`** it
decides plus the rule name the format's own chapters use, so the tool, the checklist and the spec can
be read side by side.

Exit codes belong to the caller: this module reports, `cli.py` decides.
"""

from __future__ import annotations

import hashlib
import json
import zipfile
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .container import (
    ASSET,
    CANVAS_ENTRY,
    CANVAS_MODES,
    CAPABILITY,
    MAX_ENTRIES,
    MAX_ENTRY_BYTES,
    MAX_EXPANSION,
    MAX_PAYLOAD_BYTES,
    MIMETYPE,
    MIMETYPE_ENTRY,
    MIMETYPE_PROBE_BYTES,
    MANIFEST_ENTRY,
    BRIDGE_ENTRY,
    name_problem,
)
from .errors import CannotOpen, Refused
from .jsonbytes import MAX_JSON_DEPTH, check_depth

SUPPORTED_METHODS = (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)

ERROR = "error"
WARNING = "warning"


@dataclass(frozen=True)
class Finding:
    """One broken rule, located where the reader can point at it."""

    rule: str
    name: str
    severity: str
    location: str
    message: str

    def line(self) -> str:
        return f"{self.rule} {self.severity} {self.location}: {self.message}"

    def as_json(self) -> dict[str, str]:
        return {
            "rule": self.rule,
            "name": self.name,
            "severity": self.severity,
            "location": self.location,
            "message": self.message,
        }


@dataclass
class Report:
    """Everything `validate` found, plus what it could check."""

    path: str
    entries: int
    findings: list[Finding] = field(default_factory=list)
    schema: str = "unavailable"

    @property
    def ok(self) -> bool:
        return not self.findings

    def as_json(self) -> dict[str, Any]:
        return {
            "path": self.path,
            "ok": self.ok,
            "entries": self.entries,
            "schema": self.schema,
            "findings": [finding.as_json() for finding in self.findings],
        }

    def render(self) -> str:
        if not self.findings:
            return f"{self.path}: clean ({self.entries} entries)"
        return "\n".join(finding.line() for finding in self.findings)


class _ReadError(Exception):
    """The entry could not be read; a finding has already been recorded for it."""


def validate_document(path: str | Path) -> Report:
    """Validate `path` against the container, envelope, reference and schema rules."""
    try:
        archive = zipfile.ZipFile(path)
    except OSError as exc:
        raise CannotOpen(str(exc)) from None
    except (zipfile.BadZipFile, zipfile.LargeZipFile, ValueError) as exc:
        raise CannotOpen(str(exc)) from None

    report = Report(path=str(path), entries=0)
    with archive:
        _validate(archive, report)
    return report


def _validate(archive: zipfile.ZipFile, report: Report) -> None:
    infos = archive.infolist()
    report.entries = len(infos)
    names = [info.filename for info in infos]
    by_name: dict[str, zipfile.ZipInfo] = {}
    for info in infos:
        by_name.setdefault(info.filename, info)

    _check_names(names, report)
    skipped = _check_caps(infos, report)
    skipped |= _check_zip(archive, infos, report)

    _check_mimetype(archive, infos, names, by_name, skipped, report)

    manifest = _check_manifest(archive, by_name, skipped, report)
    canvas = _check_canvas(archive, by_name, skipped, report)

    parsed: dict[str, Any] = {}
    pages: list[dict[str, Any]] = []
    chunks: list[dict[str, Any]] = []
    references: list[tuple[str, str]] = []
    if manifest is not None:
        pages = _part_list(manifest.get("pages"), "pages", report)
        chunks = _part_list(manifest.get("chunks"), "chunks", report)
        _check_mode(manifest, pages, chunks, report)
        _check_capabilities(manifest, report)
        for kind, parts in (("page", pages), ("chunk", chunks)):
            for index, part in enumerate(parts, start=1):
                _check_part(
                    archive,
                    by_name,
                    kind,
                    index,
                    part,
                    skipped,
                    report,
                    parsed,
                    references,
                )
        if canvas is not None:
            _check_canvas_fields(canvas, report)
            _check_canvas_matches_manifest(manifest, canvas, report)
    _check_bridge(archive, by_name, skipped, report)
    _check_assets(archive, infos, skipped, report)
    _check_references(references, by_name, report)

    report.schema = _check_schemas(parsed, canvas, manifest, report)


# ---- container ----------------------------------------------------------------------------


def _check_names(names: list[str], report: Report) -> None:
    """Report unsafe and duplicate names; a bad name is reported, not repaired."""
    seen: set[str] = set()
    for name in names:
        problem = name_problem(name)
        if problem:
            report.findings.append(
                Finding("row-18", "ENTRY_NAME", ERROR, name, f"entry name {problem}")
            )
        elif name in seen:
            report.findings.append(
                Finding(
                    "row-18",
                    "ENTRY_NAME",
                    ERROR,
                    name,
                    "the same entry name appears twice",
                )
            )
        seen.add(name)


def _check_caps(infos: list[zipfile.ZipInfo], report: Report) -> set[str]:
    """Report the size caps; return the entries a cap made unreadable.

    An entry over a cap is not read afterwards: reading it is what the cap exists to prevent, and
    every rule applied to its bytes would be a second finding for the same fault.
    """
    skipped: set[str] = set()
    if len(infos) > MAX_ENTRIES:
        report.findings.append(
            Finding(
                "row-17",
                "CONTAINER_LIMIT",
                ERROR,
                "archive",
                f"the archive holds {len(infos)} entries, more than the {MAX_ENTRIES} cap",
            )
        )
    total = 0
    for info in infos:
        total += info.file_size
        if info.file_size > MAX_ENTRY_BYTES:
            report.findings.append(
                Finding(
                    "row-17",
                    "CONTAINER_LIMIT",
                    ERROR,
                    info.filename,
                    f"inflates to {info.file_size} bytes, more than the "
                    f"{MAX_ENTRY_BYTES}-byte cap",
                )
            )
            skipped.add(info.filename)
        if (
            info.file_size > 0
            and info.compress_size > 0
            and info.file_size / info.compress_size > MAX_EXPANSION
        ):
            report.findings.append(
                Finding(
                    "row-17",
                    "CONTAINER_LIMIT",
                    ERROR,
                    info.filename,
                    f"expands {info.file_size // info.compress_size}x, more than the "
                    f"{MAX_EXPANSION}x cap",
                )
            )
            skipped.add(info.filename)
    if total > MAX_PAYLOAD_BYTES:
        report.findings.append(
            Finding(
                "row-17",
                "CONTAINER_LIMIT",
                ERROR,
                "archive",
                f"the archive holds {total} bytes of payload, more than the "
                f"{MAX_PAYLOAD_BYTES}-byte cap",
            )
        )
    return skipped


def _check_zip(
    archive: zipfile.ZipFile, infos: list[zipfile.ZipInfo], report: Report
) -> set[str]:
    """Encryption (central or local header) and an unsupported method; return unreadable names."""
    skipped: set[str] = set()
    for info in infos:
        encrypted = bool(info.flag_bits & 0x1)
        if not encrypted:
            header = _local_header(archive, info)
            if header is None:
                report.findings.append(
                    Finding(
                        "row-20",
                        "ZIP_READ",
                        ERROR,
                        info.filename,
                        "the local header is missing or not a ZIP local header",
                    )
                )
                skipped.add(info.filename)
                continue
            encrypted = bool(header & 0x1)
        if encrypted:
            report.findings.append(
                Finding("row-20", "ENCRYPTED_ENTRY", ERROR, info.filename, "the entry is encrypted")
            )
            skipped.add(info.filename)
        if info.compress_type not in SUPPORTED_METHODS:
            report.findings.append(
                Finding(
                    "row-20",
                    "UNSUPPORTED_METHOD",
                    ERROR,
                    info.filename,
                    f"compression method {info.compress_type} is not STORED or DEFLATED",
                )
            )
            skipped.add(info.filename)
    return skipped


def _local_header(archive: zipfile.ZipFile, info: zipfile.ZipInfo) -> int | None:
    """The local header's flag word, or None when it is not a local header."""
    fp = archive.fp
    fp.seek(info.header_offset)
    header = fp.read(30)
    if len(header) < 30 or header[:4] != b"PK\x03\x04":
        return None
    return int.from_bytes(header[6:8], "little")


def _read(
    archive: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    limit: int,
    report: Report,
) -> bytes:
    try:
        with archive.open(info) as stream:
            data = stream.read(limit + 1)
    except (RuntimeError, NotImplementedError, zipfile.BadZipFile, zlib.error, EOFError, OSError) as exc:
        report.findings.append(
            Finding("row-20", "ZIP_READ", ERROR, info.filename, f"cannot be read: {exc}")
        )
        raise _ReadError from None
    if len(data) > limit:
        report.findings.append(
            Finding(
                "row-17",
                "CONTAINER_LIMIT",
                ERROR,
                info.filename,
                f"is larger than the {limit}-byte cap",
            )
        )
        raise _ReadError
    return data


def _json_entry(
    archive: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    report: Report,
) -> Any:
    """Read and parse one JSON entry, recording the depth and syntax rules separately."""
    data = _read(archive, info, MAX_ENTRY_BYTES, report)
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        report.findings.append(
            Finding("row-20", "ZIP_READ", ERROR, info.filename, f"is not UTF-8 text: {exc}")
        )
        raise _ReadError from None
    try:
        check_depth(text, info.filename)
    except Refused as exc:  # the depth cap is a limit finding, not a syntax one
        report.findings.append(
            Finding(
                "row-17",
                "CONTAINER_LIMIT",
                ERROR,
                info.filename,
                f"nests JSON deeper than {MAX_JSON_DEPTH} levels",
            )
        )
        raise _ReadError from exc
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        report.findings.append(
            Finding("row-20", "ZIP_READ", ERROR, info.filename, f"is not valid JSON: {exc}")
        )
        raise _ReadError from None


# ---- mimetype -----------------------------------------------------------------------------


def _check_mimetype(
    archive: zipfile.ZipFile,
    infos: list[zipfile.ZipInfo],
    names: list[str],
    by_name: dict[str, zipfile.ZipInfo],
    skipped: set[str],
    report: Report,
) -> None:
    info = by_name.get(MIMETYPE_ENTRY)
    if info is None:
        report.findings.append(
            Finding(
                "row-01",
                "TYPE_ENTRY",
                ERROR,
                MIMETYPE_ENTRY,
                "the archive has no 'mimetype' entry, so it declares no type",
            )
        )
        return
    if names.index(MIMETYPE_ENTRY) != 0:
        report.findings.append(
            Finding(
                "row-01",
                "TYPE_ENTRY",
                WARNING,
                MIMETYPE_ENTRY,
                "a writer must put 'mimetype' first, so a type probe inflates nothing",
            )
        )
    if info.compress_type != zipfile.ZIP_STORED:
        report.findings.append(
            Finding(
                "row-01",
                "TYPE_ENTRY",
                WARNING,
                MIMETYPE_ENTRY,
                "a writer must store 'mimetype' uncompressed",
            )
        )
    if MIMETYPE_ENTRY in skipped:
        return
    try:
        data = _read(archive, info, MIMETYPE_PROBE_BYTES, report)
    except _ReadError:
        return
    if data != MIMETYPE:
        shown = data[:80].decode("ascii", "replace")
        report.findings.append(
            Finding(
                "row-02",
                "WRONG_TYPE",
                ERROR,
                MIMETYPE_ENTRY,
                f"declares {shown!r}, not 'application/vnd.mobixournal+zip'",
            )
        )


# ---- the envelope -------------------------------------------------------------------------


def _check_manifest(
    archive: zipfile.ZipFile,
    by_name: dict[str, zipfile.ZipInfo],
    skipped: set[str],
    report: Report,
) -> dict[str, Any] | None:
    info = by_name.get(MANIFEST_ENTRY)
    if info is None:
        report.findings.append(
            Finding(
                "row-03",
                "NO_MANIFEST",
                ERROR,
                MANIFEST_ENTRY,
                "an archive without 'manifest.json' is not a document",
            )
        )
        return None
    if MANIFEST_ENTRY in skipped:
        return None
    try:
        manifest = _json_entry(archive, info, report)
    except _ReadError:
        return None
    if not isinstance(manifest, dict):
        report.findings.append(
            Finding("row-21", "MANIFEST_FIELD", ERROR, MANIFEST_ENTRY, "is not a JSON object")
        )
        return None
    _check_manifest_fields(manifest, report)
    return manifest


def _check_manifest_fields(manifest: dict[str, Any], report: Report) -> None:
    version = manifest.get("formatVersion")
    if type(version) is not int or version != 1:
        report.findings.append(
            Finding(
                "row-21",
                "MANIFEST_FIELD",
                ERROR,
                "manifest.json#formatVersion",
                f"{version!r} is not the integer 1 this reader knows",
            )
        )
    document_id = manifest.get("documentId")
    if not isinstance(document_id, str) or not document_id:
        report.findings.append(
            Finding(
                "row-21",
                "MANIFEST_FIELD",
                ERROR,
                "manifest.json#documentId",
                "a non-empty string is required",
            )
        )
    mode = manifest.get("canvasMode")
    if mode not in CANVAS_MODES:
        report.findings.append(
            Finding(
                "row-21",
                "MANIFEST_FIELD",
                ERROR,
                "manifest.json#canvasMode",
                f"{mode!r} is not one of {', '.join(CANVAS_MODES)}",
            )
        )


def _part_list(value: Any, where: str, report: Report) -> list[dict[str, Any]]:
    if value is None:
        return []
    if not isinstance(value, list):
        report.findings.append(
            Finding("row-21", "MANIFEST_FIELD", ERROR, f"manifest.json#{where}", "is not an array")
        )
        return []
    valid: list[dict[str, Any]] = []
    for index, part in enumerate(value, start=1):
        location = f"{where}[{index}]"
        if not isinstance(part, dict):
            report.findings.append(
                Finding("row-21", "MANIFEST_FIELD", ERROR, location, "is not a JSON object")
            )
            continue
        ok = True
        for key in ("id", "entry"):
            item = part.get(key)
            if not isinstance(item, str) or not item:
                report.findings.append(
                    Finding(
                        "row-21",
                        "MANIFEST_FIELD",
                        ERROR,
                        f"{location}.{key}",
                        "a non-empty string is required",
                    )
                )
                ok = False
        digest = part.get("sha256")
        if digest is not None and not isinstance(digest, str):
            report.findings.append(
                Finding(
                    "row-21",
                    "MANIFEST_FIELD",
                    ERROR,
                    f"{location}.sha256",
                    "is not a string",
                )
            )
            ok = False
        if ok:
            valid.append(part)
    return valid


def _check_mode(
    manifest: dict[str, Any],
    pages: list[dict[str, Any]],
    chunks: list[dict[str, Any]],
    report: Report,
) -> None:
    mode = manifest.get("canvasMode")
    if pages and chunks:
        report.findings.append(
            Finding(
                "row-22",
                "CANVAS_MODE",
                ERROR,
                "manifest.json",
                "lists both 'pages' and 'chunks'",
            )
        )
    if mode == "PAGINATED" and chunks:
        report.findings.append(
            Finding(
                "row-22",
                "CANVAS_MODE",
                ERROR,
                "manifest.json#chunks",
                "a PAGINATED document lists chunks",
            )
        )
    if mode in CANVAS_MODES and mode != "PAGINATED" and pages:
        report.findings.append(
            Finding(
                "row-22",
                "CANVAS_MODE",
                ERROR,
                "manifest.json#pages",
                f"a {mode} document lists pages",
            )
        )


def _check_capabilities(manifest: dict[str, Any], report: Report) -> None:
    for key in ("requiredCapabilities", "optionalCapabilities"):
        value = manifest.get(key)
        if value is None:
            continue
        if not isinstance(value, list):
            report.findings.append(
                Finding(
                    "row-33",
                    "CAPABILITY_GRAMMAR",
                    ERROR,
                    f"manifest.json#{key}",
                    "is not an array",
                )
            )
            continue
        seen: set[str] = set()
        for index, entry in enumerate(value, start=1):
            location = f"manifest.json#{key}[{index}]"
            if not isinstance(entry, str) or not CAPABILITY.match(entry):
                report.findings.append(
                    Finding(
                        "row-33",
                        "CAPABILITY_GRAMMAR",
                        ERROR,
                        location,
                        f"{entry!r} is not <namespace>/<feature>:<version>",
                    )
                )
                continue
            if entry in seen:
                report.findings.append(
                    Finding(
                        "row-35",
                        "CAPABILITY_DUPLICATE",
                        ERROR,
                        location,
                        f"{entry!r} is declared twice",
                    )
                )
            seen.add(entry)


def _check_canvas(
    archive: zipfile.ZipFile,
    by_name: dict[str, zipfile.ZipInfo],
    skipped: set[str],
    report: Report,
) -> dict[str, Any] | None:
    info = by_name.get(CANVAS_ENTRY)
    if info is None or CANVAS_ENTRY in skipped:
        return None
    try:
        canvas = _json_entry(archive, info, report)
    except _ReadError:
        return None
    if not isinstance(canvas, dict):
        report.findings.append(
            Finding("row-21", "CANVAS_FIELD", ERROR, CANVAS_ENTRY, "is not a JSON object")
        )
        return None
    return canvas


def _check_canvas_matches_manifest(
    manifest: dict[str, Any], canvas: dict[str, Any], report: Report
) -> None:
    mode = canvas.get("mode")
    if mode in CANVAS_MODES and mode != manifest.get("canvasMode"):
        report.findings.append(
            Finding(
                "row-22",
                "CANVAS_MODE",
                ERROR,
                "canvas.json#mode",
                f"says {mode!r} while the manifest says {manifest.get('canvasMode')!r}",
            )
        )


def _check_canvas_fields(canvas: dict[str, Any], report: Report) -> None:
    if canvas.get("mode") not in CANVAS_MODES:
        report.findings.append(
            Finding(
                "row-22",
                "CANVAS_MODE",
                ERROR,
                "canvas.json#mode",
                f"{canvas.get('mode')!r} is not one of {', '.join(CANVAS_MODES)}",
            )
        )
    for key in ("widthPt", "heightPt", "chunkSizePt"):
        value = canvas.get(key)
        if value is not None and (isinstance(value, bool) or not isinstance(value, (int, float))):
            report.findings.append(
                Finding("row-21", "CANVAS_FIELD", ERROR, f"canvas.json#{key}", "is not a number")
            )


def _check_bridge(
    archive: zipfile.ZipFile,
    by_name: dict[str, zipfile.ZipInfo],
    skipped: set[str],
    report: Report,
) -> None:
    info = by_name.get(BRIDGE_ENTRY)
    if info is None or BRIDGE_ENTRY in skipped:
        return
    try:
        bridge = _json_entry(archive, info, report)
    except _ReadError:
        return
    if not isinstance(bridge, dict):
        report.findings.append(
            Finding("row-20", "ZIP_READ", ERROR, BRIDGE_ENTRY, "is not a JSON object")
        )


# ---- parts, assets and their references ----------------------------------------------------


def _check_part(
    archive: zipfile.ZipFile,
    by_name: dict[str, zipfile.ZipInfo],
    kind: str,
    index: int,
    part: dict[str, Any],
    skipped: set[str],
    report: Report,
    parsed: dict[str, Any],
    references: list[tuple[str, str]],
) -> None:
    entry = part["entry"]
    location = f"{kind}[{index}]"
    info = by_name.get(entry)
    if info is None:
        report.findings.append(
            Finding(
                "row-04",
                "PART_ENTRY",
                ERROR,
                f"{location}.entry",
                f"names {entry!r}, which the archive does not hold",
            )
        )
        return
    if entry in skipped:
        return
    try:
        data = _read(archive, info, MAX_ENTRY_BYTES, report)
    except _ReadError:
        return
    claim = part.get("sha256") or ""
    if claim and hashlib.sha256(data).hexdigest() != claim:
        report.findings.append(
            Finding(
                "row-04",
                "PART_DIGEST",
                ERROR,
                entry,
                "does not hash to the sha256 the manifest records",
            )
        )
    try:
        document = _parse_json(data, entry, report)
    except _ReadError:
        return
    parsed[entry] = document
    references.extend(_references(document, entry))


def _parse_json(data: bytes, entry: str, report: Report) -> Any:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        report.findings.append(
            Finding("row-20", "ZIP_READ", ERROR, entry, f"is not UTF-8 text: {exc}")
        )
        raise _ReadError from None
    try:
        check_depth(text, entry)
    except Refused as exc:
        report.findings.append(
            Finding(
                "row-17",
                "CONTAINER_LIMIT",
                ERROR,
                entry,
                f"nests JSON deeper than {MAX_JSON_DEPTH} levels",
            )
        )
        raise _ReadError from exc
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        report.findings.append(
            Finding("row-20", "ZIP_READ", ERROR, entry, f"is not valid JSON: {exc}")
        )
        raise _ReadError from None


def _references(value: Any, location: str) -> list[tuple[str, str]]:
    """Every `asset`/`assetId` name a part mentions, wherever it sits in the JSON tree."""
    out: list[tuple[str, str]] = []
    if isinstance(value, dict):
        for key, item in value.items():
            where = f"{location}#{key}"
            if key in ("asset", "assetId") and isinstance(item, str):
                out.append((where, item))
            out.extend(_references(item, where))
    elif isinstance(value, list):
        for index, item in enumerate(value):
            out.extend(_references(item, f"{location}[{index}]"))
    return out


def _check_references(
    references: list[tuple[str, str]],
    by_name: dict[str, zipfile.ZipInfo],
    report: Report,
) -> None:
    for where, name in references:
        if name not in by_name:
            report.findings.append(
                Finding(
                    "row-14",
                    "MISSING_ENTRY",
                    ERROR,
                    where,
                    f"names {name!r}, which the archive does not hold",
                )
            )


def _check_assets(
    archive: zipfile.ZipFile,
    infos: list[zipfile.ZipInfo],
    skipped: set[str],
    report: Report,
) -> None:
    for info in infos:
        name = info.filename
        if not name.startswith("assets/") or name.endswith("/"):
            continue
        match = ASSET.match(name)
        if match is None:
            report.findings.append(
                Finding(
                    "row-14",
                    "NAME_GRAMMAR",
                    ERROR,
                    name,
                    "is not named assets/<img|aud>_<sha256>.<suffix>",
                )
            )
            continue
        if name in skipped:
            continue
        try:
            data = _read(archive, info, MAX_ENTRY_BYTES, report)
        except _ReadError:
            continue
        if hashlib.sha256(data).hexdigest() != match.group(2):
            report.findings.append(
                Finding(
                    "row-14",
                    "DIGEST_MISMATCH",
                    ERROR,
                    name,
                    "does not hash to the digest its own name claims",
                )
            )


# ---- the optional schema layer -------------------------------------------------------------


def _check_schemas(
    parsed: dict[str, Any],
    canvas: dict[str, Any] | None,
    manifest: dict[str, Any] | None,
    report: Report,
) -> str:
    """Validate against `spec/schema/` when `jsonschema` is installed; say which it was."""
    schema_dir = Path(__file__).resolve().parents[3] / "spec" / "schema"
    try:
        from jsonschema import Draft202012Validator
        from referencing import Registry, Resource
    except ImportError:
        return "unavailable"
    if not schema_dir.is_dir():
        return "not-found"
    try:
        registry = _registry(schema_dir, Registry, Resource)
        targets: list[tuple[str, Any, str]] = []
        if manifest is not None:
            targets.append(("manifest.json", manifest, "manifest.schema.json"))
        if canvas is not None:
            targets.append(("canvas.json", canvas, "canvas.schema.json"))
        for entry, document in parsed.items():
            targets.append((entry, document, _schema_file(entry)))
        for entry, document, file_name in targets:
            schema = json.loads((schema_dir / file_name).read_text(encoding="utf-8"))
            validator = Draft202012Validator(schema, registry=registry)
            for error in validator.iter_errors(document):
                location = f"{entry}{error.json_path[1:]}"
                report.findings.append(
                    Finding("row-38", "SCHEMA", ERROR, location, error.message)
                )
    except Exception as exc:  # a schema layer that cannot run is reported, never guessed at
        report.findings.append(
            Finding(
                "row-38",
                "SCHEMA",
                WARNING,
                "spec/schema",
                f"schema validation could not run: {exc}",
            )
        )
        return "unavailable"
    return "checked"


def _registry(schema_dir: Path, registry_type, resource_type):
    resources = []
    for path in sorted(schema_dir.rglob("*.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        identifier = document.get("$id")
        if isinstance(identifier, str):
            resources.append((identifier, resource_type.from_contents(document)))
    return registry_type().with_resources(resources)


def _schema_file(entry: str) -> str:
    return "chunk.schema.json" if entry.startswith("chunks/") else "page.schema.json"
