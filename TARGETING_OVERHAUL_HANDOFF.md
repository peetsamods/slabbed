# Slabbed 1.21.11 — Targeting Overhaul Handoff

Written 2026-06-06 (overnight, autonomous, no live testing). Branch
`claude/terrain-targeting-overhaul-20260606`, based off the codex line
`c459fb01` + the dirty WIP (baseline commit `5567bcf7`). The overhaul itself is
commit `39a345e7`.

This worktree is isolated: it shares `~/CascadeProjects/Slabbed/.git` but the
codex worktree `~/.codex/worktrees/slabbed-terrain-custom-redproof-20260603`
was left **untouched** for safe comparison.

## TL;DR

The mistargeting / "nightmare" placement hijacks / lantern-clip dead-zones all
trace to **one** root cause, and it is now fixed by **one** clean mechanism that
replaced ~815 lines of brittle heuristics.

- **Root cause** (verified against decompiled 1.21.11): the mod renders some
  blocks at a visual Y offset (`SlabSupport.getVisualYOffset` →
  `{-1.0,-0.5,0.0,+0.5}`) and offsets their outline/raycast `VoxelShape`s to
  match. But vanilla `BlockView.raycast` is a voxel **DDA that returns the first
  cell** along the ray with a hit, **not the globally nearest** hit. A shape
  offset out of its own voxel cell (a lowered block's lower half living in
  `pos.down()`, or a near-horizontal ray that crosses only the offset
  mid-height) loses to a nearer cell's block, or is missed entirely.
- **Fix**: a single offset-aware **nearest-hit** raycast
  (`SlabbedOffsetRaycast`) inserted by `@Redirect`-ing the
  `entity.raycast(g,f,false)` block pick inside `ClientPlayerEntity.method_76763`
  (the client pick: `updateCrosshairTarget` → `method_76762` → `method_76763`).
  Because that one `BlockHitResult` becomes both the crosshair target **and** the
  hit sent to the server on use/place, fixing it corrects targeting **and**
  placement together.

## What changed

| File | Change |
|---|---|
| `src/main/java/com/slabbed/util/SlabbedOffsetRaycast.java` | **NEW.** The offset-aware nearest-hit raycast. Reuses vanilla's DDA cell traversal but, per cell `C`, tests the outline of `{C, C.up(), C.down()}` (the offset neighbours) and keeps the global nearest hit. |
| `src/client/java/com/slabbed/mixin/client/ClientPickOffsetRaycastMixin.java` | **NEW.** `@Redirect` wiring into `method_76763`. ~6 lines. Preserves the vanilla entity-vs-block merge and reach clamp. |
| `src/client/java/com/slabbed/mixin/client/GameRendererCrosshairRetargetMixin.java` | **DELETED** (737 lines). The per-block-type "rescue" scan is subsumed by generic geometry. |
| `src/main/java/com/slabbed/mixin/SlabSupportStateMixin.java` | Removed the **slab-side comfort overlay unions** (they unioned a torch/object shape into the slab below to coax the DDA — they would now feed the nearest-hit raycast a wrong slab+column hit). **Kept** the owner shape offset and the floor-torch own-shape substitution. **Added** a fence/wall/pane outline gate (see Blocker). |
| `src/client/resources/slabbed.client.mixins.json` | Registered the redirect, unregistered the rescue. |
| `src/gametest/.../OffsetRaycastTargetingTest.java` | **NEW.** 10 server gametests. |
| `src/gametest/.../OffsetRaycastClientGameTest.java` | **NEW.** 1 end-to-end client gametest. |
| `src/gametest/resources/fabric.mod.json` | Registered the two test classes. |

## Why it is correct (and vanilla-safe)

- **±1 window is provably complete** for the closed offset set
  `{-1.0,-0.5,0.0,+0.5}`: an owner occupies at most `{P,P.down()}` (−1.0) or
  `{P,P.up()}` (+0.5), so any ray hitting it enters a cell within ±1 of `P`.
- **Bit-exact with vanilla for non-offset blocks**: ray distance is monotonic in
  DDA march order, so first-cell-hit == nearest-hit when shapes stay in-cell. The
  primary cell `C` is tested exactly as vanilla (inside-hits and all); non-offset
  neighbours are **skipped** (they are found when the DDA reaches them as a
  primary cell), so cost ≈ vanilla in non-offset scenes; neighbour inside-hits
  are suppressed so an eye embedded in a lowered shape doesn't grab a near-zero
  hit.
- Uses `world.raycastBlock` (outline-only, side-refined), never the fluid-aware
  factory, so `includeFluids=false` semantics hold and the intentionally
  un-offset fluid shapes can't beat an offset block outline.

## Verification (all green — no live test was needed)

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew --no-daemon compileJava compileClientJava compileGametestJava runGameTest --console=plain`

1. **41 server gametests** (`runGameTest`) — targeting purely from eye+look
   across `-0.5` / `-1.0` / `+0.5` / lowered-side-slab; **exact non-offset
   parity** vs `world.raycast` (plain block, double slab, stairs, many rays);
   the fence outline gate; the floor-torch via its own offset shape; and the ±1
   grazing extremes. The bug cases also assert vanilla **MISSES** (divergence
   proof).
2. **Runtime mixin application** — `runClientGameTest` logs
   `Mixing ClientPickOffsetRaycastMixin ... into ClientPlayerEntity` with no
   `InvalidInjectionException` (under `mixin.debug` + `dumpTargetOnFailure`).
3. **End-to-end client gametest** (`OffsetRaycastClientGameTest`) — in the real
   client, the player looks at a lowered block's visual mid-height:
   `vanilla=MISS crosshair=BLOCK 0,201,0 side=north` → **GREEN**.

## A blocker this surfaced (and fixed)

Fences/walls/panes render **un-lowered** (`OffsetBlockStateModel` forces their
dy to 0) but their **outline** was being offset by `SlabSupportStateMixin`
(`shouldOffset` → `hasSlabInColumn` returns true for a fence on a slab). With
first-cell DDA this was harmless; with the authoritative nearest-hit raycast it
would target a phantom outline 0.5 below the rendered fence. The mixin now gates
the outline/raycast offset for connection blocks to match the render (same
custom-Terrain-Slabs-support exception the render uses). Verified by
`connectionBlockOutlineNotOffset`.

## Deliberately NOT changed

- **`BlockItemPlacementIntentMixin`** — kept as-is. It does two intent-only jobs
  correct geometry cannot supply: (1) up-face-edge → perpendicular side
  inference, and (2) the BOTTOM/TOP half-split clamp, because raw vanilla
  `SlabBlock.getPlacementState` can never yield TOP on a `-0.5` block's side
  (`hit.y - placePos.y` is always ≤ 0.5 there). Deleting it would regress
  perpendicular side-slab placement to "always BOTTOM" and top-edge clicks to
  "stack on top." The codex placement stabilization is preserved.

## Open items / recommended follow-ups

1. **Popping (not fixed — needs live observation).** Root cause analysis ranked
   it: (1) anchor-arrives-a-tick-late on **adjacent/column** anchored full blocks
   (a non-slab FB placed *beside* a lowered FB has no live lowering source, so it
   renders dy=0 until the anchor attachment syncs, then snaps −0.5). Note the
   **direct** FB-on-slab case does NOT pop — `shouldOffset`→`hasSlabInColumn`
   already returns −0.5 live, no anchor needed. Recommended fix (low risk):
   in `getYOffsetInner`, on a **client** world, speculatively apply the
   adjacent/column lowering geometry even before the anchor arrives (guarded to
   the exact anchor-candidate geometry). This is a **transient timing** issue the
   render-trace gametest can't catch (it only sees the settled mesh), so it must
   be live-verified — hence left for Julia. Full ranked analysis +
   `RenderOffsetTrace` harness notes are in
   `tmp/targeting-overhaul-workflow-result.json` (the `poppingPlan` field).
2. **Entity-vs-block targeting is now more correct, which is a behavior change.**
   A mob standing in front of a lowered block's nearer visual face: the block can
   now legitimately win (you're aiming at the visual surface). This is intended,
   not a regression.
3. **The legacy client proofs that asserted the OLD rescue** (e.g. the
   cross-axis-side-slab steal in `BsFbUpperFacePerpendicularPlacementClientGameTest`
   and the `runNoRescue*` proofs in `SlabbedLabClientGameTest`) are
   property-gated OFF and not in CI; if re-enabled they should be rewritten to
   assert the geometrically-correct nearest target.
4. **1.21.1 port** — see the separate assessment; the same util + redirect ports
   cleanly, but the 1.21.1 pick method differs and its fabric-client-gametest is
   broken, so only the server-gametest verification bar is reachable there.

## How to evaluate

- Diff: `git diff 5567bcf7..39a345e7` (the overhaul only).
- Re-run the suite (command above) → expect `All 41 required tests passed`.
- Live: launch `./gradlew runClient`, build a lowered terrain wall, and aim along
  it — the crosshair should follow the visual surface with no jumpiness or
  side-hijack. The reference workflow analysis is in `tmp/` (gitignored).
