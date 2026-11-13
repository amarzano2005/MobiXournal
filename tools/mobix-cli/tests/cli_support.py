"""Shared helpers for the CLI's tests: the package on `sys.path`, fixture roots, and a runner."""

from __future__ import annotations

import contextlib
import io
import json
import sys
from pathlib import Path
from typing import Any

PACKAGE_DIR = Path(__file__).resolve().parents[1]
if str(PACKAGE_DIR) not in sys.path:
    sys.path.insert(0, str(PACKAGE_DIR))

from mobix_cli.cli import main  # noqa: E402  (the path insertion has to happen first)

REPO_ROOT = Path(__file__).resolve().parents[3]
GOLDEN = REPO_ROOT / "mobix-core" / "src" / "test" / "resources" / "fixtures" / "mobix-v1"
HOSTILE = REPO_ROOT / "mobix-core" / "src" / "test" / "resources" / "fixtures" / "mobix-hostile"


def run(*args: str) -> tuple[int, str, str]:
    """Run the CLI in-process; return (exit code, stdout, stderr)."""
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = main(list(args))
    return code, out.getvalue(), err.getvalue()


def run_json(*args: str) -> tuple[int, Any, str]:
    """Run the CLI with `--json`; parse stdout whenever the command printed an object.

    A refusal prints nothing on stdout, while `validate` prints its findings and still exits 1 — so
    the presence of output, not the exit code, decides whether there is an object to parse.
    """
    code, out, err = run(*args, "--json")
    view = json.loads(out) if out.strip() else None
    return code, view, err
