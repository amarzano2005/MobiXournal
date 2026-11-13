"""The `mobix-cli` command line: two read-only commands, three exit codes.

    python3 -m mobix_cli inspect FILE [--json]
    python3 -m mobix_cli list FILE [--json]

Exit 0 is a readable document, 2 is a path that is not an archive this program can open, and 1 is a
document it must refuse. The reason goes to stderr in both failure cases, and `--json` writes exactly
one object to stdout so a script never has to parse prose.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Sequence

from .container import open_document
from .errors import CannotOpen, Refused
from .validate import validate_document
from .views import inspect_view, list_view, render_inspect, render_list

EXIT_OK = 0
EXIT_REFUSED = 1
EXIT_CANNOT_OPEN = 2


def main(argv: Sequence[str] | None = None) -> int:
    parser = _parser()
    args = parser.parse_args(argv)
    if args.command == "validate":
        return _validate(args)
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


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="mobix-cli",
        description="Inspect a .mobix notebook without opening it in the app.",
    )
    commands = parser.add_subparsers(dest="command", required=True)
    for name, help_text in (
        ("inspect", "the envelope, content counts and canvas, with digests checked"),
        ("list", "pages, chunks and assets by name, size and digest"),
        ("validate", "every conformance rule, one finding per line"),
    ):
        command = commands.add_parser(name, help=help_text)
        command.add_argument("path", type=Path, help="the .mobix file to read")
        command.add_argument(
            "--json",
            action="store_true",
            help="print one JSON object instead of the human report",
        )
    return parser
