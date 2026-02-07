---
name: slabbed-neighbor-update-map
description: Map and validate the neighbor update and revalidation paths that cause slab-supported objects to pop off, per category.
---

# Slabbed — Neighbor Update Map

## Goal
Maintain a living map of the update paths that can invalidate placement and cause pop-offs.
Use this to prevent “it places but later breaks” regressions.

## When to run
- When a category places correctly but breaks later
- After changing any survival predicate hook
- Before tagging `slabbed-<category>-pass` for update-sensitive categories (redstone, rails, plants)

## Update trigger catalog
For each category under test, explicitly test these triggers and record PASS/FAIL:

### 1) Below block change
- Break the supporting slab
- Replace supporting slab with a different slab type (bottom ↔ top)
- Replace slab with full block and back

### 2) Adjacent block change (horizontal)
- Place a full block next to the object
- Break that adjacent block
- Repeat on all four sides if shape/connection matters

### 3) Above block change
- Place a block above (if possible)
- Remove it
This matters for some plants/stacking logic.

### 4) Scheduled tick / random tick
- If the block uses ticking logic, wait and observe
- For plants, use bone meal if relevant and observe stability

### 5) Fluid update adjacency
- Place water adjacent
- Remove water
- If waterlogging is relevant, test that explicitly

### 6) Redstone neighbor propagation (if relevant)
- Toggle a nearby power source
- Observe whether revalidation happens and whether the object survives

### 7) Piston-induced updates (optional but useful)
- Push/pull an adjacent full block to create neighbor updates
- Do not require the supported object itself to be moved

### 8) Chunk unload/reload
- Fly far enough to unload chunk
- Return and confirm it is still present

## How to build the map
For each category, fill in:

- Category:
- Placement predicate location (Yarn):
- Survival predicate location (Yarn):
- Triggers that caused revalidation:
- Triggers that caused failure:
- Best hook point to intercept revalidation:
- Notes on strict visuals impact:

## Output format (required)
- Category:
- Full lane: PASS/FAIL
- Bottom slab lane: PASS/FAIL
- Top slab lane: PASS/FAIL
- Failures:
  - Trigger:
  - What broke:
  - Repro steps:
- Suspected hook target(s) (Yarn class and method):
- Next change proposed (one sentence):

## Stop conditions
- If failures differ by trigger type, do not “patch randomly”
- First identify the exact survival recheck path and only then change hooks
- If a shared hook fixes one category but breaks another, revert and go targeted
