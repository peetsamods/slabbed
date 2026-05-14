# Beta 3.5 Slab Placement Lane Jump Classification

Slice base: `23b562c` / `save/beta35-trapdoor-server-validation`.

Evidence folder: `tmp/beta35-door-owner-slab-jump-fix-23b562c/`.

## Why

Julia's 2026-05-14 6:30 live source truth still showed visible lane jumps during slab placement/break sequences around the slab/fence/wall/door structure. Existing `SBSB-TRACE` rows showed lowered visible non-slab sources with `dyHit=-1.000000` producing a normal-lane placed bottom slab with `dyPlace=0.000000`.

The question for this slice was whether that transition is a concrete wrong state change that can be narrowly fixed, or whether current placement grammar lacks a named legal lowered destination state.

## Proof

Focused proof flag:

`-Dslabbed.beta35SlabPlacementLaneJump=true -Dslabbed.beta35SlabHeightHitAcceptance=true`

Live-source truth marker:

`JULIA_BETA35_SLAB_PLACEMENT_LANE_JUMP_RED rowPhase=BEFORE_LIVE_SOURCE_TRUTH heldItem=minecraft:stone_slab hitState=Block{minecraft:nether_brick_wall} dyHit=-1.000000 placeState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false] dyPlace=0.000000 classification=SLAB_PLACEMENT_LANE_JUMP failureLayer=SLAB_PLACEMENT_LANE_JUMP_UNPROVEN`

Automated rows:

- `nether_brick_wall` lowered source: `dyHit=-1.000000`, but the fixture produced no placement, so it is captured as `LOWERED_SOURCE_NO_PLACEMENT_IN_FIXTURE`.
- `birch_fence` lowered source: reproduced the wrong transition, `dyHit=-1.000000` to `placeState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false] dyPlace=0.000000`, classified `SLAB_PLACEMENT_LANE_JUMP`.
- Legal bottom-slab source: `dyHit=-0.500000` placed at `dyPlace=-0.500000`, classified `EXPECTED_SLAB_PLACEMENT`.
- Legal double-slab source: `dyHit=-0.500000` placed top slab at `dyPlace=-0.500000`, classified `EXPECTED_SLAB_PLACEMENT`.

Green classification summary:

`JULIA_BETA35_SLAB_PLACEMENT_LANE_JUMP_SUMMARY outcome=GREEN rows=4 loweredSourceRows=2 slabJumpRowsBefore=1 slabJumpRowsAfter=1 expectedSlabPlacementRows=2 neighborDyRenormalizationRows=0 illegalDy0FromLoweredSourceRows=1 legalDestinationState=NONE productionFixImplemented=false classification=SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE failureLayer=NONE releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false`

## Result

The proof reproduced one concrete wrong placement transition from a lowered non-slab source, but did not patch production behavior because the current grammar has no named legal lowered slab destination for that source. The honest classification is `SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE`, with `productionFixImplemented=false`.

No neighbor dy renormalization was observed in the automated proof (`neighborDyRenormalizationRows=0`). Existing legal slab-source placement remained expected.

## Scope

No broad `dy=-1` lane was added. No global hit tolerance, collision, solidity, or sturdy-face behavior changed. No all-item gameplay claim was made. Julia live acceptance remains required.

## 2026-05-14 Continuation: Door Half Server Validation

Continuation base: `e23c62a` / `save/beta35-door-owner-slab-jump`.

This continuation fixed regular `DoorBlock` shifted server validation for upper and lower halves. It did not change the slab placement grammar.

Current slab status remains:

`SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE`

The door-half proof summary carries the same status:

`slabJumpStatus=SLAB_PLACEMENT_LANE_JUMP_DEFERRED_NO_NAMED_LEGAL_LANE`
