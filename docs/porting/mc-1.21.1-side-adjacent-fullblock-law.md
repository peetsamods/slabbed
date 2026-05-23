# MC1211 Side-Adjacent Full-Block Lowered Inheritance Law Decision

## Evidence

- Julia's live observation: side-placing `minecraft:stone` against a lowered
  `minecraft:stone` makes the newly placed stone inherit the lowered lane and
  visually read as merged into the slab lane.
- Restored programmatic client-world harness proof at commit `8c2839a`, tag
  `save/mc1211-programmatic-client-world-harness`, reproduces the route without
  manual world creation or UI automation.
- Focused trace row:
  - `rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE`
  - `fixtureOrigin=-32,69,5`
  - `hitPos=-32,70,5`
  - `hitState=Block{minecraft:stone}`
  - `hitFace=east`
  - `hitDy=-0.500000`
  - `placePos=-31,70,5`
  - `item=minecraft:stone`
  - `postPlaceState=Block{minecraft:stone}`
  - `postPlaceDy=-0.500000`
  - `visualRelation=sameLaneSideAdjacent`
  - `classification=LEGAL_BSFB_ADJACENT_FULLBLOCK_INHERITANCE`
- The proof emits
  `MC1211_SIDE_PLACE_STONE_LOWERING_GREEN classification=LEGAL_BSFB_ADJACENT_FULLBLOCK_INHERITANCE`.
- The route summary records
  `modelOutlineInterpretation=state_itself_lowered_not_render_mismatch`, so this
  is not a renderer bug, hitbox bug, model/outline mismatch, or route mismatch.
- Current old-law label:
  `LEGAL_BSFB_ADJACENT_FULLBLOCK_INHERITANCE`.

## Product Verdict

Julia rejects this release behavior because it reads as a full block merging
into a slab lane. The release promise favors visible physical coherence over
preserving this broad side-adjacent inheritance.

## Law Decision

Narrow side-adjacent full-block lowered inheritance for MC1211 release. Same-Y
horizontal placement of an ordinary full block against a lowered ordinary full
block must not automatically inherit `dy=-0.5` unless a future named lane rule
proves the visual relationship is coherent.

This decision does not blindly delete the entire inheritance concept. It keeps
space for cases where the relationship is visually coherent, named, and proven.

## Protected Existing Law

Do not regress:

- ordinary full block on bottom slab support
- vertical chain/lowered stack legal states
- existing known legal lowered full-block states
- top-hit preservation
- slab lane grammar
- full-height double carrier parity
- any already proven BSFB case that is not same-Y side-adjacent ordinary
  full-block inheritance

## Rejected Shape

Exact rejected row:

`SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE`

Shape fields:

- `hitState=Block{minecraft:stone}`
- `hitDy=-0.500000`
- `hitFace=east` or equivalent horizontal face
- `placePos` is same-Y and side-adjacent to the hit block
- placed item is `minecraft:stone` or another ordinary full block
- `postPlaceDy=-0.500000`
- legal label is `LEGAL_BSFB_ADJACENT_FULLBLOCK_INHERITANCE`
- Julia-visible result appears merged into the slab lane

Expected future result:

- either `postPlaceDy=0.0` as legal vanilla side placement
- or placement reject/defer if the vanilla relation is impossible
- but not `postPlaceDy=-0.5` under broad
  `LEGAL_BSFB_ADJACENT_FULLBLOCK_INHERITANCE`

## Next Implementation Slice

- Add RED proof by changing the expected classification for the existing route:
  current old-law GREEN becomes new-law RED.
- Then patch only the narrow authoring, anchor, or lowering qualifier.
- Likely target after grep:
  `SlabAnchorAttachment`, `BlockItemPlacementIntentMixin`, or a specific BSFB
  adjacent inheritance helper.
- Do not touch model, render, or retarget.

## Stop Conditions

- any patch that breaks bottom-slab full-block anchoring
- any patch that deletes all side-adjacent lowering without proof
- any patch that changes model, outline, or raycast instead of authoring law
- any unnamed hybrid state
