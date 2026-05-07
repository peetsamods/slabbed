# Beta4 stone-held side-slab retarget proof gap

Status: proof gap only. No gameplay fix is included here.

Live trace used:

- `tmp/beta4-stone-held-side-slab-retarget-red-proof-948b293/run_logs_latest_log.extract.txt:58`
- held item: `block.minecraft.stone`
- `heldIsSlab=false`
- initial vanilla/pre-retarget owner: ordinary stone at `16,-59,-1`
- final owner: lower `stone_slab` at `14,-58,0`
- `sideSlabRetargetFired=true`

What failed in harness:

- First stone-held proof WIP emitted `[BETA4_STONE_HELD_SIDE_SLAB_RETARGET_RED]` but stopped as unfaithful with `reason=source-truth-gap`; the harness side slab was `dy=0.0`.
- Source-truth repair made the harness side slab a real lowered `stone_slab[type=bottom]` with `dy=-0.5`, `anchored=false`, and `persistentLoweredSlabCarrier=false`, but still missed the live branch with `reason=live-branch-not-reproduced`, `sideSlabRetargetFired=false`, `candidateOwnerClass=NO_RESCUE`.
- Coordinate-exact replay using live positions and recorded eye/yaw/pitch still stopped before ownership proof because the legal fixture source path produced `persistentLoweredSlabCarrier=true` on the lowered side slabs, while the live trace does not record enough persistent/source-path truth to prove that fixture matches Julia's world.

Why no approximate proof was kept:

- Nearby fixture guessing already produced two unfaithful failures.
- The live trace proves the player-facing bug, but it does not fully specify the source topology needed to recreate the exact lowered, non-fake slab truth in automation.
- Committing a RED marker that stops on source-truth or branch mismatch would be a false proof.

Required recorder improvement before another proof attempt:

- Emit the full replay tuple in one line: eye, look, yaw, pitch, ray start/end, initial hit vec, final hit vec, crosshair target, outline target, held item, initial/final/candidate positions, and every candidate raycast/outline hit or miss result.
- Emit source-truth facts for initial, final, candidate, and target-neighbor blocks: state, dy, anchored, lowered, `persistentLoweredSlabCarrier`, slab type, solid/full-cube flags, and relevant support/source positions.
- Include enough surrounding block positions to rebuild the exact live source path, not only the final target neighbors.

Release remains blocked until either a coordinate-exact RED proof exists or Julia explicitly accepts a precise-targeting fallback exploration.
