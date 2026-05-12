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
