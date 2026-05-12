# Beta 3.5 Flower Pot Floor Top Survival Fix

## Scope

This slice fixes `minecraft:flower_pot` survival only on the same valid slab-supported floor/top surfaces already proven for floor torch and candle.

Out of scope: standing sign implementation, wall torches, lanterns, chains, buttons/levers, rails/redstone, side-attached or ceiling/hanging objects, all-object support, global slab solidity/sturdy-face changes, release prep, and release audit.

## Failure Layer

Previous failure layer: `SURVIVAL_FAILURE`.

The flower pot placed and survived while its support existed, but the unsupported control remained valid after support removal. The narrow mechanism was the flower pot survival predicate through `AbstractBlock.AbstractBlockState.canPlaceAt(...)`.

## Fix

`SlabSupportStateMixin` now has a `Blocks.FLOWER_POT`-only `canPlaceAt` branch. It allows survival when the block below is either vanilla full-square top support or a Slabbed legal slab top support, and rejects unsupported cases. This is not a broad floor-object or all-item support rule.

## Proof

Focused gate: `-Dslabbed.beta35FlowerPotFloorTopSurvival=true`

Markers:

- `JULIA_BETA35_FLOWER_POT_FLOOR_TOP_SURVIVAL_GREEN`
- `JULIA_BETA35_FLOWER_POT_FLOOR_TOP_SURVIVAL_SUMMARY failureLayer=NONE`

Focused result:

- lowered bottom support: `supportDy=-1.000000`, `placement=GREEN`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`
- plain bottom support: `supportDy=-0.500000`, `placement=GREEN`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`
- summary: `failureLayer=NONE`, `secondaryLayer=CONTACT_GAP`

The survival fix exposes, but does not fix, a separate flower pot visual/contact layer: `contactGap=1.000000` on lowered bottom support and `contactGap=0.500000` on plain bottom support, with `triadCoLocated=no`.

## Matrix Status

- `minecraft:torch`: `GREEN_ALREADY_INHERITS`
- `minecraft:candle`: `GREEN_ALREADY_INHERITS`
- `minecraft:flower_pot`: survival GREEN, secondary `CONTACT_GAP`
- standing `minecraft:oak_sign`: unchanged separate `CONTACT_GAP` with block-entity/special renderer risk

`wall_torch`, lanterns, wall signs, hanging signs, chains, rails, and redstone remain `NOT_COVERED` or out of scope.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FlowerPotFloorTopSurvival=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchLoweredSlabPlacement=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CandleFloorTopContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`

Evidence folder: `tmp/beta35-flower-pot-floor-top-survival-fix-b76643d`

No release audit was run. No release tag was moved. Scope is floor torch plus candle plus flower pot survival only.

## Contact Follow-Up

Follow-up savepoint: `save/beta35-flower-pot-floor-top-contact`

The remaining `CONTACT_GAP` layer is now fixed for `minecraft:flower_pot`. The contact slice reuses the explicit Beta 3.5 floor/top contact dy authority for candle plus flower pot only, and keeps the existing flower-pot survival branch intact.

Focused gate: `-Dslabbed.beta35FlowerPotFloorTopContact=true`

Result:

- lowered bottom support: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`
- plain bottom support: `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`
- summary: `failureLayer=NONE`

Updated matrix status:

- `minecraft:torch`: `GREEN_ALREADY_INHERITS`
- `minecraft:candle`: `GREEN_ALREADY_INHERITS`
- `minecraft:flower_pot`: `GREEN_ALREADY_INHERITS`
- standing `minecraft:oak_sign`: unchanged separate `CONTACT_GAP` with block-entity/special renderer risk

This does not claim all items or all floor/top objects are fixed. Release audit remains paused.
