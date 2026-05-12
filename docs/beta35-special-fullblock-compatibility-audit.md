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

The special-fullblock audit did not inspect door worktree changes and did not touch door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS

## Matrix Summary

Summary marker:

`JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY rows=30 greenAlreadyInherits=7 placementFailure=0 survivalFailure=0 contactGap=14 triadMismatch=2 blockEntityRisk=2 specialRendererRisk=2 needsCategorySlice=3 outOfScopeForBeta35=0 currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor doorSlice=PARALLEL_NOT_INSPECTED releaseAudit=NOT_RUN releasePrep=PAUSED productionBehaviorChanged=false`

| Representative | Family | Vanilla full block | Plain bottom slab `supportDy=-0.5` | Lowered bottom slab `supportDy=-1.0` | Classification |
| --- | --- | --- | --- | --- | --- |
| `minecraft:bookshelf` | `ordinary_full_block` | GREEN | `CONTACT_GAP=0.500000` | `CONTACT_GAP=1.000000` | ordinary full-block sibling, not green |
| `minecraft:enchanting_table` | `special_renderer` | `SPECIAL_RENDERER_RISK` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | needs renderer/block-entity category slice |
| `minecraft:lectern` | `interactive_block_entity` | `BLOCK_ENTITY_RISK` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | needs interactive block-entity slice |
| `minecraft:barrel` | `interactive_block_entity` | `BLOCK_ENTITY_RISK` | `TRIAD_MISMATCH`, `contactGap=0.000000` | `TRIAD_MISMATCH`, `contactGap=0.000000` | block-entity triad category slice |
| `minecraft:chest` | `special_renderer` | `SPECIAL_RENDERER_RISK` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | special-renderer category slice |
| `minecraft:crafting_table` | `ordinary_full_block` | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` control |
| `minecraft:furnace` | `ordinary_full_block` | GREEN | GREEN, `contactGap=0.000000`, triad yes | GREEN, `contactGap=0.000000`, triad yes | `GREEN_ALREADY_INHERITS` control |
| `minecraft:stonecutter` | `special_shape_fullblock` | `NEEDS_CATEGORY_SLICE` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | special-shape slice |
| `minecraft:grindstone` | `special_shape_fullblock` | `NEEDS_CATEGORY_SLICE` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | special-shape slice |
| `minecraft:anvil` | `special_shape_fullblock` | `NEEDS_CATEGORY_SLICE` | `CONTACT_GAP=0.500000`, triad no | `CONTACT_GAP=1.000000`, triad no | special-shape slice |

All representatives placed and survived on the audited rows. No row produced `PLACEMENT_FAILURE` or `SURVIVAL_FAILURE`.

## Findings

Objects already inheriting the current `crafting_table` / `furnace` behavior:

- `minecraft:crafting_table`
- `minecraft:furnace`

Objects that fail contact on slab-supported rows:

- `minecraft:bookshelf`
- `minecraft:enchanting_table`
- `minecraft:lectern`
- `minecraft:chest`
- `minecraft:stonecutter`
- `minecraft:grindstone`
- `minecraft:anvil`

Objects that fail triad after contact is otherwise acceptable:

- `minecraft:barrel`

Block-entity / special-renderer risks:

- `minecraft:enchanting_table`
- `minecraft:lectern`
- `minecraft:barrel`
- `minecraft:chest`

Separate category slices:

- `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` are special-shape fullblock-ish rows.
- `minecraft:barrel` is the cleanest block-entity triad follow-up, but not the safest first implementation because it is not an ordinary full-block sibling.
- `minecraft:enchanting_table` and `minecraft:chest` carry special-renderer risk and should stay deferred until a renderer-aware slice is authorized.
- `minecraft:lectern` is interactive block-entity work and should not be bundled with ordinary full-block contact.

## Release Decision

This audit does not change the release decision. It expands the known matrix from the clean Beta 3.5 base and keeps release prep paused.

Recommended next implementation slice, if Julia authorizes one: `minecraft:bookshelf` ordinary-full-block contact/dy proof and fix only. It is the closest sibling to the existing `crafting_table` / `furnace` controls and avoids block-entity, renderer, multipart, and special-shape risk.
