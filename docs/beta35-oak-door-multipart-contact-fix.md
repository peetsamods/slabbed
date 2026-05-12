# Beta 3.5 Oak Door Multipart Contact Fix

Narrow implementation slice for `minecraft:oak_door` only.

Base: `d6c10d8` / `save/beta35-oak-trapdoor-contact`.

## Result

`minecraft:oak_door` is GREEN for the audited oak-door multipart representative on valid slab-supported surfaces.

Focused proof gate:

`-Dslabbed.beta35OakDoorContact=true`

Focused proof summary:

`JULIA_BETA35_OAK_DOOR_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:oak_door family=multipart_door supportRows=2 expectedSupportRowsGreen=true vanillaFullBlockControl=NOT_RELEASE_CRITERION_FOR_SLAB_CONTACT currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,oak_door oak_trapdoor=GREEN_UNCHANGED signs=NOT_TOUCHED lanterns=NOT_TOUCHED chains=NOT_TOUCHED redstone=NOT_TOUCHED rails=NOT_TOUCHED releaseAudit=NOT_RUN releasePrep=PAUSED`

## Mechanism

- `SlabSupport` now has a `minecraft:oak_door`-only multipart contact dy rule for lower and upper door halves over valid lowered bottom-slab support truth: `doorDy = supportDy - 0.5`.
- `SlabSupportStateMixin` now gives lowered `minecraft:oak_door` halves an outline-backed raycast basis when the vanilla raycast shape is empty, preserving model/outline/raycast co-location.
- Door bottom/top halves remain present and share coherent dy.
- Door open/close remained GREEN in the focused proof.

This does not claim all doors, signs, lanterns, chains, redstone, rails, all multipart blocks, all objects, or global sturdy-face/solidity behavior.

## Current Green Set

- `minecraft:torch`
- `minecraft:candle`
- `minecraft:flower_pot`
- `minecraft:crafting_table`
- `minecraft:furnace`
- `minecraft:oak_fence`
- `minecraft:oak_trapdoor`
- `minecraft:oak_door`

Signs, lanterns, chains, redstone, and rails remain not covered.

No release audit ran. No release tag moved. Release remains paused pending Julia decision on whether remaining categories are required before release.
