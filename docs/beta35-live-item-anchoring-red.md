# Beta 3.5 Floor Torch Player Placement Proof

## Status

**Floor torch player-like placement is GREEN, but live visual anchoring is NOT ACCEPTED.**

Julia manually live-tested after `0f08624` /
`save/beta35-floor-torch-player-placement` and reported: "I'm not seeing how
this is better." Screenshots show torches still appear visually floating or not
convincingly anchored to the slab-supported surface.

**Beta 3.5 release prep remains PAUSED.** The current blocker is floor torch
visual/support contact, not placement permission. Do not claim
`floor_torch_only` release scope accepted.

## GREEN proof added after RED/proof-gap savepoint

Gated by `-Dslabbed.beta35LiveItemAnchoringRed=true` in
`SlabbedLabLoweredSidePlacementLiveReproClientGameTest`.

The proof now uses the client `interactionManager.interactBlock(...)` path with
a held `Items.TORCH` stack and a top-face hit on the slab-supported geometry.
The main placement assertion no longer uses direct `setBlockState(...)`.

Required GREEN markers now emit:

```
[JULIA_BETA35_LIVE_ITEM_ANCHORING_PLACEMENT_GREEN]
[JULIA_BETA35_LIVE_ITEM_ANCHORING_SURVIVAL_GREEN]
[JULIA_BETA35_LIVE_ITEM_ANCHORING_TRIAD_GREEN]
[JULIA_BETA35_LIVE_ITEM_ANCHORING_SUMMARY] ... categoryScope=floor_torch_only itemCategory=floor_torch ... failureLayer=NONE
```

The focused proof records:

- `placementResult=Success[...]` / `actionResult=Success[...]`.
- `supportPos=49,201,0`, `supportDy=-0.500`.
- `expectedTorchPos=49,202,0`, `finalState=Block{minecraft:torch}`.
- `canPlaceAt=true` after placement.
- `torchDy=-1.000`.
- Survival GREEN after an immediate slab neighbor-update pulse while support
  remains present.
- Model, outline, and raycast bounds co-located:
  `min=(49.375,201.000,0.375),max=(49.625,201.625,0.625)`.

This GREEN proof does **not** prove Julia's manual visual acceptance. It proves
the player item-use path can place a floor torch and that model/outline/raycast
co-locate. It does not prove that the co-located triad is sitting on the
player-expected visible support surface in Julia's live structure.

Category scope remains explicit:

- `floor_torch`: GREEN.
- `wall_torch`: NOT_COVERED.
- `lantern`: NOT_COVERED.
- `signs`: NOT_COVERED.
- `chains`: NOT_COVERED.

## Visual contact audit after Julia manual rejection

Gated by `-Dslabbed.beta35FloorTorchVisualContactRed=true` in
`SlabbedLabLoweredSidePlacementLiveReproClientGameTest`.

The audit measures visible support contact instead of only triad co-location:

```
[JULIA_BETA35_FLOOR_TORCH_CONTACT_GAP_MEASURED]
[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_PENDING]
[JULIA_BETA35_FLOOR_TORCH_VISUAL_SUMMARY]
```

Current controlled-fixture measurement:

- `supportPos=49,201,0`
- `supportState=stone_slab[type=bottom]`
- `supportDy=-0.500`
- `supportVisibleTopY=201.000000`
- `torchPos=49,202,0`
- `torchDy=-1.000`
- `torchModelBottomY=201.000000`
- `torchModelTopY=201.625000`
- `contactGap=0.000000`
- `contactGapAcceptable=true`
- `triad=GREEN`
- `visualContactProofStatus=PENDING`
- `failureLayer=FIXTURE_MISMATCH`

Classification: the controlled fixture reports zero model/support contact gap,
so this slice does not prove a tiny safe gameplay fix. Julia's live visual
verdict remains NOT ACCEPTED because the screenshot/live structure has not been
proven equivalent to the controlled fixture.

New follow-up RED proof status:

`-Dslabbed.beta35LiveFloorTorchContactGapRed=true`

Records:

- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]`

It classifies wall_torch/lantern/signs/chains as `NOT_COVERED`; no production
gameplay fix is implemented in this new slice.

## Live-shape fixture parity proof

Gated by `-Dslabbed.beta35FloorTorchLiveShapeRed=true` in
`SlabbedLabLoweredSidePlacementLiveReproClientGameTest`.

This proof seeds a multi-level screenshot-style slab/full geometry and uses
player-like `interactionManager.interactBlock(...)` with held `Items.TORCH` for
the measured placement.

Current measured parity result:

- `expectedTorchPos=67,202,0`
- `actualTorchPos=67,202,0`
- `supportPos=67,201,0`
- `supportState=stone_slab[type=bottom]`
- `supportDy=-0.500`
- `supportVisibleTopY=201.000000`
- `torchDy=-1.000`
- `torchModelBottomY=201.000000`
- `contactGap=0.000000`
- `triad=GREEN`
- `liveShapeProofStatus=GREEN`
- `failureLayer=NONE`

Follow-up live evidence after `3212d88` isolated the remaining failure to
`floor_torch` dy/contact over lowered bottom-slab support (`supportDy=-1.000000`),
not placement permission or survival.

The fix at `docs/beta35-floor-torch-lowered-slab-contact-fix.md` keeps this
player-like placement proof GREEN while adding lowered bottom-slab contact
coverage to the live-shape proof. Retested lowered bottom-slab cases now report
`torchDy=-1.500`, `contactGap=0.000000`, `triadCoLocated=true`,
`survival=SURVIVAL_GREEN`, and `failureLayer=NONE`.

## Live failure

Julia's manual live test (MC 1.21.11) at HEAD `4f63abe` /
`save/beta35-object-triad-inclusion-strategy` showed that the prior proof did
not cover player-facing placement onto slab-supported geometry.

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

## Historical RED proof added at 4f63abe

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
[JULIA_BETA35_LIVE_ITEM_ANCHORING_SUMMARY] proofScope=CONTROLLED_FIXTURE_PLACEMENT_AND_SURVIVAL screenshotFaithfulPlacement=NOT_PROVEN itemCategory=floor_torch_only itemCategoriesNotCovered=wall_torch,lantern,signs,chains playerItemUsePathNotCovered=true fixtureCanPlace=true fixturePlaced=true fixtureAnchorDyOk=true fixtureSurvived=true torchDy=-1.000 slabDy=-0.500 juliaLiveResult=RED failureLayer=PROOF_GAP beta35ReleaseStatus=BLOCKED_LIVE_ITEM_ANCHORING_UNPROVEN priorTriadProofStatus=INCLUDE_READY_TRIAD_ONLY_NOT_PLACEMENT_PROVEN
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
- **ANCHOR_DY**: the player-placed floor torch may not inherit the correct dy
  from the slab's visual surface in the tested geometry.
- **PROOF_GAP**: the prior triad proof was overclaimed as placement-proven when it
  only proved targeting.

## Compile and test results

- `compileJava` / `compileGametestJava`: **BUILD SUCCESSFUL**
- Focused floor torch proof (`-Dslabbed.beta35LiveItemAnchoringRed=true`): **BUILD SUCCESSFUL**
- Object-triad regression proof (`-Dslabbed.beta35ObjectSlabOwnershipRed=true`): **BUILD SUCCESSFUL**
- Default `runClientGameTest`: **BUILD SUCCESSFUL**
- `git diff --check`: **PASS**

Evidence: `tmp/beta35-floor-torch-player-placement-green-97ff495/`

## Release status

Beta 3.5 release prep is **PAUSED** due to Julia's manual visual anchoring
rejection:

1. Floor torch placement permission is GREEN.
2. Floor torch visual/support acceptance is the active blocker.
3. If Julia expands scope later, separate wall torch, lantern, sign, or chain
   proofs must be added in later slices.
4. A final release audit only after Julia authorizes release prep.

The object-triad fix remains **triad-include-ready** (`beta35IncludeStatus=INCLUDE`
on the `TRIAD_SUMMARY` marker). Do not confuse triad-include-ready with
placement-proven, visual-accepted, or release-ready.

No release tag was moved.

## See also

- `docs/beta35-inclusion-strategy.md` — updated to reflect PAUSED release status
- `docs/beta35-object-triad-dryrun.md` — updated to reflect live anchoring blocker
- `SLABBED_SPINE.md` — Beta 3.5 salvage audit status section

## Julia live dual failure after b149996

After `b149996` / `save/beta35-floor-torch-lowered-slab-contact`, Julia's live
video proves the previous player-like and contact proofs are still incomplete:

1. Floor torches sometimes place but float.
2. Floor torches sometimes cannot be placed at all on the intended support.

Do not collapse these into one bug. Placement/targeting/intent remains separate
from visual contact/source-truth/dy.

The new manual-live tracer is gated by
`-Dslabbed.beta35LiveTorchDualTrace=true`. It emits:

- `[JULIA_BETA35_LIVE_TORCH_DUAL_TRACE] enabled=true`
- `[JULIA_BETA35_LIVE_TORCH_PLACEMENT_ATTEMPT]`
- `[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT]`
- `[JULIA_BETA35_LIVE_TORCH_DUAL_SUMMARY]`

This tracer is proof-only/debug-gated and does not implement a production
behavior fix. Beta 3.5 release prep remains paused pending Julia live trace.
Scope remains `floor_torch_only`; wall torch, lantern, signs, and chains remain
`NOT_COVERED`; no release tag moved.
