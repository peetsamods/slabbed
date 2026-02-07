---
name: slabbed-debug-popoff
description: Debug cases where an object places on slab tops but later breaks or pops off due to survival rechecks or updates.
---

# Slabbed — Workflow: Debug Pop-Off

## Goal
Systematically diagnose and fix cases where a category:
- places on slab tops, but
- later breaks/drops due to revalidation (neighbor updates, chunk reload, ticks, fluids, etc.)

Deliver a minimal, safe fix with strict visuals preserved.

## Inputs
- Category: `<category>` 
- Exact object(s) popping off (explicit list)
- Repro conditions (lane + trigger)

## Hard constraints
- Do not “patch randomly”
- Identify the exact recheck path first
- Minimize blast radius (prefer targeted mixins if shared hooks affect unrelated blocks)
- Strict visuals required

---

## Step 0 — Safety checkpoint
1) `git status` must be clean. If not: STOP.
2) Confirm current HEAD hash.
3) Run `./gradlew build`. If fail: STOP.

If this is happening mid-work, branch from your current feature branch and keep commits small.

---

## Step 1 — Reproduce reliably
In the repro world lanes:
- Full blocks lane (baseline)
- Bottom slab lane
- Top slab lane

Record:
- Which lane fails
- Whether it fails immediately or only after a trigger

---

## Step 2 — Identify the trigger type
Use the skill: `slabbed-neighbor-update-map` 

Run triggers until it fails and record the first trigger that causes pop-off:
- below block change
- adjacent block change
- above block change
- scheduled/random tick
- fluid update adjacency
- redstone propagation
- piston-induced update
- chunk unload/reload

Stop once you can trigger failure on demand.

---

## Step 3 — Locate the survival predicate (source of truth)
Use IDE navigation to find which method causes the break:
- usually `canPlaceAt(...)` 
- sometimes a neighbor update path or state revalidation

Requirements:
- record Yarn class + method signature
- record what condition fails (e.g., “needs solid top support”)
- confirm the check uses `pos.down()` or equivalent support lookup

If you cannot locate it: STOP and run `slabbed-mixin-target-discovery`.

---

## Step 4 — Decide the narrowest fix
Pick one:
- A) targeted mixin on the category’s `canPlaceAt` (preferred)
- B) targeted mixin on a single helper predicate used only by this category
- C) shared hook only if proven safe and limited to slab contexts

Rule:
If a shared hook changes behavior outside slab contexts, do not use it.

---

## Step 5 — Implement minimal fix
Implementation rules:
- All slab semantics go through `SlabSupport` 
- Keep injection narrow and documented
- Do not alter baseline full-block behavior

Build:
- `./gradlew build` must pass

---

## Step 6 — Validate fix
Use:
- `slabbed-test-matrix-and-repro-world` (category only)
- plus the exact failing trigger from Step 2
- chunk unload/reload

Must pass:
- Full lane unchanged
- Bottom slab lane stable
- Top slab lane stable

---

## Step 7 — Strict visual audit
Use: `slabbed-visual-alignment-audit` 

Must pass. If visuals regress: revert and reassess.

---

## Step 8 — Regression sweep (fast)
Re-test previously passing categories on slabs.
If any regression:
- revert to last tag
- reduce scope (move to targeted mixin)
- re-run tests

---

## Step 9 — Savepoint
Commit message:
- `fix: prevent <category> popping off on slab updates` 

If this is a follow-up on a category branch, keep it as the second commit max.

Tag (only if this completes the category pass):
- `slabbed-<category>-pass` (if not already tagged)

---

## Output report (required)
- Category:
- Failing trigger:
- Survival predicate located (Yarn class#method):
- Fix strategy (A/B/C):
- Files changed:
- Test results (Full / BottomSlab / TopSlab):
- Visual audit: PASS/FAIL
- Regression sweep: PASS/FAIL
- Commit hash + tag (if any):
