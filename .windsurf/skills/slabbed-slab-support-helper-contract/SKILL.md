---
name: slabbed-slab-support-helper-contract
description: Define the single shared helper API all slab-support mixins must use.
---

# Slabbed — Slab Support Helper Contract (Single Source of Truth)

## Goal
Define a single, stable helper API for determining whether a block state at a position
should count as “ground support” for the **top face**, including slabs.

All mixins must call this helper; no duplicated slab logic inside mixins.

## Non-goals
- No per-block hacks inside mixins
- No broad physics overhaul beyond “support on top face”
- No config (hard-coded rules)

## Core rule
Treat the **top face** of:
- bottom slabs (top surface at y+0.5)
- top slabs (top surface at y+1.0)
- double slabs (y+1.0)
as valid “ground support” where vanilla expects solid top support.

Strict visuals expected: placements must align to the slab top surface when possible.


## Required helper location
Create helper class (when implementing):
- `src/main/java/com/slabbed/init/SlabSupport.java` 

No other class may implement slab-support rules.


## Required public API
When implementing, expose only these methods (no extras unless required):

- `static boolean isSupportedOnTop(WorldView world, BlockPos pos, BlockState belowState)` 
  - Meaning: “does the block at pos.down() support something resting on its TOP face?”

- `static double topSurfaceYOffset(BlockState belowState)` 
  - Returns: surface height in block-space:
    - full block / double slab / top slab => `1.0` 
    - bottom slab => `0.5` 
    - unsupported => `Double.NaN` 

- `static boolean isSlabLike(BlockState state)` 
  - True for `SlabBlock` and any other block types we explicitly add later


## Behavior rules (must match vanilla expectations)
`isSupportedOnTop(...)` should return true if:
1) Vanilla already considers it valid top support (full blocks, etc.), OR
2) The below block is a slab (or slab-like) whose **top surface** exists and is solid enough
   for the category being tested.

Do NOT automatically treat:
- stairs
- fences
- walls
- trapdoors
- glass panes
as support unless a later skill adds them explicitly.

This mod is “Slabbed”, not “All partial blocks”.


## Category flags (for later expansion)
If needed later, helper may accept a “support category” enum, but for now:
- implement the generic “top support” notion first
- per-block strictness comes later via additional wrapper methods if unavoidable


## Mandatory instrumentation (debug flags)
When implementing, add optional debug logging controlled by:
- `-Dslabbed.debugSupport=true` 

Logs must be:
- one-line
- prefix `[SLABBED_SUPPORT]` 
- include: pos, belowState, computed yOffset, result

No logs by default.


## Pass criteria
- All mixins reference `SlabSupport` instead of duplicating slab checks
- The helper clearly distinguishes bottom vs top slabs
- The helper does not overreach into other partial blocks


## Stop conditions
If more than 2 mixins need slightly different “support” semantics:
- do NOT fork logic inside mixins
- extend helper API in a single place and update all call sites
