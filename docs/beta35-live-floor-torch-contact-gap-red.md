# Beta 3.5 Floor Torch Live Contact Gap RED Proof

## Status

Floor-torch contact-gap proof is now updated for the corrected V2 live capture math.
`wall_torch`, `lantern`, `signs`, and `chains` are still **NOT_COVERED** in this slice.
Beta 3.5 release prep remains **PAUSED**.

The V2 source truth from live capture is:
- `supportDy=-1.000` (optionally on `stone_slab[type=top]` or `stone_slab[type=bottom]`)
- `torchDy=-0.500` for top-slab support cases or `torchDy=-1.000` for bottom-slab support cases
- `contactGap=0.500000`

No production gameplay fix is implemented in this proof slice.

## Focused v2 proof gate

Run with:

```bash
-Dslabbed.beta35FloorTorchV2ContactGapRed=true
```

Required markers:
- `[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_RED]`
- `[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_SUMMARY]`

Required fields in V2 markers:
- `supportCandidateState`
- `supportDy`
- `torchDy`
- `rawSupportTopY`
- `supportVisibleTopY`
- `rawTorchShapeMinY`
- `torchModelBottomY`
- `contactGap`
- `fixtureMatchesV2LiveStack`

Allowed `failureLayer` values in this slice:
- `V2_LIVE_FLOOR_TORCH_CONTACT_GAP`
- `V2_SOURCE_TRUTH_MISMATCH`
- `NONE`

If source truth does not reproduce after two scenarios (top/bottom support cases), classify as
`V2_SOURCE_TRUTH_MISMATCH`.

## Evidence summary (current)

- Live capture context: `NO_TORCH_TARGET=1077`, `CONTACT_GAP=52`, `TORCH_FOUND_NEAR_TARGET=1`.
- No wall-torch captures in this v2 extract; this is floor-torch only.
- Representative capture examples:
  - top support: support candidate top, `torchDy=-0.500000`, `supportDy=-1.000000`, `contactGap=0.500000`
  - bottom support: support candidate bottom, `torchDy=-1.000000`, `supportDy=-1.000000`, `contactGap=0.500000`

## Next slice after RED proof

`production gameplay fix for floor_torch contact height only` (not in this proof slice).
