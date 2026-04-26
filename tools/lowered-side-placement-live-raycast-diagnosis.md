# Lowered Side Placement — Live Raycast Diagnosis

## Session metadata

| Field | Value |
|-------|-------|
| Base branch | `diagnose/lowered-full-block-hitbox-live-raycast` |
| Base commit | `5d2a4e5` |
| Base tag | `save/lowered-side-placement-live-failure-repro` |
| Live failure record | `tools/lowered-side-slab-live-checklist-result.md` |
| Screenshot repro artifact | `build/run/clientGameTest/screenshots/live_repro_side_placement_audit.json` |
| Key repro finding | Camera-A (proof screenshot angle, yaw=180) = MISS; Camera-B (east-side, live raycast) = hitY=201.5000 vs synthetic hitY=200.7500 |

---

## Layer diagnosis table

| Layer | Current behavior | Uses `getYOffset`? | Matches lowered visual? | Status |
|-------|------------------|--------------------|-------------------------|--------|
| **Model** | Visual model offset −0.5 via render quad pipeline | Yes (ClientDy path) | Yes — block appears at Y[200.5, 201.5] | OK |
| **Outline shape** | `SlabSupportStateMixin.slabbed$offsetOutline` offsets `getOutlineShape` result by `getYOffset` = −0.5 | Yes | Yes — outline at Y[200.5, 201.5] | OK |
| **Raycast shape** | `SlabSupportStateMixin.slabbed$offsetRaycast` offsets `getRaycastShape` result by `getYOffset` = −0.5 | Yes | Yes — raycast shape at Y[200.5, 201.5] | OK |
| **Crosshair retarget** | No production crosshair mixin for ordinary (non-BE) lowered full blocks. Crosshair uses vanilla `ShapeType.OUTLINE` raycast, which IS offset. | Via outline shape | Yes — crosshair correctly selects the lowered block when ray is within [200.5, 201.5] | OK (with caveats — see below) |
| **Placement intent remap** | `BlockItemPlacementIntentMixin.slabbed$remapLoweredFullBlockSideHit` remaps hit Y to `targetPos.getY() + 0.499` for solid lowered blocks on horizontal side hits | Yes (`getYOffset == -0.5` guard) | Yes — remap fires correctly when conditions met | OK |
| **Proof fixture** | Camera-A (yaw=180, looking north) = **MISS** — cannot hit east face. Synthetic hit at Y=200.75 constructs the `BlockHitResult` by hand and calls `interactBlock` directly, bypassing the live raycast entirely. | n/a | n/a | **BROKEN as live-path proxy** |

---

## Exact failing layer

**The proof fixture.**

The automated proof for `fb_on_bs_lower_half_side_slab_intent` is not broken in the sense that the production mixin logic is wrong — it is broken as a **proxy for live player behaviour**. Specifically:

1. The proof screenshot camera (yaw=180, looking north from Z+3.25) is set up for a good visual angle. It **cannot physically aim at the east face** of the full block from that position. The explicit Camera-A raycast in the repro returned **MISS**.

2. The proof constructs a `BlockHitResult` manually at `Y = blockY − 0.25` (below the vanilla block floor, in the lowered visual space) and calls `interactBlock` directly. **No live raycast is performed.**

3. A Camera-B live raycast (east of block, eye at Y = blockY + 0.5, looking west) **confirms the production path works**: the offset raycast shape [200.5, 201.5] is correctly hit, `BlockItemPlacementIntentMixin` fires, and the slab places as BOTTOM at the correct adjacent position.

4. Julia's live failure is therefore **not a production mixin bug** in the path that Camera-B exercises. It is a **proof coverage gap** combined with a **player approach angle mismatch**:
   - The proof only tests the **east face** from the east side.
   - Julia's natural approach angle (likely from the south, matching the screenshot camera orientation, or at a different eye height) put her either: (a) clicking the **south face** (not east), sending the slab to a different adjacent block than the proof's `reproPlacePos`; or (b) with her eye **above Y = 201.5** (the top of the offset outline), causing the outline raycast to miss the offset shape and target something unexpected.

---

## Why the automated proof passed

The proof never performs a live raycast. It constructs a synthetic `BlockHitResult` with the exact correct block, face, and Y position, then calls `interactBlock` directly. The production mixin (`BlockItemPlacementIntentMixin`) fires correctly on this synthetic hit and places the slab correctly. The proof is testing "does the mixin correctly handle a perfectly-crafted hit?" — not "does a live player see and click the correct target?"

---

## Why live play failed

Julia's live approach angle was not equivalent to Camera-B. The most likely causes, in order:

1. **Wrong face**: The screenshot camera (used to see the scene) is positioned south of the block, looking north. A player following the screenshot's implied POV would click the **south face** of the block. The proof tests the **east face**. Clicking the south face places the slab to the south of fullPos — a completely different position than `reproPlacePos`. The mixin fires for any horizontal face, but the target position changes.

2. **Eye above the offset outline**: If Julia's feet were at ground level (approx. Y=200, the slab surface) and she was standing upright (eye ≈ Y=201.62), a horizontal ray would be at Y=201.62, which is **above** the offset outline top (Y=201.5). The outline raycast would miss the full block's offset shape. The crosshair would then target whatever is behind the block (next block west), or report MISS, and placement would go to an unexpected location.

3. **Interaction of (1) and (2)**: Standing to the south with eye above the offset outline simultaneously.

---

## What is NOT failing

- `SlabSupportStateMixin` outline and raycast offsets — both fire correctly for stone on a bottom slab (`getYOffset` returns −0.5).
- `BlockItemPlacementIntentMixin` placement remap — fires correctly when the hit is on the correct face of a solid lowered block.
- `SlabSupport.getYOffset` / `shouldOffset` / `hasSlabInColumn` chain — correctly identifies stone on bottom slab as a −0.5 offset block.
- The Camera-B live path — works end-to-end, placing BOTTOM slab at the correct adjacent position.

---

## Proposed next slice title

**`test: expand side slab proof to cover natural approach angle and confirm Camera-B live path with Julia`**

This slice should:
1. Verify live play with Camera-B-equivalent position (east of block, looking west at Y+0.5, pitch=0) to confirm Julia can reproduce the PASS from that angle.
2. Expand the proof fixture to add the SOUTH face scenario (natural screenshot camera direction), or confirm south-face placement also works correctly.
3. If south-face placement is broken, that is the real fix target — scoped to south-face remap in `BlockItemPlacementIntentMixin` (the face direction does not need special casing, but the placement position for south face must be verified).
4. Do not touch torch flame animation, rescue logic, or any other category in this slice.

---

## Stop conditions for next slice

- Any temptation to broaden rescue
- Any change to SlabSupport, torch, bed, chain, crafting-table logic
- Any attempt to change the visual triad for an unrelated block family
- Expanding beyond the single face/position verification
