# Beta 3.5 Barrel Triad Fix

Date: 2026-05-12

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

Base: `0ee0ab3` / `save/beta35-chest-contact`

Evidence folder: `tmp/beta35-barrel-triad-fix-0ee0ab3`

## Scope

This slice fixed `minecraft:barrel` only. It did not implement enchanting table, lectern, stonecutter, grindstone, anvil, door, trapdoor, signs, lanterns, chains, end rods, redstone, or rails. No release audit was run, no release tag was moved, and the canonical checkout was not modified.

## Mechanism

Previous barrel failure layer: `TRIAD_MISMATCH` on slab-supported rows. Placement and survival were already GREEN, contact was already `contactGap=0.000000`, and model, outline, and collision were already co-located. The mismatch was `raycastBounds=empty` while the lowered visual/outline/collision proxy occupied the shifted full-block bounds.

The narrow fix is a barrel-only lowered raycast fallback in `SlabSupportStateMixin`: when `Blocks.BARREL` is lowered and the native raycast shape is empty, raycast uses the already lowered outline basis. This does not add an all block-entity fallback, does not change barrel contact dy, and does not change renderer code.

## Proof

Focused proof:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta35BarrelTriad=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS

Required marker:

`JULIA_BETA35_BARREL_TRIAD_SUMMARY failureLayer=NONE objectId=minecraft:barrel rows=3 expectedRowsGreen=true`

Slab-supported barrel rows report `placementResult=Success`, `blockAppearedAfterAttempt=true`, `blockEntityPresent=true`, `survivalResult=SURVIVAL_GREEN`, `contactGap=0.000000`, `triadCoLocated=yes`, and co-located model, outline, raycast, and collision bounds.

Special-fullblock matrix:

`JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY rows=30 greenAlreadyInherits=15 placementFailure=0 survivalFailure=0 contactGap=10 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=1 needsCategorySlice=3 outOfScopeForBeta35=0 currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,bookshelf,chest,barrel doorSlice=PARALLEL_NOT_INSPECTED releaseAudit=NOT_RUN releasePrep=PAUSED productionBehaviorChanged=bookshelf_chest_contact_dy_and_barrel_raycast_triad`

`minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, and `minecraft:chest` remain GREEN. `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain separate contact/category slices.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- Focused barrel proof: PASS
- Focused special-fullblock audit: PASS
- Focused common-object matrix: PASS
- Default `./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `git diff --check`: PASS
