---
name: slabbed-ceiling-support
description: Add hanging/ceiling attachment support for blocks under top slabs, using SlabSupport.isCeilingSupportSurface as the single source of truth.
---

# Slabbed — Skill: Ceiling Support

## Goal
Allow blocks that require ceiling attachment (chains, hanging signs, lanterns) to place on and survive under top slabs and double slabs.

## Key finding (2026-02-07)
Analysis showed that ceiling support **already works** without additional mixins:
- `SlabSupportStateMixin.isSideSolid` returns true for `Direction.DOWN` on top slabs (all `SideShapeType` variants)
- `SlabSupportBlockMixin.sideCoversSmallSquare` returns true for `Direction.DOWN` on top slabs
- Top slab collision shape `[0,0.5,0 → 1,1,1]` natively satisfies `isSideSolid(DOWN, CENTER)` via shape cache
- `ChainBlock` has no `canPlaceAt` override — places anywhere
- `HangingSignBlock.canPlaceAt` checks `isSideSolid(pos.up(), DOWN, CENTER)` — satisfied by top slabs natively
- Lanterns use `sideCoversSmallSquare(DOWN)` — covered by `SlabSupportBlockMixin`

## When to use this skill
When adding ceiling-attachment support for a new block type under top slabs.

## Hard stops
- Ceiling support leaks to bottom slabs, stairs, fences, walls, or panes
- Baseline full-block behavior changes
- Build fails

## Steps
1. Test whether existing `isSideSolid` / `sideCoversSmallSquare` changes already enable placement
2. If not: check what placement method the block uses (`isSideSolid`, `sideCoversSmallSquare`, or custom)
3. If custom: add a narrow per-block mixin (`canPlaceAt` inject with early return) routing through `SlabSupport`
4. Verify survival after neighbor updates (break and replace the slab above)
5. Verify visual alignment (flush against slab bottom)
6. Build gate
7. Test matrix
8. Commit + tag

## Note on `isCeilingSupportSurface`
A dedicated `SlabSupport.isCeilingSupportSurface(BlockView, BlockPos)` helper was planned but is NOT needed currently. The existing shared hooks cover all known ceiling-attachment blocks. Only add this helper if a future block requires a custom check that the shared hooks don't cover.
