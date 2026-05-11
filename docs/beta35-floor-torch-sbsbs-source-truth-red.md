# Beta 3.5 Floor Torch SBSBS Source-Truth RED

- Date: 2026-05-11
- Gate: `-Dslabbed.beta35FloorTorchSbsbsSourceTruthRed=true`
- Evidence folder: `tmp/beta35-floor-torch-sbsbs-source-truth-red-e6736ab`

## Goal
Prove whether Julia’s live SBSBS+floor-torch failure is caused by missing source-truth emission during live placement (anchor/carrier), not by steady-state geometry.

## What was changed for proof only
- Kept existing controlled SBSBS proof path manual anchoring behavior (`runBeta35FloorTorchSbsbsRedProof(..., false)`) unchanged.
- Added source-truth branch (`runBeta35FloorTorchSbsbsRedProof(..., true)`) that builds SBSBS using player-like placement interactions and then runs torch placement/capture.
- Added capture instrumentation markers:
  - `JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_RED`
  - `JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_MEASURED`
  - `JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_SUMMARY`
- Added source-truth capture diagnostics in `Beta35LiveTorchCaptureRecorder` under the same gate:
  - support anchor/carrier/compound state
  - `supportHasBottomSlabBelow`
  - `anchoredFullBlockBelow`
  - `isVanillaPosition`
  - `supportDy`
  - `torchDy`
  - `contactGap`

## Intended interpretation
- If live placement still places torch in vanilla position while source-truth components are missing/incorrect, classify as `SBSBS_SOURCE_TRUTH_NOT_AUTHORED` or `SBSBS_STEADY_STATE_OK_BUT_LIVE_CONSTRUCTION_GAP`.
- Controlled SBSBS steady-state green remains as a control path; this slice is recorder/proof only.

## Stop condition
- No production gameplay logic was modified in this slice.
- No release tags changed or moved.

## 2026-05-11 follow-up fix
- Implemented the narrow production fix in the follow-up slice documented at `docs/beta35-floor-torch-sbsbs-source-truth-fix.md`.
- Live/player-like SBSBS authoring now classifies the top support as `COMPOUND_VISIBLE_OWNER_TOP_SLAB` source truth (`supportDy=-1.000000`) rather than the controlled fixture's persistent lowered carrier.
- `floor_torch` on that owner-top bottom slab is rejected by law because contact would require an illegal lower-than-`dy=-1.000` torch placement.
- Focused gate `-Dslabbed.beta35FloorTorchSbsbsSourceTruthRed=true` is GREEN with `failureLayer=NONE`, `supportDy=-1.000000`, `torchDy=N/A`, and `sourceTruth=SBSBS_OWNER_TOP_SUPPORT_REJECTED_BY_LAW`.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`; Beta 3.5 remains paused pending Julia live retest; no release tag moved.
