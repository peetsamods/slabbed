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
