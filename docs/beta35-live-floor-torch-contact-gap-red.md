# Beta 3.5 Floor Torch Live Contact Gap RED Proof

## Status

Floor-torch contact-gap proof is now updated for the corrected V2 live capture math.
`wall_torch`, `lantern`, `signs`, and `chains` are still **NOT_COVERED** in this slice.
Beta 3.5 release prep remains **PAUSED**.

The V2 source truth from live capture is:
- `supportDy=-1.000` (on `stone_slab[type=top]` or `stone_slab[type=bottom]`)
- `torchDy=-0.500` for top-slab support cases or `torchDy=-1.000` for bottom-slab support cases
- `contactGap=0.500000`

No production gameplay fix is implemented in this proof slice.

## Coordinate replay result (HEAD c837127)

The previous v2 proof had an off-by-one Y: it passed Y=-56 as the support position,
placing the torch at Y=-55 instead of the live Y=-56. Fixed by passing Y=-57 as support
so torch lands at Y=-56.

After fix:

| Field | top_slab_support | bottom_slab_support |
|---|---|---|
| `coordinateParity` | `true` | `true` |
| `fixtureMatchesV2LiveStack` | `false` | `false` |
| `supportDy` (controlled) | `0.000` | `0.000` |
| `supportDy` (live) | `-1.000` | `-1.000` |
| `contactGap` (controlled) | `0.000000` | `0.000000` |
| `contactGap` (live) | `0.500000` | `0.500000` |
| `failureLayer` | `V2_SOURCE_TRUTH_MISMATCH` | `V2_SOURCE_TRUTH_MISMATCH` |

Mismatch root cause: controlled test places vanilla slabs (`supportDy=0`). Live session had
Slabbed-lowered slabs (`supportDy=-1.0`). Reproducing `supportDy=-1.0` requires the
production lowering mechanism. See `docs/beta35-floor-torch-v2-coordinate-replay.md`.

## Focused v2 proof gate

Run with:

```bash
-Dslabbed.beta35FloorTorchV2ContactGapRed=true
```

Required markers:
- `[JULIA_BETA35_FLOOR_TORCH_V2_COORDINATE_REPLAY]`
- `[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_RED]`
- `[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_SUMMARY]`

Required fields in V2 markers:
- `caseName`
- `expectedTorchPos` / `actualTorchPos`
- `expectedSupportCandidatePos` / `actualSupportCandidatePos`
- `coordinateParity`
- `supportCandidateState`
- `supportDy`
- `torchDy`
- `rawSupportTopY`
- `supportVisibleTopY`
- `rawTorchShapeMinY`
- `torchModelBottomY`
- `contactGap`
- `fixtureMatchesV2LiveStack`

Allowed `failureLayer` values:
- `V2_COORDINATE_MISMATCH` — fixture positions do not match v2 live coordinates
- `V2_SOURCE_TRUTH_MISMATCH` — coordinates match but dy stack does not reproduce
- `V2_LIVE_FLOOR_TORCH_CONTACT_GAP` — dy stack matches but contactGap ≠ 0.5
- `NONE` — full parity confirmed

## Evidence summary (live capture)

- Live capture context: `NO_TORCH_TARGET=1077`, `CONTACT_GAP=52`, `TORCH_FOUND_NEAR_TARGET=1`.
- No wall-torch captures in this v2 extract; this is floor-torch only.
- Representative capture examples:
  - top support: `stone_slab[type=top]`, `torchDy=-0.500000`, `supportDy=-1.000000`, `contactGap=0.500000`
  - bottom support: `stone_slab[type=bottom]`, `torchDy=-1.000000`, `supportDy=-1.000000`, `contactGap=0.500000`

## Next slice after RED proof

`production gameplay fix for floor_torch contact height only` — requires enabling the lowered
slab state in the controlled test fixture (via the production lowering path), then measuring
`contactGap=+0.5` to confirm production-fix-ready RED.
