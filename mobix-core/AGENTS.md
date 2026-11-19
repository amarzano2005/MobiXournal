# AGENTS.md — `mobix-core` module rules

Shared pure-JVM core consumed by both Android (`app/`) and Windows (`desktop/`).
All rules in root `AGENTS.md` apply; keep this module strictly free of Android, Compose, and UI dependencies.

## Model, codecs, and interchange

- `Element` (`format/model/Element.kt`, `Living.kt`) is sealed; codecs dispatch exhaustively in `format/mobix/MobixElementCodec.kt`.
- Every living element and sub-record preserves unknown JSON keys verbatim (`RawElement` fallback for unrecognised types).
- Never serialise `NaN` or `Infinity` into JSON, and never read/write `Locale.getDefault()` in codecs or formatters.
- One rendering dispatch (`render/LivingInk.kt`) feeds canvas, `PdfPagePlan.kt`, `PageSvgWriter.kt`, and `XoppSynthesis.kt`.
- When adding a new living element family, follow `.agents/skills/new-living-element/SKILL.md`.

## Testing and structure

- Run pure-core tests with `:mobix-core:test`.
- `MobixSchemaGuardTest.kt` guards `.mobix` element types; `FormatDriftTest.kt` covers `.xopp` fixtures only.
- `XoppExportCoverageTest.kt` and `XoppSynthesisTest.kt` live under `app/src/test` (run via `:app:testDebugUnitTest`).
- Keep source files <= 700 lines and brace-bodied functions <= 100 lines (`SourceStructureTest`).
