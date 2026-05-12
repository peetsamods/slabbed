# Beta 3.5 Bookshelf Contact Fix

## Scope

Implementation/proof slice for exact `minecraft:bookshelf` contact/dy behavior only.

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

Operating base: `e0da848` / `save/beta35-special-fullblock-compatibility-audit`

Evidence folder: `tmp/beta35-bookshelf-contact-fix-e0da848`

No release audit was run. No release tag was moved. Canonical checkout was not modified.

## Result

Previous failure layer: `CONTACT_GAP`.

New failure layer: `NONE`.

`minecraft:bookshelf` places, survives, and reports `contactGap=0.000000` on valid slab-supported rows. Model proxy, outline, raycast, and collision bounds are co-located for the focused rows.

Focused marker:

`JULIA_BETA35_BOOKSHELF_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:bookshelf rows=3 expectedRowsGreen=true`

## Mechanism

`SlabSupport.getYOffsetInner(...)` now lets exact `Blocks.BOOKSHELF` use the existing Beta 3.5 ordinary-full-block contact dy helper over valid lowered bottom slab support truth.

This is not all full blocks, not all special fullblocks, not block-entity support, not a renderer rewrite, and not a global slab solidity or sturdy-face change.

## Matrix Status

Special-fullblock matrix summary after the fix:

`rows=30 greenAlreadyInherits=9 placementFailure=0 survivalFailure=0 contactGap=12 triadMismatch=2 blockEntityRisk=2 specialRendererRisk=2 needsCategorySlice=3 outOfScopeForBeta35=0`

Controls `minecraft:crafting_table` and `minecraft:furnace` remain GREEN.

Remaining rows are unchanged and honest:

- `minecraft:enchanting_table`: contact gap / special renderer category.
- `minecraft:lectern`: contact gap / interactive block-entity category.
- `minecraft:chest`: contact gap / special renderer category.
- `minecraft:barrel`: triad mismatch / interactive block-entity category.
- `minecraft:stonecutter`, `minecraft:grindstone`, `minecraft:anvil`: contact gap / special-shape category.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35BookshelfContact=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `git diff --check`: PASS

Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched.
