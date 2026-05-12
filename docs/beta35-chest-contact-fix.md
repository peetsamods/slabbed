# Beta 3.5 Chest Contact Fix

Date: 2026-05-12

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

Base: `baf09f0` / `save/beta35-bookshelf-contact`

Evidence folder: `tmp/beta35-chest-contact-fix-baf09f0`

## Scope

This slice fixed `minecraft:chest` only. It did not implement barrel, enchanting table, lectern, stonecutter, grindstone, anvil, door, trapdoor, signs, lanterns, chains, end rods, redstone, or rails. No release audit was run, no release tag was moved, and the canonical checkout was not modified.

## Mechanism

Previous chest failure layer: `CONTACT_GAP` on slab-supported rows, with a first focused attempt exposing a second layer, `CHEST_TRIAD_MISMATCH`, after contact was corrected.

The narrow fix is:

- `SlabSupport` admits exact `Blocks.CHEST` into the Beta 3.5 ordinary/fullblock contact dy helper without admitting barrel or all block entities.
- `SlabSupportStateMixin` supplies a chest-only lowered raycast fallback when vanilla chest raycast is empty, using the already lowered outline as the raycast basis.
- Existing client `BlockEntityOffsetMixin` applies the same `SlabSupport` dy to the chest block entity render path.

## Proof

Focused proof:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta35ChestContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS

Required marker:

`JULIA_BETA35_CHEST_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:chest rows=3 expectedRowsGreen=true`

Slab-supported chest rows report `placementResult=Success`, `blockAppearedAfterAttempt=true`, `blockEntityPresent=true`, `survivalResult=SURVIVAL_GREEN`, `contactGap=0.000000`, `triadCoLocated=yes`, and co-located model, outline, raycast, and collision bounds.

Special-fullblock matrix:

`JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY rows=30 greenAlreadyInherits=12 placementFailure=0 survivalFailure=0 contactGap=10 triadMismatch=2 blockEntityRisk=2 specialRendererRisk=1 needsCategorySlice=3 outOfScopeForBeta35=0 currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,bookshelf,chest doorSlice=PARALLEL_NOT_INSPECTED releaseAudit=NOT_RUN releasePrep=PAUSED productionBehaviorChanged=bookshelf_and_chest_contact_dy_only`

`minecraft:crafting_table`, `minecraft:furnace`, and `minecraft:bookshelf` remain GREEN. `minecraft:barrel` remains `TRIAD_MISMATCH`. `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain separate contact/category slices.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- Focused chest proof: PASS
- Focused special-fullblock audit: PASS
- Focused common-object matrix: PASS
- Default `./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `git diff --check`: PASS
