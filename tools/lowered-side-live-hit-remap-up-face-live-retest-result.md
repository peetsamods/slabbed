# Lowered Side Live Hit Remap — Guided Retest (UP-Face Edge Fix)

## Branch / base
- Branch: `guided-live-retest/lowered-side-live-hit-remap-up-face-verify`
- Head commit: `80ac773`
- Tag at start: `save/lowered-side-live-hit-remap-up-face-fix`

## Fix under test
- Narrow remap for slab-item clicks where the hit arrives as **UP** but is near the lowered block’s side edge, inferring the nearest horizontal face.

## Retest scope
- Check 2 — lowered full block side slab placement intent
- Check 3 — repeat-click / no ghost-face (blocked because Check 2 failed)

## Julia’s report
- Face tested: visual lowered side face (exact cardinal direction not specified in screenshot)
- Outline hugged lowered side: **YES**
- Check 2 result: **FAIL** — slab still appears at the wrong top/vanilla position instead of the selected lowered side.
- Check 3 result: **Not meaningfully re-tested** (previous failure still manifests; repeat click previously caused ghost face / culling.)
- Screenshot: provided (outline on lowered side, placement still top-style)

## Verdict
- **FAIL** — live behavior still places on the top/vanilla position even though the outline hugs the lowered side after the up-face edge remap fix.
