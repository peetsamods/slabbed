# Changelog

Player-facing changes for the Fabric 1.21.1 line. See LAW.md — this doc does not redefine the law.

## [0.5.2-alpha.9] — Minecraft 1.21.1 (Fabric) — unreleased candidate

Brings the Fabric 1.21.1 line forward from `0.4.2-beta.1`. The headline: **where you place a block is
where it stays.** Alpha status reflects the size of the rebuild, not its test coverage: 152 server
GameTests plus a 315-case native client proof run green on every build.

### New placements are permanent; existing blocks are untouched

- **Every block you place from now on records its exact height at placement time and keeps it.**
  Breaking its support, building next to it, reloading the world, or restarting the game changes
  nothing. This closes the long-standing family of "block pops up / snaps down later" bugs at the
  root instead of case by case.
- **Blocks that already exist in your world keep behaving exactly as they do today.** Nothing moves
  when you load an older world into this version, and there is no conversion step, prompt, or
  backup requirement. Older blocks keep the old rules, including the old rules' quirks, until you
  break and re-place them.
- Placing a new block on top of an older lowered block lands it flush on that block's visible top.
- A new placement appears at its final height immediately instead of rendering flat and snapping
  after the server confirms it.
- Heights are stored on a sixteenth-of-a-block grid and synchronized compactly, so dense
  slab-supported builds no longer risk oversized chunk packets.

### Deeper placement

- Side aiming and cantilever placement now work through three blocks of depth, at every
  sixteenth-grid height, while shallow and ordinary vanilla placement stays as it was.
- Models, selection outlines, targeting, and collision agree at stored depths, including snow,
  farmland and crops, beds, carpets, powder snow, turtle eggs, panes, and slab-attached blocks.
- Ambient and event particles stay attached to lowered blocks: torches, wall torches, campfires,
  candles, levers, decorated pots, redstone burnout, and block-display particles.
- Pistons carry a moved block's stored height with it, and a moving piston's block is no longer
  drawn shifted twice.

### Entities on lowered blocks

- Item frames and glow item frames placed on a lowered block sit at its real height, physically
  and visually, so they can be targeted where you see them.
- Minecarts follow rails on lowered blocks physically; riders sit where the cart is drawn.
- Boats and armor stands placed onto a lowered block land on its visible surface.
- These apply to blocks placed in this version. On older blocks, frames and carts keep the old
  render-only behavior.

### Fixed

- Restored vanilla redstone connection, direction, occlusion, and power rules. Redstone can still
  use supported slab steps, but ordinary solid blocks no longer create phantom wire arms or
  power paths.
- Bottom slabs and compatible bottom-like slab surfaces stay mob-proof: making those surfaces
  usable for placement no longer makes them valid ground for mob spawning.
- Placed heights stay stable when a block changes shape or state in place: fence, wall, and pane
  connections, waterlogging, redstone power, and stair-shape updates.
- Stored heights are no longer misread as whole blocks by the chunk renderer, which could draw a
  correctly placed block far below its outline and server position.
- Fabric's model path no longer applies a height offset twice on some non-vanilla models.

### Limits and notes

- Older blocks keep the old behavior by design. A future version will offer converting them.
- `-Dslabbed.frozenDy=true` makes every block, old or new, read its stored height (a block with no
  stored height reads flat). This is an opt-in for testing, not a supported way to play older worlds.
- No claim is made about compatibility with large rendering or optimization modpacks beyond the
  mods listed as tested in the release notes.
- Open reports about delayed render refresh, missing item icons, and glass-pane crashes in large
  packs are not declared fixed by this release without their own retest.
- This is a Fabric 1.21.1 changelog. Changes released on other Minecraft versions or loaders are
  not its baseline.

## [Unreleased — deferred]
- Terrain Slabs named-surface compatibility (objects lowering onto Terrain Slabs surfaces, compound −1.0) is planned for a follow-up; this build keeps the existing gated compat (Terrain Slabs blocks are excluded from Slabbed's visual offsets).

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
