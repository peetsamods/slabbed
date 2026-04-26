## [Unreleased]
### Added
- Slabbed lab developer workflow for scripted fixture resets, support actions, and neighbor-update audits.
- Category audit scaffolding for carpets and torches, with structured failure reporting.
- Client screenshot capture tooling and gap-filler overlay support for repeatable visual proofs.
- GameTest wiring for server and client test entrypoints.

### Changed
- `SlabSupport` now clamps shared offset eligibility to slab-aware cases instead of applying broader offset rules.
- Raycast shapes now follow the same Y-offset logic as outline shapes so targeting stays aligned with visuals.
- Chain survival checks now recognize slab ceiling support during downward support walks.
- Mod metadata and build wiring were updated to register client-only mixins and the new GameTest source set.

### Fixed
- Restored chain ceiling support under top slabs and double slabs.
- Restored raycast offset parity after the prior offset regression.
- Deduped the carpet outline offset path to avoid double application.
- Added model-height and offset proof coverage so the visual regressions are backed by client tests.

### Tests
- Added server GameTests for the slabbed lab fixture flow.
- Added client GameTests for screenshot-based proof collection.
- Tightened client screenshot capture and overlay behavior to make proof runs repeatable.

### Known issues
- Stairs still have visual and face-culling quirks and need refinement.
- Rail slope transitions between ground-height and slab-height rails remain visually awkward.
- Slab-on-offset-object targeting can still be finicky around placement edge cases.
- Carpet and snow still obey vanilla replacement behavior when a slab is placed in the same block space.
- Hanging roots still follow vanilla survival rules and do not yet have special slab support.

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
