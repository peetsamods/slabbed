# Slabbed — Branch Handoff

> **Current state of THIS branch — overwrite freely.** Append-only *history* lives in
> [`SLABBED_SPINE.md`](SLABBED_SPINE.md). Design rationale: [`SLABBED_YSYSTEM_DESIGN.md`](SLABBED_YSYSTEM_DESIGN.md).

## Branch
- **Branch:** `claude/1211-terrain-slabs-named-surface`
- **Worktree:** `/Users/joolmac/CascadeProjects/Slabbed-claude-1211port-candidate-20260606`
- **Remote:** `peetsamods/slabbed` — **PUSHED** (`git push -u origin` done; tracks `origin/claude/1211-terrain-slabs-named-surface`).
- **HEAD:** `3739f76b` (pushed). Tree clean.
- **Minecraft:** 1.21.1 · **Loader:** Fabric 0.19.2 · **Yarn:** 1.21.1+build.3 · **Java:** 21
- **Author convention:** `Julia Schohl <joolmac@users.noreply.github.com>` + `Co-Authored-By: Claude Opus 4.8`.
- This is the **most feature-complete** Slabbed line (SlabSupport ~2400 lines). Canonical `port/mc-1.21.1`
  (in `Slabbed-phase19-integrate`) is behind + carries Julia's owner-top WIP; consolidation recommendation =
  **adopt this candidate as the release base** and forward-port canonical's few uniques.

## Strategy (agreed with Julia)
Patch 1.21.1 to a shippable beta → then build the **RESOLVER**: one authority that answers "how lowered is
this block," replacing the scattered consumers (`getYOffset` vs `loweredBottomSlabSupportDy` vs
`connectingBlockVisualDy` vs anchor-authoring) whose disagreements are the root of this whole bug class
(adversarial pass confirmed). 1.21.11 reconciliation + 1.20.1/26.1.2 after.

## Done this session (all committed + pushed, harness 97-98/98 green at each step)
- `d2aa911e` **/slabdy** dev overlay (block source + dy + LOWERED/RAISED/flush; later +`[UPPER/lower half]`).
  Toggle in-game `/slabdy`; default-on with `-Dslabbed.targetDyOverlay=true`.
- `f6ef7f99` fence/wall/pane connection break across a height step. `9ed019dc` TOP_LIKE/DOUBLE_LIKE +
  skip-offset-asymmetry characterization tripwires. `cdd27b01` Y-system design note.
- `92601c80` first placement-DODO fix → superseded by `b6352bf2`. `c8437388` reverted the
  `tsInheritLowering` prototype (`d17eacf1`) — wrong approach; the float was placement, not rendering.
- `0ea587a5` +38 combined-slab dy MATRIX tests. `bb436990` `connectingBlockVisualDy` now mirrors the
  render path (kills false fence/wall/pane step-breaks). `82917f08` **BUG1**: object on a vanilla TOP slab
  capping a TS slab compounds to −1.0 (was floating at −0.5).
- **`b6352bf2`** ★ slab **side-placement** on a lowered block lands flush: the placement remapper now
  PREDICTS the slab's own dy via `getYOffset` (safe — not inside getYOffsetInner) and picks cell+type to
  match. The recurring "too low/too high." LIVE-CONFIRMED. Also added the `/slabdy` half readout.
- **`d4dae6c5`** ★ vegetation no longer double-lowered: Slabbed defers `PlantBlock` (vegetation) to Terrain
  Slabs, which lowers veg/snow itself (`CompatHooks.isNativelyOffsetOnTop`). Stops generated grass clipping
  into TS slabs. LIVE-CONFIRMED.

## DONE — fence-side float (`3739f76b`, LIVE-CONFIRMED + pushed)
A slab placed beside a **lowered fence/wall/pane** (lowered because it stands on a TS BOTTOM_LIKE slab, e.g.
Packed Mud Slab) used to float at grid height instead of sitting flush. Two coupled causes, both fixed:
- **LOWERING** (`SlabSupport`): `isLoweredConnectingBlockCarrier` makes a lowered fence/wall/pane a
  side-support carrier (wired into `isFullHeightLoweredCarrierForSideSupport`), so a slab placed beside it
  inherits −0.5 via `getYOffset`. Harness-proven (`slabBesideStackedFenceOnTsInheritsLowering`).
- **PLACEMENT** (`BlockItemPlacementIntentMixin`): the side-hit remapper used to bail on any non-solid target
  (`target_not_solid`) → vanilla planted the slab at grid height. It now engages for a lowered connecting
  block (`targetIsLoweredConnectingCarrier`) and routes through the ordinary-lowered-full-block path:
  lower-half→BOTTOM, upper-half→TOP, placed in the neighbor cell where it inherits −0.5 and sits flush.
- Confirmed live (slab flush against lowered Spruce Fence, both halves track). Harness 98/98.

## Open (need Julia / deferred)
- **BUG A — RESOLVED (live, 2026-06-10).** Opaque full cube on a TS slab shows NO cull hole live (cull fix
  `BlockRenderInfoCullMixin` present); the −0.5 lowering is correct/by-design parity with vanilla slabs.
  Confirmed not a bug. CLEANUP: the client-gametest `TerrainSlabsDirectSupportClientGameTest` still asserts
  0 — a stale contract to flip/delete so it stops misleading (tracked as a background task).
- **BUG4** — compound stack under-lowers (−0.5 not −1.0) when the top block is placed via the GENERIC anchor
  path (piston/dispenser/`/setblock`), not the player top-face mixin. DEFERRED (rarer; delicate anchor law).
- **Release gate** = a systematic LIVE matrix pass (the 38 matrix tests + adversarial recipes make it fast).
  This is the LAST thing between the branch and a shippable beta — all major placement/render bugs are fixed
  and live-confirmed (slab-on-lowered, vegetation, fence-side, BUG A).

## Build / run
- Headless tests: `JAVA_HOME=<temurin-21> ./gradlew --no-daemon --console=plain runGameTest` (TS shim
  `terrain_slabs:test_slab`; gametest mod `provides` terrain_slabs).
- Live: `./gradlew --no-daemon runClient -Dslabbed.targetDyOverlay=true` (run/mods has terrain_slabs 3.1.2
  + architectury + midnightlib).
