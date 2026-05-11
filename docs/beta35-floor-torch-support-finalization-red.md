# Beta 3.5 Floor Torch Support Finalization RED

## Status

- RED proof confirmed: stale floor torch after slab support finalization.
- No production gameplay fix implemented in this slice.
- `wall_torch`, `lantern`, `signs`, `chains` remain `NOT_COVERED`.
- Beta 3.5 release remains paused.
- No release tag moved.
- Next slice: production fix after this RED proof is confirmed by Julia.

## Post-cf709aa live retest context

The main Beta 3.5 floor-torch fix (`d21d211`, `cf709aa`) was overwhelmingly successful in live
retest. The remaining failures are a timing/support-finalization race:

- 27 cases: `supportCandidateState=stone_slab[type=bottom]`, `supportDy=-1.000`,
  `torchDy=-1.000`, `contactGap=0.500` — torch authored before compound mark; remains after.
- 11 cases: `torchDy=-0.500`, `contactGap=1.000` — stale dy variant (no compound source present
  at placement time).
- 4 wall_torch cases remain out of scope.

## Bug mechanism

```
1. Slab placed in vanilla/pre-finalization state (no compound mark).
2. Floor torch authored on slab (canPlaceAt passes — isRejectedFloorTorchTopFace=false).
3. Server writes compound-visible-side-lower mark to slab via addCompoundVisibleSideLowerSlab.
   → Attachment write calls chunk.setAttached (chunk dirty + sync).
   → Does NOT call world.updateNeighbors.
   → getStateForNeighborUpdate is NEVER triggered on the torch.
4. Torch remains.
   → isRejectedFloorTorchTopFace=true, canPlaceAt=false, neighborUpdateWouldRemove=true.
   → But none of these are evaluated automatically.
```

`TorchBlockMixin.getStateForNeighborUpdate` has the correct rejection logic (returns AIR when
`isRejectedFloorTorchTopFace` is true with `direction == DOWN`). The fix direction is to call
`world.updateNeighbors` (or equivalent) from the site where the compound mark is written, so the
torch's `getStateForNeighborUpdate` is triggered.

## RED proof observed values

### before_finalization
- `supportStateBefore=stone_slab[type=bottom]`
- `supportDyBefore=-0.500` (plain bottom slab, no compound mark)
- `torchStateBefore=torch` (placement accepted)
- `torchDyBefore=-1.000` (dy correct because compound source is adjacent)
- `isRejectedBefore=false` ✓ (torch legally placed)

### after_finalization
- `supportStateAfter=stone_slab[type=bottom]`
- `supportDyAfter=-1.000` ✓ (compound mark applied)
- `compoundLowerMarkWritten=true` ✓
- `isRejectedAfter=true` ✓ (mark makes torch illegal)
- `canPlaceAfter=false` ✓
- `neighborUpdateWouldRemove=true` ✓ (getStateForNeighborUpdate(DOWN) returns AIR)
- `torchStateAfter=torch` ← **BUG: torch not removed**
- `torchDyAfter=-1.000`
- `contactGapAfter=0.500000` ← matches live triage 27-case pattern
- `legalOutcome=FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_STALE_TORCH_REMAINS`
- `failureLayer=SUPPORT_FINALIZATION_STALE_TORCH`
- `redProofResult=RED` ✓

## Fix direction

Call `world.updateNeighbors(supportPos, supportBlock)` (or the targeted single-neighbor variant)
from `SlabAnchorAttachment.addCompoundVisibleSideLowerSlab` (and symmetrically for the mark-removal
path) so the torch's `getStateForNeighborUpdate(direction=DOWN)` fires after finalization.

The existing `TorchBlockMixin.getStateForNeighborUpdate` logic already handles this correctly:
if `isRejectedFloorTorchTopFace` is true, it returns `Blocks.AIR.getDefaultState()`.
No additional torch removal code is needed.

## Proof gate

`-Dslabbed.beta35FloorTorchSupportFinalizationRed=true`

Command:

```
JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTorchSupportFinalizationRed=true" ./gradlew --no-daemon runClientGameTest --console plain
```

## Regression status

- compileJava compileGametestJava: GREEN
- focused support finalization RED proof: RED (bug confirmed, not thrown as test failure)
- v2 contact gap regression: GREEN
- full-block contact fix regression: GREEN
- default client gametest suite: GREEN
- git diff --check: GREEN

Evidence folder:

`tmp/beta35-floor-torch-support-finalization-red-cf709aa`
