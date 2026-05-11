# Beta 3.5 Floor Torch Live Contact Gap RED Proof

## Status

Wall_torch remains a separate `NOT_COVERED` category and is not part of the floor-torch blocker in this slice.

Julia’s live recorder confirmed `CONTACT_GAP` for in-scope floor torches after floor-placement attempts where both
`torchState=Block{minecraft:torch}` and
`supportCandidateState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false]`.

Current operating result is **RED proof-only** and `Beta 3.5` release remains **PAUSED**.

The replay gap proof in this slice did not reproduce the live dy stack:
`torchDy=-1.000`, `supportDy=-0.500`, `contactGap=-1.500000` until the source-truth parity gate below proves the fixture equivalence.

Source-truth parity verification now runs with:

```bash
-Dslabbed.beta35LiveFloorTorchSourceTruthParity=true
```

Its markers are:

- `[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_GREEN]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_FAIL]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]`

If source truth still cannot be reproduced, the proof should classify `failureLayer=SOURCE_TRUTH_MISMATCH` with `fixtureMatchesLiveDyStack=false` and report exact missing anchor/source truth.

## Gate

Enable the focused live contact-gap reproduction with:

```bash
-Dslabbed.beta35LiveFloorTorchContactGapRed=true
```

Markers:

- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]`

## Live contact-gap evidence snapshot (manual)

Two in-scope floor-torch captures in `contactGap` class:

- `torchPos=51,-55,89`
  `supportCandidatePos=51,-56,89`
  `supportDy=-0.500000`
  `torchDy=-1.000000`
  `supportVisibleTopY=-55.500000`
  `torchModelBottomY=-57.000000`
  `contactGap=-1.500000`
- `torchPos=45,-56,93`
  `supportCandidatePos=45,-57,93`
  `supportDy=-0.500000`
  `torchDy=-1.000000`
  `supportVisibleTopY=-56.500000`
  `torchModelBottomY=-58.000000`
  `contactGap=-1.500000`

`wall_torch` was also captured at `51,-57,87` with `contactGap=0.187500`, which is classified as
`NOT_COVERED` and must not be fixed in this slice.

## Failure layer contract

This proof classifies into:

- `LIVE_FLOOR_TORCH_WRONG_DY`
- `LIVE_FLOOR_TORCH_WRONG_SUPPORT_OWNER`
- `LIVE_FLOOR_TORCH_CONTACT_GAP`
- `NONE`

Current expected status from this slice:

- `failureLayer=LIVE_FLOOR_TORCH_CONTACT_GAP`
- `redProofResult=RED`
- `beta35ReleaseStatus=PAUSED_LIVE_TORCH_CONTACT_GAP_PROOF`
- `releasePrep=PAUSED`
- `productionGameplayFixApplied=false`

No production gameplay fix is implemented in this slice.
