# Terrain Slabs × Slabbed — Manual Hole Check (live)

Goal: confirm live that the beta 4.1 **"culled faces / see-through terrain"** blocker is fixed.

The fix: opaque full cubes (dirt/grass/stone/…) are **no longer lowered** onto Countered's
Terrain Slabs, so they can't sink out of their voxel and tear holes. Non-opaque **objects**
(fences, torches, doors, redstone torches) still lower to sit flush on the slab. Vanilla
slab behaviour is unchanged.

Branch under test: `compat/mc1211-terrain-slabs-named-surface-support` (commit `b32aca1d` + this fix),
in `/Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest`.

---

## 1. Launch with BOTH mods loaded

**Option A — dev runtime (recommended; auto-loads both):**

```
cd /Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest
./gradlew runClient
```

This loads **Slabbed** (the dev build with the fix) + **Countered's Terrain Slabs 2.2.5** +
**MidnightLib** (its dependency) via `modLocalRuntime`.

**Confirm both are actually loaded before testing:**
- Title screen → **Mods** — the list must show **Slabbed** *and* **Terrain Slabs** (and MidnightLib).
- Or watch the launch log for `terrainslabs` in the loaded-mods line.
- If Terrain Slabs is missing, the slabs below won't exist in the creative search — stop and fix the runtime.

> Option B (your real install): the ModrinthApp "Slabbed+Terrain Slabs" profile works too,
> **but** only if its Slabbed jar is rebuilt from this fix (`./gradlew build` → `build/libs/`),
> otherwise you're testing the old blocked build.

---

## 2. World setup

1. **Create New World → Creative**, world type **Superflat**, Peaceful.
2. In‑world: `/time set day` and `/weather clear` (clean lighting makes holes obvious).
3. Grab materials from the creative inventory (search "slab" for the `terrainslabs:*` slabs), or:
   ```
   /give @s terrainslabs:grass_slab 64
   /give @s terrainslabs:dirt_slab 64
   /give @s terrainslabs:sand_slab 64
   ```

**How to inspect for holes:** press **F5** to orbit the camera, and look from **low / grazing
angles** and **along the slab's top surface**. A "hole" = a see‑through gap, void, or black
where a block/slab face should be. Switching to **Spectator** (`/gamemode spectator`) lets you
fly *under* and *inside* the build to check the worst angles.

---

## 3. Scenarios — build, then inspect

### A. THE FIX — opaque full cube on a terrain bottom slab  ⭐ primary check
1. Place a `terrainslabs:grass_slab` (it places as a **bottom** slab) on the ground.
2. Place a **`dirt`**, a **`grass_block`**, and a **`stone`** block — one on top of each of three grass_slabs.
3. Repeat on `terrainslabs:dirt_slab` and `terrainslabs:sand_slab` bottoms.

- ✅ **PASS:** each full cube sits at **normal grid height** on top of the slab (a clean half‑step up),
  **all six faces solid**, no see‑through from any angle.
- ❌ **FAIL (the old bug):** the cube visually **sinks ~½ block** and you can see **through** its
  lower side faces and/or the adjacent slab faces (void / see‑through terrain).

### B. Objects still lower (should sit flush on the slab)
1. On a `terrainslabs:grass_slab` (bottom), place: **`oak_fence`**, **`torch`**, **`oak_door`**, **`redstone_torch`**.

- ✅ **PASS:** each sits **visually lowered** so its base rests on the slab's top surface
  (not floating a half‑block above it), no holes, and the selection outline hugs the visible shape.

### C. Terrain‑building scene (the real‑world stress) ⭐ primary check
1. Build a small terraced patch: mix `terrainslabs:grass_slab` (bottom) with full `grass_block` /
   `dirt` placed **on** slabs and **beside** each other at a couple of different heights (a little hill / steps).
2. Walk around it; look from **below**, from **grazing eye level**, and straight down.

- ✅ **PASS:** continuous solid terrain — no see‑through windows between cubes and slabs.
- ❌ **FAIL:** any persistent see‑through gap where two surfaces meet.

### D. Control — vanilla slab unchanged
1. Place a vanilla `minecraft:stone_slab` (bottom), put a `stone` block on top.

- ✅ **PASS:** the stone **still lowers** onto the vanilla slab (Slabbed's normal behaviour).
  This proves the fix is Terrain‑Slabs‑only and didn't touch vanilla slabs.

---

## 4. Checklist

- [ ] Both mods confirmed loaded (Slabbed **and** Terrain Slabs)
- [ ] **A** — full cubes (dirt/grass/stone) on terrain slabs: grid height, **no see‑through**
- [ ] **B** — fence / torch / door / redstone torch on terrain slabs: lowered flush, no holes
- [ ] **C** — mixed terrace: **no see‑through from any angle** (incl. spectator under/inside)
- [ ] **D** — vanilla `stone_slab` + `stone`: still lowers (unchanged)

---

## Notes

- The fix only stops **opaque full cubes** from lowering onto Terrain Slabs. Objects
  (fences/torches/doors/etc.) and non‑opaque full blocks (ice/glass/slime) are unaffected.
- If you find a hole in **A** or **C**, note the exact block + terrain‑slab variant + camera
  angle and grab a screenshot — that would point at a specific terrain‑slab model whose
  geometry differs from the `grass/dirt/sand` family that's covered by the automated proofs.
- Automated backing (already green, Terrain Slabs loaded):
  `./gradlew runClientGameTest` with
  `JAVA_TOOL_OPTIONS="-Dslabbed.terrainSlabsDirectSupportRedOnly=true"` (opt‑out proof) and
  `…=-Dslabbed.terrainSlabsLivePlacementProof=true` (live‑placement proof).
