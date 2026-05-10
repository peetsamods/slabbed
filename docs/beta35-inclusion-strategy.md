# Beta 3.5 Object Triad Inclusion Strategy

## Decision

The Beta 3.5 object triad fix should be included from the current integration branch lineage, not cherry-picked onto the old `release/0.2.0-beta.2` baseline.

## Proven facts

All focused proof markers are GREEN at classification savepoint `00f62e5` / `save/beta35-object-triad-classification`:

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

Triad co-location values (model / outline / raycast bounds are identical):

| Position    | Block                     | dy      |
|-------------|---------------------------|---------|
| 49,202,0    | torch (object)            | -1.000  |
| 49,201,0    | stone_slab[type=bottom]   | -0.500  |
| 48,200,0    | stone_slab[type=bottom]   | 0.000   |

Object bounds: `min=(49.375,201.000,0.375),max=(49.625,201.625,0.625)` — all three (model/outline/raycast) agree.

## Invalid path

Cherry-picking commits `c96e674` + `6cb28bb` + `274b286` onto `d1417ff` (`release/0.2.0-beta.2`) is **invalid**.

The first cherry-pick (`c96e674`) stopped immediately with conflicts in four files:

- `SLABBED_SPINE.md` — delete/modify conflict (diverged docs history)
- `docs/beta35-salvage-audit.md` — delete/modify conflict (does not exist on the beta.2 baseline)
- `src/client/java/com/slabbed/mixin/client/GameRendererCrosshairRetargetMixin.java` — content conflict
- `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java` — content conflict

These conflicts arise from divergent context between the old beta.2 baseline and the current integration lineage. They are not a defect in the fix itself. The fix code is functionally correct — all triad markers are GREEN on the integration branch. Do not attempt to re-cherry-pick onto `d1417ff` without first applying prerequisite commits that close this context gap; that path is classified as invalid.

## Valid path

Use the current integration branch lineage as the source for Beta 3.5 inclusion. Two options:

1. **Include directly from integration lineage** — if the release plan allows cutting from the integration branch, the object triad fix is already present and proven at `save/beta35-object-triad-classification`.

2. **Create a fresh `release/0.2.0-beta.3.5` branch** — start from the current stable integration baseline (a known-green integration commit) and apply commits in integration-lineage order. The prerequisite context that caused the cherry-pick conflicts will already be present; `c96e674` + `6cb28bb` + `274b286` will apply cleanly.

Do not use `d1417ff` (`release/0.2.0-beta.2`) as the starting point for either option.

## Gameplay status

No object-triad gameplay fix is needed. At savepoint `00f62e5`:

- No support-anchor truth gap remains.
- No owner/raycast targeting gap remains.
- No model/outline offset gap remains.
- `failureLayer=NONE` on both `MODEL_OUTLINE_GREEN` and `RAYCAST_GREEN`.
- `beta35IncludeStatus=INCLUDE` on `TRIAD_SUMMARY`.

The object triad is complete. Do not reopen it.

## Release status

The object triad fix is **triad-include-ready** (targeting, model, outline, raycast co-location proven for a controlled pre-placed fixture). The floor torch player-facing placement path is also GREEN in the gated proof, with category scope limited to `floor_torch_only`.

Beta 3.5 release prep remains **PAUSED** because Julia manually live-tested after
`0f08624` / `save/beta35-floor-torch-player-placement` and did not accept the
floor torch visual anchoring. The current blocker is floor torch visual/support
contact, not placement permission.

The gated floor torch player-placement proof uses `-Dslabbed.beta35LiveItemAnchoringRed=true`. Markers emit placement GREEN, survival GREEN, triad GREEN, and summary `failureLayer=NONE` with `itemCategory=floor_torch` and `categoryScope=floor_torch_only`. See `docs/beta35-live-item-anchoring-red.md`.

The new gated visual contact audit uses
`-Dslabbed.beta35FloorTorchVisualContactRed=true`. It measures
`supportVisibleTopY`, `torchModelBottomY`, `contactGap`, outline/raycast Y
ranges, and triad co-location. The controlled fixture currently reports
`contactGap=0.000000`, `triad=GREEN`, `visualContactProofStatus=PENDING`, and
`failureLayer=FIXTURE_MISMATCH`, so no tiny gameplay fix is proven by this
measurement alone.

The live-shape parity proof uses
`-Dslabbed.beta35FloorTorchLiveShapeRed=true`. It seeds a multi-level
screenshot-style slab/full structure and re-runs player-like floor torch
placement measurement. Current measured result is still
`supportVisibleTopY=201.000000`, `torchModelBottomY=201.000000`,
`contactGap=0.000000`, `torchDy=-1.000`, `supportDy=-0.500`, `triad=GREEN`,
`liveShapeProofStatus=GREEN`, `failureLayer=NONE`. This means the fixture still
does not reproduce Julia's manual visual complaint directly.

Do not conflate the triad proof with broader category scope or visual
acceptance. Wall torch, lanterns, signs, and chains remain not covered and are
not the current blocker.

## Next recommended action

1. Do not touch the object triad. It is triad-proven.
2. Keep the current work scoped to floor torch visual/support contact.
3. Do not claim floor-torch-only release scope accepted.
4. Keep live-shape parity work proof-only until a reproducible RED layer is captured.
5. Resume release audit only if Julia explicitly authorizes release prep after the visual anchoring blocker is resolved or waived.
6. When cutting the Beta 3.5 release branch, use option 1 or 2 from the valid path above, never the invalid cherry-pick-onto-d1417ff path.

## See also

- `docs/beta35-object-triad-dryrun.md` — cherry-pick dry-run evidence and integration branch proof markers
- `docs/beta35-salvage-audit.md` — full Beta 3.5 INCLUDE/EXCLUDE/NEEDS PROOF candidate table
- `docs/beta4-compound-lowered-fullblock-contract-audit.md` — separate Beta 4 blocker details
