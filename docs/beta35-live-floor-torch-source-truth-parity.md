# Beta 3.5 Floor Torch Source Truth Parity Proof

## Status

This proof-only slice classifies whether the replay fixture can reproduce Julia’s live
floor-torch dy stack for the same in-scope pattern:

- `supportCandidateState=Block{minecraft:stone_slab}[type=bottom]`
- `supportDy=-0.500000`
- `torchDy=-1.000000`

Beta 3.5 release prep remains **PAUSED**.
`wall_torch`, `lantern`, `signs`, and `chains` are still `NOT_COVERED`.

## Gate

Enable with:

```bash
-Dslabbed.beta35LiveFloorTorchSourceTruthParity=true
```

## Markers

- `[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_GREEN]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_FAIL]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]`

## Required logged fields

- `torchPos`
- `torchState`
- `supportCandidatePos`
- `supportCandidateState`
- `supportDy`
- `torchDy`
- `supportVisibleTopY`
- `torchModelBottomY`
- `contactGap`
- `outlineMinY`
- `outlineMaxY`
- `raycastMinY`
- `raycastMaxY`
- `targetFace`
- `targetType`
- `targetPos`
- `targetHitX/targetHitY/targetHitZ`
- `placementResult`
- `placementAccepted`
- `fixtureMatchesLiveDyStack=true/false`
- `failureLayer`

## Failure-layer contract

- `SOURCE_TRUTH_MISMATCH`
- `LIVE_FLOOR_TORCH_WRONG_SUPPORT_OWNER`
- `LIVE_FLOOR_TORCH_WRONG_DY`
- `LIVE_FLOOR_TORCH_CONTACT_GAP`
- `NONE`

`fixtureMatchesLiveDyStack` is computed as `supportDy == -0.500` and `torchDy == -1.000`.
If this is `false`, the proof should report `SOURCE_TRUTH_MISMATCH` and stop.
