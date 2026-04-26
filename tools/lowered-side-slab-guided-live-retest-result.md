# Lowered Side Slab Guided Live Retest Result

## Branch / base
- Branch at start: `fix/lowered-side-placement-live-hit-remap`
- Head at start: `d7b6dbb`
- Tag at start: `save/lowered-side-live-aim-guidance`

## Retest scope
- Check 2: lowered full block side slab placement intent
- Check 3: repeat-click / no ghost-face behavior

## Result summary
- Face tested: visual lowered side face from Julia’s screenshot; exact cardinal face not specified in chat.
- Outline hugged lowered side: **YES**
- Check 2: **FAIL**
- Placement result: slab placed at the **top / vanilla position** of the lowered full block despite the lowered side being selected.
- Check 3: **FAIL**
- Second click result: **ghost face / face culling**

## Verdict
- Production placement intent / live-hit remap failure is now confirmed.
- Ray acquisition is **not** the blocker in this live case because the outline clearly hugged the lowered side.

## Evidence
- Julia-provided screenshot in chat showing the outline hugging the lowered side face while placement landed in the wrong/top location.
