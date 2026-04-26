# Slabbed — Work Summary (latest)

## Scope & Goals
- Provide generic slab support so blocks/entities visually anchor to slab surfaces.
- Remove collision offsets to avoid player clipping.
- Support chain offsets (blocks on blocks on slabs) and special structures/entities (beds, tall blocks, minecarts, item frames).
- Fix hanging support (lanterns), top-slab placement, and visual offsets.

## Key Changes Implemented
- **Central offset helpers:**
  - `SlabSupport.shouldOffset()` (boolean) for -0.5 offset eligibility.
  - **New** `SlabSupport.getYOffset()` (double) returning:
    - `-0.5` for blocks on/above bottom slabs (including chain stacks).
    - `+0.5` for hanging blocks (`HANGING=true`) directly under top slabs.
    - `0.0` otherwise.
- **Chain offset:** Recursive down-walk (16 deep) for stacked blocks (sign on fence on slab, etc.).
- **Bed coordination:** Either half on a slab offsets both halves.
- **Double-block handling:** Upper halves check two blocks down.
- **Hanging exclusion from downward offset:** `HANGING=true` blocks aren’t pushed down by slabs; instead they can receive the +0.5 upward offset when under top slabs.
- **Stairs:** Re-enabled slab offset (user-requested) despite possible visual quirks; will refine later.
- **Wall-mounted blocks:** Wall signs, wall banners, wall torches, wall hanging signs check the attached block’s column for slab offset.
- **Top-slab support faces:** `isSideSolid` and `sideCoversSmallSquare` return true for `Direction.DOWN` on top slabs (enables hanging attachments).
- **Model/Outline/BE offsets updated to `getYOffset()`:**
  - `TorchModelOffsetMixin` (model offset in chunk meshing).
  - `SlabSupportStateMixin` (outline/hitbox offset).
  - `BlockEntityOffsetMixin` (block entity rendering).
- **Entity render offsets:**
  - **Minecarts:** `MinecartRenderOffsetMixin` injects in `updateRenderState`, adjusts `state.positionOffset` based on rail slab offset (uses `entity.getEntityWorld()`).
  - **Item frames:** New `ItemFrameRenderOffsetMixin` offsets frames based on the block they’re attached to.
- **Mixin registration:** Added `ItemFrameRenderOffsetMixin` to `slabbed.client.mixins.json`.
- **Top-slab lanterns:** Hanging lanterns under top slabs now receive +0.5 Y to sit flush against the slab bottom.

## Current Status (0.1.1-alpha)
- Build succeeds with JDK `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`.
- Client runs; Sodium visual alignment (FRAPI offsets) active.
- Redstone power/hanging support beyond visuals not yet PASS-tagged (documented limitation).
- **Tags:** `slabbed-pre-slice-abc` (post-slice tags pending in-game verification)

## Verified / Expected Behaviors
- Blocks on bottom slabs render at slab height (-0.5); chain stacks supported.
- Beds offset if either half is on slab; tall/double blocks handled.
- Wall-mounted blocks (signs/banners/torches/hanging signs) offset if their attached block is offset.
- Minecarts on rails on slabs should sit correctly (render-state offset).
- Item frames on offset blocks should sit correctly.
- Hanging lanterns under top slabs should sit against slab bottoms (+0.5).
- Stairs now anchor again (visual quirks possible).
- **Redstone dust power propagation** on slabs works without additional mixins (vanilla `calculateWirePowerAt` step-up/step-down logic handles slab positions natively).
- **Chains** place under top slabs (no `canPlaceAt` restriction in `ChainBlock`).
- **Hanging signs** place under top slabs (top slab collision shape satisfies `isSideSolid(DOWN, CENTER)` natively).

## Slice Results (2026-02-07)

### Slice A — Redstone Power Propagation
- **Status:** PARTIAL — visuals + placement verified; power propagation across slab paths not yet verified in-game.
- **Strategy:** already-functional (no code change)
- **Analysis:** `RedstoneController.calculateWirePowerAt` handles same-Y, step-up, and step-down via neighbor checks. Slab positions don't break this (needs in-game confirmation).
// Pending: in-game verification before tagging.

### Slice B — Ceiling Support (Chains + Hanging Signs)
- **Status:** ANALYSIS COMPLETE / VERIFICATION PENDING — vanilla logic appears sufficient; requires in-game placement + survival + reload checks.
- **Strategy:** already-functional (no code change)
- **Analysis:** `ChainBlock` has no `canPlaceAt` override. `HangingSignBlock.canPlaceAt` checks `isSideSolid(pos.up(), DOWN, CENTER)` — top slab collision shape `[0,0.5,0→1,1,1]` satisfies this natively. `SlabSupportStateMixin.isSideSolid` provides additional coverage.
// Pending: in-game verification before tagging.

### Slice C — Regression Sweep
- **Status:** BUILD + ANALYSIS ONLY — regression pass gated on in-game verification of Slices A and B.
- **Strategy:** build gate + code-level analysis (no code changes in A or B means zero regression risk)
- **Build:** PASS

## Known / Potential Issues & Future Work
- **Stairs:** Visual/face-culling quirks may still occur; needs refinement.
- **Rail slopes:** Transition from ground-height rail to slab-height rail remains visually awkward (asset/geometry issue).
- **Slab on offset objects:** Outline-offset makes face targeting tricky; cosmetic/non-breaking.
- **Placement edge cases:** Gaps under top slabs can still be finicky due to targeting.
- **Carpet/snow replacement:** Carpet and snow layers cannot coexist with slab placement in the same block space; placing a slab will replace them (vanilla behavior). Ghosting is avoided by excluding thin top-layer blocks from visual offsets.
- **Hanging roots:** Follow vanilla survival rules; no special slab support yet.
- **TorchBlockMixin:** Exists but is not registered in `slabbed.mixins.json`. Redundant with shared hooks (`SlabSupportStateMixin` + `SlabSupportBlockMixin`). Can be removed or registered if targeted torch behavior is needed later.
- **Compat:** Terralith blocks are excluded from slab offsets when the Terralith mod is present (subtractive-only, mod-gated).
- **Compat:** Optional veto for `terrainslabs` (Countered’s Terrain Slabs) skips Slabbed visual offsets on `terrainslabs:*` blocks to mitigate ghost terrain.

## Files Touched (recent)
- `src/main/java/com/slabbed/util/SlabSupport.java` — add `getYOffset`, `isRedstoneSupportTopSurface`, re-enable stairs, +0.5 for hanging under top slabs.
- `src/main/java/com/slabbed/mixin/SlabSupportStateMixin.java` — outline offset via `getYOffset`, `isSideSolid` for top slab DOWN face.
- `src/main/java/com/slabbed/mixin/SlabSupportBlockMixin.java` — top slab DOWN face `sideCoversSmallSquare` support.
- `src/main/java/com/slabbed/mixin/RedstoneWireBlockMixin.java` — redstone dust placement + visual connections on slabs.
- `src/main/java/com/slabbed/mixin/client/BlockEntityOffsetMixin.java` — block entity render offset via `getYOffset`.
- `src/main/java/com/slabbed/mixin/client/MinecartRenderOffsetMixin.java` — render-state offset.
- `src/main/java/com/slabbed/mixin/client/ItemFrameRenderOffsetMixin.java` — item frame offset.
- `src/main/resources/slabbed.mixins.json` — registered `RedstoneWireBlockMixin`.

## Next Steps
- In-game visual audit for all categories (required before final release tag).
- Investigate rail slope visuals (deferred).
- Optional: refine stairs offset/face-culling handling.
- Optional: clean up unregistered `TorchBlockMixin`.
