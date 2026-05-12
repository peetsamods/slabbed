# Beta 3.5 Fence Model Render RED

Render-quad dy RED proof for the Beta 3.5 fence/wall variant false-green identified at HEAD `a576fa1` / `save/beta35-fence-false-green-opus-audit`.

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`

Branch: `integrate/phase19-into-side-slab-top-support`

Base: `a576fa1` / `save/beta35-fence-false-green-opus-audit`

Evidence folder: `tmp/beta35-fence-model-render-red-a576fa1`

Gate: `-Dslabbed.beta35FenceModelRenderRed=true`

Markers:

- `JULIA_BETA35_FENCE_MODEL_RENDER_RED`
- `JULIA_BETA35_FENCE_MODEL_RENDER_ROW`
- `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY`

No production gameplay code was changed. No release audit was run. No release tag was moved. Pane support was not added. `OffsetBlockStateModel` production behavior is unchanged.

## Scope

Proof-only slice. Measures the actual model dy applied by `OffsetBlockStateModel.emitQuads` for the four allowlisted Beta 3.5 fence/wall variants on a lowered slab support:

- `minecraft:oak_fence`
- `minecraft:spruce_fence`
- `minecraft:nether_brick_fence`
- `minecraft:cobblestone_wall`

`minecraft:glass_pane` and pane behavior remain out of scope / `NOT_COVERED`.

## How the probe works

For each row:

1. Place the variant on a lowered bottom slab via the same fixture as `runBeta35FenceFamilyAuditRow` (`prepareBeta35FenceFamilyLiveSupport` + `interactBlock`).
2. Read `SlabSupport.getYOffset` and `ClientDy.dyFor` to confirm the production support truth still reports `objectDy = -1.5` for the lowered fence/wall on top of a lowered bottom slab.
3. Call `OffsetBlockStateModel.resetModelDyOwnerTrace(objectPos)` and force a re-render via `mc.worldRenderer.scheduleBlockRenders(...)` over the object position.
4. Wait six ticks plus `waitForChunksRender` so `emitQuads` runs.
5. Snapshot `OffsetBlockStateModel.snapshotModelDyOwnerTrace()` and read `emitCalls`, `appliedCalls`, `totalAppliedDy`, and `lastDy`.
6. Classify the row.

The shape proxy that drove the old false-green (`shapeContactGap` from outline/collision and `shapeTriadCoLocated`) is also recorded in each row so the divergence is plain in the same line.

## Classifications

| Code | Meaning |
| --- | --- |
| `PLACEMENT_FAILURE` | The fence/wall did not place. |
| `SUPPORT_DY_ZERO` | `SlabSupport.getYOffset` is ~0; the fixture failed to be a lowered support row, so the probe cannot run. |
| `MODEL_DY_TRACE_NOT_OBSERVED` | `OffsetBlockStateModel.emitQuads` was never observed at the target position; the render did not happen and the probe is inconclusive. |
| `MODEL_RENDER_GAP` | `emitQuads` was observed (`emitCalls > 0`) but applied dy is zero (`appliedCalls == 0`, `totalAppliedDy == 0`) while `objectDy != 0`. The visible model never shifted; this is the live-visible Julia gap. |
| `GREEN_RENDER_DY_APPLIED` | `emitQuads` applied the expected non-zero dy. |

## Result

Outcome: RED.

`JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=RED rows=4 modelRenderGap=4 greenRenderDyApplied=0 modelDyTraceNotObserved=0 placementFailure=0 supportDyZero=0 oakFenceClassification=MODEL_RENDER_GAP spruceFenceClassification=MODEL_RENDER_GAP netherBrickFenceClassification=MODEL_RENDER_GAP cobblestoneWallClassification=MODEL_RENDER_GAP glassPane=NOT_COVERED previousFailureLayer=OBJECT_MODEL_BOTTOM_PROXY_GAP failureLayer=MODEL_RENDER_GAP productionFixImplemented=false releaseAudit=NOT_RUN releaseTagMoved=false canonicalCheckoutModified=false`

All four allowlisted variants show:

- `supportDy=-1.000000`
- `objectDy=-1.500000`
- `expectedModelDy=-1.500000`
- `modelDyTraceSeen=true`
- `emitCalls=6`
- `appliedCalls=0`
- `totalAppliedDy=0.000000`
- `actualModelAppliedDy=0.000000`
- `renderDyApplied=no`
- `shapeContactGap=0.000000`
- `shapeTriadCoLocated=yes`
- `classification=MODEL_RENDER_GAP`

The shape triad is co-located and `shapeContactGap` is zero — exactly what the previous fence/wall variant proof saw. The render-quad probe reports zero applied dy in the same row — exactly what Julia sees in live play.

## Confirmed false-green mechanism

`runBeta35FenceFamilyAuditRow` reads `objectModelBottomY` from `collisionShape` or `outlineShape`. Both are offset by `SlabSupportStateMixin` for the four allowlisted fence/wall variants, so the shape triad is always co-located at the lowered Y and `contactGap = 0`. The visible client quads come from `OffsetBlockStateModel.emitQuads`, which forces `dy = 0.0f` for every `FenceBlock | WallBlock | PaneBlock` (`src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java:122-127`). The proof's proxy and the rendered model are decoupled by design; the previous proof had no probe that could see the divergence, so it went green while the visible model stayed at the un-shifted Y.

This new gate adds that probe. It now goes RED on current HEAD.

## Status of prior fence/wall claims

The prior `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN` for `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall` is **rescinded as a release artifact**. The shape-triad proof is necessary but not sufficient; it must be paired with the render-quad dy probe for any future fence/wall/pane variant release claim.

`docs/beta35-fence-family-live-red.md` and `docs/beta35-fence-wall-variant-coverage-fix.md` describe the shape-side history and remain accurate for the shape path. They do not constitute a fence/wall variant release green on their own.

## Next implementation slice

Production fix only on the model render path. The minimum surface is `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java:117-127`:

```java
dy = (float) SlabSupport.getYOffset(view, pos, state);
if (dy != 0.0f) {
    // Prevent visual connection offsets for fences/walls/panes
    if (state.getBlock() instanceof FenceBlock
            || state.getBlock() instanceof WallBlock
            || state.getBlock() instanceof PaneBlock) {
        dy = 0.0f;
    }
}
```

The blanket exclusion needs a conditional that re-applies dy when the block is one of the four allowlisted variants on a lowered support (i.e., `SlabSupport.isBeta35FenceWallVariantContactObject(state)` and `SlabSupport.getYOffset(view, pos, state) != 0`). Panes remain `NOT_COVERED` and should keep the exclusion. Pane support and the underlying reason for the original blanket exclusion can be handled in separate slices.

The shape side already works. No `SlabSupport.java` or `SlabSupportStateMixin.java` change is needed for this slice.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava` → `BUILD SUCCESSFUL`.
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceModelRenderRed=true" ./gradlew --no-daemon runClientGameTest --console plain` → `BUILD SUCCESSFUL`; RED markers emitted as documented above.
- `git diff --check` → clean.

The default suite was not rerun; this slice is proof-only and only adds a new gated method that is dormant unless `slabbed.beta35FenceModelRenderRed` is set.

## Out of scope

- Pane support.
- Production render fix.
- New object support.
- Special-fullblock work.
- Door, trapdoor, sign, lantern, chain, end rod, redstone, rail work.
- Release audit.
- Release tag movement.
- Version, changelog, or release metadata.
- `OffsetBlockStateModel`, `SlabSupport`, or `SlabSupportStateMixin` production behavior changes.
