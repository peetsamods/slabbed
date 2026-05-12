# Beta 3.5 Floor Torch Lowered Bottom-Slab Placement Fix

- Date: 2026-05-11
- Base: `fe7677a` / `save/beta35-live-torch-dual-tracer`
- Evidence folder: `tmp/beta35-floor-torch-lowered-slab-placement-fix-fe7677a`
- Scope: `floor_torch_only`

## Failure isolated

The live dual tracer split contact from placement. Existing floor-torch contact
was GREEN when the torch existed, including `supportDy=-1.000000` /
`torchDy=-1.500000` paths. The remaining live failure was player-like placement
on a lowered bottom slab support:

- `heldItem=minecraft:torch`
- `intendedSupportCandidateState=stone_slab[type=bottom]`
- `intendedSupportSourceType=PLAIN_STATE`
- `intendedSupportDy=-1.000000`
- `finalInteractResult=Fail[]`
- `torchBlockAppearedAfterAttempt=false`
- `classification=PLACEMENT_RESULT_UNKNOWN`
- `failureLayer=PLACEMENT_FAILURE_ON_LOWERED_BOTTOM_SLAB_SUPPORT`

## Mechanism

The failing live path is a lowered bottom-slab support that resolves to
`supportDy=-1.0` through Slabbed source truth but was still rejected by the
floor-torch support predicate. The patch adds one narrow SlabSupport authority:
`isLegalFloorTorchLoweredBottomSlabSupport(...)`.

The helper only applies to `floor_torch` on a bottom slab already proven as a
named lowered bottom-slab support (`COMPOUND_VISIBLE_SIDE_LOWER_SLAB` or the
owner-top lowered bottom-slab support path) with `supportDy=-1.0`. It does not
legalize wall torches, lanterns, signs, chains, all attachables, or global slab
solidity.

Because this support is now legal for placement, the existing contact law is
reused for it: the floor torch resolves to `torchDy=-1.500000`, keeping
`contactGap=0.000000`.

## Proof

Focused gate:

```text
JAVA_TOOL_OPTIONS="-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchLoweredSlabPlacement=true" ./gradlew --no-daemon runClientGameTest --console plain
```

GREEN markers:

- `[JULIA_BETA35_LIVE_TORCH_PLACEMENT_ATTEMPT] classification=PLACEMENT_ATTEMPT_OK`
- `[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_GREEN]`
- `[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_SUMMARY] failureLayer=NONE`

Measured fixed result:

- `intendedSupportCandidateState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false]`
- `intendedSupportDy=-1.000000`
- `finalInteractResult=Success[...]`
- `torchBlockAppearedAfterAttempt=true`
- `finalTorchState=Block{minecraft:torch}`
- `torchDy=-1.500000`
- `contactGap=0.000000`
- `survival=SURVIVAL_GREEN`
- `supportDyMinusHalfRegression=GREEN`

## Validation

- `compileJava compileGametestJava`: GREEN
- Lowered bottom-slab placement proof: GREEN
- Live-shape/contact regression: GREEN
- Visual contact regression: BUILD SUCCESSFUL, controlled contact remains `0.000000`
- Player-like placement regression: GREEN
- Object triad regression: GREEN
- Default `runClientGameTest`: GREEN
- `git diff --check`: GREEN

## Scope guard

Beta 3.5 release prep remains paused pending Julia live acceptance. No release
tag moved. Scope remains `floor_torch_only`; `wall_torch`, `lantern`, `signs`,
and `chains` remain `NOT_COVERED`.

## Live acceptance addendum

At `save/beta35-floor-torch-plain-bottom-contact` (`226cc6c`), this path is
now part of the live-accepted `floor_torch_only` bundle:

- Julia live trace classification: `PLACEMENT_ATTEMPT_OK` for `8/8` floor torch
  attempts during the acceptance run.
- The floor-torch lowered bottom-slab support row remains `GREEN`.
- Non-floor categories remain out of scope: `wall_torch`, `lantern`, `signs`,
  and `chains`.

## Follow-up plain-bottom contact fix

After this savepoint, Julia's live acceptance trace confirmed the
`supportDy=-1.0` lowered bottom-slab placement bug stayed GREEN, including
`finalInteractResult=Success[...]` and
`torchBlockAppearedAfterAttempt=true`.

The remaining concrete contact gap was a different row:
`supportSourceType=PLAIN_STATE`, `supportDy=-0.500000`, previous
`torchDy=-0.500000`, and previous `contactGap=0.500000`.

That row is now fixed by the plain-bottom contact slice. The focused proof
reports `supportDy=-0.500000`, `torchDy=-1.000000`,
`supportVisibleTopY=-55.000000`, `torchModelBottomY=-55.000000`,
`contactGap=0.000000`, `classification=PLACED_CONTACT_GREEN`, and
`failureLayer=NONE`. Duplicate occupied-target torch clicks are tracer
classified as `OCCUPIED_TORCH_TARGET`, not a true empty-target placement
failure.

This follow-up remains `floor_torch_only`; `wall_torch`, `lantern`, `signs`,
and `chains` remain `NOT_COVERED`. Beta 3.5 release prep remains paused pending
Julia live acceptance. No release tag moved.
