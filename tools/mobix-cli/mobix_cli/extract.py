"""`extract`: write chosen entries under a destination, and never anywhere else.

Two rules do the work. The name written is the **archive's own** entry name, never a name read out of
the document's JSON, and the path it resolves to has to stay under the destination the caller gave —
including through a symbolic link, which is the one way a relative name can still leave the folder.
A payload is hashed against the digest the format records for it before the file appears, and the
write lands through a temporary file, so a refused or interrupted extraction leaves nothing behind.
"""

from __future__ import annotations

import hashlib
import zipfile
import zlib
from dataclasses import dataclass
from pathlib import Path

from .container import (
    MAX_ENTRY_BYTES,
    MAX_PAYLOAD_BYTES,
    Document,
    name_problem,
    open_document,
)
from .errors import Refused


@dataclass(frozen=True)
class Extracted:
    """One entry that reached the disk."""

    entry: str
    target: str
    size: int
    digest: str


def extract_entries(
    path: str | Path,
    destination: str | Path,
    entries: list[str],
    write_all: bool,
    force: bool,
) -> list[Extracted]:
    """Write `entries` (or every entry) of `path` under `destination`.

    Refuses an entry whose name could escape the destination, a target that already exists without
    `force`, bytes that do not match the digest the document records, and anything over the stated
    caps. Nothing outside `destination` is written, ever.
    """
    document = open_document(path)
    dest = Path(destination)
    _prepare_destination(dest)
    expected = _expected_digests(document)

    with zipfile.ZipFile(path) as archive:
        infos = {info.filename: info for info in archive.infolist()}
        selected = _selection(infos, entries, write_all)
        written: list[Extracted] = []
        total = 0
        for name in selected:
            info = infos[name]
            target = _safe_target(dest, name)
            if target.exists() and not force:
                raise Refused(f"{target} already exists; pass --force to overwrite it")
            if target.is_dir():
                raise Refused(f"{target} is a directory, not a file to write")
            data = _read(archive, info)
            total += len(data)
            if total > MAX_PAYLOAD_BYTES:
                raise Refused(
                    f"extraction would inflate more than the {MAX_PAYLOAD_BYTES}-byte total cap"
                )
            digest = hashlib.sha256(data).hexdigest()
            claim = expected.get(name)
            if claim and digest != claim:
                raise Refused(
                    f"entry {name!r} hashes to {digest}, not the {claim} the document records"
                )
            written.append(_write(target, data, name, digest))
    return written


def _prepare_destination(destination: Path) -> None:
    try:
        destination.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        raise Refused(f"destination {destination} cannot be created: {exc}") from None
    if not destination.is_dir():
        raise Refused(f"destination {destination} is not a directory")


def _selection(
    infos: dict[str, zipfile.ZipInfo], entries: list[str], write_all: bool
) -> list[str]:
    if write_all:
        return [name for name in infos if not name.endswith("/")]
    if not entries:
        raise Refused("no entries chosen: pass --entry NAME (once or more) or --all")
    selected: list[str] = []
    for name in entries:
        if name not in infos:
            raise Refused(f"the archive does not hold {name!r}")
        if name not in selected:
            selected.append(name)
    return selected


def _safe_target(destination: Path, name: str) -> Path:
    """The file to write, proven to stay under `destination` even through a symlink."""
    problem = name_problem(name)
    if problem:
        raise Refused(f"entry name {problem}")
    base = destination.resolve()
    target = destination / name
    resolved = target.resolve(strict=False)
    if resolved != base / name:
        raise Refused(
            f"entry {name!r} resolves through a symbolic link outside {base}"
        )
    return target


def _read(archive: zipfile.ZipFile, info: zipfile.ZipInfo) -> bytes:
    try:
        with archive.open(info) as stream:
            data = stream.read(MAX_ENTRY_BYTES + 1)
    except (RuntimeError, NotImplementedError, zipfile.BadZipFile, zlib.error, EOFError, OSError) as exc:
        raise Refused(f"entry {info.filename!r} cannot be read: {exc}") from None
    if len(data) > MAX_ENTRY_BYTES:
        raise Refused(
            f"entry {info.filename!r} is larger than the {MAX_ENTRY_BYTES}-byte cap"
        )
    return data


def _write(target: Path, data: bytes, name: str, digest: str) -> Extracted:
    """Write through a sibling temporary file, so a failure never leaves a half file in place."""
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(f".{target.name}.part")
    try:
        temporary.write_bytes(data)
        temporary.replace(target)
    except OSError as exc:
        try:
            temporary.unlink()
        except OSError:
            pass
        raise Refused(f"entry {name!r} cannot be written to {target}: {exc}") from None
    return Extracted(entry=name, target=str(target), size=len(data), digest=digest)


def _expected_digests(document: Document) -> dict[str, str]:
    """The digest each entry claims: a part's manifest record, an asset's own name."""
    expected: dict[str, str] = {}
    for part in document.pages + document.chunks:
        if part.sha256:
            expected[part.entry] = part.sha256
    for asset in document.assets:
        expected[asset.name] = asset.digest
    return expected
