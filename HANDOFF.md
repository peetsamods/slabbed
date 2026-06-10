# Slabbed — Branch Handoff

> **Current state of THIS branch — overwrite freely.** This is "where things stand right now."
> Append-only *history* lives in [`SLABBED_SPINE.md`](SLABBED_SPINE.md). One `HANDOFF.md` per branch.

## Branch

- **Branch:** `hotfix/0.3.0-beta.2-terrain-slabs-modid`
- **Checkout:** `/Users/joolmac/CascadeProjects/Slabbed`
- **HEAD:** `622bac6a` (run `git rev-parse --short HEAD` to confirm)
- **Minecraft:** 1.21.11 · **Loader:** Fabric · **Java:** 21 (Gradle toolchain 25)
- **Latest release tag below HEAD:** `slabbed-0.3.0-beta.2` at `00b6a7c4`

## Status: RELEASED `0.3.0-beta.2` + one clean post-tag metadata/docs commit

`slabbed-0.3.0-beta.2` is the proof-confirmed release point for the Terrain Slabs mod-id hotfix:
the optional compat layer now detects both modern `terrain_slabs` and legacy `terrainslabs`.
The current HEAD `622bac6a` is a narrow follow-up that removes the stale Indium recommendation
from `fabric.mod.json` and updates the renderer comment; no new gameplay behavior was changed here.

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
