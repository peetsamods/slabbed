# Beta 3.5 Special Fullblock Helper Consolidation

## Scope

Worktree: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree`

Branch: `work/beta35-special-fullblock-compat`

Operating base: `d854e2b` / `save/beta35-stonecutter-contact`

Evidence folder: `tmp/beta35-special-fullblock-helper-consolidation-d854e2b`

This is a consolidation slice, not a new compatibility slice.

## Result

Consolidation performed: yes.

Helpers extracted:

- `SlabSupport.isBeta35SpecialFullblockContactObject(...)`
- `SlabSupport.beta35SpecialFullblockContactDy(...)`
- `SlabSupportStateMixin.slabbed$isBeta35SpecialFullblockRaycastFallbackObject(...)`

Contact dy and raycast fallback remain separate concerns. The contact helper covers only already-green representatives: `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, and `minecraft:stonecutter`.

The raycast fallback helper covers only already-proven empty-native-raycast representatives: `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, and `minecraft:stonecutter`.

## Boundaries

No new object support was implemented.

The green set remains unchanged.

Still open:

- `minecraft:lectern`: interactive block-entity contact slice
- `minecraft:grindstone`: special-shape slice
- `minecraft:anvil`: special-shape/falling-ish slice

Forbidden categories were not touched: doors, trapdoors, signs, lanterns, chains, end rods, redstone, and rails.

No release audit was run. No release tag was moved. Canonical checkout was not modified.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `git diff --check`: PASS

Special-fullblock matrix summary stayed unchanged: `rows=30 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=6 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=2 outOfScopeForBeta35=0`.

Common-object matrix summary stayed unchanged: `rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0`.

## Next Slice

Recommended next implementation slice: `minecraft:grindstone` or `minecraft:anvil`.

Do not bundle either with lectern.
