# Beta4 Live-First Seam Ownership Contract

## Current Blocked State

- Current contract savepoint HEAD before proof-harness alignment: `b3e71ff`.
- Current tag at that HEAD: `save/beta4-seam-ownership-contract`.
- Release remains blocked: `0.2.0-beta.4` must not be uploaded, re-tagged, or treated as ready.
- Failed shared seam-owner classifier `763434e` was reverted by `b3a09db`.
- The proof harness was aligned at `991c1dc` / `save/beta4-seam-ownership-proofs`.
- The first live-first classifier was saved at `767f735` / `save/beta4-live-first-seam-owner-classifier`.
- Julia live validation failed: the visible upper lowered slab targeted from the top/upper path, but side-face aim on that same visible body resolved to `NO_RESCUE` / no owner.
- This slice fixes that MISS-side proof path in the harness only; release still requires Julia live retest.

## Player-Visible Owner Classes

- Anchored lowered full-block owner: the lowered full block at Y that is anchored to its bottom-slab support and is actually hit on its valid visible/top shape.
- Visible upper lowered slab owner: the lowered slab body directly above the anchored owner at Y+1 when the crosshair is visually on that slab body, not merely near the seam.
- Adjacent visible lowered target owner: a neighboring lowered visible target beside the seam that the player is directly aiming at.
- No rescue / air / behind owner: no visible lowered-owner hit exists on the player ray, or the ray is behind/through air, so Slabbed must keep the initial target or miss instead of inventing ownership.

## Ownership Table

| Case ID | Player aim description | Held item | Initial/vanilla target shape if known | Intended final owner | Must not steal from | Proof status |
| --- | --- | --- | --- | --- | --- | --- |
| A | Centered valid `UP` hit on anchored lowered full block | Slab | Anchored lowered full block, `UP`, interior/valid top | `ANCHORED_FULL_BLOCK` | Visible upper lowered slab / side rescue | GREEN and still trusted for this case only: `[BETA4_ANCHORED_UP_PRESERVE_GREEN]` / previous `[JULIA_BETA4_TARGETING_GREEN]` |
| B1 | Screenshot-style above/front seam where the crosshair reaches the upper lowered slab through the current top/above harness path | Slab | Harness source truth hits the visible upper lowered slab | `VISIBLE_UPPER_LOWERED_SLAB` | Anchored lowered full block | GREEN in focused proof: `[BETA4_SEAM_VISIBLE_UPPER_SLAB_GREEN]`; not sufficient for side-face live validation |
| B2 | Side-face aim on the visible upper lowered slab body, matching Julia's live failure | Slab | Initial target is `MISS`; actual offset-shape side hit proves the visible upper owner | `VISIBLE_UPPER_LOWERED_SLAB` | `NO_RESCUE`, anchored suppression, or wrong neighbor | GREEN in opt-in proof after the MISS-side fix: `[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_GREEN]` |
| C | Adjacent visible lowered target beside the seam | Slab | Adjacent lowered slab/body is the visible target | `ADJACENT_VISIBLE_TARGET` | Centered anchored owner / visible upper owner | GREEN and still trusted for this boundary: `[BETA4_ADJACENT_VISIBLE_SEAM_GREEN]` / previous `[JULIA_BETA4_ADJACENT_VISIBLE_GREEN]` |
| D | Air, miss, or behind case with no actual visible lowered-owner hit | Slab or non-slab | `MISS`, air, or non-owner/behind shape | `NO_RESCUE` / `KEEP_INITIAL` | Any inferred anchored, upper, or adjacent owner | GREEN for slab-held air/miss boundary: `[BETA4_SEAM_NO_RESCUE_GREEN]` |
| E | Same/near seam while holding a non-slab block | Non-slab block | Same visible-owner truth as slab-held path, minus slab placement-intent guards | Same visible owner class as live source truth; usually `KEEP_INITIAL` unless a visible lowered owner is actually hit | Slab-held-only rescue behavior | Missing beta4-specific parity proof. Parity is required for owner identity when the visible owner is real; slab-held may differ only for placement-intent preservation. |

## Existing Proof Map

GREEN and still trusted:

- Previous beta4 anchored-UP targeting proof: `-Dslabbed.juliaBeta4TargetingRedOnly=true`, `runJuliaBeta4StoneSlabTargetingOutlineMismatchRedCase(...)`, marker `[JULIA_BETA4_TARGETING_GREEN]`. This proves Case A only.
- Beta4 anchored-UP preservation marker: opt-in green route `slabbed.beta4SeamGreenProofsOnly=true`, marker `[BETA4_ANCHORED_UP_PRESERVE_GREEN]`.
- Adjacent visible seam proof: `-Dslabbed.juliaBeta4AdjacentVisibleRedOnly=true`, `runJuliaBeta4AdjacentVisibleTargetRedCase(...)`, markers `[BETA4_ADJACENT_VISIBLE_SEAM_GREEN]` and `[JULIA_BETA4_ADJACENT_VISIBLE_GREEN]`. This proves Case C remains protected.
- No-rescue seam boundary: opt-in green route `slabbed.beta4SeamGreenProofsOnly=true` or `slabbed.beta4SeamNoRescueOnly=true`, marker `[BETA4_SEAM_NO_RESCUE_GREEN]`. This proves Case D for the current slab-held air/miss seam boundary.

Green but not enough for the live side-face case:

- Screenshot-intent proof: opt-in route `slabbed.beta4SeamVisibleUpperRedOnly=true`, `runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(...)`, markers `[BETA4_SEAM_VISIBLE_UPPER_SLAB_GREEN]` and `[JULIA_BETA4_SCREENSHOT_INTENT_GREEN]`. This now proves the visible upper lowered slab owner in the harness; release remains blocked pending Julia live validation.

Former RED now green in focused proof:

- Side-face visible upper proof: opt-in route `slabbed.beta4SeamVisibleUpperSideFaceRedOnly=true`, `runBeta4SeamVisibleUpperSideFaceRedCase(...)`, marker `[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_GREEN]`. Current evidence shows expected and actual owner class `VISIBLE_UPPER_LOWERED_SLAB`, `vanillaType=MISS`, `finalType=BLOCK`, and `sideScanCandidateReason=visible-upper-side-face-offset-hit`.

False-green history / not enough:

- The visible-owner fix at `72770c5` passed automation but failed live and was reverted by `b58368d`.
- The shared seam-owner classifier at `763434e` passed enough proof to look plausible but failed live and was reverted by `b3a09db`.
- The live-first classifier at `767f735` passed the above/top visible-upper proof but failed Julia's side-face live test, so `[BETA4_SEAM_VISIBLE_UPPER_SLAB_GREEN]` must not be treated as release evidence for Case B2.
- Green proof is not enough for this bug class unless the proof table first names the player-visible owner and live retest mirrors that owner table.

Missing proof:

- A beta4-specific non-slab parity check if the next classifier touches non-slab routing.
- A live checklist that mirrors Cases A through E before any beta4 release candidate is called ready.

## Required Next Proof Plan

The next implementation slice cannot start by patching gameplay behavior. It must first create or update proofs so they express this contract:

- Side-face visible upper proof expects `VISIBLE_UPPER_LOWERED_SLAB` and stays green after the MISS/no-owner fix.
- Top/above screenshot-intent proof stays green, but it is no longer the complete Case B release proof.
- Anchored-UP preservation stays green for `ANCHORED_FULL_BLOCK`.
- Adjacent visible seam stays green for `ADJACENT_VISIBLE_TARGET`.
- No-rescue boundary stays green for air/behind/miss with no visible lowered-owner hit.
- Live test checklist mirrors the ownership table and records the final owner class before any release decision.

## Proposed Implementation Boundary

No Java implementation is included in this note.

The first implementation extracted a local seam owner classifier/helper in `GameRendererCrosshairRetargetMixin.java` instead of adding another inline one-off guard. The helper classifies player-visible ownership into conceptual outcomes:

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
