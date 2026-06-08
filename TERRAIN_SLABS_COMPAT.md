# 1.21.1 — Terrain Slabs compat: named-surface direct support (first cut)

> **STATUS: headless-green (compiles + 37 server gametests pass), NOT live-confirmed.**
> The named-surface path is **inert without the Terrain Slabs mod** (`customSlabSurfaceKind`
> returns `NONE`), so the non-TS path is byte-identical — that's why the existing tests still
> pass and prove no regression. Correctness on real Terrain Slabs surfaces needs a `runClient`
> pass **with a 1.21.1-compatible Terrain Slabs jar installed**.

## Branch
`claude/1211-terrain-slabs-named-surface` (off the proven overhaul `claude/1211-targeting-overhaul-activate`). NOT pushed.

## What this adds
Before: the candidate already had the **subtractive** compat (`shouldSkipOffset` /
`shouldSkipSlabSupport` → Slabbed stays out of Terrain Slabs' way). It did **not** lower
anything *onto* Terrain Slabs surfaces.

Now: the **named-surface direct-support** layer — objects/blocks placed on a Terrain Slabs
`terrainslabs:*_slab` BOTTOM_LIKE surface lower **−0.5** to sit flush, exactly as on a vanilla
bottom slab.

### Changes
- NEW `compat/CompatSlabSurfaceKind.java` (enum) + `TerrainSlabsCompat.customSlabSurfaceKind`
  (+ `isNamedCustomSlabSurface`/`propertyEquals`/`propertyValueName`) + `CompatHooks.customSlabSurfaceKind`
  dispatch. Recognises only real `terrainslabs:*_slab` / `*_slab_bottom` states (excl.
  waterlogged/snowy), classified BOTTOM/TOP/DOUBLE_LIKE. Copied from the shipped 0.3.0-beta.1
  reference (MOD_ID `terrainslabs`).
- `SlabSupport`: three **gated, additive** extensions to the existing column-walk helpers —
  `hasBottomSlabBelow`, `hasSlabInColumn`, `slabColumnYOffset` — each now also treats a
  `customSlabSurfaceKind == BOTTOM_LIKE` block as a bottom-slab-equivalent support (checked
  *before* the `instanceof SlabBlock` early-exits, since TS slabs are SlabBlock instances).
  This routes TS-supported objects through the **existing proven −0.5 path** rather than a
  parallel method tree — a cleaner adaptation than the 1.21.11 reference (whose SlabSupport
  had a different structure). Stacking (object-on-object-on-TS-slab) works for free via the
  existing column walk.
- `build.gradle`: auto-detecting `modLocalRuntime` block — drop a TS jar + midnightlib into
  `run/mods/` and they're picked up; inert/ build-safe when absent.

## Verified (headless)
`runGameTest` → **All 37 tests pass**; compiles (main+client+gametest). The TS path is gated
on `customSlabSurfaceKind != NONE` (requires the mod loaded), so without TS the engine is
unchanged — the green tests prove no regression to the targeting overhaul, hanger-follow, or
mixed-slab lowering.

## To live-test (Julia)
1. Put a **1.21.1-compatible** Terrain Slabs jar (mod id `terrainslabs`) + `midnightlib` into
   `run/mods/`. ⚠️ `countereds_terrain_slabs-2.2.5.jar` in the sibling roots is the **1.21.11**
   build — confirm a 1.21.1 build first (it must load on MC 1.21.1).
2. `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runClient`.
3. Place a full block / object on a `terrainslabs` bottom slab → it should sit flush (−0.5),
   like on a vanilla bottom slab. Stack another on top → also flush.

## First-cut scope / known gaps (iterate live)
This cut nails the **headline BOTTOM_LIKE direct + column** case. Not yet wired (deliberately,
to keep the first cut low-risk — extend after live confirmation):
- **TOP_LIKE / DOUBLE_LIKE** surfaces (the +1.0 cases — objects on a TS top/double slab; and
  ceiling support / hangers under a TS top slab). `getDirectObjectSupportTopOffset` semantics
  exist in the reference; not yet consumed here.
- **Vanilla slab placed on a TS slab** (the `getYOffsetInner` slab-self branch wasn't extended).
- **Double-block UPPER half** on a TS slab (`shouldOffset` line ~934 still uses the vanilla
  `isBottomSlab(pos.down(2))`); the LOWER half + beds already flow through the TS-aware
  `hasBottomSlabBelow` / `hasSlabInColumn`.
- The window/cull fix's `isSlabHeightStepFace` gate (on the cull-fix branch) uses generic
  `getYOffset<0`; once named surfaces are live-proven, decide whether to make it TS-aware for
  parity with 1.21.11.
