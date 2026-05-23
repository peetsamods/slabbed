# AGENTS.md - Slabbed 26.1.2 Port Rules

These rules apply only to the dedicated MC 26.1.2 port checkout:

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Do not apply this file to `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate` or any other Slabbed tree.

## Required First Reads

Before any Slabbed 26.1.2 port work, read:

1. `AGENTS.md`
2. `SLABBED_SPINE.md`
3. Relevant `docs/porting/*` notes for the active blocker

Legacy phase19 source-index/doctrine/status files are not required in this checkout unless they are explicitly added here later. Treat `SLABBED_SPINE.md` as the current operating context for this port, but verify it against Git before edits.

## Required Preflight

Before edits, builds, tests, commits, or savepoints, run:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD
```

Hard stop unless the root is:

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

If the tree is dirty, inspect only the files relevant to the intended slice before editing. Do not auto-stash, clean, reset, or revert unrelated work.

## Scope Discipline

- Work one port slice only.
- Prefer mapping/tooling/classpath proof before source migration.
- Prefer one-file mechanical probes for source API drift.
- Do not broaden into gameplay behavior, beta release finalization, phase19 proof work, or unrelated compatibility families unless Julia explicitly says so.
- Preserve unrelated dirty files and untracked evidence, especially `tmp/` and `docs/porting/`.
- Stage only intended files.
- Never invent commits, tags, pushes, tests, or proof.

## 26.1.2 Port Defaults

- Base release provenance starts at `release/0.2.0-beta.4` / `f9014fb`.
- Branch is expected to be `port/mc-26.1.2` unless Git proves otherwise.
- Treat this checkout as an experimental port workspace until a clean savepoint proves otherwise.
- Java 25 / Gradle 9.4.x / Fabric Loom / mappings decisions must be proven from the active checkout or a verified donor, not guessed from broad cache scans.
- A green `buildEnvironment` is not proof that `compileJava` is green.

## SLABBED_SPINE.md Update Discipline

Update `SLABBED_SPINE.md` when current operating truth changes:

- root / branch / HEAD / tag changes
- active port blocker changes
- next safe action changes
- compile frontier changes
- mapping/provider/classpath proof changes
- a stop condition is hit and the next agent needs the reason
- a stale or dangerous interpretation is discovered

Do not use `SLABBED_SPINE.md` as a scratchpad, full running log, commit index, or research notebook.

At every savepoint, either update `SLABBED_SPINE.md` or explicitly report:

```text
SLABBED_SPINE.md unchanged because current operating truth did not change.
```

## Savepoint Discipline

A savepoint is not complete until:

1. intended files only are staged
2. validation passed
3. commit created
4. annotated `save/...` tag created
5. branch pushed
6. tag pushed
7. final tracked tree verified
8. `SLABBED_SPINE.md` is updated or explicitly declared unchanged

If any of those are missing, report the state as WIP, not a savepoint.

## When Blocked

Stop after two failed attempts or when evidence becomes unclear. Report:

- tried
- observed
- proven
- unproven
- next smallest audit

Do not continue wandering through adjacent source families.

## Compact Report Format

For repo work, use:

```text
Root:
Branch:
HEAD:
Tree:
Task:
Files inspected:
Files changed:
Finding:
Proof:
Status:
Next slice:
```
