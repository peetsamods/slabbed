# Beta 3.5 Common Object Compatibility Audit

## Scope

Audit-only representative matrix for Julia's expanded Beta 3.5 common-object scope.

Operating base: `e8f5fdb` / `save/beta35-scoped-release-readiness-audit`

Gate: `-Dslabbed.beta35CommonObjectCompatibilityAudit=true`

Evidence folder: `tmp/beta35-common-object-compatibility-audit-e8f5fdb`

No production behavior fix was implemented. No release audit was run. No release tag was moved.

## Current Proven Green Set

- `minecraft:torch` / floor torch
- `minecraft:candle`
- `minecraft:flower_pot`

These remain green on the audited slab-supported rows:

- `PLAIN_BOTTOM_DY_MINUS_HALF`
- `LOWERED_BOTTOM_DY_MINUS_ONE`

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS

## Matrix Summary

Summary marker:

Original audit summary marker:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=11 placementFailure=0 survivalFailure=0 contactGap=12 triadMismatch=0 collisionShapeRisk=1 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=1 lantern=NOT_AUDITED_CEILING_HANGING_CATEGORY chain=NOT_AUDITED_CEILING_HANGING_CATEGORY redstone_wire=NOT_AUDITED_SPECIAL_FLOOR_LOGIC rail=NOT_AUDITED_SPECIAL_FLOOR_LOGIC releaseAudit=NOT_RUN releasePrep=PAUSED`

Post crafting-table contact fix summary marker:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=13 placementFailure=0 survivalFailure=0 contactGap=8 triadMismatch=2 collisionShapeRisk=1 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=1 lantern=NOT_AUDITED_CEILING_HANGING_CATEGORY chain=NOT_AUDITED_CEILING_HANGING_CATEGORY redstone_wire=NOT_AUDITED_SPECIAL_FLOOR_LOGIC rail=NOT_AUDITED_SPECIAL_FLOOR_LOGIC releaseAudit=NOT_RUN releasePrep=PAUSED`

| Representative | Family | Vanilla full block | Plain bottom slab `supportDy=-0.5` | Lowered bottom slab `supportDy=-1.0` | Classification |
| --- | --- | --- | --- | --- | --- |
| `minecraft:torch` | floor/top decor control | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:candle` | floor/top decor control | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:flower_pot` | floor/top decor control | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:crafting_table` | ordinary full block | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:furnace` | ordinary full block | GREEN | `contactGap=0.000000`, `TRIAD_MISMATCH` | `contactGap=0.000000`, `TRIAD_MISMATCH` | `TRIAD_MISMATCH` |
| `minecraft:oak_fence` | partial collision block | `COLLISION_SHAPE_RISK` | `CONTACT_GAP=0.500000` | `CONTACT_GAP=1.000000` | `CONTACT_GAP` plus category risk |
| `minecraft:oak_trapdoor` | attachment / hinge block | `NEEDS_CATEGORY_SLICE` | `CONTACT_GAP=0.500000` | `CONTACT_GAP=1.000000` | `CONTACT_GAP` plus category risk |
| `minecraft:oak_door` | multipart block | `MULTIPART_RISK` | `CONTACT_GAP=0.500000` | `CONTACT_GAP=1.000000` | `CONTACT_GAP` plus multipart risk |
| `minecraft:oak_sign` | renderer / block entity standing sign | `RENDERER_SPECIAL_CASE` | `CONTACT_GAP=0.500000` | `CONTACT_GAP=1.000000` | `CONTACT_GAP` plus renderer risk |

## Grouped Findings

The expanded matrix is not release-green for common objects beyond the current floor/top decor controls.

All required representatives placed on the audited rows. No row produced `PLACEMENT_FAILURE` or `SURVIVAL_FAILURE`.

The shared slab-supported failure layer is contact alignment:

- `crafting_table` is now GREEN for the ordinary-full-block representative contact slice. It places and survives on vanilla, plain bottom slab support, and lowered bottom slab support; slab-supported rows now report `contactGap=0.000000` and `triadCoLocated=yes`.
- `furnace` inherited the ordinary full-block contact dy (`contactGap=0.000000`) but is not release-green because slab-supported rows now classify as `TRIAD_MISMATCH`. It needs a separate furnace/block-entity triad slice if Julia wants it in the Beta 3.5 minimum set.
- partial/attachment/multipart/render-special representatives show the same slab-supported contact-gap pattern before their family-specific risks can honestly be called green.
- `oak_fence` also needs a collision/connection category slice.
- `oak_trapdoor` also needs an attachment/hinge orientation/open-closed category slice.
- `oak_door` also needs a multipart two-block category slice.
- standing `oak_sign` remains the known renderer/block-entity special case.

Ceiling/hanging and special floor-logic categories were not audited in this slice:

- `minecraft:lantern`: `NOT_AUDITED_CEILING_HANGING_CATEGORY`
- `minecraft:chain`: `NOT_AUDITED_CEILING_HANGING_CATEGORY`
- `minecraft:redstone_wire`: `NOT_AUDITED_SPECIAL_FLOOR_LOGIC`
- `minecraft:rail`: `NOT_AUDITED_SPECIAL_FLOOR_LOGIC`

## Release Decision Point

A. Stop here and release with documented object limitations:

Not recommended unless Julia accepts that Beta 3.5 only promises `floor_torch + candle + flower_pot` and explicitly excludes ordinary full blocks, fences, trapdoors, doors, standing signs, ceiling/hanging objects, rails, and redstone.

B. Fix one more high-value family before release:

The first ordinary full-block representative (`minecraft:crafting_table`) is now fixed. Recommended next implementation category, if Julia wants one more common-object family, is either furnace/block-entity triad parity or partial collision blocks (`minecraft:oak_fence`). Do not bundle trapdoors, doors, signs, hanging objects, rails, redstone, or all-item support.

C. Defer Beta 3.5 release until common object matrix minimum set is green:

Required if Julia wants expanded common-object compatibility to be a Beta 3.5 release criterion.

## Status

Release remains paused pending Julia decision.

Post-fix status: `minecraft:crafting_table` is GREEN; `minecraft:furnace` has contact fixed but remains `TRIAD_MISMATCH`; `minecraft:oak_fence`, `minecraft:oak_trapdoor`, `minecraft:oak_door`, and standing `minecraft:oak_sign` remain separate categories. Release remains paused pending Julia decision.
