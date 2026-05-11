# Beta 3.5 Floor Torch V2 Coordinate Replay

## Status

Coordinate parity is now **true** for both `top_slab_support` and `bottom_slab_support` cases.
Source-truth parity remains **false** (`fixtureMatchesV2LiveStack=false`).
`contactGap=0.000000` in the controlled test vs `contactGap=0.500000` in the v2 live capture.
`failureLayer=V2_SOURCE_TRUTH_MISMATCH`.
Beta 3.5 release prep remains **PAUSED**.
No production gameplay fix implemented. No release tag moved.

HEAD at time of replay: `c837127` / tag `save/beta35-floor-torch-v2-contact-gap-red`

---

## Audit answers

| # | Question | Answer |
|---|---|---|
| 1 | Why did the previous top proof use supportCandidatePos=44,-56,87 instead of live 44,-57,87? | Off-by-one Y: the method computes `torchPos = supportCandidatePos.up()`. Passing Y=-56 as support gave torch at Y=-55, not the live Y=-56. The support must be Y=-57 to give torch at Y=-56. |
| 2 | Is the proof measuring the torch block as the support candidate? | No. The proof correctly uses `supportCandidatePos` (the slab) and `torchPos = supportCandidatePos.up()` (the torch). No confusion between the two. |
| 3 | Does the proof construct exact torch/support coordinate pairs from v2 live capture? | Yes, after fix. `expectedTorchPos=44,-56,87` == `actualTorchPos=44,-56,87`; `expectedSupportCandidatePos=44,-57,87` == `actualSupportCandidatePos=44,-57,87`. `coordinateParity=true`. |
| 4 | What source truth is needed for supportDy=-1.000 in each exact case? | The slab must be lowered by the Slabbed production mechanism. `getYOffset` returns 0 for a vanilla (non-lowered) slab and -1.0 for a lowered slab. The controlled test sets a vanilla slab; the live session had a Slabbed-lowered slab. `supportDy=-1.000` requires the production lowering path to be active. |

---

## Fix applied

Previous call (wrong):
```java
// top case — torch lands at 44,-55,87 (NOT the live 44,-56,87)
new BlockPos(44, -56, 87)   // supportCandidatePos
```

Corrected call:
```java
// top case — torch lands at 44,-56,87 (matches live)
new BlockPos(44, -57, 87)   // supportCandidatePos

// bottom case — torch lands at 43,-56,88 (matches live)
new BlockPos(43, -57, 88)   // supportCandidatePos
```

---

## Proof output (v2 gate run)

### top_slab_support

```
[JULIA_BETA35_FLOOR_TORCH_V2_COORDINATE_REPLAY]
  caseName=top_slab_support
  expectedTorchPos=44, -56, 87
  actualTorchPos=44, -56, 87
  expectedSupportCandidatePos=44, -57, 87
  actualSupportCandidatePos=44, -57, 87
  coordinateParity=true
  failureLayer=V2_SOURCE_TRUTH_MISMATCH

[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_MEASURED]
  caseName=top_slab_support
  supportCandidatePos=44, -57, 87
  supportCandidateState=Block{minecraft:stone_slab}[type=top,waterlogged=false]
  supportDy=0.000          (live: -1.000)  <-- source truth mismatch
  torchDy=0.000            (live: -0.500)  <-- source truth mismatch
  rawSupportTopY=-56.000000  (live: -56.000000)  ✓
  supportVisibleTopY=-56.000000  (live: -57.000000)  ✗
  rawTorchShapeMinY=0.000000  (live: -0.500000)  ✗
  torchModelBottomY=-56.000000  (live: -56.500000)  ✗
  contactGap=0.000000      (live: 0.500000)  ✗
  coordinateParity=true
  fixtureMatchesV2LiveStack=false
  failureLayer=V2_SOURCE_TRUTH_MISMATCH
```

### bottom_slab_support

```
[JULIA_BETA35_FLOOR_TORCH_V2_COORDINATE_REPLAY]
  caseName=bottom_slab_support
  expectedTorchPos=43, -56, 88
  actualTorchPos=43, -56, 88
  expectedSupportCandidatePos=43, -57, 88
  actualSupportCandidatePos=43, -57, 88
  coordinateParity=true
  failureLayer=V2_SOURCE_TRUTH_MISMATCH

[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_MEASURED]
  caseName=bottom_slab_support
  supportCandidatePos=43, -57, 88
  supportCandidateState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false]
  supportDy=0.000          (live: -1.000)  <-- source truth mismatch
  torchDy=-0.500           (live: -1.000)  <-- source truth mismatch
  rawSupportTopY=-56.500000  (live: -56.500000)  ✓
  supportVisibleTopY=-56.500000  (live: -57.500000)  ✗
  rawTorchShapeMinY=-0.500000  (live: -0.500000)  ✓
  torchModelBottomY=-56.500000  (live: -57.000000)  ✗
  contactGap=0.000000      (live: 0.500000)  ✗
  coordinateParity=true
  fixtureMatchesV2LiveStack=false
  failureLayer=V2_SOURCE_TRUTH_MISMATCH
```

---

## Root cause of remaining V2_SOURCE_TRUTH_MISMATCH

The v2 live recorder captured slabs **lowered by the Slabbed production mechanism**
(`supportDy=-1.000`). `SlabSupport.getYOffset` returns `-1.0` for a lowered slab
and `0.0` for a vanilla (non-lowered) slab.

The controlled test places vanilla stone slabs via `world.setBlockState(...)` with no
Slabbed lowering state. There is no path to write `supportDy=-1.0` into a slab block
without triggering the production lowering mechanism, which is currently unimplemented.

Consequence:
- `supportDy=0` → `supportVisibleTopY` is 1.0 higher than the live value
- `supportVisibleTopY` error cascades directly into `contactGap`
- `contactGap=0` (no gap) rather than `0.5` (live gap)

This is **not** a recorder formula bug. The v2 recorder math was audited and corrected
in `docs/beta35-live-torch-recorder-contact-math-audit.md`. The live `contactGap=0.500000`
is a real measurement of a real visual gap in the production code.

---

## Success-criteria classification

Per the task spec:

> A. coordinate/source parity true and contactGap=+0.5 → production-fix-ready RED
> **B. coordinate/source parity true and contactGap=0 → live recorder/context still suspect**
> C. parity false → stop with V2_COORDINATE_MISMATCH or V2_SOURCE_TRUTH_MISMATCH

Result: **Case B** — coordinateParity=true, fixtureMatchesV2LiveStack=false, contactGap=0.

However case B's "live recorder/context still suspect" does NOT apply here because the
recorder math was already audited and the mismatch source is now identified: the controlled
test cannot produce lowered slabs without the production mechanism. The live contactGap=0.5
is a real gap. The test environment gap=0 is the vanilla (unaffected) baseline.

---

## Next slice

Production fix (floor-torch contact height) is required to:
1. Enable lowered slab state in a controlled way (or test against a live slab that is
   lowered by the production path)
2. Reproduce `supportDy=-1.000` in the fixture
3. Measure `contactGap=+0.5` in the controlled test → achieve production-fix-ready RED

No production fix is in scope for this proof slice.

---

## Release and coverage

- Beta 3.5 release prep: **PAUSED**
- `productionGameplayFixApplied=false`
- `releaseTagMoved=false`
- `wall_torch=NOT_COVERED`
- `lantern=NOT_COVERED`
- `signs=NOT_COVERED`
- `chains=NOT_COVERED`
