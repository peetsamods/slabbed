# Beta 3.5 Fence Model Render Fix

Production model render fix for the Beta 3.5 fence/wall variant `MODEL_RENDER_GAP` identified at HEAD `67ba365` / `save/beta35-fence-model-render-red`.

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`

Branch: `integrate/phase19-into-side-slab-top-support`

Base: `67ba365` / `save/beta35-fence-model-render-red`

Evidence folder: `tmp/beta35-fence-model-render-fix-67ba365`

Gate: `-Dslabbed.beta35FenceModelRenderRed=true`

Markers:

- `JULIA_BETA35_FENCE_MODEL_RENDER_GREEN`
- `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=GREEN failureLayer=NONE`

No release audit was run. No release tag was moved. Pane support was not added.

## Scope

Production fix only on the model render path for the four proven Beta 3.5 fence/wall variants:

- `minecraft:oak_fence`
- `minecraft:spruce_fence`
- `minecraft:nether_brick_fence`
- `minecraft:cobblestone_wall`

`minecraft:glass_pane` and all pane behavior remain not covered. No shape path, door, trapdoor, sign, lantern, chain, end rod, redstone, rail, special-fullblock, release, version, or changelog files were edited.

## Fix

**File**: `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java`

**Before**:
```java
if (dy != 0.0f) {
    // Prevent visual connection offsets for fences/walls/panes
    if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof PaneBlock) {
        dy = 0.0f;
    }
}
```

**After**:
```java
if (dy != 0.0f) {
    // Prevent visual connection offsets for fences/walls/panes,
    // except for the explicitly proven Beta 3.5 fence/wall variants.
    if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof PaneBlock) {
        if (!SlabSupport.isBeta35FenceWallVariantContactObject(state)) {
            dy = 0.0f;
        }
    }
}
```

`SlabSupport.isBeta35FenceWallVariantContactObject` covers exactly `Blocks.OAK_FENCE`, `Blocks.SPRUCE_FENCE`, `Blocks.NETHER_BRICK_FENCE`, and `Blocks.COBBLESTONE_WALL`. All other `FenceBlock`, `WallBlock`, and `PaneBlock` instances remain at `dy = 0.0f` exactly as before.

The shape path (`SlabSupportStateMixin`) was already correct. No `SlabSupport.java` or `SlabSupportStateMixin.java` change is needed for this fix.

The proof method `runBeta35FenceModelRenderRedProof` was also updated to emit `JULIA_BETA35_FENCE_MODEL_RENDER_GREEN` when outcome is GREEN, and `productionFixImplemented=true` in the summary.

## Result

Outcome: GREEN.

Previous failure layer: `MODEL_RENDER_GAP`.

New failure layer: `NONE`.

```
JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=GREEN rows=4 modelRenderGap=0 greenRenderDyApplied=4
  modelDyTraceNotObserved=0 placementFailure=0 supportDyZero=0
  oakFenceClassification=GREEN_RENDER_DY_APPLIED spruceFenceClassification=GREEN_RENDER_DY_APPLIED
  netherBrickFenceClassification=GREEN_RENDER_DY_APPLIED cobblestoneWallClassification=GREEN_RENDER_DY_APPLIED
  glassPane=NOT_COVERED previousFailureLayer=OBJECT_MODEL_BOTTOM_PROXY_GAP failureLayer=NONE
  productionFixImplemented=true releaseAudit=NOT_RUN releaseTagMoved=false canonicalCheckoutModified=false
JULIA_BETA35_FENCE_MODEL_RENDER_GREEN outcome=GREEN
  variants=minecraft:oak_fence,minecraft:spruce_fence,minecraft:nether_brick_fence,minecraft:cobblestone_wall
  glass_pane=NOT_COVERED failureLayer=NONE productionFixImplemented=true
```

All four variants now show:

- `emitCalls=6`
- `appliedCalls=6`
- `totalAppliedDy=-9.000000`
- `actualModelAppliedDy=-1.500000`
- `renderDyApplied=yes`
- `shapeContactGap=0.000000`
- `shapeTriadCoLocated=yes`
- `classification=GREEN_RENDER_DY_APPLIED`

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava` → `BUILD SUCCESSFUL`.
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceModelRenderRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; GREEN markers emitted as documented above.
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallVariantCoverage=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN`.
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceFamilyLiveRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_FENCE_FAMILY_SUMMARY outcome=GREEN`.
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=27 contactGap=0`.
- `./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`.
- `git diff --check` → clean.

## Current Fence Wall Set

All four variants are now proven across both shape-triad and render-quad dy probes:

- `minecraft:oak_fence` — `GREEN_RENDER_DY_APPLIED`
- `minecraft:spruce_fence` — `GREEN_RENDER_DY_APPLIED`
- `minecraft:nether_brick_fence` — `GREEN_RENDER_DY_APPLIED`
- `minecraft:cobblestone_wall` — `GREEN_RENDER_DY_APPLIED`

`minecraft:glass_pane` and pane behavior remain out of scope / `NOT_COVERED`.

## Out of scope

- Pane support.
- Other fence/wall variants not in the allowlist.
- New object support.
- Special-fullblock work.
- Door, trapdoor, sign, lantern, chain, end rod, redstone, rail work.
- Release audit.
- Release tag movement.
- Version, changelog, or release metadata.

## Birch fence RED confirmation (2026-05-12)

The allowlist at `isBeta35FenceWallVariantContactObject` is confirmed too narrow for wood fence families by the focused birch_fence variant RED proof at `4f09773` / `save/beta35-birch-fence-variant-red`. Gate: `-Dslabbed.beta35BirchFenceVariantRed=true`. `minecraft:birch_fence` places and survives but reports `inFenceWallAllowlist=no`, `renderDyApplied=no`, `shapeContactGap=1.500000`, `classification=VARIANT_FAMILY_COVERAGE_GAP`. The four-variant fix here remains correct for its declared scope; the next slice should expand the allowlist to include `birch_fence` narrowly. See `docs/beta35-birch-fence-variant-red.md`.
