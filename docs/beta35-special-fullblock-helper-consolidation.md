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

Contact dy and raycast fallback remain separate concerns. At consolidation time, the contact helper covered only already-green representatives: `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, and `minecraft:stonecutter`.

The raycast fallback helper covered only already-proven empty-native-raycast representatives at consolidation time: `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, and `minecraft:stonecutter`.

Follow-up: `minecraft:anvil` joined both explicit helper allowlists only after the focused anvil proof at `9f3bacf` showed the same narrow contact-dy plus empty-raycast mechanism on valid slab-supported rows.

Follow-up: `minecraft:grindstone` joined both explicit helper allowlists only after the focused grindstone proof at `805b070` showed the same narrow contact-dy plus empty-raycast mechanism on valid slab-supported rows. A separate grindstone-only collision fallback was added to keep the tested floor-oriented special shape co-located; this is not broad collision support.

## Boundaries

New object support after the grindstone follow-up is limited to exact `minecraft:grindstone`.

The green set now adds `minecraft:grindstone`.

Still open:

- `minecraft:lectern`: interactive block-entity contact slice

Forbidden categories were not touched: doors, trapdoors, signs, lanterns, chains, end rods, redstone, and rails.

No release audit was run. No release tag was moved. Canonical checkout was not modified.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `git diff --check`: PASS

Special-fullblock matrix summary after grindstone stayed PASS: `rows=30 greenAlreadyInherits=27 placementFailure=0 survivalFailure=0 contactGap=2 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=0 outOfScopeForBeta35=0`.

Common-object matrix summary stayed unchanged: `rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0`.

## Next Slice

Recommended next implementation slice after the grindstone follow-up: `minecraft:lectern` only if Julia chooses the interactive block-entity slice.

Do not bundle other special fullblocks with lectern.
