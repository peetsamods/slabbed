## [Unreleased]

## [0.2.0-beta.4.1] - Deprecated (Blocked, Unreleased)

This beta 4.1 candidate is blocked and not approved for release.

### Blockers
- Live gameplay still shows excessive culled faces in Terrain Slabs scenarios.
- The current candidate is considered game-breaking and deprecated until a fix is proven live.
- This build has not been released or distributed publicly.

### Fixed
- Lowered generated Terrain Slabs double surfaces for direct-supported fences, walls, and fence gates.
- Restored crosshair ownership for lowered direct-supported objects when the support block is the vanilla raycast hit.
- Kept terrain top faces visible against direct Terrain Slabs bottom-like support.

### Preserved
- The earlier `release/0.2.0-beta.4.1` tag was a blocked, undistributed candidate from private testing and has been superseded before release.
- Terrain Slabs compatibility remains direct-only and does not broaden into generic Slabbed support.

## Superseded private beta 4.1 candidate notes

The original `release/0.2.0-beta.4.1` tag covered this private candidate, was blocked by live Terrain Slabs testing, and was superseded before distribution.

### Fixed in the superseded candidate
- Added compatibility with Countered's Terrain Slabs custom bottom-slab surfaces.
- Preserved lowered object support on valid named Terrain Slabs surfaces.
- Kept live placement support for doors, fences, full blocks, torches, and redstone torch particles.

### Preserved in the superseded candidate
- Terrain Slabs remain out of generic Slabbed support and culling-sensitive paths.
- The release keeps the proven Terrain Slabs live-placement and debug-hook hygiene closures intact.

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
