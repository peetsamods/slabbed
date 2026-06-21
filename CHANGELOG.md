## [Unreleased]

## [0.4.2-beta.1+26.1.2] — MC 26.1.2 port

This is the first official 26.1.2 Slabbed candidate on the `0.4.x` line. The 26.1.2 port originally branched from
`0.2.0-beta.4`, so its version string lagged even after it had absorbed the 0.4.0-era 1.21.1/1.21.11
behavior work. The version was moved to `0.4.1-beta.1+26.1.2-port` to make the version line honest: this
build carries the 0.4.0 feature/fix families forward, then adds the 26.1.2-specific port, compatibility, and
live-confirmed cleanup work below. It is now finalized as `0.4.2-beta.1+26.1.2` after the 26.2
`0.4.2-beta.1` improvements were selectively backported and live-confirmed green on 26.1.2.

### Minecraft 26.1.2 port baseline
- Ported Slabbed onto MC 26.1.2 with Java 25, Fabric Loader `0.19.2`, Fabric API `0.145.4+26.1.2`, Loom
  `1.15.5`, and Mojang-mapped sources.
- Added the committed `./gradlew25` wrapper because this checkout must build and test with Java 25; plain
  `./gradlew` may pick an older local JDK and fail on 26.1.2 classfiles.
- Reworked source-set and jar exclusions so current Mojang-named runtime and gametest sources compile while
  old Yarn-mapped SlabbedLab/dev diagnostics stay out of the release jar.
- Reconciled the version from `0.2.0-beta.4+26.1.2-port` to `0.4.2-beta.1+26.1.2` and verified the
  baked `fabric.mod.json` version string.

### Terrain Slabs compatibility
- Added the 26.1.2 Terrain Slabs compat surface with both current and legacy mod-id handling
  (`terrain_slabs` and `terrainslabs`) and safe no-op behavior when Terrain Slabs is absent.
- Fixed a fresh-world render-region crash caused by 26.x throwing on render-region out-of-bounds reads;
  model-path probes now treat those edge reads as flush instead of crashing chunk rendering.
- Fixed Terrain Slabs world holes: full blocks such as stone now stay flush on TS terrain instead of being
  lowered halfway into it.
- Ported direct custom support so curated objects and vanilla slabs can sit on named TS bottom-like surfaces;
  vanilla-slab-on-TS already makes the mixed slab/full-block-looking case.
- Fixed block-on-TS-slab snap-down by adding TS guards to the anchor/support path used by placement.
- Fixed double-tall vegetation on Terrain Slabs. Sunflowers, tall grass, and large ferns no longer split
  with the upper half lowered; both visual halves stay flush on TS surfaces.
- Kept TS natural terrain protected by default. Full TS-slab combining is deliberately deferred because
  relaxing the `shouldSkipOffset` guard is the same class of change that can re-open world-hole rendering.

### WYSIWYG placement and slab/compound behavior
- Preserved the player-facing law established during live testing: a placed block or slab should land where
  the crosshair actually aimed.
- Ported the NEVER-POP freeze-on-place law so player-authored flat placements do not spontaneously snap when
  nearby supports change.
- Fixed cantilever side placement so slabs, fences, walls, and bars placed beside lowered blocks follow the
  visible lowered side when that is what the player clicked.
- Fixed side placement beside deeper compound stacks by reading the neighbor's true dy magnitude rather than
  hardcoding `-0.5`.
- Fixed the bare-single-lowered-slab side case so a neighboring slab source can also drive the lowered result.
- Fixed upper-half side placement overshoot: clicks on a `-0.5` lowered source no longer get misclassified
  as `-1.0` compound-side placement.
- Fixed the final WYSIWYG side-click regression: clicking the side face of a lowered block with solid ground
  below now follows to `-0.5` instead of freezing flat 0.5 too high, while clicking the flat ground beside
  the lowered block still stays flat.
- Fixed ceiling blocks under lowered top slabs so they sit flush with the lowered support instead of being
  raised into it.
- Fixed deeper anchored fence/support behavior so a fence stacked on a `-1.0` support stores and preserves
  its true magnitude, including after support changes.

### Rendering, targeting, and collision
- Restored dy0 slab shape dispatch for unnamed vanilla slabs so outline/raycast/interaction behavior stays
  aligned with the visible target.
- Kept lowered-block movement collision vanilla where required, while adding the neighbor-aware broadphase
  path that makes lowered blocks solid where they are drawn.
- Made stairs slab-friendly: stairs sitting on lowered slab-supported surfaces now keep collision aligned
  with the lowered visual body.
- Made scaffolds slab-friendly: lowered scaffolding can be climbed and passed through at the slab-adjusted
  visual height.
- Contained y-offset emitter cull state so render culling decisions do not leak between emitted quads.
- Fixed the DODO/ghost-window seam: flat-vs-lowered step faces no longer incorrectly keep baked cull faces
  that make a face disappear from certain angles.
- Added and used `/slabdy` target-dy HUD diagnostics during the port to classify dy, source, side, and
  targeted half during live checks.
- Ported the offset-aware crosshair targeting overhaul from the 26.2 `0.4.2-beta.1` line so model, outline,
  raycast, HUD target, and placement owner agree directly instead of relying on post-hoc retarget rescue.

### Blocks, decorations, and visual details
- Ported ceiling-hung decoration support so hanging decorations read dy from the support above.
- Fixed hanging lantern behavior so lanterns follow their support instead of smooshing into the wrong height.
- Added revived chain-ceiling extended geometry for chains directly under top/double slabs.
- Kept powder snow from offsetting onto slabs.
- Fixed fence, wall, glass pane, and iron bars connections so they break correctly across slab-height steps
  while still connecting on same-height supports.
- Fixed redstone torch dust particles so the lit dust emits from the lowered torch head instead of floating
  above the model.
- Rechecked redstone wire survival after a false alarm: redstone does not float over air; the earlier red
  was a test artifact reading the popped air block.

### Testing, proof, and release hygiene
- Added the standardized Release Sanity Checklist so future versions and ports have a repeatable gate instead
  of ad hoc memory.
- Added the dy fingerprint regression suite and committed `src/gametest/resources/dy-baseline.txt` so behavior
  drift shows up as a small, reviewable diff.
- Added Tier-2 client dy fingerprint dumping for dev use; it is dev-only and excluded from the release jar.
- Added real `BlockItem.useOn` gametest coverage for placement paths, including the RC3 dy behavior.
- Widened the maintained headless suite to 130 required gametests covering dy, WYSIWYG placement, connectors,
  resting objects, survival behavior, collision depth, compound stack matrices, and regression fixtures.
- Caught and fixed a false-green test around double-tall plants despawning to air on bare slabs; tests now
  assert against the explicit state or verify the fixture still exists.
- Removed the heavy live cursor/rendered-outline diagnostic recorder logic from the release jar path; the
  remaining `LiveCursorIntentRecorder` is an inert API stub with `enabled() == false`.
- Savepoint proof before tagging passed: compile, `130/130` gametests, clean build, and live confirmation in
  the `TEST_ SLABBED 26.1.2` Modrinth profile.

### Live-confirmed in this candidate
- Slabbed loads with Terrain Slabs 3.3.1 on the 26.1.2 test profile.
- Fresh TS worlds no longer crash from render-region reads.
- Natural TS terrain no longer shows Slabbed-created world holes.
- WYSIWYG side-click slab placement lands on the lowered side that was clicked.
- Redstone torch particles align with the lowered torch head.
- Double-tall vegetation is flush on Terrain Slabs.

### Deferred by design — not pre-release blockers
- **Full VS+TS slab combining:** vanilla-slab-on-TS already works, but TS-slab-on-vanilla, TS+TS, and deep TS
  chains are post-release work because the current TS skip guard is protecting terrain rendering.
- **Step-up feel on lowered slabs:** collision intentionally stays at the vanilla cell height so players do
  not clip through lowered blocks. The feel differs from a vanilla slab at the same visual height; this is
  documented as a known minor quirk, not a release blocker.
- **Optional precision cleanup:** centralizing the DODO cull test around the canonical slab-height-step helper
  can wait because the current live-confirmed seam fix works.

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
