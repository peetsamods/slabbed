# Beta 3.5 Anvil Contact Fix

## Scope

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

Operating base: `9f3bacf` / `save/beta35-special-fullblock-helper-consolidation`

Evidence folder: `tmp/beta35-anvil-contact-fix-9f3bacf`

Gate: `-Dslabbed.beta35AnvilContact=true`

## Result

`minecraft:anvil` is GREEN for contact and triad on valid slab-supported rows.

Previous failure layer: `CONTACT_GAP`.

New failure layer: `NONE`.

The fix is exact to `Blocks.ANVIL`:

- `SlabSupport.isBeta35SpecialFullblockContactObject(...)` now includes `minecraft:anvil`.
- `SlabSupportStateMixin.slabbed$isBeta35SpecialFullblockRaycastFallbackObject(...)` now includes `minecraft:anvil`.

No all-special-fullblock, all-falling-block, grindstone, lectern, or global sturdy-face/solidity behavior was added.

## Proof

Focused proof summary:

`JULIA_BETA35_ANVIL_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:anvil rows=3 expectedRowsGreen=true fallingBehavior=STABLE_ON_VALID_SUPPORT`

Slab-supported rows:

- Plain bottom support: `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `triadCoLocated=yes`, `survivalResult=SURVIVAL_GREEN`.
- Lowered bottom support: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, `survivalResult=SURVIVAL_GREEN`.

The anvil is treated as `falling_special_shape`; focused proof classifies falling behavior as stable on valid slab-supported surfaces.

Post-live audit at `edbba27` added
`-Dslabbed.beta35LiveHitboxGateRed=true`. The bounded anvil row still reports
`supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`,
`collisionCoLocated=yes`, and `triadCoLocated=yes`, so it did not reproduce
Julia's live hitbox/collision feel as `ANVIL_COLLISION_HITBOX_GAP`. That is
not a release green: current live classification is `PENDING` /
`PROOF_HARNESS_GAP`, and contact/triad green is not sufficient to close a
future hitbox/collision RED if one is reproduced. See
`docs/beta35-live-hitbox-gate-red.md`.

## Matrix

Special-fullblock matrix summary after the fix:

`rows=30 greenAlreadyInherits=24 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=1 outOfScopeForBeta35=0`

Current GREEN special/fullblock set:

- `minecraft:crafting_table`
- `minecraft:furnace`
- `minecraft:bookshelf`
- `minecraft:chest`
- `minecraft:barrel`
- `minecraft:enchanting_table`
- `minecraft:stonecutter`
- `minecraft:anvil`

Still open:

- `minecraft:lectern`: interactive block-entity slice
- `minecraft:grindstone`: special-shape contact slice

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35AnvilContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `git diff --check`: PASS

No release audit was run. No release tag was moved. Canonical checkout was not modified.

Next recommended implementation slice: `minecraft:grindstone`, or `minecraft:lectern` if Julia chooses the interactive block-entity slice.
