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
**Status: captured at `84bbb81`** (`save/beta4-authored-compound-anchor-depth-red-proof`).
See "Authored compound anchor depth RED proof" below.

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

## Authored compound anchor depth RED proof

Marker: `[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_RED]`. Future GREEN marker:
`[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_GREEN]`.

File:
`src/gametest/java/com/slabbed/test/SlabbedLabBeta4AuthoredCompoundAnchorDepthClientGameTest.java`.

Property: `-Dslabbed.beta4AuthoredCompoundAnchorDepthRedOnly=true`. The
proof is a no-op when the property is not set; default
`runClientGameTest` is unaffected.

The proof seeds the matrix row 9/10 fixture (vanilla bottom slab
`BASE_FULL_SUPPORT`, anchored ordinary `BASE_FULL`,
`persistentLoweredBottomSlabCarrier` `LOWERED_BOTTOM_SLAB`, authored
compound ordinary `COMPOUND` at `dy=-1.0`). It captures pre/post snapshots
around an explicit source-slab removal plus
`world.updateNeighborsAlways` pulse. Captured at `84bbb81`:

- `phase=preSourceRemoval`:
  - `placedDy=-1.000`
  - `placedPersistentFullBlockAnchor=true`
  - `sourceDy=-0.500`
  - `sourcePersistentLoweredSlabCarrier=true`
  - `expectedAuthoredDy=-1.000`
- `phase=postSourceRemoval`:
  - `placedPersistentFullBlockAnchor=true`
  - `actualDy=-0.500`
  - `expectedAuthoredDy=-1.000`
  - `authoredDepthMissing=true`
  - `currentAnchorCanExposeDepth=false`
  - `classification=RED`

Structural facts confirmed by the proof:

- `SlabAnchorAttachment.ANCHOR_TYPE` is
  `AttachmentType<LongOpenHashSet>` — a packed-position set with no
  per-position payload.
- A reflective probe of `SlabAnchorAttachment` for any public method
  whose name contains `dy`, `depth`, or `lane` and which returns
  `double`/`Double` returns `none`.
- Therefore `currentAnchorCanExposeDepth=false` is a compile-time/structural
  invariant of the current attachment surface, not a runtime accident.

This is the "authored compound lane depth cannot be encoded by a boolean"
contract from the audit, made executable. The proof intentionally throws
an `AssertionError` carrying the RED marker so the opt-in run exits
non-zero and cannot be silently ignored. The default
`runClientGameTest` batch is verified to remain `BUILD SUCCESSFUL`
(no gameplay behavior change).

Evidence harvest at `84bbb81`:
`tmp/beta4-authored-compound-anchor-depth-red-84bbb81/`.

## Recommended next implementation slice

Add a sidecar attachment, **not** a replacement of `ANCHOR_TYPE`:

- New attachment, e.g. `COMPOUND_FULL_BLOCK_ANCHOR_TYPE`, mapping packed
  `BlockPos` to a small payload that records the authored compound lane
  depth (initially: `authoredDy=-1.0`, optional `sourceKind`,
  `survivalPolicy`).
- Authored at the same call site that today calls
  `SlabAnchorAttachment.addAnchor` for compound ordinary full blocks
  (`SlabSupport.getYOffsetInner` compound branch + `Block.onPlaced`
  recorder, per `docs/beta4-compound-lowered-fullblock-height.md`).
- Read by `SlabSupport.getYOffsetInner` *before* falling back to the
  per-column re-derivation, so the compound lane survives source slab
  removal, neighbor update, save/reload, and chunk reload.
- Cleared together with `removeAnchor` on legitimate break / replace.

Why a sidecar rather than replacing `ANCHOR_TYPE`:

- Preserves the existing `LongOpenHashSet` boolean anchor semantics for
  ordinary `dy=-0.5` anchors (no behavior change for non-compound rows).
- Keeps the change additive and reversible.
- Lets the matrix RED rows 9/10 close one at a time on top of an
  unchanged boolean anchor, instead of forcing a cross-cutting rewrite.

Do not implement the sidecar in this slice. Release remains blocked on
the slice 2 sidecar implementation, slice 3 source-slab-break fix,
slice 4–5 placement rows, and slice 6 Julia live retest.
