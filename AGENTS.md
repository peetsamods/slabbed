# AGENTS.md - Slabbed 26.1.2 Port Rules

These rules apply only to the dedicated MC 26.1.2 port checkout:

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Do not apply this file to `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate` or any other Slabbed tree.

## Required First Reads

Before any Slabbed 26.1.2 port work, read:

1. `AGENTS.md`
2. `HANDOFF.md`
3. `SLABBED_SPINE.md`
4. `docs/lessons/LESSONS_INDEX.md`
5. `docs/porting/PORTING_MAP.md` for port/backport/API/mapping work
6. `docs/process/LIVE_DRIVE_PREFLIGHT.md` before any live-client or Modrinth-profile work
7. `docs/process/FALSE_GREEN_CHECKLIST.md` when automation proof and live behavior disagree
8. Relevant `docs/porting/*` notes for the active blocker

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

## When to Use Superpowers / Subagents

Use the Superpowers plugin to delegate to subagents when the work can be decomposed into independent units with bounded context.
Subagents are separate model runs and may cost more and slow the session through coordination overhead. Use them only when their outputs will directly reduce main-thread work, risk, or context size.

### Required invocation sequence

- **Must explicitly invoke** `@Superpowers` before launching any subagent work for this workspace.
- **Default mode:** when a request can be decomposed into 2+ independent slices, automatically delegate only if the cost/benefit rule below passes. Otherwise keep the work in the main context, even if `@Superpowers` skills are used.
- After `@Superpowers` is acknowledged, invoke the dispatcher (`@dispatching-parallel-agents` when there are multiple independent slices, or `@systematic-debugging` / other single-specialist skill as needed) in the same turn.
- Provide each subagent a fixed contract in one message:
  - Inputs and current file scope.
  - Expected output.
  - Hard constraints and non-goals.
  - Acceptance criteria.
- Do not proceed with delegated execution until the invocation sequence has happened in order.

### Use subagents when:
- The task can be split into **2 or more independent slices** with minimal or no shared mutable state.
- Each slice has a **clear contract**: inputs, expected outputs, constraints, and acceptance criteria.
- The work benefits from **parallel execution** or **separate review passes**.
- The amount of context needed per slice is much smaller than the full session context.
- A **fresh context** is valuable to reduce contamination from previous reasoning, false starts, or unrelated history.
- The task includes a **natural review boundary** such as:
  - spec compliance
  - correctness review
  - code quality review
  - regression check
- The cost of coordination overhead is lower than the cost of keeping the entire problem in one context.

### Do not use subagents when:
- The work is **tightly coupled** and requires continuous shared state across steps.
- The task is **small enough** that subagent setup and review overhead would dominate the total effort.
- The task is routine preflight, simple grep, a small edit, live-client/runtime debugging, or any step where the main thread must preserve one continuous evidence chain.
- The next step depends on information that is only available after the previous step completes, with no meaningful parallelism.
- The task requires **frequent interactive back-and-forth** with the user to resolve ambiguity before progress can be made.
- The task requires a single coherent reasoning chain where splitting context would reduce accuracy.
- The scope is unstable and cannot yet be decomposed into well-defined slices.
- The result depends on subtle cross-file or cross-system interactions that one agent must hold in working memory at once.

### Preferred pattern
- Use one subagent per independent problem domain.
- Give each subagent a narrow scope and explicit deliverable.
- Keep the main agent as coordinator only.
- Review outputs at the boundaries, not continuously.
- Re-dispatch with corrected context if a subagent reports `NEEDS_CONTEXT` or `BLOCKED`.

### Cost/benefit rule
Use subagents only when there are 2+ genuinely independent subtasks and at least one of the following is true:
- they enable parallelism,
- they materially reduce context size,
- they improve review isolation,
- or they reduce the chance of cross-contamination in reasoning.

Use subagents mainly for parallel read-only lanes: artifact summaries, dirty-tree classification, mod/tag inventories, log-marker extraction, or independent review passes. Otherwise, execute directly in the main context.

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
