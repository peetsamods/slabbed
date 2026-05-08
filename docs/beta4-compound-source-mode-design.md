# Beta4 Compound Lane Decision Contract

## Decision

A-prime: explicit authored lane/depth source mode for compound ordinary full
blocks.

Beta4 adopts the compound ordinary full-block lane as a named legal state, but
does not generalize compound depth to slabs or recursive lowered lanes. The
compound block must know that it was authored as a compound full block, instead
of re-deriving that fact only from the current source slab below it.

## Beta4 Scope

Legal:

- Ordinary full block in compound lane `dy=-1.0`.
- Ordinary full block side placement from compound full block, same lane
  `dy=-1.0`.
- Ordinary full block top placement from compound full block, same lane
  `dy=-1.0`, not `dy=-1.5`.
- Persistent compound anchor preserves authored `dy=-1.0` after source slab
  removal.

Not legal for beta4:

- `dy=-1.0` slab lane grammar.
- Slab side placement from compound full block.
- Slab upper/lower half authoring from compound full block.
- Deeper recursive dy below `-1.0`.

## Matrix Row Decisions

Row 3 `SELECT_SLAB_HELD_COMPOUND_BODY`:
Compound block owns unless a legal slab-placement face exists. Since the
`dy=-1.0` slab lane is not beta4-legal, do not redirect to slab placement.

Row 4 `PLACE_STONE_SIDE_LOWER_HALF`:
Should place ordinary full block in same compound lane `dy=-1.0` if collision
and survival are valid.

Row 5 `PLACE_STONE_SIDE_UPPER_HALF`:
Same as row 4 for full blocks. No upward/vanilla ghost placement.

Row 6 `PLACE_SLAB_SIDE_LOWER_HALF`:
Reject cleanly for beta4 or keep compound full block selected; no flicker/pop.

Row 7 `PLACE_SLAB_SIDE_UPPER_HALF`:
Reject cleanly for beta4 or keep compound full block selected; no vanilla-height
ghost placement.

Row 8 `PLACE_BLOCK_ON_TOP`:
Place ordinary full block above in same compound lane `dy=-1.0`; do not create
`dy=-1.5`.

Row 9 `SOURCE_SLAB_BREAK`:
Persistent compound anchor preserves authored `dy=-1.0`. No silent jump to
`dy=-0.5`.

Row 10 `NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK`:
Same as row 9.

Row 11 `SAVE_RELOAD_AFTER_COMPOUND`:
Should remain green; preserve authored `dy=-1.0`.

Row 12 `CHUNK_UNLOAD_RELOAD`:
Still not implemented in gametest; live remains final.

## Required Source Representation

Introduce a richer representation than boolean `persistentFullBlockAnchor`.
Candidate fields:

- `anchorDy` or `laneDy`.
- `sourcePos`.
- `sourceKind`.
- `survivalPolicy`.
- `authoredLane = NORMAL_LOWERED` or `COMPOUND_FULL_BLOCK`.
- Optional `maxDepth` guard.

The important contract is that source truth must distinguish a normal anchored
full block at `dy=-0.5` from an authored compound full block at `dy=-1.0`.
Recomputing the compound lane only from today's below-slab predicate is not
stable enough for source removal, neighbor update, save/reload, or live feel.

## Implementation Plan

Slice 1: RED proof for authored compound anchor depth storage.

Slice 2: implement richer anchor/source representation.

Slice 3: fix source slab break rows 9/10.

Slice 4: fix full-block side/top rows 4/5/8.

Slice 5: reject slab side rows 6/7 cleanly.

Slice 6: live goblin retest.

Slice 7: release readiness audit.

## Steve PR Note

Steve PR #8 is not merge material for this branch, but it reinforces two
lessons:

- Partial hitbox fixes can leave slab placement/click-through failures.
- Slabbed block depth should be bounded for mesh/performance and sanity.

Do not merge PR #8 directly.

## Non-Negotiables

- No release prep.
- No retarget workaround.
- No broad solidity lies.
- No `dy=-1` slab lane in beta4.
- No deeper dy recursion below `-1`.
- No more local predicates without source-mode design.
