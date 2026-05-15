# Beta 3.5 SBSBS Held-Item Acceptance Audit

- auditHead: `9278cd3`
- expectedTagAtHead: `save/beta35-sbsbs-held-item-acceptance-audit`
- branch: `integrate/phase19-into-side-slab-top-support`
- evidenceFolder: `tmp/beta35-sbsbs-held-item-acceptance-audit-c869e92`
- proofFlag: `-Dslabbed.beta35SbsbsHeldItemAcceptance=true -Dslabbed.beta35SlabHeightHitAcceptance=true`
- status: release-ready with limitations; no gameplay fix; no all-item claim

## Source Truth

Julia's 2026-05-14 9:29 PM live video showed inconsistent held-item behavior against an SBSBS-style structure. The release-finalization path is paused until the inconsistency is classified.

This audit answers only the repeatable SBSBS top-visible-support fixture created by the proof harness. If the live video failure was from a side/against-face action or a different SBSBS arrangement, that needs a second side-face fixture matrix before any gameplay fix.

## Fixture

The proof fixture is a vertical SBSBS/BS-FB-0.5S-style stack:

- base bottom slab support
- lowered full-block anchor
- persistent lowered middle carrier slab
- lowered full-block anchor
- visible support bottom slab

The tested interaction aims at the visible support slab's top surface. Each matrix row logs support/object/target dy, initial and final target, target owner, placement result, placed state/position/dy, survival after neighbor update, contact/side/axis gaps when meaningful, classification, and failure layer.

## Matrix Result

Summary marker:

```text
JULIA_BETA35_SBSBS_HELD_ITEM_ACCEPTANCE_SUMMARY rows=16 greenRows=14 redRows=0 notCoveredRows=2 supportStealRows=0 missRows=0 serverRejectRows=0 placementRejectRows=0 survivalPopRows=0 contactGapRows=0 sideAttachmentGapRows=0 axisDeferredRows=0 deferredNoNamedLaneRows=0 recommendedNextAction=RELEASE_WITH_LIMITATIONS releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false
```

Green categories:

- inert full block: `minecraft:stone`
- slab: `minecraft:stone_slab`
- stairs: `minecraft:stone_stairs`
- trapdoor: `minecraft:birch_trapdoor`
- door: `minecraft:spruce_door`
- button: `minecraft:acacia_button`
- floor torch: `minecraft:torch`
- candle: `minecraft:candle`
- flower pot: `minecraft:flower_pot`
- chain: `minecraft:iron_chain`
- fence: `minecraft:oak_fence`
- wall: `minecraft:cobblestone_wall`
- fence gate: `minecraft:oak_fence_gate`
- lantern: `minecraft:lantern`

Red categories:

- none reproduced in this top-visible-support matrix

Explicitly not covered:

- pane: `minecraft:glass_pane`
- thin top layer: `minecraft:white_carpet`

## Classification

- Generic SBSBS visible-target owner gap: not proven for the tested top visible support surface.
- Category-specific failures: not reproduced for the audited green categories in this fixture.
- Release-blocking rows from this matrix: none.
- Documented `NOT_COVERED` rows: pane/glass pane and thin top layer/carpet.
- Slab-jump lane law: separate and still deferred as `SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE`.

## Recommendation

`recommendedNextAction=RELEASE_WITH_LIMITATIONS` for this top-surface matrix only.

Release finalization should remain paused until Julia accepts that limitation boundary or asks for a second SBSBS side-face/against-face matrix matching the video more tightly. Do not treat this as an all-item support claim.

## Validation

Passed:

- `./gradlew --no-daemon compileJava compileGametestJava`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35SbsbsHeldItemAcceptance=true -Dslabbed.beta35SlabHeightHitAcceptance=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35TrapdoorServerValidationFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35DoorHalfServerValidationFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35RegularDoorOwnerFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35VisibleObjectOwnerStability=true -Dslabbed.beta35SlabHeightHitAcceptance=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorButtonContact=true -Dslabbed.beta35SlabHeightHitAcceptance=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35HitboxApertureFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallVisualHitboxStackAim=true -Dslabbed.beta35FenceWallLiveInspect=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallStackContact=true -Dslabbed.beta35FenceWallLiveInspect=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallOwnerServerHit=true -Dslabbed.beta35FenceWallLiveInspect=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CandleFloorTopContact=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchLoweredSlabPlacement=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FlowerPotFloorTopContact=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FlowerPotFloorTopSurvival=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectMatrix=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `./gradlew --no-daemon runClientGameTest --console plain`
- `git diff --check`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35SbsbsHeldItemAcceptance=true -Dslabbed.beta35SlabHeightHitAcceptance=true" ./gradlew --no-daemon runClient --console plain`

The `runClient` smoke observed the slab-height startup marker and was intentionally stopped after startup.
