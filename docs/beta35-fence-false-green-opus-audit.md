# Beta 3.5 Fence False-Green Opus Audit

High-reasoning audit/classification slice for the Beta 3.5 fence/wall variant proof contradiction at HEAD `8545b84` / `save/beta35-beta35-fence-wall-variant-coverage-integrated`.

Evidence folder: `tmp/beta35-fence-false-green-opus-audit-8545b84`

No production gameplay code was changed. No release audit was run. No release tag was moved. Canonical checkout was not modified beyond this audit doc and a spine append.

## Contradiction

Automation: `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN ... contactGap=0 triadMismatch=0 collisionShapeRisk=0 connectionShapeRisk=0 ... failureLayer=NONE` for `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, `minecraft:cobblestone_wall`.

Julia, live/headless visual: fences are “in no way shape or form fixed at all.”

## Classification

Failure layer: `OBJECT_MODEL_BOTTOM_PROXY_GAP` in the proof harness, masking an underlying `MODEL_RENDER_GAP` in production. Both layers are present; the proof harness layer is the reason the production layer was not caught.

Outcome: previous proof is false-green. Release remains blocked.

## What the current proof actually proves

The current `runBeta35FenceWallVariantCoverageProof` and `runBeta35FenceFamilyAuditRow` in `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java` (≈ lines 12626–13041) measure:

- Whether the fence/wall variant `place`s and `survive`s on the lowered slab support.
- `objectState.getOutlineShape(world, pos, ShapeContext.of(player))` bounds.
- `objectState.getRaycastShape(world, pos)` bounds.
- `objectState.getCollisionShape(world, pos, ShapeContext.of(player))` bounds.
- `SlabSupport.getYOffset(world, pos, state)` for the fence/wall.
- A connection-state read of the fence/wall `BlockState`.
- A derived `objectModelBottomY = (collisionBox != null ? collisionBox : outlineBox).minY` — i.e., a **proxy for the model bottom taken from the collision (or outline) shape bounds**.
- A derived `contactGap = objectModelBottomY - supportVisibleTopY`.

In the canonical slabbed lowered configuration, `SlabSupportStateMixin` explicitly offsets:

- `getOutlineShape` for fence/wall variants (`slabbed$offsetOutline`, returning the collision shape with the lowered `yOff`).
- `getCollisionShape` for fence/wall variants and grindstone (`slabbed$offsetOakFenceAndGrindstoneCollision`, applying `.offset(0, yOff, 0)`).
- `getRaycastShape` for empty-raycast lowered fence/wall variants (`slabbed$offsetRaycast`, returning the offset outline shape).

So the proof always sees fully offset collision/outline/raycast boxes, by construction.

`contactGap == 0` is therefore guaranteed once the variant is added to the allowlist. The proof tautologically passes for any block whose collision/outline/raycast shapes are offset by `SlabSupportStateMixin`.

## What the current proof does NOT prove

The proof never inspects the actual rendered client model. Specifically it does not:

- Look at the `OffsetBlockStateModel.emitQuads` translate, the Fabric renderer dy path, or `BlockModelDyTranslateMixin`'s `MatrixStack` translate.
- Read `OffsetBlockStateModel.snapshotModelDyOwnerTrace()` for the placed fence/wall position.
- Capture a headless screenshot or pixel column over the fence/wall to check whether the visible model bottom is actually at the offset Y.
- Cross-check `objectModelBottomY` against any model-side quantity. `objectModelBottomY` is a **collision/outline shape proxy**, not the rendered model bottom.

The proof's `objectModelBottomY` and the actual visible model bottom are deliberately divergent for fences, walls, and panes; the proof harness has no signal that detects this divergence.

## Why Julia can still see broken fences despite GREEN markers

Two paths are intentionally desynchronized in the codebase as of `8545b84`:

1. **Shape / contact path** — `SlabSupportStateMixin` shifts outline, raycast, and collision shapes downward by `yOff = beta35FenceWallVariantContactDy(world, pos, state)` (≈ `supportDy - 0.5`) for the four allowlisted variants. The proof exercises exactly this path and reports `contactGap=0`.

2. **Visible render path** — In `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java:117-127`, `emitQuads` does:

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

   This explicitly zeros the model dy for **every** `FenceBlock`, `WallBlock`, and `PaneBlock`, including the four allowlisted variants. The Fabric renderer (Indigo / Sodium+Indium) goes through `OffsetBlockStateModel`, so for fences and walls the visible model is **not** shifted by the support dy.

The vanilla `BlockModelRenderer` path in `BlockModelDyTranslateMixin` does honor `ClientDy.dyFor` for fences and walls; in practice Indigo's Fabric renderer is the active path, so the visible quads come from `emitQuads` and stay at the un-shifted Y.

Net behavior:

- Collision, hitbox, raycast: ~1.5 blocks lower than the unshifted position.
- Visible model: unchanged, at the original full-block Y position.
- `SlabSupport.isBeta35FenceWallVariantContactObject` triggers shape offset only; it has no counterpart in the render path.
- Proof reads only the shifted shapes and reports GREEN. Player sees the fence floating where it always was, with the hitbox and collision dropped below it.

This is exactly the divergence Julia describes.

## False-green mechanism (exact)

`objectModelBottomY` in the proof is sourced from `collisionShape` or `outlineShape`. Both are forcibly offset by `SlabSupportStateMixin`. The visible client model bottom is sourced from `OffsetBlockStateModel.emitQuads`, which forcibly zeros dy for `FenceBlock | WallBlock | PaneBlock`. The proof's proxy and the real visible quantity are decoupled by design. The proof has no probe that fails when this decoupling is in effect, so the proof cannot go RED for this defect.

## Files and functions involved

- `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java`
  - `runBeta35FenceWallVariantCoverageProof` (≈ line 12626)
  - `runBeta35FenceFamilyLiveRedProof` (≈ line 12758)
  - `runBeta35FenceFamilyAuditRow` (≈ line 12899), critical line: `objectModelBottomY = modelProxyBox.minY` where `modelProxyBox` is collision or outline.
- `src/main/java/com/slabbed/util/SlabSupport.java`
  - `isBeta35FenceWallVariantContactObject` (line 176)
  - `beta35FenceWallVariantContactDy` (line 201)
  - Inner dispatch at line 1296–1298 of `getYOffsetInner`.
- `src/main/java/com/slabbed/mixin/SlabSupportStateMixin.java`
  - `slabbed$isLoweredBeta35FenceWallVariantContactObject` (line 122)
  - Raycast branch at line 257–260.
  - Collision branch at line 268–280 (`slabbed$offsetOakFenceAndGrindstoneCollision`).
  - Outline branch at line 303–306 (returns collision shape for lowered fence/wall variants).
- `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java`
  - `emitQuads` lines 117–127: explicit `dy = 0.0f` for `FenceBlock | WallBlock | PaneBlock`. **This is the actual visible-render defect for the four allowlisted variants.**
- `src/client/java/com/slabbed/client/ClientDy.java`
  - `dyFor` returns `SlabSupport.getYOffset` for non-carpet blocks; the render exclusion happens above it in `emitQuads`.
- `src/client/java/com/slabbed/mixin/client/BlockModelDyTranslateMixin.java`
  - Vanilla render path that does translate by dy, but is not the active Fabric renderer path.

## Next RED proof to add

The next RED proof must observe the actual rendered model dy for the fence/wall variants, not the collision/outline shape bounds. Two cheap options exist; either reproduces Julia's observation as a hard RED:

1. **Render-quad dy probe via existing trace.** Before placement, call `OffsetBlockStateModel.resetModelDyOwnerTrace(objectPos)`; after placement and `waitForChunksRender`, call `OffsetBlockStateModel.snapshotModelDyOwnerTrace()`. Assert `trace.totalAppliedDy() != 0` for each variant when `SlabSupport.getYOffset(...)` is non-zero. With the current `emitQuads` exclusion, this returns `totalAppliedDy=0` while `slabSupportDy` is `-1.5` — a clean RED.

2. **Direct dy-policy assertion.** In the same row, compute `clientDy = ClientDy.dyFor(world, pos, state)` and `slabSupportDy = SlabSupport.getYOffset(world, pos, state)`. Also compute the renderer's effective dy as the proof currently sees it. If `slabSupportDy != 0` and the block is a `FenceBlock`, `WallBlock`, or `PaneBlock`, classify the row as `MODEL_RENDER_GAP` if the renderer exclusion is in effect (read by checking `state.getBlock() instanceof FenceBlock | WallBlock | PaneBlock` against the `emitQuads` policy). This is RED for every allowlisted variant on the current HEAD.

A headless screenshot/pixel-column probe would also work, but it is heavier than either option above and is not necessary to make this RED.

## Recommendation

Two layers must change. Pick both, in order:

1. **A — Proof harness fix (required first).** Add the render-quad dy probe described above. Until the proof can go RED for this divergence, every future fence/wall/pane variant claim risks repeating this false-green.

2. **B — Model / render path fix (required for the fence/wall variant green claim).** Revisit `OffsetBlockStateModel.emitQuads:117-127`. The blanket `FenceBlock | WallBlock | PaneBlock` exclusion was added to prevent some prior visual artifact (per the inline comment, "visual connection offsets"). The four allowlisted variants need a conditional that applies `dy` when `SlabSupport.getYOffset(view, pos, state)` is non-zero — at minimum gated on `SlabSupport.isBeta35FenceWallVariantContactObject(state)`. If the historical connection artifact reappears with the offset applied, a follow-up slice can address it; that is a separate problem from the lowered-support visible offset.

Recommendations C (collision/connection shape fix), D (ClientDy/render-view bridge fix), and E (revert/defer fence variants) are not the cheapest path. The shape offset path is correct. The render exclusion is the entire delta.

## Audit answers (compact)

| # | Question | Answer |
|---|---|---|
| 1 | What does the current proof measure? | Collision/outline/raycast shape bounds, placement, survival, connection state. |
| 2 | Real rendered model position vs proxy? | Proxy only (`objectModelBottomY` derived from collision/outline). |
| 3 | Actual client model quads? | No. |
| 4 | Connected fence post/side-arm? | Indirectly via shape bounds; not as quads. |
| 5 | Collision after connection update? | Yes, post `updateNeighbors`. |
| 6 | Headless screenshot / pixel capture? | No. |
| 7 | Is `objectModelBottomY` wrong for fence/wall? | Yes — it is a shifted-shape proxy, not the rendered model bottom. |
| 8 | Dy on outline/raycast but not model? | Exactly this. |
| 9 | Dy on model but not collision? | No (the opposite). |
| 10 | Connection-state recompute missed? | No; not the active failure mode. |
| 11 | Client render-view dependent? | Yes — Fabric `emitQuads` path zeros fence/wall/pane dy. |
| 12 | Green via precomputed bounds vs live render? | Yes. |
| 13 | What new RED proof reproduces Julia's observation? | Model-dy trace probe or pixel column. |

## Release status

Release remains blocked. The fence/wall variant coverage claim is rescinded as a release artifact; the four variants must be re-proven via the new RED-first probe before they re-enter a release slice.

## Julia evidence needed

Not strictly required. The code path is unambiguous from the HEAD source. A confirmatory `F3+B` screenshot showing the hitbox at slab-top and the model floating one full block above would document the artifact for the record, but the audit conclusion does not depend on it.

## Validation

No new gradle commands were run for this audit. Existing prior-slice evidence in `tmp/beta35-fence-wall-variant-integration-merge-f9995b6` and `tmp/beta35-fence-wall-variant-coverage-fix-c570299` was sufficient to confirm the proof structure; the render-side exclusion was confirmed by direct source read of `OffsetBlockStateModel.java` at HEAD.

No production gameplay code was changed by this audit. No release audit was run. No release tag was moved.
