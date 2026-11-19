# The check that closes a UI change

> Read when you design or review a user-facing change: the design declaration `done` demands before a task can be archived.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

The build, packaging and release pipelines this sits beside are [tools.md](tools.md)'s.

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
empty one is refused, and an unknown axis is refused with the vocabulary. It cannot read a screenshot, only that the question was asked and answered.
Implementation `scripts/todo/design.py`; regression check `python3 scripts/todo/test_design.py`, run by CI.
