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

### Fixed + LIVE-CONFIRMED (2026-06-19)
- **WYSIWYG side-click follow:** a slab placed by clicking a lowered block's SIDE face now follows to that
  lowered surface (lands where the crosshair clicked) instead of freezing flat 0.5 above; clicking the flat
  ground beside a lowered block still stays flat (NEVER-POP rail intact).
- **Redstone torch particle** follows the lowered model (new `RedstoneTorchParticleMixin` — RedstoneTorchBlock
  has its own animateTick the regular torch mixin didn't cover).
- **Vegetation flush on Terrain Slabs:** double-tall plants no longer split (upper half was lowering −0.5 via
  an un-TS-gated `isBottomSlab`); both halves now flush on TS, unchanged on vanilla slabs.

### Test / process
- Standardized **Release Sanity Checklist** + a dy **FINGERPRINT** regression suite (`dy-baseline.txt`).
- Headless gametest net = **107 checks** (resting dy, connectors, survival, real `useOn` placement, collision
  presence, compound chain matrix, vegetation). RC3 dy RED-verified DONE headlessly. Caught + fixed a
  false-green (tall plants despawn on a bare slab — the test was measuring air).
- ~28 KB dev diagnostic recorders removed from the release jar. Tier-2 client dy dump (dev-only, jar-excluded).
  `./gradlew25` Java-25 wrapper.

### Deferred (post-release, by design — NOT pre-release blockers)
- **Full VS+TS slab combining.** Vanilla-slab-on-TS already merges into a full block ("mixed slab", P0.4); a
  *TS* slab itself lowering/combining (TS-on-vanilla, TS+TS, deep chains) is deferred — TS blocks are
  categorically `shouldSkipOffset` to protect TS terrain rendering (the world-hole guard); relaxing it is a
  scoped post-release feature needing heavy live terrain testing.
- **Step-up onto a lowered slab** feels different from a vanilla slab — collision intentionally stays at
  vanilla cell height so you can't clip through lowered blocks. Known minor quirk.

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
