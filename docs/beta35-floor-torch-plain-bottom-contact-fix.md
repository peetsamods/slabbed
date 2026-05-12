# Beta 3.5 Floor Torch Plain Bottom Contact Fix

- Date: 2026-05-11
- Base: `66ca74a` / `save/beta35-floor-torch-lowered-slab-placement`
- Evidence folder: `tmp/beta35-floor-torch-plain-bottom-contact-fix-66ca74a`
- Scope: `floor_torch_only`

## Failure isolated

Julia's live acceptance trace after `66ca74a` proved the previous
`supportDy=-1.0` lowered bottom-slab placement failure was fixed. The remaining
concrete contact gap was a different row:

- `torchPos=32,-54,80`
- `supportCandidateState=stone_slab[type=bottom]`
- `supportSourceType=PLAIN_STATE`
- `supportDy=-0.500000`
- previous `torchDy=-0.500000`
- previous `contactGap=0.500000`

## Mechanism

A floor torch on a bottom slab must place its model bottom at the slab's visible
top:

```text
torchDy = supportDy - 0.5
```

For a plain bottom slab already resolving to `supportDy=-0.5`, the correct
floor-torch dy is `-1.0`. The patch adds a narrow floor-torch bottom-slab dy
helper in `SlabSupport` that computes this contact dy from existing SlabSupport
source truth. It does not change global slab solidity, all item placement, wall
torches, lanterns, signs, or chains.

The tracer also classifies duplicate clicks on an already occupied torch target
as `OCCUPIED_TORCH_TARGET` instead of treating the `Fail[]` as a true empty
target placement failure.

## Proof

Focused gate:

```text
JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchPlainBottomContact=true" ./gradlew --no-daemon runClientGameTest --console plain
```

GREEN markers:

- `[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_GREEN]`
- `[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT] classification=PLACED_CONTACT_GREEN`
- `[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_SUMMARY] failureLayer=NONE`

Fixed values:

- `supportSourceType=PLAIN_STATE`
- `supportDy=-0.500000`
- `torchDy=-1.000000`
- `supportVisibleTopY=-55.000000`
- `torchModelBottomY=-55.000000`
- `contactGap=0.000000`
- `triadCoLocated=yes`
- `survival=SURVIVAL_GREEN`
- duplicate click: `classification=OCCUPIED_TORCH_TARGET`

## Validation

- `compileJava compileGametestJava`: GREEN
- Plain bottom contact proof: GREEN
- Lowered `supportDy=-1.0` placement regression: GREEN
- Live-shape/contact regression: GREEN
- Visual contact regression: BUILD SUCCESSFUL
- Player-like placement regression: GREEN
- Object triad regression: GREEN
- Default `runClientGameTest`: GREEN
- `git diff --check`: GREEN

## Scope guard

Sequence 16 / `PERSISTENT_LOWERED_SLAB_CARRIER` comfort-scan behavior was
deferred; it was not changed by this contact slice. Scope remains
`floor_torch_only`; `wall_torch`, `lantern`, `signs`, and `chains` remain
`NOT_COVERED`. Beta 3.5 release prep remains paused pending Julia live
acceptance. No release tag moved.

## Live acceptance addendum

Julia live acceptance at `226cc6c` (`save/beta35-floor-torch-plain-bottom-contact`)
confirms this row is accepted:

- `PLACEMENT_ATTEMPT_OK` for all 8 attempts.
- `PLAIN_STATE` support path is `PLACED_CONTACT_GREEN` with `contactGap=0.000000`.
- `supportDy=-0.500` plain-bottom support is green in live acceptance.
- `supportDy=-1.0` lowered-bottom-slab support remains green.
- Non-floor categories remain `NOT_COVERED`.
- Old wall-torch contact-gap rows for air support are outside `floor_torch_only`
  acceptance and do not gate this savepoint.
