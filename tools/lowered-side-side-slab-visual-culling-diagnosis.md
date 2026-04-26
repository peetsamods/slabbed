# Lowered Side Slab — Visual Culling Diagnosis

## Base
- Branch: `diagnose/lowered-side-side-slab-visual-culling`
- Head: `8ae6935`

## Placement trace summary
- Target (lowered stone): `6, -59, -48`
- Placed birch slab: `6, -59, -47`
- finalPlacedSlabType: `bottom`
- verdict: `guard_matched_expected_side_placement`
- Placement intent is confirmed correct by trace.

## Visual setup geometry

### Lowered stone block
- Block grid position: `y = -59`
- SlabSupport.getYOffset: `-0.5`
- Visual model baseline: `y = -59.5`
- Visual top surface: `y = -58.5`

### Placed birch slab (adjacent, one block south)
- Block grid position: `y = -59`
- SlabSupport.getYOffset for a bottom slab NOT above the lowered stone's own bottom slab: `0.0`
- Visual model baseline: `y = -59.0`
- Visual top surface: `y = -59.5`

## The visual discrepancy

The placed birch slab renders at the standard `y = -59.0` baseline.
The lowered stone block renders with its visual body dropped 0.5 units to `y = -59.5`.

From a normal eye-height camera angle looking south:
- The lowered stone appears sunken.
- The adjacent birch slab appears at the standard grid height — 0.5 blocks higher than the lowered stone's visual baseline.
- This makes the placed slab look like it is sitting beside or above the stone at an unexpected level, not flush against the lowered face.

Julia is likely interpreting this height mismatch as "wrong/top/vanilla placement" when the block is actually placed at the correct adjacent side position.

## Face culling status
- No ghost face or face culling defect was proven in this slice.
- The shared face between the lowered stone and the adjacent bottom slab is an internal face between two different-height visual models.
- There may be a visible gap or depth difference that reads as a visual discontinuity.

## Whether production visual fix is warranted
- The placement itself is correct (trace confirmed).
- However, the adjacent side slab's visual Y does not match the lowered stone's visual Y.
- If the adjacent bottom slab is also supposed to inherit the `-0.5` dy offset to align visually with the lowered stone, then `SlabSupport.getYOffset` for a slab placed adjacent to a lowered full block on a bottom slab may need a "lateral neighbour lowering" rule.
- This would be a **new feature / visual alignment fix**, not a placement bug fix.
- It must be designed very carefully to avoid offsetting all bottom slabs placed adjacent to any lowered block.

## Conclusion
- The placement fix is working.
- Julia is seeing a visual height mismatch between the lowered stone and the correctly placed adjacent slab.
- The adjacent slab is NOT being visually lowered to match the lowered stone's dy.
- This is a separate visual alignment slice, not a ghost-face or placement failure.

## Next recommended slice
- `fix/lowered-side-adjacent-slab-dy-inherit`
