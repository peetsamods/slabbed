# Beta4 Live-First Seam Ownership Contract

## Current Blocked State

- Current proof/evidence HEAD: `b3a09db`.
- Current tag at HEAD: `save/revert-failed-seam-owner-classifier`.
- Release remains blocked: `0.2.0-beta.4` must not be uploaded, re-tagged, or treated as ready.
- Failed shared seam-owner classifier `763434e` was reverted by `b3a09db`.
- This slice is design/proof only. No gameplay fix is included here.

## Player-Visible Owner Classes

- Anchored lowered full-block owner: the lowered full block at Y that is anchored to its bottom-slab support and is actually hit on its valid visible/top shape.
- Visible upper lowered slab owner: the lowered slab body directly above the anchored owner at Y+1 when the crosshair is visually on that slab body, not merely near the seam.
- Adjacent visible lowered target owner: a neighboring lowered visible target beside the seam that the player is directly aiming at.
- No rescue / air / behind owner: no visible lowered-owner hit exists on the player ray, or the ray is behind/through air, so Slabbed must keep the initial target or miss instead of inventing ownership.

## Ownership Table

| Case ID | Player aim description | Held item | Initial/vanilla target shape if known | Intended final owner | Must not steal from | Proof status |
| --- | --- | --- | --- | --- | --- | --- |
| A | Centered valid `UP` hit on anchored lowered full block | Slab | Anchored lowered full block, `UP`, interior/valid top | `ANCHORED_FULL_BLOCK` | Visible upper lowered slab / side rescue | GREEN and still trusted for this case only: previous `[JULIA_BETA4_TARGETING_GREEN]` anchored-UP proof |
| B | Screenshot-style above/front seam where the crosshair is visually on the upper lowered slab body | Slab | Initial may still report anchored/lower-front seam depending ray reconstruction | `VISIBLE_UPPER_LOWERED_SLAB` | Anchored lowered full block | RED and intentionally expected before the next fix: `[JULIA_BETA4_SCREENSHOT_INTENT_RED]` |
| C | Adjacent visible lowered target beside the seam | Slab | Adjacent lowered slab/body is the visible target | `ADJACENT_VISIBLE_TARGET` | Centered anchored owner / visible upper owner | GREEN and still trusted for this boundary: `[JULIA_BETA4_ADJACENT_VISIBLE_GREEN]` |
| D | Air, miss, or behind case with no actual visible lowered-owner hit | Slab or non-slab | `MISS`, air, or non-owner/behind shape | `NO_RESCUE` / `KEEP_INITIAL` | Any inferred anchored, upper, or adjacent owner | Missing proof: add an explicit no-rescue boundary before implementation is accepted |
| E | Same/near seam while holding a non-slab block | Non-slab block | Same visible-owner truth as slab-held path, minus slab placement-intent guards | Same visible owner class as live source truth; usually `KEEP_INITIAL` unless a visible lowered owner is actually hit | Slab-held-only rescue behavior | Missing beta4-specific parity proof. Parity is required for owner identity when the visible owner is real; slab-held may differ only for placement-intent preservation. |

## Existing Proof Map

GREEN and still trusted:

- Previous beta4 anchored-UP targeting proof: `-Dslabbed.juliaBeta4TargetingRedOnly=true`, `runJuliaBeta4StoneSlabTargetingOutlineMismatchRedCase(...)`, marker `[JULIA_BETA4_TARGETING_GREEN]`. This proves Case A only.
- Adjacent visible seam proof: `-Dslabbed.juliaBeta4AdjacentVisibleRedOnly=true`, `runJuliaBeta4AdjacentVisibleTargetRedCase(...)`, marker `[JULIA_BETA4_ADJACENT_VISIBLE_GREEN]`. This proves Case C remains protected.

RED and intentionally expected:

- Screenshot-intent proof: `-Dslabbed.juliaBeta4AboveAngleRedOnly=true`, `runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(...)`, marker `[JULIA_BETA4_SCREENSHOT_INTENT_RED]`. This should fail until the live-first visible upper owner is implemented.

False-green history / not enough:

- The visible-owner fix at `72770c5` passed automation but failed live and was reverted by `b58368d`.
- The shared seam-owner classifier at `763434e` passed enough proof to look plausible but failed live and was reverted by `b3a09db`.
- Green proof is not enough for this bug class unless the proof table first names the player-visible owner and live retest mirrors that owner table.

Missing proof:

- A no-rescue boundary for air/behind/miss with no visible lowered-owner hit.
- A beta4-specific non-slab parity check if the next classifier touches non-slab routing.
- A live checklist that mirrors Cases A through E before any beta4 release candidate is called ready.

## Required Next Proof Plan

The next implementation slice cannot start by patching gameplay behavior. It must first create or update proofs so they express this contract:

- Screenshot-intent proof expects `VISIBLE_UPPER_LOWERED_SLAB` and fails before the fix.
- Anchored-UP preservation stays green for `ANCHORED_FULL_BLOCK`.
- Adjacent visible seam stays green for `ADJACENT_VISIBLE_TARGET`.
- No-rescue boundary exists for air/behind/miss if no such proof is already present.
- Live test checklist mirrors the ownership table and records the final owner class before any release decision.

## Proposed Implementation Boundary

No Java implementation is included in this note.

The next implementation should extract one seam owner classifier/helper instead of adding another inline one-off guard to `GameRendererCrosshairRetargetMixin.java`. The helper should classify player-visible ownership into conceptual outcomes only:

- `ANCHORED_FULL_BLOCK`
- `VISIBLE_UPPER_LOWERED_SLAB`
- `ADJACENT_VISIBLE_TARGET`
- `KEEP_INITIAL`
- `NO_RESCUE`

The implementation slice should map these outcomes back to the existing retarget paths after the proof plan exists. It should not broaden rescue or change placement/lane grammar.

## Hard Non-Negotiables

- Do not upload beta4.
- Do not move `release/0.2.0-beta.4`.
- Do not broaden rescue.
- Do not change `SlabSupport` lane grammar.
- Do not stage `tmp/`.
- Do not call the current seam issue fixed.
- Do not add a final Bug Blaster for this seam issue yet.
