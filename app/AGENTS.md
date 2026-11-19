# AGENTS.md — `app` (Android) module rules

Android Jetpack Compose shell consuming `mobix-core`. All rules in root `AGENTS.md` apply.

## UI, menus, and placement

- Menus must use `ToolbarMenu` (`ui/ToolbarPopup.kt`), never raw `DropdownMenu` (`ToolbarMenuGuardTest`).
- Do not grow `ui/EditorRegions.kt` (at its legacy line ceiling); touch, pen, and PDF long-press gestures route through `render/DrawingSurfaceInput.kt`, `CanvasGestureRouter.kt`, `SelectionGestureController.kt`, and `PdfLongPress.kt`.
- Element placement routes through `EditorTool` / `PlaceKind` / `ShapeKind` and `DialogPresence` (not `LivingSlot`, which appears only in `DrawingSurfaceStrokes.kt` for plots).
- Enforce one selection owner per pane (`docs/pdf.md`): model `SelectionActionBar` vs `TextSelectionBar` (PDF words); Back dismissal routes through `ui/EditorBackHandler.kt`.
- `TableCellEditorModel.kt` exposes `TableCellEditorState` (paired with `TableCellEditorDialog.kt`).

## Guards and verification

- Guard suites live under `app/src/test/java/com/mobixournal/guards/` (`DocsGuardTest`, `DocClaimsTest`, `SourceStructureTest`, `AgentInstructionsTest`, `CiCoverageTest`, `XoppExportCoverageTest`) plus `ui/ToolbarMenuGuardTest.kt`.
- Non-document exports run through `MainActivitySaves.kt` / `MainActivityPortableCopy.kt` and `EditorOverlays.kt` (`ExportAsHost`); in-place saves on `.pmobix` are refused.
- Build and verify with `scripts/verify.cmd` (Windows) or `scripts/verify.sh` (POSIX).
