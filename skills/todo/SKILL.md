---
name: todo
description: >-
  Read and edit this project's task list. Use whenever the user says "add this
  to the todo", "what's next / what's on the todo", "mark X done", "edit the
  todo", "how many tasks / how many bugs are left", or otherwise wants to query
  or change TODO.toml / FINISHED.toml. The task list is TOML, not Markdown —
  drive it through this skill's CLI so ids, ordering, and metadata stay
  consistent.
---

# Todo skill

The active task list lives in **`TODO.toml`** and the completed-work archive in
**`FINISHED.toml`** at the repo root. They are the single source of truth for
what's in flight and what has shipped (see `AGENTS.md` → documentation map).
Both are plain TOML, edited through a small dependency-free Python CLI so every
write stays diff-friendly and every task carries full metadata.

## How to run it

```sh
scripts/todo.sh <command> [options]          # operator wrapper (from repo root)
# or directly:
python3 scripts/todo/todo.py <command> [options]
```

Run `scripts/todo.sh <command> --help` for a command's options.

## The schema

Each file is a document: a `[meta]` header (title, purpose, rules) then an
array of `[[task]]` tables. A task carries **more** metadata rather than less:

| Field         | Where            | Meaning                                             |
|---------------|------------------|-----------------------------------------------------|
| `id`          | both             | stable kebab-case slug (the handle for every command) |
| `title`       | both             | short one-line summary                              |
| `description` | both             | the detail                                          |
| `status`      | both             | active · in-progress · blocked (TODO) / finished (archive) |
| `level`       | both             | task · scope · epic — how big it is, and how to work it |
| `category`    | both             | feature · bug · docs · refactor · test · chore     |
| `urgency`     | TODO             | low · normal · high · critical                     |
| `order`       | TODO             | manual sort key (10, 20, 30…); lower = sooner       || `created`     | both             | date the task was added (`YYYY-MM-DD`)              |
| `completed`   | archive          | date it was finished                                |
| `release`     | archive          | version it shipped in (`"1.2.0"`); **absent = pending the next version** |
| `tags`        | TODO             | freeform string list                                |
| `rebuild`     | TODO             | rebuild the Android app for this task? (default `true`) |
| `emulator_debug` | TODO          | run the full emulator verify loop for this task? (default `false`) |

`release` is the one field that separates *shipped* work from work still on its way out, and it is
what makes a changelog correct: the archive holds **every** finished task forever, so without the
stamp you cannot tell what the next version actually contains. It is written by `release` (below),
never by hand.

## Levels — task, scope, epic

`level` says **how big the item is and therefore what "working" it means**. It is
three-tiered, smallest first:

- **`task`** (default) — atomic and implementable as written. Just build it.
- **`scope`** — not yet implementable. Working it means **investigating the code**
  to find out what actually needs to happen, then running `add` for the concrete
  atomic `task`s it breaks into. A scope item produces **todos, not code**; close
  it with `done` once its children exist.
- **`epic`** — a large, spanning feature (the radial palette, say). It will never
  have hyper-defined atomic steps up front. When you encounter an epic, **do not
  try to implement the whole thing**: scope out the next steps and `add` child
  items — `scope` items where more investigation is needed, `task` items where the
  work is already clear. An epic usually stays active across several rounds of
  this, shrinking as its children ship.

**Write `task`-level items for a small model.** A `task` description must be
detailed enough that a low-end local model (think `qwen3.5:9b`) can carry it out
without further investigation: name the files and functions to touch, state the
exact behaviour wanted, and spell out how to verify it. No "figure out where this
lives" — that's what `scope` is for. **Many small, fully-specified tasks beat a few
broad ones**, so split freely; a task that would need judgement calls or code
exploration is really a `scope`.

The rule of thumb: **an epic spawns scopes, a scope spawns tasks, a task spawns
code.** When the user says "add this epic," they are asking for the big
definition to be recorded — the breakdown happens later, when it's picked up.

Set it with `add --level scope|epic` or `edit <id> --level …`; filter with
`list --level epic` and tally with `count --by level`.

`rebuild` and `emulator_debug` are **build hints** for whoever works the task.
`rebuild` defaults to **`true`** and `emulator_debug` to **`false`** — the normal
loop is "build the APK and run the unit tests," with no emulator pass. Turn
`emulator_debug` **on** for a task whose runtime behaviour you actually need to
see (install and exercise it on the emulator: screenshots, logcat, simulated
touches). Set `rebuild` **`false`** for a run of rapid-fire tweaks that don't each
need a fresh build; you still build/verify once at the end. Like `urgency`/`order`, they're
TODO-only and are dropped when a task is archived.

Active tasks list most-urgent-first, then by `order`. The archive is
newest-`completed`-first.

## Commands

- **`list`** `[--finished] [--unreleased] [--status S] [--category C] [--level L] [--json]` —
  list tasks. `--unreleased` (with `--finished`) is the **pending set**: the finished tasks the next
  version will ship, i.e. what its changelog is drawn from.
- **`show <id>`** `[--json]` — print one task with its full description.
- **`stats`** `[--json]` — totals plus counts by status, category, and urgency
  (active) and by category (finished). This is the "how many …" answer.
- **`count`** `[--finished] [--by status|category|urgency|level]` — a raw count, or
  a grouped tally.
- **`add --title T --description D`** `[--category C] [--urgency U]
  [--level L] [--status S] [--tag t …] [--id ID] [--no-rebuild] [--emulator-debug]` —
  append an active task. The `id` is a slug of the title (made unique) and
  `order` auto-increments unless given. `urgency` defaults to normal.
  `level` defaults to `task`. `rebuild` defaults to yes (`--no-rebuild` turns it off); `emulator_debug`
  defaults to no (`--emulator-debug` turns it on).
- **`edit <id>`** `[--title|--description|--status|--level|--category|--urgency|--order
  …] [--add-tag t] [--rebuild|--no-rebuild] [--emulator-debug|--no-emulator-debug]`
  — change fields on an active task.
- **`done <id>`** `[--date YYYY-MM-DD]` — move an active task into
  `FINISHED.toml`, stamped `completed` (today unless `--date`), newest-first.
- **`release`** `--version X.Y.Z` `[--date D] [--through D] [--no-changelog]
  [--bump-gradle] [--force] [--dry-run]` — **freeze a release**: stamp every pending archive task
  with the version, record `latest_release` in the archive meta, and write the draft
  `docs/releases/vX.Y.Z.md` grouped by category (plus a compare link to the previous version).
  Refuses to overwrite an existing changelog (`--force`), refuses a version that is not newer than
  the last recorded one, and refuses to run with nothing pending. `--bump-gradle` also sets
  `versionName` and increments `versionCode` in `app/build.gradle.kts`. `--through YYYY-MM-DD`
  narrows the stamp to tasks finished by that date — the one-shot backfill for a release older than
  this field. `--dry-run` prints the draft and changes nothing. Implementation: `release.py`;
  regression check: `python3 scripts/todo/test_release.py`.
- **`remove <id>`** `[--reason "…"]` — drop an active task (e.g. descoped).
- **`verify-release --version X.Y.Z`** — check a version was frozen before it is tagged: the
  archive must record that version as released, `docs/releases/vX.Y.Z.md` must exist, and
  `versionName` in `app/build.gradle.kts` must match. Exits non-zero with the reason otherwise.
  CI runs it on every `v*` tag, so a tag whose release was skipped cannot publish.
- **`validate`** — lint both files: required fields, unique ids, valid enum
  values, archive tasks have a `completed` date, and `release` (archive) an X.Y.Z version.
  Exits non-zero on any problem.
- **`migrate`** `[--created YYYY-MM-DD]` — one-shot cleanup of legacy/rough TOML
  into the current schema. Already run during the Markdown→TOML migration; kept
  for re-runs.

## Working rules (mirror of `AGENTS.md`)

- **A release freezes the pending set; never hand-pick a changelog.** Everything finished since
  the last release has no `release` stamp, and `todo.sh release --version X.Y.Z` turns exactly that
  set into a version: it stamps the tasks and drafts the changelog. So the correct workflow is
  `done <id>` as work lands, then `release` at the tag — a finished task forgotten in `TODO.toml`
  or left unstamped is a changelog that lies. The release procedure is owned by `docs/tools.md`
  (see *Cutting a release*).
- When you finish a task (built, tested, documented), run **`done <id>`** in the
  same commit that completes the work — don't leave shipped items in `TODO.toml`.
- **After every `done <id>`, run `stats` and report how many open items remain in
  `TODO.toml`** to the user, as part of the same reply.
- **Documentation belongs to the task that wrote the code — never to a task of its
  own.** Whenever a task changes code, it updates `README.md` and the owning spoke doc
  in the same task and the same commit. Don't `add` a "document X" follow-up for work a
  task already did, and don't `done` a task whose docs are still stale.
- When you notice the next thing to build, **`add`** it rather than losing it.
- **Picking up a `scope` or `epic` item means breaking it down, not building it.**
  Investigate, then `add` the child items at the next level down; only `task`-level
  items get implemented directly.
- Prefer the CLI over hand-editing so metadata and ordering stay consistent; if
  you do hand-edit, run **`validate`** afterward.
