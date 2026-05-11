# Beta 3.5 Floor Torch Full-Block Contact Fix

## Status

- Production fix applied narrowly for `floor_torch` on lowered ordinary full-block support.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
- Beta 3.5 release remains paused pending Julia live re-test.
- No release tag moved.

## Residual triage context

Live retest from `save/beta35-floor-torch-v2-contact-fix` found 14 in-scope `minecraft:torch` cases
on lowered full-block support with:

- `supportDy=-1.000`
- `torchDy=-0.500` (pre-fix)
- `contactGap=0.500000` (pre-fix)

These were not covered by the previous slice which fixed slab supports only.

## Fix mechanism

`SlabSupport.getYOffsetInner` — floor-torch branch extended:

- Added private helper `isOrdinaryFullBlockWithCompoundDy(world, pos, state)` that mirrors the
  exact conditions that yield `dy=-1.0` for a non-slab solid block:
  - Block is anchored via `SlabAnchorAttachment.isAnchored`
  - AND either `isCompoundFullBlockAnchor` is true OR the bottom slab directly below is
    adjacent-side-lowered (`isAdjacentSideSlabLowered`)
  - Safe to call inside the `IN_GET_Y_OFFSET` recursion guard; does not delegate to `getYOffset`.

- In the `isFloorTorch` branch: if `isOrdinaryFullBlockWithCompoundDy(world, supportPos, supportState)`
  returns true, returns `-1.0` (floor_torch only; wall_torch, lanterns, signs, chains are not affected).

## Legal outcomes

| Case | legalOutcome | torchDy | contactGap | Notes |
|---|---|---|---|---|
| lowered_full_block_support | `FLOOR_TORCH_LOWERED_FULL_BLOCK_SUPPORT_CONTACT_ALIGNED` | -1.000 | 0.000000 | Fixed by this slice |
| top_slab_support | `FLOOR_TORCH_COMPOUND_VISIBLE_TOP_SLAB_SUPPORT` | -1.000 | 0.000000 | Fixed previous slice |
| bottom_slab_support | `FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_REJECTED_DY_LT_MINUS_ONE_ILLEGAL` | N/A | N/A | Rejected by law |

## Invariant preserved

- `dy >= -1.0` invariant is not violated: `-1.0` is the legal floor.
- The bottom slab compound-visible reject/defer case is preserved; that case requires illegal `dy < -1.0`.

## Focused proof

Gate: `-Dslabbed.beta35FloorTorchFullBlockContactRed=true`

Command:

```
JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTorchFullBlockContactRed=true" ./gradlew --no-daemon runClientGameTest --console plain
```

### lowered_full_block_support

- `compoundAnchorAtSupport=true`
- `supportDy=-1.000`
- `torchDyBefore=-0.500`
- `torchDyAfter=-1.000`
- `supportVisibleTopY=-57.000000`
- `torchModelBottomY=-57.000000`
- `contactGap=0.000000`
- `survival=SURVIVAL_GREEN`
- `triad=GREEN`
- `legalOutcome=FLOOR_TORCH_LOWERED_FULL_BLOCK_SUPPORT_CONTACT_ALIGNED`
- `failureLayer=NONE`

### Regression: top_slab_support

- `contactGap=0.000000`
- `torchDyAfter=-1.000`
- `survival=SURVIVAL_GREEN`
- `triad=GREEN`
- `failureLayer=NONE`

### Regression: bottom_slab_support

- `placementResult=Fail[]`
- `survival=REJECTED_BY_LAW`
- `triad=REJECTED_BY_LAW`
- `failureLayer=NONE`

## Regression status

- compileJava compileGametestJava: GREEN
- focused full-block contact proof: GREEN
- previous live item anchoring proof: GREEN
- previous object triad proof: GREEN
- default client gametest suite: GREEN
- clean build gate: GREEN
- git diff --check: GREEN

## Coverage

- `wall_torch=NOT_COVERED`
- `lantern=NOT_COVERED`
- `signs=NOT_COVERED`
- `chains=NOT_COVERED`

Evidence folder:

`tmp/beta35-floor-torch-fullblock-contact-fix-d21d211`
