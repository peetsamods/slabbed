---
name: slabbed-broad-support-hook-rollout
description: Safely expand slab support from one working object to more categories without regressions.
---

# Slabbed — Broad Support Hook Rollout (Safe Expansion)

## Goal
Expand from a single working object (e.g., torch) to a broader set while staying safe:
- keep strict visuals
- avoid regressions
- expand one category at a time
- tag each milestone

## Non-goals
- “All items” in one leap
- Feature creep (stairs/fences/etc.) unless explicitly planned
- Performance tuning beyond basic sanity

## Preconditions
- `slabbed-first-placement-pass` and ideally `slabbed-first-survival-pass` exist
- Repro world lanes exist
- `./gradlew build` passes
- clean git status


## Rollout order (recommended)
Expand in this order (lowest risk to higher):
1) **Carpet**
2) **Pressure plates**
3) **Redstone dust**
4) **Repeaters / comparators**
5) **Rails**
6) **Plants** (only after solid support semantics are proven)

If you must reorder, justify it in the report.


## Method: expand by categories
For each category:
1) Identify the survival predicate(s) (usually `canPlaceAt` / neighbor update)
2) Identify placement predicate(s) (`getPlacementState` or item placement logic)
3) Prefer shared hook points over per-block when they’re clearly safe.
4) If shared points are risky, do targeted per-block mixins for that category only.

### Strict commit slicing
- ONE category per branch
- ONE to TWO commits maximum:
  - `feat:` allow placement/support on slabs
  - `fix:` prevent pop-off on updates
- Tag after passing the category:
  - `slabbed-<category>-pass` (e.g., `slabbed-carpet-pass`)


## Required tests per category
Use the test matrix skill, but only for:
- baseline full blocks lane
- bottom slab lane
- top slab lane

Run update triggers:
- neighbor update
- chunk unload/reload

Additionally:
- Redstone: power cycle it (lever adjacent full block ok)
- Rails: place straight, corner, slope attempt (slope may be invalid on slabs—record expected behavior)
- Plates: confirm entity activation still works (stand on it)


## Regression gate (mandatory)
After each category passes:
- Re-run all previously passing categories quickly on slabs
- If any regression occurs:
  - revert to last tag
  - reduce scope (narrow injection or move to targeted mixin)


## Output report format (required)
For each category:
- Category:
- Strategy (shared hook vs targeted mixins):
- Yarn targets touched:
- Files changed:
- Test summary (✅/❌ on bottom/top slabs):
- Commit hash + tag:


## Stop conditions
- If a shared hook changes behavior for unrelated blocks: revert + switch to targeted mixin
- If strict visuals cannot be met: stop, document mismatch, and do not “ship ugly”
- If the rollout requires >3 new mixins for one category: pause and reassess architecture
