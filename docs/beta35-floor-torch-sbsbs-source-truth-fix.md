# Beta 3.5 Floor Torch SBSBS Source-Truth Fix

- Date: 2026-05-11
- Base: `4bca184` / `save/beta35-floor-torch-sbsbs-source-truth-red`
- Gate: `-Dslabbed.beta35FloorTorchSbsbsSourceTruthRed=true`
- Evidence folder: `tmp/beta35-floor-torch-sbsbs-source-truth-fix-4bca184`

## Missing source truth mechanism

The live/player-like SBSBS path was not missing lower-stack sync. The first three source-truth components were authored and synced:

- `lowerAnchorBlock`: anchored, `dy=-0.500`
- `middleCarrierSlab`: persistent lowered carrier, `dy=-0.500`
- `upperAnchorBlock`: anchored compound full block, `dy=-1.000`

The mismatch was the top support slab. In the live/player-like path it is authored as `COMPOUND_VISIBLE_OWNER_TOP_SLAB`, not as the controlled fixture's `LOWERED_SLAB_CARRIER_TYPE` support. That source truth gives `supportDy=-1.000` with an anchored full block below. A same-position floor torch would need to go below the legal `dy=-1.000` floor-torch lower bound to make contact, so the legal outcome is rejection by law rather than placing a floating/stale torch.

## Production fix location

- `src/main/java/com/slabbed/util/SlabSupport.java`
  - `isRejectedFloorTorchTopFace(...)` now rejects `floor_torch` on a bottom slab marked `COMPOUND_VISIBLE_OWNER_TOP_SLAB`, matching the existing rejected law for `COMPOUND_VISIBLE_SIDE_LOWER_SLAB`.
- `src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java`
  - `addCompoundVisibleOwnerTopSlab(...)` now triggers `replaceWithStateForNeighborUpdate(Direction.DOWN, pos.up(), ...)` when the owner-top mark is first authored, so any stale torch above that support revalidates through `TorchBlockMixin` and is removed.

This is limited to `floor_torch` support law and existing compound-owner-top source truth. It does not add broad item/object support, global solidity/support lies, or any wall_torch/lantern/signs/chains implementation.

## Focused proof result

Gate:

```text
JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTorchSbsbsSourceTruthRed=true" ./gradlew --no-daemon runClientGameTest --console plain
```

Result: GREEN.

Key markers:

```text
[JULIA_BETA35_FLOOR_TORCH_SBSBS_MEASURED]
  supportType=compound_visible_owner_top
  supportDy=-1.000000
  torchState=Block{minecraft:air}
  torchDy=N/A
  contactGap=N/A
  placementResult=Fail[]
  survivalResult=REJECTED_BY_LAW
  triadResult=REJECTED_BY_LAW
  torchRejectedByLaw=true
  failureLayer=NONE

[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_SUMMARY]
  sourceTruth=SBSBS_OWNER_TOP_SUPPORT_REJECTED_BY_LAW
  failureLayer=NONE
  supportDy=-1.000000
  torchDy=N/A
  redProofResult=GREEN
```

## Regression results

- `compileJava compileGametestJava`: GREEN
- Focused SBSBS source-truth proof: GREEN
- Controlled SBSBS regression (`-Dslabbed.beta35FloorTorchSbsbsRed=true`): GREEN (`contactGap=0.000000`, `failureLayer=NONE`)
- Support finalization regression (`-Dslabbed.beta35FloorTorchSupportFinalizationRed=true`): GREEN
- V2 contact regression (`-Dslabbed.beta35FloorTorchV2ContactGapRed=true`): GREEN
- Fullblock contact regression (`-Dslabbed.beta35FloorTorchFullBlockContactRed=true`): GREEN
- Default `runClientGameTest`: GREEN
- Build gate `clean build -x test`: GREEN
- `git diff --check`: GREEN

## Residual status

- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
- Beta 3.5 remains paused pending Julia live retest.
- No release tag moved.
