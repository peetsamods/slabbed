# Beta 3.5 Fence/Wall Stack Contact Fix

Implementation slice after `aa66efd` / `save/beta35-fence-wall-owner-server-hit`.

Evidence folder: `tmp/beta35-fence-wall-stack-jank-fix-aa66efd/`.

## Why

Julia's live acceptance trace after `aa66efd` showed the shifted server-hit validation fix was live-green:

- `SERVER_HIT_TOO_FAR=0`
- `SERVER_SHIFTED_HIT_GREEN=21`
- `SERVER_HIT_WITHIN_TOLERANCE=4`

The remaining concrete contact bucket was stacked wall/fence-family support, not slab or full-block support. The repeated live row was `minecraft:stone_brick_wall` over lowered `minecraft:stone_brick_wall` support:

- before object dy: `-0.500000`
- support dy: `-1.000000`
- support visible top: `-55.000000`
- object model bottom: `-54.500000`
- contact gap: `0.500000`
- triad: co-located
- final decision: `object-shape-owner-preserve`

Previous failure layer: `FENCE_WALL_STACK_CONTACT_DY_MISSING`.

## Fix

`SlabSupport.beta35FenceWallVisibleSupportDy(...)` now treats lowered `FenceBlock` / `WallBlock` support states as legal visible-support candidates for the already-scoped fence/wall family contact path.

The helper derives the top object's dy from the support's visible top for legal stack rows:

- wall over lowered wall support
- fence over lowered fence support
- wall over lowered fence support
- fence over lowered wall support

This keeps model, outline, raycast, and collision on the existing shared dy source. No server hit tolerance change was made, and there is no all-item or pane/sign/door/trapdoor category expansion.

## Result

Focused proof:

- Gate: `-Dslabbed.beta35FenceWallStackContact=true -Dslabbed.beta35FenceWallLiveInspect=true`
- Summary: `JULIA_BETA35_FENCE_WALL_STACK_CONTACT_SUMMARY outcome=GREEN rows=4 green=4 contactGap=0 triadMismatch=0 ownerGap=0 other=0 wallOnWall=GREEN fenceEquivalents=GREEN previousFailureLayer=FENCE_WALL_STACK_CONTACT_DY_MISSING failureLayer=NONE serverHitTooFarRows=0 serverHitToleranceChanged=false releaseAudit=NOT_RUN releaseTagMoved=false`
- Green marker: `JULIA_BETA35_FENCE_WALL_STACK_CONTACT_GREEN wallOnWall=GREEN fenceOnFence=GREEN wallOnFence=GREEN fenceOnWall=GREEN contact=STACK_CONTACT_GREEN triad=TRIAD_GREEN owner=OWNER_GREEN failureLayer=NONE releaseAudit=NOT_RUN releaseTagMoved=false`

Reproduced wall-on-wall row after the fix:

- `objectDy=-1.000000`
- `supportDy=-1.000000`
- `supportVisibleTopY=-55.000000`
- `objectModelBottomY=-55.000000`
- `contactGap=0.000000`
- `triadCoLocated=yes`
- `finalDecision=object-shape-owner-preserve`

Fence stack equivalents are green for fence-on-fence, wall-on-fence, and fence-on-wall. These are proof-fixture existing-state stack cases; no new placement legality was invented.

## Residual Jank Classification

The residual connected-wall triad bucket is classified as `TRACE_FALSE_POSITIVE` / vanilla connected-wall shape limit for now: the rows have contact green and dy `0.000000`, but connected wall model/collision height can exceed outline/raycast reporting (`raycastBounds=null`). This slice does not patch that path.

Residual owner gaps are classified as `OUT_OF_SCOPE_HELD_ITEM_OWNER_GAP_OR_NEXT_FOCUSED_RED`: top buckets from Julia's trace involve held chain/stairs/trapdoor/button or anvil-plus-chain contexts outside this slice. If post-fix live capture still shows in-scope fence/wall/anvil visible-owner rows with legal support, that should become a separate focused RED proof.

## Regression Gates

- Compile: `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- Stack contact proof: `BUILD SUCCESSFUL`
- Owner/server-hit proof: `BUILD SUCCESSFUL`
- Fence/wall contact-hitbox proof: `BUILD SUCCESSFUL`
- Live-hitbox owner proof for wall/fence/anvil: `BUILD SUCCESSFUL`
- Live-hitbox gate proof: `BUILD SUCCESSFUL`
- Fence/wall family proof: `BUILD SUCCESSFUL`
- Fence-gate family proof: `BUILD SUCCESSFUL`
- Common-object matrix: `BUILD SUCCESSFUL`
- Floor torch plain-bottom contact: `BUILD SUCCESSFUL`
- Candle floor/top contact: `BUILD SUCCESSFUL`
- Flower pot floor/top contact and survival: `BUILD SUCCESSFUL`
- Default client gametest: `BUILD SUCCESSFUL`
- runClient live-inspect smoke emitted `[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] enabled=true`.
- `git diff --check` -> clean

Release audit remains paused pending Julia live acceptance. No release tag was moved. Scope remains fence/wall stack contact plus the existing fence/wall/anvil owner diagnostics and floor_torch/candle/flower_pot regressions; there is no all-item claim.
