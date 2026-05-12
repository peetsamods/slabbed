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

## Superseded as a release artifact (2026-05-12)

The GREEN result above is **rescinded as a release artifact** by the Beta 3.5 fence false-green Opus audit at `a576fa1` and the follow-up render-quad RED proof. The shape-triad GREEN reflected only `getOutlineShape`, `getRaycastShape`, and `getCollisionShape` — all three forcibly offset by `SlabSupportStateMixin` for the four allowlisted variants. The visible quads come from `OffsetBlockStateModel.emitQuads`, which forces `dy = 0.0f` for every `FenceBlock | WallBlock | PaneBlock`, so the rendered model never shifted. See `docs/beta35-fence-false-green-opus-audit.md` and `docs/beta35-fence-model-render-red.md`. The new gate `-Dslabbed.beta35FenceModelRenderRed=true` reproduces the live-visible gap as `MODEL_RENDER_GAP` for all four allowlisted variants on current HEAD. Release remains blocked; a follow-up production render fix in `OffsetBlockStateModel.emitQuads` is required before this slice can re-enter a release claim.

## Wood fence family scope gap confirmed (2026-05-12)

In addition to the render-quad gap, the four-variant allowlist does not cover the wood fence family: `birch_fence` (Julia's live-tested variant) is not in the allowlist. The focused RED proof at `4f09773` / `save/beta35-birch-fence-variant-red` confirms `VARIANT_FAMILY_COVERAGE_GAP` for `minecraft:birch_fence` independently of the render-quad issue. The current green set (`oak_fence`, `spruce_fence`, `nether_brick_fence`, `cobblestone_wall`) is too exact for vanilla player expectations. See `docs/beta35-birch-fence-variant-red.md`.
