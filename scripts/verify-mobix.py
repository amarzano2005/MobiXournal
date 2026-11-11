#!/usr/bin/env python3
"""Decide the `.mobix` conformance rows a script can decide.

This is the independent half of the pair. `scripts/make-mobix-fixtures.py` writes
the golden corpus; every check on what it produced used to be Kotlin inside this
repository's own build, so "a file written here validates in the independent tool"
had no tool behind it. This script is that tool: it reads a `.mobix` with nothing
but the Python standard library and the published rules, and it never imports,
runs or reads anything from `mobix-core`.

It decides these rows of `spec/CONFORMANCE.md`, by number:

    1, 19   the stored `mimetype` entry: its exact bytes, `mimetype` and
            `manifest.json` as the two required entries
    2       an archive whose `mimetype` is not the type this format owns
    3       an archive without `manifest.json`
    4       a `pages[]`/`chunks[]` part that names an entry the archive lacks,
            and the `sha256` each part records for its stored bytes
    9       an entry outside the documented namespaces is legal (the half of the
            row a reader can decide; the preservation half is a save-and-reopen)
    18      a duplicate, `..`/`.`-segment, absolute, backslashed or
            control-character entry name
    21      `formatVersion`, `documentId` and `canvasMode` as the three required
            manifest fields, and a `canvasMode` this format knows
    33      a capability identifier that does not parse as
            `<namespace>/<feature>:<version>`
    35      one entry per capability in each list, an absent list meaning empty
    38      `manifest.json`, `canvas.json`, every part and every modelled element
            against the schemas in `spec/schema/`

Where it stops, so a passing run is not read as more than it is: anything that
needs a rewrite (rows 9, 10, 11, 12, 16), a digest stored in another entry, a
reference from one entry to another, geometry that is the sum of other fields, or
the save-and-reopen half of an accepted row stays a reader's job. The hostile
corpus is expected to fail here — one file per threat, each for its own rule — so
run this over the golden corpus, not over that one.

Usage:

    python3 scripts/verify-mobix.py FILE [FILE ...]
    python3 scripts/verify-mobix.py --rows
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMA_DIR = ROOT / "spec" / "schema"

MIMETYPE_ENTRY = "mimetype"
MANIFEST_ENTRY = "manifest.json"
CANVAS_ENTRY = "canvas.json"
MIMETYPE = b"application/vnd.mobixournal+zip"

CANVAS_MODES = ("PAGINATED", "INFINITE_VERTICAL", "INFINITE_HORIZONTAL", "INFINITE_2D")

REQUIRED_MANIFEST_KEYS = ("formatVersion", "documentId", "canvasMode")

# `<namespace>/<feature>:<version>` — docs/mobix-capabilities.md, the identifier's grammar:
# a namespace of at least two dot-separated lowercase labels, a feature of lowercase
# alphanumerics joined by single dots or dashes, and a positive integer version.
IDENTIFIER = re.compile(
    r"^[a-z0-9]+(?:-[a-z0-9]+)*(?:\.[a-z0-9]+(?:-[a-z0-9]+)*)+"
    r"/[a-z0-9]+(?:[.-][a-z0-9]+)*"
    r":[1-9][0-9]*$"
)

# The entries this format documents; anything else is a construct from another writer
# and must be carried rather than refused (row 9).
KNOWN_PREFIXES = (MANIFEST_ENTRY, CANVAS_ENTRY, MIMETYPE_ENTRY, "pages/", "chunks/", "assets/")

ROWS = (1, 2, 3, 4, 9, 18, 19, 21, 33, 35, 38)


# ---- the schema layer ------------------------------------------------------------------------


class Schemas:
    """The minimal Draft 2020-12 subset `spec/schema/` uses, and no more."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self._cache: dict[str, dict] = {}

    def schema(self, relative: str) -> dict:
        if relative not in self._cache:
            path = self.directory / relative
            self._cache[relative] = json.loads(path.read_text(encoding="utf-8"))
        return self._cache[relative]

    def validate(self, value, relative: str, where: str) -> list[str]:
        return self._against(value, self.schema(relative), relative, where)

    def _against(self, value, node: dict, relative: str, where: str) -> list[str]:
        ref = node.get("$ref")
        if isinstance(ref, str):
            target_file, target = self._resolve(ref, relative)
            return self._against(value, target, target_file, where)

        if "const" in node and not _same(value, node["const"]):
            return [f"{where}: expected {json.dumps(node['const'])}"]
        errors: list[str] = []
        if "enum" in node and not any(_same(value, allowed) for allowed in node["enum"]):
            errors.append(f"{where}: {_show(value)} is not one of {node['enum']}")
        if "not" in node and not self._against(value, node["not"], relative, where):
            errors.append(f"{where}: matches a shape the schema excludes")

        declared = node.get("type")
        if declared is not None:
            types = [declared] if isinstance(declared, str) else list(declared)
            if not any(_is_type(value, name) for name in types):
                errors.append(f"{where}: {_show(value)} is not {' or '.join(types)}")
                return errors

        if isinstance(value, dict):
            for key in node.get("required", []):
                if key not in value:
                    errors.append(f"{where}: `{key}` is required")
            properties = node.get("properties", {})
            extra = node.get("additionalProperties", True)
            for key, child in value.items():
                if key in properties:
                    errors += self._against(child, properties[key], relative, f"{where}.{key}")
                elif extra is False:
                    errors.append(f"{where}: `{key}` is not described by this schema")
                elif isinstance(extra, dict):
                    errors += self._against(child, extra, relative, f"{where}.{key}")

        if isinstance(value, list):
            minimum = node.get("minItems")
            if isinstance(minimum, int) and len(value) < minimum:
                errors.append(f"{where}: {len(value)} items, at least {minimum} required")
            items = node.get("items")
            if isinstance(items, dict):
                for index, item in enumerate(value):
                    errors += self._against(item, items, relative, f"{where}[{index}]")

        if isinstance(value, (int, float)) and not isinstance(value, bool):
            low, high = node.get("minimum"), node.get("maximum")
            if isinstance(low, (int, float)) and value < low:
                errors.append(f"{where}: {value} is below {low}")
            if isinstance(high, (int, float)) and value > high:
                errors.append(f"{where}: {value} is above {high}")

        if isinstance(value, str):
            length = node.get("minLength")
            if isinstance(length, int) and len(value) < length:
                errors.append(f"{where}: {len(value)} characters, at least {length} required")
            pattern = node.get("pattern")
            if isinstance(pattern, str) and re.search(pattern, value) is None:
                errors.append(f"{where}: {json.dumps(value)} does not match {pattern}")

        branches = node.get("oneOf")
        if isinstance(branches, list):
            passing = sum(1 for branch in branches if not self._against(value, branch, relative, where))
            if passing != 1:
                errors.append(f"{where}: matches {passing} of the shapes offered, exactly one must match")

        return errors

    def _resolve(self, ref: str, relative: str) -> tuple[str, dict]:
        file_part, _, pointer = ref.partition("#")
        target_file = file_part or relative
        node = self.schema(target_file)
        for token in pointer.split("/"):
            if not token:
                continue
            key = token.replace("~1", "/").replace("~0", "~")
            if isinstance(node, dict) and key in node:
                node = node[key]
            elif isinstance(node, list) and key.isdigit() and int(key) < len(node):
                node = node[int(key)]
            else:
                raise KeyError(f"{ref} does not resolve: no `{key}`")
        return target_file, node


def _show(value) -> str:
    """A value as one short line: a deep-nested page must not print a screenful to say `no`."""
    text = json.dumps(value)
    return text if len(text) <= 60 else text[:57] + "..."


def _is_type(value, name: str) -> bool:
    if name == "object":
        return isinstance(value, dict)
    if name == "array":
        return isinstance(value, list)
    if name == "string":
        return isinstance(value, str)
    if name == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if name == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if name == "boolean":
        return isinstance(value, bool)
    if name == "null":
        return value is None
    raise ValueError(f"unknown type {name}")


def _same(value, expected) -> bool:
    if isinstance(value, bool) or isinstance(expected, bool):
        return isinstance(value, bool) and isinstance(expected, bool) and value == expected
    if isinstance(value, (int, float)) and isinstance(expected, (int, float)):
        return value == expected
    return type(value) is type(expected) and value == expected


# ---- the rows ---------------------------------------------------------------------------------


def _read(archive: zipfile.ZipFile, name: str) -> bytes:
    return archive.read(name)


def _digest_mismatch(archive: zipfile.ZipFile, part: dict, where: str) -> list[str]:
    entry = part.get("entry")
    if not isinstance(entry, str) or not entry:
        return [f"{where}: a part with no `entry` names no bytes"]
    if entry not in archive.namelist():
        return [f"{where}: `{entry}` is named by the manifest and absent from the archive"]
    recorded = part.get("sha256")
    if isinstance(recorded, str) and recorded:
        actual = hashlib.sha256(_read(archive, entry)).hexdigest()
        if actual != recorded:
            return [f"{where}: `{entry}` records {recorded}, its stored bytes hash to {actual}"]
    return []


def _entry_name_problems(names: list[str]) -> list[str]:
    problems: list[str] = []
    for name, count in Counter(names).items():
        if count > 1:
            problems.append(f"`{name}` is stored {count} times")
    for name in names:
        if name.startswith("/") or name.startswith("\\") or re.match(r"^[A-Za-z]:", name):
            problems.append(f"`{name}` is an absolute entry name")
        if "\\" in name:
            problems.append(f"`{name}` uses a backslash")
        segments = name.split("/")
        if any(segment in ("", ".", "..") for segment in segments[:-1]) or segments[-1] in (".", ".."):
            problems.append(f"`{name}` has a dot segment")
        if any(ord(character) < 0x20 or ord(character) == 0x7F for character in name):
            problems.append(f"`{name}` carries a control character")
    return problems


def _capability_problems(manifest: dict) -> tuple[list[str], list[str]]:
    grammar: list[str] = []
    duplicates: list[str] = []
    for key in ("requiredCapabilities", "optionalCapabilities"):
        declared = manifest.get(key)
        if declared is None:
            continue
        if not isinstance(declared, list):
            grammar.append(f"`{key}` is not a list")
            continue
        seen: list[str] = []
        for value in declared:
            if not isinstance(value, str) or IDENTIFIER.match(value) is None:
                grammar.append(f"`{key}`: {json.dumps(value)} is not `<namespace>/<feature>:<version>`")
                continue
            if value in seen:
                duplicates.append(f"`{key}` declares `{value}` twice")
            seen.append(value)
    return grammar, duplicates


def check(path: Path, schemas: Schemas) -> list[tuple[int, str]]:
    """Every problem this script can decide about one document, as (row, message)."""
    problems: list[tuple[int, str]] = []

    try:
        archive = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError) as failure:
        return [(1, f"not a readable archive: {failure}")]

    with archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]

        if not infos or infos[0].filename != MIMETYPE_ENTRY:
            problems.append((1, "the first entry is not `mimetype`"))
        elif infos[0].compress_type != zipfile.ZIP_STORED:
            problems.append((1, "`mimetype` is not stored uncompressed"))
        if MIMETYPE_ENTRY not in names:
            problems.append((19, "`mimetype` is missing"))
        else:
            declared = _read(archive, MIMETYPE_ENTRY)
            if declared != MIMETYPE:
                problems.append((2, f"`mimetype` is {declared!r}, not {MIMETYPE!r}"))

        if MANIFEST_ENTRY not in names:
            problems.append((3, "`manifest.json` is missing"))
        elif not _read(archive, MANIFEST_ENTRY).strip():
            problems.append((19, "`manifest.json` is present and empty"))

        for name in _entry_name_problems(names):
            problems.append((18, name))

        manifest = None
        if MANIFEST_ENTRY in names:
            try:
                manifest = json.loads(_read(archive, MANIFEST_ENTRY).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as failure:
                problems.append((21, f"`manifest.json` is not JSON: {failure}"))
            if not isinstance(manifest, dict):
                problems.append((21, "`manifest.json` is not a JSON object"))
                manifest = None

        if manifest is not None:
            for key in REQUIRED_MANIFEST_KEYS:
                if key not in manifest:
                    problems.append((21, f"`manifest.json` has no `{key}`"))
            version = manifest.get("formatVersion")
            if "formatVersion" in manifest and not (isinstance(version, int) and not isinstance(version, bool)):
                problems.append((21, "`manifest.json`: `formatVersion` is not an integer"))
            if "documentId" in manifest and not (isinstance(manifest.get("documentId"), str) and manifest["documentId"]):
                problems.append((21, "`manifest.json`: `documentId` is not a non-empty string"))
            mode = manifest.get("canvasMode")
            if "canvasMode" in manifest and mode not in CANVAS_MODES:
                problems.append((21, f"`manifest.json`: `canvasMode` {json.dumps(mode)} is not one this format knows"))

            for key in ("pages", "chunks"):
                parts = manifest.get(key)
                if parts is None:
                    continue
                if not isinstance(parts, list):
                    problems.append((4, f"`manifest.json`: `{key}` is not a list"))
                    continue
                for index, part in enumerate(parts):
                    if not isinstance(part, dict):
                        problems.append((4, f"`{key}[{index}]` is not an object"))
                        continue
                    for message in _digest_mismatch(archive, part, f"`{key}[{index}]`"):
                        problems.append((4, message))

            grammar, duplicates = _capability_problems(manifest)
            problems += [(33, message) for message in grammar]
            problems += [(35, message) for message in duplicates]

            problems += [(38, message) for message in
                         schemas.validate(manifest, "manifest.schema.json", "manifest")]

        if CANVAS_ENTRY in names:
            try:
                canvas = json.loads(_read(archive, CANVAS_ENTRY).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as failure:
                problems.append((38, f"`canvas.json` is not JSON: {failure}"))
            else:
                problems += [(38, message) for message in
                             schemas.validate(canvas, "canvas.schema.json", "canvas")]

        for name in names:
            if not (name.startswith("pages/") or name.startswith("chunks/")):
                continue
            try:
                page = json.loads(_read(archive, name).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as failure:
                problems.append((38, f"`{name}` is not JSON: {failure}"))
                continue
            part_schema = "chunk.schema.json" if name.startswith("chunks/") else "page.schema.json"
            problems += [(38, message) for message in schemas.validate(page, part_schema, name)]
            for layer in page.get("layers", []) if isinstance(page, dict) else []:
                for element in layer.get("elements", []) if isinstance(layer, dict) else []:
                    if not isinstance(element, dict):
                        continue
                    kind = element.get("type")
                    if not isinstance(kind, str):
                        continue
                    if not (SCHEMA_DIR / "elements" / f"{kind}.schema.json").is_file():
                        continue  # retained rather than modelled: no schema to hold it to
                    problems += [(38, message) for message in
                                 schemas.validate(element, f"elements/{kind}.schema.json", f"{name}:{kind}")]

    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Decide the .mobix conformance rows a script can decide.")
    parser.add_argument("paths", nargs="*", metavar="FILE", help="a .mobix document")
    parser.add_argument("--rows", action="store_true",
                        help="print the conformance rows this script decides")
    parser.add_argument("--schema-dir", default=str(SCHEMA_DIR),
                        help="where the published schemas live (default: spec/schema)")
    arguments = parser.parse_args(argv)

    if arguments.rows:
        for row in ROWS:
            print(row)
        return 0
    if not arguments.paths:
        parser.error("give one or more .mobix files, or --rows")

    schemas = Schemas(Path(arguments.schema_dir))
    failed = False
    for argument in arguments.paths:
        path = Path(argument)
        if not path.is_file():
            print(f"{path}: row 1: not a file")
            failed = True
            continue
        for row, message in sorted(check(path, schemas)):
            print(f"{path}: row {row}: {message}")
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
