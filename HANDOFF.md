# Slabbed — Branch Handoff

> **Current state of THIS branch — overwrite freely.** Append-only *history* lives in
> [`SLABBED_SPINE.md`](SLABBED_SPINE.md). Design rationale: [`SLABBED_YSYSTEM_DESIGN.md`](SLABBED_YSYSTEM_DESIGN.md).

## Branch
- **Branch:** `claude/1211-terrain-slabs-named-surface`
- **Worktree:** `/Users/joolmac/CascadeProjects/Slabbed-claude-1211port-candidate-20260606`
- **Remote:** `peetsamods/slabbed` — **PUSHED** (`git push -u origin` done; tracks `origin/claude/1211-terrain-slabs-named-surface`).
- **HEAD:** `434e7c41` (pushed). Tree clean. Version bumped to 0.4.0-beta.1; publishable jar = **0.4.0-beta.2** (re-cut pending after the hanger fix).
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

## DONE — powder-snow DODO (`95c0ab12`, LIVE-CONFIRMED + pushed)
Powder snow on a slab was lowered −0.5 while neighbouring powder snow on full ground stayed flush → a
half-block step in snowy terrain. Root cause: `isThinTopLayer` excluded only `SnowBlock`; powder snow is
`PowderSnowBlock` (a FULL CUBE) → matched the full-block-on-slab −0.5 branch. Fix: never offset
`PowderSnowBlock` (`getYOffset` + `shouldOffset` short-circuit), matching TS's own behaviour. Harness 99/99
(`powderSnowOnSlabIsNeverLowered`). Lesson: [`memory`] exclude by ROLE, not one class name. FOLLOW-UP: audit
other natural full-cube terrain fill (mud, sculk, moss block…) for the same slip (background task).

## DONE — ceiling-hanger droop + sign smoosh (`434e7c41`, LIVE-CONFIRMED + pushed)
Two release-blockers, one root: ceiling-hung blocks (hanging roots / spore / hanging sign / pale moss) were
run through "resting on a support BELOW" branches whose downward walks step through any non-air block — a
placed lantern bridged the walk to a slab 2 cells down → roots drooped −0.5; and `HangingSign` was missing
from the follow-a-lowered-support set → it smooshed +0.5 up into a lowered slab. Fix: early-dispatch always-
ceiling-hung decorations (no floor variant) at the top of `getYOffsetInner` to `ceilingHungDecorationDy()` =
dy from the support ABOVE only, bypassing all below-branches; +`HangingSign` in the follow-sets;
`loweredSlabUndersideSupportDy`→NaN for TS slabs (flush ≠ lowered support). Harness 101/101
(`hangingRootsNotLoweredByBridgedSlabBelow`, `hangingSignUnderNormalTopSlabKeepsRaisedBaseline`). Lesson:
[`memory`] ceiling hangers attach from ABOVE; downward walks bridge through placed blocks.

## Open (need Julia / deferred)
- **BUG A — RESOLVED (live, 2026-06-10).** Opaque full cube on a TS slab shows NO cull hole live (cull fix
  `BlockRenderInfoCullMixin` present); the −0.5 lowering is correct/by-design parity with vanilla slabs.
  Confirmed not a bug. CLEANUP: the client-gametest `TerrainSlabsDirectSupportClientGameTest` still asserts
  0 — a stale contract to flip/delete so it stops misleading (tracked as a background task).
- **BUG4** — compound stack under-lowers (−0.5 not −1.0) when the top block is placed via the GENERIC anchor
  path (piston/dispenser/`/setblock`), not the player top-face mixin. DEFERRED (rarer; delicate anchor law).
- **Release gate — live matrix PASSED (2026-06-10), then 2 hanger blockers found+fixed (`434e7c41`).**
  Branch is **SHIPPABLE.** `0.4.0-beta.1` was tagged/staged then re-blocked by the hangers; publishable jar =
  **`0.4.0-beta.2`** — RE-CUT PENDING: bump `mod_version` 0.4.0-beta.1→0.4.0-beta.2, `./gradlew build`, tag
  `slabbed-0.4.0-beta.2`, restage jar to `~/Library/Application Support/ModrinthApp/profiles/Slabbed 1.21.1/mods/`
  (NOT `Slabbed_` = the 1.21.11 profile), then Codex upload to Modrinth/CurseForge. Two parked KNOWN-MINOR
  (not blockers): B5 saplings won't place on a TS slab; "SBSBS" slab-beside-slab chain. Note in release notes.

## Build / run
- Headless tests: `JAVA_HOME=<temurin-21> ./gradlew --no-daemon --console=plain runGameTest` (TS shim
  `terrain_slabs:test_slab`; gametest mod `provides` terrain_slabs).
- Live: `./gradlew --no-daemon runClient -Dslabbed.targetDyOverlay=true` (run/mods has terrain_slabs 3.1.2
  + architectury + midnightlib).
