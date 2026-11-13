"""JSON out of an archive, decoded within the format's nesting budget.

The manifest, the canvas, the bridge sidecar and every part are byte strings inside the ZIP, and a
hostile one may nest deeply enough to exhaust a recursive parser. The rules bound nesting at 64
levels before any model object is built (`docs/mobix-format.md`, *What a reader must refuse*), so the
depth is measured on the raw text first and `json` only sees a document that fits.
"""

from __future__ import annotations

import json
from typing import Any

from .errors import Refused

MAX_JSON_DEPTH = 64


def decode_json(data: bytes, where: str) -> Any:
    """Decode one JSON value read from `where`, or refuse it by name."""
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise Refused(f"{where} is not UTF-8 text: {exc}") from None
    check_depth(text, where)
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise Refused(f"{where} is not valid JSON: {exc}") from None


def check_depth(text: str, where: str) -> None:
    """Refuse `text` when it nests arrays/objects deeper than the format's cap.

    This counts brackets outside string literals; it deliberately does not validate the JSON, which
    is `json.loads`'s job. Its only failure mode is the one a recursive parser cannot survive.
    """
    depth = 0
    in_string = False
    escaped = False
    for char in text:
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char in "[{":
            depth += 1
            if depth > MAX_JSON_DEPTH:
                raise Refused(
                    f"{where} nests JSON deeper than {MAX_JSON_DEPTH} levels"
                )
        elif char in "]}":
            depth -= 1
