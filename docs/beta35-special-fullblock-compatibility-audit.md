# Beta 3.5 Special Fullblock Compatibility Audit

## Scope

Audit/proof/docs-only matrix for special/full-block-ish Beta 3.5 representatives.

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

Operating base: `d6c10d8` / `save/beta35-oak-trapdoor-contact`

Gate: `-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true`

Evidence folder: `tmp/beta35-special-fullblock-compat-audit-d6c10d8`

No production behavior fix was implemented. No release audit was run. No release tag was moved. Canonical checkout was not modified.

Only `SLABBED_SPINE.md` is tracked in this checkout among the expected numbered source-pack docs; `00_SLABBED_SOURCE_INDEX.md`, `01_SLABBED_CANONICAL_DOCTRINE.md`, and `02_SLABBED_ACTIVE_STATUS.md` are absent from repo root.

## Current Green Set Preserved

- `minecraft:torch`
- `minecraft:candle`
- `minecraft:flower_pot`
- `minecraft:crafting_table`
- `minecraft:furnace`
- `minecraft:oak_fence`
- `minecraft:oak_trapdoor`
- `minecraft:bookshelf`
- `minecraft:chest`
- `minecraft:barrel`

The special-fullblock audit did not inspect door worktree changes and did not touch door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35BookshelfContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35ChestContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35BarrelTriad=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS

## Matrix Summary

Summary marker:

`JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY rows=30 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=6 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=2 outOfScopeForBeta35=0 currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,bookshelf,chest,barrel,enchanting_table,stonecutter doorSlice=PARALLEL_NOT_INSPECTED releaseAudit=NOT_RUN releasePrep=PAUSED productionBehaviorChanged=bookshelf_chest_enchanting_table_stonecutter_contact_dy_and_barrel_raycast_triad`

| Representative | Family | Vanilla full block | Plain bottom slab `supportDy=-0.5` | Lowered bottom slab `supportDy=-1.0` | Classification |
| --- | --- | --- | --- | --- | --- |
| `minecraft:bookshelf` | `ordinary_full_block` | GREEN | GREEN, `contactGap=0.000000`, triad yes | GREEN, `contactGap=0.000000`, triad yes | `GREEN_ALREADY_INHERITS` fixed representative |
| `minecraft:enchanting_table` | `special_renderer` | GREEN, `blockEntityPresent=true` | GREEN, `contactGap=0.000000`, triad yes, `blockEntityPresent=true` | GREEN, `contactGap=0.000000`, triad yes, `blockEntityPresent=true` | `GREEN_ALREADY_INHERITS` fixed representative |
| `minecraft:lectern` | `interactive_block_entity` | `BLOCK_ENTITY_RISK` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | needs interactive block-entity slice |
| `minecraft:barrel` | `interactive_block_entity` | GREEN, `blockEntityPresent=true` | GREEN, `contactGap=0.000000`, triad yes, `blockEntityPresent=true` | GREEN, `contactGap=0.000000`, triad yes, `blockEntityPresent=true` | `GREEN_ALREADY_INHERITS` fixed representative |
| `minecraft:chest` | `special_renderer` | GREEN, `blockEntityPresent=true` | GREEN, `contactGap=0.000000`, triad yes, `blockEntityPresent=true` | GREEN, `contactGap=0.000000`, triad yes, `blockEntityPresent=true` | `GREEN_ALREADY_INHERITS` fixed representative |
| `minecraft:crafting_table` | `ordinary_full_block` | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` control |
| `minecraft:furnace` | `ordinary_full_block` | GREEN | GREEN, `contactGap=0.000000`, triad yes | GREEN, `contactGap=0.000000`, triad yes | `GREEN_ALREADY_INHERITS` control |
| `minecraft:stonecutter` | `special_shape_fullblock` | GREEN | GREEN, `contactGap=0.000000`, triad yes | GREEN, `contactGap=0.000000`, triad yes | `GREEN_ALREADY_INHERITS` fixed representative |
| `minecraft:grindstone` | `special_shape_fullblock` | `NEEDS_CATEGORY_SLICE` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | special-shape slice |
| `minecraft:anvil` | `special_shape_fullblock` | `NEEDS_CATEGORY_SLICE` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | special-shape slice |

All representatives placed and survived on the audited rows. No row produced `PLACEMENT_FAILURE` or `SURVIVAL_FAILURE`.

## Findings

Objects already inheriting the current `crafting_table` / `furnace` behavior:

- `minecraft:bookshelf`
- `minecraft:enchanting_table`
- `minecraft:chest`
- `minecraft:barrel`
- `minecraft:stonecutter`
- `minecraft:crafting_table`
- `minecraft:furnace`

Objects that fail contact on slab-supported rows:

- `minecraft:lectern`
- `minecraft:grindstone`
- `minecraft:anvil`

Objects that fail triad after contact is otherwise acceptable:

- none in this focused special-fullblock matrix after the barrel triad slice

Block-entity / special-renderer risks:

- `minecraft:lectern`

Separate category slices:

- `minecraft:grindstone` and `minecraft:anvil` are special-shape fullblock-ish rows.
- `minecraft:lectern` is interactive block-entity work and should not be bundled with ordinary full-block contact.

## Release Decision

This audit does not change the release decision. It expands the known matrix from the clean Beta 3.5 base and keeps release prep paused.

`minecraft:bookshelf`, `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, and `minecraft:stonecutter` are now GREEN for their focused representatives. Chest, enchanting table, and stonecutter use exact block dy authority plus exact lowered raycast fallbacks to their lowered outlines when native raycast is empty; barrel keeps its existing contact dy and uses a barrel-only lowered raycast fallback to its lowered outline. Do not claim all special fullblocks or all block entities are fixed; remaining rows are still separate interactive block-entity or special-shape slices.
