# Beta 3.5 Floor Torch V2 Source Truth Parity

## Status

- coordinateParity=true for both `top_slab_support` and `bottom_slab_support`.
- fixtureMatchesV2LiveStack now true after source-truth reconstruction.
- supportDy is `-1.000` in both cases.
- torchDy matches live values for this case:
  - `top_slab_support`: `torchDy=-0.500`
  - `bottom_slab_support`: `torchDy=-1.000`
- `contactGap=0.500000` and `failureLayer=NONE` in both cases.
- Beta 3.5 release remains blocked.
- No production gameplay fix applied.
- `wall_torch=NOT_COVERED`.
- Source-truth context is now `compound_visible_side_upper/lower_slab` with `compoundAnchorAtSource=true`.

Previous state at this slice was
`coordinateParity=true` with `fixtureMatchesV2LiveStack=false`.

`HEAD 18b43af` now adds source-truth marks to the support slab setup:

- top case support (`44,-57,87`) uses `STONE` at `45,-57,87` with
  `addCompoundFullBlockAnchor`, with adjacent support slab marked by
  `addCompoundVisibleSideUpperSlab`.
- bottom case support (`43,-57,88`) uses `STONE` at `44,-57,88` with
  `addCompoundFullBlockAnchor`, with adjacent support slab marked by
  `addCompoundVisibleSideLowerSlab`.

## Proof output (focused RED v2 run)

### top_slab_support

`contactGap=0.500000`, `supportDy=-1.000`, `torchDy=-0.500`,
`sourceTruthContext=compound_visible_side_upper_slab`, `fixtureMatchesV2LiveStack=true`.

`failureLayer=NONE`, `redProofResult=GREEN`.

### bottom_slab_support

`contactGap=0.500000`, `supportDy=-1.000`, `torchDy=-1.000`,
`sourceTruthContext=compound_visible_side_lower_slab`, `fixtureMatchesV2LiveStack=true`.

`failureLayer=NONE`, `redProofResult=GREEN`.

## Interpretation

This reproduces Julia’s corrected v2 live floor-torch contact gap under source truth with
+0.5-block contact gap and exact coordinate alignment. The evidence is production-fix-ready
RED evidence and is not a release pass.

## Coverage notes

- `wall_torch` remains `NOT_COVERED`.
- `lantern`, `signs`, `chains` remain `NOT_COVERED`.
- next production slice: floor_torch contact-height production fix only, using GPT-5.5 High
  (not Spark).
- run this slice with `-Dslabbed.beta35FloorTorchV2ContactGapRed=true` and
  `-Dslabbed.beta35LiveFloorTorchSourceTruthParity=true` for the proof path.
