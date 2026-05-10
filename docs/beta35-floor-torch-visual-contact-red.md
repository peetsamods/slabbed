# Beta 3.5 Floor Torch Visual Contact Audit

## Status

Julia manually live-tested after `0f08624` /
`save/beta35-floor-torch-player-placement` and did not accept the visual
anchoring. The placement proof is GREEN, but the release blocker is now whether
the floor torch visually sits on the slab-supported surface where the player
expects it to sit.

Beta 3.5 release prep remains **PAUSED**. No release tag was moved.

## Gated audit

Gate:

```
-Dslabbed.beta35FloorTorchVisualContactRed=true
```

Markers:

```
[JULIA_BETA35_FLOOR_TORCH_CONTACT_GAP_MEASURED]
[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_PENDING]
[JULIA_BETA35_FLOOR_TORCH_VISUAL_SUMMARY]
```

## Current measured result

The controlled floor-torch fixture measures:

- `supportPos=49,201,0`
- `supportState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false]`
- `supportDy=-0.500`
- `supportVisibleTopY=201.000000`
- `torchPos=49,202,0`
- `torchState=Block{minecraft:torch}`
- `torchDy=-1.000`
- `torchModelBottomY=201.000000`
- `torchModelTopY=201.625000`
- `outlineMinY=201.000000`
- `outlineMaxY=201.625000`
- `raycastMinY=201.000000`
- `raycastMaxY=201.625000`
- `contactGap=0.000000`
- `contactGapAcceptable=true`
- `triadCoLocated=true`
- `visualContactProofStatus=PENDING`
- `failureLayer=FIXTURE_MISMATCH`

## Classification

The controlled fixture does not show a model/support contact gap:
`torchModelBottomY` equals `supportVisibleTopY`. The triad remains GREEN because
model, outline, and raycast all agree with each other.

This does **not** clear Julia's manual RED. It means the current automated
fixture is not yet proven to match the live screenshot shape, or the live
complaint depends on player expectation/fixture geometry outside this controlled
case. No tiny gameplay fix is proven by this measurement alone.

## Audit questions

1. Does the current placement proof measure the visible support top Y?
   - Not before this audit. The visual-contact proof now measures
     `supportVisibleTopY`.
2. Does it measure the torch model bottom Y?
   - Not before this audit. The visual-contact proof now measures
     `torchModelBottomY`.
3. Is torch model bottom equal to support top, or is there a gap?
   - In the controlled fixture, they are equal:
     `torchModelBottomY=201.000000`, `supportVisibleTopY=201.000000`,
     `contactGap=0.000000`.
4. Does `torchDy=-1.000` make sense relative to `supportDy=-0.500` for a floor
   torch?
   - In this controlled fixture, yes: the support bottom slab is lowered to a
     visible top at world Y `201.000000`, and the torch model bottom also lands
     at world Y `201.000000`.
5. Is triad GREEN simply proving model/outline/raycast agree with each other,
   even if all three are at the wrong visual height?
   - Yes. Triad GREEN is co-location only. The new contact audit separately
     checks model bottom against support visible top.
6. Does the live screenshot shape match the controlled proof fixture?
   - Not proven. This is the current `FIXTURE_MISMATCH` classification.
7. Is the failure model height, support top calculation, fixture mismatch, or
   player expectation mismatch?
   - Current automated classification is `FIXTURE_MISMATCH`. Model height and
     support top calculation are internally consistent in the controlled
     fixture, so a broad render/dy fix is not justified by this proof.

## Scope guard

This audit is floor-torch-only. `wall_torch`, `lantern`, `signs`, and `chains`
remain NOT_COVERED and are not the current blocker.
