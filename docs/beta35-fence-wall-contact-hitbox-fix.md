# Beta 3.5 Fence/Wall Contact Hitbox Fix

Implementation slice for Julia's live report after `983d8ab` / `save/beta35-live-hitbox-owner-fix`.

Evidence folder: `tmp/beta35-fence-wall-contact-hitbox-fix-983d8ab/`.

## Scope

`983d8ab` fixed visible owner selection only. It did not fix contact height.

This slice changes only the `FenceBlock` / `WallBlock` family contact dy path in `SlabSupport`. The corrected dy is computed from the visible support top for legal slab-supported floor/top cases so wall/fence model, outline, raycast, and collision bounds stay co-located.

No release audit was run. No release tag was moved. Standing signs, lanterns, chains, redstone, rails, buttons/levers, wall/hanging signs, panes, doors, and trapdoors remain out of scope. Anvil owner remains a regression check only here.

## Result

Focused proof:

- Gate: `-Dslabbed.beta35FenceWallContactHitbox=true`
- Summary: `JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_SUMMARY outcome=GREEN rows=10 green=10 contactGap=0 triadMismatch=0 ownerGap=0 dyMismatch=0 other=0 previousFailureLayer=FENCE_WALL_CONTACT_DY_MISSING_FOR_DEEP_SUPPORT failureLayer=NONE`
- Green marker: `JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_GREEN rows=10 wall=GREEN fence=GREEN contact=CONTACT_GREEN triad=TRIAD_GREEN owner=OWNER_GREEN failureLayer=NONE releaseAudit=NOT_RUN releaseTagMoved=false`

Corrected rows:

- Wall over lowered full block `supportDy=-1.0`: `objectDy=-1.0`, `contactGap=0.000000`, `triadCoLocated=yes`, owner `OWNER_GREEN`.
- Wall over lowered top slab `supportDy=-1.0`: `objectDy=-1.0`, `contactGap=0.000000`, `triadCoLocated=yes`, owner `OWNER_GREEN`.
- Wall over lowered bottom slab `supportDy=-1.0`: preserved `objectDy=-1.5`, `contactGap=0.000000`, `triadCoLocated=yes`, owner `OWNER_GREEN`.
- Wall over top/double `supportDy=-0.5`: preserved `objectDy=-0.5`, `contactGap=0.000000`, `triadCoLocated=yes`, owner `OWNER_GREEN`.
- Fence equivalents are green for the same support categories.

Live inspect before this fix found wall contact gaps of `0.5` on `supportDy=-1.0` full-block and top-slab support. After the fix, the focused proof records `supportVisibleTopY == objectModelBottomY` for the named rows.

## Regression Gates

- Compile: `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- Owner proof: `JULIA_BETA35_LIVE_HITBOX_OWNER_SUMMARY outcome=GREEN rows=3 red=0 pending=0 green=3`
- Live-hitbox-gate proof: `JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY outcome=PENDING rows=5 red=0 pending=3 green=2`
- Fence/wall family proof: `JULIA_BETA35_FENCE_WALL_FAMILY_SUMMARY outcome=GREEN rows=21 greenFamily=20 notCovered=1 failureLayer=NONE`
- Fence-gate family proof: `JULIA_BETA35_FENCE_GATE_FAMILY_SUMMARY outcome=GREEN failureLayer=NONE variants=11 rows=22 greenRows=22`
- Common-object matrix: `JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=27 placementFailure=0 survivalFailure=0 contactGap=0 triadMismatch=0 collisionShapeRisk=0 releaseAudit=NOT_RUN releasePrep=PAUSED`
- Floor torch plain-bottom contact: `failureLayer=NONE`
- Candle floor/top contact: `failureLayer=NONE`
- Flower pot floor/top contact and survival: `failureLayer=NONE`
- Default client gametest: `BUILD SUCCESSFUL`
- `git diff --check` -> clean

Beta 3.5 release audit remains paused until Julia live-accepts this wall/fence correction.

## Follow-Up: Live Reject Tracer

Julia's latest live acceptance zip after `57d651a` emitted no fence/wall live contact markers, so the proof harness was not producing live runClient diagnostics. The same log did emit vanilla server `Rejecting UseItemOnPacket ... too far away from hit block` lines at the tested lowered positions.

Current classification is `LIVE_TRACE_MISSING_PLUS_SERVER_HIT_TOLERANCE_REJECT`. The follow-up tracer slice adds `-Dslabbed.beta35FenceWallLiveInspect=true` with startup marker `[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] enabled=true`, client contact/triad/owner rows, and server hit-tolerance rows including `SERVER_HIT_TOO_FAR`.

This is diagnostics only. No gameplay fix, tolerance widening, contact dy change, release audit, or release tag movement is included.

## Follow-Up: Owner / Server-Hit Fix

The `fbbbd68` tracer proved contact and triad stayed green in live rows, but owner targeting and server shifted-hit validation still failed: `LIVE_OWNER_GAP=1495`, `SERVER_HIT_TOO_FAR=2`, `LIVE_CONTACT_GAP=0`, and `LIVE_TRIAD_MISMATCH=0`.

The follow-up owner/server-hit slice changes only final owner priority and server validation for legal Slabbed-lowered fence/wall/anvil target contexts. It does not rewrite the `57d651a` fence/wall contact dy path. The focused proof now reports `JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_SUMMARY outcome=GREEN ... failureLayer=NONE`, with `finalDecision=object-shape-owner-preserve` and `SERVER_SHIFTED_HIT_GREEN`.

No release audit was run. No release tag was moved. Standing signs, lanterns, chains, redstone, rails, buttons/levers, wall/hanging signs, panes, doors, and trapdoors remain out of scope.
