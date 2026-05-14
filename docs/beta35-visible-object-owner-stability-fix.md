# Beta 3.5 Visible Object Owner Stability Fix

Slice base: `05f1582` / `save/beta35-hitbox-aperture-fix`.

Evidence folder: `tmp/beta35-floor-button-contact-fix-05f1582/visible-owner-stability-opus/`.

## Why

Julia's 2026-05-13 live co-test showed target jumping and misplacements at the problematic slab height. Latest live logs had server rejects at zero and contact gaps at zero; the remaining blocker was ownership: visible trapdoors/chains could lose final target ownership to the slab/support layer.

Primary failure layer: `VISIBLE_OBJECT_OWNER_SUPPORT_STEAL`.

Secondary chain layer: `CHAIN_AXIS_VISIBLE_OWNER_UNSTABLE`.

## Change

- `GameRendererCrosshairRetargetMixin` now preserves a proven visible object owner before slab/support rescue can return support ownership.
- `SlabSupport` centralizes the Beta 3.5 visible-owner candidate set for this slice: lowered floor buttons, lowered bottom trapdoors, and lowered vertical chains.
- `Beta35SlabHeightHitAcceptanceRecorder` separates chain owner stability from unresolved axis/contact metric bookkeeping and records whether the ray intersects the visible object.
- The dirty floor-button contact WIP is retained, with floor-button contact gaps green at `supportDy=-1.0` and `supportDy=-0.5`.

The ray must intersect the visible outline/raycast shape. Empty overhang and explicit support aim remain non-owning; support aim is logged as `HIT_ACCEPTANCE_SUPPORT_AIM failureLayer=NONE`, not as support steal.

## Proof

Focused visible-owner proof:

`JULIA_BETA35_VISIBLE_OBJECT_OWNER_STABILITY_SUMMARY outcome=GREEN rows=16 green=16 red=0 trapdoorSupportStealRowsBefore=1 trapdoorSupportStealRowsAfter=0 chainSupportStealRowsBefore=1 chainSupportStealRowsAfter=0 chainOwnerGreenRows=2 chainMetricDeferredRows=2 hitAcceptanceMissRowsForReproducedTargets=0 slabJumpRowsBefore=2 slabJumpRowsAfter=0 serverRejectRows=0 emptyOverhangStealRows=0 failureLayer=NONE`

Chain status:

`chainMetricStatus=OWNER_GREEN_METRIC_DEFERRED`

Run-client startup smoke:

The refreshed smoke emitted beta35 hit-acceptance markers, trapdoor owner-green rows, and `CHAIN_AXIS_METRIC_ONLY_DEFERRED failureLayer=NONE` rows. It did not emit `CHAIN_AXIS_VISIBLE_OWNER_UNSTABLE` or `HIT_ACCEPTANCE_SUPPORT_STEAL` before intentional termination.

Floor-button retained WIP proof:

`JULIA_BETA35_FLOOR_BUTTON_CONTACT_SUMMARY outcome=GREEN rows=4 green=4 red=0 supportDyNegOneContactGap=0.000000 supportDyNegHalfContactGap=0.000000 failureLayer=NONE`

Corrected RED proof after this continuation:

`JULIA_BETA35_HITBOX_APERTURE_CONTACT_SUMMARY outcome=NOT_REPRODUCED rows=8 green=8 red=0 buttonContactGapRows=0 chainMetricGapRows=0 apertureTooNarrowRows=0 firstFailureLayer=NONE`

Retained aperture proof:

`JULIA_BETA35_HITBOX_APERTURE_FIX_SUMMARY outcome=GREEN rows=6 apertureTooNarrowRowsAfter=0 emptyOverhangStealRows=0 failureLayer=NONE`

Regression status:

- Fence/wall owner-server hit: GREEN, `globalHitToleranceWidened=false`.
- Fence/wall stack contact: GREEN, `serverHitToleranceChanged=false`.
- Fence/wall visual-hitbox stack aim: GREEN, `globalCollisionLowered=false`.
- Common object matrix: GREEN, `rows=27 greenAlreadyInherits=27 contactGap=0`.

## Scope

This is an automated-proof savepoint, not Julia live acceptance. Julia live acceptance is still required.

No global hit tolerance widening. No server accept bypass. No global collision lowering. No global solidity or sturdy-face lies. No release audit run. No release tag moved. No all-item gameplay claim.
