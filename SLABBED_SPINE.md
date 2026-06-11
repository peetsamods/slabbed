# SLABBED_SPINE — Running Log

> **APPEND-ONLY history.** Add a new entry at the **top** for each proof-confirmed or
> live-confirmed savepoint or milestone. **Never edit or delete prior entries** — they are
> the permanent record of how the project got here.
>
> ⚠️ Current state of a branch does **NOT** go here — it lives in that branch's
> [`HANDOFF.md`](HANDOFF.md). This file is *history*; `HANDOFF.md` is *now*. That split is
> what stops current-state data from clobbering the log.

---

## 2026-06-10 — Terrain Slabs world-hole "DODO" fixed (CompatHooks dual-mod-id)

- **Branch:** `release/mc1.21.11-0.4.0-beta.3` (off the released hotfix `eab0d786`) · **HEAD:** `42002295` · **MC:** 1.21.11 · pushed to `peetsamods/slabbed`.
- **Symptom:** see-through world holes ("DODOs") scattered across natural Stone/Dirt terrain whenever Terrain Slabs is loaded — present in the **shipped `0.4.0-beta.3` jar**. `/slabdy` over a holey block confusingly read `dy=0.000` (main thread) while the render worker shifted it `-0.5`.
- **ROOT CAUSE (one line):** `CompatHooks` gated every TS-compat hook on `isModLoaded(TerrainSlabsCompat.MOD_ID)` — the **modern id `terrain_slabs` ONLY**, never `LEGACY_MOD_ID "terrainslabs"`. So TS slab blocks (`terrainslabs:gravel_slab`, `terrain_stone_slab`, …) were NOT recognised → treated as vanilla bottom slabs → the slab-column walk lowered the natural terrain sitting above them `-0.5` → the chunk mesher culls at the un-shifted voxel → holes.
- **Why it hid:** the dev test env loaded the **legacy** TS id while the real release loads the **modern** id — so the compat gate failed in one and "worked" in the other, masking the bug from BOTH automated tests and dev play. (See the false-green tests below.)
- **Fixes (`42002295`):** (1) `CompatHooks` gates on `MOD_ID || LEGACY_MOD_ID`. (2) `hasSlabInColumn`/`slabColumnYOffset` STOP at a solid full cube (never tunnel through solid terrain to a deep slab; placed towers still chain via per-block anchors). (3) `getYOffsetInner` ceiling guard pins opaque cubes flush via view-independent `isOpaqueFullCube()` (not region-clamped `isSolidBlock(world,pos)`). (4) `isSlabSitCandidate` excludes generic opaque cubes from the TS direct-support path.
- **Net behaviour:** generic natural terrain (stone/dirt/grass) STAYS FLUSH on Terrain Slabs; curated objects + non-cube things still lower to form combined slabs. The proven principle (Notion BB#41/#48, compat-line `cb0a950c`/`1769e02f`): **lower objects, NOT terrain — never lower an opaque full cube onto a TS surface** (the only cull-redraw is renderer-specific/Indigo-only and is dead under the release's Sodium).
- **Proof:** 58/58 gametests green (two false-green tests that asserted "grass/stone must lower -0.5 onto TS" were corrected to assert flush). LIVE-CONFIRMED by Julia: DODOs gone across the TS stone hillside.
- **LESSONS (so this never happens again):** ① any new mod-id MUST be added to **both** the loaded-check AND every namespace gate — prefer one `isLoaded()`/`isTerrainSlabsNamespace()` helper over scattered `isModLoaded(MOD_ID)` calls. ② Dev runtime MUST load the **same Terrain Slabs build/mod-id as the shipped release** (or test both ids) or compat bugs hide. ③ Never lower an **opaque full cube** onto a Terrain Slabs surface — it tears holes. ④ A `dy` that differs between `ClientWorld` and `ChunkRendererRegion` = use a view-independent test (`isOpaqueFullCube()`), never `isSolidBlock(world,pos)`. ⑤ A test that asserts the *hole-causing* behaviour is a **false green** — when behaviour is corrected, fix the assertion, don't trust the green.

---

## 2026-06-09 — `0.3.0-beta.2` released: Terrain Slabs mod-id hotfix

- **Branch:** `hotfix/0.3.0-beta.2-terrain-slabs-modid` · **HEAD:** `00b6a7c4` · **MC:** 1.21.11
- Restored the optional Terrain Slabs compatibility layer for current installs by detecting the modern `terrain_slabs` mod id as well as legacy `terrainslabs`; before this hotfix the base slab behavior worked, but the TS compat path stayed inert against 3.x.
- Added `TerrainSlabsHotfixTest` + `TerrainSlabsTestShim` to prove modern-id classification/lowering and keep the legacy-id fixtures alive.
- **Proof:** 54/54 headless tests green at tag `slabbed-0.3.0-beta.2`; modern `terrain_slabs:test_slab` classifies `BOTTOM_LIKE` and lowers supported blocks by `-0.5` again.
- **Scope note:** this fixed compat detection only; the deferred terrain-render / deep-chaining limits from `0.3.0-beta.1` remain unchanged.

---

## 2026-06-07 — `0.3.0-beta.1` released: Terrain Slabs compatibility

- **Branch:** `claude/terrain-targeting-overhaul-20260606` · **HEAD:** `9f85d8b6` · **MC:** 1.21.11
- Shipped **Countered's Terrain Slabs compatibility** (the much-requested feature) on top of the targeting overhaul: objects, blocks, and vanilla slabs lower flush on Terrain Slabs surfaces to form combined slabs. Optional + runtime-gated.
- Mixed-slab lowering fixed: lantern/full-block on a mixed slab → −1.0 flush (`55bba392`, `01cf6d4f`); vanilla TOP-slab-on-terrain + vanilla-slab-stacked-on-mixed chain → −1.0 (`b9f5325c`); "doom-infinity-window" cull fix (`ec6c7024`).
- Added the combined-slab chaining matrix guard `CombinedSlabChainingMatrixTest` (`a8bdadf2`).
- Pre-release hygiene + version bump `0.2.0-beta.5` → `0.3.0-beta.1`, license → GPL-3.0-only (`9f85d8b6`).
- **Proof:** 52/52 gametests green; jar `slabbed-0.3.0-beta.1.jar`. Lantern flush + crafting-table no-pop live-confirmed by Julia.
- **Decision log:** deep 3+ combined-slab towers cap at −1.0 (protects the pick-raycast window); custom-slab-onto-vanilla mixing left deferred (terrain-render risk).
- **Convention change (this entry):** spine is now this append-only running log; per-branch current state moved to `HANDOFF.md`.

---

<!--
Pre-running-log spine snapshots still live as current-state docs in the other worktree
roots' SLABBED_SPINE.md files (e.g. the 1.21.1 port: branch port/mc-1.21.1 @ 94a5643e in
Slabbed-phase19-integrate; the 26.1.2 ports under Slabbed-port-26.1.2*). Migrate those to
the running-log + HANDOFF.md convention when each root is next touched — do not bulk-rewrite.
-->
