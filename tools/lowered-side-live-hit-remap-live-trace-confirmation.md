# Lowered Side Live Hit Remap — Live Trace Confirmation

## Branch / head at capture
- Branch: `diagnose/lowered-side-live-hit-remap-up-face-remaining-gap`
- Head: `edce12a`

## Trace flag used
- `-Dslabbed.debug.sbsb=true`

## Trace file
- `build/run/clientGameTest/screenshots/lowered_side_live_hit_remap_runtime_values.json`

## Captured values
- hitFace: `up`
- hit: `8.5000 / 201.5000 / 0.8515`
- local hit X/Z: X centered, Z toward south edge
- remapGuardMatched: `true`
- failedGuard: `none`
- remapMode: `up_face_edge`
- effectiveRemapFace: `south`
- placementContextPos: `8, 201, 1`
- finalPlacedBlockPos: `8, 201, 1`
- finalPlacedBlockId: `minecraft:stone_slab`
- finalPlacedSlabType: `bottom`
- verdict: `guard_matched_expected_side_placement`

## Conclusion
The live click did reach the fixed UP-face edge remap path and placed the slab at the intended side position.

## Interpretation
The prior visual fail likely came from stale client/build state or a mismatched retest click, not from the fixed remap path failing.

## Next step
Final normal guided live retest from a freshly launched client/build without making code changes.
