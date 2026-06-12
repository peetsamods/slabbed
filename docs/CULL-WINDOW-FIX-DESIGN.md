# Cull / "ghost window" fix — design & status (1.21.1)

**Status: ROOT-CAUSED + predicate shipped & tested + RENDERER-AGNOSTIC FIX IMPLEMENTED (model-path, mirrors 1.21.11). Live visual confirmation is the one remaining step.** Written 2026-06-11 (overnight autonomous session). Investigation backed by a 4-agent workflow + direct jar verification.

> **UPDATE (same session):** the per-renderer cull-mixin plan below was SUPERSEDED. After reading the 1.21.11 repo I found a renderer-agnostic fix it already ships — clear the *emitted quad's own `cullFace`* on step faces in `OffsetBlockStateModel`. Because the dy-shift already rides `RenderContext.pushTransform` and that path is honoured by Indigo **and Sodium** (blocks already render lowered under Sodium), clearing `cullFace` in the same transform un-culls the exposed strip under every renderer — **no Indigo/Sodium mixin, no conditional machinery, no Sodium-internal coupling.** Implemented on 1.21.1 in `OffsetBlockStateModel.emitBlockQuads` (clears `cullFace`, preserves `nominalFace`, gated by `slabbed$hasLoweredStepFace` so the flat neighbour's exposed face is un-culled too; only flips cull→draw). Compiles, 30/30 gametests green. The per-renderer-mixin analysis below is kept for reference / as the fallback if the model-path approach ever proves insufficient.

---

## Symptom

On a slab-height step — a block Slabbed renders **lowered** (dy −0.5/−1.0) sitting beside a block at a different height — a thin **see-through strip** ("ghost window" / culled face) appears along the shared vertical seam. Julia flagged it on stacked oak-log + slab structures. The port even ships the symptom as a (deferred, uncompiled) gametest: `SlabbedLabB2UpperHalfGhostWindowClientGameTest` (`hasGhostWindowGap`).

## Root cause — model-vs-voxel divergence

Slabbed lowers a block **only in the render mesh** (dy), via:
- `OffsetBlockStateModel.emitBlockQuads` (FRAPI `pushTransform`, shifts vertex Y) — `src/client/.../model/OffsetBlockStateModel.java:191` (`sourceDy = ClientDy.dyFor(view,pos,state)`)
- `BlockModelDyTranslateMixin` (vanilla `BlockModelRenderer.render` → `matrices.translate(0,dy,0)`)

…while the **BlockState stays a normal full cube in its original [0,1] voxel cell**. Face culling is decided from that *unshifted* voxel geometry, independent of the model offset. Two opaque full cubes sharing a vertical face → the renderer culls (drops) the shared side face from the section mesh. When the model then drags one cube down 0.5/1.0, the now-exposed strip of that side face **was never meshed** → you see through the world.

`ClientDy.dyFor` → for non-carpet blocks → `SlabSupport.getYOffset`. So **the offset signal the model renders with is `getYOffset`**. Any cull fix must use the *same* signal so cull matches render by construction.

## The cull gate differs per renderer — and Julia's env is Sodium

| Renderer | Active when | Cull gate (true = draw) | Calls vanilla `Block.shouldDrawSide`? |
|---|---|---|---|
| Vanilla `BlockModelRenderer` | no FRAPI renderer present (never, here) | `Block.shouldDrawSide` (`class_2248.method_9607`) | — (is vanilla) |
| Fabric **Indigo** (`fabric-renderer-indigo` **1.7.0**) | FRAPI present, no Sodium | `BlockRenderInfo.shouldDrawFace(Direction)` — **package-private**, returns true=draw; delegates to `class_2248.method_9607`. `isFaceCulled = !shouldDrawFace`. Fields `blockView/blockPos/blockState` are public. **No** `shouldDrawSide`/`shouldCullSide` (those exist only on 1.21.11's 8.0.2). Caches via `cullCompletionFlags/cullResultFlags` — but `@At("RETURN")` catches the cached return too. | yes |
| **Sodium** (`sodium-fabric-0.8.12-alpha.4`, pkg `net.caffeinemc.mods.sodium`) | present in the live profile | `BlockOcclusionCache.shouldDrawSide(class_2680 state, class_1922 view, class_2338 pos, class_2350 dir)` — **public**, true=draw, **own voxel-shape reimplementation** (`calculate(ShapeComparison)`/`lookup`/`isFullShape`) | **NO** — does not call vanilla |

**Consequence:** the live profile (`Slabbed 1.21.1`) runs **Sodium**, which provides the FRAPI renderer itself and **bypasses Indigo entirely**. So the window Julia sees is produced by *Sodium's* occlusion. A vanilla `Block.shouldDrawSide` mixin would NOT affect Sodium (it reimplements), and the Indigo `BlockRenderInfo` mixin is **dead code under Sodium**. Only a **Sodium `BlockOcclusionCache.shouldDrawSide` mixin** fixes it in her environment. (Indigo/vanilla mixins still help users who run *without* Sodium.)

## The fix predicate (SHIPPED, safe, tested)

`SlabSupport.isSlabHeightStepFace(BlockView world, BlockPos pos, BlockState state, Direction direction)` — pure, renderer-agnostic. Returns true when `direction`'s **horizontal** side face sits at a height step: neighbour not air, and `abs(getYOffset(self) − getYOffset(neighbour)) > 1e-6`. Uses the exact `getYOffset` signal the model renders with. Gated by `-Dslabbed.disableStepCull` (kill switch for A/B). Horizontal-only by design (see limitation below). **Only ever used to flip cull→draw**, so it cannot create new culling/z-fight artifacts (opposite-facing coplanar seam faces are GPU back-face-culled; the occluded portion hides behind the opaque neighbour body).

This predicate + a headless truth-table gametest are committed now. Nothing calls it at render time yet, so there is **zero behaviour change** until the wiring below is approved.

## Render wiring — DEFERRED, needs approval

Two options:

**Option A — per-renderer cull mixins (recommended).** A safe `@Inject(method=…, at=@At("RETURN"), cancellable=true)` on each renderer's gate: if it returned false (cull) AND `isSlabHeightStepFace` → `setReturnValue(true)`.
- **Sodium** (the one that matters here): target `net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache#shouldDrawSide(class_2680,class_1922,class_2338,class_2350)`. Live-verifiable in Julia's env, A/B via `-Dslabbed.disableStepCull`.
- **Indigo** (non-Sodium users): target `BlockRenderInfo#shouldDrawFace`, shadow `blockView/blockPos/blockState`. Turnkey (API verified against the 1.7.0 jar).
- **Blocker:** mixing into Sodium's class means non-Sodium users would hit a missing-target error. Needs **conditional mixin application** — a `IMixinConfigPlugin.shouldApplyMixin` (or a separate Sodium-only mixin config gated on `FabricLoader.isModLoaded("sodium")`). The repo has **none today**; building it wrong breaks loading for everyone. This is the real reason to defer to a supervised session.

**Option B — offset the culling shape (renderer-agnostic, higher risk).** Offset the lowered block's `getSidesShape`/culling shape to match the model; then *all* renderers' occlusion (which consult the culling shape) account for the offset → one fix, no per-renderer mixins, no Sodium-internal coupling. **But** the culling shape is load-bearing (light propagation, AO, neighbour occlusion); offsetting it risks light leaks / AO artifacts and needs careful testing. Worth prototyping behind the same kill switch.

## Recommendation

1. Land the predicate + test now (done).
2. Supervised: prototype **Option B** first (one clean, renderer-agnostic change). If light/AO regress, fall back to **Option A** with the Sodium-conditional machinery, live-verifying in the Sodium profile.
3. Either way, ship behind `-Dslabbed.disableStepCull` and A/B both directions to prove the hook is the thing fixing it.

## Known limitation

Horizontal faces only. A **vertical** step — a frozen-flat block (dy 0) directly above a lowered one (dy −0.5), reachable via the freeze-on-place law with a specific build order — would leave a top/bottom-face window the horizontal predicate doesn't cover. Rare; documented as a follow-up. Extending the predicate to up/down faces is trivial (drop the `isHorizontal` guard) but adds harmless overdraw on lowered-above-normal pairs, so it was scoped out of the first pass.

## Verified API facts (for turnkey implementation)

- `fabric-renderer-indigo 1.7.0`: `boolean BlockRenderInfo.shouldDrawFace(class_2350)` (pkg-private, true=draw); public fields `class_1920 blockView; class_2338 blockPos; class_2680 blockState;`. `AbstractBlockRenderContext.isFaceCulled = !blockInfo.shouldDrawFace(face)`.
- `sodium 0.8.12-alpha.4`: `public boolean BlockOcclusionCache.shouldDrawSide(class_2680, class_1922, class_2338, class_2350)` (true=draw, own reimpl).
- Port: `SlabSupport.getYOffset(BlockView,BlockPos,BlockState)` (recursion-guarded via `IN_GET_Y_OFFSET`); `state.isSolidBlock(world,pos)` is the port's two-arg full-cube idiom (no `isOpaqueFullCube` call sites exist here).
