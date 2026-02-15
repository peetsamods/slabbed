---
name: slabbed-client-offset-triad
description: Keep slab dy consistent across model rendering, outline, and raycast targeting so visuals and interaction never diverge.
---

Problem
Slabbed applies a client dy offset, but Minecraft has three separate “surfaces” that must match:
1) Model (rendered quads)
2) Outline/wireframe (block highlight)
3) Raycast targeting (what the crosshair actually hits)

If only 1 is updated: outline floats.
If 1+2 are updated: clicking still happens “in the air above.”
This is a regression pattern and must be prevented structurally.

Invariant (non-negotiable)
For any block/item rendered with dy != 0:
- the outline must use the same dy
- the client raycast must use the same dy
Any mismatch is a bug even if placement works.

Implementation rule
There must be exactly one shared helper that computes dy:
- ClientDy.dyFor(world, pos, state) -> float
All three paths must call it. No duplicate dy logic.

No shared mutable dy state
No static “current dy.” If a context flag is needed, it must be ThreadLocal and cleared in try/finally.

Where dy must be applied
A) Model/quads: OffsetBlockStateModel (or wrapper)
B) Outline: WorldRenderer renderTargetBlockOutline hook
C) Raycast: crosshair raycast shape getter during GameRenderer targeting

Raycast gotcha (must-follow)
- Do not raycast against VoxelShapes that have been translated into negative local Y (minY < 0), especially for thin shapes like carpets.
- For dy = -0.5 in raycast context, prefer evaluating the hit test at effectivePos = pos.down() and using effectiveShape = shape.offset(0, +0.5, 0) so the shape stays within local [0..1] space while matching the same world-space geometry.
- This rule is raycast-only; model and outline still apply dy normally.

Testing requirement per “new offsettable”
In a 3-lane test world (full blocks / bottom slabs / top slabs):
- Model height correct
- Outline hugs model
- Clicking matches outline (no “aim above”)
- Chunk reload does not reintroduce divergence
- Crafting-table seam ghost repro does not return

Stop conditions
If any change updates only 1 or 2 surfaces, stop and rollback.
