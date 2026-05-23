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

Superseded by Julia's latest live outline/target/model trace at `f98a9d9`: the
prior rejection was too broad for the actual release shape.

Direct slab→stone anchoring is healthy. The remaining player-facing failure is
the next same-Y side placement against that anchored lowered stone: the placed
stone stayed vanilla `dy=0.0` after the b7576fb-side law change, so it no longer
sat in the coherent slab-supported row.

## Law Decision

Same-Y horizontal placement of an ordinary full block against a valid
anchored/lowered ordinary full-block source must inherit `dy=-0.5` when that
source is part of the coherent slab-supported row Julia is building.

This restores/narrows ordinary side-adjacent inheritance for the proven live
shape. It does not authorize model/render/retarget fixes, compound `dy=-1.0`
changes, arbitrary side lowering, or unnamed hybrid lanes.

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

## Accepted Shape

Exact accepted row:

`SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE`

Shape fields:

- `hitState=Block{minecraft:stone}`
- `hitDy=-0.500000`
- `hitFace=east` or equivalent horizontal face
- `placePos` is same-Y and side-adjacent to the hit block
- placed item is `minecraft:stone` or another ordinary full block
- expected `postPlaceDy=-0.500000`
- legal label is `LEGAL_BSFB_ADJACENT_FULLBLOCK_INHERITANCE`
- Julia-visible result continues the coherent slab-supported row

Rejected current-bug result:

- `postPlaceDy=0.0`
- `postPlaceAnchored=false`
- classification formerly treated as `LEGAL_VANILLA_SIDE_PLACEMENT`
- visible result remains vanilla height beside the lowered source

## Next Implementation Slice

- Correct the focused side-place proof so vanilla-height side placement is RED.
- Narrow the side-adjacent anchor qualifier so ordinary lowered full-block sources
  at `dy=-0.5` can qualify through existing source predicates.
- Do not touch model, render, retarget, outline, or `ClientDy`.

## Stop Conditions

- any patch that breaks bottom-slab full-block anchoring
- any patch that deletes all side-adjacent lowering without proof
- any patch that changes model, outline, or raycast instead of authoring law
- any unnamed hybrid state
