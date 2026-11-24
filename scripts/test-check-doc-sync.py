#!/usr/bin/env python3
"""Tests for scripts/check-doc-sync.py pre-commit doc-sync guard."""
from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import sys
import unittest
from unittest import mock


REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = REPO_ROOT / "scripts" / "check-doc-sync.py"


def _load_module():
    spec = importlib.util.spec_from_file_location("check_doc_sync", SCRIPT_PATH)
    assert spec is not None and spec.loader is not None
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class CheckDocSyncTest(unittest.TestCase):
    def setUp(self):
        self.mod = _load_module()

    def test_all_source_and_watched_prefixes_exist_in_repo(self):
        for prefix in (*self.mod.SOURCE_PREFIXES, *self.mod.WATCHED_PREFIXES):
            target = REPO_ROOT / prefix.rstrip("/")
            self.assertTrue(
                target.is_dir(),
                f"Configured prefix does not exist in repo: {prefix}",
            )

    def test_desktop_shell_and_tools_without_docs_fail(self):
        for path in (
            "desktop/src/main/java/com/mobixournal/desktop/shell/DesktopWindow.kt",
            "desktop/src/main/java/com/mobixournal/desktop/tools/InspectorPanel.kt",
        ):
            with self.subTest(path=path):
                with mock.patch.object(self.mod, "staged_files", return_value=[path]):
                    with mock.patch.dict(os.environ, {}, clear=False):
                        os.environ.pop("MOBIX_SKIP_DOC_CHECK", None)
                        self.assertEqual(self.mod.main(), 1)

    def test_android_ui_without_docs_fails(self):
        staged = ["app/src/main/java/com/mobixournal/ui/EditorTopBar.kt"]
        with mock.patch.object(self.mod, "staged_files", return_value=staged):
            with mock.patch.dict(os.environ, {}, clear=False):
                os.environ.pop("MOBIX_SKIP_DOC_CHECK", None)
                self.assertEqual(self.mod.main(), 1)

    def test_desktop_tools_with_doc_update_passes(self):
        staged = [
            "desktop/src/main/java/com/mobixournal/desktop/tools/InspectorPanel.kt",
            "docs/desktop-ui-layout.md",
        ]
        with mock.patch.object(self.mod, "staged_files", return_value=staged):
            self.assertEqual(self.mod.main(), 0)

    def test_non_ui_core_change_without_docs_passes(self):
        staged = ["mobix-core/src/main/java/com/mobixournal/render/PlotInk.kt"]
        with mock.patch.object(self.mod, "staged_files", return_value=staged):
            self.assertEqual(self.mod.main(), 0)

    def test_skip_env_bypasses_small_internal_fix(self):
        staged = ["app/src/main/java/com/mobixournal/ui/EditorTopBar.kt"]
        with mock.patch.object(self.mod, "staged_files", side_effect=lambda diff_filter="ACM": [] if diff_filter == "A" else staged):
            with mock.patch.dict(os.environ, {"MOBIX_SKIP_DOC_CHECK": "1"}):
                self.assertEqual(self.mod.main(), 0)

    def test_skip_env_refused_for_new_or_multi_file_watched_changes(self):
        many = [
            "app/src/main/java/com/mobixournal/ui/A.kt",
            "app/src/main/java/com/mobixournal/ui/B.kt",
            "app/src/main/java/com/mobixournal/render/C.kt",
        ]
        with mock.patch.object(self.mod, "staged_files", side_effect=lambda diff_filter="ACM": [] if diff_filter == "A" else many):
            with mock.patch.dict(os.environ, {"MOBIX_SKIP_DOC_CHECK": "1"}):
                self.assertEqual(self.mod.main(), 1)

        added = ["app/src/main/java/com/mobixournal/ui/NewPanel.kt"]
        with mock.patch.object(self.mod, "staged_files", return_value=added):
            with mock.patch.dict(os.environ, {"MOBIX_SKIP_DOC_CHECK": "1"}):
                self.assertEqual(self.mod.main(), 1)


if __name__ == "__main__":
    raise SystemExit(unittest.main())
