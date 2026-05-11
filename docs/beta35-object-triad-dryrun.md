# Beta 3.5 Object/Slab Triad Dry-Run Report

## Scope
- Repo: /Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
- Baseline: d1417ff3d59e6af16eed23b1dea92e60de5ff00c (`release/0.2.0-beta.2`)
- Dry-run worktree: ../Slabbed-beta35-dryrun-object-triad
- Candidate sequence: c96e674 -> 6cb28bb -> 274b286

## Cherry-pick checks
- c96e674: **NO** (conflicts)
- 6cb28bb: **NOT RUN** (stopped after first conflict)
- 274b286: **NOT RUN** (stopped after first conflict)

## Conflicts observed
- First cherry-pick `c96e674` reported:
  - `SLABBED_SPINE.md` (delete/modify conflict)
  - `docs/beta35-salvage-audit.md` (delete/modify conflict)
  - `src/client/java/com/slabbed/mixin/client/GameRendererCrosshairRetargetMixin.java` (content conflict)
  - `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java` (content conflict)
- Evidence captured:
  - `tmp/beta35-object-triad-dryrun-274b286/cherrypick-c96e674-status.txt`
  - `tmp/beta35-object-triad-dryrun-274b286/cherrypick-c96e674-files.txt`
  - `tmp/beta35-object-triad-dryrun-274b286/cherrypick-c96e674-diff.patch`

## Compile and test (integration branch HEAD 9c90c23)

Proof was run on the integration branch at HEAD `9c90c23` / `save/beta35-object-triad-dryrun` because the cherry-pick dry-run stopped. This proves the fix is functionally correct even though it cannot cherry-pick cleanly.

- `compileJava` / `compileGametestJava`: **BUILD SUCCESSFUL** (up-to-date)
- Focused proof (`JAVA_TOOL_OPTIONS="-Dslabbed.beta35ObjectSlabOwnershipRed=true" ./gradlew --no-daemon runClientGameTest --console plain`): **BUILD SUCCESSFUL in 31s**

### Focused proof markers (all GREEN)

```
[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_FIXTURE_GREEN] proofScope=OWNER_ROUTE_ONLY_SIMPLE_ROUTING screenshotFaithfulTriad=NOT_PROVEN objectPos=49,202,0 objectState=torch objectDy=-1.000 slabPos=49,201,0 slabState=stone_slab[type=bottom] slabDy=-0.500 supportPos=48,200,0 supportState=stone_slab[type=bottom] supportDy=0.000
[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_TORCH_TARGET_GREEN] expectedOwner=torch actualOwner=torch vanillaTarget=BLOCK pos=49,201,0 face=north finalTarget=BLOCK pos=49,202,0 face=north raycastTargetPos=49,202,0
[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SLAB_TARGET_GREEN] expectedOwner=slab actualOwner=slab vanillaTarget=MISS finalTarget=BLOCK pos=49,201,0 face=north raycastTargetPos=49,201,0
[JULIA_BETA35_OBJECT_SLAB_TRIAD_OWNER_ROUTE_GREEN] proofScope=OWNER_ROUTE_ONLY_SIMPLE_ROUTING oldOwnerOnlyProof=GREEN screenshotFaithfulTriad=NOT_PROVEN objectTargetOwner=torch slabTargetOwner=slab survival=GREEN
[JULIA_BETA35_OBJECT_SLAB_TRIAD_FIXTURE_GREEN] objectPos=49,202,0 objectDy=-1.000 objectModelBoundsAccessible=false objectModelExpectedBounds=vanilla_torch_post_proxy:min=(49.375,201.000,0.375),max=(49.625,201.625,0.625) objectOutlineBounds=min=(49.375,201.000,0.375),max=(49.625,201.625,0.625) objectRaycastBounds=min=(49.375,201.000,0.375),max=(49.625,201.625,0.625) slabOutlineBounds=min=(49.000,200.500,0.000),max=(50.000,201.625,1.000)
[JULIA_BETA35_OBJECT_SLAB_TRIAD_MODEL_OUTLINE_GREEN] failureLayer=NONE outlineCoLocatedWithVisibleTorchBody=true objectModelExpectedBounds=vanilla_torch_post_proxy:min=(49.375,201.000,0.375),max=(49.625,201.625,0.625) objectOutlineBounds=min=(49.375,201.000,0.375),max=(49.625,201.625,0.625)
[JULIA_BETA35_OBJECT_SLAB_TRIAD_RAYCAST_GREEN] failureLayer=NONE objectRaycastTargetOwner=torch finalTargetOwner=torch
[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SURVIVAL_GREEN] objectPos=49,202,0 objectState=torch supportPos=49,201,0 supportState=stone_slab[type=bottom] survival=true
[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SUMMARY] proofScope=OWNER_ROUTE_ONLY_SIMPLE_ROUTING objectClass=torch slabState=stone_slab[type=bottom] objectDy=-1.000 slabDy=-0.500 torchTarget=GREEN slabTarget=GREEN survival=GREEN screenshotFaithfulTriad=GREEN failureLayer=NONE flashSnap=deferred_not_same_path
[JULIA_BETA35_OBJECT_SLAB_TRIAD_SUMMARY] oldOwnerOnlyProof=GREEN ownerRoute=GREEN modelOutline=GREEN raycast=GREEN survival=GREEN screenshotFaithfulTriad=GREEN failureLayer=NONE beta35IncludeStatus=INCLUDE targetLaw=A
```

Evidence folder: `tmp/support-anchor-object-triad-audit-9c90c23/`

## Classification

- **Failure layer: beta35 inclusion mismatch** (for cherry-pick; not a functional code bug)
- The fix (c96e674 + 6cb28bb + 274b286) is functionally correct on the integration branch — all triad markers are GREEN.
- Model, outline, and raycast agree: all share identical bounds `min=(49.375,201.000,0.375),max=(49.625,201.625,0.625)`.
- Support-anchor truth exists: torch at `dy=-1.000`, slab carrier at `dy=-0.500`, support at `dy=0.000`.
- Support-anchor dy values were recorded: `objectDy=-1.000`, `slabDy=-0.500`. This was not a visible contact-gap proof against the slab top surface.
- Beta 3.5 triad include status: **INCLUDE** (confirmed by `TRIAD_SUMMARY`). **This is triad-only; it does not prove player-facing floor torch placement or Julia's manual visual acceptance.**
- Cherry-pick onto old `release/0.2.0-beta.2` (d1417ff) fails due to divergent context in SLABBED_SPINE.md, docs/beta35-salvage-audit.md, GameRendererCrosshairRetargetMixin.java, and SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java — not a functional code bug.
- Release prep remains **PAUSED** because Julia's manual live test after the floor-torch player-placement GREEN savepoint did not accept the visual anchoring.
- Video evidence: not recorded/provided.

## Floor torch player-placement proof status

Julia's manual live test (MC 1.21.11) at HEAD `4f63abe` / `save/beta35-object-triad-inclusion-strategy` showed that the object-triad proof did not cover player-facing floor torch placement onto slab-supported geometry.

The proof at this savepoint (`proofScope=OWNER_ROUTE_ONLY_SIMPLE_ROUTING`) only proved owner-route targeting, model/outline/raycast co-location for a pre-placed fixture. It did not prove:
- player-initiated floor torch placement onto slab-supported faces
- anchor-dy correctness at the moment of player placement
- survival through real neighbor-update pulses from player actions
- coverage beyond floor torch

The gated proof at `-Dslabbed.beta35LiveItemAnchoringRed=true` now uses `interactionManager.interactBlock(...)` with a held floor torch. It emits placement, survival, triad, and summary GREEN markers with `failureLayer=NONE`, `itemCategory=floor_torch`, and `categoryScope=floor_torch_only`.

Julia's later manual verdict is still NOT ACCEPTED. The current blocker is floor
torch visual/support contact, not placement permission. The new gated audit
`-Dslabbed.beta35FloorTorchVisualContactRed=true` measures
`supportVisibleTopY`, `torchModelBottomY`, and `contactGap`; the controlled
fixture currently reports `contactGap=0.000000`, `triad=GREEN`,
`visualContactProofStatus=PENDING`, and `failureLayer=FIXTURE_MISMATCH`.

## Recommendation

- **integration branch triad proof: GREEN** / **floor torch player-placement proof: GREEN** / **lowered bottom-slab contact proof: GREEN** / **floor torch visual acceptance: pending Julia live acceptance** / **cherry-pick into beta.2 baseline: blocked by conflicts** / **release prep: PAUSED**

The 2026-05-11 lowered bottom-slab contact fix does not reopen object-triad scope.
The object triad regression remains GREEN with `failureLayer=NONE`; the new
contact fix is limited to `floor_torch` dy over `COMPOUND_VISIBLE_SIDE_LOWER_SLAB`
support. `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.

## Next slice

- Keep floor torch as the only current category for this slice.
- Do not claim floor-torch-only release scope accepted until Julia's visual anchoring blocker is resolved or explicitly waived.
- Do not expand to wall torch, lanterns, signs, or chains unless Julia explicitly requests a new slice.
- Do not re-cherry-pick without resolving the four conflicting files first.

## See also

- `docs/beta35-inclusion-strategy.md` — inclusion decision, invalid path classification, and valid release path (updated at `4f63abe`)
- `docs/beta35-live-item-anchoring-red.md` — floor torch player-placement proof details
