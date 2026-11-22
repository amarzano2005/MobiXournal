---
name: close-task
description: >-
  End-of-task verification, documentation, archiving, desktop packaging, and
  atomic commit sequence. Use whenever finishing a task, scope, or bug fix
  before running `todo done` and reporting completion.
---

# Closing a task cleanly

Follow this sequence before marking any active item finished. Delivery rules live
in [AGENTS.md](../../../AGENTS.md#task-bookkeeping-and-delivery).

## Six-step sequence

1. **Review context & flags**
   - Run `scripts/todo.cmd brief <id>` (Windows) or `scripts/todo.sh brief <id>`
     (POSIX) to confirm `rebuild`, `emulator_debug`, dependency history, and
     whether any cited `docs/*.md` or `.kt` file is near its line budget.

2. **Check implementation + documentation parity**
   - Ensure source files stay ≤ 700 lines and brace-bodied functions ≤ 100 lines
     (`SourceStructureTest`), and every modified doc stays ≤ 300 lines
     (`DocsGuardTest`).
   - Confirm docs landed in the same work per
     [writing-docs.md](../../../docs/writing-docs.md) and check whether
     `README.md` needs changes per
     [doc-governance.md](../../../docs/doc-governance.md).

3. **Run sequential verification (`scripts/verify.cmd` / `scripts/verify.sh`)**
   - Never run parallel Gradle commands. Pass targeted `--tests` selectors plus
     flags matching UI scope (`--ui-guards`) and the task's `rebuild` requirement
     (`--build-apk` builds the signed release APK via `scripts/release-apk.sh`):
     - Windows: `scripts/verify.cmd [--ui-guards] [--build-apk] [-- ...]`
     - Linux/macOS: `scripts/verify.sh [--ui-guards] [--build-apk] [-- ...]`
   - Always refresh `:desktop:createDistributable` (the wrapper's default).
   - If `emulator_debug = true`, run the real device pass via `scripts/emulator.cmd`
     (Windows) or `scripts/emulator.sh` (POSIX) per
     [tools-emulator.md](../../../docs/tools-emulator.md) and capture evidence.

4. **Archive through the `todo` CLI**
   - Close with `done <id> --shipped "..." --design "..."` (see
     [todo](../todo/SKILL.md)):
     - `--shipped`: concrete files, behaviour, tests, docs, and anything
       deliberately deferred (never copy the old future-tense plan).
     - `--design`: at least 2 verified axes from
       [interface-contract.md](../../../docs/interface-contract.md), or
       `none: <reason>` when no UI surface changed.

5. **Advance downstream plan items when closing a `scope`**
   - Add every concrete child `task` with explicit file anchors, verification,
     `epic`, and `depends_on`.
   - Retarget and unblock any downstream `blocked` tasks whose scope dependency
     just resolved, then run `todo validate` and `todo doctor`.

6. **Report, commit atomically, and push**
   - Run `todo stats` and `todo list --ready` to verify remaining open counts.
   - Check `git status` for stray files, commit atomically with a clear why
     subject, and `git push`.
