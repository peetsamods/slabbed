## [Unreleased]
- Terrain Slabs named-surface compatibility (objects lowering onto Terrain Slabs surfaces, compound −1.0) is planned for a follow-up; this build keeps the existing gated compat (Terrain Slabs blocks are excluded from Slabbed's visual offsets).

## [0.4.2-beta.2] - Minecraft 1.21.1 (NeoForge)

Carries the 0.4.2 parity-candidate work for this port plus a render-path
performance cleanup. Build metadata corrected from `+26.2` (inherited from the
26.2 parity port it was derived from) to `+1.21.1`, the actual target Minecraft
version; this is a relabel only and does not affect version precedence.

### Performance
- Removed always-on per-block work from the chunk-mesh render path: the
  full-mesh-bounds diagnostic sampler no longer does a block-registry lookup,
  ~6 string allocations, and an atomic increment per block before checking its
  (production-off) trace flag — that work and its per-vertex bounds loop are now
  skipped entirely unless the trace is armed. Render trace flags are read once at
  class-load instead of per block; `render.offset.trace` (which client gametests
  toggle at runtime) stays live but is gated cheap-first. Zero behavior change;
  matters most under Sodium, which routes all block geometry through this path.
  (This is the generalized form of the Fabric 1.21.1 per-block-trace lag; the
  exact Fabric cause — per-block reflection on a release-excluded class — is
  structurally absent on NeoForge.)

## [0.4.0-beta.3] - Slabbed 0.4.0 Beta 3 / Minecraft 1.21.1

The Minecraft 1.21.1 port, with a rebuilt targeting path. Consolidates the
slab-lowering, placement, and visual-contact work since 0.2.0-beta.4.

> Version note: this port moves from `0.2.0-beta.4` directly to `0.4.0-beta.3`;
> the intervening `0.3.x` and `0.4.0-beta.1`–`beta.2` tags belong to separate
> Slabbed branches and are not part of this 1.21.1 line.

### Targeting
- Replaced the old DDA "rescue" retargeter with an offset-aware nearest-hit raycast, so the crosshair selects and breaks exactly the block you are pointing at on lowered/offset shapes — no more sideways mistargeting. Fence/wall/pane and lowered-block outlines are kept consistent with their rendered shape so the raycast can't target a phantom.

### Fixed
- Adjacent side slabs beside a lowered full block stay visually lowered and merge flush (no seam/float across the height step).
- Decorative hangers — lanterns, soul lanterns, spore blossoms, hanging roots, pale hanging moss — follow a lowered support block down instead of clipping into it. Chains excluded; top-slab `+0.5` adherence preserved.
- Powder snow is never lowered onto slabs (it is a full terrain cube, not a thin top layer), fixing the snowy-terrain see-through step.
- Lowered top-slab lower-edge side placement lands flush in the aimed visual half; a slab placed onto a compound/lowered stack targets the visible owner's top.
- Fence, wall, and pane connections no longer draw across a Slabbed height step.
- Lowered trapdoor seam resolves from the correct block-state authority.

### Developer
- `/slabdy` overlay: a toggleable HUD readout of the targeted block's source and visual offset (off by default).

### Known limitations
- A face-culling / shadow artifact beside a lowered full-block ↔ vanilla-slab boundary is deferred to a later render/culling slice.
- Full Terrain Slabs named-surface support (lowering objects onto Terrain Slabs surfaces) is not in this build; Terrain Slabs blocks are kept un-offset (no ghost terrain) and otherwise behave as vanilla support.
- No all-item or all-partial-block support claim is made for this beta.

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
