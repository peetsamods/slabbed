---
name: slabbed-add-category
description: Add slab-top support for one new category (placement + survival) with strict visuals, tests, and tagged savepoints.
---

# Slabbed — Workflow: Add Category

## Goal
Implement slab-top support for exactly ONE category (e.g., carpet, pressure plates, dust, rails) including:
- placement works on slab tops
- survival checks do not pop off after updates
- strict visuals pass
- regression sweep passes
- commit + tag created

## Inputs (must be decided before starting)
- Category name: `<category>` 
- Target blocks/items list for the category (explicit)
- Expected behavior notes (especially for rails/plants)

## Hard constraints
- One category per branch
- One to two commits max
- `./gradlew build` must pass before any commit
- Strict visuals required (visual audit must pass)

---

## Step 0 — Safety checkpoint
1) `git status` must be clean. If not clean: STOP.
2) Confirm current HEAD hash.
3) Run `./gradlew build`. If fail: STOP and fix before proceeding.

---

## Step 1 — Create branch
Create branch:
- `feat/<category>-on-slabs` 

Example:
- `feat/carpet-on-slabs` 

---

## Step 2 — Confirm targets and hooks (read-only)
Use the skill: `slabbed-mixin-target-discovery` 

Deliverable for this workflow step:
- Identify the primary Yarn method(s) controlling placement for this category
- Identify the primary Yarn method(s) controlling survival rechecks for this category
- Decide if this category will use:
  - shared hook point, OR
  - targeted per-block mixins

For ceiling-attachment blocks (chains, hanging signs, etc.), also check whether the block queries `isSideSolid(Direction.DOWN)` on the block above. If so, the existing `SlabSupportStateMixin` may already handle placement — verify before writing new mixins. See skill: `slabbed-ceiling-support`.

If targets are unclear: STOP and report.

---

## Step 3 — Implement placement support (minimal change)
1) Add the smallest placement-time change necessary for slab-top support.
2) All slab semantics must route through `SlabSupport` (single source of truth).
3) Keep the injection narrow:
   - avoid broad redirects unless proven safe
4) Build: `./gradlew build` must pass.

---

## Step 4 — Implement survival support (minimal change)
1) Add the smallest survival-time change necessary to prevent pop-offs.
2) Validate it still fails correctly when truly unsupported (air, liquids, etc.).
3) Build: `./gradlew build` must pass.

---

## Step 5 — Test matrix for this category
Use the skill: `slabbed-test-matrix-and-repro-world` 

Requirements:
- Full blocks lane: PASS
- Bottom slab lane: PASS
- Top slab lane: PASS
- Neighbor update trigger: PASS
- Chunk unload/reload: PASS

If any FAIL:
- Do not continue
- Switch to the debug workflow (future): pop-off diagnosis

---

## Step 6 — Strict visual audit
Use the skill: `slabbed-visual-alignment-audit` 

Must pass:
- placement height matches slab top
- outline/selection box aligns
- interaction feel is normal
- survives updates without visual shifting

If any FAIL: STOP (no tag).

---

## Step 7 — Regression sweep (quick)
Re-test previously-passing categories on slabs (fast pass).
If any regression:
- revert to last tag
- reduce scope (switch to targeted mixins if needed)
- re-run tests

---

## Step 8 — Savepoint commit(s) + tag
If the category passes:

Commit 1 (required):
- Message: `feat: support <category> on slab tops` 

If a second commit was needed for stability:
- Message: `fix: prevent <category> popping off on slab updates` 

Tag after passing:
- `slabbed-<category>-pass` 

Examples:
- `slabbed-carpet-pass` 
- `slabbed-redstone-dust-pass` 

---

## Output report (required)
- Category:
- Strategy: shared hook vs targeted mixins
- Yarn targets touched (class#method):
- Files changed:
- Test results (Full / BottomSlab / TopSlab):
- Visual audit: PASS/FAIL
- Regression sweep: PASS/FAIL
- Commit hash(es) + tag:
