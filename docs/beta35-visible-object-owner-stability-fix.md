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

## 2026-05-14 Continuation: Trapdoor Server Validation

Continuation base: `22ec3f2` / `save/beta35-visible-object-owner-stability`.

Julia's 10:47 live source truth keeps the visible-owner fix intact: chains, trapdoor selection, torches, and buttons are very good. The remaining trapdoor failure is server-side validation, not client ownership. Live rows show `TRAPDOOR_OWNER_GREEN` and `HIT_ACCEPTANCE_GREEN` are common, with `SERVER_HIT_TOO_FAR=0`, but specific legal lowered bottom-trapdoor hits still reached `classification=HIT_ACCEPTANCE_SERVER_REJECT` because `shiftedValidationCenter=null`.

Primary failure layer for the continuation:

`LOWERED_TRAPDOOR_SERVER_SHIFTED_VALIDATION_GAP`

Representative before row:

`targetState=minecraft:oak_trapdoor[half=bottom] targetDy=-1.000000 hitFace=up validationDeltaY=-1.312500 shiftedValidationCenter=null classification=HIT_ACCEPTANCE_SERVER_REJECT failureLayer=LOWERED_TRAPDOOR_SERVER_SHIFTED_VALIDATION_GAP`

The continuation narrows server shifted validation to legal lowered bottom-trapdoor server targets with finite negative target dy. The shifted center is still checked through the existing vanilla component tolerance path; no global hit tolerance was widened and no server accept bypass was added.

Focused proof flag:

`-Dslabbed.beta35TrapdoorServerValidationFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true`

Required green coverage:

- Oak, mangrove, and birch bottom-trapdoor rows at `targetDy=-1.000000` classify as `SERVER_SHIFTED_HIT_GREEN` with `failureLayer=NONE`.
- Held item examples include `stone_slab`, `mangrove_trapdoor`, `torch`, and `acacia_button`.
- Negative boundary remains rejected as `TRAPDOOR_SERVER_NEGATIVE_GREEN accepted=false`.
- Open-state proof reports `JULIA_BETA35_TRAPDOOR_OPEN_STATE_GREEN` with `serverAccepted=true` and `finalStateOpen=true`.

The paired slab-neighbor tracer is proof-only. It records side placement, merge placement, and neighbor-update state/dy around the live-shaped slab lane and currently classifies the automated fixture as `EXPECTED_SLAB_PLACEMENT failureLayer=NONE`. No neighbor-update production patch was made.

## 2026-05-14 Continuation: Regular Door Visible Owner

Continuation base: `23b562c` / `save/beta35-trapdoor-server-validation`.

Julia's 6:30 live source truth after trapdoor server validation kept the earlier visible-owner categories mostly green, but regular doors still lost ownership to the support slab even when `rayIntersectsVisibleObject=true`. Door support-steal source counts were `spruce_door=23` and `acacia_door=3`; `birch_stairs=5` remains deferred.

The continuation extends the same visible-owner preservation rule to lowered regular `DoorBlock` halves. The ray must intersect the shifted door outline/raycast shape, and explicit support aim still remains support-owned.

Focused proof:

`JULIA_BETA35_REGULAR_DOOR_OWNER_SUMMARY outcome=GREEN rows=6 green=6 red=0 doorSupportStealRowsBefore=26 doorSupportStealRowsAfter=0 spruceDoorSupportStealRowsBefore=23 spruceDoorSupportStealRowsAfter=0 acaciaDoorSupportStealRowsBefore=3 acaciaDoorSupportStealRowsAfter=0 regularDoorOwnerGreenRows=6 stairRowsDeferred=5 stairRowsFixed=0 classification=REGULAR_DOOR_OWNER_GREEN failureLayer=NONE releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false`

The paired slab-lane proof reproduced one lowered non-slab source jump but classified it rather than patching it:

`JULIA_BETA35_SLAB_PLACEMENT_LANE_JUMP_SUMMARY outcome=GREEN rows=4 loweredSourceRows=2 slabJumpRowsBefore=1 slabJumpRowsAfter=1 expectedSlabPlacementRows=2 neighborDyRenormalizationRows=0 illegalDy0FromLoweredSourceRows=1 legalDestinationState=NONE productionFixImplemented=false classification=SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE failureLayer=NONE`

No global tolerance, collision, solidity, or sturdy-face behavior changed. No release audit ran, no release tag moved, and no all-item gameplay claim is made.
