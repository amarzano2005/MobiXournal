---
name: new-living-element
description: >-
  Checklist for introducing or extending a living element family across
  mobix-core, Android, Windows desktop, interchange exports, and architecture
  docs. Use when implementing any new Element subtype (code blocks, connectors,
  measurements, constrained geometry, matrices, scientific text, study cards).
---

# Adding or extending a living element

Follow the shared core rule in [AGENTS.md](../../../AGENTS.md): model, codecs,
rendering, and export compilation live once in `mobix-core`, while both shells
only wire editors, placement, and gestures around them.

## Ten-step checklist

1. **Model (`mobix-core/src/main/java/com/mobixournal/format/model/`)**
   - Put a new family in its own file implementing `Element` (`Element.kt`) and
     add it to `Element.isLivingEntity` (`Living.kt`) when its ink is derived
     from a stored source/recipe.
   - Keep `unknown` / `extraAttrs` preservation on the element and every nested
     record. Never store `NaN`, `Infinity`, or `Locale.getDefault()` state.
   - Read [mobix-living-entities.md](../../../docs/mobix-living-entities.md) and
     [mobix-element-fields.md](../../../docs/mobix-element-fields.md) first.

2. **Codec (`mobix-core/src/main/java/com/mobixournal/format/mobix/`)**
   - Add `Mobix<Name>Codec` and register it in `MobixElementCodec`'s exhaustive
     `when (element)` and `when (type)` arms.
   - Ensure older readers preserve unrecognised types verbatim as `RawElement`.
   - Pin round-trip and unknown-field preservation in `:mobix-core:test` and
     check `MobixSchemaGuardTest`.

3. **Shared rendering (`mobix-core/src/main/java/com/mobixournal/render/`)**
   - Add `<Name>Ink` and wire it into `LivingInk` so canvas, `PdfPagePlan`,
     `PageSvgWriter`, and `XoppSynthesis` share one geometry output.
   - Verify `.xopp` synthesis fallback and `mobix:meta` revival claim.

4. **Coverage & drift guards (`app/src/test/java/com/mobixournal/`)**
   - Add a representative sample to `guards/XoppExportCoverageTest.kt` and bump
     `elementFamilies` by 1.
   - Add export verification in `render/XoppSynthesisTest.kt` (Android test
     module `:app:testDebugUnitTest`, **not** `:mobix-core:test`).

5. **Capability declaration (`docs/mobix-capabilities.md`)**
   - If the element carries semantics an older build must not silently drop or
     rewrite, register a capability string in `MobixFormat.declaredCapabilities`
     and `docs/mobix-capabilities.md`.

6. **Android & Desktop shells**
   - **Android (`app/AGENTS.md`)**: register placement via `EditorTool` /
     `PlaceKind` and `DialogPresence` (not `LivingSlot`); respect `ToolbarMenu`,
     gesture routing (`DrawingSurfaceInput.kt` / `PdfLongPress.kt`), and one
     selection owner per pane. For the element editor, follow
     [interface-contract.md](../../../docs/interface-contract.md) §1: use
     `LivingInspectorAnchor` for contextual/visual inspectors (handles IME and
     custom-keyboard clamping) or `DialogPresence` + `FloatingDialog` with
     `verticalScroll` for structured/IME-heavy forms.
   - **Desktop (`desktop/AGENTS.md`)**: add `LivingSlot` arms in all 5 files
     (`PendingPlacement.kt`, `LivingSlots.kt`, `LivingGesture.kt`,
     `PlacementPick.kt`, `InspectorPanel.kt`); update `DesktopSelectionBarTest`
     (`EDIT` availability); use `DesktopDialogFrame` / `DesktopButton` /
     `DesktopTextField` (watch fixed `30.dp` height on multiline fields).

7. **Search, clipboard, and merge**
   - Index searchable source lines in `PageTextIndex.kt` and `DocumentSearch.kt`
     (plus `desktop/.../shell/search/`).
   - Remap any `ElementId` references during copy/paste/duplicate
     (`DrawingSurfaceSelection.kt`, `DesktopSelectionCommands.kt`) and
     `MobixMergeEngine.kt`.

8. **Documentation (same commit)**
   - Update [mobix-living-entities.md](../../../docs/mobix-living-entities.md)
     (if already at its 300-line cap, split a new spoke linked from
     [architecture.md](../../../docs/architecture.md)) and
     [mobix-element-fields.md](../../../docs/mobix-element-fields.md).
   - Record `.xopp` losses in [xopp-bridge.md](../../../docs/xopp-bridge.md),
     user workflow in [guide-stem-tools.md](../../../docs/guide-stem-tools.md),
     and platform parity in [guide-limitations.md](../../../docs/guide-limitations.md).

9. **Interface contract check**
   - Verify all axes in [interface-contract.md](../../../docs/interface-contract.md)
     (material class, tokens, catalogue glyphs, 5 states, English copy, shell
     parity) before preparing `--design`.

10. **Final verification**
    - Close through [close-task](../close-task/SKILL.md) using
      `scripts/verify.cmd` (Windows) or `scripts/verify.sh` (POSIX).
