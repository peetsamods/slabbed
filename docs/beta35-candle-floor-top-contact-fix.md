# Beta 3.5 Candle Floor Top Contact Fix

## Scope

This slice fixes `minecraft:candle` only as the next floor/top-surface representative after the floor/top object family audit at `08198ef` / `save/beta35-floor-top-object-family-audit`.

`minecraft:torch` remains the reference/live-accepted control. `minecraft:flower_pot`, standing `minecraft:oak_sign`, wall torches, lanterns, chains, wall signs, hanging signs, redstone, and rails remain outside this implementation slice.

## Previous Failure Layer

The audit proved candle placement and survival were already good, but candle visual contact was too shallow:

- lowered bottom support: `supportDy=-1.000000`, previous `objectDy=-0.500000`, previous `contactGap=1.000000`
- plain bottom support: `supportDy=-0.500000`, previous `objectDy=-0.500000`, previous `contactGap=0.500000`
- previous classification: `CONTACT_GAP`

The first implementation pass closed the contact gap, then the focused proof exposed a second candle-specific layer: candle raycast did not share the lowered outline basis, so the row classified as `TRIAD_MISMATCH`.

## Fix

`SlabSupport.getYOffsetInner(...)` now has a `Blocks.CANDLE`-only floor/top contact branch. It reuses the existing floor-torch bottom-slab support dy calculation but applies it only to `minecraft:candle`; a lowered bottom support at `supportDy=-1.0` gives `objectDy=-1.5`, and a plain bottom support at `supportDy=-0.5` gives `objectDy=-1.0`.

`SlabSupportStateMixin` now has a candle-only raycast basis hook. When the lowered `minecraft:candle` raycast shape is empty, it returns the already-lowered outline shape so model, outline, and raycast stay co-located.

No flower pot survival law, standing sign renderer/block-entity path, side-attached category, ceiling/hanging category, all-object support, or global sturdy-face/solidity rule was changed.

## Proof

Focused gate: `-Dslabbed.beta35CandleFloorTopContact=true`

Required markers:

- `JULIA_BETA35_CANDLE_FLOOR_TOP_CONTACT_GREEN`
- `JULIA_BETA35_CANDLE_FLOOR_TOP_CONTACT_SUMMARY`

Results:

- lowered bottom support: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `triadCoLocated=yes`, `classification=GREEN_ALREADY_INHERITS`
- plain bottom support: `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `triadCoLocated=yes`, `classification=GREEN_ALREADY_INHERITS`
- summary: `expectedRowsGreen=true failureLayer=NONE`

Updated family audit:

- `minecraft:torch`: `GREEN_ALREADY_INHERITS`
- `minecraft:candle`: `GREEN_ALREADY_INHERITS`
- `minecraft:flower_pot`: unchanged `SURVIVAL_FAILURE`
- standing `minecraft:oak_sign`: unchanged `CONTACT_GAP` with block-entity/special renderer risk

Validation:

- `./gradlew --no-daemon compileJava compileGametestJava`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CandleFloorTopContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchLoweredSlabPlacement=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `git diff --check`: passed

Evidence folder: `tmp/beta35-candle-floor-top-contact-fix-08198ef`

## Status

The implementation scope is floor torch plus `minecraft:candle` only. Release audit remains paused until Julia decides whether candle is enough for this one-more-family pass or whether flower pot/sign need their own slices.

No release audit was run. No release tag was moved.
