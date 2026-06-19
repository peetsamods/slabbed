# RC3 — compound-side slab marker authoring + midline split (live RED-verify first)

> Status **2026-06-17 PM**, HEAD `dc4bec2d`. RC1 + RC2 + RC2-gaps are DONE. RC3 is **analyzed, not
> implemented** — it must be RED-verified LIVE before touching code, because GAP-1 changed the picture.

## What RC3 claims (from the 8-agent audit, `WYSIWYG-PLACEMENT-AUDIT.md` Root Cause 3)

A slab placed against the SIDE of a vertical compound (−1.0) stack lands at −0.5 instead of −1.0, and the
TOP/BOTTOM type can be wrong for a lower-half aim. Two code facts (both CONFIRMED still present at `dc4bec2d`):

1. `findLegalCompoundSlabRemap` (`SlabSupport.java`) returns `legal()=true` with reasons
   **`COMPOUND_BELOW_LANE_SIDE_SLAB`** (`:1075`) and **`COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB`**
   (`:1094`). The marker-write switch in `BlockItemPlacementIntentMixin.java:1128-1140` handles ONLY
   `COMPOUND_VISIBLE_SIDE_{LOWER,UPPER,DOUBLE}_SLAB` — the two reasons fall through with **no
   `COMPOUND_VISIBLE_SIDE_*_INTENT`** authored. (Verified: grep shows only the 3 handled reasons.)
2. `compoundBelowLaneResultType` (`:1117`) splits TOP/BOTTOM at `hitY >= sourcePos.getY()` (the grid top),
   not the VISUAL mid used by `isCompoundVisibleSideUpperHit/LowerHit` (`:1138/:1124`, which use
   `sourcePos.getY() + sourceDy`). So a lower-half aim can mint the wrong slab TYPE.

## Why GAP-1 changes the picture (READ THIS BEFORE IMPLEMENTING)

The audit said the un-marked slab "falls to the geometric `isAdjacentSideSlabLowered` branch → −0.5." But
the RC2 GAP-1 fix (`dc4bec2d`) inserted **`adjacentLoweredSideMagnitude` BEFORE that fallback** in the slab
branch of `getYOffsetInner`, air-gated:

- compound-side slab with **AIR below** → now reads **−1.0** via GAP-1 (reads the compound neighbour's true
  magnitude). This is the WYSIWYG-correct outcome and is likely what most of RC3's reported "−0.5" cases were.
- compound-side slab with **SOLID below** → stays **0.0** by the NEVER-POP rail (also WYSIWYG-correct: a slab
  on its own flush ground beside a lowered block must not pop).

**So RC3's residual after GAP-1 is probably NOT the dy magnitude — it is the TARGETING/TYPE layer:** which
cell the slab lands in (the intent marker also steers placement) and the TOP/BOTTOM type via the midline
split. That is the `useOn` intent path, not `onPlaced`/`getYOffset`.

## Live RED-verify (do this FIRST — do not implement blind)

In the `TEST_ SLABBED 26.1.2` Modrinth profile (re-set keybind `Use`→`r` for computer-use driving; it is
currently reverted to `mouse.right` for normal play):

1. Build a compound −1.0 stack: `/setblock` stone / bottom-slab / stone (the top stone reads −1.0; confirm
   with `/slabdy` = −1.000).
2. Place a slab AGAINST THE SIDE of the top compound stone (right-click the side face), aiming **lower half**
   then **upper half** separately. For each: read `/slabdy` on the placed slab AND eyeball the position vs the
   crosshair.
   - If both halves already read **−1.000** and sit where aimed → **GAP-1 absorbed RC3's dy; only the TYPE
     (TOP vs BOTTOM) and/or landing-cell may remain.** Narrow the fix to the midline split + (if needed) the
     marker for correct cell/type.
   - If a half reads **−0.500** or the slab lands in the wrong cell → the **marker authoring is genuinely
     needed**; apply the audit fix below.

## The fix (apply ONLY for whatever the live RED-verify proves is still broken)

- **Marker authoring** — in `BlockItemPlacementIntentMixin.java:1128-1140`, extend the switch: when
  `remapDecision.reason()` is `COMPOUND_BELOW_LANE_SIDE_SLAB` or
  `COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB`, set `COMPOUND_VISIBLE_SIDE_UPPER_INTENT` when
  `resultType()==TOP` else `COMPOUND_VISIBLE_SIDE_LOWER_INTENT` (sourcePos + candidatePlacementPos), and make
  the place-RETURN predicate persist that candidate so `getYOffsetInner` reads −1.0.
- **Midline split** — fix `compoundBelowLaneResultType` (`:1117`) to split at the visual mid. NOTE the audit's
  literal `sourcePos.getY() - 0.5` assumes a specific `sourceDy`; the geometrically-correct form mirrors
  `isCompoundVisibleSideUpperHit`: split at `sourcePos.getY() + sourceDy + 0.5` (sourceDy = the owner's −1.0).
  Verify the exact threshold live against both halves; do not blind-copy the audit literal.

## Coverage to add before declaring RC3 closed

Per the audit: a NEW real-`useOn`-driven gametest (NOT just `authorBlock`/`onPlaced`) asserting **dy == −1.0
for BOTH halves** and the correct slab TYPE. The current 46 tests do not exercise the `useOn` remap path.
GAP-3 (full-block-to-full-block / mixed fence chains beside a compound) stays deferred.

## Regression watch

The full-block side case routes through the LOWER/UPPER branches (`:986-1043`), unaffected. The two reasons
only arise for slab items at `legalLaneCount==0`. The midline change can flip TOP/BOTTOM near the boundary —
verify against the compound resting-state contract tests + live both halves.

## Headless RED-verify RESULT (2026-06-18) — `Slabbed2612UseOnPlacementTest`

The "Coverage to add" gametest now EXISTS and runs (real `useOn` via a mock player + hand-built
`UseOnContext`; 7 methods, all green). It answers the dy half of RC3 headlessly:

- **dy is DONE.** A slab placed (real `useOn`) against the side of a compound −1.0 stack reads **dy=−1.000
  for BOTH the upper-half and lower-half aim** — GAP-1 (`dc4bec2d`) absorbed RC3's dy magnitude exactly as
  this plan hypothesised. RC2's −0.5 side case is likewise confirmed (both halves).
- **TYPE residual is REAL (not a harness artifact).** Every side-merge placement mints **`type=TOP`
  regardless of the aimed half**, for both the −0.5 and −1.0 cases. A CONTROL (`controlSlabSideTypeTracksHitHalf`)
  proves the harness reproduces vanilla's hit-based type on a flush block (upper→TOP, lower→BOTTOM), so the
  always-TOP is the `compoundBelowLaneResultType` midline split, exactly as predicted (`:1117`).

**So RC3's residual is now precisely scoped, and it is NOT the dy:**
1. **Type policy + midline split** — decide the correct TOP/BOTTOM for an upper- vs lower-half aim at a −1.0
   side, then fix `compoundBelowLaneResultType` to that threshold. The test logs the current type (`USEON-FP`
   lines) but does NOT assert it (policy is unsettled — do not enshrine a guess). Once decided, add the type
   assertion to the two `useOnSlabBesideCompoundStack{Upper,Lower}Half` methods.
2. **Client cell-targeting** — which cell the crosshair actually hits on a visually-lowered block. This is the
   only genuinely-live part left (P4 / raycast); the server remap above is now covered headlessly.

The marker-authoring fix in "The fix" above is likely UNNECESSARY for dy (GAP-1's geometric path already
yields −1.0 with no marker); it may still matter for landing-cell/type. Re-scope before implementing.
