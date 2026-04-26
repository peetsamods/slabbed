# Lowered Side Placement — Live Hit Remap Diagnosis

## Base
- Base branch: `fix/lowered-side-placement-live-hit-remap`
- Base commit: `db1c0d7`
- Live retest note: `tools/lowered-side-slab-guided-live-retest-result.md`

## Evidence summary
- Julia’s live retest showed the outline visibly hugging the lowered side face.
- Check 2 still failed: the slab placed at the top / vanilla position of the lowered full block.
- Check 3 still failed downstream with ghost-face / face-culling behavior.
- That means ray acquisition is **not** the blocker in the live case.

## Diagnosis status
- The live hit clearly reaches the placement stage.
- The existing placement remap mixin is the correct narrow layer to inspect.
- However, the exact defect is **not yet proven** from code + current evidence alone.

## What is known from code
- Slab items only.
- Horizontal face clicks only.
- Ordinary solid lowered full blocks only.
- Block entities and crafting tables are excluded.
- The remap currently rewrites the hit Y to `targetPos.getY() + 0.499d`.

## What remains ambiguous
- Whether the live hit actually satisfies the remap guard at runtime.
- Whether the fixed `0.499d` remap Y is the wrong value for the live south-side case.
- Whether `ItemPlacementContext` is reinterpreting the remapped hit in a way the current remap does not anticipate.

## Conclusion
- The failure is in the **placement-intent / live-hit remap layer**, but the exact sub-defect is still unproven.
- I should **not** patch the remap yet without a tiny audit of the actual values entering the remap path.

## Next slice
- `diagnose/lowered-side-live-hit-remap-values`
