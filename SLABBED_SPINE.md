# SLABBED_SPINE

This file is the current repo-local truth for Codex. Keep it short. Update it after every proof-confirmed or live-confirmed savepoint.

## Current active root

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

Known alternate active port root when explicitly working MC 26.1.2:

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Stop if the actual root does not match the intended current root.

## Current branch

```text
port/mc-1.21.1
```

## Current known-good savepoint

Commit:

```text
94a5643e
```

Tag:

```text
(untagged — local commit on port/mc-1.21.1)
```

Pushed branch: no (local only — not yet pushed)
Pushed tag: n/a

Live-confirmed 2026-06-03: decorative-hanger follow-down under lowered FULL blocks
AND lowered TOP slabs (SlabSupport.java; lantern/soul lantern/spore blossom/
hanging roots/pale hanging moss, chains excluded). Supersedes prior tagged+pushed
savepoint `eab0880a` (tag `save/mc1211-sbbs-underside-pre-manual-testing`), which
remains in history. Note: the working tree still carries the uncommitted
`LoweredSideSlabRetargeter.java` WIP (deliberately not in 94a5643e).

## Current objective

The SBBS underside automation fixtures are saved and pushed. Keep the authority order current, preserve the savepoint, and prepare the next manual live acceptance rerun for the slab-held lowered-side rescue lane.

## Current blocker

Visible symptom:

```text
Manual slab-held live acceptance has not yet been re-proven after the savepoint.
```

Failing layer:

```text
proof gap
```

Protected invariant:

```text
The new SBBS markers must come from the manual runClient lane and not from gameplay changes.
```

Latest proof:

```text
compileGametest and runClientGameTest passed for the SBBS underside fixture update; commit and annotated tag were created and pushed.
```

Live status:

```text
manual rerun pending
```

## Next legal slice

Type:

```text
manual-proof
```

Allowed files:

```text
run/logs/latest.log, build/run/clientGameTest/logs/latest.log
```

Forbidden files:

```text
src/**, build.gradle, settings.gradle, gradle.properties, fabric.mod.json, *.mixins.json, release/version/changelog files
```

Required proof:

```text
Manual `runClient` pass with a slab in hand on the lowered-side rescue setup, followed by log classification from `run/logs/latest.log`.
```

Stop condition:

```text
Unexpected live lane mismatch, any gameplay edit, or any non-doc file touched.
```

## Do not touch boundaries

- Do not touch culling unless fresh RED says culling.
- Do not promote Terrain Slabs into generic Slabbed support.
- Do not broaden rescue without RED proof.
- Do not move release tags unless explicitly running release correction.
- Do not use dirty/archive roots unless recovery is explicitly requested.
- Do not edit multiple layers in one slice.

---

## Savepoint 2026-06-07 (overnight, autonomous) — 1.21.1 TARGETING OVERHAUL ACTIVATED

Branch: `claude/1211-targeting-overhaul-activate` (off `43b5eadc` → off committed HEAD
`20a5ac28`). Commit: `73a0af4b`. NOT pushed, NOT merged, savepoint `94a5643e` untouched.

Activated the offset-aware nearest-hit raycast as the single 1.21.1 targeting authority
(mirrors proven 1.21.11 overhaul `39a345e7`); deleted the old DDA-rescue lane
(GameRendererCrosshairRetargetMixin 2786L + LoweredSideSlabRetargeter 249L); stripped the
slab-torch comfort overlay; added the fence/wall/pane outline gate (mirrors
OffsetBlockStateModel.emitBlockQuads exactly — on 1.21.1 panes are render-zeroed,
fences/walls are contact objects that lower flush); ported the 2 omitted server gametests.
KEPT ServerInteractBlockHitToleranceMixin (load-bearing server placement, NOT a targeting
tolerance) + BlockItemPlacementIntentMixin. Net −2960 lines.

Proof (headless): `runGameTest` → All 37 required tests pass (35 + 2 ported). `javap` on
yarn `GameRenderer.findCrosshairTarget` → exactly one `Entity.raycast(DFZ)` so the
`@Redirect` binds uniquely. NOT live-confirmed (fabric-client-gametest broken on 1.21.1) →
final client-pick acceptance is Julia's `runClient`. See branch `HANDOFF.md`.

Next legal slices (separate, in order): (1) Julia live `runClient` accept + re-prove the
combined-slab chain; (2) window/cull fix (BlockRenderInfoCullMixin, absent on 1.21.1);
(3) cleanup-to-parity (gate-off trace mixins + jar exclude, hot-path println, gitignore
tmp/) — deliberately deferred so the live trace tooling stays available for Julia's
morning confirmation; (4) optional ServerInteractBlockHitToleranceMixin narrowing
(post-live-confirm only); (5) merge story onto port/mc-1.21.1 (Julia's WIP likely dissolved).

---

## Savepoint 2026-06-08 — Terrain Slabs named-surface direct support (first cut) [headless-green]

Branch `claude/1211-terrain-slabs-named-surface` (off the overhaul branch). Added the
named-surface direct-support compat: CompatSlabSurfaceKind enum + TerrainSlabsCompat.customSlabSurfaceKind
+ CompatHooks dispatch (from shipped 0.3.0-beta.1, MOD_ID `terrainslabs`), and three gated
additive extensions in SlabSupport (hasBottomSlabBelow, hasSlabInColumn, slabColumnYOffset)
so objects/columns on a TS BOTTOM_LIKE surface lower −0.5 via the existing proven path.
build.gradle auto-detects a TS jar in run/mods. Compiles + All 37 server gametests pass
(inert without the mod → non-TS path byte-identical → no regression). NOT live-confirmed:
needs a 1.21.1-compatible Terrain Slabs jar + runClient. First-cut gaps (iterate live):
TOP_LIKE/DOUBLE_LIKE, vanilla-slab-on-TS, double-block upper. See TERRAIN_SLABS_COMPAT.md.

## Savepoint 2026-06-10 — 1.21.1 patches toward shippable, PUSHED to peetsamods/slabbed

Branch `claude/1211-terrain-slabs-named-surface` is now PUSHED (first push; HEAD `d4dae6c5`).
Strategy locked with Julia: patch 1.21.1 to shippable, THEN build the RESOLVER (one authority
for "how lowered is this block") — the adversarial pass proved that multiple functions
disagreeing about lowering is the root of the whole bug class. Sequence committed + pushed:

- /slabdy overlay (`d2aa911e`, + `[UPPER/lower half]` readout in `b6352bf2`); fence/wall/pane
  step-break (`f6ef7f99`); TOP_LIKE characterization (`9ed019dc`); Y-system design note
  (`cdd27b01`); +38 matrix tests (`0ea587a5`); connectingBlockVisualDy↔render sync (`bb436990`).
- BUG1 — object on a vanilla TOP slab capping a TS slab → −1.0 (`82917f08`, was floating −0.5).
- ★ Slab side-placement on a lowered block lands flush (`b6352bf2`): placement remapper now
  PREDICTS the slab's own dy via getYOffset and picks cell+type to match (mini-resolver). The
  recurring "too low/too high." LIVE-CONFIRMED.
- ★ Vegetation no longer double-lowered (`d4dae6c5`): Slabbed defers PlantBlock to Terrain Slabs
  (which lowers veg/snow itself). Stops generated grass clipping into TS slabs. LIVE-CONFIRMED.
- Reverted the tsInheritLowering prototype (`d17eacf1` → `c8437388`) — float was placement, not render.

Separately: 0.3.0-beta.2 HOTFIX shipped on the core repo (`Slabbed`, branch
`hotfix/0.3.0-beta.2-terrain-slabs-modid` @ `00b6a7c4`, tag `slabbed-0.3.0-beta.2`): detect both
`terrain_slabs` + legacy `terrainslabs` mod ids (shipped beta.1 was inert against modern TS). Jar
in ModrinthApp profile + staged for Codex upload. 1.21.11 compat branch: BUG1 ported (`908a3ea3`);
worktree branches `parity-1211-wt` (safe column-lowered-carrier lane) + `adv-1211-compat-wt` (BUG A repro).

IN FLIGHT (uncommitted): fence-side float — slab beside a lowered fence floats. Carrier fix
(`isLoweredConnectingBlockCarrier`) is harness-proven (98/98) but live still floats → root is the
PLACEMENT remapper bailing on non-solid (fence) targets. Awaiting Julia's /slabdy on the placed slab.
Open: BUG A (likely stale TerrainSlabsDirectSupportClientGameTest contract; needs live cull check),
BUG4 (compound via generic anchor path) deferred. Release gate = live matrix pass.

---

## Savepoint 2026-06-10 (afternoon) — fence-side float RESOLVED

`3739f76b` (pushed). The fence-side float is fixed end-to-end and LIVE-CONFIRMED (slab flush against a
lowered Spruce Fence, both halves track). Root was two coupled gaps: (1) lowering — a lowered fence/wall/
pane is now a side-support carrier (`isLoweredConnectingBlockCarrier`), so a slab beside it inherits −0.5;
(2) placement — the side-hit remapper bailed on non-solid targets (`target_not_solid`) and let vanilla
plant the slab at grid height; it now engages for a lowered connecting block (`targetIsLoweredConnecting-
Carrier`) and routes through the ordinary-lowered-full-block path (lower→BOTTOM, upper→TOP, neighbor cell
inherits −0.5). Julia's two /slabdy readings (fence at 24,119,8 + 24,120,8 both dy=−0.500 LOWERED) proved
the lowering was already correct and isolated the bug to placement. Harness 98/98. Tree clean at HEAD.
Open unchanged: BUG A (likely-stale cull contract, live check), BUG4 (deferred). Next: RESOLVER + live matrix.

---

## Savepoint 2026-06-10 (late afternoon) — BUG A dismissed; release gate = live matrix

Julia live-checked BUG A: opaque full cube on a TS slab shows NO cull hole (cull fix present); the −0.5
lowering is correct by-design. **BUG A is not a bug** — only cleanup left is flipping the stale
`TerrainSlabsDirectSupportClientGameTest` assertion (tracked as a background task). With fence-side,
slab-on-lowered, vegetation, and BUG A all resolved + live-confirmed, the ONLY remaining gate to a
shippable 1.21.1 beta is a systematic LIVE matrix pass. BUG4 still deferred. Next big rock = the RESOLVER.

---

## Savepoint 2026-06-10 (evening) — powder-snow DODO fixed (release blocker)

`95c0ab12` (pushed, LIVE-CONFIRMED). Julia hit a half-block step ("DODO") in snowy grove terrain.
First mis-diagnosed (by me + a 3-agent fan-out) as vanilla powder-snow pits / a Terrain Slabs
render-offset quirk — WRONG. Her /slabdy read `Powder Snow · VANILLA · dy=-0.500 LOWERED`: the
VANILLA tag = Slabbed's own getYOffset lowering it. Root cause: `isThinTopLayer` excluded only
`SnowBlock`; powder snow is `PowderSnowBlock` (a FULL CUBE), so it matched the full-block-on-slab
−0.5 branch and stepped vs flush neighbours. Fix: never offset PowderSnowBlock (getYOffset +
shouldOffset short-circuit), matching what TS itself does. Harness 99/99 (added
`powderSnowOnSlabIsNeverLowered` with a stone-on-slab control). Lesson recorded:
[[slabbed-exclusion-by-behavior-not-classname]] — exclude by role not one class; trust the overlay
source tag; agent fan-outs can be wrong in unison under a shared wrong framing. Follow-up: audit
other full-cube terrain fill (mud/sculk/moss). Release gate unchanged: live matrix pass.

---

## Savepoint 2026-06-10 (night) — LIVE MATRIX PASSED, branch SHIPPABLE

Julia walked the full live matrix (A single-lowering, B vegetation, C fence-side, D side-placement,
E compound/stacking, F render sweep) — **ALL GREEN.** The 1.21.1 candidate
`claude/1211-terrain-slabs-named-surface` has cleared every blocker and is SHIPPABLE. Remaining work is
purely release mechanics: version bump + build jar + tag + Codex upload to Modrinth/CurseForge. Two parked
KNOWN-MINOR (agreed non-blockers): B5 saplings won't place on a TS slab; SBSBS slab-beside-slab chain.
Post-ship: the RESOLVER, then 1.21.11 reconciliation + 1.20.1/26.1.2 ports.
