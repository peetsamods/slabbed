# Changelog

All notable, player-facing changes are listed here. Slabbed ships a separate file per
Minecraft version; entries note which versions/loaders a change applies to. For the exact
latest file, see [Modrinth](https://modrinth.com/mod/slabbed) or
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/slabbed).
See LAW.md — this doc does not redefine the law.

## [0.5.2-alpha.1] — 1.21.11 (Fabric) — unreleased

In development. Entries land here as they are proved.

### Fixed

- **A tall stack of blocks in a lowered hole no longer sinks into itself part-way up.** Blocks
  resting on a lowered block sit at the same depth as the block under them, all the way up — but
  past the seventh course, an older block (one carrying no saved height, so any build made before
  0.5.1-alpha.1) stopped inheriting that depth and dropped to the deepest height the version
  allows, sinking half a block into the block it stands on. It is drawn and clicked at the sunken
  height, so a tall column read as visibly broken from the seventh block up. Fixed at the root:
  the resolver's depth budget is spent only by a support that actually lowers its neighbour, so a
  straight stack — which lowers nothing, it just passes the depth along — no longer runs out of
  budget at all. *(Proved by a failing test first, at both height limits; the whole suite is green
  at both.)*

### ⚠️ Upgrading a world built on 0.5.1-alpha.1 or earlier — some tall stacks move back up

Blocks placed before 0.5.1-alpha.1 carry no saved height, so they follow the current rule each time
they load. If such a build has **more than six blocks stacked in a lowered hole**, everything from
the seventh block up was sitting half a block too low (a block and a half too low in a world using
the deeper height range). Those blocks now line up with the rest of the stack — they move **up**,
back to where the column reads as straight. Nothing is deleted or relocated. Anything placed from
0.5.1-alpha.1 onward records its own height and is unaffected.

## [0.5.1-alpha.1] — 1.21.11 (Fabric) — 2026-08-18

The largest update this line has shipped — 116 commits since 0.5.0-beta.8. The placement core was
rebuilt around a single idea: **a block's height is computed once, when you place it, and then
frozen** — recorded in the world save itself, immune to anything that happens around it later.
Alpha status reflects the size of that rebuild, not its test coverage: 389 gametests plus
client-side proofs run green on every build.

### ⚠️ Upgrading an existing 0.5.0-beta.8 world — some blocks will shift down half a block

Slabbed now lets a block rest lower in three cases where it previously stayed flush. Blocks placed
before this version carry no saved height, so they follow the new rule the first time they load:

- a **carpet** resting directly on a bottom slab
- **powder snow** resting directly on a bottom slab
- a **top slab** resting directly on a bottom slab

Each now sits half a block lower than on beta.8. Nothing is deleted or relocated — the same builds
simply render and collide the way they would if placed today. Measured by resolving identical
layouts on both versions: three of thirteen legacy layouts differ, all by −0.5, all downward. If a
beta.8 build depends on those staying flush, back it up before upgrading.

### The core change: placement is permanent

- **Every block item placement records its height at placement time, atomically.** Where a block
  lands is where it stays — breaking its support, building next to it, reloading, or restarting
  changes nothing. This closes the long-standing family of pop/snap bugs at the root instead of
  case by case. *(Enforced by a blocking invariance matrix; support removal, reload and
  full-restart persistence confirmed live.)*
- A lowered placement that qualifies for no anchor **still** records its height — protection no
  longer depends on a lucky classification.
- A block placed flat stays flat, whatever kind of block it is; in-place transforms (grass → dirt,
  crop growth, waterlogging) keep the recorded height; removal clears only the removed block's
  record.
- Same-item merges (a second candle, sea pickle, snow layer) preserve the cell's recorded height
  instead of re-deriving it.

### Placement accuracy

- **Scaffolding works correctly with Slabbed installed** ([#65](https://github.com/peetsamods/slabbed/issues/65)):
  side-click stacking lands on top of the column, placing upward from inside scaffolding no longer
  creates an invisible untargetable block, and stacking on a lowered column follows the column's
  real seat.
- **Placing into a replaceable cell** (grass, ferns, flowers, one-layer snow) no longer records a
  wrong height a full cell up or down.
- **Slab top-edge and corner clicks place on top** of the slab instead of deflecting to the side.
  *(Live-confirmed.)*
- Clicks on the visible face of a lowered block are accepted at the server's own validation seam,
  so what you can see and aim at is what the server lets you build against.
- A slab seats on a bottom slab's top face; a slab may lower onto a slab that is itself sunk; a
  follower can never sink *into* its own support; followers inherit their support's actual height
  rather than a guessed constant.
- Seat decisions ask **geometry, not block class**: a seat is a face rather than a volume, a TOP or
  DOUBLE slab counts as a seat, "hanging" is judged by whether a block *is* hanging rather than
  whether its type could be, and a plain full block on bare Terrain Slabs no longer earns a
  spurious anchor and snap.

### Targeting

- The pick window widened to ±2 blocks with its radius derived from the active depth cap, so
  lowered blocks stay targetable across the whole supported range.
- Escape hatch: `-Dslabbed.offsetRaycast=false` restores vanilla picking entirely.

### Terrain Slabs compatibility — improved, not complete

- Player-placed Terrain slab heights are preserved exactly, and a Terrain slab placed onto an
  already-lowered block continues the surface at the same depth. *(Live-confirmed with the original
  Terrain Slabs 3.3.0.)*
- Floor objects (torches, levers, repeaters, comparators) seat on Terrain slab surfaces; hanging
  objects follow Terrain underside planes.
- Terrain-Slabs-owned vegetation and snow get **exactly one** offset — Terrain Slabs' own — whether
  the surface underneath is natural or player-placed.
- Carpets follow their support, and potting a flower no longer moves the block above it.

### Stability and rendering

- **Fixed a client crash while meshing chunks on Terrain-slab-dense terrain.** beta.8 guarded one
  renderer-boundary read; the rest of the resolver could still walk past the region edge on a mesh
  worker and crash the game on world load. Every resolver read is now bounded, ending the lookup at
  the region edge. *(Reproduced live on a Terrain worldgen world; fix confirmed on the same world.)*
- Chunk-seam culling compares real heights instead of booleans, fixing see-through seams on back
  rows, and its eligibility follows geometry rather than anchor membership.
- Dependent chunk remeshes are coalesced and prioritised, bounded per tick.
- Lever use particles align with the lever's frozen height, joining the earlier torch, candle,
  brewing-stand and pot particle fixes.

### Multiplayer and world data

- **Fixed a chunk that could fail to load once it accumulated roughly 2,048 height markers**
  ([#38](https://github.com/peetsamods/slabbed/issues/38)): the sync format now groups positions by
  chunk section, keeping even a fully dense chunk under the engine's attachment size ceiling — and
  at the true limit the store declines a new record instead of corrupting the chunk.
- **Vanilla redstone wire connections restored on lowered supports**
  ([#37](https://github.com/peetsamods/slabbed/issues/37)).
- **Bottom-slab mob proofing restored** ([#39](https://github.com/peetsamods/slabbed/issues/39)).
- Loading a pre-0.5.1 world writes nothing into it: old saves stay byte-clean of Slabbed data until
  you place something new. *(Verified at the region-file level.)*

### Notices, tooling, packaging

- The join notice now describes the build it is on — this version greets you with *alpha*, not
  *beta* — and the wording can no longer drift from the version.
- `/slabdy` (the height-readout overlay) ships in every build and is **off** by default; toggle it
  with `/slabdy`. Its readout shows cached versus freshly-computed heights for the target and its
  support.
- Test, recorder and rig tooling never ships: the release jar is gated by a closed-world allowlist,
  and both jars are scanned for stray content before release.

### Known limits

- The supported lowering floor is **one full block (−1.0)**. A deeper range exists behind an
  explicit per-world opt-in, for development only.
- A custom Terrain Slabs slab placed directly on top of a vanilla slab still does not lower; the
  reverse works.
- Side-clicking certain legacy lowered slabs (placed before this version, in specific side-by-side
  arrangements) can silently place nothing. Being traced; a fix will follow.
- [#36](https://github.com/peetsamods/slabbed/issues/36) (0–1 FPS) did not reproduce in controlled
  testing against beta.8 and remains open pending reporter follow-up.

### Every 1.21.11 Fabric report filed on GitHub

Reports confirmed against Minecraft 1.21.11 Fabric, credited to their reporters. Issues filed
against other versions or loaders are tracked separately and are not listed here.

| # | What it was about | Reported by | Status |
|---|---|---|---|
| [#1](https://github.com/peetsamods/slabbed/issues/1) | Crash with Create Fly during startup | DavidBlackCN | Fixed — Slabbed-side initialisation case corrected |
| [#6](https://github.com/peetsamods/slabbed/issues/6) | Chest stayed attached to a shelf above it; breaking the shelf dropped it | s1rnikorigona-cyber | Fixed — closed on reporter confirmation |
| [#9](https://github.com/peetsamods/slabbed/issues/9) | Some sand blocks visually affected, suspected Terrain Slabs interaction | Kart0 | Fixed with the Terrain Slabs compatibility work |
| [#10](https://github.com/peetsamods/slabbed/issues/10) | World appeared to shift globally above slabs | Errnick-code | Fixed with the Terrain Slabs compatibility work |
| [#23](https://github.com/peetsamods/slabbed/issues/23) | Missing ambient occlusion on top faces of blocks on slabs | ChaosMakerMLG | **Open** — chunk-mesh lighting; not attempted this cycle |
| [#24](https://github.com/peetsamods/slabbed/issues/24) | Invisible side faces on top slabs placed mid-air | ChaosMakerMLG | Fixed — the face-culling check only considered full blocks; widened to slabs |
| [#35](https://github.com/peetsamods/slabbed/issues/35) | Rain caused screen flashing, then a crash | 07Productions | Fixed in beta.8 — a renderer-boundary check used a development-only class name |
| [#36](https://github.com/peetsamods/slabbed/issues/36) | 0–1 FPS after the #35 crash fix | 07Productions | **Open** — did not reproduce against beta.8 under matched conditions; kept open rather than closed on an unreplicated result |
| [#37](https://github.com/peetsamods/slabbed/issues/37) | Redstone dust connected to every horizontal direction | MrFrederic | **Fixed in this release** |
| [#38](https://github.com/peetsamods/slabbed/issues/38) | A chunk could permanently fail to load once its height data grew too large | MrFrederic | **Fixed in this release** |
| [#57](https://github.com/peetsamods/slabbed/issues/57) | Slabbed reported to cause a redstone problem | KumuraTo | **Open** — related piston-circuit stability now has regression coverage; this report awaits a retest |
| [#64](https://github.com/peetsamods/slabbed/issues/64) | Pots lost vanilla's floating gap | cassanogiuseppe702-sudo | **Open by design** — flush seating is intentional; an opt-out is planned |
| [#67](https://github.com/peetsamods/slabbed/issues/67) | Pots "falling" when the block beneath is broken | Leumas257 | **Open by design** — pots need no support in vanilla and Slabbed adds no survival rule; the visible change is the same intentional flush seating as #64 |
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
