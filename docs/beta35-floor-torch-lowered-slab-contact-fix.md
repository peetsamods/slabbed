# Beta 3.5 Floor Torch Lowered Bottom-Slab Contact Fix

- Date: 2026-05-11
- Base: `3212d88` / `save/beta35-floor-torch-sbsbs-source-truth-fix`
- Evidence folder: `tmp/beta35-floor-torch-lowered-slab-contact-fix-3212d88`
- Scope: `floor_torch_only`

## Failure isolated

Julia's live log proved that floor torch placement and survival were not the active failure. The bad layer was visual contact for `floor_torch` over lowered bottom-slab support:

- `supportCandidateState=stone_slab[type=bottom]`
- `supportDy=-1.000000`
- `classification=CONTACT_GAP`
- `failureLayer=WRONG_OBJECT_DY_ON_LOWERED_SLAB_SUPPORT` / `SOURCE_TRUTH_FIX_INCOMPLETE`

The model, outline, and raycast were co-located, so this was not a triad split. They were co-located at the wrong Y for lowered bottom-slab support.

## Mechanism

For a bottom slab marked as `COMPOUND_VISIBLE_SIDE_LOWER_SLAB`, the support slab resolves to `supportDy=-1.000000`. Its visible top is:

```text
supportVisibleTopY = supportY + 0.5 + supportDy
```

For the live bad case at `supportCandidatePos=43,-57,88`, that is `-57.500000`. The old floor torch dy was `torchDy=-1.000000`, making the torch model bottom `-57.000000` and leaving `contactGap=0.500000`.

The fix makes `SlabSupport.getYOffset(...)` return `torchDy=-1.500000` only for `floor_torch` above a bottom slab with the `COMPOUND_VISIBLE_SIDE_LOWER_SLAB` source-truth marker. The existing model, outline, and raycast paths all consume the same dy source, so triad co-location is preserved.

`COMPOUND_VISIBLE_OWNER_TOP_SLAB` remains rejected by law for `floor_torch`; this fix does not reopen owner-top support.

## Retested bad positions

### Previous bad case A

- `torchPos=43,-56,88`
- `supportCandidatePos=43,-57,88`
- `supportCandidateState=stone_slab[type=bottom]`
- Previous `contactGap=0.500000`
- After fix:
  - `supportDy=-1.000`
  - `supportVisibleTopY=-57.500000`
  - `torchDy=-1.500`
  - `torchModelBottomY=-57.500000`
  - `contactGap=0.000000`
  - `triadCoLocated=true`
  - `survival=SURVIVAL_GREEN`
  - `failureLayer=NONE`

### Previous bad case B

- `torchPos=43,-55,79`
- `supportCandidatePos=43,-56,79`
- `supportCandidateState=stone_slab[type=bottom]`
- Previous `contactGap=1.000000`
- After fix:
  - `supportDy=-1.000`
  - `supportVisibleTopY=-56.500000`
  - `torchDy=-1.500`
  - `torchModelBottomY=-56.500000`
  - `contactGap=0.000000`
  - `triadCoLocated=true`
  - `survival=SURVIVAL_GREEN`
  - `failureLayer=NONE`

## Validation

- `compileJava compileGametestJava`: GREEN
- Focused lowered-slab/live-shape proof (`-Dslabbed.beta35FloorTorchLiveShapeRed=true`): GREEN
- Visual contact regression (`-Dslabbed.beta35FloorTorchVisualContactRed=true`): GREEN/PENDING controlled fixture unchanged with `contactGap=0.000000`
- Player-like placement regression (`-Dslabbed.beta35LiveItemAnchoringRed=true`): GREEN
- Object triad regression (`-Dslabbed.beta35ObjectSlabOwnershipRed=true`): GREEN
- Default `runClientGameTest`: GREEN
- `git diff --check`: GREEN

## Scope guard

- `floor_torch_only` remains the active scope.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
- Beta 3.5 release prep remains paused pending Julia live acceptance.
- No release tag moved.
