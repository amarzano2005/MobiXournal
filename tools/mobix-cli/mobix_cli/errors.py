"""The two ways a path can fail to become a document.

`CannotOpen` is about the file: it is missing, unreadable, or not a ZIP at all. `Refused` is about
the document: the archive opened, but the rules this reader enforces say it is not a valid `.mobix`
(no mimetype, no manifest, an unsafe entry name, a cap exceeded, a digest that does not match).
Keeping them apart is what gives the two exit codes separate meanings.
"""

from __future__ import annotations


class CannotOpen(Exception):
    """The path could not be opened as an archive (exit code 2)."""


class Refused(Exception):
    """The archive opened but is not a document this reader accepts (exit code 1)."""
