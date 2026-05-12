# Beta 3.5 Oak Fence Contact Risk

## Scope

Single representative category slice for `minecraft:oak_fence` partial-collision/contact behavior.

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-object-compat-worktree`

Branch: `work/beta35-common-object-compat`

Starting HEAD: `a6400ca` / `save/beta35-furnace-triad`

Evidence folder: `tmp/beta35-oak-fence-contact-risk-a6400ca`

No release audit was run. No release tag was moved. Trapdoor, door, and sign were not touched.

## Previous Classification

`minecraft:oak_fence` was `CONTACT_GAP` plus `COLLISION_SHAPE_RISK`.

Measured pre-slice matrix state:

- Plain bottom slab support: `contactGap=0.500000`
- Lowered bottom slab support: `contactGap=1.000000`
- Placement: GREEN
- Survival: GREEN
- Category risk: partial collision and connection-sensitive shape behavior

## Fix

`SlabSupport.getYOffsetInner(...)` now has an oak-fence-only contact dy rule. It applies only when `minecraft:oak_fence` is placed above bottom slab support with lowered Slabbed support truth, and computes `objectDy = supportDy - 0.5`.

`SlabSupportStateMixin` now aligns lowered oak-fence outline, raycast, and collision shapes to the fence collision body for that same oak-fence-only path. This keeps the visible/contact body, hit shape, raycast basis, and collision body co-located for the representative no-neighbor oak fence rows.

This did not add all-fence, wall, pane, trapdoor, door, sign, all-partial-block, all-item, global sturdy-face, or global solidity support.

## Proof

Focused proof command:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta35OakFenceContact=true" ./gradlew --no-daemon runClientGameTest --console plain`

Result: PASS.

Focused marker:

`JULIA_BETA35_OAK_FENCE_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:oak_fence family=partial_collision rows=3 expectedRowsGreen=true ... releaseAudit=NOT_RUN releasePrep=PAUSED`

Key slab-supported rows:

- Plain bottom support: `supportDy=-0.500000`, `objectDy=-1.000000`, `supportVisibleTopY=-55.000000`, `objectModelBottomY=-55.000000`, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes`, `survival=SURVIVAL_GREEN`
- Lowered bottom support: `supportDy=-1.000000`, `objectDy=-1.500000`, `supportVisibleTopY=-55.500000`, `objectModelBottomY=-55.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes`, `survival=SURVIVAL_GREEN`

Common-object matrix command:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`

Result: PASS.

Post-fix matrix summary:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=18 placementFailure=0 survivalFailure=0 contactGap=6 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=1 ... releaseAudit=NOT_RUN releasePrep=PAUSED`

Regression command:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`

Result: PASS. `minecraft:torch`, `minecraft:candle`, and `minecraft:flower_pot` remained GREEN; standing `minecraft:oak_sign` remained the known separate `CONTACT_GAP`.

Default suite:

`./gradlew --no-daemon runClientGameTest --console plain`

Result: PASS.

## Status

`minecraft:oak_fence` is GREEN for the oak-fence-only partial-collision representative.

Remaining common-object risks are unchanged:

- `minecraft:oak_trapdoor`: `CONTACT_GAP` plus `NEEDS_CATEGORY_SLICE`
- `minecraft:oak_door`: `CONTACT_GAP` plus `MULTIPART_RISK`
- standing `minecraft:oak_sign`: `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`

Release remains paused.
