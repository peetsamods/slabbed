# Beta 3.5 Live Hitbox Owner RED

Proof-only slice for Julia's remaining Beta 3.5 live hitbox complaint after contact/render/fence-gate support went green.

Operating base: `13775ce` / `save/beta35-fence-gate-family-fix` on `integrate/phase19-into-side-slab-top-support`.

Evidence folder: `tmp/beta35-live-hitbox-owner-red-13775ce/`.

Gate: `-Dslabbed.beta35LiveHitboxOwnerRed=true`

Markers:

- `JULIA_BETA35_LIVE_HITBOX_OWNER_RED`
- `JULIA_BETA35_LIVE_HITBOX_OWNER_ROW`
- `JULIA_BETA35_LIVE_HITBOX_OWNER_SUMMARY`

No production gameplay, collision, targeting, mixin, or render fix was implemented. No release audit was run. No release tag was moved.

## Result

Focused proof outcome: RED.

`JULIA_BETA35_LIVE_HITBOX_OWNER_SUMMARY outcome=RED rows=3 red=3 pending=0 green=0 wallHitboxOwnerClassification=WALL_HITBOX_OWNER_GAP wallHitboxOwnerFailureLayer=OUTLINE_RAYCAST_OWNER_GAP fenceHitboxOwnerClassification=FENCE_HITBOX_OWNER_GAP fenceHitboxOwnerFailureLayer=OUTLINE_RAYCAST_OWNER_GAP anvilHitboxOwnerClassification=ANVIL_HITBOX_OWNER_GAP anvilHitboxOwnerFailureLayer=OUTLINE_RAYCAST_OWNER_GAP contactRenderFenceGateFamily=GREEN_SEPARATE hitboxOwnershipProof=RED recommendedNextSlice=GameRendererCrosshairRetargetMixin_owner_classification productionBehaviorChanged=false releaseAudit=NOT_RUN releaseTagMoved=false`

Rows:

- `minecraft:stone_brick_wall`: direct outline/raycast hit the expected owner at `680, -54, 196`, but final crosshair stayed `type=MISS`; classification `WALL_HITBOX_OWNER_GAP`, failure layer `OUTLINE_RAYCAST_OWNER_GAP`.
- `minecraft:oak_fence`: direct outline/raycast hit the expected owner at `690, -54, 196`, but final crosshair stayed `type=MISS`; classification `FENCE_HITBOX_OWNER_GAP`, failure layer `OUTLINE_RAYCAST_OWNER_GAP`.
- `minecraft:anvil`: direct outline/raycast hit the expected owner at `700, -54, 196`, but final crosshair stayed `type=MISS`; classification `ANVIL_HITBOX_OWNER_GAP`, failure layer `OUTLINE_RAYCAST_OWNER_GAP`.

## Classification

This proof splits hitbox ownership from the already-green support/contact/render/gate rows. The visible lowered object bodies are aimable by direct outline/raycast tests, but the live crosshair owner does not resolve to the wall, fence, or anvil object.

The next implementation slice should inspect `GameRendererCrosshairRetargetMixin` owner classification for lowered fence/wall/anvil bodies. Do not reopen contact/render/fence-gate support unless a new proof contradicts this RED.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveHitboxOwnerRed=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`; RED markers emitted as documented above

## Follow-Up: Live Hitbox Owner Fix

The implementation slice based on `ace31b5` / `save/beta35-live-hitbox-owner-red` fixed the owner layer only. It did not change collision math, contact dy, model/render dy, `SlabSupportStateMixin`, or `OffsetBlockStateModel`.

Focused owner proof now reports:

- `JULIA_BETA35_LIVE_HITBOX_OWNER_SUMMARY outcome=GREEN rows=3 red=0 pending=0 green=3 wallHitboxOwnerClassification=GREEN wallHitboxOwnerFailureLayer=NONE fenceHitboxOwnerClassification=GREEN fenceHitboxOwnerFailureLayer=NONE anvilHitboxOwnerClassification=GREEN anvilHitboxOwnerFailureLayer=NONE contactRenderFenceGateFamily=GREEN_SEPARATE hitboxOwnershipProof=GREEN recommendedNextSlice=savepoint_gate productionBehaviorChanged=true releaseAudit=NOT_RUN releaseTagMoved=false`
- `JULIA_BETA35_LIVE_HITBOX_OWNER_GREEN rows=3 wall=GREEN fence=GREEN anvil=GREEN finalCrosshairOwner=expected_visible_owner failureLayer=NONE releaseAudit=NOT_RUN releaseTagMoved=false`

The three former RED rows now preserve the visible lowered owner:

- `minecraft:stone_brick_wall`: final crosshair target is the wall owner.
- `minecraft:oak_fence`: final crosshair target is the fence owner.
- `minecraft:anvil`: final crosshair target is the anvil owner.

Panes remain excluded. Doors, trapdoors, signs, lanterns, chains, end rods, redstone, and rails were not opened. No release audit was run. No release tag was moved.

## Follow-Up: Fence/Wall Contact Hitbox Fix

The `983d8ab` owner fix did not fix contact height. Julia's live inspect after that savepoint found wall contact gaps of `0.5` when lowered wall/fence-family objects sat over `supportDy=-1.0` full-block and top-slab support.

The follow-up contact slice changes only the `FenceBlock` / `WallBlock` family contact dy path. Focused proof now reports `JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_SUMMARY outcome=GREEN rows=10 green=10 contactGap=0 triadMismatch=0 ownerGap=0 dyMismatch=0 failureLayer=NONE`; wall and fence representatives both contact the visible support surface and keep model/outline/raycast/collision co-located.

Anvil owner remains a regression check only in this contact slice. Floor_torch, candle, and flower_pot regressions remain green. Standing signs, lanterns, chains, redstone, rails, buttons/levers, wall/hanging signs, panes, doors, and trapdoors remain not covered. No release audit was run. No release tag was moved.
