# Beta 3.5 Door Half Server Validation Fix

Slice base: `e23c62a` / `save/beta35-door-owner-slab-jump`.

Evidence folder: `tmp/beta35-door-half-server-validation-fix-e23c62a/`.

## Why

Julia's 2026-05-14 7:20 live source truth showed the regular-door visible-owner fix held: `REGULAR_DOOR_OWNER_GREEN=1621` and `HIT_ACCEPTANCE_SUPPORT_STEAL=0`. The remaining regular-door symptom was half-dependent blinking or inconsistent open/close behavior.

The live rows identify this as server validation, not owner selection, contact dy, or global distance:

- `HIT_ACCEPTANCE_SERVER_REJECT=23`
- `SERVER_HIT_TOO_FAR=0`
- `CONTACT_GAP=0`
- reject targets: `spruce_door=21`, `acacia_door=2`
- upper-half rejects: `spruce_door upper=20`, `acacia_door upper=2`
- representative row: `targetDy=-1.000000`, `shiftedValidationCenter=null`, `classification=HIT_ACCEPTANCE_SERVER_REJECT`

Primary failure layer: `REGULAR_DOOR_SERVER_SHIFTED_VALIDATION_GAP`.

Secondary failure layer: `DOOR_HALF_PAIR_INTERACTION_INSTABILITY`.

## Change

- Generalized the existing Beta 3.5 regular-door contact dy path from oak-only to regular `DoorBlock` halves.
- Added `SlabSupport.isBeta35LoweredRegularDoorServerHitTarget(...)` for lowered, paired, internally consistent regular doors.
- Added regular-door eligibility to `ServerInteractBlockHitToleranceMixin`'s existing shifted-center server validation path.
- Added recorder classification for `REGULAR_DOOR_SERVER_SHIFTED_VALIDATION_GAP`.
- Added focused proof for upper/lower server validation, paired open-state durability, and a non-ownable dy negative boundary.

The shifted hit still has to pass the existing vanilla component tolerance after applying `targetDy`. This is not a global hit tolerance widening, not a server accept bypass, not global collision lowering, not a solidity or sturdy-face lie, and not an all-item support claim.

## Proof

Focused proof flag:

`-Dslabbed.beta35DoorHalfServerValidationFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true`

After rows cover:

- spruce upper, open, held `minecraft:stone_slab`, `targetDy=-1.000000`
- spruce lower, open, held `minecraft:stone_slab`, `targetDy=-1.000000`
- acacia upper, closed, held `minecraft:acacia_door`, `targetDy=-1.000000`
- spruce upper, closed, held `minecraft:acacia_door`, `targetDy=-1.000000`

Each row reports a shifted validation center, shifted delta inside vanilla tolerance, `classification=DOOR_SERVER_VALIDATION_GREEN`, and `failureLayer=NONE`.

Open-state proof:

- lower-half click: `false/false -> true/true`
- upper-half click: `false/false -> true/true`
- repeated upper-half click: `true/true -> false/false`

Each row reports `serverAccepted=true` and `finalPairedStateConsistent=true`.

Negative boundary:

`JULIA_BETA35_DOOR_HALF_SERVER_NEGATIVE_GREEN ... targetDy=0.000000 shiftedValidationCenter=null expectedAccepted=false accepted=false classification=DOOR_SERVER_NEGATIVE_GREEN`

Summary:

`JULIA_BETA35_DOOR_HALF_SERVER_VALIDATION_SUMMARY outcome=GREEN rows=8 green=8 red=0 spruceUpperServerRejectRowsBefore=20 spruceUpperServerRejectRowsAfter=0 spruceLowerServerRejectRowsBefore=1 spruceLowerServerRejectRowsAfter=0 acaciaUpperServerRejectRowsBefore=2 acaciaUpperServerRejectRowsAfter=0 beforeFailureLayer=REGULAR_DOOR_SERVER_SHIFTED_VALIDATION_GAP afterFailureLayer=NONE openState=JULIA_BETA35_DOOR_HALF_OPEN_STATE_GREEN negativeBoundary=JULIA_BETA35_DOOR_HALF_SERVER_NEGATIVE_GREEN slabJumpStatus=SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false`

## Scope

Half-targeting is structurally normal because vanilla doors are two blocks. This slice fixes inconsistent server rejection and paired-state blinking in automated proof only. Julia live acceptance is still required.

The slab jump remains `SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE`; no broad slab `dy=-1` lane grammar was added. Release audit remains paused. No release tag was moved.
