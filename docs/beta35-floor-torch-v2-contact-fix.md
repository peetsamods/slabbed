# Beta 3.5 Floor Torch V2 Contact Fix

## Status

- Production fix applied narrowly for `floor_torch` only.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
- Beta 3.5 release remains paused pending Julia live re-test.
- No release tag moved.

## Fix mechanism

The floor torch dy and support law now use `SlabSupport` as the shared authority.

- `FLOOR_TORCH_COMPOUND_VISIBLE_TOP_SLAB_SUPPORT`: floor torch above a compound visible side upper/top slab uses `torchDy=-1.000`, matching the support visible top without authoring any `dy<-1.0` object lane.
- `FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_REJECTED_DY_LT_MINUS_ONE_ILLEGAL`: floor torch above a compound visible side lower/bottom slab is rejected because contact alignment at the same torch block position would require illegal `torchDy=-1.500`.

`TorchBlockMixin` delegates placement and survival to the narrow `SlabSupport` floor-torch support authority instead of adding inline broad support law.

## Focused proof

Command:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTorchV2ContactGapRed=true" ./gradlew --no-daemon runClientGameTest --console plain`

Result: GREEN.

### top_slab_support

- `coordinateParity=true`
- `fixtureMatchesFixedLegalStack=true`
- `supportDy=-1.000`
- `torchDyBefore=-0.500`
- `torchDyAfter=-1.000`
- `supportVisibleTopY=-57.000000`
- `torchModelBottomY=-57.000000`
- `contactGap=0.000000`
- `survival=SURVIVAL_GREEN`
- `triad=GREEN`
- `failureLayer=NONE`

### bottom_slab_support

- `coordinateParity=true`
- `fixtureMatchesFixedLegalStack=true`
- `supportDy=-1.000`
- `torchDyBefore=-1.000`
- `torchDyAfter=N/A`
- `supportVisibleTopY=-57.500000`
- `torchModelBottomY=N/A`
- `contactGap=N/A`
- `placementResult=Fail[]`
- `survival=REJECTED_BY_LAW`
- `triad=REJECTED_BY_LAW`
- `failureLayer=NONE`

## Regression status

Regression proof runs for this slice:

- compileJava compileGametestJava: GREEN
- focused v2 contact fix proof: GREEN
- previous live item anchoring proof: GREEN
- previous object triad proof: GREEN
- default client gametest suite: GREEN
- git diff --check: GREEN

Evidence folder:

`tmp/beta35-floor-torch-v2-contact-fix-883b204`
