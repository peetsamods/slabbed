# Lowered Side Placement — South Pitch Boundary Diagnosis

## Base
- Base branch: `diagnose/lowered-side-natural-pitch-boundary`
- Base commit: `da5536f`
- Base tag: `save/lowered-side-natural-aim-boundary-diagnosis`

## Findings
- Horizontal south aim still misses the lowered side.
- Slight and moderate downward south pitches also miss the lowered side.
- The steepest downward pitch no longer misses, but it hits the **up face of the full block**, not the lowered south side face.
- Because of that, the sweep still does **not** prove a valid south-face lowered-side hit/placement path.

## Diagnosis
- This is **not** a confirmed proof-harness-only issue anymore.
- The south-facing lowered side appears unreachable by the tested natural camera geometry, and the steepest pitch only reaches the full block top face.
- The next step should be a focused production diagnosis of the south-face ray/outline interaction, or a manual live aim repro using a more precise downward angle if the live feel path differs.

## Conclusion
- South face: **still not hit**.
- Eye-height boundary: **horizontal and shallow-down aim miss as expected**.
- Clear-down pitch: **hits the wrong face (top of the full block)**.
- Repeat-click: **still downstream of the first miss**.
- Production code should be fixed next: **likely yes, but only after focused south-face diagnosis**.

## Next slice
- `diagnose/lowered-side-south-face-ray-boundary`
