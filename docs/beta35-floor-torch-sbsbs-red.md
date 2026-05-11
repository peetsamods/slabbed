# Beta 3.5 Floor Torch SBSBS Red Proof

## Status

- Proof-only slice. No production gameplay fix implemented.
- Beta 3.5 release remains paused.
- No release tag moved.

## Julia live report (post 04ace65)

After the support-finalization fix at `04ace65` / `save/beta35-floor-torch-support-finalization-fix`,
Julia live-tested and reported:

> "SBSBS+item = floating item in vanilla position. (torch, in this case.)"

This is a distinct remaining in-scope floor_torch failure separate from the stale-torch-after-
compound-visible-lower-mark fix.

## SBSBS fixture definition

SBSBS is a 5-level alternating slab/full-block vertical tower (bottom to top):

| Level | Role | Block |
|---|---|---|
| `baseSlab` (S) | Base slab — lowest layer | `stone_slab[type=bottom]` |
| `lowerAnchorBlock` (B) | Lower ordinary full block | `Blocks.STONE` + `addAnchor` |
| `middleCarrierSlab` (S) | Middle carrier slab | `stone_slab[type=bottom]` + `updatePersistentLoweredSlabCarrier` |
| `upperAnchorBlock` (B) | Upper ordinary full block | `Blocks.STONE` + `addAnchor` |
| `supportPos` (S) | Top support slab — where torch is placed | `stone_slab[type=bottom]` + `updatePersistentLoweredSlabCarrier` |
| `torchPos` | Floor torch placed on supportPos | `minecraft:torch` via `interactBlock` |

Proof coordinates: `supportPos = (60, -57, 90)`, distinct from all existing fixtures.

## Controlled fixture result

Gate: `-Dslabbed.beta35FloorTorchSbsbsRed=true`

### FIXTURE_GREEN

```
[JULIA_BETA35_FLOOR_TORCH_SBSBS_FIXTURE_GREEN]
  structure=SBSBS
  supportPos=60,-57,90
  supportState=stone_slab[type=bottom,waterlogged=false]
  supportDy=-0.500
  upperAnchorBlock=60,-58,90
  upperAnchored=true
  middleCarrierMarked=true
  supportCarrierMarked=true
  fixtureResult=GREEN
```

### MEASURED

```
[JULIA_BETA35_FLOOR_TORCH_SBSBS_MEASURED]
  structure=SBSBS
  torchPos=60,-56,90
  torchState=Block{minecraft:torch}
  supportPos=60,-57,90
  supportState=stone_slab[type=bottom,waterlogged=false]
  supportDy=-0.500000
  torchDy=-1.000000
  rawSupportTopY=-56.500000
  supportVisibleTopY=-57.000000
  rawTorchShapeMinY=-56.000000
  torchModelBottomY=-57.000000
  contactGap=0.000000
  isVanillaPosition=false
  placementAccepted=true
  survivalResult=SURVIVAL_GREEN
  triadResult=GREEN
  supportType=lowered_carrier
  torchPlacedBeforeFinalization=false
  failureLayer=NONE
```

### SUMMARY

```
[JULIA_BETA35_FLOOR_TORCH_SBSBS_SUMMARY]
  structure=SBSBS
  failureLayer=NONE
  isVanillaPosition=false
  redProofResult=GREEN
  juliaLiveReport=SBSBS_TORCH_FLOATS_VANILLA_POSITION
  productionGameplayFixApplied=false
  beta35ReleaseStatus=PAUSED_SBSBS_UNRESOLVED
```

## Analysis: why the controlled fixture is GREEN

The SBSBS proof with explicitly seeded marks (via `addAnchor` and
`updatePersistentLoweredSlabCarrier`) produces the correct result:

- `upperAnchorBlock` is anchored → `isPersistentLoweredBottomSlabCarrierNonRecursive` returns
  `true` for `supportPos` → `getYOffset(supportPos) = -0.5`
- `isAdjacentSideSlabLowered(world, supportPos)` = `true` (via `isPersistentLoweredSlabCarrier`)
- `getYOffsetInner` for the torch at `torchPos`:
  - `shouldOffset` = `true` (bottom slab below in column)
  - Compound case: `isBottomSlab(belowSlab) && isAdjacentSideSlabLowered` → returns `-1.0`
- `torchDy = -1.000`, `contactGap = 0.000000`

Even without the explicit `updatePersistentLoweredSlabCarrier` call, the dynamic qualification
pathway (`hasBottomSlabBelow(world, upperAnchorBlock)` = `true` because `middleCarrierSlab` is
`stone_slab[type=bottom]`) causes `isPersistentLoweredBottomSlabCarrierNonRecursive` to return
`true` for `supportPos`.

## Gap between controlled fixture and Julia live

The controlled fixture is **GREEN** but Julia's live test showed the torch at "vanilla position."
The gap is unresolved. Possible causes:

1. Julia's live SBSBS used blocks or a structure arrangement not captured by the plain
   `S-B-S-B-S` fixture (e.g., different slab type, interaction with adjacent compound-visible
   blocks, or a pre-existing block at the support position).
2. Gameplay-hook ordering issue: if blocks are placed in a non-sequential order or after
   chunk reload, some marks may not have been written at the time the torch was placed.
3. Visual rendering discrepancy: `dy=-1.000` is computed correctly but some render-path
   does not apply it.

## Next action

Investigate Julia's exact live structure and block types before implementing a production fix.
The current code handles plain SBSBS correctly in the controlled fixture. Next slice should
target the specific gameplay path that Julia actually encountered.

## Failure layer taxonomy

| Layer | Meaning |
|---|---|
| `NONE` | Controlled fixture is GREEN (current result) |
| `SBSBS_FLOOR_TORCH_VANILLA_POSITION` | Torch at vanilla height (dy≈0) |
| `SBSBS_SUPPORT_SOURCE_TRUTH_MISMATCH` | Support slab dy ≠ -0.5 |
| `SBSBS_STALE_TORCH_AFTER_FINALIZATION` | Like support finalization bug in SBSBS context |
| `SBSBS_TORCH_NOT_PLACED` | Placement rejected (e.g. compound visible lower slab) |
| `SBSBS_OUT_OF_SCOPE` | Out of floor_torch scope |

## Regression status

- compileJava compileGametestJava: GREEN
- focused SBSBS proof (`-Dslabbed.beta35FloorTorchSbsbsRed=true`): GREEN (NONE)
- support finalization regression (`-Dslabbed.beta35FloorTorchSupportFinalizationRed=true`): GREEN
- v2 contact fix regression (`-Dslabbed.beta35FloorTorchV2ContactGapRed=true`): GREEN
- default client gametest suite: GREEN
- git diff --check: GREEN

## Coverage

- `wall_torch=NOT_COVERED`
- `lantern=NOT_COVERED`
- `signs=NOT_COVERED`
- `chains=NOT_COVERED`

Evidence folder: `tmp/beta35-floor-torch-sbsbs-red-04ace65/`
