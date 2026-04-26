---
name: slabbed-visual-offset-audit
description: Verify strict visual alignment for offset-rendered blocks (model + outline) on slab tops and slab undersides.
---

# Goal
Ensure strict visuals: model Y, outline/selection box, and interaction feel align with the intended support surface.

# Hard stops
- Model is offset but outline is not (or vice versa)
- Baseline lane differs from vanilla
- Any visible clipping or floating on intended supported placements

# Steps
1) Verify top-surface cases (dy = -0.5)
   - Place: torch, carpet-like thin things you support, etc.
   - Confirm model sits on slab top
   - Confirm outline matches
2) Verify underside cases (dy = +0.5)
   - Place: hanging sign, chain (Y-axis), dripstone, spore blossom, hanging roots
   - Confirm they visually anchor to TOP slab underside
   - Confirm outline matches
3) Cascade cases
   - Chain stack under top slab
   - 2x dripstone / vine segments / trapdoor cascade you support
4) Interaction sanity
   - Can reliably target/break the offset block
   - No “ghost feel” clicking empty space
5) Output report
   - List tested blocks + PASS/FAIL
   - If FAIL: identify whether issue is model offset path or outline offset path
