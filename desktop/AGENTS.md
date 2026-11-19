# AGENTS.md — `desktop` (Windows) module rules

Windows Compose Multiplatform shell consuming `mobix-core`. All rules in root `AGENTS.md` apply.

## Flat-workstation UI and controls

- Follow `docs/desktop-workstation.md` and `docs/interface-contract.md`: opaque 8 dp popups, 4 dp `controlShape`, `DesktopDialogFrame` (`tools/DesktopDialogFrame.kt`) for modals, `DesktopButton` / `DesktopIconButton` (`tools/DesktopButtons.kt`), `DesktopTextField` (`tools/DesktopInputs.kt`), and `ChoiceRow` / `DesktopTextAction` (never Material3 `FilterChip`).
- Note `DesktopTextField` fixes `.height(height)` (30 dp default) even when `singleLine = false`; adjust explicitly for multiline fields.
- Desktop has no menu-bar Edit menu (`shell/DesktopMenu.kt` holds File, PDF/Export, Pen/Workspace).
- The inspector lane (`tools/InspectorPanel.kt`) opens only for pending placement or single-element editing (footer: Cancel left, Place right). Plot live preview lives in `tools/PlotPreview.kt` (`PlotQuestionPreview`) with explicit `remember(...)` keys.

## Selection, slots, search, and verification

- `DesktopClipboard` (`sheet/DesktopSelectionCommands.kt`) is in-memory only; OS clipboard writes need an explicit helper. Focused text fields take keyboard priority via `WindowKeys.focusField`.
- Adding a `LivingSlot` requires arms in `PendingPlacement.kt`, `LivingSlots.kt`, `LivingGesture.kt`, `PlacementPick.kt`, and `InspectorPanel.kt`; search commands live under `shell/search/`.
- Unit tests live flat under `desktop/src/test/java/com/mobixournal/desktop/` and run with `:desktop:test`. Always refresh `:desktop:createDistributable` at task completion.
