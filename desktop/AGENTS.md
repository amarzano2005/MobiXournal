# AGENTS.md — `desktop` (Windows) module rules

Windows Compose Multiplatform shell consuming `mobix-core`. All rules in root `AGENTS.md` apply.

## Flat-workstation UI and controls

- Follow `docs/desktop-workstation.md`, `docs/desktop-ui-layout.md`, and `docs/interface-contract.md`: opaque `DESKTOP_*_RGB` tones with 1 dp `bandDivider` and **no shadows** on bands, capsules, or canvas edge; opaque 8 dp `popupSurface` popups, 4 dp `controlShape`, `DesktopDialogFrame` (`tools/DesktopDialogFrame.kt`) for modals, `DesktopButton` (4 variants: `Primary`, `Secondary`, `Ghost`, `Destructive`), `DesktopIconButton` (`tools/DesktopButtons.kt`), `DesktopTextField` (`tools/DesktopInputs.kt`), and `ChoiceRow` / `DesktopTextAction` (never Material buttons or `FilterChip`).
- Open native popup windows only through `GlassPopup` (immediate opaque first frame; no raw `Popup(`, no `glassSpring` / `GlassPresence` inside `GlassPopup` — `GlassPopupGuardTest`, `DesktopPopupMaterialTest`); route every popup trigger through `DismissGuard` (`DismissGuardTest`).
- Read every glyph from `mobix-core` catalogues (`ToolGlyphCatalogue`, `ChromeGlyphCatalogue`, `SettingsGlyphCatalogue`), never `material.icons`. Every chrome action must bind a physical-key shortcut documented in `docs/ui-modernization.md` §3 (`ShortcutTableTest`).
- Note `DesktopTextField` fixes `.height(height)` (30 dp default) even when `singleLine = false`; adjust explicitly for multiline fields. Desktop has no menu-bar Edit menu (`shell/DesktopMenu.kt` holds File, PDF/Export, Pen/Workspace).
- The inspector lane (`tools/InspectorPanel.kt`) opens only for pending placement or single-element editing (footer: Cancel left, Place right). Plot live preview lives in `tools/PlotPreview.kt` (`PlotQuestionPreview`) with explicit `remember(...)` keys.

## Selection, slots, search, and verification

- `DesktopClipboard` (`sheet/DesktopSelectionCommands.kt`) is in-memory only; OS clipboard writes need an explicit helper. Focused text fields take keyboard priority via `WindowKeys.focusField`.
- Adding a `LivingSlot` requires arms in `PendingPlacement.kt`, `LivingSlots.kt`, `LivingGesture.kt`, `PlacementPick.kt`, and `InspectorPanel.kt`; search commands live under `shell/search/`.
- Desktop UI guards (`DesktopThemeTest`, `DesktopDialogContractTest`, `DesktopTextFeedbackContractTest`, `GlassPopupGuardTest`, `DesktopPopupMaterialTest`, `DesktopSettingsGlyphTest`, `DesktopSettingsStyleTest`, `DesktopShapeFeedbackTest`, `DismissGuardTest`, `ShortcutTableTest`, `ChromeMaterialRenderTest`, `LightMaterialTest`, `ToolStripsTest`) and unit tests live flat under `desktop/src/test/java/com/mobixournal/desktop/` (`:desktop:test`).
- Verify UI changes with `scripts/verify.cmd --ui-guards` (Windows) or `scripts/verify.sh --ui-guards` (POSIX), and always refresh `:desktop:createDistributable`.
