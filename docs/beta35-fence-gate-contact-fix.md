# Beta 3.5 Cherry Fence Gate Contact Fix

Implementation/proof slice for the live `minecraft:cherry_fence_gate` support/contact gap.

Operating base: HEAD `0916b36` / `save/beta35-live-hitbox-gate-red` on `integrate/phase19-into-side-slab-top-support`.

Evidence folder: `tmp/beta35-fence-gate-contact-fix-0916b36/`.

Scope:

- Implemented only `minecraft:cherry_fence_gate` contact/support behavior.
- Did not add all-fence-gate family support.
- Did not fix fence/wall/anvil hitboxes.
- Did not implement panes.
- Did not touch door/trapdoor/sign/lantern/chain/end-rod/redstone/rail categories.
- Did not run release audit or move release tags.

## Production Change

`SlabSupport` now contains a cherry-fence-gate-only predicate and contact dy path. When a cherry fence gate sits on a valid lowered bottom-slab support, it uses the same contact math as the fence/wall family: `supportDy=-1.000000` produces `objectDy=-1.500000`.

`SlabSupportStateMixin` uses that same predicate for lowered shape offset and empty-raycast fallback, so outline/raycast/collision stay coherent with the model/contact proof. The closed gate keeps vanilla's taller collision volume; the proof treats that as category-valid when the bottom and X/Z footprint align. The open gate keeps empty collision and is category-valid when outline/raycast/model remain co-located.

`OffsetBlockStateModel` was not modified.

## Focused Proof

Gate:

`-Dslabbed.beta35FenceGateContact=true`

Markers:

- `JULIA_BETA35_FENCE_GATE_CONTACT_GREEN`
- `JULIA_BETA35_FENCE_GATE_CONTACT_ROW`
- `JULIA_BETA35_FENCE_GATE_CONTACT_SUMMARY`

Summary:

`JULIA_BETA35_FENCE_GATE_CONTACT_SUMMARY outcome=GREEN failureLayer=NONE rows=2 closedClassification=GREEN closedFailureLayer=NONE openClassification=GREEN openFailureLayer=NONE scope=cherry_fence_gate_only fenceWallHitboxRows=UNCHANGED_PENDING anvilHitboxRows=UNCHANGED_PENDING panes=NOT_COVERED releaseAudit=NOT_RUN releaseTagMoved=false`

Closed row:

- `supportDy=-1.000000`
- `objectDy=-1.500000`
- `expectedContactDy=-1.500000`
- `contactGap=0.000000`
- `collisionCoLocated=category_valid_closed_tall`
- `triadCoLocated=yes`
- `placementResult=PLACEMENT_GREEN`
- `survivalResult=SURVIVAL_GREEN`
- `classification=GREEN`
- `failureLayer=NONE`

Open row:

- `supportDy=-1.000000`
- `objectDy=-1.500000`
- `expectedContactDy=-1.500000`
- `contactGap=0.000000`
- `collisionCoLocated=category_valid_open_empty`
- `triadCoLocated=yes`
- `placementResult=PLACEMENT_GREEN`
- `survivalResult=SURVIVAL_GREEN`
- `interactionResult=Success[...]`
- `classification=GREEN`
- `failureLayer=NONE`

## Live Hitbox Matrix After Fix

`-Dslabbed.beta35LiveHitboxGateRed=true` now reports:

`JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY outcome=PENDING rows=5 red=0 pending=3 green=2 fenceHitboxClassification=PENDING fenceHitboxFailureLayer=PROOF_HARNESS_GAP wallHitboxClassification=PENDING wallHitboxFailureLayer=PROOF_HARNESS_GAP anvilHitboxClassification=PENDING anvilHitboxFailureLayer=PROOF_HARNESS_GAP fenceGateClosedClassification=GREEN fenceGateClosedFailureLayer=NONE fenceGateOpenClassification=GREEN fenceGateOpenFailureLayer=NONE`

The remaining three pending rows are fence, wall, and anvil hitbox/collision proof-harness gaps. They were not fixed in this slice.

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceGateContact=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveHitboxGateRed=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallFamilyFix=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `./gradlew --no-daemon runClientGameTest --console plain` -> `BUILD SUCCESSFUL`
- `git diff --check` -> clean

Beta 3.5 release remains blocked. No release audit was run. No release tag was moved.
