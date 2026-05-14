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
