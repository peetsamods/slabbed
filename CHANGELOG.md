## [Unreleased]

## [0.4.1-beta.1+26.1.2-port] — MC 26.1.2 port

Version reconciliation: this 26.1.2 port branched from the `0.2.0-beta` line and was never renumbered, so
its `mod_version` lagged at `0.2.0-beta.4+26.1.2-port` even though it carries the full `0.4.0`-era content
(the forward-ported June fix families, Terrain Slabs compat, and the WYSIWYG placement fixes). Bumped to
`0.4.1-beta.1+26.1.2-port` to join the `0.4.x` line the 1.21.1 / 1.21.11 builds shipped under.

### Content already in this port (carried forward; not new in this bump)
- Terrain Slabs compat: render-region crash guard, world-hole fix, dual mod-id gate, P0.4 object lowering.
- Connecting blocks break-across-step (fence / wall / pane); ceiling-hung dy-from-above; NEVER-POP freeze.
- WYSIWYG placement: RC1 + RC2 (+ GAP-1/2), live bug fixes A/B/C/D.

### Test / process (this session)
- Standardized release sanity checklist + a 19-fixture dy FINGERPRINT regression suite (`dy-baseline.txt`).
- Headless gametest net widened to 105 checks (resting dy, connectors, survival, real `useOn` placement,
  collision presence, compound chain matrix). RC3 dy RED-verified DONE headlessly.
- Tier-2 client dy dump (dev-only, jar-excluded). `./gradlew25` Java-25 wrapper.

### Known open (pre-release)
- Policy calls: tall plants stay flush on slabs; slab-on-deep-stack drops one step; RC3 slab TOP/BOTTOM type.
- Live (Lane 3) visual/feel pass still required; ~28 KB diagnostic recorders pending removal from the jar.

## [0.2.0-beta.4] — Slabbed 0.2.0 Beta 4 / Beta 4

### Highlights
- Major slab-supported object targeting/contact stability pass covering:
  - floor torch
  - candle
  - flower pot
  - button
  - trapdoor
  - door
  - fence
  - wall
  - fence gate
  - chain
  - lantern
- This final public beta.4 slice locks the beta.4 runtime/metadata on `0.2.0-beta.4` after non-blocking final proof checks.

### Known limitation
- `SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE`
- panes/carpet/thin top layers are not covered by SBSBS matrix
- No all-item support claim is made for beta.4

### Credit
Special thanks to Steve (@steve6472) for the extensive PR investigation into hitbox selection, ghost rendering, slab placement edge cases, and bounded slabbed-depth behavior. The PR was not merged directly, but it helped shape the Beta 4 compound-lane decisions and release guardrails. PR: https://github.com/joolbits/slabbed/pull/8

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
