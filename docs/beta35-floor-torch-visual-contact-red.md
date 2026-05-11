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

## Live-shape fixture parity follow-up

A second gated proof now targets a screenshot-faithful multi-level structure:

```
-Dslabbed.beta35FloorTorchLiveShapeRed=true
```

Markers:

```
[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_FIXTURE_GREEN]
[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_CONTACT_MEASURED]
[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_SUMMARY]
```

Measured live-shape result in the parity fixture:

- `expectedTorchPos=67,202,0`
- `actualTorchPos=67,202,0`
- `supportPos=67,201,0`
- `supportState=stone_slab[type=bottom]`
- `supportDy=-0.500`
- `supportVisibleTopY=201.000000`
- `torchDy=-1.000`
- `torchModelBottomY=201.000000`
- `torchModelTopY=201.625000`
- `contactGap=0.000000`
- `triad=GREEN`
- `liveShapeProofStatus=GREEN`
- `failureLayer=NONE`

Follow-up live evidence after `3212d88` proved the remaining bad cases were
lowered bottom-slab support cases with `supportDy=-1.000000`, not placement,
survival, or a model/outline/raycast triad split.

The narrow lowered bottom-slab contact fix is documented at
`docs/beta35-floor-torch-lowered-slab-contact-fix.md`. The focused
`-Dslabbed.beta35FloorTorchLiveShapeRed=true` proof now also retests two
coordinate-equivalent lowered bottom-slab cases:

- `torchPos=43,-56,88`, `supportCandidateState=stone_slab[type=bottom]`,
  previous `contactGap=0.500000`, now `torchDy=-1.500`,
  `supportVisibleTopY=-57.500000`, `torchModelBottomY=-57.500000`,
  `contactGap=0.000000`, `triadCoLocated=true`, `failureLayer=NONE`.
- `torchPos=43,-55,79`, `supportCandidateState=stone_slab[type=bottom]`,
  previous `contactGap=1.000000`, now `torchDy=-1.500`,
  `supportVisibleTopY=-56.500000`, `torchModelBottomY=-56.500000`,
  `contactGap=0.000000`, `triadCoLocated=true`, `failureLayer=NONE`.

The controlled `supportDy=-0.500` fixture remains GREEN/PENDING with
`contactGap=0.000000`. Beta 3.5 release prep remains paused pending Julia live
acceptance.

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

Beta 3.5 release prep remains **PAUSED**. Do not claim `floor_torch_only`
release scope accepted.

## Follow-up contact-gap proof (RED slice)

Wall-torch, lantern, sign, and chain coverage remains `NOT_COVERED`.

A new floor-torch contact-gap proof exists for Julia live capture `CONTACT_GAP`
with gate `-Dslabbed.beta35LiveFloorTorchContactGapRed=true` and markers:

- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]`

This historical RED slice is superseded for lowered bottom-slab support by
`docs/beta35-floor-torch-lowered-slab-contact-fix.md`. Scope remains
`floor_torch_only`; `wall_torch`, `lantern`, `signs`, and `chains` remain
`NOT_COVERED`.

## Julia live dual failure after b149996

Julia's live video after `b149996` / `save/beta35-floor-torch-lowered-slab-contact`
reopens visual/contact acceptance for live-only floor-torch paths. Two failure
layers must stay separate:

- **VISUAL CONTACT / SOURCE TRUTH / DY**: existing or newly placed floor torches
  can visibly float even after the narrow `COMPOUND_VISIBLE_SIDE_LOWER_SLAB`
  case measured `torchDy=-1.500` and `contactGap=0.000000`.
- **PLACEMENT / TARGETING / INTENT**: some floor torch placement attempts do not
  resolve to the intended support/position.

The follow-up tracer is manual-live, proof-only, and gated by
`-Dslabbed.beta35LiveTorchDualTrace=true`. It logs existing floor-torch contact
with `[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT]` and placement attempts with
`[JULIA_BETA35_LIVE_TORCH_PLACEMENT_ATTEMPT]`.

Beta 3.5 release prep remains paused pending Julia live trace. Scope remains
`floor_torch_only`; non-floor categories remain `NOT_COVERED`; no release tag
moved.

## Placement follow-up after dual tracer

The `fe7677a` dual trace separated a placement failure from visual/contact. The
new lowered bottom-slab placement proof is GREEN for `floor_torch` with
`intendedSupportDy=-1.000000`: `finalInteractResult=Success[...]`,
`torchBlockAppearedAfterAttempt=true`, `torchDy=-1.500000`,
`contactGap=0.000000`, `survival=SURVIVAL_GREEN`, and `failureLayer=NONE`.

This does not change the visual-contact fixture status: the controlled visual
contact proof still builds successfully with `contactGap=0.000000`, `triad=GREEN`,
and `failureLayer=FIXTURE_MISMATCH`. Julia live acceptance is still required
before release prep resumes.
