# Beta 3.5 Fence Gate Family Fix

Implementation/proof slice for promoting the Beta 3.5 fence-gate contact/support fix from `minecraft:cherry_fence_gate` to the vanilla `FenceGateBlock` family.

Operating base: HEAD `57e6c95` / `save/beta35-cherry-fence-gate-contact` on `integrate/phase19-into-side-slab-top-support`.

Evidence folder: `tmp/beta35-fence-gate-family-fix-57e6c95/`.

No doors, trapdoors, signs, panes, lanterns, chains, end rods, redstone, rails, fence/wall hitboxes, or anvil hitboxes were changed. No release audit was run. No release tag was moved.

## Production Change

`SlabSupport` now treats `state.getBlock() instanceof FenceGateBlock` as the Beta 3.5 fence-gate contact object. The family path uses the same lowered contact dy that made `minecraft:cherry_fence_gate` green: `supportDy=-1.000000` produces `objectDy=-1.500000`.

`SlabSupportStateMixin` uses the same family helper for lowered shape offset and empty-raycast fallback. `OffsetBlockStateModel` was not modified.

## Harness Repair

The first family matrix attempts timed out at `waitForChunksRender` in `runBeta35FenceGateContactAuditRow`. The repaired harness removes that brittle post-row render wait; the model trace still performs targeted render scheduling and waiting before it reads render dy evidence.

The repair also stabilizes the proof player before placement and interaction. That prevents long-matrix drift after several rows and keeps placement results tied to the intended slab-supported target.

## Focused Proof

Gate:

`-Dslabbed.beta35FenceGateFamilyFix=true`

Markers:

- `JULIA_BETA35_FENCE_GATE_FAMILY_GREEN`
- `JULIA_BETA35_FENCE_GATE_FAMILY_ROW`
- `JULIA_BETA35_FENCE_GATE_FAMILY_SUMMARY`

Summary:

`JULIA_BETA35_FENCE_GATE_FAMILY_SUMMARY outcome=GREEN failureLayer=NONE variants=11 rows=22 greenRows=22 closedGreen=11 openGreen=11 interactionRepresentatives=3 interactionRepresentativeGreen=3 scope=FenceGateBlock_family fenceWallHitboxRows=UNCHANGED_PENDING anvilHitboxRows=UNCHANGED_PENDING panes=NOT_COVERED releaseAudit=NOT_RUN releaseTagMoved=false failedRows=none`

Tested variants:

- `minecraft:oak_fence_gate`
- `minecraft:spruce_fence_gate`
- `minecraft:birch_fence_gate`
- `minecraft:jungle_fence_gate`
- `minecraft:acacia_fence_gate`
- `minecraft:dark_oak_fence_gate`
- `minecraft:mangrove_fence_gate`
- `minecraft:cherry_fence_gate`
- `minecraft:bamboo_fence_gate`
- `minecraft:crimson_fence_gate`
- `minecraft:warped_fence_gate`

Closed and open rows are GREEN for every variant. All rows have `contactGap=0.000000` and `triadCoLocated=yes`. Closed collision is category-valid as `category_valid_closed_tall`; open collision is category-valid as `category_valid_open_empty`.

Open/close interaction representatives are GREEN for `minecraft:oak_fence_gate`, `minecraft:cherry_fence_gate`, and `minecraft:crimson_fence_gate`.

## Savepoint Gates

- `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceGateFamilyFix=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceGateContact=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveHitboxGateRed=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallFamilyFix=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `git diff --check` -> clean

## Remaining Blockers

Fence gate contact/support is green for the tested vanilla family. Fence/wall/anvil live hitbox rows remain `PENDING` / `PROOF_HARNESS_GAP` and were not fixed in this slice. Panes remain `NOT_COVERED`.

Beta 3.5 release remains blocked. No release audit was run. No release tag was moved.
