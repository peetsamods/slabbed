# Beta 3.5 Floor Top Object Family Audit

## Scope

This audit checks one floor/top-surface object family before Beta 3.5 release prep resumes. The reference control is the already live-accepted `minecraft:torch` / floor torch path from `save/beta35-floor-torch-bug-blaster`.

Out of scope for this slice: `wall_torch`, wall signs, hanging signs, lanterns, chains, buttons/levers, rails/redstone implementation, all-item blanket support, global solidity or sturdy-face changes, release prep, and release audit.

## Proof Gate

Gate: `-Dslabbed.beta35FloorTopObjectFamilyAudit=true`

Markers:

- `JULIA_BETA35_FLOOR_TOP_OBJECT_MATRIX_START`
- `JULIA_BETA35_FLOOR_TOP_OBJECT_ROW`
- `JULIA_BETA35_FLOOR_TOP_OBJECT_SUMMARY`

Evidence folder: `tmp/beta35-floor-top-object-family-audit-8a03902`

Validation:

- `./gradlew --no-daemon compileJava compileGametestJava`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchLoweredSlabPlacement=true" ./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`
- `./gradlew --no-daemon runClientGameTest --console plain`: `BUILD SUCCESSFUL`

## Matrix

| Object | Lowered bottom support `supportDy=-1.0` | Plain bottom support `supportDy=-0.5` | Classification |
| --- | --- | --- | --- |
| `minecraft:torch` | places, survives, unsupported fails, `contactGap=0.000000`, triad co-located | places, survives, unsupported fails, `contactGap=0.000000`, triad co-located | `GREEN_ALREADY_INHERITS` |
| `minecraft:candle` | places and survives, unsupported fails, `objectDy=-0.500000`, `contactGap=1.000000`, triad not co-located | places and survives, unsupported fails, `objectDy=-0.500000`, `contactGap=0.500000`, triad not co-located | `CONTACT_GAP` |
| `minecraft:flower_pot` | places and survives, unsupported remains valid, `objectDy=-0.500000`, `contactGap=1.000000`, triad not co-located | places and survives, unsupported remains valid, `objectDy=-0.500000`, `contactGap=0.500000`, triad not co-located | `SURVIVAL_FAILURE` |
| `minecraft:oak_sign` standing only | places and survives, unsupported fails, `objectDy=-0.500000`, `contactGap=1.000000`, triad not co-located, block-entity/special renderer path | places and survives, unsupported fails, `objectDy=-0.500000`, `contactGap=0.500000`, triad not co-located, block-entity/special renderer path | `CONTACT_GAP` |

Summary marker:

`rows=8 greenAlreadyInherits=2 placementFailure=0 survivalFailure=2 contactGap=4 triadMismatch=0 rendererSpecialCase=0 outOfScope=0 releaseAudit=NOT_RUN releasePrep=PAUSED`

## Finding

The floor/top family does not already inherit the floor-torch support/contact behavior.

`minecraft:candle` and standing `minecraft:oak_sign` share the visible failure layer: player-like placement and survival work, but the object stays at `objectDy=-0.500000` instead of aligning its bottom with the lowered or plain bottom slab support surface. That produces `contactGap=1.000000` on the lowered bottom support and `contactGap=0.500000` on the plain bottom support, with model/outline/raycast not co-located.

`minecraft:flower_pot` is not just the same contact-gap case. It places and survives while support exists, but the unsupported control remains valid after support removal, so its first classification is `SURVIVAL_FAILURE`. It also shows the same contact gap, but should not be bundled with the candle fix until its unsupported survival law is understood.

Standing `minecraft:oak_sign` is represented cleanly enough for placement/survival/contact audit, but its sign block entity and renderer path make it a separate category risk. Wall signs and hanging signs remain out of scope.

## Decision

Implementation is needed before release if Julia wants this one-more-family floor/top category to be green rather than audit-only.

Recommended next slice: target `minecraft:candle` first as the narrow shared floor/top contact representative. Prove one shared contact/dy mechanism for candle only before considering flower pot or standing sign. Do not bundle flower pot because its unsupported survival result differs. Do not bundle standing sign because it has a block-entity/special renderer path.

No production behavior fix was implemented in this audit slice. No release audit was run. No release tag was moved.

`wall_torch`, lanterns, chains, wall signs, and hanging signs remain `NOT_COVERED`.

## Candle Follow-Up Status

Follow-up savepoint: `save/beta35-candle-floor-top-contact`

`minecraft:candle` is now GREEN for floor/top contact and survival on the two audited support rows:

- lowered bottom support: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `triadCoLocated=yes`
- plain bottom support: `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `triadCoLocated=yes`

Updated matrix summary:

`rows=8 greenAlreadyInherits=4 placementFailure=0 survivalFailure=2 contactGap=2 triadMismatch=0 rendererSpecialCase=0 outOfScope=0 releaseAudit=NOT_RUN releasePrep=PAUSED`

`minecraft:flower_pot` remains separate due to `SURVIVAL_FAILURE`. Standing `minecraft:oak_sign` remains separate due to `CONTACT_GAP` plus block-entity/special renderer risk.

This does not claim all items or all floor/top objects are fixed. Release audit remains paused until Julia decides whether candle is enough for this one-more-family pass or whether flower pot/sign require separate slices.
