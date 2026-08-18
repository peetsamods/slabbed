# Changelog

All notable, player-facing changes are listed here. Slabbed ships a separate file per
Minecraft version; entries note which versions/loaders a change applies to. For the exact
latest file, see [Modrinth](https://modrinth.com/mod/slabbed) or
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/slabbed).
See LAW.md — this doc does not redefine the law.

## [0.5.1-alpha.1] — 1.21.11 (Fabric) — 2026-08-17

Alpha: the placement core was rebuilt around heights that are computed once, at placement, and
then frozen. Automated coverage is extensive (387 gametests plus client proofs); items below note
their proof level.

### ⚠️ Upgrading an existing 0.5.0-beta.8 world — some blocks will shift down half a block

Slabbed now lets a block rest lower in three cases where it previously stayed flush. Blocks placed
before this version carry no saved height, so they follow the new rule the first time they load:

- a **carpet** resting directly on a bottom slab
- **powder snow** resting directly on a bottom slab
- a **top slab** resting directly on a bottom slab

Each of these now sits half a block lower than it did on beta.8. Nothing is deleted or relocated —
the blocks stay in the same places, they just render and collide half a block down, matching how
the same build looks if you make it today. Anything you place from this version onward records its
height as you place it and will not move afterward.

Measured directly by resolving identical unstored layouts on both builds: three of thirteen legacy
layouts differ, all by −0.5, all in the direction of lowering. If you have a beta.8 build that
depends on those blocks staying flush, back it up before upgrading.

### Fixed
- **Placement heights are frozen at placement time, atomically, for every block item.** Where a
  block lands is where it stays; later neighbor or support edits can never move it. This is the
  core 0.5.1 change and closes the long-standing pop/snap family. *(Gametest-enforced; support
  removal, reload, and full-restart persistence live-confirmed in the dev client.)*
- **Fixed a client crash while meshing chunks on Terrain-slab-dense terrain.** A support walk
  could read past the renderer's region boundary on a mesh worker; every resolver read is now
  bounded, ending the lookup at the region edge. *(Live-reproduced and live-confirmed.)*
- **Scaffolding works with Slabbed installed again**
  ([#65](https://github.com/peetsamods/slabbed/issues/65)): side-click stacking lands on top of
  the column, placing upward from inside scaffolding no longer creates an invisible untargetable
  block, and stacking on a lowered column follows the column's real seat. *(Gametest-proven
  through the real item path.)*
- **Placing a block into a replaceable cell (grass, ferns, flowers, one-layer snow) no longer
  records a wrong height** a full cell up or down, and same-item merges (candles, sea pickles,
  snow layers) preserve the cell's recorded height instead of re-deriving it. *(Gametest-proven.)*
- **Slab top-edge and corner clicks place on top of the slab.** *(Live-confirmed.)*
- **Improved Terrain Slabs compatibility:** player-placed Terrain slab heights are preserved and
  continue lowered surfaces at the same depth; floor objects (torches, levers, repeaters,
  comparators) seat on Terrain slab surfaces; hanging objects follow Terrain underside planes;
  Terrain-Slabs-owned vegetation and snow get exactly one offset — Terrain Slabs' own — over
  both natural and player-placed Terrain surfaces. *(Core rows live-confirmed in the dev client
  with the original Terrain Slabs 3.3.0; the rest gametest-proven.)*
- **Lever particles align with the lever's frozen height.** *(Gametest-proven.)*
- **Vanilla redstone wire connections on lowered supports repaired**, with the piston-circuit
  stability replay ([#57](https://github.com/peetsamods/slabbed/issues/57)) covered by gametests.
- **Client meshes for height-dependent blocks rebuild promptly and in a bounded, coalesced way.**

### Notes
- The supported player height floor remains **−1.0** (one full block down). A deeper range exists
  for development and testing only, behind explicit opt-in, and is not supported player behavior.
- `/slabdy` (the diagnostic overlay) ships in every build and is **off** by default.

## [0.5.0-beta.8] — 1.21.11 (Fabric) — 2026-07-12

### Fixed
- **Fixed a client crash while rebuilding chunk meshes near a renderer-region boundary.** A
  bounded support-column lookup correctly caught the renderer's out-of-range read, but recognized
  the renderer view using a development-only mapped class-name string. Production uses an
  intermediary runtime name, so the exception was rethrown instead of ending the bounded scan.
  The check now uses a client-only class reference that Fabric Loom remaps for the running
  environment. ([#35](https://github.com/peetsamods/slabbed/issues/35))

## [0.5.0-beta.7] — 1.21.11 (Fabric) — 2026-07-04 — **published on Modrinth and CurseForge**

> Consolidates what was internally iterated as beta.2 through beta.7 in a single day of live-test
> fixes on top of `0.5.0-beta.1` — those intermediate version numbers were never separately
> published; this is the one entry for what's actually live.

### Fixed
- **Redstone repeaters and comparators can now be placed on a Terrain Slabs bottom slab** (this
  also generically fixes any other block whose placement depends on the same vanilla solidity
  check — buttons, pressure plates, rails, etc.). *(Live-confirmed.)*
- **Decorative objects (candles, trapdoors, and similar) resting on a slab, fence, or other
  support no longer pop back to full height when that support is broken** — previously they had
  no persisted height-lock at all, unlike every other object category. *(Live-confirmed.)*
- **Lowered brewing stands emit their ambient smoke particles at the stand's rendered height**,
  not full block height.
- **Stashing an item into a lowered decorated pot spawns its particle burst at the pot's rendered
  height**, not full block height. *(Live-confirmed.)*
- **A hanging lantern under a Terrain Slabs slab no longer hangs too low** (a gap between the
  lantern and the slab). This was a regression introduced earlier in this same batch by the
  decorative-object anchor work; a lantern now stays flush under a Terrain Slabs slab, matching how
  a hanging sign already behaved. *(Live-confirmed.)*
- **A slab placed beside a lowered full block no longer has an invisible side face**
  ([#24](https://github.com/peetsamods/slabbed/issues/24)). The see-through-hole mitigation
  (`isSlabHeightStepFace`) only ever considered opaque full cubes as a subject/neighbour — a BOTTOM
  or TOP slab's own face toward a lowered neighbour was never evaluated at all, only the opposite
  (full-block) side was. Widened to also cover slabs. *(Live-confirmed.)*

### Added
- **A brief one-time notice reminding you Slabbed is in beta.** The first time you join a world in
  a play session, a short chat message appears: "Slabbed is in beta — expect some rough edges
  while it's being developed." with a clickable **[Don't show again]** link. Dismissing it only
  silences that specific world/server — a different world you haven't dismissed it in will still
  show it. Shows at most once per world per play session either way.
- **`/slabdy` now reports a "cache:" line** showing the cached (last-rendered) vs freshly
  recomputed height offset for the block you're targeting and its support, so a stale-render bug
  and a real logic bug can be told apart at a glance.

### Changed
- **The `/slabdy` target-height overlay is now off by default.** It still ships in every build and
  can be turned on with a bare `/slabdy`, but a player no longer sees a diagnostic readout in the
  corner of their screen unless they asked for it.

### Security
- **An internal dev tool's output file could contain live Microsoft account credentials.** This
  tool is not part of a normal build and never affected regular play, but the leak is fixed, and
  the tool has additionally been removed from release builds entirely.

### Fixed (tooling)
- **The `/slabdy` overlay's outline display was double-applying the height offset**, making a
  perfectly correct block look "internally inconsistent" in its own debug readout. This was a bug
  in the diagnostic tool itself, not in any block's actual rendered height — an earlier finding
  during this same investigation that blamed a real dy bug has been corrected in the ledger.

### Known issue (deferred)
- Full blocks, fences, and standing objects placed directly on top of a Terrain Slabs slab can
  render half a block too low in some arrangements — same underlying cause as the lantern fix
  above, but via a different code path that is entangled with the combined-slab behaviour, so it is
  being addressed carefully rather than rushed.

### Investigated, not yet resolved
- Recurring see-through "world hole" diagnostics — all match the mod's own intended geometry with
  no confirmed height mismatch in the data available; needs exact coordinates of a visible hole
  (if any) to pursue further.

## [0.5.0-beta.1] — 1.21.11 (Fabric) — 2026-07-04

> Skips 0.4.0: that number was already spent on a 1.21.11 release that was pulled from
> Modrinth/CurseForge after a critical world-hole regression (see `release/mc1.21.11-0.4.0-beta.3`);
> the project's own decision at the time was to treat 0.3.0 as latest again and cut the next real
> release fresh. This is that release — the accumulated never-pop / WYSIWYG hardening batch.

### Fixed
- **Fences, walls, panes, and fence gates keep their placed height** — a lowered fence no longer
  pops back up (and a flat one is no longer pulled down) when a neighbouring block changes.
- **Selection outline and targeting match the rendered fence/wall/pane** on a slab — the box you
  see and the block you hit are now at the same lowered height ([#21](https://github.com/peetsamods/slabbed/issues/21)).
- **Hoppers, chests, and furnaces on a slab keep their lowered height** — no more one-frame snap
  when the block below them changes.
- **In-place block changes no longer jitter the height** — e.g. a grass block converting to dirt
  keeps its placed offset instead of flickering.
- **Lowered redstone torches emit their dust particles at the torch head**, not at full block
  height.
- **Lowered candles and candle cakes emit their smoke particles at the candle's height**, not at
  full block height.
- **A full block placed on a lowered log surface shares that surface's height** (no see-through
  seam) ([#22](https://github.com/peetsamods/slabbed/issues/22)).
- **Placing a fence, wall, pane, or gate beside an existing lowered one now matches its height**
  instead of sitting flat and detached.
- **Chaining a hopper or chest horizontally beside a lowered one keeps the lowered height**
  instead of placing upward at full block height.
- **Placing a slab near the edge of another lowered slab no longer silently merges the two into a
  double slab** — found and fixed via two separate triggers of the same visible symptom.
- **Breaking one slab in a lowered, chained row of Terrain Slabs slabs no longer pops the rest of
  the row upward.**
- **Fixed a see-through gap that could appear at the seam of a lowered block placed on an ordinary
  vanilla slab** (previously this cull fix only covered Terrain Slabs surfaces).
- **A slab resting on top of another lowered slab no longer pops back to full height when the
  slab underneath it is broken.**
- Dropped the stale `indium` recommendation from `fabric.mod.json` (Sodium 0.6+ ships its own
  FRAPI-compatible renderer; this was already cleaned up on an abandoned release branch and had
  never been ported back to this line).

### Added
- **`/slabdy` debug overlay** now ships in every build (a passive corner readout of the height
  offset your crosshair is targeting). It is a diagnostic aid, on by default in test builds and
  toggled with `/slabdy`; release builds can ship it off.

### Pending live confirmation
Every fix above is proven by the automated gametest suite (141/141 green). The following were
**not yet** explicitly re-confirmed against a real client at time of cut, and should be watched
for on the next live pass rather than assumed solid:
- **Candle/candle-cake particle height** — the mixin wiring is proven headlessly, but the actual
  rendered particle position is client-only and has not had an explicit live re-look since the fix
  landed.
- **Hopper/chest horizontal-chain fix** — proven headlessly; no explicit live confirmation on
  record.
- **Terrain Slabs chained-row break-pop fix** — proven headlessly; no explicit live confirmation
  on record.
- **Vanilla-slab see-through-seam cull fix** — proven headlessly; the mitigation targets Indigo's
  renderer class by name and has **not** been checked against Sodium's own FRAPI-compatible
  implementation, which is what this project actually runs live. If the seam is still visible on a
  plain anchored block (no Terrain Slabs involved), Sodium needs its own equivalent patch.
- **Vertical slab-on-slab anchor fix (this change's newest fix)** — proven headlessly (RED/GREEN
  both directions); zero live testing yet, staged fresh in this same build.

## [0.4.2] — Ports: 1.21.1 + Minecraft 26.x

### Added
- **Minecraft 1.21.1 port** — full Slabbed and Terrain Slabs compatibility, on **Fabric** and **NeoForge**.
- **Minecraft 26.1 / 26.1.2 / 26.2 ports.**

### Fixed
- **Performance:** removed a per-block diagnostic code path that could cause frame-time spikes on some setups (notably under Sodium).

## [0.4.0] — Terrain Slabs polish & the first port

### Added
- The cross-version release line begins here; the first **1.21.1** build ships alongside **1.21.11**.

### Fixed
- **Fences, walls, and panes** on a vanilla slab now render flush ([#21](https://github.com/peetsamods/slabbed/issues/21)).
- **See-through "world holes"** with Terrain Slabs installed are fixed; opaque full cubes no longer lower onto Terrain Slabs surfaces incorrectly.
- **Terrain Slabs vegetation** no longer double-offsets (sunk / invisible grass).
- **Hanging lanterns** sit flush under a flush Terrain Slabs slab (no half-block gap).
- **Ceiling-hung decorations** take their offset from the support *above* only.
- **Vertical combined-slab stacks** lower fully (−1.0) instead of floating.

### Changed
- Placed blocks **lock their height** on placement (a "never-pop" rule) so they don't auto-adjust after the fact.
- Dropped the Indium recommendation on 1.21.11 (Sodium 0.6+ ships FRAPI).

## [0.3.0-beta.1] — Terrain Slabs Compatibility

### Added
- **Countered’s Terrain Slabs (`terrainslabs`) compatibility.** Blocks, objects (lanterns, torches, chains), and vanilla slabs placed on Terrain Slabs surfaces lower to sit flush, forming continuous "combined slab" surfaces. The compatibility is optional and runtime-gated — Slabbed runs unchanged when Terrain Slabs is not installed.

### Fixed
- **Targeting overhaul.** Blocks rendered at a visual offset are now selected by true nearest-hit from the player's eye, fixing the long-standing mistargeting where the crosshair and the selection outline disagreed on offset shapes.
- **Lanterns / objects on a mixed slab** (a vanilla slab capping a Terrain Slabs slab) now lower the full amount to sit flush instead of floating half a block above.
- **Full blocks on a mixed slab** no longer briefly lower and then pop back up after placement (a placement-anchor sync race).
- **Vanilla slabs chain flush on combined slabs:** a top slab capping a Terrain Slabs slab, and a vanilla slab stacked on a mixed slab, now sit flush instead of leaving a gap.
- **See-through "window" holes** on lowered blocks — a wrongly culled side face that exposed the void/sky through the block — are fixed.

### Known limitations
- Custom (Terrain Slabs) slabs do not yet lower when placed directly onto a vanilla slab.
- Deep (3+ high) combined-slab towers cap at a one-block visual offset to keep block targeting reliable.

## [0.2.0-beta.2] — Side-Slab Torch Stability

### Fixed
- Fixed adjacent side slabs beside lowered slab-supported full blocks so they remain visually lowered.
- Fixed repeat-click / double-slab behavior on adjacent lowered side slabs.
- Fixed floor torch placement, selection, and flame particle behavior on BS-FB-0.5S setups.
- Fixed wall torch flame particles floating above lowered torch visuals.
- Removed forced ghost wireframe debug boxes from normal runClient.
- Corrected a stale server proof so ordinary full blocks lowering onto slabs is protected as intended behavior.

### Improved
- Added proof coverage for adjacent side-slab dy inheritance.
- Added proof coverage for floor torch compound dy and selectable comfort.
- Preserved known no-rescue boundaries for chain and crafting table.
- Enforced release artifact purity by excluding dev/debug tooling from the public jar.

### Known note
- Floor torch selection on BS-FB-0.5S may reach slightly downward into the supporting slab area; accepted because breaking that support would break the torch anyway.

## [0.2.0-beta.1-hotfix.1]
### Fixed
- Restored stable selection outline/hitbox behavior for slab-supported functional blocks.
- Removed experimental/debug instrumentation (no [SHAPES]/[RAYCAST_EMPTY]/DIAG_FALLBACK/CrosshairTargetRedirectMixin/RaycastShapeDebugMixin on this hotfix line).

### Known issues
- Ghosting in complex slab+block stacking remains; not addressed in this hotfix.

## [0.1.1-alpha]
### Added / Changed
- Sodium-compatible rendering: FRAPI quad vertex translation so block models visually align with slab top surfaces (no Indigo/Indium required).
- Generic model wrapping approach: slab Y-offset determined at render time via SlabSupport single-source-of-truth.

### Fixed
- Torch + common block model visual alignment on slab tops under Sodium.

### Known issues
- Redstone on slabs: visual/connection edge cases remain (down-step and power propagation still under investigation).
- Hanging support under top slabs: needs explicit in-game verification/triage for any remaining blocks beyond lanterns.
