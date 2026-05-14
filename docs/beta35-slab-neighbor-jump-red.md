# Beta 3.5 Slab Neighbor Jump Proof

Slice base: `22ec3f2` / `save/beta35-visible-object-owner-stability`.

Evidence folder: `tmp/beta35-trapdoor-server-slab-jump-fix-22ec3f2/`.

## Why

Julia's 10:47 live source truth still reports slab jump or misplacement around neighbor updates. This slice treats that as unproven until a focused automated tracer shows a wrong state/dy transition.

Failure layer entering the slice:

`SLAB_NEIGHBOR_UPDATE_JUMP_UNPROVEN`

## Tracer

Focused proof flag:

`-Dslabbed.beta35SlabNeighborJumpRed=true -Dslabbed.beta35SlabHeightHitAcceptance=true`

Required markers:

- `JULIA_BETA35_SLAB_NEIGHBOR_JUMP_RED`
- `JULIA_BETA35_SLAB_NEIGHBOR_JUMP_GREEN`
- `JULIA_BETA35_SLAB_NEIGHBOR_JUMP_SUMMARY`

The tracer logs state and dy for:

- `hitPos`
- `placePos`
- `placeAbove`
- `placeBelow`
- `visibleObject`

It records the lane before side placement, after side placement, after merge placement, and after neighbor update, including expected lane, actual lane, visual jump detection, classification, and failure layer.

## Current Automated Classification

The proof currently classifies the automated fixture as:

`EXPECTED_SLAB_PLACEMENT failureLayer=NONE`

That means the local fixture logs the sequence without proving a wrong neighbor-update dy/state transition. No neighbor-update production patch is made in this slice.

If Julia live acceptance still sees a jump after the trapdoor server-validation fix, the next smallest slice should capture the exact live lane and compare the automated `hitPos/placePos/placeAbove/placeBelow/visibleObject` rows against that capture.

## Scope

No tuning, neighbor survival rewrite, global collision lowering, sturdy-face lie, or all-item gameplay claim is included here. Release audit remains paused.
