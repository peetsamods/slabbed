# Adjacent Side Slab dy Inheritance — Design Slice

## Base
- Branch: `diagnose/adjacent-side-slab-dy-inheritance-design`
- From: `cbf4e98` / tag `save/lowered-side-side-slab-visual-culling-diagnosis`
- Production code changed: NO

---

## Current placement status
Placement is correct and trace-confirmed:
- Lowered stone block at `6, -59, -48` (dy = -0.5 from bottom slab below)
- Placed birch slab at `6, -59, -47` (side-adjacent, dy = 0.0)
- Visual mismatch: birch slab renders 0.5 blocks higher than the visual surface of the lowered stone

---

## Why the mismatch exists

`SlabSupport.getYOffsetInner` for a `SlabBlock`:
```
if (state.getBlock() instanceof SlabBlock) {
    BlockPos belowPos = pos.down();
    BlockState below = world.getBlockState(belowPos);
    if (!(belowBlock instanceof SlabBlock) && !below.isAir()
            && below.getFluidState().isEmpty()
            && hasBottomSlabBelow(world, belowPos)) {
        return -0.5;
    }
}
```
This only offsets a slab that is **directly above** a non-slab, non-air block that has a bottom slab below it.
An **adjacent side slab** at the same Y does not satisfy this rule — it looks sideways, not down.

`shouldOffset` also explicitly guards: `if (state.getBlock() instanceof SlabBlock) return false;`

So there is no current path that gives an adjacent bottom slab a -0.5 dy.

---

## Design questions answered

### 1. Should adjacent side slabs visually align with a lowered full block?
Desirable. The placed birch slab should visually hug the lowered side face of the stone,
meaning its model, outline, and raycast should all be at the same -0.5 visual Y as the stone.

### 2. Visual-only or full model/outline/raycast triad?
**Full triad required.** `SlabSupport.getYOffset` feeds:
- `BlockModelDyTranslateMixin` (model render)
- `SlabSupportStateMixin.slabbed$offsetOutline` (outline / hit-box wireframe)
- `SlabSupportStateMixin.slabbed$offsetRaycast` (raycast shape)

Any dy rule must return the same value for all three. A visual-only fix without triad
alignment would break raycast targeting and outline rendering.

### 3. If adjacent slab gets dy=-0.5, should outline and raycast also shift?
**Yes.** They must stay in triad — model = outline = raycast.
If outline/raycast do NOT shift, a slab that visually appears at the lowered level
will be interactable only at the standard Y level, creating a ghost-face-style pick mismatch.

### 4. Exact condition candidates for the intended case

#### Candidate A — Bottom check extended laterally
A bottom slab gets dy=-0.5 when ANY of the 4 horizontal neighbors at the same Y
is an ordinary solid lowered full block (getYOffset == -0.5) AND the slab is type=bottom.

Risks:
- **Over-broad**: any bottom slab placed next to any lowered block will offset,
  including normal builds where the player just happens to have a lowered stone nearby.
- Cannot distinguish "placed by lowered-side remap" from "coincidentally adjacent".
- Would cause cascading: a chain of bottom slabs along a lowered wall all offset.

#### Candidate B — Block state tag / NBT flag
Mark the placed slab with a custom tag at placement time (remap path sets a marker).
Offset only if tag present.

Risks:
- Block state tags are not available in vanilla SlabBlock (would require a new block subclass
  or blockstate property). Custom NBT is lost on chunk save/reload unless stored in
  a block entity, which slabs don't have.
- **Not feasible without fundamentally changing the block type.**

#### Candidate C — Detect "no block below, neighbor is lowered full block" at same Y
A bottom slab gets dy=-0.5 when:
- Its own `pos.down()` block has `hasBottomSlabBelow` == false (ordinary ground, not already on a slab stack)
- Its `pos.down()` block is either air or a full block with dy=0.0
- At least one horizontal neighbor at `pos` is an ordinary solid lowered full block (getYOffset == -0.5)
- The slab itself is type=bottom

Risks:
- **Still over-broad**: a bottom slab placed naturally next to any lowered block wall
  in a normal world would offset, even if it has nothing to do with a lowered-side placement.
- Breaks "normal slab adjacent to lowered decoration" cases.

#### Candidate D — Vertical re-check: slab at same-Y as lowered block, BELOW original slab pos
If the bottom slab is at the same Y as the lowered block, and the block BELOW the slab
has a bottom slab below it (i.e., the lowered stone's slab support extends laterally),
this is equivalent to: `hasBottomSlabBelow(world, pos)` where the bottom slab is
the same support slab for the lowered stone.

Concrete check:
- `state.getBlock() instanceof SlabBlock`
- `state.get(SlabBlock.TYPE) == SlabType.BOTTOM`
- `hasBottomSlabBelow(world, pos)` — the slab at pos.down() is a bottom slab

This is equivalent to: "this bottom slab is sitting directly above a bottom slab support".
This is **already handled** by the existing slab-on-offset-block path IF the block below
is a non-slab non-air block with a slab below it. But a slab directly on a slab (`below instanceof SlabBlock`)
is explicitly excluded.

**Wait** — the key insight: in the traced scenario, the placed birch slab is at `6,-59,-47`.
What is at `6,-60,-47` (one below)? If there is no block there (air), then `hasBottomSlabBelow` returns false.
If there IS a bottom slab at `6,-60,-47` (the same support slab that runs under both the stone and the birch),
then `hasBottomSlabBelow(world, pos)` would return true for the birch slab position.

The existing rule:
```java
if (!(belowBlock instanceof SlabBlock) && !below.isAir()
        && below.getFluidState().isEmpty()
        && hasBottomSlabBelow(world, belowPos)) {
    return -0.5;
}
```
requires the block directly below to be a non-slab non-air solid. If `belowPos` is air,
this returns 0.0. If `belowPos` is itself a bottom slab, it also returns 0.0 (slab excluded).

So: if the support slab continues laterally under the birch slab position,
the birch slab would be sitting directly on the bottom slab — excluded.
If air is below the birch slab, also excluded.

### 5. Cases that must NOT offset
- Any ordinary bottom slab placed on normal ground with no lowered context
- Slabs that are not adjacent to any lowered block
- Top slabs and double slabs under all circumstances
- Any slab where the neighbor happens to be lowered but the player placed normally
- Carpet and thin top-layer blocks (already guarded by `isThinTopLayer`)

### 6. Double-shift risk
**High.** If an adjacent slab also gets -0.5, and then something is placed on top of THAT slab,
the stacking rule (`slab-on-offset-block`) would apply to that new block, shifting it
another -0.5 from an already-shifted base. The visual column would look correct but the
actual raycast / outline positions must also chain correctly.

### 7. Where would this belong — SlabSupport.getYOffset or ClientDy.dyFor?
**SlabSupport.getYOffset** — because it feeds the triad (model + outline + raycast).
ClientDy is carpet-only and must remain so.

### 8. Is there enough world context to distinguish intentional side slabs?
**No.** Block state alone (SlabType.BOTTOM at a given pos) cannot distinguish
"placed by lowered-side remap" from "placed normally next to a lowered wall".
The remap is a placement-time intent, not a persistent block property.

---

## Recommended next implementation slice

### CONDITIONAL GO — only if the support slab is laterally continuous

The safest and narrowest rule is an **extension of the existing `getYOffsetInner` slab branch**:

Current rule: offset slab if block below is non-slab solid with `hasBottomSlabBelow`.

**Proposed extension**: also offset a bottom slab if the block directly below it is
a **bottom slab** (not air) AND a horizontal neighbor at the same grid Y is an ordinary
solid lowered full block (getYOffset == -0.5).

Concrete:
```java
// Slab at same Y as a lowered neighbor, sitting on the shared support slab
if (state.getBlock() instanceof SlabBlock
        && state.get(SlabBlock.TYPE) == SlabType.BOTTOM) {
    BlockPos belowPos = pos.down();
    BlockState below = world.getBlockState(belowPos);
    if (below.getBlock() instanceof SlabBlock
            && isBottomSlab(below)) {
        // check horizontal neighbors at same Y for an ordinary lowered full block
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (neighbor.getBlock() instanceof SlabBlock) continue;
            if (!neighbor.isSolidBlock(world, neighborPos)) continue;
            if (getYOffset(world, neighborPos, neighbor) == -0.5) {
                return -0.5;
            }
        }
    }
}
```

**Risk analysis of proposed extension:**
- Triggers only when: bottom slab, sitting directly on a bottom slab, adjacent to a lowered solid
- Normal slab-on-slab (double slab): excluded because SlabType.BOTTOM check and
  the below is also a slab
- Slab on solid floor with nearby lowered block but NOT directly above the support slab:
  not triggered (below is solid, not a slab)
- Would trigger for any bottom slab placed manually on a support slab next to a lowered wall —
  **this is the correct desired behavior** in all such cases

**Remaining risk:**
- A player builds a slab floor (support slab layer) and places a decorative slab next to
  a lowered stone — that slab would also offset. This may be desired (visual consistency)
  or surprising (player didn't use lowered-side remap).
- Reversibility: if the lowered full block is removed, the neighbor check would return 0.0
  and the adjacent slab would snap back to standard height — may cause a visual pop.

### VERDICT: IMPLEMENT THIS SLICE
The rule is narrow, the trigger is specific (bottom slab on bottom slab next to lowered solid),
and the triad is preserved through `getYOffset`. The remaining risk (non-remap-placed slab
also offsets) is acceptable and actually matches user expectation: a slab placed flush against
a lowered wall should look flush.

---

## Required proof gates for implementation
1. Model + outline + raycast alignment at dy=-0.5 for placed adjacent slab
2. Full block (stone) baseline dy unchanged
3. Bottom slab side placement: outline hugs lowered face correctly
4. Normal slab placement (no lowered neighbor): dy remains 0.0
5. Repeat-click / ghost face: no double-placement or phantom click
6. Carpet invariant: `ClientDy.dyFor` unchanged, carpet still uses carpet path
7. Proof bundle verifier: `python3 tools/verify_lowered_side_slab_proof_bundle.py` passes

---

## Next slice title
`fix/adjacent-side-slab-dy-inherit`
