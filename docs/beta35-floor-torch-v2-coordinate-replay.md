# Beta 3.5 Floor Torch V2 Coordinate Replay

## Status

Coordinate parity is **true** and source-truth parity is **true** for both `top_slab_support`
and `bottom_slab_support` cases.
`contactGap=0.500000` in both cases — matches the v2 live capture.
`failureLayer=NONE`. `redProofResult=GREEN`.
Beta 3.5 release prep remains **PAUSED**.
No production gameplay fix implemented. No release tag moved.

This proof slice at `save/beta35-floor-torch-v2-coordinate-replay` (`18b43af`) fixed
`coordinateParity` but still had source-truth parity mismatch before the next source-truth
proof slice.

HEAD at time of coordinate replay: `c837127` / tag `save/beta35-floor-torch-v2-contact-gap-red`
HEAD at time of source truth parity: `18b43af` / tag `save/beta35-floor-torch-v2-coordinate-replay`

---

## Audit answers

| # | Question | Answer |
|---|---|---|
| 1 | Why did the previous top proof use supportCandidatePos=44,-56,87 instead of live 44,-57,87? | Off-by-one Y: the method computes `torchPos = supportCandidatePos.up()`. Passing Y=-56 as support gave torch at Y=-55, not the live Y=-56. The support must be Y=-57 to give torch at Y=-56. |
| 2 | Is the proof measuring the torch block as the support candidate? | No. The proof correctly uses `supportCandidatePos` (the slab) and `torchPos = supportCandidatePos.up()` (the torch). No confusion between the two. |
| 3 | Does the proof construct exact torch/support coordinate pairs from v2 live capture? | Yes, after fix. `expectedTorchPos=44,-56,87` == `actualTorchPos=44,-56,87`; `expectedSupportCandidatePos=44,-57,87` == `actualSupportCandidatePos=44,-57,87`. `coordinateParity=true`. |
| 4 | What source truth is needed for supportDy=-1.000 in each exact case? | The slab must carry a compound visible slab lane mark. `addCompoundVisibleSideUpperSlab` / `addCompoundVisibleSideLowerSlab` write this mark. The compound source must be a compound full block anchor at the same Y level adjacent to the support slab. Building the full 4-level tower (bare-bottom-slab → anchored-stone → carrier-slab → compound-anchor) provides `isLoweredCompoundSourceSlab=true` at the carrier position, enabling `addCompoundFullBlockAnchor`, then the compound visible mark on the support slab. `getYOffset` returns -1.0 for compound visible slab marks. |

---

## Fix applied (coordinate parity, save/beta35-floor-torch-v2-contact-gap-red → save/beta35-floor-torch-v2-coordinate-replay)

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

## Source truth parity fix (save/beta35-floor-torch-v2-coordinate-replay → save/beta35-floor-torch-v2-source-truth-parity)

The coordinate replay left `supportDy=0.000` (vanilla slab) while the live capture had
`supportDy=-1.000` (compound visible slab lane). Fix: build a 4-level compound tower
adjacent to the support slab at the same Y level, write the compound visible slab mark
on the support slab.

Tower for `top_slab_support` (compoundSourcePos=45,-57,87, support at 44,-57,87):
- `towerBase` = `(45,-60,87)`: `stone_slab[type=bottom]` (bare bottom slab)
- `towerAnchor` = `(45,-59,87)`: `stone` + `addAnchor` (anchored stone; hasBottomSlabBelow=true)
- `towerCarrier` = `(45,-58,87)`: `stone_slab[type=bottom]` + `updatePersistentLoweredSlabCarrier`
- `compoundSourcePos` = `(45,-57,87)`: `stone` + `addAnchor` + `addCompoundFullBlockAnchor`
- `supportCandidatePos` = `(44,-57,87)`: `stone_slab[type=top]` + `addCompoundVisibleSideUpperSlab`

Tower for `bottom_slab_support` (compoundSourcePos=44,-57,88, support at 43,-57,88):
- `towerBase` = `(44,-60,88)`: `stone_slab[type=bottom]`
- `towerAnchor` = `(44,-59,88)`: `stone` + `addAnchor`
- `towerCarrier` = `(44,-58,88)`: `stone_slab[type=bottom]` + `updatePersistentLoweredSlabCarrier`
- `compoundSourcePos` = `(44,-57,88)`: `stone` + `addAnchor` + `addCompoundFullBlockAnchor`
- `supportCandidatePos` = `(43,-57,88)`: `stone_slab[type=bottom]` + `addCompoundVisibleSideLowerSlab`

---

## Proof output (source truth parity run)

### top_slab_support

```
[JULIA_BETA35_FLOOR_TORCH_V2_COORDINATE_REPLAY]
  caseName=top_slab_support
  expectedTorchPos=44, -56, 87
  actualTorchPos=44, -56, 87
  expectedSupportCandidatePos=44, -57, 87
  actualSupportCandidatePos=44, -57, 87
  coordinateParity=true
  failureLayer=NONE

[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_MEASURED]
  caseName=top_slab_support
  supportCandidatePos=44, -57, 87
  supportCandidateState=Block{minecraft:stone_slab}[type=top,waterlogged=false]
  supportDy=-1.000          (live: -1.000)  ✓
  torchDy=-0.500            (live: -0.500)  ✓
  rawSupportTopY=-56.000000   (live: -56.000000)  ✓
  supportVisibleTopY=-57.000000  (live: -57.000000)  ✓
  rawTorchShapeMinY=-0.500000  (live: -0.500000)  ✓
  torchModelBottomY=-56.500000  (live: -56.500000)  ✓
  contactGap=0.500000       (live: 0.500000)  ✓
  sourceTruthContext=compound_visible_side_upper_slab
  compoundAnchorAtSource=true
  compoundVisibleMarkWritten=true
  coordinateParity=true
  fixtureMatchesV2LiveStack=true
  failureLayer=NONE
  redProofResult=GREEN
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
  failureLayer=NONE

[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_MEASURED]
  caseName=bottom_slab_support
  supportCandidatePos=43, -57, 88
  supportCandidateState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false]
  supportDy=-1.000          (live: -1.000)  ✓
  torchDy=-1.000            (live: -1.000)  ✓
  rawSupportTopY=-56.500000   (live: -56.500000)  ✓
  supportVisibleTopY=-57.500000  (live: -57.500000)  ✓
  rawTorchShapeMinY=-1.000000  (live: -1.000000)  ✓
  torchModelBottomY=-57.000000  (live: -57.000000)  ✓
  contactGap=0.500000       (live: 0.500000)  ✓
  sourceTruthContext=compound_visible_side_lower_slab
  compoundAnchorAtSource=true
  compoundVisibleMarkWritten=true
  coordinateParity=true
  fixtureMatchesV2LiveStack=true
  failureLayer=NONE
  redProofResult=GREEN
```

---

## Success-criteria classification

Per the task spec:

> **A. coordinate/source parity true and contactGap=+0.5 → production-fix-ready RED**
> B. coordinate/source parity true and contactGap=0 → live recorder/context still suspect
> C. parity false → stop with V2_COORDINATE_MISMATCH or V2_SOURCE_TRUTH_MISMATCH

Result: **Case A** — coordinateParity=true, fixtureMatchesV2LiveStack=true, contactGap=0.500000.
The real visual gap exists. The proof is production-fix-ready RED.

---

## Next slice

Production fix (floor-torch contact height) is required to close the 0.5-block visual gap
between the torch model bottom and the lowered slab visible top surface. The controlled
proof now reproduces the exact live stack:
- `supportDy=-1.000` via `COMPOUND_VISIBLE_SIDE_UPPER/LOWER_SLAB` mark
- `contactGap=0.500000` confirmed in fixture

Next: implement the production fix and measure `contactGap=0.000000` → `failureLayer=NONE`
with the fix applied, or extend the proof gate to gate-keep the fix.

---

## Release and coverage

- Beta 3.5 release prep: **PAUSED**
- `productionGameplayFixApplied=false`
- `releaseTagMoved=false`
- `wall_torch=NOT_COVERED`
- `lantern=NOT_COVERED`
- `signs=NOT_COVERED`
- `chains=NOT_COVERED`
