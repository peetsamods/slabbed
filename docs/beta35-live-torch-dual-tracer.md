# Beta 3.5 Live Torch Dual Tracer

- Date: 2026-05-11
- Base: `b149996` / `save/beta35-floor-torch-lowered-slab-contact`
- Scope: `floor_torch_only`
- Gate: `-Dslabbed.beta35LiveTorchDualTrace=true`

## Status

`b149996` is not live-accepted. Julia's live video after that savepoint shows two
separate floor-torch failures:

1. Floor torches sometimes place but visibly float above the slab/stone surface.
2. Floor torches sometimes cannot be placed at all on the intended support.

The prior proof fixed one narrow measured case:

- support source: `COMPOUND_VISIBLE_SIDE_LOWER_SLAB`
- `torchDy=-1.500`
- `contactGap=0.000000`

That proof did not cover every live source path or the full player
placement/targeting/intent path.

## Trace markers

- `[JULIA_BETA35_LIVE_TORCH_DUAL_TRACE] enabled=true`
- `[JULIA_BETA35_LIVE_TORCH_PLACEMENT_ATTEMPT]`
- `[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT]`
- `[JULIA_BETA35_LIVE_TORCH_DUAL_SUMMARY]`

## Placement classifications

- `PLACEMENT_TARGET_MISS`
- `PLACEMENT_REJECTED`
- `COMFORT_NO_BOX_INTERSECTION`
- `WRONG_TARGET_OWNER`
- `PLACEMENT_RESULT_UNKNOWN`
- `PLACEMENT_ATTEMPT_OK`
- `OCCUPIED_TORCH_TARGET`
- `DUPLICATE_TORCH_TARGET`

## Existing contact classifications

- `PLACED_CONTACT_GREEN`
- `PLACED_CONTACT_GAP`
- `WRONG_SOURCE_TYPE`
- `NO_SUPPORT_CANDIDATE`
- `NO_TORCH_NEAR_TARGET`

## Scope guard

This tracer is manual-live, proof-only, and off by default. It does not implement
a production behavior fix. Beta 3.5 release prep remains paused pending Julia
live trace. `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
No release tag moved.

## Follow-up placement fix

The live trace at `fe7677a` isolated a placement-layer failure on lowered
bottom-slab support:

- `heldItem=minecraft:torch`
- `intendedSupportCandidateState=stone_slab[type=bottom]`
- `intendedSupportSourceType=PLAIN_STATE`
- `intendedSupportDy=-1.000000`
- `finalInteractResult=Fail[]`
- `torchBlockAppearedAfterAttempt=false`
- `classification=PLACEMENT_RESULT_UNKNOWN`

That is now covered by
`-Dslabbed.beta35FloorTorchLoweredSlabPlacement=true`. The fixed proof reports
`classification=PLACEMENT_ATTEMPT_OK`, `finalInteractResult=Success[...]`,
`torchBlockAppearedAfterAttempt=true`, `torchDy=-1.500000`,
`contactGap=0.000000`, `survival=SURVIVAL_GREEN`, and `failureLayer=NONE`.

This remains `floor_torch_only`; wall torch, lantern, signs, and chains are still
`NOT_COVERED`. Beta 3.5 release prep remains paused pending Julia live
acceptance. No release tag moved.

## Follow-up plain-bottom contact fix

The live trace after `66ca74a` showed one remaining concrete contact gap, now
isolated away from the previous `supportDy=-1.0` placement bug:

- `torchPos=32,-54,80`
- `supportCandidateState=stone_slab[type=bottom]`
- `supportSourceType=PLAIN_STATE`
- `supportDy=-0.500000`
- previous `torchDy=-0.500000`
- previous `contactGap=0.500000`

That row is now covered by
`-Dslabbed.beta35FloorTorchPlainBottomContact=true`. The fixed proof reports
`supportSourceType=PLAIN_STATE`, `supportDy=-0.500000`,
`torchDy=-1.000000`, `supportVisibleTopY=-55.000000`,
`torchModelBottomY=-55.000000`, `contactGap=0.000000`,
`triadCoLocated=yes`, `classification=PLACED_CONTACT_GREEN`, and
`failureLayer=NONE`.

Duplicate clicks on an already occupied floor-torch target now classify as
`OCCUPIED_TORCH_TARGET` instead of a release-blocking placement failure. Scope
remains `floor_torch_only`; wall torch, lantern, signs, and chains are still
`NOT_COVERED`. Beta 3.5 release prep remains paused pending Julia live
acceptance. No release tag moved.
