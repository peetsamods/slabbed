# MC1211 Side-Placed Stone Lowering Proof Report

## Verdict

Classification: `TRACE_GAP_NOT_VIDEO_EQUIVALENT`

The focused route reproduced the client-side side-placement relation up through the live-style `SBSB-TRACE` head and client return:

- hit block: `Block{minecraft:stone}`
- hit face: `east`
- hit dy: `-0.5`
- place pos: same Y, east-adjacent air
- item: `minecraft:stone`
- client placement result: `SUCCESS`

It did not reproduce the live final condition. At the row sample, the place position had returned to `Block{minecraft:air}` with `postPlaceDy=0.000000`, so the proof cannot classify the live bug as legal or illegal yet.

## Required Questions

1. Did the proof reproduce the live trace relation?
   - Partially. It reproduced the client `[SBSB-TRACE][HEAD]` and `[SBSB-TRACE][RETURN]` relation, including `dyHit=-0.5`, `face=east`, same-Y adjacent `placePos`, and client `SUCCESS`.
   - It did not reproduce the live server-retained placed stone or final `postPlaceDy=-0.5`.

2. Is the side-placed stone lowering legal under an existing named Slabbed state?
   - Unproven in this automated route. The sampled final block was air, so no placed stone lowering was available to classify.

3. If legal, what is the exact legal state name and source relationship?
   - Not proven. The seeded source relationship was `DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK_SOURCE_TO_SIDE_ADJACENT_FULL_BLOCK`, which is the candidate route for `BSFB_ADJACENT_FULLBLOCK_INHERITANCE`, but the placed block did not persist in this proof.

4. If illegal, what code path authored or allowed it?
   - Not proven. Grep/localization points to `BlockItemPlacementIntentMixin` finalization calling `SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(...)`, whose qualifier accepts same-Y side-adjacent ordinary full blocks when the clicked source is a full-height lowered carrier.

5. Does this explain Julia's "stone model merged into slab lane" video?
   - Not by this proof alone. The live trace still explains that symptom if the placed stone is actually retained with client/world dy `-0.5`; this route did not reproduce the retained placed stone.

6. Did model/outline trace now agree because the state itself was lowered?
   - The latest live trace says yes: model, outline, and client world dy all agreed at `-0.5`. This proof route did not run model/outline sampling and did not reproduce the final lowered placed state.

7. Which file/layer is the smallest implementation target, if RED?
   - No RED implementation target is authorized. If a later proof reproduces `postPlaceDy=-0.5` without a legal source relationship, the smallest likely target is the placement/state authority seam in `BlockItemPlacementIntentMixin` / `SlabAnchorAttachment`, not model/render.

8. What remains unproven?
   - Server-retained same-Y east side placement of `minecraft:stone`.
   - Server placement result (`CONSUME`) for this route.
   - Whether the retained placed stone becomes `postPlaceDy=-0.5`.
   - Whether that lowering is legal `BSFB_ADJACENT_FULLBLOCK_INHERITANCE` or illegal unnamed side lowering.

## Evidence

Focused route:

- `MC1211_SIDE_PLACE_STONE_LOWERING_ROUTE_CANARY`
- `MC1211_SIDE_PLACE_STONE_LOWERING_START`
- `MC1211_SIDE_PLACE_STONE_LOWERING_ROW`
- `MC1211_SIDE_PLACE_STONE_LOWERING_SUMMARY`
- `MC1211_SIDE_PLACE_STONE_LOWERING_TRACE_GAP`

Row summary:

- `hitPos=10,-59,-1`
- `hitState=Block{minecraft:stone}`
- `hitFace=east`
- `hitDy=-0.500000`
- `hitAnchored=true`
- `hitFullHeightLoweredCarrier=true`
- `hitHasBottomSlabBelow=true`
- `placePos=11,-59,-1`
- `prePlaceState=Block{minecraft:air}`
- `placementResultClient=SUCCESS`
- `placementResultServer=notCaptured`
- `postPlaceState=Block{minecraft:air}`
- `postPlaceDy=0.000000`
- `classification=TRACE_GAP_NOT_VIDEO_EQUIVALENT`

Commands:

- `./gradlew --no-daemon compileJava compileClientJava compileGametestJava --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.mc1211.sidePlaceStoneLoweringOnly=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.mc1211.goblinOnly=true" ./gradlew --no-daemon runClientGameTest --console plain`

Results:

- Compile: `BUILD SUCCESSFUL`
- Focused route: `BUILD SUCCESSFUL`, final marker `MC1211_SIDE_PLACE_STONE_LOWERING_TRACE_GAP`
- Goblin route: `MC1211_GOBLIN_GREEN` emitted; `BUILD SUCCESSFUL`; known shutdown `ConcurrentModificationException` caveat still present

## Next Smallest Slice

Improve the proof harness so server and client placement use the same held-stack/game-mode state as the live trace and capture the server-side placement return. Stop again unless the row retains `Block{minecraft:stone}` at `placePos` and can classify the final dy/source relationship.
