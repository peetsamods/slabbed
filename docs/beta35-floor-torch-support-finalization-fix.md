# Beta 3.5 Floor Torch Support Finalization Fix

## Status

- Production fix applied narrowly for `floor_torch` only.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
- Beta 3.5 release remains paused pending Julia live re-test.
- No release tag moved.
- **Remaining gap (2026-05-11):** After this fix, Julia reported a distinct failure: `SBSBS+torch = floating torch at vanilla position`. The SBSBS controlled-fixture proof is GREEN (`torchDy=-1.000`, `contactGap=0.000000`, `failureLayer=NONE`). The gap between the controlled fixture and Julia's live experience is unresolved. See `docs/beta35-floor-torch-sbsbs-red.md`.

## Bug recap

After the compound-visible-lower mark is written to a bottom slab via
`addCompoundVisibleSideLowerSlab`, any floor torch that was placed before that mark was applied
could remain stale. The mark write went through `chunk.setAttached` which does NOT trigger
`getStateForNeighborUpdate` on adjacent blocks, so `TorchBlockMixin.getStateForNeighborUpdate`
was never called and the torch was never removed.

RED proof confirmed the pattern from live triage (27 cases):
`torchDy=-1.000`, `contactGap=0.500000`, `legalOutcome=FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_STALE_TORCH_REMAINS`.

See `docs/beta35-floor-torch-support-finalization-red.md` for the full RED proof record.

## Fix mechanism

In `addCompoundVisibleSideLowerSlab` (after a freshly-added mark):

```java
boolean added = addToAttachment(world, pos, COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE,
        "compound_visible_side_lower_slab");
if (added) {
    world.replaceWithStateForNeighborUpdate(Direction.DOWN, pos.up(), pos, state, Block.NOTIFY_ALL, 512);
}
```

`World.replaceWithStateForNeighborUpdate(Direction.DOWN, torchPos, slabPos, slabState, flags, depth)`
calls `getStateForNeighborUpdate(direction=DOWN, ...)` on the block at `torchPos`. When that block
is a floor torch, `TorchBlockMixin.getStateForNeighborUpdate` fires, sees
`isRejectedFloorTorchTopFace=true`, and returns `Blocks.AIR.getDefaultState()`. Minecraft then
replaces the torch with AIR.

The call is guarded by `added` (true only on first mark write) so it does not spam on every
attach-path re-entry.

## Why not `world.updateNeighborsAlways`

`updateNeighborsAlways` triggers `Block.neighborUpdate` on adjacent blocks. `AbstractTorchBlock`
does NOT override `neighborUpdate`, so the call is a no-op for torches. The survival path for
torches goes exclusively through `getStateForNeighborUpdate`, which is what
`replaceWithStateForNeighborUpdate` invokes directly.

## Legal outcome

| Case | legalOutcome | torchStateAfter | failureLayer |
|---|---|---|---|
| compound lower slab — torch placed before mark | `FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_TORCH_CORRECTLY_REMOVED` | `air` | `SUPPORT_FINALIZATION_REMOVED_GREEN` |

## Focused proof

Gate: `-Dslabbed.beta35FloorTorchSupportFinalizationRed=true`

Command:

```
JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTorchSupportFinalizationRed=true" ./gradlew --no-daemon runClientGameTest --console plain
```

Result: GREEN.

### after_finalization

- `supportDyAfter=-1.000` ✓
- `compoundLowerMarkWritten=true` ✓
- `torchStateAfter=Block{minecraft:air}` ✓
- `torchPresent=false` ✓
- `legalOutcome=FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_TORCH_CORRECTLY_REMOVED`
- `failureLayer=SUPPORT_FINALIZATION_REMOVED_GREEN`
- `redProofResult=GREEN`
- `productionGameplayFixApplied=true`

## Regression status

- compileJava compileGametestJava: GREEN
- focused support finalization proof: GREEN (was RED)
- v2 contact fix regression: GREEN
- full-block contact fix regression: GREEN
- default client gametest suite: GREEN
- git diff --check: GREEN

## Coverage

- `wall_torch=NOT_COVERED`
- `lantern=NOT_COVERED`
- `signs=NOT_COVERED`
- `chains=NOT_COVERED`

Evidence folder:

`tmp/beta35-floor-torch-support-finalization-fix-e5f15ec`
