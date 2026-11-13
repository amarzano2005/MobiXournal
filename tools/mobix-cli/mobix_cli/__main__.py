"""`python3 -m mobix_cli` — the package's own entry point."""

from __future__ import annotations

from .cli import main

if __name__ == "__main__":
    raise SystemExit(main())
