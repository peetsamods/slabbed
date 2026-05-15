# Beta 3.5 Slab Jump Source-Truth Audit

Slice base: `0512f50` / `save/beta35-door-half-server-validation`.

Evidence folder: `tmp/beta35-slab-jump-source-truth-audit-0512f50/`.

## Why

Julia's live source truth at this base still shows neighbor slabs visually shifting
toward vanilla lane during slab place/break sequences, even after doors, trapdoors,
buttons, chains, fences, and walls are proof-good or live-good. The question for this
slice was whether the remaining slab jump is caused by:

- A. Placement authoring a normal-lane slab from a lowered visible non-slab source.
- B. Break-time source-truth removal at the broken position clearing markers that
  downstream visuals depend on.
- C. Adjacent dependent slabs losing derived source truth when their lowered carrier
  is broken, without any anchor/marker event firing.

The slice traces every anchor/marker mutation through the single chokepoints in
`SlabAnchorAttachment.addToAttachment` and `removeFromAttachment`, plus probes
neighborhood `dy` before/after place and break actions, and classifies each row.

## Proof

Focused proof flag:

`-Dslabbed.beta35SlabJumpSourceTruth=true`

Markers emitted:

- `[JULIA_BETA35_SLAB_JUMP_ANCHOR_EVENT]` — every ADD/REMOVE on ANCHOR, COMPOUND_FULL_BLOCK_ANCHOR, COMPOUND_VISIBLE_SIDE_LOWER/UPPER/DOUBLE_SLAB, COMPOUND_VISIBLE_OWNER_TOP_SLAB, LOWERED_SLAB_CARRIER.
- `[JULIA_BETA35_SLAB_JUMP_DY_SAMPLE]` — `dyBefore`/`dyAfter` plus all seven marker flags before and after the action.
- `[JULIA_BETA35_SLAB_JUMP_SOURCE_TRUTH]` — per-row classification line.
- `[JULIA_BETA35_SLAB_JUMP_SUMMARY]` — slice-level totals.

Three rows ran:

```
JULIA_BETA35_SLAB_JUMP_SOURCE_TRUTH rowPhase=PLACEMENT_AUTHOR rowId=placement_lowered_fence_source
  sourceId=minecraft:birch_fence dyHit=-1.000000 placeState=stone_slab[type=bottom] dyPlaceAfter=0.000000
  classification=PLACEMENT_AUTHORED_NORMAL_LANE_FROM_LOWERED_SOURCE

JULIA_BETA35_SLAB_JUMP_SOURCE_TRUTH rowPhase=SOURCE_BREAK rowId=break_legal_double_slab_source
  sourceType=double dyHitBefore=-0.500000 dyHitAfter=0.000000
  dyNeighborBefore=-0.500000 dyNeighborAfter=0.000000
  dyFullBefore=-0.500000 dyFullAfter=-0.500000 removeEvents=0
  removedAtBrokenPos=false removedAtAdjacent=false neighborJumped=true
  classification=NEIGHBOR_DY_RENORMALIZATION reason=dependent_dy_changed_without_marker_event

JULIA_BETA35_SLAB_JUMP_SOURCE_TRUTH rowPhase=SOURCE_BREAK rowId=break_legal_bottom_slab_source
  sourceType=bottom dyHitBefore=-0.500000 dyHitAfter=0.000000
  dyNeighborBefore=-0.500000 dyNeighborAfter=0.000000
  dyFullBefore=-0.500000 dyFullAfter=-0.500000 removeEvents=0
  removedAtBrokenPos=false removedAtAdjacent=false neighborJumped=true
  classification=NEIGHBOR_DY_RENORMALIZATION reason=dependent_dy_changed_without_marker_event

JULIA_BETA35_SLAB_JUMP_SUMMARY rows=3 sourceMarkerRemovedRows=0
  adjacentDependentLostSourceTruthRows=0 placementAuthoredNormalLaneRows=1
  neighborDyRenormalizationRows=2 expectedPlacementRows=0 noJumpRows=0
  recommendedNextFix=investigate_derived_dependent_dy_after_source_break_no_named_lane
  releaseBlocking=no releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false
```

## Result

The slab jump observed on live is NOT caused by `onStateReplaced` clearing a
marker carrier at the broken position. In both break rows `removeEvents=0` for
the broken slab itself — the broken positions never carried any of the seven
tracked markers in the first place (`anchorBefore=false`, all `compoundFull*`,
`compVisLower/Upper/Double/OwnerTop*`, and `loweredCarrierBefore=false`).

Instead the dependent slab next door visibly jumped from `dy=-0.5` to `dy=0.0`
without any marker mutation. That means its lowered lane was derived dynamically
from the now-removed slab carrier, not stored as a persistent marker on the
dependent. This is hypothesis C from the slice prompt: **derived dependent
renormalization after source-slab break, no named legal lane to hold the
dependent at `dy=-0.5` once its visible carrier is gone.**

Hypothesis A is also confirmed reproducible (row 0,
`PLACEMENT_AUTHORED_NORMAL_LANE_FROM_LOWERED_SOURCE`), matching the existing
`docs/beta35-slab-placement-lane-jump.md` classification of
`SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE`.

Hypothesis B (source marker removed at replaced pos) is **not** what is firing
in these fixtures.

## Recommended next step

`investigate_derived_dependent_dy_after_source_break_no_named_lane`

A safe production fix is not attempted in this slice. The dependent slab's
lowered state in these fixtures is derived, not stored, so persistence work
would have to either (a) add an authored marker to the dependent at the moment
its carrier is broken, or (b) introduce a named lowered lane the dependent can
fall back to. Either path is broader than this slice scope and demands its
own audit before any code change.

## Scope

- No `SlabBlock` neighbor-update override added.
- No `dy=-1` lane grammar added.
- No global solidity / sturdy-face changes.
- No release audit run. No release tag moved.
- No all-item gameplay claim.
- Tracer is gated off by default by `-Dslabbed.beta35SlabJumpSourceTruth=true`.

Live acceptance still required for any next fix.
