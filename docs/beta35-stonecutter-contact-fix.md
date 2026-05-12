# Beta 3.5 Stonecutter Contact Fix

Date: 2026-05-12

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

Base: `99b03ed` / `save/beta35-enchanting-table-contact`

Evidence: `tmp/beta35-stonecutter-contact-fix-99b03ed`

## Scope

This slice changed only `minecraft:stonecutter` contact/triad behavior on valid slab-supported surfaces. It did not implement lectern, grindstone, anvil, doors, trapdoors, signs, lanterns, chains, end rods, redstone, or rails. The canonical checkout was not modified. No release audit was run and no release tag was moved.

## Mechanism

Previous failure layer: `CONTACT_GAP`.

The historical matrix showed `minecraft:stonecutter` already placed and survived. Vanilla full-block support was coherent, but slab-supported rows used too-shallow object dy:

- Plain bottom support: `supportDy=-0.500000`, old `objectDy=-0.500000`, old `contactGap=0.500000`.
- Lowered bottom support: `supportDy=-1.000000`, old `objectDy=-0.500000`, old `contactGap=1.000000`.

The stonecutter model, outline, and collision proxy moved together, but native raycast was empty on the audited lowered rows. The fix is exact `Blocks.STONECUTTER` contact dy in `SlabSupport` plus a stonecutter-only lowered raycast fallback in `SlabSupportStateMixin` that uses the lowered outline when native raycast is empty.

This is not a broad special-fullblock rule, not a stone-like utility block rule, and not a global sturdy-face or solidity change.

## Proof

Focused gate: `-Dslabbed.beta35StonecutterContact=true`.

Focused proof summary:

`JULIA_BETA35_STONECUTTER_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:stonecutter rows=3 expectedRowsGreen=true`

Focused slab-supported rows:

- Plain bottom support: `supportDy=-0.500000`, `objectDy=-1.000000`, `supportVisibleTopY=-55.000000`, `objectModelBottomY=-55.000000`, `contactGap=0.000000`, `triadCoLocated=yes`, `survivalResult=SURVIVAL_GREEN`.
- Lowered bottom support: `supportDy=-1.000000`, `objectDy=-1.500000`, `supportVisibleTopY=-55.500000`, `objectModelBottomY=-55.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, `survivalResult=SURVIVAL_GREEN`.

Focused marker:

`JULIA_BETA35_STONECUTTER_CONTACT_GREEN`

Updated special-fullblock matrix:

`JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY rows=30 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=6 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=2 outOfScopeForBeta35=0 currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,bookshelf,chest,barrel,enchanting_table,stonecutter doorSlice=PARALLEL_NOT_INSPECTED releaseAudit=NOT_RUN releasePrep=PAUSED productionBehaviorChanged=bookshelf_chest_enchanting_table_stonecutter_contact_dy_and_barrel_raycast_triad`

Controls and already fixed representatives remain GREEN: `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, `minecraft:chest`, `minecraft:barrel`, and `minecraft:enchanting_table`.

Remaining rows are unchanged and honest: `minecraft:lectern` remains an interactive block-entity contact slice; `minecraft:grindstone` and `minecraft:anvil` remain special-shape category slices.
