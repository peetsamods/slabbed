# Beta 3.5 Fence Wall Variant Coverage Fix

Implementation slice for the fence-family `VARIANT_COVERAGE_GAP` captured at `c570299` / `save/beta35-fence-family-live-red`.

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-fence-family-worktree`

Branch: `work/beta35-fence-family-live-red`

Base: `c570299` / `save/beta35-fence-family-live-red`

Evidence folder: `tmp/beta35-fence-wall-variant-coverage-fix-c570299`

Focused gate: `-Dslabbed.beta35FenceWallVariantCoverage=true`

Markers:

- `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_GREEN`
- `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY`
- `JULIA_BETA35_FENCE_FAMILY_ROW`

## Scope

The fix extends the existing `minecraft:oak_fence` lowered contact/raycast/collision treatment to the explicitly proven missing variants only:

- `minecraft:spruce_fence`
- `minecraft:nether_brick_fence`
- `minecraft:cobblestone_wall`

`minecraft:glass_pane` and all pane behavior remain not covered. No door, trapdoor, sign, lantern, chain, end rod, redstone, rail, special-fullblock, release, version, or changelog files were edited.

## Result

Outcome: GREEN.

Previous failure layer: `VARIANT_COVERAGE_GAP`.

New failure layer: `NONE`.

Focused summary:

`JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN rows=16 greenLiveLike=15 greenSimplifiedOnly=1 contactGap=0 triadMismatch=0 collisionShapeRisk=0 connectionShapeRisk=0 placementFailure=0 survivalFailure=0 variantCoverageGap=0 needsImplementation=0 oakFenceClassification=GREEN_LIVE_LIKE spruceFenceClassification=GREEN_LIVE_LIKE netherBrickFenceClassification=GREEN_LIVE_LIKE cobblestoneWallClassification=GREEN_LIVE_LIKE glassPane=NOT_COVERED previousFailureLayer=VARIANT_COVERAGE_GAP failureLayer=NONE productionFixImplemented=true releaseAudit=NOT_RUN releaseTagMoved=false canonicalCheckoutModified=false`

## Configurations Tested

The focused proof tested `isolated`, `one_neighbor`, `two_neighbor`, and `beside_lowered_fullblock` configurations for:

- `minecraft:oak_fence`
- `minecraft:spruce_fence`
- `minecraft:nether_brick_fence`
- `minecraft:cobblestone_wall`

All target rows place and survive. The three newly fixed variants now report `contactGap=0.000000`, non-empty lowered raycast bounds co-located with outline/model/collision, `triadCoLocated=yes`, and `collisionCoLocated=yes`.

## Current Fence Wall Set

Current green set for this focused category:

- `minecraft:oak_fence`
- `minecraft:spruce_fence`
- `minecraft:nether_brick_fence`
- `minecraft:cobblestone_wall`

The prior oak-fence green was valid but incomplete. It covered the representative and did not prove variant breadth.

## Notes

The implementation is an explicit named allowlist, not an all-`FenceBlock`, all-`WallBlock`, all-`PaneBlock`, or global solidity/sturdy-face change.

Validation passed:

- `./gradlew --no-daemon compileJava compileGametestJava`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallVariantCoverage=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceFamilyLiveRed=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `./gradlew --no-daemon runClientGameTest --console plain`

No release audit was run. No release tag was moved. Canonical checkout was not modified.
