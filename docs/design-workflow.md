# The design skill, and the check that closes a UI change

> Read when you design or review a user-facing change: the vendored design skill, and the design declaration `done` demands before a task can be archived.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

The build, packaging and release pipelines this sits beside are [tools.md](tools.md)'s.

## UI/UX Pro Max (design skill)

The agent-facing design skill vendored under `skills/`: UI styles, product palettes, font pairings, UX
guidelines, chart types and per-stack implementation notes — including **Jetpack Compose** and **WPF**,
the two stacks this product ships on. It is committed like `skills/todo` because it is text and scripts
only (no binaries), so a clone needs no per-machine install.

**When it is consulted.** Before a UI change lands, as the *checklist*: it names the markers a page can
carry without anyone deciding to (a weight the family does not ship, body text under 12 sp, one ellipsis
spelled two ways, an icon control with no accessible name, a press state that shifts layout, a gradient
used as decoration). It supplies rules to check against and never the verdict —
`docs/interface-contract.md` decides, and where the two disagree the contract wins, because its 36/40 dp
controls and its one-glyph catalogue are recorded decisions rather than drift.

```sh
scripts/design.sh --domain ux "modal keyboard focus"
scripts/design.sh --domain style "tool app chrome"
scripts/design.sh --domain typography "dense toolbar type scale"
scripts/design.sh --design-system "tablet note-taking editor"
```

`scripts/design.sh` finds a real Python 3.8+ and **refuses the Store stub** (AGENTS.md, Profile D), so
no interpreter path is pasted into a session, and `DESIGN_PYTHON` overrides the search. `--domain`
answers well; `--stack jetpack-compose` is thin in the shipped CSV, so an empty result there is the
corpus rather than the install.

`skills/ui-ux-pro-max/SKILL.md` carries the skill's own workflow; it and its data came from the upstream
repository, whose **web-page** skills (shadcn/Tailwind styling, brand books, slide decks, banners) are
not vendored — a Compose/WPF product has no website to style. The upstream installers (`npx skills add`,
`uipro init`) need Node, absent here, so a refresh is a copy of the same folders.

## Closing a UI change

`docs/interface-contract.md` owns what a change must satisfy; this is the half a machine holds. **`done`
refuses to archive a task without a design declaration**, so a change that touched the interface records
which lines it verified instead of leaving the question to memory.

```sh
scripts/todo.sh done <id> --shipped "what shipped" \
    --design "material: capsule in the ribbon's lane; states: hover and focus added"
scripts/todo.sh done <id> --shipped "..." --design "none: docs only, no interface surface"
```

A declaration is `axis: answer` clauses split by `;` (or one per line, in `--design-file`), in the contract's own checklist words: `material`,
`tokens`, `glyph`, `states`, `copy`, `keyboard`, `parity`, `docs`, `tests`. Two answers are the minimum, an
empty one is refused, and an unknown axis is refused with the vocabulary. A `rule: <Category>/<Issue>`
clause cites the corpus and every id must exist in `ux-guidelines.csv` — checked there, skipped where the
skill is not vendored. It cannot read a screenshot, only that the question was asked and answered.
Implementation `scripts/todo/design.py`; regression check `python3 scripts/todo/test_design.py`, run by CI.
