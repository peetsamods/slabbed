# Lowered Side Placement — South Face Ray Boundary Diagnosis

## Base
- Base branch: `diagnose/lowered-side-natural-pitch-boundary`
- Base commit: `8339b95`
- Base tag: `save/lowered-side-south-pitch-fail-repro`

## Evidence summary
- East-face lowered-side path reaches the lowered block and places correctly.
- South-facing natural approach misses at horizontal, slight-down, and center-down pitches.
- The steeper downward probe does not reach the lowered south face; it hits the **up face of the full block** instead.
- The south-facing repeat-click path is blocked by the first miss, so repeat behavior is downstream of aim acquisition.

## Exact failing layer
- **Ray acquisition / crosshair target geometry**.
- The south-facing ray never reaches the lowered south face within the tested natural camera geometry.
- Placement remap is **not** the failing layer, because it is only exercised after a valid hit is acquired.
- Shape offset itself is **not** disproven; the issue is that the south ray path meets the top face band first or misses entirely before it can target the lowered south side.

## Why east vs south differs
- East-face success uses a geometry where the ray enters the lowered block’s side-face band cleanly.
- South-facing natural aim starts too high and/or too shallow for the ray to intersect the lowered south face before the top face band.
- This is a camera/ray-vector intersection problem, not evidence of a remap bug.

## Recommendation
- A production fix is **not yet warranted from this slice alone**.
- The next step should be a guided live retest with a more deliberate south-facing downward aim, or a focused ray-boundary diagnosis if live play still cannot reach the lowered south face.

## Stop conditions for the next slice
- Do not change SlabSupport semantics.
- Do not broaden crosshair rescue.
- Do not change model, outline, or raycast logic without a directly observed hit/reach failure.
- If a future live test still cannot reach the south lowered face with precise aim, then move to a focused production diagnosis/fix for ray acquisition.

## Next slice
- `guided-live-retest/lowered-side-south-face-aim-instructions`
