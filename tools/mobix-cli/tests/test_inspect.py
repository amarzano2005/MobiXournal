"""`inspect` over the committed golden corpus: the envelope, the counts and what it verified."""

from __future__ import annotations

import os
import unittest
from pathlib import Path

from cli_support import GOLDEN, run, run_json


def fixture(name: str) -> Path:
    return GOLDEN / name


class InspectMinimalTest(unittest.TestCase):
    def test_reports_the_hand_written_manifest(self) -> None:
        path = fixture("01-minimal-single-page.mobix")
        code, view, err = run_json("inspect", str(path))

        self.assertEqual(code, 0, err)
        self.assertEqual(view["mimetype"], "application/vnd.mobixournal+zip")
        self.assertEqual(view["formatVersion"], 1)
        self.assertEqual(view["documentId"], "0f6a3f2e-6b0c-4f43-9c1a-2d7f3f7c1a10")
        self.assertIsNone(view["creator"])
        self.assertEqual(view["canvasMode"], "PAGINATED")
        self.assertEqual(view["pages"], 1)
        self.assertEqual(view["chunks"], 0)
        self.assertEqual(view["entries"], 3)
        self.assertEqual(view["fileBytes"], os.path.getsize(path))
        self.assertEqual(view["requiredCapabilities"], [])
        self.assertEqual(view["optionalCapabilities"], [])

    def test_page_size_falls_back_to_the_page_when_canvas_json_is_absent(self) -> None:
        view = run_json("inspect", str(fixture("01-minimal-single-page.mobix")))[1]

        self.assertAlmostEqual(view["pageSize"]["widthPt"], 595.275591, places=5)
        self.assertAlmostEqual(view["pageSize"]["heightPt"], 841.889764, places=5)
        self.assertEqual(view["integrity"]["partsChecked"], 1)
        self.assertEqual(view["integrity"]["digestsVerified"], 1)

    def test_human_report_names_the_document(self) -> None:
        code, out, err = run("inspect", str(fixture("01-minimal-single-page.mobix")))

        self.assertEqual(code, 0, err)
        self.assertIn("application/vnd.mobixournal+zip", out)
        self.assertIn("PAGINATED", out)
        self.assertIn("1 page(s), 0 chunk(s)", out)


class InspectCorpusTest(unittest.TestCase):
    def test_manifest_order_is_the_page_count_not_the_names(self) -> None:
        view = run_json("inspect", str(fixture("02-multi-page-order.mobix")))[1]

        self.assertEqual(view["pages"], 3)
        self.assertAlmostEqual(view["pageSize"]["widthPt"], 595.275591, places=5)

    def test_bundled_assets_are_verified(self) -> None:
        view = run_json("inspect", str(fixture("03-bundled-assets.mobix")))[1]

        self.assertEqual(view["integrity"]["assetsChecked"], 3)
        self.assertGreaterEqual(view["integrity"]["digestsVerified"], 3)

    def test_chunked_document_reports_tiles_and_the_tile_size(self) -> None:
        view = run_json("inspect", str(fixture("10-chunked-document.mobix")))[1]

        self.assertEqual(view["canvasMode"], "INFINITE_VERTICAL")
        self.assertEqual(view["pages"], 0)
        self.assertEqual(view["chunks"], 2)
        self.assertEqual(view["pageSize"]["widthPt"], 1024.0)
        self.assertEqual(view["pageSize"]["heightPt"], 1024.0)

    def test_third_party_capability_is_reported_and_does_not_refuse_a_read(self) -> None:
        code, view, err = run_json("inspect", str(fixture("17-third-party-writer.mobix")))

        self.assertEqual(code, 0, err)
        self.assertEqual(view["optionalCapabilities"], ["com.example/lab.notebook:1"])

    def test_required_capability_is_reported_not_refused(self) -> None:
        code, view, err = run_json("inspect", str(fixture("13-required-capability-unknown.mobix")))

        self.assertEqual(code, 0, err)
        self.assertEqual(view["requiredCapabilities"], ["example.org/stem.connector:1"])

    def test_every_golden_fixture_is_readable(self) -> None:
        for path in sorted(GOLDEN.glob("*.mobix")):
            with self.subTest(path=path.name):
                code, _, err = run("inspect", str(path), "--json")
                self.assertEqual(code, 0, f"{path.name}: {err}")


if __name__ == "__main__":
    unittest.main()
