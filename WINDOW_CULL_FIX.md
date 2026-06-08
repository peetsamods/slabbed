# 1.21.1 — Window / "doom-infinity" cull fix (port of the 1.21.11 fix)

> **STATUS: headless-green, NOT live-confirmed.** Compiles; all 37 server gametests pass;
> the client cull mixin's target + shadow types are bytecode-verified. The actual visual
> fix and the mesher-thread neighbour lookup need a `runClient` pass. **Kill switch:**
> `-Dslabbed.disableStepCull=true`.

## Branch
- `claude/1211-window-cull-fix` (off `claude/1211-targeting-overhaul-activate`).
- NOT pushed, NOT merged. Carries the targeting overhaul commits underneath.

## What it fixes
The see-through "window" / "doom-infinity" hole on a **lowered opaque cube at a slab
height step**: the Indigo chunk mesher culls the exposed step face. We force-redraw it.
Only ever ADDS faces — never removes.

## What changed
- `SlabSupport.isSlabHeightStepFace(world, pos, state, direction)` + `STEP_CULL_DISABLED`
  (new). 1.21.1 adaptation of the 1.21.11 helper: "lowered" = `getYOffset(...) < 0`
  (1.21.11 used `isDirectCustomSlabSupportedObject`, absent here); opaque-cube guard uses
  the 1.21.1 signature `isOpaqueFullCube(world, pos)`.
- `BlockRenderInfoCullMixin` (new, client). **Adapted, not verbatim:** 1.21.1's
  fabric-api (renderer-indigo **1.7.0**, bundled with 0.115.6+1.21.1) exposes the cull gate
  as a single `boolean shouldDrawFace(Direction)` — NOT the `shouldDrawSide`/`shouldCullSide`
  pair of 1.21.11's Indigo 5.x. Verified by `javap` on the bundled indigo jar. Injects at
  `shouldDrawFace` RETURN; force-true if the face is a step face.
- Registered `BlockRenderInfoCullMixin` in `slabbed.client.mixins.json`.

## Why it's NOT marked done
1. **Visual proof only.** fabric-client-gametest is broken on 1.21.1 → no automated e2e.
   Test live: lower an opaque cube onto a slab next to a non-lowered cube; the step face
   must render solid (no see-through window) from all horizontal angles.
2. **Mesher-thread neighbour risk.** `isSlabHeightStepFace` calls `getYOffset` on the
   NEIGHBOUR from the Indigo mesher. `getYOffset` for the current block is already called
   there (by `OffsetBlockStateModel.emitBlockQuads`), so self is safe; a chunk-border
   neighbour mid-load could in theory block on a `CompletableFuture.join` (the same hazard
   `SlabSupportStateMixin` guards `getOutlineShape` against). If you see a mesher hang or
   chunk-load stutter, set `-Dslabbed.disableStepCull=true` and report — the fix then needs
   a cheaper/async-safe "is neighbour lowered" path.
3. **Mixin apply is client-only** — can't be confirmed by `runGameTest`. The target class +
   `shouldDrawFace` method + the `blockView/blockPos/blockState` shadow fields are all
   confirmed present in indigo 1.7.0, so it SHOULD apply; confirm via the mixin-apply log
   on `runClient` (no `InvalidInjectionException`).

## How to verify (Julia)
```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runClient
```
Build the step scenario; look along the seam. If good → this can fold into the 0.3.0-beta.1
parity set. If it hangs/glitches → `-Dslabbed.disableStepCull=true` to neutralise instantly.
