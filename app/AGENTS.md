# AGENTS.md — `app` (Android) module rules

Android Jetpack Compose shell consuming `mobix-core`. All rules in root `AGENTS.md` apply.

## UI contract, menus, and placement

- Follow `docs/interface-contract.md` and `docs/ui-modernization.md`: use glass bands and capsules (`rememberGlassSurfaceColor()`, `bandClosingEdge` / `bandSideEdge`, no band cast elevation) and floating surfaces (`rememberFloatingSurfaceColor()`, `Modifier.floatingRim()`, 20 dp `FloatingDialog` / `FloatingSurface` / `GlassToastHost`; never opaque Material cards or system `Toast`).
- Read every colour, radius, and motion value from `DesignTokens` / `ModernTheme` (`ui/theme/`) — no `Color(0x...)` literals outside `ui/theme/` (`ColorContractGuardTest`), use `rememberChromeInk()` on glass (never bare `Color.White`), only shipped Inter weights `Normal` / `Medium` / `Bold` (`TypeContractGuardTest`), transparent system bars (`SystemBarsGuardTest`), and clip-before-click shaped state layers (`ShapeFeedbackTest`).
- Menus must use `ToolbarMenu` (`ui/ToolbarPopup.kt`), never raw `DropdownMenu` (`ToolbarMenuGuardTest`); all tool, settings, and chrome glyphs read `mobix-core` catalogues (`ToolGlyphCatalogue`, `ChromeGlyphCatalogue`, `SettingsGlyphCatalogue`; `ToolGlyphCoverageTest`, `SettingsGlyphParityTest`, `SettingsCopyParityTest`).
- Do not grow `ui/EditorRegions.kt` (at its legacy line ceiling); touch, pen, and PDF long-press gestures route through `render/DrawingSurfaceInput.kt`, `CanvasGestureRouter.kt`, `SelectionGestureController.kt`, and `PdfLongPress.kt`.
- Element placement routes through `EditorTool` / `PlaceKind` / `ShapeKind` and `DialogPresence` (not `LivingSlot`, which appears only in `DrawingSurfaceStrokes.kt` for plots).
- Enforce one selection owner per pane (`docs/pdf.md`): model `SelectionActionBar` vs `TextSelectionBar` (PDF words); Back dismissal routes through `ui/EditorBackHandler.kt`.
- `TableCellEditorModel.kt` exposes `TableCellEditorState` (paired with `TableCellEditorDialog.kt`).

## Guards and verification

- Guard suites live under `app/src/test/java/com/mobixournal/guards/` (`DocsGuardTest`, `DocClaimsTest`, `SourceStructureTest`, `AgentInstructionsTest`, `CiCoverageTest`, `XoppExportCoverageTest`, `ColorContractGuardTest`, `ChromeGeometryGuardTest`, `TypeContractGuardTest`, `SystemBarsGuardTest`, `SettingsCopyParityTest`) plus `ui/ToolbarMenuGuardTest.kt`, `ui/ShapeFeedbackTest.kt`, `ui/ToolGlyphCoverageTest.kt`, and `ui/SettingsGlyphParityTest.kt`.
- Non-document exports run through `MainActivitySaves.kt` / `MainActivityPortableCopy.kt` and `EditorOverlays.kt` (`ExportAsHost`); in-place saves on `.pmobix` are refused.
- Build and verify with `scripts/verify.cmd [--ui-guards]` (Windows) or `scripts/verify.sh [--ui-guards]` (POSIX).
