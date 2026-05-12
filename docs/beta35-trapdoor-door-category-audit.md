# Beta 3.5 Trapdoor / Door Category Audit

Audit-only category proof for `minecraft:oak_trapdoor` and `minecraft:oak_door`.

Operating base: `f88afb7` / `save/beta35-oak-fence-contact-integrated`

Gate: `-Dslabbed.beta35TrapdoorDoorAudit=true`

Evidence folder: `tmp/beta35-trapdoor-door-category-audit-f88afb7`

No production behavior changed. No release audit ran. No release tag moved. Signs, lanterns, chains, redstone, and rails were not touched.

## Current Green Set Preserved

- `minecraft:torch`
- `minecraft:candle`
- `minecraft:flower_pot`
- `minecraft:crafting_table`
- `minecraft:furnace`
- `minecraft:oak_fence`

The focused common-object matrix remains passing after this audit slice.

## Trapdoor Classification

`minecraft:oak_trapdoor` places and survives on the audited rows, including slab-supported rows.

Summary marker:

`JULIA_BETA35_TRAPDOOR_DOOR_SUMMARY trapdoorClassification=CONTACT_GAP trapdoorFailureLayer=TRAPDOOR_CONTACT_GAP doorClassification=CONTACT_GAP doorFailureLayer=DOOR_MULTIPART_CONTACT_GAP currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence trapdoorRecommendation=NEXT_SLICE_CATEGORY_SPECIFIC_CONTACT_AND_OPEN_CLOSE_FIX doorRecommendation=DEFER_MULTIPART_CATEGORY_SLICE signs=NOT_TOUCHED lanterns=NOT_TOUCHED chains=NOT_TOUCHED redstone=NOT_TOUCHED rails=NOT_TOUCHED releaseAudit=NOT_RUN releasePrep=PAUSED`

Observed rows:

- vanilla full-block control: placed/survived, `contactGap=0.000000`, but native raycast stayed empty, so the control row still reports `TRIAD_MISMATCH`.
- plain bottom slab support: placed/survived, `supportDy=-0.500000`, `objectDy=-0.500000`, `contactGap=0.500000`, `classification=CONTACT_GAP`.
- lowered bottom slab support: placed/survived, `supportDy=-1.000000`, `objectDy=-0.500000`, `contactGap=1.000000`, `classification=CONTACT_GAP`.

Open/close was exercised with an empty hand and returned success twice. The audited placement produced `half=bottom`, `facing=north`, `open=false` after the two toggles. Top-half trapdoor behavior remains unproven in this slice.

Exact failure layer: `TRAPDOOR_CONTACT_GAP`.

Recommendation: fix trapdoor next only as a narrow category-specific contact/open-close slice. Do not bundle door, sign, lantern, chain, redstone, rail, or all-object support.

## Door Classification

`minecraft:oak_door` places both halves and survives on the audited rows, including slab-supported rows.

Observed rows:

- vanilla full-block control: bottom and top halves appeared, `bottomDy=0.000000`, `topDy=0.000000`, `bottomContactGap=0.000000`, but native raycast stayed empty, so the control row reports `TRIAD_MISMATCH`.
- plain bottom slab support: bottom and top halves appeared, `supportDy=-0.500000`, `bottomDy=-0.500000`, `topDy=-0.500000`, `bottomContactGap=0.500000`, `classification=CONTACT_GAP`.
- lowered bottom slab support: bottom and top halves appeared, `supportDy=-1.000000`, `bottomDy=-0.500000`, `topDy=-0.500000`, `bottomContactGap=1.000000`, `classification=CONTACT_GAP`.

Top/bottom half integrity: both halves appear and share the same dy on the audited rows. The remaining risk is not basic placement or immediate survival; it is slab-supported contact alignment plus multipart/raycast category complexity.

Exact failure layer: `DOOR_MULTIPART_CONTACT_GAP`.

Recommendation: defer door for Beta 3.5 unless Julia explicitly authorizes a separate multipart category slice.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35TrapdoorDoorAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS

Release remains paused pending Julia decision.

## Post-Fix Oak Trapdoor Contact Slice

Follow-up implementation at base `2300229` / `save/beta35-trapdoor-door-category-audit` added `-Dslabbed.beta35OakTrapdoorContact=true` for `minecraft:oak_trapdoor` only.

`minecraft:oak_trapdoor` is now GREEN for the audited bottom-half trapdoor representative on valid slab-supported surfaces. Plain bottom slab support now reports `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `triadCoLocated=yes`, collision bounds co-located, and `openCloseResult=Success->Success`. Lowered bottom slab support now reports `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, collision bounds co-located, and `openCloseResult=Success->Success`.

Focused proof summary:

`JULIA_BETA35_OAK_TRAPDOOR_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:oak_trapdoor family=interactive_hinge supportRows=2 expectedSupportRowsGreen=true vanillaFullBlockControl=NOT_RELEASE_CRITERION_FOR_SLAB_CONTACT currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor oak_door=UNCHANGED_DEFERRED_MULTIPART_RISK signs=NOT_TOUCHED lanterns=NOT_TOUCHED chains=NOT_TOUCHED redstone=NOT_TOUCHED rails=NOT_TOUCHED releaseAudit=NOT_RUN releasePrep=PAUSED`

`minecraft:oak_door` was not implemented in this slice and remains a deferred multipart risk with exact failure layer `DOOR_MULTIPART_CONTACT_GAP`.

Current green set is now `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, and `minecraft:oak_trapdoor`.

No release audit ran. No release tag moved. Signs, lanterns, chains, redstone, and rails were not touched. Release remains paused pending Julia decision on whether door is required before release.

## Post-Fix Oak Door Multipart Contact Slice

Follow-up implementation at base `d6c10d8` / `save/beta35-oak-trapdoor-contact` added `-Dslabbed.beta35OakDoorContact=true` for `minecraft:oak_door` only.

`minecraft:oak_door` is now GREEN for the audited oak-door multipart representative on valid slab-supported surfaces. Plain bottom slab support reports `supportDy=-0.500000`, `bottomDy=-1.000000`, `topDy=-1.000000`, `bottomContactGap=0.000000`, `topAlignment=GREEN`, `triadCoLocated=yes`, `collisionCoLocated=yes`, and `openCloseResult=Success->Success`. Lowered bottom slab support reports `supportDy=-1.000000`, `bottomDy=-1.500000`, `topDy=-1.500000`, `bottomContactGap=0.000000`, `topAlignment=GREEN`, `triadCoLocated=yes`, `collisionCoLocated=yes`, and `openCloseResult=Success->Success`.

Focused proof summary:

`JULIA_BETA35_OAK_DOOR_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:oak_door family=multipart_door supportRows=2 expectedSupportRowsGreen=true vanillaFullBlockControl=NOT_RELEASE_CRITERION_FOR_SLAB_CONTACT currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,oak_door oak_trapdoor=GREEN_UNCHANGED signs=NOT_TOUCHED lanterns=NOT_TOUCHED chains=NOT_TOUCHED redstone=NOT_TOUCHED rails=NOT_TOUCHED releaseAudit=NOT_RUN releasePrep=PAUSED`

Current green set is now `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, `minecraft:oak_trapdoor`, and `minecraft:oak_door`.

This proves only `minecraft:oak_door`, not all doors. Signs, lanterns, chains, redstone, and rails remain not covered. No release audit ran. No release tag moved. Release remains paused pending Julia decision on whether remaining categories are required before release.
