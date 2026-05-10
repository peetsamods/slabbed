# Beta 3.5 Live Item Anchoring RED Proof

## Status

**Beta 3.5 release prep is PAUSED.**

## Live failure

Julia's manual live test (MC 1.21.11) at HEAD `4f63abe` /
`save/beta35-object-triad-inclusion-strategy` shows torches and items
floating or failing to anchor on slab-supported geometry in player-facing use.

Julia's report: "Wait we didn't fix the items anchoring to slabs."

Screenshot: provided in chat; local file not available to agent.

## What the prior proof proved

The Beta 3.5 object-triad proof (`-Dslabbed.beta35ObjectSlabOwnershipRed=true`,
`save/beta35-object-triad-classification`) proved:

- Owner-route targeting: torch/slab targeting resolves to correct owner.
- Model/outline/raycast co-location: all three agree for a pre-placed torch.
- Static `canPlaceAt` call on an already-placed torch.
- `failureLayer=NONE`, `beta35IncludeStatus=INCLUDE`.

The proof scope was `OWNER_ROUTE_ONLY_SIMPLE_ROUTING`. The fixture placed the
torch directly via `world.setBlockState(...)`, bypassing player item-use context.
`screenshotFaithfulTriad=NOT_PROVEN` in the fixture line.

## What the prior proof did NOT prove

- Player-initiated placement of a torch onto a slab-supported face.
- Correct anchor dy at the moment of player placement.
- Survival through real neighbor-update pulses triggered by player actions.
- Item category coverage beyond floor torch (wall_torch, lantern, sign, etc.).
- Player crosshair targeting for placement intent (only pre-placed targeting was
  tested).

## RED proof added at 4f63abe

Gated by `-Dslabbed.beta35LiveItemAnchoringRed=true` in
`SlabbedLabLoweredSidePlacementLiveReproClientGameTest`.

Fixture geometry (matches existing triad proof, torch omitted):

| Position  | Block                   | dy     |
|-----------|-------------------------|--------|
| 48,200,0  | stone_slab[type=bottom] | 0.000  |
| 48,201,0  | stone (full block)      | -0.500 |
| 49,201,0  | stone_slab[type=bottom] | -0.500 |
| 49,202,0  | AIR (torch target)      | —      |

### Focused proof markers (HEAD 4f63abe)

```
[JULIA_BETA35_LIVE_ITEM_ANCHORING_FIXTURE_GREEN] supportPos=48,200,0 slabPos=49,201,0 torchPos=49,202,0 slabState=stone_slab[type=bottom] slabDy=-0.500 slabLowered=true torchPosIsAir=true canPlaceAt=true proofScope=PLACEMENT_AND_SURVIVAL_NOT_PLAYER_ITEM_USE_CONTEXT
[JULIA_BETA35_LIVE_ITEM_ANCHORING_PLACEMENT_GREEN] canPlaceAt=true placementAccepted=true torchPresent=true torchDy=-1.000 expectedTorchDy=-1.000 anchorDyCorrect=true failureLayer=NONE
[JULIA_BETA35_LIVE_ITEM_ANCHORING_SURVIVAL_GREEN] torchPresent=true afterNeighborUpdateFromSlab=true slabStillPresent=true failureLayer=NONE
[JULIA_BETA35_LIVE_ITEM_ANCHORING_SUMMARY] proofScope=CONTROLLED_FIXTURE_PLACEMENT_AND_SURVIVAL screenshotFaithfulPlacement=NOT_PROVEN itemCategory=floor_torch_only itemCategoriesNotCovered=wall_torch,lantern,sign,all_attachable_objects playerItemUsePathNotCovered=true fixtureCanPlace=true fixturePlaced=true fixtureAnchorDyOk=true fixtureSurvived=true torchDy=-1.000 slabDy=-0.500 juliaLiveResult=RED failureLayer=PROOF_GAP beta35ReleaseStatus=BLOCKED_LIVE_ITEM_ANCHORING_UNPROVEN priorTriadProofStatus=INCLUDE_READY_TRIAD_ONLY_NOT_PLACEMENT_PROVEN
[JULIA_BETA35_LIVE_ITEM_ANCHORING_RED] fixtureResult=FIXTURE_PASS_LIVE_FAIL failureLayer=PROOF_GAP juliaReport=torches_and_items_floating_or_not_anchoring_on_slab_supported_geometry screenshotEvidence=provided_in_chat_local_file_not_available_to_agent classification=PENDING_RELEASE_BLOCKING nextAction=implement_player_facing_placement_fix_after_RED_proof_classification
```

### Classification

- `failureLayer=PROOF_GAP`: the controlled fixture (block-level setBlockState path)
  passes, but the player item-use / packet / server-tolerance path is not covered.
- The live failure is real (Julia screenshot, MC 1.21.11).
- The fixture's canPlaceAt=true and torchDy=-1.000 confirm the basic predicate and
  dy logic are present, but do not prove they are exercised in the player placement
  flow.

### Likely failure sub-layers

One or more of these must be true to produce the live failure:

- **PLACEMENT** (player path): vanilla `BlockItem.place` or
  `ServerPlayerInteractionManager.interactBlock` does not route through the slab
  support predicate correctly for all slab configurations Julia is using.
- **CATEGORY_SCOPE**: wall_torch, lantern, signs, and other attachable objects may
  lack equivalent `canPlaceAt` / `getStateForNeighborUpdate` coverage.
- **ANCHOR_DY**: player-placed items may not inherit the correct dy from the slab's
  visual surface in all geometry configurations.
- **PROOF_GAP**: the prior triad proof was overclaimed as placement-proven when it
  only proved targeting.

## Compile and test results (4f63abe)

- `compileJava` / `compileGametestJava`: **BUILD SUCCESSFUL**
- Focused proof (`-Dslabbed.beta35LiveItemAnchoringRed=true`): **BUILD SUCCESSFUL in 29s**
- Default `runClientGameTest`: **BUILD SUCCESSFUL in 1m 43s**

Evidence: `tmp/beta35-live-item-anchoring-red-4f63abe/`

## Release status

Beta 3.5 release prep is **PAUSED** pending:

1. Implementation of the player-facing item placement/anchoring fix.
2. A GREEN focused proof under `-Dslabbed.beta35LiveItemAnchoringRed=true`.
3. Julia live retest confirming items anchor correctly on slab-supported geometry.

The object-triad fix remains **triad-include-ready** (`beta35IncludeStatus=INCLUDE`
on the `TRIAD_SUMMARY` marker). Do not confuse triad-include-ready with
placement-proven or release-ready.

No gameplay fix was implemented in this slice. No release tag was moved.

## See also

- `docs/beta35-inclusion-strategy.md` — updated to reflect PAUSED release status
- `docs/beta35-object-triad-dryrun.md` — updated to reflect live anchoring blocker
- `SLABBED_SPINE.md` — Beta 3.5 salvage audit status section
