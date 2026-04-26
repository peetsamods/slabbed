# Lowered Side Placement — Live Hit Remap Values Diagnosis

## Base
- Base branch: `diagnose/lowered-side-live-hit-remap-values`
- Base commit: `87a5a42`
- Base tag: `save/lowered-side-live-hit-remap-diagnosis`
- Live retest note: `tools/lowered-side-slab-guided-live-retest-result.md`

## What is already known
- Julia’s guided live retest showed the outline visibly hugging the lowered side face.
- Check 2 still failed: the slab appeared in the wrong top / vanilla position.
- Check 3 still failed downstream.
- Ray acquisition is therefore not the blocker.

## What the current code shows
- The narrow placement intent remap only applies when:
  - the held item is a slab,
  - the clicked face is horizontal,
  - the clicked block is an ordinary solid lowered full block,
  - block entities are excluded,
  - crafting tables are excluded,
  - the block’s lowered offset is exactly `-0.5d`.
- The remap currently rewrites the hit Y to `targetPos.getY() + 0.499d`.

## What is still not proven
- The exact runtime values entering the remap path for the failing live click.
- Whether the remap guard matched in the failing screenshot scenario.
- Whether the remapped hit Y or placement context position is the part that diverges.
- Whether the vanilla placement interpretation is overriding the intended placement after remap.

## Diagnosis
- The failing layer is still the **placement intent / live-hit remap** layer.
- The exact sub-defect is **not yet proven** from the current evidence.
- I should **not** patch the production remap yet without a tiny values audit or a direct runtime trace of the failing live click.

## Next slice
- `diagnose/lowered-side-live-hit-remap-runtime-values`
