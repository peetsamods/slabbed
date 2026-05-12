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
