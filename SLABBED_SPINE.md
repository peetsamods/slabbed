# SLABBED_SPINE — Running Log

> **APPEND-ONLY history.** Add a new entry at the **top** for each proof-confirmed or
> live-confirmed savepoint or milestone. **Never edit or delete prior entries** — they are
> the permanent record of how the project got here.
>
> ⚠️ Current state of a branch does **NOT** go here — it lives in that branch's
> [`HANDOFF.md`](HANDOFF.md). This file is *history*; `HANDOFF.md` is *now*. That split is
> what stops current-state data from clobbering the log.

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
