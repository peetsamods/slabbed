# AGENTS.md - Slabbed Agent Rules

## Required first reads

Before any Slabbed work, read:

1. `00_SLABBED_SOURCE_INDEX.md`
2. `01_SLABBED_CANONICAL_DOCTRINE.md`
3. `02_SLABBED_ACTIVE_STATUS.md`
4. `SLABBED_SPINE.md`

Treat `SLABBED_SPINE.md` as current operating context, but verify it against Git before edits.

## Required preflight

Before edits, builds, tests, commits, or savepoints, run:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD
```

Hard stop unless root is:

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

Do not work from `.windsurf/worktrees/*`.

## SLABBED_SPINE.md update discipline

Update `SLABBED_SPINE.md` when current operating truth changes:

- root / branch / HEAD / tag changes
- active slice changes
- next safe action changes
- live proof changes the status of a fix
- a stop condition is hit and the next agent needs the reason
- a stale or dangerous interpretation is discovered

Do not use `SLABBED_SPINE.md` as a scratchpad, full running log, Bug Blaster archive, commit index, or research notebook.

At every savepoint, either update `SLABBED_SPINE.md` or explicitly report:

```text
SLABBED_SPINE.md unchanged because current operating truth did not change.
```

## Savepoint discipline

A savepoint is not complete until:

1. intended files only are staged
2. validation passed
3. commit created
4. annotated `save/...` tag created
5. branch pushed
6. tag pushed
7. final tree verified
8. `SLABBED_SPINE.md` is updated or explicitly declared unchanged

## Scope discipline

Work one slice only. Do not broaden into adjacent bugs, Phase20, Ultra2, category expansion, release cleanup, or broad goblin chaos unless Julia explicitly says so.

If two attempts fail or guessing starts, stop and report:

- tried
- observed
- proven
- unproven
- next safest audit path
