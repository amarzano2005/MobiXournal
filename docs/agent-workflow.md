# Agent instruction discovery and consistency

> Read when changing agent guidance, local skills, task instructions, or investigating an agent that ignores project conventions.

The working preferences are [AGENTS.md](../AGENTS.md)'s. This page owns how that guidance is
discovered and checked, rather than repeating the preferences. Host detection belongs to
[host-profiles.md](host-profiles.md); documentation impact belongs to
[doc-governance.md](doc-governance.md).

## The instruction entry points

Keep the root instruction file as a small map and the decisions needed for every task. Conditional
detail goes in the owning spoke, skill, or module guide (`mobix-core/AGENTS.md`, `app/AGENTS.md`,
`desktop/AGENTS.md`). Codex's default combined project-instruction budget is 32 KiB; this
repository reserves half of that for the root file, leaving plenty of headroom for nested
module guidance. A line count alone cannot protect against byte truncation, especially with Unicode
copy. See [Codex instruction discovery](https://learn.chatgpt.com/docs/agent-configuration/agents-md).

Repository skills live in `.agents/skills/`, the
[documented discovery location](https://learn.chatgpt.com/docs/build-skills):
[todo](../.agents/skills/todo/SKILL.md), [new-living-element](../.agents/skills/new-living-element/SKILL.md),
and [close-task](../.agents/skills/close-task/SKILL.md). Each entry point carries the name and
description used for selection. Operator wrappers remain `scripts/todo.sh` (POSIX) and
`scripts/todo.cmd` (Windows cmd/PowerShell); callers need not know the `scripts/todo/` layout, and
both wrappers probe Python 3.11+ while skipping Windows Store aliases unless `TODO_PYTHON` overrides them.

Moving a skill requires updating its callers and checking the available-skill list in a fresh
session. This chat may retain its initial catalogue: reading the explicit entry point remains the
fallback until the host discovers the moved skill. File placement checks prove the repository
layout, not that every running client has refreshed its catalogue.

## Automated checks for known instruction failures

`AgentInstructionsTest` checks the root's UTF-8 byte budget, the combined budget along nested
instruction paths, discoverable skill entry points, their local Markdown links and CI coverage of
the task-instruction regression suite. The app test task declares the skill tree, task tools and
instruction files as inputs so a guidance-only edit cannot leave these guards UP-TO-DATE.

`scripts/todo/todo.py validate` also calls `scripts/todo/operational.py` for active tasks. It rejects
the retired install identifier, unqualified claims that the current machine lacks device tools,
and explicit device-verification test titles whose emulator flag disables that work. Dated or
clearly labelled historical observations remain possible; completed deliveries are not rewritten.
The regression suite is `scripts/todo/test_operational.py`, also run in CI.

Beside it, `scripts/todo/plan_rules.py` enforces plan coherence across active work: acyclic
`depends_on` edges whose `order` ascends and `urgency` never rises downstream, mandatory
`status = "blocked"` on tasks downstream of an undecided `scope`, active children for non-research
epics, concrete repo anchors and verification steps without open design imperatives on `task`
entries, no standalone follow-up `docs` tasks after code tasks, coherent `rebuild` flags, clean
UTF-8 symbols, host-neutral AVD/doc pointers, existing `targets` paths, `reviewed >= created`,
and resolution of cited `docs/` files and Gradle test modules (`scripts/todo/test_plan_rules.py`
runs in CI). For rolling-wave hygiene and faster task startup, `scripts/todo/doctor.py` powers
`todo brief <id>` (dependency summaries, doc/source ceiling warnings) and `todo doctor` (git-stale
targets, missing paths, and WIP advisories), guarded in CI by `scripts/todo/test_doctor.py`;
sequential task closing checks run through `scripts/verify.cmd` and `scripts/verify.sh`
([tools.md](tools.md#task-verification-wrapper)).

Pre-commit enforcement runs through `.githooks/pre-commit`, which locates Python 3.11+ portably
(skipping `WindowsApps` stubs) and runs both `scripts/check-doc-sync.py` (covering Android `ui/`,
desktop `shell/`/`tools/`/`settings/`/`sheet/`/`navigation/`, and core `design/`) and
`scripts/todo/todo.py validate`. CI exercises `scripts/test-check-doc-sync.py`,
`scripts/test-verify.py`, and `scripts/test-emulator.py` alongside the task-tooling suites.

These are conservative checks for known failures, not a parser of every natural-language request.
A feature description can mention an emulator without requiring a device pass. Review still has
to reconcile the user's goal, task metadata and evidence before closing the work.

## Scenarios for reviewing an instruction change

Use these small requests to review routing and decisions after editing guidance. They are manual
behaviour checks, not evidence that a model will always comply; run them in a fresh session when
validating host discovery or automatic skill selection.

| Request or condition | Expected decision | Evidence to inspect |
|---|---|---|
| Audit instructions without implementing changes | Read relevant owners and report findings; no application packaging solely for the question | tool calls and report scope |
| Internal refactor with no user-facing effect | Consult the owning spoke and governance; leave the README alone when its answers did not change | resulting diff |
| Explicit emulator verification with its flag disabled | Reconcile the task through the CLI before device work | validator failure and corrected task |
| Old task says its machine lacked device tools | Detect today's host; preserve historical limits as history | host checks and verification report |
| UI request suggests a different framework or palette | Apply repository interface rules and shared shell architecture | design declaration and diff |
| User narrows a task during execution | Carry the latest scope into the remaining work and report | final diff and delivery summary |

The test inventory and commands are [testing.md](testing.md)'s and [tools.md](tools.md)'s.
