"""`validate` against the checklist: golden clean, hostile by its own row, mutations one rule each."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from cli_support import GOLDEN, HOSTILE, run, run_json

MIMETYPE = "application/vnd.mobixournal+zip"
PAGE = "pages/page-0001.json"
HAS_JSONSCHEMA = importlib.util.find_spec("jsonschema") is not None


def page(width: float = 100.0, elements: list[dict] | None = None) -> bytes:
    return json.dumps(
        {
            "widthPt": width,
            "heightPt": 120.0,
            "layers": [{"elements": elements or []}],
        },
        separators=(",", ":"),
    ).encode("utf-8")


def document(page_bytes: bytes, *, digest: str = "", title: str = "Test") -> dict[str, bytes]:
    manifest = {
        "formatVersion": 1,
        "documentId": "doc-validate",
        "canvasMode": "PAGINATED",
        "title": title,
        "pages": [{"id": "pg_1", "entry": PAGE, "sha256": digest}],
    }
    return {
        "mimetype": MIMETYPE.encode("ascii"),
        "manifest.json": json.dumps(manifest, separators=(",", ":")).encode(),
        PAGE: page_bytes,
    }


def write_archive(path: Path, entries: dict[str, bytes], store: tuple[str, ...] = ("mimetype",)) -> Path:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries.items():
            compressed = zipfile.ZIP_STORED if name in store else zipfile.ZIP_DEFLATED
            archive.writestr(name, data, compress_type=compressed)
    return path


def patch_central_method(path: Path, entry: str, method: int) -> None:
    """Rewrite one central-directory compression method, which no writer API lets us set."""
    data = bytearray(path.read_bytes())
    marker = b"PK\x01\x02"
    index = data.find(marker)
    while index != -1:
        name_len = struct.unpack_from("<H", data, index + 28)[0]
        name = bytes(data[index + 46 : index + 46 + name_len]).decode("utf-8")
        if name == entry:
            struct.pack_into("<H", data, index + 10, method)
            path.write_bytes(bytes(data))
            return
        index = data.find(marker, index + 4)
    raise AssertionError(f"no central directory entry for {entry}")


class GoldenCorpusValidatesCleanTest(unittest.TestCase):
    def test_every_golden_fixture_is_clean(self) -> None:
        for path in sorted(GOLDEN.glob("*.mobix")):
            with self.subTest(path=path.name):
                code, view, err = run_json("validate", str(path))
                self.assertEqual(code, 0, f"{path.name}: {view or err}")
                self.assertTrue(view["ok"])
                self.assertEqual(view["findings"], [])

    def test_the_legal_hostile_neighbour_is_clean(self) -> None:
        code, view, err = run_json("validate", str(HOSTILE / "00-smallest.mobix"))

        self.assertEqual(code, 0, err)
        self.assertTrue(view["ok"])

    def test_schema_layer_reports_what_it_could_check(self) -> None:
        view = run_json("validate", str(GOLDEN / "18-every-family.mobix"))[1]

        self.assertEqual(view["schema"], "checked" if HAS_JSONSCHEMA else "unavailable")


class HostileCorpusRuleTest(unittest.TestCase):
    def rules(self, name: str) -> list[str]:
        code, view, err = run_json("validate", str(HOSTILE / name))
        self.assertEqual(code, 1, err)
        return [finding["rule"] for finding in view["findings"]]

    def test_entry_count_cap_is_row_17(self) -> None:
        self.assertIn("row-17", self.rules("01-too-many-entries.mobix"))

    def test_repeated_doubling_is_one_row_17_finding(self) -> None:
        code, view, _ = run_json("validate", str(HOSTILE / "02-deflate-bomb.mobix"))

        self.assertEqual(code, 1)
        self.assertEqual([finding["rule"] for finding in view["findings"]], ["row-17"])

    def test_deep_nesting_is_row_17(self) -> None:
        self.assertIn("row-17", self.rules("03-deep-nesting.mobix"))

    def test_duplicate_name_is_row_18(self) -> None:
        self.assertIn("row-18", self.rules("04-duplicate-entry.mobix"))

    def test_traversal_name_is_row_18(self) -> None:
        self.assertIn("row-18", self.rules("05-traversal-entry.mobix"))

    def test_digest_mismatch_is_row_04(self) -> None:
        self.assertEqual(self.rules("06-wrong-digest.mobix"), ["row-04"])

    def test_encryption_is_row_20(self) -> None:
        self.assertEqual(self.rules("07-encrypted-entry.mobix"), ["row-20"])

    def test_truncated_archive_is_exit_2(self) -> None:
        code, _, err = run("validate", str(HOSTILE / "08-truncated-directory.mobix"))

        self.assertEqual(code, 2, err)


class MutatedArchiveTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.temp = Path(self._temp.name)

    def validate(self, entries: dict[str, bytes], **kwargs) -> list[dict]:
        path = write_archive(self.temp / "case.mobix", entries, **kwargs)
        code, view, err = run_json("validate", str(path))
        self.assertEqual(code, 1, f"{view or err}")
        return view["findings"]

    def test_mimetype_not_first_is_one_row_01_finding(self) -> None:
        entries = document(page(), digest=hashlib.sha256(page()).hexdigest())
        path = self.temp / "case.mobix"
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name in ("manifest.json", PAGE):
                archive.writestr(name, entries[name])
            archive.writestr("mimetype", entries["mimetype"], compress_type=zipfile.ZIP_STORED)

        code, view, err = run_json("validate", str(path))

        self.assertEqual(code, 1, err)
        self.assertEqual([finding["rule"] for finding in view["findings"]], ["row-01"])
        self.assertEqual(view["findings"][0]["severity"], "warning")

    def test_bad_hex_in_an_asset_name_is_one_row_14_finding(self) -> None:
        entries = document(page(), digest=hashlib.sha256(page()).hexdigest())
        entries["assets/img_not-a-digest.png"] = b"picture"

        findings = self.validate(entries)

        self.assertEqual([finding["rule"] for finding in findings], ["row-14"])
        self.assertEqual(findings[0]["name"], "NAME_GRAMMAR")

    def test_missing_asset_reference_is_one_row_14_finding(self) -> None:
        name = "assets/img_" + "a" * 64 + ".png"
        elements = [
            {
                "type": "image",
                "bounds": {"left": 0, "top": 0, "right": 10, "bottom": 10},
                "asset": name,
            }
        ]
        page_bytes = page(elements=elements)
        entries = document(page_bytes, digest=hashlib.sha256(page_bytes).hexdigest())

        findings = self.validate(entries)

        self.assertEqual([finding["rule"] for finding in findings], ["row-14"])
        self.assertEqual(findings[0]["name"], "MISSING_ENTRY")

    def test_part_digest_mismatch_is_one_row_04_finding(self) -> None:
        entries = document(page(), digest="0" * 64)

        findings = self.validate(entries)

        self.assertEqual([finding["rule"] for finding in findings], ["row-04"])
        self.assertEqual(findings[0]["name"], "PART_DIGEST")

    def test_unsupported_compression_is_one_row_20_finding(self) -> None:
        entries = document(page(), digest=hashlib.sha256(page()).hexdigest())
        path = write_archive(self.temp / "case.mobix", entries)
        patch_central_method(path, PAGE, 99)

        code, view, err = run_json("validate", str(path))

        self.assertEqual(code, 1, err)
        self.assertEqual([finding["rule"] for finding in view["findings"]], ["row-20"])
        self.assertEqual(view["findings"][0]["name"], "UNSUPPORTED_METHOD")

    def test_unknown_canvas_mode_is_a_finding(self) -> None:
        entries = document(page(), digest=hashlib.sha256(page()).hexdigest())
        entries["canvas.json"] = json.dumps({"mode": "SPIRAL"}).encode()
        entries["manifest.json"] = json.dumps(
            {
                "formatVersion": 1,
                "documentId": "doc-validate",
                "canvasMode": "PAGINATED",
                "pages": [{"id": "pg_1", "entry": PAGE, "sha256": hashlib.sha256(page()).hexdigest()}],
            }
        ).encode()

        findings = self.validate(entries)

        self.assertIn("row-22", [finding["rule"] for finding in findings])


class ValidateOutputTest(unittest.TestCase):
    def test_human_output_prints_one_finding_per_line(self) -> None:
        code, out, err = run("validate", str(HOSTILE / "07-encrypted-entry.mobix"))

        self.assertEqual(code, 1, err)
        self.assertEqual(len(out.strip().splitlines()), 1)
        self.assertTrue(out.startswith("row-20 error "))
        self.assertIn("encrypted", out)

    def test_clean_human_output_names_the_entries(self) -> None:
        code, out, err = run("validate", str(GOLDEN / "01-minimal-single-page.mobix"))

        self.assertEqual(code, 0, err)
        self.assertIn("clean (3 entries)", out)

    def test_json_carries_rule_severity_and_location(self) -> None:
        code, view, _ = run_json("validate", str(HOSTILE / "04-duplicate-entry.mobix"))

        self.assertEqual(code, 1)
        finding = view["findings"][0]
        self.assertEqual(
            set(finding), {"rule", "name", "severity", "location", "message"}
        )
        self.assertEqual(finding["severity"], "error")
        self.assertIn("pages/page-0001.json", finding["location"])


if __name__ == "__main__":
    unittest.main()
