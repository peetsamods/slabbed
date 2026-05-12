# Beta 3.5 Live Hitbox Gate RED

Audit/proof slice for Julia's remaining live Beta 3.5 object failures after the fence/wall family visual-support fix.

Operating base: HEAD `edbba27` / `save/beta35-fence-wall-family-fix` on `integrate/phase19-into-side-slab-top-support`.

Evidence folder: `tmp/beta35-live-hitbox-gate-red-edbba27/`.

Gate: `-Dslabbed.beta35LiveHitboxGateRed=true`

Markers:

- `JULIA_BETA35_LIVE_HITBOX_GATE_MATRIX_START`
- `JULIA_BETA35_LIVE_HITBOX_GATE_ROW`
- `JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY`

No production gameplay or render fix was implemented. No release audit was run. No release tag was moved.

## Result

Focused proof outcome: RED.

`JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY outcome=RED rows=5 red=2 pending=3 green=0 fenceHitboxClassification=PENDING fenceHitboxFailureLayer=PROOF_HARNESS_GAP wallHitboxClassification=PENDING wallHitboxFailureLayer=PROOF_HARNESS_GAP anvilHitboxClassification=PENDING anvilHitboxFailureLayer=PROOF_HARNESS_GAP fenceGateClosedClassification=FENCE_GATE_CONTACT_GAP fenceGateClosedFailureLayer=FENCE_GATE_CONTACT_GAP fenceGateOpenClassification=FENCE_GATE_CONTACT_GAP fenceGateOpenFailureLayer=FENCE_GATE_CONTACT_GAP`

Rows:

- `minecraft:cherry_fence`, connected: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `collisionCoLocated=yes`, `triadCoLocated=yes`, `renderDyApplied=yes`, `classification=PENDING`, `failureLayer=PROOF_HARNESS_GAP`.
- `minecraft:stone_brick_wall`, connected: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `collisionCoLocated=yes`, `triadCoLocated=yes`, `renderDyApplied=yes`, `classification=PENDING`, `failureLayer=PROOF_HARNESS_GAP`.
- `minecraft:anvil`: `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `collisionCoLocated=yes`, `triadCoLocated=yes`, `renderDyApplied=yes`, `classification=PENDING`, `failureLayer=PROOF_HARNESS_GAP`.
- `minecraft:cherry_fence_gate` closed: `supportDy=-1.000000`, `objectDy=-0.500000`, `expectedContactDy=-1.500000`, `contactGap=1.000000`, `collisionCoLocated=no`, `triadCoLocated=no`, `classification=FENCE_GATE_CONTACT_GAP`, `failureLayer=FENCE_GATE_CONTACT_GAP`.
- `minecraft:cherry_fence_gate` open: `supportDy=-1.000000`, `objectDy=-0.500000`, `expectedContactDy=-1.500000`, `contactGap=1.000000`, `collisionCoLocated=no`, `triadCoLocated=no`, `interactionResult=Success[...]`, `classification=FENCE_GATE_CONTACT_GAP`, `failureLayer=FENCE_GATE_CONTACT_GAP`.

## Classification

Fence/wall visuals and shape math are improved, including connected `FenceBlock` and `WallBlock` rows, but Julia's live hitbox/collision complaint is not closed by this proof. The bounded harness saw co-located model/outline/raycast/collision bounds, while the live inspect log still includes target misses and player-facing hitbox suspicion. Classification remains `PROOF_HARNESS_GAP`, not GREEN.

Anvil contact/triad remains green in automation, but this slice does not prove Julia's live hitbox feel false. The new bounded row did not reproduce an anvil collision mismatch, so classification is `PROOF_HARNESS_GAP`; release remains blocked until a live-faithful hitbox/collision proof closes or reproduces it.

Fence gates are a separate category from the `FenceBlock`/`WallBlock` family. The closed and open `cherry_fence_gate` rows place and survive over the lowered bottom slab, but only inherit `objectDy=-0.500000` instead of the required `expectedContactDy=-1.500000`, leaving `contactGap=1.000000`. This is a release-blocking `FENCE_GATE_CONTACT_GAP`.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveHitboxGateRed=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`; RED/PENDING markers emitted as documented above

## Next Slice

Recommended next implementation slice: add a fence-gate-specific contact/support law and open/closed proof. Keep anvil and fence/wall live-hitbox reproduction as a separate proof-harness slice unless Julia supplies a sharper local repro path.

## Follow-Up: Cherry Gate Contact Fix

The next slice implemented only `minecraft:cherry_fence_gate` contact/support behavior. It did not expand to all fence gates, panes, fence/wall hitboxes, anvil hitboxes, or any door/trapdoor/sign/lantern/chain/end-rod/redstone/rail category.

Focused proof:

- Gate: `-Dslabbed.beta35FenceGateContact=true`
- Summary: `JULIA_BETA35_FENCE_GATE_CONTACT_SUMMARY outcome=GREEN failureLayer=NONE rows=2 closedClassification=GREEN closedFailureLayer=NONE openClassification=GREEN openFailureLayer=NONE scope=cherry_fence_gate_only fenceWallHitboxRows=UNCHANGED_PENDING anvilHitboxRows=UNCHANGED_PENDING panes=NOT_COVERED releaseAudit=NOT_RUN releaseTagMoved=false`

Post-fix live-hitbox audit:

- `JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY outcome=PENDING rows=5 red=0 pending=3 green=2 fenceHitboxClassification=PENDING fenceHitboxFailureLayer=PROOF_HARNESS_GAP wallHitboxClassification=PENDING wallHitboxFailureLayer=PROOF_HARNESS_GAP anvilHitboxClassification=PENDING anvilHitboxFailureLayer=PROOF_HARNESS_GAP fenceGateClosedClassification=GREEN fenceGateClosedFailureLayer=NONE fenceGateOpenClassification=GREEN fenceGateOpenFailureLayer=NONE`

The original RED rows above remain the historical audit result at `edbba27`; after the cherry-gate fix, the remaining live-hitbox matrix blocker is the fence/wall/anvil proof-harness gap, not fence gate contact.
