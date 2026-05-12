# Beta 3.5 Fence Wall Family Fix

Production fix promoting the Beta 3.5 fence/wall support from exact-block allowlist to the full `FenceBlock` / `WallBlock` family.

Operating base: HEAD `25a6ef6` / `save/beta35-birch-fence-variant-fix` on `integrate/phase19-into-side-slab-top-support`.

Evidence folder: `tmp/beta35-fence-wall-family-fix-25a6ef6/`.

Gate: `-Dslabbed.beta35FenceWallFamilyFix=true`

Markers:

- `JULIA_BETA35_FENCE_WALL_FAMILY_GREEN`
- `JULIA_BETA35_FENCE_WALL_FAMILY_SUMMARY outcome=GREEN failureLayer=NONE`

No release audit was run. No release tag was moved.

## Scope

Production fix only in `SlabSupport.isBeta35FenceWallVariantContactObject`:

- **Before**: exact-block allowlist — `OAK_FENCE`, `BIRCH_FENCE`, `SPRUCE_FENCE`, `NETHER_BRICK_FENCE`, `COBBLESTONE_WALL`
- **After**: class-membership rule — `state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock`
- **Excluded**: `PaneBlock` — remains `NOT_COVERED` / `dy=0`

`OffsetBlockStateModel.emitQuads` was **not modified** — it already uses `SlabSupport.isBeta35FenceWallVariantContactObject(state)` as its inner guard within the `FenceBlock | WallBlock | PaneBlock` branch. Widening the helper automatically propagates to the render path.

`SlabSupportStateMixin` was **not modified** — it already delegates to the same helper for outline/raycast/collision shape offset.

## Fix

**File**: `src/main/java/com/slabbed/util/SlabSupport.java`

**Before**:
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

**After**:
```java
public static boolean isBeta35FenceWallVariantContactObject(BlockState state) {
    return state != null
            && (state.getBlock() instanceof FenceBlock
                    || state.getBlock() instanceof WallBlock);
}
```

## Result

Focused family proof outcome: GREEN.

```
JULIA_BETA35_FENCE_WALL_FAMILY_SUMMARY outcome=GREEN rows=21 greenFamily=20 notCovered=1
  modelRenderGap=0 shapeContactGap=0 placementFailure=0 other=0
  glassPaneControl=NOT_COVERED failureLayer=NONE
  productionFixImplemented=true releaseAudit=NOT_RUN
  releaseTagMoved=false canonicalCheckoutModified=false
JULIA_BETA35_FENCE_WALL_FAMILY_GREEN outcome=GREEN
  fenceFamilyRows=11 wallFamilyRows=9 glassPaneControl=NOT_COVERED
  failureLayer=NONE productionFixImplemented=true
```

### FenceBlock family rows (all GREEN_FAMILY)

| Variant | objectDy | renderDyApplied | shapeContactGap | shapeTriadCoLocated |
|---|---|---|---|---|
| `minecraft:oak_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:birch_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:jungle_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:acacia_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:dark_oak_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:mangrove_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:cherry_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:bamboo_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:crimson_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:warped_fence` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:nether_brick_fence` | -1.500000 | yes | 0.000000 | yes |

### WallBlock family rows (all GREEN_FAMILY)

| Variant | objectDy | renderDyApplied | shapeContactGap | shapeTriadCoLocated |
|---|---|---|---|---|
| `minecraft:cobblestone_wall` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:mossy_cobblestone_wall` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:stone_brick_wall` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:brick_wall` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:andesite_wall` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:granite_wall` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:diorite_wall` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:cobbled_deepslate_wall` | -1.500000 | yes | 0.000000 | yes |
| `minecraft:polished_blackstone_brick_wall` | -1.500000 | yes | 0.000000 | yes |

### PaneBlock control (NOT_COVERED)

- `minecraft:glass_pane` — `inFenceWallFamily=no`, `renderDyApplied=no`, `shapeContactGap=1.500000`, `classification=NOT_COVERED`

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava` → `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallFamilyFix=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; GREEN markers emitted as documented above
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35BirchFenceVariantRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=GREEN`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceModelRenderRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=GREEN`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallVariantCoverage=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceFamilyLiveRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_FENCE_FAMILY_SUMMARY outcome=GREEN`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=27 contactGap=0`
- `./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`
- `git diff --check` → clean

## Release status

**Beta 3.5 release remains BLOCKED.**

Post-live audit at `edbba27` added `-Dslabbed.beta35LiveHitboxGateRed=true`.
The connected `minecraft:cherry_fence` and `minecraft:stone_brick_wall`
rows still report `objectDy=-1.500000`, `contactGap=0.000000`,
`collisionCoLocated=yes`, and `triadCoLocated=yes`, but Julia's live
hitbox/collision complaint is not closed by this shape-math proof. Current
classification: `PENDING` / `PROOF_HARNESS_GAP`. Fence gates are separate
from this family law and are RED as `FENCE_GATE_CONTACT_GAP`; see
`docs/beta35-live-hitbox-gate-red.md`.

Remaining unresolved live failures:

- `minecraft:birch_trapdoor` — not yet covered (no RED proof yet)
- `minecraft:spruce_door` — not yet covered (no RED proof yet)
- `minecraft:birch_sign` — not yet covered (no RED proof yet)
- `minecraft:anvil` — in allowlist; live evidence is inspect-only; placement-event capture still needed

No release audit was run. No release tag was moved.

## Out of scope

- Trapdoor, door, sign, lectern, chain, end rod, redstone, rail work.
- Special-fullblock work beyond existing allowlist.
- Pane support.
- Release audit.
- Release tag movement.
- Version, changelog, or release metadata.
