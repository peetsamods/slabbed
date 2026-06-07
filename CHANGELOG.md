## [Unreleased]

### Docs
- Added external findings log: `tools/terrain-slabs-window-claude-findings-2026-06-01.md` summarizing Claude's latest terrain-slab "window" investigation timeline, claimed commits/proofs, and unresolved live-status endpoint.
- Added recorder infra slice thread record (`019e9531-58e5-7013-a1d1-9dabe9e215d7`): recorder mixins optionalized, contract artifacts hardened (`manifest.json`, `summary.json`, `summary.md`, `session.jsonl`, `actions.tsv`, `mismatches.tsv`), action ledger/backfill stabilized, and non-recorder gameplay logic kept untouched.

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
