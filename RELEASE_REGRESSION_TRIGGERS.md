# Slabbed — Release Regression Triggers

**Purpose.** A permanent, per-release checklist of the "standard tricky triggers" that keep
regressing — especially across ports (the same bug class gets independently rediscovered on
every loader/version fork). Run/verify these before ANY release or port sign-off. Each row
says whether it is **AUTO** (a headless gametest asserts it — just run the suite) or **LIVE**
(needs a human / recorder / video because it is a render-thread or particle or transient
effect a gametest cannot observe).

**How to run the AUTO half:** `./gradlew runGameTest` (server) — every AUTO row below maps to a
named gametest. A green suite means those invariants hold. Re-run on every port BEFORE calling
a fix "ported".

**How to run the LIVE half:** load the staged jar (Modrinth jar-swap) with `/slabdy record` on,
build the fixture, and read the overlay / grep `session.jsonl` for `"suspect":"true"`. Items
marked LIVE-ONLY cannot yet be auto-detected — see "Recorder coverage gaps" at the bottom for
what would close them.

Legend: ✅ fixed + covered · ⚠️ known-open · 🔁 recurs-on-port (verify every port) · TS = needs Terrain Slabs

---

## 1. Never-pop / WYSIWYG — a placed block STAYS where placed (dy is absolute)

| Trigger | Expected | Status | Test |
|---|---|---|---|
| Fence/wall/pane/gate placed lowered on a slab | stays −0.5, never pops when a neighbour changes | ✅🔁 | AUTO `FenceNeverPopTest` |
| Fence/wall/gate placed FLAT | stays 0.0; a slab placed under/beside later can't pull it down | ✅🔁 | AUTO `FenceNeverPopTest` (flat lane) |
| Full block / slab placed lowered or flat | stays at placed dy | ✅🔁 | AUTO `SlabbedLabFixtureTest` + `NeverPopMatrixTest` |
| Every block category (full/fence/wall/pane/gate) never-pops on support removal | placed dy stays | ✅🔁 | AUTO `NeverPopMatrixTest` |
| **Hopper/chest/furnace (block entity) placed lowered on a slab** | height-locked, does NOT snap when the column below changes | ✅🔁 | AUTO `BlockEntityNeverPopTest` (server half — commit 7f213cb1) |
| Flat-placed hopper not pulled down by a slab added under it | stays flat | ✅🔁 | AUTO `BlockEntityNeverPopTest` |
| **State-change jitter** — grass-block tower, grass→dirt conversion | the in-place transform must NOT strip the height-lock (dy stays) | ✅🔁 | AUTO `StateChangeAnchorTest` (server FIX — commit 78ec0aa4); residual client re-render transient is LIVE-only |
| **Snap on placement (client)** — block shown at grid for a split second then settles | placed at final dy immediately | ⚠️🔁 | LIVE-ONLY (irreducible one-frame client placement-prediction gap; server dy is now correct/locked) |

## 2. Visual triad — model == outline == raycast (WYSIWYG targeting)

| Trigger | Expected | Status | Test |
|---|---|---|---|
| Fence/wall/pane on a vanilla slab | model + outline + raycast all lowered together | ✅🔁 | AUTO `SlabConnectionSteppedTest`, `OffsetRaycastTargetingTest` |
| **Two same-dy blocks render at the SAME height** (e.g. two lowered fence gates) | equal dy ⇒ equal rendered height | ⚠️ | LIVE-ONLY today (recorder captures dy, not model height — see gaps) |
| Lowered top slab / anchored slab | outline follows (top slab base 0.5 → minY 0.0 is correct) | ✅ | AUTO `AnchoredSlabTriadTest` |

## 3. Smoosh — double-offset / lowered too far (esp. under Terrain Slabs)

| Trigger | Expected | Status | Test |
|---|---|---|---|
| Hanging sign / roots UNDER a TS slab | hang flush; do NOT smoosh/clip UP into the TS (works on vanilla) | ✅🔁 TS | **AUTO `SmooshUnderTerrainSlabsTest`** (spec row `CH-TS`, commit `3d9be2e8`). Root cause was `SlabSupport.ceilingHungDecorationDy`'s cursor walk calling `isTopLikeCeilingSurface(cur)` unguarded — a TS surface classifies TOP_LIKE, returning +0.5. Fixed: guard the walk with `!CompatHooks.shouldSkipOffset(cur)`. **NOTE:** the earlier "can't headless-verify — shim is a vanilla slab" claim was WRONG — the `TerrainSlabsTestShim` registers real `terrain_slabs:*` blocks, so this is RED/GREEN-pinned through the actual `TerrainSlabsCompat` classification. |
| Dripstone into a vanilla slab | not smooshed too high (combining itself is OK) | ⚠️🔁 | LIVE-ONLY |
| Lantern on a TS surface | single −0.5, not double-offset (TS already wraps it) | ✅🔁 TS | AUTO `SlabbedDiagnostics` SMOOSH flag; LIVE confirm |
| Chain + lantern hanging stack | same dy, no vertical gap | ✅🔁 | AUTO `SlabbedDiagnostics` GAP flag |

## 4. DODOs — see-through "doom infinity windows"

| Trigger | Expected | Status | Test |
|---|---|---|---|
| Opaque full cube onto a vanilla/TS slab | must NOT lower (world-hole guard); stays flush | ✅🔁 | AUTO (isSlabSitCandidate excludes opaque cubes) |
| Opaque cube lowered by anchor (copper column) | DODO risk flag | ⚠️ | AUTO `SlabbedDiagnostics` DODO flag; LIVE confirm holes |
| Grass tower → dirt conversion | no DODO/hole opens on state change | ⚠️🔁 | needs AUTO + LIVE |

## 5. Particles — must follow the lowered model

| Trigger | Expected | Status | Test |
|---|---|---|---|
| Lowered redstone torch | dust particles emit at the LOWERED position, not grid height ("particles too high") | ✅🔁 | FIX landed (commit a414ad3d: RedstoneTorch/WallRedstoneTorch particle mixins). Particles are client-render → the offset math is proven but the rendered result is LIVE-only |
| Lowered floor torch flame | flame follows the lowered post | ⚠️🔁 | LIVE-ONLY |

## 6. Redstone

| Trigger | Expected | Status | Test |
|---|---|---|---|
| Redstone wire over/across slabs | propagates normally | ⚠️ (deferred) | LIVE |
| Redstone connection shape and directional output beside full blocks, components, slab steps, and obstructions | ordinary solids never create phantom arms/power; same-level, rise, drop, occlusion, headroom, and directional-component facing remain vanilla-correct | ✅ | AUTO `RedstoneWireConnectionTest` (GH #37) |
| Redstone over air after support removed | rejects air as support / pops per vanilla | ✅ | AUTO `RedstoneWireConnectionTest#dustPopsWhenItsSupportIsRemoved` |

---

## Recorder coverage gaps (what would let you STOP sending video)

The live recorder (`/slabdy record`) catches dy / outline / DODO / smoosh / gap per target.
It does NOT yet catch these — closing them is the path to "the recorder is enough":

1. **Rendered model height** — `modelDy` is `missing` because the render trace is only armed by
   `/slabdy row`, not passively. Arm it passively on target-change and MODEL_MISMATCH fires for
   the "same dy, different rendered height" class (the fence-gate issue). *Tractable.*
2. **Snaps** — a transient dy at placement that settles a frame later. The recorder logs the
   final state only. Capture the placement-frame dy vs the settled dy and flag a delta. *Tractable.*
3. **Particles** — no capture. Particle emit position vs model dy. *Harder (client particle hook).*
4. **State-change jitter** — a setBlockState at the same position (grass→dirt) re-triggering
   lowering. Capture consecutive dy at an unchanged position across a state swap. *Tractable via
   the existing dy-over-time signature.*

---

_Maintained because these regress on every port. If you fix one, add/greenlight its AUTO row so
the next port catches it for free._
