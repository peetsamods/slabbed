# Slabbed — Branch Handoff

> **Current state of THIS branch — overwrite freely.** This is "where things stand right now."
> Append-only *history* lives in [`SLABBED_SPINE.md`](SLABBED_SPINE.md). One `HANDOFF.md` per branch.

## Branch

- **Branch:** `release/mc1.21.11-0.4.0-beta.3` (off the released hotfix `eab0d786`) · pushed to `peetsamods/slabbed`
- **Checkout:** `/Users/joolmac/CascadeProjects/Slabbed`
- **HEAD:** `42002295` (run `git rev-parse --short HEAD` to confirm)
- **Minecraft:** 1.21.11 · **Loader:** Fabric (dev loader bumped to 0.19.2 for TS 3.x) · **Java:** 21
- **Prior release tag:** `slabbed-0.4.0-beta.3` at `eab0d786`

## Status: WORLD-HOLE "DODO" FIXED — holed beta.3 pulled; next release cuts from this branch

The released `0.4.0-beta.3` jar (`eab0d786`) shipped a **world-hole bug**: with Terrain Slabs
loaded, natural Stone/Dirt above TS slab surfaces was lowered −0.5 and tore see-through holes.
**Root cause:** `CompatHooks` only recognised the modern `terrain_slabs` mod-id, not legacy
`terrainslabs`, so TS slabs were treated as vanilla bottom slabs and the column walk lowered the
terrain. **Fixed at HEAD `42002295`** (dual-mod-id + column-walk solid-terrain stop +
view-independent opaque-cube flush). 58/58 gametests green; LIVE-CONFIRMED by Julia (DODOs gone).
See `SLABBED_SPINE.md` 2026-06-10 entry for the full root-cause + lessons.

> ✅ The holed `0.4.0-beta.3` was **pulled from Modrinth/CurseForge** as soon as the bug was
> found — it is **not live**. The latest published 1.21.11 jar is now **`0.3.0`**. The next
> 1.21.11 release must be cut from this branch's HEAD (`42002295`), not from `eab0d786`.

### Open / next

- Re-cut + re-upload `0.4.0-beta.3` (or bump to beta.4) from `42002295`.
- Decorative-hanger "smoosh" + merging step-cull were noted during testing but parked while the
  world-hole DODO took priority — revisit if still visible after the hole fix.
- Curated **opaque-cube** objects in the `isSlabSitCandidate` allow-list (logs, bookshelf,
  crafting table, dried kelp) still lower onto TS and could DODO; decide whether to exclude them
  too (full "no opaque cube on TS" rule) — generic terrain is already excluded.

### Done

- **Targeting overhaul** — offset-aware nearest-hit pick raycast (`SlabbedOffsetRaycast` + `ClientPickOffsetRaycastMixin`); fixes mistargeting on offset shapes.
- **Terrain Slabs compat** — objects/blocks/vanilla slabs lower flush on Terrain Slabs surfaces (mixed/combined slabs). Runtime-gated; inert without Terrain Slabs.
- **Modern mod-id hotfix (`0.3.0-beta.2`)** — the compat layer now activates for current Terrain Slabs 3.x installs (`terrain_slabs`) as well as legacy `terrainslabs`.
- **Mixed-slab lowering** — lantern & full block on a mixed slab → −1.0 flush (lantern live-confirmed; block no longer pops up after anchor sync).
- **Vanilla chaining** — vanilla TOP slab on terrain & a vanilla slab stacked on a mixed slab → −1.0 flush.
- **"Doom-infinity-window" fix** — ported `BlockRenderInfoCullMixin` (lowered block's exposed face was wrongly culled).
- **Release/docs hygiene** — removed shipping log spam + dead code, license → GPL-3.0-only, README/CHANGELOG refreshed, and HEAD removes the stale Indium recommendation from mod metadata.

### Deferred (documented, by decision)

- Custom (Terrain Slabs) slab placed **directly onto a vanilla slab** does not lower (terrain slabs are `shouldSkipOffset`; relaxing touches terrain rendering).
- **Deep 3+ combined-slab towers** cap their offset at −1.0 to protect the pick-raycast window (`{C, C.up, C.down}`).
- **Slab-vs-visual height mismatch** (wood slab renders as a full cube / terrain dirt-slab box undershoots).

### Build / test

- Release proof (`00b6a7c4` / `slabbed-0.3.0-beta.2`): headless gametests `54/54` green, including `TerrainSlabsHotfixTest`.
- Jar: `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew --no-daemon build`
- Gametests: `… runGameTest` → expect `All 54 required tests passed`
- Dev client: `./gradlew runClient` (Terrain Slabs + midnightlib staged in `run/mods/` + `run/.fabric/processedMods/`)
- **Combined-slab law guard:** `CombinedSlabChainingMatrixTest` logs `[MATRIX]` actual-vs-flush for every ordering. Always check the full chain before claiming a lowering fix is done.
- Post-tag note: no fresh proof run is recorded in this checkout after `622bac6a` because that commit is metadata/docs-only.

### Next

- If another artifact is cut from this branch, rerun build/headless proof from `HEAD` and tag from the new result rather than treating `622bac6a` as implicitly re-proved.
- Live-soak `0.3.0-beta.2`; if quiet, drop "beta" → `0.3.0`.
- (If pulled in) custom-slab-onto-vanilla mixing + deep chaining (needs the pick raycast widened beyond ±1).
- 1.21.1 port (separate root: `Slabbed-phase19-integrate`, branch `port/mc-1.21.1`).

### Gotchas

- This file is for the current main checkout, not the old `claude/terrain-targeting-overhaul-20260606` worktree.
- `main` still points at the docs split commit `6da1643e`; this hotfix branch carries the release tag `slabbed-0.3.0-beta.2` one commit below HEAD.
- The gated `TRACE`/recorder/render-trace machinery is intentional, off by default, and excluded from the release jar — leave it.
