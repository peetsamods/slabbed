# Beta 3.5 Flower Pot Floor Top Contact Fix

## Scope

This slice fixes `minecraft:flower_pot` visual/contact alignment only on the valid slab-supported floor/top surfaces already proven for floor torch and candle.

Out of scope: standing sign implementation, wall torches, lanterns, chains, buttons/levers, rails/redstone, side-attached or ceiling/hanging objects, all-object support, global slab solidity/sturdy-face changes, release prep, and release audit.

## Failure Layer

Previous failure layer: `CONTACT_GAP` after the survival fix.

The flower pot placed correctly, survived, and rejected unsupported placement, but stayed at the generic lowered-object `objectDy=-0.500000`. That produced `contactGap=1.000000` on lowered bottom support and `contactGap=0.500000` on plain bottom support, with model/outline/raycast not co-located.

## Fix

`SlabSupport.getYOffsetInner(...)` now treats `Blocks.FLOWER_POT` as an explicit Beta 3.5 floor/top contact object alongside `Blocks.CANDLE`. It computes flower pot dy from the support dy so the object bottom contacts the visible slab top.

`SlabSupportStateMixin` now applies the existing lowered floor/top contact raycast fallback to flower pot as well as candle when vanilla raycast shape is empty, preserving model/outline/raycast co-location.

The existing `Blocks.FLOWER_POT` survival branch is preserved. This is not broad all-object support and does not change standing signs.

## Proof

Focused gate: `-Dslabbed.beta35FlowerPotFloorTopContact=true`

Markers:

- `JULIA_BETA35_FLOWER_POT_FLOOR_TOP_CONTACT_GREEN`
- `JULIA_BETA35_FLOWER_POT_FLOOR_TOP_CONTACT_SUMMARY failureLayer=NONE`

Focused result:

- lowered bottom support: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`
- plain bottom support: `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`

## Matrix Status

- `minecraft:torch`: `GREEN_ALREADY_INHERITS`
- `minecraft:candle`: `GREEN_ALREADY_INHERITS`
- `minecraft:flower_pot`: `GREEN_ALREADY_INHERITS`
- standing `minecraft:oak_sign`: unchanged separate `CONTACT_GAP` with block-entity/special renderer risk

`wall_torch`, lanterns, wall signs, hanging signs, chains, rails, and redstone remain `NOT_COVERED` or out of scope.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FlowerPotFloorTopContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FlowerPotFloorTopSurvival=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CandleFloorTopContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchLoweredSlabPlacement=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `git diff --check`: clean

Evidence folder: `tmp/beta35-flower-pot-floor-top-contact-fix-9ce0211`

No release audit was run. No release tag was moved. Scope is floor torch plus candle plus flower pot only.
