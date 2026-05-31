# SLABBED Skills and Commands

Status: consolidated replacement for `SKILL.md`, `slabbed-git-skills.md`, `ClaudeHandoff.txt`, client offset triad skill, and protected carpet note.
Updated: 2026-05-04.

## Skill — Client Offset Triad

Problem: Slabbed applies client dy offsets, but Minecraft has three separate surfaces:

1. Model
2. Outline
3. Raycast

If only model changes, outline floats. If model and outline change but raycast does not, clicking happens in the wrong place.

Invariant:

For any rendered block/item with `dy != 0`, model, outline, and raycast must use the same dy source.

Authoritative helper:

```java
ClientDy.dyFor(world, pos, state)
```

Rules:

- all three paths call shared dy authority
- no duplicate dy logic
- no static mutable “current dy”
- use ThreadLocal only if needed and clear in `try/finally`
- stop if a change updates only one or two surfaces

Test per new offsettable:

- full block lane
- bottom slab lane
- top slab lane
- model height correct
- outline hugs model
- clicking matches outline
- reload does not reintroduce mismatch

## Skill — Carpet + Global Model Dy Coexistence

Protected hotfix memory:

- global model shift path remains in `OffsetBlockStateModel`
- carpets are special inside the same quad pipeline
- carpets must use `ClientDy.dyFor(view, pos, state)` for model dy when appropriate
- non-carpets use general `SlabSupport.getYOffset(view, pos, state)` path
- `SlabSupportStateMixin.getOutlineShape` must skip carpets to prevent recursion / StackOverflow

If carpets regress:

1. check carpet override in `OffsetBlockStateModel`
2. check carpet skip in `SlabSupportStateMixin`
3. check for a second competing dy path / double shift

## Skill — Git Savepoint + Push

Goal: produce clean, reproducible savepoint.

Hard stops:

- not in git repo
- wrong root
- unexpected dirty files
- no origin remote
- build/proof fails
- invalid/existing tag

Steps:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git remote -v
./gradlew --no-daemon compileJava compileGametestJava
./gradlew --no-daemon runClientGameTest --console plain
```

Then stage intended files only, commit, annotated tag, push branch, push tag, verify status.

Output report:

- root
- branch
- old HEAD
- new commit
- tag
- validation results
- pushed branch yes/no
- pushed tag yes/no
- final tree

## Skill — GitHub Repo Bootstrap

Use only for new repo setup.

Hard stops:

- not a git repo
- zero commits
- ambiguous/existing origin
- auth failure

Steps:

1. confirm local repo and commits
2. check existing origin
3. ensure branch naming
4. create repo via UI or `gh`
5. push branch and tags
6. output report

## Skill — Code Review / Static Audit

Use when asked for review, after two failed implementation attempts, or when build/live proof contradicts claimed fix.

Focus:

- logic errors
- edge cases
- null/reference risks
- race/sync/cache issues
- resource leaks
- API contract violations
- mapping/mixin registration drift
- release classpath closure
- violations of Slabbed state law

Do not report low-confidence speculation. If evidence is incomplete, say exactly what remains unproven.

## Skill — Research First

Before novel architecture, new category family, or unfamiliar support pattern, perform prior-art research.

Sources:

- Modrinth
- CurseForge
- GitHub source
- Fabric/Yarn/Mojang docs where relevant
- known reference mods

Required output:

- existing prior art
- solution patterns
- what they avoid
- constraints/failure modes
- decision gate

Do not propose hooks or code before research is complete.

## Command — `/claude`

Produce a comprehensive single-pass Claude Opus handoff.

Must include:

- done / partial / not started
- invariants and non-negotiables
- remaining work as ordered slices
- exact systems/files where known
- Windsurf-ready plan: skills, branches, tags, tests, stop conditions
- token discipline: grep first, avoid rereading, no speculative broad audits

## Command — `/cc`

Produce concise copy-pasteable context handoff.

Must include:

- current root/branch/HEAD/tag
- tree state
- current WIP
- latest savepoints
- exact next action
- commands to run
- bug blasters relevant to next slice
- source/Notion update instructions when relevant

## Command — `/hmh`

Hold-my-hand mode.

Use beginner-friendly steps. One step at a time. Include exact command/click instructions and stop conditions.

## Command — `/storm`

Brainstorm mode.

Use for strategy, not repo edits. Keep doctrine intact.

## Command — `/brutal` / `/scalpel`

Direct critique mode.

Use for design/code review; do not soften technical risks.
