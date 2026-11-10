#!/usr/bin/env python3
"""Generate the **hostile corpus**: one `.mobix` per threat in the limits table.

The corpus is committed under
`mobix-core/src/test/resources/fixtures/mobix-hostile/`, and this script is how it
is regenerated: dependency-free, deterministic (fixed ZIP timestamps, no clock, no
locale), so a re-run anywhere reproduces the same bytes. `00-smallest.mobix` is the
legal neighbour that keeps the test honest — a reader that refused *everything*
would pass every hostile case and fail this one.

Each file is small on purpose: the bomb inflates 16 MiB from a few kB, and the
many-entry archive carries empty entries, so the whole corpus stays reviewable.

Usage:
    python3 scripts/make-mobix-hostile-corpus.py
"""

from __future__ import annotations

import hashlib
import json
import os
import struct
import warnings
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "mobix-core", "src", "test", "resources", "fixtures", "mobix-hostile")

MIMETYPE = b"application/vnd.mobixournal+zip"
FIXED_TIME = (1980, 1, 1, 0, 0, 0)
PAGE_ENTRY = "pages/page-0001.json"


def j(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def page() -> bytes:
    return j({"widthPt": 595, "heightPt": 842,
              "layers": [{"elements": [
                  {"type": "text", "content": "hi", "x": 72, "y": 96}]}]})


def manifest(page_bytes: bytes, *, digest: str | None = None) -> bytes:
    return j({
        "formatVersion": 1,
        "minAppVersion": "2.0.0",
        "documentId": "0f6a3f2e-6b0c-4f43-9c1a-2d7f3f7c1a10",
        "canvasMode": "PAGINATED",
        "created": 1775952000000,
        "modified": 1775952000000,
        "title": "Hostile corpus",
        "pages": [{"id": "pg_001", "entry": PAGE_ENTRY, "modified": 1775952000000,
                   "sha256": digest if digest is not None else sha(page_bytes)}],
    })


def canvas() -> bytes:
    return j({"mode": "PAGINATED", "widthPt": 595.27559100, "heightPt": 841.88976400,
              "chunkSizePt": 1024.0, "origin": {"pageNo": 1, "offsetPt": 0.0}})


def document(page_bytes: bytes, *, digest: str | None = None, extra=()) -> list[tuple[str, bytes]]:
    return [("manifest.json", manifest(page_bytes, digest=digest)),
            ("canvas.json", canvas()),
            (PAGE_ENTRY, page_bytes)] + list(extra)


def write(filename: str, entries: list[tuple[str, bytes]], *, compress=True) -> bytes:
    """Write a `.mobix`-shaped archive: `mimetype` STORED and first, the rest as asked."""
    path = os.path.join(OUT, filename)
    with zipfile.ZipFile(path, "w") as archive:
        info = zipfile.ZipInfo("mimetype", date_time=FIXED_TIME)
        info.compress_type = zipfile.ZIP_STORED
        info.external_attr = 0o644 << 16
        archive.writestr(info, MIMETYPE)
        for name, data in entries:
            info = zipfile.ZipInfo(name, date_time=FIXED_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED if compress else zipfile.ZIP_STORED
            info.external_attr = 0o644 << 16
            archive.writestr(info, data)
    with open(path, "rb") as handle:
        data = handle.read()
    print(f"wrote {os.path.relpath(path, ROOT)} ({len(data)} bytes)")
    return data


def write_bytes(filename: str, data: bytes) -> None:
    with open(os.path.join(OUT, filename), "wb") as handle:
        handle.write(data)
    print(f"wrote {os.path.relpath(os.path.join(OUT, filename), ROOT)} ({len(data)} bytes)")


def local_header_offsets(data: bytes):
    """Yield (offset, name, compressed size) for each local file header, in archive order."""
    at = 0
    while True:
        at = data.find(b"PK\x03\x04", at)
        if at < 0:
            return
        _, _, flag, method, _, _, crc, comp, uncomp, name_len, extra_len = struct.unpack_from(
            "<IHHHHHIIIHH", data, at)
        name = data[at + 30:at + 30 + name_len].decode("utf-8")
        yield at, name, comp, uncomp
        at += 30 + name_len + extra_len + comp


def encrypt_local_entry(data: bytes, target: str) -> bytes:
    """Set the ZIP 'encrypted' flag on the local header of `target` (the only header a stream reads)."""
    out = bytearray(data)
    for offset, name, _, _ in local_header_offsets(data):
        if name == target:
            flag = struct.unpack_from("<H", out, offset + 6)[0]
            struct.pack_into("<H", out, offset + 6, flag | 0x1)
            return bytes(out)
    raise SystemExit(f"no local header for {target}")


# ---- 00: the legal neighbour ------------------------------------------------------

def smallest() -> bytes:
    return write("00-smallest.mobix", document(page()))


# ---- 01: more entries than the caps allow -----------------------------------------

def too_many_entries() -> bytes:
    # The default cap is 20 000, so one past it is the smallest file that proves the rule.
    entries = [(f"x{i}", b"") for i in range(20_001)]
    return write("01-too-many-entries.mobix", entries, compress=False)


# ---- 02: a deflate bomb -----------------------------------------------------------

def deflate_bomb() -> bytes:
    payload = b"\0" * (16 * 1024 * 1024)  # 16 MiB of zeros: ~1000:1, past the ratio cap
    return write("02-deflate-bomb.mobix", [("manifest.json", manifest(page())),
                                           ("canvas.json", canvas()),
                                           (PAGE_ENTRY, payload)])


# ---- 03: a page nested past the depth cap -----------------------------------------

def deep_nesting() -> bytes:
    payload = b"[" * 200 + b"0" + b"]" * 200
    return write("03-deep-nesting.mobix", document(payload))


# ---- 04: two entries with one name ------------------------------------------------

def duplicate_entry() -> bytes:
    page_bytes = page()
    entries = document(page_bytes) + [(PAGE_ENTRY, page_bytes)]
    return write("04-duplicate-entry.mobix", entries)


# ---- 05: a name that climbs out of the archive ------------------------------------

def traversal_entry() -> bytes:
    return write("05-traversal-entry.mobix", document(page(), extra=[("../escape.json", b"{}")]))


# ---- 06: a page whose recorded digest is wrong ------------------------------------

def wrong_digest() -> bytes:
    return write("06-wrong-digest.mobix", document(page(), digest="0" * 64))


# ---- 07: an encrypted entry -------------------------------------------------------

def encrypted_entry() -> bytes:
    data = write("07-encrypted-entry.mobix", document(page()))
    patched = encrypt_local_entry(data, PAGE_ENTRY)
    write_bytes("07-encrypted-entry.mobix", patched)
    return patched


# ---- 08: a truncated central directory --------------------------------------------

def truncated_directory() -> bytes:
    path = os.path.join(OUT, "08-truncated-directory.mobix")
    data = write("08-truncated-directory.mobix", document(page()))
    with zipfile.ZipFile(path) as archive:
        cut = archive.start_dir  # everything before the central directory survives
    truncated = data[:cut]
    write_bytes("08-truncated-directory.mobix", truncated)
    return truncated


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    # A duplicate name is the threat, not a mistake in this script.
    warnings.simplefilter("ignore", UserWarning)
    smallest()
    too_many_entries()
    deflate_bomb()
    deep_nesting()
    duplicate_entry()
    traversal_entry()
    wrong_digest()
    encrypted_entry()
    truncated_directory()


if __name__ == "__main__":
    main()
