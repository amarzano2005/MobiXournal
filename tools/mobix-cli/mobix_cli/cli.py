"""The `mobix-cli` command line: seven commands, three exit codes.

    inspect FILE [--json]        the envelope, with digests checked
    list FILE [--json]           pages, chunks and assets by size and digest
    validate FILE [--json]       every conformance rule, one finding per line
    extract FILE --dest DIR [--entry NAME ... | --all] [--force] [--json]
    export-json FILE             a stable document dump
    new OUT --title T [--force] [--json]
    retitle FILE --title T [--out FILE] [--force] [--json]

Exit 0 is a readable document (or a clean validation), 2 is a path that is not an archive this
program can open, and 1 is a document it must refuse or a validation with findings. The reason goes
to stderr for the two failure cases; `--json` writes exactly one object to stdout, so a script never
has to parse prose.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Sequence

from .container import open_document
from .errors import CannotOpen, Refused
from .export import export_document
from .extract import extract_entries
from .validate import validate_document
from .views import inspect_view, list_view, render_inspect, render_list
from .writer import create_document, retitle

EXIT_OK = 0
EXIT_REFUSED = 1
EXIT_CANNOT_OPEN = 2


def main(argv: Sequence[str] | None = None) -> int:
    parser = _parser()
    args = parser.parse_args(argv)
    if args.command == "validate":
        return _validate(args)
    if args.command == "extract":
        return _extract(args)
    if args.command == "export-json":
        return _export_json(args)
    if args.command == "new":
        return _new(args)
    if args.command == "retitle":
        return _retitle(args)

    try:
        document = open_document(args.path, integrity=args.command == "inspect")
    except CannotOpen as exc:
        print(f"mobix-cli: cannot open {args.path}: {exc}", file=sys.stderr)
        return EXIT_CANNOT_OPEN
    except Refused as exc:
        print(f"mobix-cli: refused {args.path}: {exc}", file=sys.stderr)
        return EXIT_REFUSED

    view = inspect_view(document) if args.command == "inspect" else list_view(document)
    if args.json:
        print(json.dumps(view, indent=2))
    else:
        print(render_inspect(view) if args.command == "inspect" else render_list(view))
    return EXIT_OK


def _validate(args: argparse.Namespace) -> int:
    """Findings, not a refusal: a document with a defect is still a document to report on."""
    try:
        report = validate_document(args.path)
    except CannotOpen as exc:
        print(f"mobix-cli: cannot open {args.path}: {exc}", file=sys.stderr)
        return EXIT_CANNOT_OPEN
    if args.json:
        print(json.dumps(report.as_json(), indent=2))
    else:
        print(report.render())
    return EXIT_OK if report.ok else EXIT_REFUSED


def _extract(args: argparse.Namespace) -> int:
    try:
        written = extract_entries(
            path=args.path,
            destination=args.dest,
            entries=args.entry,
            write_all=args.all,
            force=args.force,
        )
    except CannotOpen as exc:
        print(f"mobix-cli: cannot open {args.path}: {exc}", file=sys.stderr)
        return EXIT_CANNOT_OPEN
    except Refused as exc:
        print(f"mobix-cli: refused {args.path}: {exc}", file=sys.stderr)
        return EXIT_REFUSED

    view = {
        "path": str(args.path),
        "dest": str(args.dest),
        "written": [
            {"entry": item.entry, "file": item.target, "bytes": item.size, "digest": item.digest}
            for item in written
        ],
    }
    if args.json:
        print(json.dumps(view, indent=2))
    else:
        for item in written:
            print(f"{item.entry} -> {item.target} ({item.size} B, sha256:{item.digest})")
    return EXIT_OK


def _export_json(args: argparse.Namespace) -> int:
    try:
        text = export_document(args.path)
    except CannotOpen as exc:
        print(f"mobix-cli: cannot open {args.path}: {exc}", file=sys.stderr)
        return EXIT_CANNOT_OPEN
    except Refused as exc:
        print(f"mobix-cli: refused {args.path}: {exc}", file=sys.stderr)
        return EXIT_REFUSED
    sys.stdout.write(text)
    return EXIT_OK


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="mobix-cli",
        description="Read, validate, create and inspect a .mobix notebook without the app.",
    )
    commands = parser.add_subparsers(dest="command", required=True)
    for name, help_text in (
        ("inspect", "the envelope, content counts and canvas, with digests checked"),
        ("list", "pages, chunks and assets by name, size and digest"),
        ("validate", "every conformance rule, one finding per line"),
        ("export-json", "one stable JSON dump a script can diff"),
    ):
        command = commands.add_parser(name, help=help_text)
        command.add_argument("path", type=Path, help="the .mobix file to read")
        if name != "export-json":
            command.add_argument(
                "--json",
                action="store_true",
                help="print one JSON object instead of the human report",
            )

    extract = commands.add_parser("extract", help="write chosen entries under a destination")
    extract.add_argument("path", type=Path, help="the .mobix file to read")
    extract.add_argument("--dest", type=Path, required=True, help="the directory to write under")
    extract.add_argument(
        "--entry", action="append", default=[], metavar="NAME", help="an entry to write (repeatable)"
    )
    extract.add_argument("--all", action="store_true", help="write every entry in the archive")
    extract.add_argument(
        "--force", action="store_true", help="overwrite a file that already exists"
    )
    extract.add_argument("--json", action="store_true", help="print one JSON object")

    new = commands.add_parser("new", help="write a one-page document with a stroke and an asset")
    new.add_argument("out", type=Path, metavar="OUT", help="the .mobix file to write")
    new.add_argument("--title", required=True, help="the document's title")
    new.add_argument("--force", action="store_true", help="overwrite an existing file")
    new.add_argument("--json", action="store_true", help="print one JSON object")

    retitle = commands.add_parser("retitle", help="change only the title, preserving the rest")
    retitle.add_argument("path", type=Path, help="the .mobix file to rewrite")
    retitle.add_argument("--title", required=True, help="the new title")
    retitle.add_argument(
        "--out", type=Path, help="write elsewhere instead of rewriting in place"
    )
    retitle.add_argument(
        "--force", action="store_true", help="overwrite an existing --out file"
    )
    retitle.add_argument("--json", action="store_true", help="print one JSON object")
    return parser


def _new(args: argparse.Namespace) -> int:
    try:
        written = create_document(args.out, args.title, force=args.force)
    except Refused as exc:
        print(f"mobix-cli: refused {args.out}: {exc}", file=sys.stderr)
        return EXIT_REFUSED
    print(json.dumps(written.as_json(), indent=2) if args.json else written.render())
    return EXIT_OK


def _retitle(args: argparse.Namespace) -> int:
    try:
        written = retitle(args.path, args.title, out=args.out, force=args.force)
    except CannotOpen as exc:
        print(f"mobix-cli: cannot open {args.path}: {exc}", file=sys.stderr)
        return EXIT_CANNOT_OPEN
    except Refused as exc:
        print(f"mobix-cli: refused {args.path}: {exc}", file=sys.stderr)
        return EXIT_REFUSED
    print(json.dumps(written.as_json(), indent=2) if args.json else written.render())
    return EXIT_OK
