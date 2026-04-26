# Lowered Side Placement — Natural Aim Miss Diagnosis

## Base
- Base branch: `diagnose/lowered-full-block-hitbox-live-raycast`
- Base commit: `0478600`
- Base tag: `save/lowered-side-placement-natural-angle-fail-repro`

## Findings
- The east-face confirmation still passes.
- The south-facing natural approach misses in the current audit.
- The eye-height horizontal probe also misses.
- The repeat-click path fails because the first south-facing click never produces a valid hit, so repeat behavior is not independently proven broken.

## Diagnosis
- This slice does **not** prove a south-face production bug.
- The natural south camera is positioned high above the lowered visual block, and the current audit raycast is effectively horizontal.
- Given the reported positions, the south ray starts well above the lowered block’s top edge, so a horizontal ray should miss by geometry.
- That makes the current failure primarily a **proof-harness aim setup issue** plus a **live-test instruction issue** unless a later pitch sweep proves otherwise.

## Conclusion
- South face: **missed due to eye/pitch geometry in the current proof setup**.
- Eye-height horizontal aim: **misses as expected from the current camera height**.
- Repeat-click: **not independently diagnosable; it is blocked by the first miss**.
- Production fix next: **no**.

## Next slice
- `diagnose/lowered-side-natural-pitch-boundary`
