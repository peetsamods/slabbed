# Beta 3.5 Grindstone Contact Fix

Operating base: `805b070` / `save/beta35-anvil-contact`

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

## Scope

This slice fixes only `minecraft:grindstone` contact/orientation behavior on valid slab-supported lowered surfaces.

No lectern, door, trapdoor, sign, lantern, chain, end-rod, redstone, rail, release metadata, or release tag work was included.

## Previous Failure

Baseline special-fullblock matrix rows showed `minecraft:grindstone` placed and survived, but slab-supported rows reported:

- Plain bottom support: `supportDy=-0.500000`, `objectDy=-0.500000`, `contactGap=0.500000`, `triadCoLocated=no`
- Lowered bottom support: `supportDy=-1.000000`, `objectDy=-0.500000`, `contactGap=1.000000`, `triadCoLocated=no`
- Tested state: `Block{minecraft:grindstone}[face=floor,facing=south]`

Failure layer: `CONTACT_GAP`.

## Fix

`Blocks.GRINDSTONE` joined the explicit Beta 3.5 special-fullblock contact dy allowlist and the separate lowered empty-raycast fallback allowlist after focused proof showed the same too-shallow dy plus empty native raycast mechanism as the prior special-shape slices.

`Blocks.GRINDSTONE` also received an exact collision fallback through the existing collision hook so the tested oriented shape reports co-located model, outline, raycast, and collision bounds. This is not broad collision support.

## Proof

Focused gate:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta35GrindstoneContact=true" ./gradlew --no-daemon runClientGameTest --console plain`

Result: PASS.

Summary marker:

`JULIA_BETA35_GRINDSTONE_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:grindstone rows=3 expectedRowsGreen=true testedOrientation=face_floor_facing_south_from_top_use_hit collisionCategory=oriented_special_shape_collision_co_located`

Slab-supported rows report:

- `contactGap=0.000000`
- `triadCoLocated=yes`
- `survivalResult=SURVIVAL_GREEN`
- `finalBlockState=Block{minecraft:grindstone}[face=floor,facing=south]`
- co-located model/outline/raycast/collision bounds

Special-fullblock matrix result after the fix:

`JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY rows=30 greenAlreadyInherits=27 placementFailure=0 survivalFailure=0 contactGap=2 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=0 outOfScopeForBeta35=0`

`minecraft:lectern` remains the remaining interactive block-entity contact slice.

No release audit was run. No release tag was moved. Canonical checkout was not modified.
