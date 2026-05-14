# Beta 3.5 Regular Door Owner Fix

Slice base: `23b562c` / `save/beta35-trapdoor-server-validation`.

Evidence folder: `tmp/beta35-door-owner-slab-jump-fix-23b562c/`.

## Why

Julia's 2026-05-14 6:30 live source truth after the trapdoor server-validation savepoint kept trapdoors, chains, buttons, and torches much improved, but regular doors still would not target reliably. The live extract showed support steal with visible door geometry under the crosshair:

`visibleObjectState=Block{minecraft:spruce_door}[half=lower,...] supportCandidateState=Block{minecraft:stone_slab}[type=bottom] supportDy=-0.500000 targetDy=-0.500000 objectDy=-1.000000 rayIntersectsVisibleObject=true finalDecision=scan-side-slab-fired targetOwner=support_slab classification=HIT_ACCEPTANCE_SUPPORT_STEAL failureLayer=REGULAR_DOOR_VISIBLE_OWNER_SUPPORT_STEAL`

Support-steal source counts were `minecraft:spruce_door=23`, `minecraft:acacia_door=3`, and `minecraft:birch_stairs=5`.

## Change

- `SlabSupport.isBeta35SlabHeightVisibleOwnerObject(...)` now includes regular `DoorBlock` halves only when they are in a lowered Beta 3.5 context.
- `GameRendererCrosshairRetargetMixin` preserves the visible door owner before slab/support rescue when the ray intersects the corrected shifted outline/raycast shape.
- `SlabSupportStateMixin` applies the existing shifted door raycast fallback to regular doors, covering lower and upper halves.
- `Beta35SlabHeightHitAcceptanceRecorder` classifies regular-door owner green rows and maps remaining door support steals to `REGULAR_DOOR_VISIBLE_OWNER_SUPPORT_STEAL`.

This is not held-item based. It does not make all doors own unconditionally, does not widen global hit tolerance, does not lower global collision, does not add solidity or sturdy-face lies, and does not claim all-item gameplay support.

## Proof

Focused proof flag:

`-Dslabbed.beta35RegularDoorOwnerFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true`

Before marker:

`JULIA_BETA35_REGULAR_DOOR_OWNER_RED ... visibleObjectState=Block{minecraft:spruce_door}[facing=south,half=lower,hinge=right,open=true,powered=false] ... supportDy=-0.500000 targetDy=-0.500000 objectDy=-1.000000 rayIntersectsVisibleObject=true finalDecision=scan-side-slab-fired targetOwner=support_slab classification=HIT_ACCEPTANCE_SUPPORT_STEAL failureLayer=REGULAR_DOOR_VISIBLE_OWNER_SUPPORT_STEAL`

After coverage:

- Spruce lower open door, held torch.
- Spruce lower open door, held stone slab.
- Spruce upper open door, held stone slab.
- Spruce lower closed door, held torch.
- Acacia lower open door, held torch.
- Acacia lower closed door, held stone slab.

Green summary:

`JULIA_BETA35_REGULAR_DOOR_OWNER_SUMMARY outcome=GREEN rows=6 green=6 red=0 doorSupportStealRowsBefore=26 doorSupportStealRowsAfter=0 spruceDoorSupportStealRowsBefore=23 spruceDoorSupportStealRowsAfter=0 acaciaDoorSupportStealRowsBefore=3 acaciaDoorSupportStealRowsAfter=0 regularDoorOwnerGreenRows=6 stairRowsDeferred=5 stairRowsFixed=0 classification=REGULAR_DOOR_OWNER_GREEN failureLayer=NONE releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false`

Stairs remain explicit deferred scope: `STAIRS_VISIBLE_OWNER_DEFERRED`, not fixed or claimed here.

## Scope

This is automated-proof green only. Julia live acceptance is still required. Release audit remains paused. No release tag was moved.

## 2026-05-14 Continuation: Door Half Server Validation

Continuation base: `e23c62a` / `save/beta35-door-owner-slab-jump`.

Julia's 7:20 live source truth after the visible-owner fix showed support steal was gone for regular doors (`HIT_ACCEPTANCE_SUPPORT_STEAL=0`) and regular-door owner rows were frequent, but server validation still rejected lowered door hits. The live reject distribution was `spruce_door=21`, `acacia_door=2`, mostly upper-half rows, with `targetDy=-1.000000`, `shiftedValidationCenter=null`, and `failureLayer=HIT_ACCEPTANCE_SERVER_REJECT`.

This continuation narrows the fix to regular `DoorBlock` server validation and paired half durability:

- regular door contact dy now applies to all regular `DoorBlock` halves, not only oak;
- server shifted validation accepts lowered regular doors only when the paired half exists and is consistent;
- the shifted hit still has to remain inside the vanilla component tolerance;
- upper and lower half clicks both toggle the paired door durably.

Focused proof flag:

`-Dslabbed.beta35DoorHalfServerValidationFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true`

Green summary:

`JULIA_BETA35_DOOR_HALF_SERVER_VALIDATION_SUMMARY outcome=GREEN rows=8 green=8 red=0 spruceUpperServerRejectRowsBefore=20 spruceUpperServerRejectRowsAfter=0 spruceLowerServerRejectRowsBefore=1 spruceLowerServerRejectRowsAfter=0 acaciaUpperServerRejectRowsBefore=2 acaciaUpperServerRejectRowsAfter=0 beforeFailureLayer=REGULAR_DOOR_SERVER_SHIFTED_VALIDATION_GAP afterFailureLayer=NONE openState=JULIA_BETA35_DOOR_HALF_OPEN_STATE_GREEN negativeBoundary=JULIA_BETA35_DOOR_HALF_SERVER_NEGATIVE_GREEN slabJumpStatus=SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false`

Half-targeting is structurally normal because vanilla doors are two blocks. The inconsistent rejection/blinking layer is fixed in automated proof; Julia live acceptance remains required.
