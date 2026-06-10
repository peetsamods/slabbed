# Slabbed — Branch Handoff

> **Current state of THIS branch — overwrite freely.** Append-only *history* lives in
> [`SLABBED_SPINE.md`](SLABBED_SPINE.md). Design rationale: [`SLABBED_YSYSTEM_DESIGN.md`](SLABBED_YSYSTEM_DESIGN.md).

## Branch
- **Branch:** `claude/1211-terrain-slabs-named-surface`
- **Worktree:** `/Users/joolmac/CascadeProjects/Slabbed-claude-1211port-candidate-20260606`
- **Remote:** `peetsamods/slabbed` — **PUSHED** (`git push -u origin` done; tracks `origin/claude/1211-terrain-slabs-named-surface`).
- **HEAD:** `d4dae6c5` (pushed). **Uncommitted:** the in-flight fence-side carrier fix (see below).
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

## IN FLIGHT — fence-side float (uncommitted; do NOT commit until live-confirmed)
A slab placed beside a **lowered fence** floats instead of sitting flush. The fence is lowered because it
stands on a TS BOTTOM_LIKE slab (e.g. Packed Mud Slab) — confirmed via `/slabdy`.
- **Re-applied:** `isLoweredConnectingBlockCarrier` in `SlabSupport` (a lowered fence/wall/pane is a
  side-support carrier) + `slabBesideStackedFenceOnTsInheritsLowering` test. **Harness PROVES** the slab
  inherits −0.5 (98/98, incl. Julia's exact 2-fence-on-TS-slab geometry).
- **But live still floated** → root is the **PLACEMENT**, not the lowering: `BlockItemPlacementIntentMixin`'s
  remapper only engages for SOLID targets, so a fence target (non-solid) bails → the slab lands a cell off.
- **Next:** Julia to `/slabdy` the PLACED slab (cell + dy) → confirms the off-cell → extend the placement
  remapper to handle non-solid (fence/wall/pane) lowered targets. Files dirty: `SlabSupport.java`,
  `TerrainSlabsCompatTest.java`.

## Open (need Julia / deferred)
- **BUG A** — opaque full cube lowers −0.5 on a TS slab (parity with vanilla slabs by design). The
  client-gametest `TerrainSlabsDirectSupportClientGameTest` asserts 0 — likely a STALE contract. Needs a
  live cull-hole check (cull fix `BlockRenderInfoCullMixin` present on this branch); if clean, delete/flip
  the stale contract.
- **BUG4** — compound stack under-lowers (−0.5 not −1.0) when the top block is placed via the GENERIC anchor
  path (piston/dispenser/`/setblock`), not the player top-face mixin. DEFERRED (rarer; delicate anchor law).
- **Release gate** = a systematic LIVE matrix pass (the 38 matrix tests + adversarial recipes make it fast).

## Build / run
- Headless tests: `JAVA_HOME=<temurin-21> ./gradlew --no-daemon --console=plain runGameTest` (TS shim
  `terrain_slabs:test_slab`; gametest mod `provides` terrain_slabs).
- Live: `./gradlew --no-daemon runClient -Dslabbed.targetDyOverlay=true` (run/mods has terrain_slabs 3.1.2
  + architectury + midnightlib).
