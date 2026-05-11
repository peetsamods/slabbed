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
