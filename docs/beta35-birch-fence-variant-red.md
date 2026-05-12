# Beta 3.5 Birch Fence Variant RED Proof

Focused RED proof for `minecraft:birch_fence` variant-family coverage gap.

Operating base: HEAD `4f09773` / `save/beta35-live-object-coverage-gap` on `integrate/phase19-into-side-slab-top-support`.

Evidence folder: `tmp/beta35-birch-fence-variant-red-4f09773/`.

Gate: `-Dslabbed.beta35BirchFenceVariantRed=true`

Markers:

- `JULIA_BETA35_BIRCH_FENCE_VARIANT_RED`
- `JULIA_BETA35_BIRCH_FENCE_VARIANT_ROW`
- `JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=RED failureLayer=VARIANT_FAMILY_COVERAGE_GAP`

No production gameplay or render code was changed. No release audit was run. No release tag was moved.

## What this proof measures

The `minecraft:birch_fence` is the wood-fence variant Julia held during her live session at `gitHead=a891ba6`. It is outside the current `isBeta35FenceWallVariantContactObject` allowlist (`OAK_FENCE`, `SPRUCE_FENCE`, `NETHER_BRICK_FENCE`, `COBBLESTONE_WALL`). The proof places `birch_fence` on a compound-lowered bottom-slab support (identical setup to the existing fence model render proof) and records whether:

- it is in the fence/wall allowlist (`inFenceWallAllowlist`)
- `SlabSupport.getYOffset` returns the full fence-contact dy
- `OffsetBlockStateModel` applies the render-quad dy
- the shape contact gap is zero

`minecraft:oak_fence` is the control row and must remain GREEN (`GREEN_ALLOWLISTED`).

## Proof result

`JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=RED rows=2 greenAllowlisted=1 variantCoverageGap=1 modelRenderGap=0 placementFailure=0 oakFenceClassification=GREEN_ALLOWLISTED birchFenceClassification=VARIANT_FAMILY_COVERAGE_GAP failureLayer=VARIANT_FAMILY_COVERAGE_GAP productionFixImplemented=false releaseAudit=NOT_RUN releaseTagMoved=false canonicalCheckoutModified=false`

## Row details

### oak_fence (control)

```
JULIA_BETA35_BIRCH_FENCE_VARIANT_ROW
  objectId=minecraft:oak_fence
  family=fence_family
  configuration=isolated
  inFenceWallAllowlist=yes
  placementResult=Fail[]
  blockAppearedAfterAttempt=true
  survivalResult=survived
  supportDy=-1.000000
  objectDy=-1.500000
  expectedModelDy=-1.500000
  actualModelAppliedDy=-1.500000
  totalAppliedDy=-9.000000
  renderDyApplied=yes
  shapeContactGap=0.000000
  shapeTriadCoLocated=yes
  emitCalls=6  appliedCalls=6
  classification=GREEN_ALLOWLISTED
  failureLayer=NONE
```

### birch_fence (live-tested variant)

```
JULIA_BETA35_BIRCH_FENCE_VARIANT_ROW
  objectId=minecraft:birch_fence
  family=fence_family
  configuration=isolated
  inFenceWallAllowlist=no
  placementResult=Success[swingSource=CLIENT, itemContext=...]
  blockAppearedAfterAttempt=true
  survivalResult=survived
  supportDy=-1.000000
  objectDy=-0.500000
  expectedModelDy=-0.500000
  actualModelAppliedDy=0.000000
  totalAppliedDy=0.000000
  renderDyApplied=no
  shapeContactGap=1.500000
  shapeTriadCoLocated=no
  emitCalls=6  appliedCalls=0
  classification=VARIANT_FAMILY_COVERAGE_GAP
  failureLayer=VARIANT_FAMILY_COVERAGE_GAP
```

## Failure mechanism

1. `SlabSupport.isBeta35FenceWallVariantContactObject(birch_fence_state)` → `false`
2. `beta35FenceWallVariantContactDy(...)` → `Double.NaN` (allowlist check fails early)
3. `getYOffsetInner` falls through to generic `shouldOffset` path → returns `-0.5` (basic slab offset, not the compound fence-contact `-1.5`)
4. `OffsetBlockStateModel.emitQuads`: `blk instanceof FenceBlock` → inner guard `!isBeta35FenceWallVariantContactObject(state)` is `true` → `dy = 0.0f` for rendered quads
5. Rendered model stays at block-grid position; support is compound-lowered by `-1.0`; result: `shapeContactGap=1.500000` and `renderDyApplied=no`

`minecraft:birch_fence` places and survives (vanilla placement rules allow it on the slab surface), but the rendered model and collision/outline shapes are not lowered to match the compound support. This is the release-blocking live gap Julia observed.

## Current allowlist (too exact for wood fence families)

`isBeta35FenceWallVariantContactObject` at `4f09773`:

- `minecraft:oak_fence` — covered, GREEN
- `minecraft:spruce_fence` — covered, GREEN
- `minecraft:nether_brick_fence` — covered, GREEN
- `minecraft:cobblestone_wall` — covered, GREEN
- `minecraft:birch_fence` — **NOT covered, RED**
- all other wood fences — not covered (not yet proven)

The render-quad guard in `OffsetBlockStateModel.emitQuads` is an exact mirror of this allowlist. Any variant not in it renders at `dy=0.0f` regardless of the underlying slab offset.

## Release status

**Beta 3.5 release remains BLOCKED.**

- The `a891ba6` fence model render fix is valid for its four-variant allowlist and is **not rescinded**.
- The proof harness now covers `minecraft:birch_fence` explicitly. Oak_fence control remains GREEN.
- No production fix was implemented in this slice.

## Next implementation slice

**DONE.** `isBeta35FenceWallVariantContactObject` expanded to add `Blocks.BIRCH_FENCE` narrowly in the follow-up commit `save/beta35-birch-fence-variant-fix`. See `docs/beta35-birch-fence-variant-fix.md`.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava` → `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35BirchFenceVariantRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; `JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=RED` emitted as documented above
- `git diff --check` → clean
- No production gameplay or render files changed
- No release audit run
- No release tag moved
