# Slabbed — Branch Handoff

> **Current state of THIS branch — overwrite freely.** Append-only *history* lives in
> [`SLABBED_SPINE.md`](SLABBED_SPINE.md). One `HANDOFF.md` per branch.
> Companion doc: [`TARGETING_OVERHAUL_1211_PORT.md`](TARGETING_OVERHAUL_1211_PORT.md) (the activation playbook this branch executed).

## Branch

- **Branch:** `claude/1211-targeting-overhaul-activate`
- **Worktree:** `/Users/joolmac/CascadeProjects/Slabbed-claude-1211port-candidate-20260606`
- **Based on:** `43b5eadc` (foundation) → off committed HEAD `20a5ac28` of `port/mc-1.21.1`
- **Minecraft:** 1.21.1 · **Loader:** Fabric 0.17.3 · **Yarn:** 1.21.1+build.3 · **Java:** 21
- **NOT pushed. NOT merged. Savepoint `94a5643e` untouched. Julia's uncommitted WIP in `Slabbed-phase19-integrate` untouched.**

## Status: TARGETING OVERHAUL ACTIVATED — headless-green, awaiting live `runClient`

The MC 1.21.1 port now uses the offset-aware nearest-hit raycast as the single targeting
authority, mirroring the proven 1.21.11 overhaul (commit `39a345e7`). The old DDA-rescue
lane is deleted. **Net −2,960 lines** (−3,035 rescue lane, +75 geometry gate + tests).

### Done (this branch)

- **Activated** `GameRendererPickOffsetRaycastMixin` — `@Redirect` of the single
  `Entity.raycast(DFZ)` call inside `GameRenderer.findCrosshairTarget`, backed by the
  already-proven `SlabbedOffsetRaycast`. Registered in `slabbed.client.mixins.json`.
- **Deleted** `GameRendererCrosshairRetargetMixin` (2,786 lines) + `LoweredSideSlabRetargeter`
  (249 lines) — the old per-block-type rescue lane.
- **Stripped** the slab-side torch comfort-overlay union (+ its helper) from
  `SlabSupportStateMixin` — it would feed the now-authoritative raycast a phantom slab hit.
- **Added** the fence/wall/pane outline gate in `SlabSupportStateMixin` (`getRaycastShape`
  + `getOutlineShape`): bail (don't offset) when the block is a render-zeroed connection
  block. The gate reuses the *exact* render predicate
  (`(FenceBlock||WallBlock||PaneBlock) && !isBeta35FenceWallVariantContactObject`), so
  outline and model can never disagree. On 1.21.1, fences/walls ARE contact objects (they
  lower flush); **panes** are the render-zeroed case the gate protects.
- **Ported** the 2 omitted 1.21.11 server gametests: `connectionBlockOutlineNotOffset`
  (adapted to a glass PANE — the 1.21.1 render-zeroed block — with self-validating
  fixture asserts) and `loweredFloorTorchTargetedViaOwnShape`.
- **Stubbed** `SlabbedRetargetTestHooks.findLoweredSideSlabRetarget` → `null` so the legacy
  (non-running) client gametests still compile.

### Verification (headless, all green)

- `JAVA_HOME=<temurin-21> ./gradlew compileJava compileClientJava compileGametestJava runGameTest`
  → **All 37 required tests passed** (35 prior + the 2 ported). `OffsetRaycastTargetingTest`
  now 10 methods; the pane gate + torch-own-shape both green.
- **`@Redirect` binding proven by bytecode:** `javap` on yarn `GameRenderer.findCrosshairTarget`
  shows EXACTLY ONE `Entity.raycast:(DFZ)` invoke (the other raycast is `ProjectileUtil.raycast`,
  a different descriptor) → the redirect binds uniquely and applies cleanly. This is the
  client-side proof `runGameTest` cannot give (client-only mixin).

### NOT done — needs Julia (live / deferred)

1. **Live `runClient` acceptance (REQUIRED gate).** fabric-client-gametest is broken on
   1.21.1, so the end-to-end client-pick proof is not automatable. Aim along a lowered
   terrain wall / at lowered blocks' visual surfaces; confirm the crosshair follows the
   visual surface with no jumpiness or side-hijack, and place/break works. Then re-prove
   the committed lowering/hanger cases (combined-slab chain) per "always check the chain".
2. **`ServerInteractBlockHitToleranceMixin` — KEPT as-is, deliberately.** Recon proved it is
   load-bearing SERVER placement logic (same-cell slab-merge finalize + `ofCenter`
   validation-center shift), NOT a 1.21.11-style targeting tolerance. It was NOT deleted.
   A possible later *narrowing* (drop the same-cell-merge `@Inject` that was the server
   companion of the deleted client rescue) is behavioral + headlessly-unprovable → do it as
   a SEPARATE commit only after the client cutover is live-confirmed.
3. **Cleanup-to-parity (separate slice, behaviorally inert).** 1.21.1 still ships 6
   Beta3.5/Beta4 trace mixins `required:true` + a hot-path `System.out.println`
   (`SlabSupport` BOTTOM_PERSISTENT) + `tmp/` not gitignored — 1.21.11 gates these off and
   excludes from the jar. Bring to parity before any beta tag.
4. **Merge story.** This branch diverged from `port/mc-1.21.1`. Julia's uncommitted WIP in
   `Slabbed-phase19-integrate` (the slab-held compound-visible-owner-top-slab feature) lives
   only in that root and is **likely dissolved by this overhaul** — re-evaluate its target
   case live before salvaging any of it. Decide: fast-forward `port/mc-1.21.1` onto this
   branch after live-confirm, or cherry-pick.
5. **Window/cull fix** (`BlockRenderInfoCullMixin`) is still absent on 1.21.1 — separate slice.

### Build / test

- Gametests: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew --no-daemon runGameTest`
  → expect `All 37 required tests passed`.
- Dev client (live): `./gradlew runClient` (Indigo; the bug reproduces under Indigo too).
- Do NOT add `OffsetRaycastClientGameTest` (the 1.21.11 e2e client test) — fabric-client-gametest is broken on 1.21.1.

### Gotchas

- The gate predicate MUST stay identical to `OffsetBlockStateModel.emitBlockQuads:197-202`.
  If that render exclusion changes, change the gate in lockstep or outline/model desync.
- `SlabbedRetargetTestHooks` returning null is intentional (the lane is gone); the legacy
  client gametests that call it do not run on 1.21.1.
