# Beta 3.5 Live Hitbox Owner Fix

Implementation slice for the RED captured at `ace31b5` / `save/beta35-live-hitbox-owner-red`.

Evidence folder: `tmp/beta35-live-hitbox-owner-fix-ace31b5/`.

## Scope

Changed crosshair owner preservation / retarget eligibility only.

The production gate is limited to lowered visible owners that are already shifted by the Beta 3.5 shape path:

- `SlabSupport.isBeta35FenceWallVariantContactObject(state)` for `FenceBlock` / `WallBlock` family members.
- Exact `minecraft:anvil`.

The fix does not retarget every lowered block, every solid block, panes, or arbitrary block entities. Existing torch, block-entity, and bed owner rules remain in place.

Collision math was not changed. Contact dy was not changed. Model/render dy was not changed. `SlabSupportStateMixin` and `OffsetBlockStateModel` were not edited. Door, trapdoor, sign, pane, lantern, chain, end-rod, redstone, and rail behavior was not opened.

## Result

Focused owner proof:

- Gate: `-Dslabbed.beta35LiveHitboxOwnerRed=true`
- Summary: `JULIA_BETA35_LIVE_HITBOX_OWNER_SUMMARY outcome=GREEN rows=3 red=0 pending=0 green=3 wallHitboxOwnerClassification=GREEN wallHitboxOwnerFailureLayer=NONE fenceHitboxOwnerClassification=GREEN fenceHitboxOwnerFailureLayer=NONE anvilHitboxOwnerClassification=GREEN anvilHitboxOwnerFailureLayer=NONE contactRenderFenceGateFamily=GREEN_SEPARATE hitboxOwnershipProof=GREEN recommendedNextSlice=savepoint_gate productionBehaviorChanged=true releaseAudit=NOT_RUN releaseTagMoved=false`
- Green marker: `JULIA_BETA35_LIVE_HITBOX_OWNER_GREEN rows=3 wall=GREEN fence=GREEN anvil=GREEN finalCrosshairOwner=expected_visible_owner failureLayer=NONE releaseAudit=NOT_RUN releaseTagMoved=false`

Rows:

- `minecraft:stone_brick_wall`: direct outline/raycast still hits the visible wall owner; final crosshair target is the wall; `ownerMatchesExpected=yes`; `finalDecision=object-shape-owner-preserve`; classification `GREEN`.
- `minecraft:oak_fence`: direct outline/raycast still hits the visible fence owner; final crosshair target is the fence; `ownerMatchesExpected=yes`; `finalDecision=object-shape-owner-preserve`; classification `GREEN`.
- `minecraft:anvil`: direct outline/raycast still hits the visible anvil owner; final crosshair target is the anvil; `ownerMatchesExpected=yes`; `finalDecision=object-shape-owner-preserve`; classification `GREEN`.

The final target is not `MISS`, not the slab underneath, and not the stone support underneath for all three rows.

## Regression Gates

- Compile: `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- Owner proof: `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveHitboxOwnerRed=true -Dfabric.client.gametest.disableNetworkSynchronizer=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- Live-hitbox-gate proof: `JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY outcome=PENDING rows=5 red=0 pending=3 green=2 ... fenceGateClosedClassification=GREEN ... fenceGateOpenClassification=GREEN`
- Fence/wall family proof: `JULIA_BETA35_FENCE_WALL_FAMILY_SUMMARY outcome=GREEN rows=21 greenFamily=20 notCovered=1 ... glassPaneControl=NOT_COVERED failureLayer=NONE`
- Fence-gate family proof: `JULIA_BETA35_FENCE_GATE_FAMILY_SUMMARY outcome=GREEN failureLayer=NONE variants=11 rows=22 greenRows=22`
- Common-object matrix: `JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=27 placementFailure=0 survivalFailure=0 contactGap=0 triadMismatch=0 collisionShapeRisk=0 ... releaseAudit=NOT_RUN releasePrep=PAUSED`
- Default client gametest: `BUILD SUCCESSFUL`
- `git diff --check` -> clean

The first exact owner-proof run without the Fabric synchronizer disable emitted GREEN owner rows but the harness exited during later network synchronization. The validated owner rerun used the same proof flag plus `-Dfabric.client.gametest.disableNetworkSynchronizer=true`, matching the rest of the savepoint gates.

No release audit was run. No release tag was moved.

## Follow-Up: Fence/Wall Contact Hitbox Fix

Julia's live inspect after `983d8ab` confirmed the owner fix but found a separate wall/fence contact-height problem: wall/fence objects over `supportDy=-1.0` full-block and top-slab support could float by `0.5` block. The previous owner savepoint did not change contact dy, collision math, or model/render dy.

The follow-up contact slice updates only the `FenceBlock` / `WallBlock` family contact dy path in `SlabSupport`, deriving object dy from the visible support top. Focused proof `-Dslabbed.beta35FenceWallContactHitbox=true` now reports `JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_SUMMARY outcome=GREEN rows=10 green=10 contactGap=0 triadMismatch=0 ownerGap=0 dyMismatch=0 failureLayer=NONE`.

Wall/fence model, outline, raycast, and collision bounds are co-located after the corrected dy. Anvil owner remains a regression check only for this contact slice. Floor_torch, candle, and flower_pot regressions remain green. Standing signs, lanterns, chains, redstone, rails, buttons/levers, wall/hanging signs, panes, doors, and trapdoors remain not covered. No release audit was run. No release tag was moved.

## Follow-Up: Fence/Wall Live Reject Tracer

After `57d651a`, Julia's live acceptance zip emitted no fence/wall live contact markers, but did emit server `Rejecting UseItemOnPacket ... too far away from hit block` lines at the lowered test positions. The next problem is live client/server hit validation capture, not release audit.

The diagnostics-only tracer flag is `-Dslabbed.beta35FenceWallLiveInspect=true`. It emits `[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] enabled=true`, client contact/triad/owner classifications, and server `SERVER_HIT_TOO_FAR` tolerance rows. No gameplay fix, release audit, or release tag movement is included.
