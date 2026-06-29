# Changelog

All notable, player-facing changes are listed here. Slabbed ships a separate file per
Minecraft version; entries note which versions/loaders a change applies to. For the exact
latest file, see [Modrinth](https://modrinth.com/mod/slabbed) or
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/slabbed).

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
