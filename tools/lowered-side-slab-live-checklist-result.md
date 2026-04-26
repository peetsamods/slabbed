# Lowered Side Slab Live Checklist Result

## Session metadata

| Field | Value |
|-------|-------|
| Date | 2026-04-26 |
| Branch | `test/lowered-side-slab-live-checklist-run` |
| HEAD | `d1bd8bb` |
| Tag at start | `save/lowered-side-slab-live-checklist` |
| Automated proof bundle result | **PASS** |
| Live checklist result | **FAIL** |

---

## Item-by-item results

| # | Proof key | Result | Notes |
|---|-----------|--------|-------|
| 1 | `fb_on_bs_lower_half_owner_targeting` | PASS | Crosshair and outline target the lowered block correctly. |
| 2 | `fb_on_bs_lower_half_side_slab_intent` | **FAIL** | Side slab does not place at the crosshair position. Places at the vanilla (non-lowered) location instead. |
| 3 | `fb_on_bs_repeat_click_no_ghost_face` | **FAIL** | Repeat click fails. (Downstream of item 2 side placement failure.) |
| 4 | `torch_on_fb_on_bs_rescue_targeting` | PARTIAL FAIL | Torch places correctly on the top face. On the side face, the torch flame animation appears too high relative to the lowered block. |
| 5 | `bed_on_bs_rescue_targeting` | PASS | Bed places correctly, rescue targeting feels correct, head/foot orientation sane. |
| 6 | `full_block_on_full_block_baseline` | PASS | Vanilla-feeling placement, unaffected. |
| 7 | `slab_on_normal_vanilla_face_baseline` | PASS | Ordinary slab placement, identical to vanilla. |
| 8 | `chain_on_fb_on_bs_no_rescue_targeting` | PASS | No rescue behavior observed. No-rescue boundary holds. |
| 9 | `crafting_table_on_bs_no_rescue_targeting` | PASS | Aiming at the lower half of a crafting table targets the bottom slab — correct under the audited no-rescue boundary for crafting table. Not a failure. |

---

## Failure detail

### Item 2 — Side slab placement intent
Side slab placement when targeting the side face of a lowered full block lands at the vanilla (non-lowered) position, not at the crosshair position. The visual intent (aim at the lowered side, get a slab at that height) is broken. Automated proof passed this case; live play does not.

### Item 3 — Repeat click / ghost-face
Downstream of the item 2 failure. Side placement not at the correct position makes the repeat-click behavior unverifiable as written. Marked FAIL because the precondition (correct side placement) did not hold.

### Item 4 — Torch side animation
Torch places correctly on the side face of the lowered block, but the flame animation appears too high — it does not sit at the lowered model position. This is a visual triad gap: placement is correct but the rendered flame height is misaligned with the lowered position. The visual triad (model / outline / raycast) must agree; this is a partial miss on the model side of the torch visual.

---

## Stop condition triggered

> **Live play contradicts automated proof.**
> Per project rules (RULES §10 and §17): trust live play. Stop. Do not patch in this slice.

---

## Notes

- Items 8 and 9 are confirmed holding correctly. No rescue boundary violations observed.
- Item 9 clarification: Julia observed that aiming at the lower half of a crafting table targets the bottom slab. This is the expected no-rescue behavior — the crafting table is an audited no-rescue target, so the slab being the hit target is correct, not a failure.
- Item 4 is a visual-only partial failure (animation height), not a placement failure. It is distinct from the item 2/3 side placement regression but should be tracked in the same next audit pass.

---

## Final verdict

**STOP. Do not patch in this slice.**

Three live failures identified:
1. **Item 2** — Side slab placement intent broken (places at vanilla position, not crosshair position).
2. **Item 3** — Repeat-click / ghost-face check invalid because item 2 precondition failed.
3. **Item 4 (partial)** — Torch side flame animation too high on lowered block.

**Recommended next slice:** a focused screenshot repro/audit pass that isolates the side placement intent failure (item 2) and the torch flame height discrepancy (item 4) against the proof screenshots. Understand what the automated proof passed and why live play diverges before proposing any fix. Do not bundle the fix with the repro pass.
