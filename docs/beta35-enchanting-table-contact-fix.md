# Beta 3.5 Enchanting Table Contact Fix

Date: 2026-05-12

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

Base: `e46cd26` / `save/beta35-barrel-triad`

Evidence: `tmp/beta35-enchanting-table-contact-fix-e46cd26`

## Scope

This slice changed only `minecraft:enchanting_table` contact/triad behavior on valid slab-supported surfaces. It did not implement lectern, stonecutter, grindstone, anvil, doors, trapdoors, signs, lanterns, chains, end rods, redstone, or rails. The canonical checkout was not modified. No release audit was run and no release tag was moved.

## Mechanism

Previous failure layer: `CONTACT_GAP`.

The audited slab-supported rows placed and survived, but `minecraft:enchanting_table` inherited the generic anchored block-entity dy of `-0.500000`. That left the table model/outline/collision bottom above the support surface:

- Plain bottom support: `supportDy=-0.500000`, old `objectDy=-0.500000`, old `contactGap=0.500000`.
- Lowered bottom support: `supportDy=-1.000000`, old `objectDy=-0.500000`, old `contactGap=1.000000`.

The native enchanting-table raycast shape was empty, so the contact repair also needed an exact lowered-outline raycast basis to keep model, outline, raycast, and collision co-located.

Fix:

- `SlabSupport` now includes exact `Blocks.ENCHANTING_TABLE` contact dy in the existing Beta 3.5 slab-supported full-block contact path.
- `SlabSupportStateMixin` now gives exact lowered `Blocks.ENCHANTING_TABLE` rows an outline-backed raycast basis when native raycast is empty.
- The existing block-entity renderer offset path continues to read `SlabSupport.getYOffset`; no renderer rewrite was added.

## Proof

Focused gate:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta35EnchantingTableContact=true" ./gradlew --no-daemon runClientGameTest --console plain`

Focused summary:

`JULIA_BETA35_ENCHANTING_TABLE_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:enchanting_table rows=3 expectedRowsGreen=true`

Observed slab-supported rows:

- Plain bottom support: `placementResult=Success`, `blockEntityPresent=true`, `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `triadCoLocated=yes`, `survivalResult=SURVIVAL_GREEN`.
- Lowered bottom support: `placementResult=Success`, `blockEntityPresent=true`, `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, `survivalResult=SURVIVAL_GREEN`.

Updated special-fullblock summary:

`JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY rows=30 greenAlreadyInherits=18 placementFailure=0 survivalFailure=0 contactGap=8 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=3 outOfScopeForBeta35=0 currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,bookshelf,chest,barrel,enchanting_table doorSlice=PARALLEL_NOT_INSPECTED releaseAudit=NOT_RUN releasePrep=PAUSED productionBehaviorChanged=bookshelf_chest_enchanting_table_contact_dy_and_barrel_raycast_triad`

Remaining special-fullblock rows are unchanged and honest: `minecraft:lectern` remains the interactive block-entity contact slice; `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain special-shape category slices.
