# Beta 3.5 Fence Family Live RED

Proof/classification slice for Julia's report that fence behavior is still not fixed despite the prior `minecraft:oak_fence` green matrix.

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-fence-family-worktree`

Branch: `work/beta35-fence-family-live-red`

Base: `0ccbc7f` / `save/beta35-special-fullblock-compat-integrated`

Evidence folder: `tmp/beta35-fence-family-live-red-0ccbc7f`

Gate: `-Dslabbed.beta35FenceFamilyLiveRed=true`

Markers:

- `JULIA_BETA35_FENCE_FAMILY_LIVE_RED`
- `JULIA_BETA35_FENCE_FAMILY_ROW`
- `JULIA_BETA35_FENCE_FAMILY_SUMMARY`

No production behavior fix was implemented. No release audit was run. No release tag was moved. Canonical checkout files were not modified.

Validation passed:

- `./gradlew --no-daemon compileJava compileGametestJava`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceFamilyLiveRed=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `./gradlew --no-daemon runClientGameTest --console plain`
- `git diff --check`

## Classification

Outcome: RED.

Failure layer: `VARIANT_COVERAGE_GAP`.

Summary marker:

`JULIA_BETA35_FENCE_FAMILY_SUMMARY outcome=RED rows=7 greenSimplifiedOnly=1 greenLiveLike=3 contactGap=0 triadMismatch=0 collisionShapeRisk=0 connectionShapeRisk=0 placementFailure=0 survivalFailure=0 variantCoverageGap=3 needsImplementation=0 oakFenceClassification=GREEN_LIVE_LIKE firstFailureLayer=VARIANT_COVERAGE_GAP previousOakFenceGreenStatus=VALID_SIMPLIFIED_ONLY_LIVE_SUPERSEDED implementationNeeded=true productionFixImplemented=false releaseAudit=NOT_RUN releaseTagMoved=false canonicalCheckoutModified=false`

The prior `minecraft:oak_fence` green is still valid for the simplified isolated oak-fence representative, but it is superseded as a fence-family release claim. The new live-like proof shows connected `minecraft:oak_fence` rows are green, while other cheap family representatives do not inherit the oak-fence-only support path.

## Rows

| Object | Configuration | Classification | Notes |
| --- | --- | --- | --- |
| `minecraft:oak_fence` | `isolated` | `GREEN_SIMPLIFIED_ONLY` | `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, triad/collision co-located |
| `minecraft:oak_fence` | `one_neighbor` | `GREEN_LIVE_LIKE` | east connection true, `contactGap=0.000000`, triad/collision co-located |
| `minecraft:oak_fence` | `two_neighbor` | `GREEN_LIVE_LIKE` | east/west connections true, `contactGap=0.000000`, triad/collision co-located |
| `minecraft:oak_fence` | `beside_lowered_fullblock` | `GREEN_LIVE_LIKE` | east connection true to lowered full block, `contactGap=0.000000`, triad/collision co-located |
| `minecraft:spruce_fence` | `isolated` | `VARIANT_COVERAGE_GAP` | `objectDy=-0.500000`, `contactGap=1.500000`, raycast empty, triad not co-located |
| `minecraft:nether_brick_fence` | `isolated` | `VARIANT_COVERAGE_GAP` | `objectDy=-0.500000`, `contactGap=1.500000`, raycast empty, triad not co-located |
| `minecraft:cobblestone_wall` | `isolated` | `VARIANT_COVERAGE_GAP` | `objectDy=-0.500000`, `contactGap=1.500000`, raycast empty, triad not co-located |

`minecraft:glass_pane` was not included in this first proof because the existing oak-fence helper did not already support pane-specific connection semantics cheaply.

## Answered Audit Questions

The exact behavior the previous proof failed to cover was fence-family breadth and connected live-like states. The previous proof covered a single `minecraft:oak_fence` representative in the common-object matrix.

Fence placement succeeds in every tested row. Oak fence survives and keeps model, outline, raycast, and collision co-located across isolated, one-neighbor, two-neighbor, and beside-lowered-fullblock configurations. Spruce fence, nether brick fence, and cobblestone wall place and survive, but keep the wrong family dy and show a `1.500000` contact gap plus empty raycast.

The issue captured here is not oak-fence connection/collision behavior in the tested live-like fixture. It is that the production support path is `Blocks.OAK_FENCE`-only, so other fence-family and wall-family representatives are uncovered.

## Next Slice

Recommended next implementation slice, if Julia authorizes it: expand the existing oak-fence contact/raycast/collision treatment into one explicit fence-family/wall-family allowlist, starting with `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall`; rerun this gate plus the existing common-object matrix and default gametest. Do not bundle panes unless Julia wants pane coverage in the same category.

## Variant Coverage Fix Follow-up

Follow-up implementation at `c570299` / `save/beta35-fence-family-live-red` was run in this same worktree.

Focused gate: `-Dslabbed.beta35FenceWallVariantCoverage=true`; markers `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_GREEN` and `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY`.

The prior `minecraft:oak_fence` green remains valid but was incomplete as a family claim. The variant coverage gap is now fixed for the explicitly proven missing variants: `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall`.

Focused proof result: GREEN, `failureLayer=NONE`, `rows=16`, `contactGap=0`, `triadMismatch=0`, `collisionShapeRisk=0`, `connectionShapeRisk=0`, and `variantCoverageGap=0`. Tested configurations were `isolated`, `one_neighbor`, `two_neighbor`, and `beside_lowered_fullblock` for oak fence, spruce fence, nether brick fence, and cobblestone wall.

Current green fence/wall set: `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall`.

`minecraft:glass_pane` and pane behavior remain out of scope / not covered. No release audit was run. No release tag was moved. Canonical checkout was not modified.
