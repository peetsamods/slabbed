# Beta 3.5 Birch Fence Variant Fix

Narrow production fix adding `minecraft:birch_fence` to the proven Beta 3.5 fence/wall variant allowlist.

Operating base: HEAD `77c11e0` / `save/beta35-birch-fence-variant-red` on `integrate/phase19-into-side-slab-top-support`.

Evidence folder: `tmp/beta35-birch-fence-variant-fix-77c11e0/`.

Gate: `-Dslabbed.beta35BirchFenceVariantRed=true`

Markers:

- `JULIA_BETA35_BIRCH_FENCE_VARIANT_GREEN`
- `JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=GREEN failureLayer=NONE`

No release audit was run. No release tag was moved.

## Scope

Production fix only in `SlabSupport.isBeta35FenceWallVariantContactObject`:

- Added: `minecraft:birch_fence`
- Already present: `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, `minecraft:cobblestone_wall`
- Not added: any other wood fence, all `FenceBlock`, all `WallBlock`, all `PaneBlock`, `glass_pane`

`OffsetBlockStateModel.emitQuads` was **not modified** — it already uses `SlabSupport.isBeta35FenceWallVariantContactObject(state)` as its inner guard, so adding `BIRCH_FENCE` to the allowlist automatically passes through render-quad dy for `birch_fence`.

`SlabSupportStateMixin` was **not modified** — it already uses the same helper for outline/raycast/collision shape offset.

## Fix

**File**: `src/main/java/com/slabbed/util/SlabSupport.java`

**Before**:
```java
public static boolean isBeta35FenceWallVariantContactObject(BlockState state) {
    return state != null
            && (state.isOf(Blocks.OAK_FENCE)
                    || state.isOf(Blocks.SPRUCE_FENCE)
                    || state.isOf(Blocks.NETHER_BRICK_FENCE)
                    || state.isOf(Blocks.COBBLESTONE_WALL));
}
```

**After**:
```java
public static boolean isBeta35FenceWallVariantContactObject(BlockState state) {
    return state != null
            && (state.isOf(Blocks.OAK_FENCE)
                    || state.isOf(Blocks.BIRCH_FENCE)
                    || state.isOf(Blocks.SPRUCE_FENCE)
                    || state.isOf(Blocks.NETHER_BRICK_FENCE)
                    || state.isOf(Blocks.COBBLESTONE_WALL));
}
```

## Result

Focused proof outcome: GREEN.

```
JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=GREEN rows=2 greenAllowlisted=2
  variantCoverageGap=0 modelRenderGap=0 placementFailure=0
  oakFenceClassification=GREEN_ALLOWLISTED birchFenceClassification=GREEN_ALLOWLISTED
  failureLayer=NONE productionFixImplemented=true releaseAudit=NOT_RUN
  releaseTagMoved=false canonicalCheckoutModified=false
JULIA_BETA35_BIRCH_FENCE_VARIANT_GREEN outcome=GREEN
  variants=minecraft:oak_fence,minecraft:birch_fence failureLayer=NONE
  productionFixImplemented=true
```

### oak_fence control

- `inFenceWallAllowlist=yes`, `supportDy=-1.000000`, `objectDy=-1.500000`
- `actualModelAppliedDy=-1.500000`, `totalAppliedDy=-9.000000`, `renderDyApplied=yes`
- `shapeContactGap=0.000000`, `shapeTriadCoLocated=yes`
- `classification=GREEN_ALLOWLISTED`, `failureLayer=NONE`

### birch_fence (previously RED, now GREEN)

- `inFenceWallAllowlist=yes`, `supportDy=-1.000000`, `objectDy=-1.500000`
- `actualModelAppliedDy=-1.500000`, `totalAppliedDy=-9.000000`, `renderDyApplied=yes`
- `shapeContactGap=0.000000`, `shapeTriadCoLocated=yes`
- `placementResult=Success`, `survivalResult=survived`
- `classification=GREEN_ALLOWLISTED`, `failureLayer=NONE`

Previous result: `VARIANT_FAMILY_COVERAGE_GAP`, `inFenceWallAllowlist=no`, `renderDyApplied=no`, `shapeContactGap=1.500000`.

## Current fence/wall green set

All five variants proven across shape-triad and render-quad dy:

- `minecraft:oak_fence` — `GREEN_ALLOWLISTED`
- `minecraft:birch_fence` — `GREEN_ALLOWLISTED` (new)
- `minecraft:spruce_fence` — `GREEN_RENDER_DY_APPLIED`
- `minecraft:nether_brick_fence` — `GREEN_RENDER_DY_APPLIED`
- `minecraft:cobblestone_wall` — `GREEN_RENDER_DY_APPLIED`

`minecraft:glass_pane` and pane behavior remain `NOT_COVERED`. Other wood fences (`jungle_fence`, `acacia_fence`, `dark_oak_fence`, `mangrove_fence`, `cherry_fence`, `bamboo_fence`, `crimson_fence`, `warped_fence`) are not yet covered.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava` → `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35BirchFenceVariantRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; GREEN markers emitted
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceModelRenderRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=GREEN`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallVariantCoverage=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceFamilyLiveRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_FENCE_FAMILY_SUMMARY outcome=GREEN`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=27 contactGap=0`
- `./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`
- `git diff --check` → clean

## Release status

**Beta 3.5 release remains BLOCKED.**

Remaining unresolved live failures from Julia's session at `gitHead=a891ba6`:

- `minecraft:birch_trapdoor` — not yet covered (no RED proof yet)
- `minecraft:spruce_door` — not yet covered (no RED proof yet)
- `minecraft:birch_sign` — not yet covered (no RED proof yet)
- `minecraft:anvil` — in allowlist; live evidence is inspect-only; placement-event capture still needed

No release audit was run. No release tag was moved.

## Out of scope

- Other wood fence variants (jungle, acacia, dark oak, mangrove, cherry, bamboo, crimson, warped).
- All `FenceBlock`, all `WallBlock`, all `PaneBlock` expansion.
- Pane support.
- Trapdoor, door, sign, lantern, chain, end rod, redstone, rail work.
- Release audit.
- Release tag movement.
- Version, changelog, or release metadata.
