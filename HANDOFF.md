# Slabbed — Branch Handoff

> **Current state of THIS branch — overwrite freely.** This is "where things stand right now."
> Append-only *history* lives in [`SLABBED_SPINE.md`](SLABBED_SPINE.md). One `HANDOFF.md` per branch.

## Branch

- **Branch:** `claude/terrain-targeting-overhaul-20260606`
- **Worktree:** `/Users/joolmac/CascadeProjects/Slabbed-claude-targeting-overhaul-20260606`
- **HEAD:** `9f85d8b6` (run `git rev-parse --short HEAD` to confirm)
- **Minecraft:** 1.21.11 · **Loader:** Fabric · **Java:** 21 (Gradle toolchain 25)
- **Released as:** `0.3.0-beta.1`

## Status: SHIPPABLE — 0.3.0-beta.1

Clean build, **52/52 gametests green**, release jar at `build/libs/slabbed-0.3.0-beta.1.jar`.
Published to Modrinth/CurseForge as Beta (Fabric · 1.21.11 · Fabric API required · Terrain Slabs optional).

### Done

- **Targeting overhaul** — offset-aware nearest-hit pick raycast (`SlabbedOffsetRaycast` + `ClientPickOffsetRaycastMixin`); fixes mistargeting on offset shapes.
- **Terrain Slabs compat** — objects/blocks/vanilla slabs lower flush on Terrain Slabs surfaces (mixed/combined slabs). Runtime-gated; inert without Terrain Slabs.
- **Mixed-slab lowering** — lantern & full block on a mixed slab → −1.0 flush (lantern live-confirmed; block no longer pops up after anchor sync).
- **Vanilla chaining** — vanilla TOP slab on terrain & a vanilla slab stacked on a mixed slab → −1.0 flush.
- **"Doom-infinity-window" fix** — ported `BlockRenderInfoCullMixin` (lowered block's exposed face was wrongly culled).
- **Pre-release hygiene** — removed shipping log spam + dead code, license → GPL-3.0-only, README/CHANGELOG refreshed.

### Deferred (documented, by decision)

- Custom (Terrain Slabs) slab placed **directly onto a vanilla slab** does not lower (terrain slabs are `shouldSkipOffset`; relaxing touches terrain rendering).
- **Deep 3+ combined-slab towers** cap their offset at −1.0 to protect the pick-raycast window (`{C, C.up, C.down}`).
- **Slab-vs-visual height mismatch** (wood slab renders as a full cube / terrain dirt-slab box undershoots).

### Build / test

- Jar: `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew --no-daemon build`
- Gametests: `… runGameTest` → expect `All 52 required tests passed`
- Dev client: `./gradlew runClient` (Terrain Slabs + midnightlib staged in `run/mods/` + `run/.fabric/processedMods/`)
- **Combined-slab law guard:** `CombinedSlabChainingMatrixTest` logs `[MATRIX]` actual-vs-flush for every ordering. Always check the full chain before claiming a lowering fix is done.

### Next

- Live-soak `0.3.0-beta.1`; if quiet, drop "beta" → `0.3.0`.
- (If pulled in) custom-slab-onto-vanilla mixing + deep chaining (needs the pick raycast widened beyond ±1).
- 1.21.1 port (separate root: `Slabbed-phase19-integrate`, branch `port/mc-1.21.1`).

### Gotchas

- Code lives in the **worktree** above, not the usual main checkout.
- Branch is **not yet pushed or merged to `main`**.
- The gated `TRACE`/recorder/render-trace machinery is intentional, off by default, and excluded from the release jar — leave it.
