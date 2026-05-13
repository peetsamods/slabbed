# Beta 3.5 Fence/Wall Visual Hitbox Stack Aim Fix

Implementation slice after `5f94ed5` / `save/beta35-fence-wall-stack-contact`.

Evidence folder: `tmp/beta35-fence-wall-visual-hitbox-stack-aim-5f94ed5/`.

## Why

Julia's live verdict after `5f94ed5` was that the fence/wall work looked much better, but the remaining visible issue was stack aim and selection overhang: fence/wall hitboxes appeared above the visible model, and stacking felt like it required aiming into empty space.

The live trace also showed:

- `SERVER_HIT_TOO_FAR=0`
- stack contact fixed by the prior savepoint
- remaining torch-support contact rows were support-candidate noise, not legal fence/wall support

Previous failure layer: `FENCE_WALL_VISUAL_HITBOX_OVERHANG_STACK_AIM`.

## Fix

`SlabSupportStateMixin` no longer substitutes the tall collision shape for lowered Beta 3.5 fence/wall outline selection. Visual selection/raycast/stack aim now use the visible outline/raycast body.

`GameRendererCrosshairRetargetMixin` extends the existing Beta 3.5 visible-owner scan by one upward candidate so deeply lowered fence/wall/anvil owners remain discoverable after outline no longer uses collision. This remains gated to the proven fence/wall/anvil owner set.

`Beta35FenceWallLiveInspectRecorder` now separates visual bounds from collision:

- visual triad: model/outline/raycast
- collision: logged separately as `collisionOverhangY` / `COLLISION_OVERHANG`
- invalid torch support candidates: `TRACER_SUPPORT_NOISE`

No contact dy rewrite, server hit tolerance change, global collision lowering, or all-item/category expansion was made.

## Result

Focused proof:

- Gate: `-Dslabbed.beta35FenceWallVisualHitboxStackAim=true -Dslabbed.beta35FenceWallLiveInspect=true`
- Summary: `JULIA_BETA35_FENCE_WALL_VISUAL_HITBOX_STACK_AIM_SUMMARY outcome=GREEN rows=2 visualHitboxGreen=1 collisionOnlyOverhang=1 red=0 ... failureLayer=NONE`
- Green marker: `JULIA_BETA35_FENCE_WALL_VISUAL_HITBOX_STACK_AIM_GREEN birchFence=VISUAL_HITBOX_GREEN connectedWall=COLLISION_OVERHANG_NOT_VISUAL_TRIAD visibleBodyAim=STACK_AIM_GREEN emptyOverhang=DOES_NOT_STEAL_OWNER torchSupportContactGap=TRACER_SUPPORT_NOISE failureLayer=NONE`

Birch fence row after fix:

- `visualSelectionMinY=-55.000000`
- `visualSelectionMaxY=-54.000000`
- `outlineMinY=-55.000000`
- `outlineMaxY=-54.000000`
- `raycastMinY=-55.000000`
- `raycastMaxY=-54.000000`
- `collisionMinY=-55.000000`
- `collisionMaxY=-53.500000`
- `collisionOverhangY=0.500000`
- visible-body aim selects the fence and would use it for stack placement
- empty-overhang aim returns MISS and does not steal owner

Connected wall row after fix:

- visual outline/model are aligned
- collision overhang remains `0.500000`
- classification is `COLLISION_OVERHANG_NOT_VISUAL_TRIAD`
- empty overhang does not steal owner

Torch-support contact rows are classified as `TRACER_SUPPORT_NOISE` / `INVALID_SUPPORT_CANDIDATE`.

## Regression Gates

- Compile: `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- Visual hitbox / stack aim proof: `BUILD SUCCESSFUL`
- Stack contact proof: `BUILD SUCCESSFUL`
- Owner/server-hit proof: `BUILD SUCCESSFUL`
- Fence/wall contact-hitbox proof: `BUILD SUCCESSFUL`
- Live-hitbox owner proof: `BUILD SUCCESSFUL`
- Live-hitbox gate proof: `BUILD SUCCESSFUL`
- Fence/wall family proof: `BUILD SUCCESSFUL`
- Fence-gate family proof: `BUILD SUCCESSFUL`
- Common-object matrix: `BUILD SUCCESSFUL`
- Floor torch plain-bottom contact: `BUILD SUCCESSFUL`
- Candle floor/top contact: `BUILD SUCCESSFUL`
- Flower pot floor/top contact and survival: `BUILD SUCCESSFUL`
- Default client gametest: `BUILD SUCCESSFUL`
- runClient live-inspect smoke emitted `[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] enabled=true`; the dev client was intentionally stopped after the marker.
- `git diff --check` -> clean

Release audit remains paused pending Julia live acceptance. No release tag was moved. Scope remains fence/wall visual selection/stack aim plus existing fence/wall/anvil owner diagnostics and floor_torch/candle/flower_pot regressions. There is no all-item claim.

## Follow-Up Julia Live Scope Correction

After `eec3bc0`, Julia's live verdict was near-acceptance for fence/wall: "everything looks almost perfect." The remaining issue is at one slab height and is not bounded to lantern, chain, or button. Those are examples from the video; the real question is generic visible-target hit acceptance for arbitrary held-item categories at that slab height.

The follow-up diagnostic slice adds `-Dslabbed.beta35SlabHeightHitAcceptance=true` and focused matrix proof `-Dslabbed.beta35SlabHeightHitAcceptanceRed=true`. It is tracing/proof only: no gameplay fix, no category-specific fix, no all-item claim, no release audit, and no release tag movement.

The `63a0e32` slab-height matrix is superseded as proof evidence after Julia's live retest. It was false green because it did not fail nonzero contact metrics and did not measure aim aperture. The corrected diagnostic flag is `-Dslabbed.beta35HitboxApertureContactRed=true`, and the latest local corrected summary is `outcome=RED rows=8 green=2 red=6 buttonContactGapRows=1 chainMetricGapRows=1 apertureTooNarrowRows=4 firstFailureLayer=BUTTON_FLOOR_CONTACT_DY_MISSING nextRecommendedFix=MIXED`. This remains proof-only; no gameplay fix, all-item claim, release audit, or release tag movement was made.
