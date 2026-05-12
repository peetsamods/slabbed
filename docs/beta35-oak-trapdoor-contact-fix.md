# Beta 3.5 Oak Trapdoor Contact Fix

Narrow implementation slice for `minecraft:oak_trapdoor` only.

Operating base: `2300229` / `save/beta35-trapdoor-door-category-audit`

Gate: `-Dslabbed.beta35OakTrapdoorContact=true`

Evidence folder: `tmp/beta35-oak-trapdoor-contact-fix-2300229`

## Result

`minecraft:oak_trapdoor` is GREEN for the audited bottom-half trapdoor representative on valid slab-supported surfaces.

Focused proof summary:

`JULIA_BETA35_OAK_TRAPDOOR_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:oak_trapdoor family=interactive_hinge supportRows=2 expectedSupportRowsGreen=true vanillaFullBlockControl=NOT_RELEASE_CRITERION_FOR_SLAB_CONTACT currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor oak_door=UNCHANGED_DEFERRED_MULTIPART_RISK signs=NOT_TOUCHED lanterns=NOT_TOUCHED chains=NOT_TOUCHED redstone=NOT_TOUCHED rails=NOT_TOUCHED releaseAudit=NOT_RUN releasePrep=PAUSED`

## Implementation Scope

- `SlabSupport` now has an `minecraft:oak_trapdoor` bottom-half-only contact dy rule over valid lowered bottom-slab support truth: `objectDy = supportDy - 0.5`.
- `SlabSupportStateMixin` now gives lowered bottom-half `minecraft:oak_trapdoor` an outline-backed raycast basis when the vanilla raycast shape is empty, preserving model/outline/raycast co-location.
- Collision was not directly offset by a new trapdoor collision branch; the focused proof shows collision bounds remain coherent with the existing outline/model behavior.
- This is not an oak door fix, not all trapdoors, not signs, not lanterns/chains, not redstone/rails, not all-item support, and not a global sturdy-face or solidity change.

## Proof Facts

- Plain bottom slab support: `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionBounds` co-located, `openCloseResult=Success->Success`, `classification=GREEN_ALREADY_INHERITS`.
- Lowered bottom slab support: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionBounds` co-located, `openCloseResult=Success->Success`, `classification=GREEN_ALREADY_INHERITS`.
- The vanilla full-block control still has an empty native raycast in the audit harness and is not a release criterion for slab contact.

## Current Green Set

- `minecraft:torch`
- `minecraft:candle`
- `minecraft:flower_pot`
- `minecraft:crafting_table`
- `minecraft:furnace`
- `minecraft:oak_fence`
- `minecraft:oak_trapdoor`

`minecraft:oak_door` remains unchanged and deferred as a separate multipart risk with `DOOR_MULTIPART_CONTACT_GAP`.

Signs, lanterns, chains, redstone, and rails remain not covered by this slice.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35OakTrapdoorContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35TrapdoorDoorAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS

No release audit ran. No release tag moved. Release remains paused pending Julia decision on whether door is required before release.
