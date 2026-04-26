# Adjacent Side Slab dy Fix — Live Failure Root-Cause Audit

## 1. State

- Branch: `fix/adjacent-side-slab-dy-inherit`
- HEAD: `8dbda59`
- Tag at HEAD: `save/adjacent-side-slab-dy-inherit-fix`
- Tree: clean
- Production code change in this candidate: `SlabSupport.getYOffsetInner` only

## 2. Authoritative live evidence

From the live `[SBSB-TRACE][RETURN]` after Julia's south-face click on the lowered stone:

```
side=CLIENT item=minecraft:birch_slab
hitPos = 7,-59,-54  hitState=stone   dyHit  = -0.5
placePos = 7,-59,-53                  dyPlace = 0.0
placeState = birch_slab[type=bottom]
result = Success
```

The placed birch slab at `7,-59,-53` reports `dyPlace = 0.0`. The trace's `dyPlace`
is computed as `SlabSupport.getYOffset(world, placePos, placeState)`. **This proves
the new adjacent-side-slab branch did NOT trigger on the live placement.**

Runtime audit JSON `run/screenshots/lowered_side_live_hit_remap_runtime_values.json`
confirms placement is at the correct side position with `verdict =
guard_matched_expected_side_placement`. Placement intent is fine. The defect is
in the dy path.

## 3. Why the new branch didn't fire

The added rule (in `SlabSupport.getYOffsetInner`):

```java
if (isBottomSlab(state) && isBottomSlab(below)) {
    for (Direction dir : Direction.Type.HORIZONTAL) {
        ...
        if (getYOffset(world, neighborPos, neighbor) == -0.5) return -0.5;
    }
}
```

requires **the block directly below the placed slab to also be a bottom slab**.

For Julia's live click:
- Placed slab at `7,-59,-53`
- `pos.down() = 7,-60,-53`
- The support slab is only at `7,-60,-54` (under the lowered stone), NOT at `7,-60,-53`
- So `isBottomSlab(below) = FALSE` → rule short-circuits → returns 0.0
- The horizontal-neighbour check (which would have detected the lowered stone at `7,-59,-54`) is never reached.

This is the realistic single-pillar case the user actually plays.

## 4. Why automated proof passed

`runLoweredSideSlabPlacementRepro` setup (gametest, around lines 943-951):

```java
world.setBlockState(reproSupportPos, STONE_SLAB[BOTTOM], NOTIFY_LISTENERS);
world.setBlockState(reproFullPos, STONE, NOTIFY_LISTENERS);
world.setBlockState(reproPlacePos, AIR, NOTIFY_LISTENERS);
world.setBlockState(reproPlacePos.up(), AIR, NOTIFY_LISTENERS);
// reproPlacePos.down() is NEVER set
```

The gametest assertions (lines 993-1054) check only:

- `placed.isOf(Blocks.STONE_SLAB)` — block id
- `placed.get(SlabBlock.TYPE) == BOTTOM` (then `DOUBLE` after repeat) — slab type
- `above.isAir()` — no upward stack
- screenshot artifacts written

**None of these assertions call `getYOffset` on `reproPlacePos`. None verify visual
dy alignment. None compare rendered model Y to the lowered stone's visual Y.**

So:
- The candidate fix never fires in the gametest (same `pos.down()` not-a-slab condition).
- The gametest's existing assertions remain green either way.
- The proof bundle and its verifier check artifact existence and structural schema, not visual alignment.

The proof gap is **"correct block state assertions cannot prove correct visual dy"**.

## 5. Audit answers to the required questions

1. **Live placed slab actually getting dy=-0.5?** No. `dyPlace=0.0` in trace.
2. **Slab model render uses SlabSupport.getYOffset?** Yes — `BlockModelDyTranslateMixin` and `OffsetBlockStateModel`.
3. **Outline/raycast use the same dy?** Yes — `SlabSupportStateMixin.slabbed$offsetOutline` and `slabbed$offsetRaycast`. Triad is intact; the issue is dy is 0.
4. **New branch reachable?** Code path is reachable but its inner condition `isBottomSlab(below)` is false in the live geometry, so the body never runs.
5. **Condition correct for live position?** **No.** It assumes the support slab extends laterally; live single-pillar geometry violates this.
6. **Lateral/diagonal support relationship missed?** Yes — the only lowered "owner" is the horizontal neighbour, not anything below.
7. **Live placement creates a different state than the gametest assumes?** No, block states match. The mismatch is in geometry-around-placePos.
8. **Proof bundle asserts only block state, not rendered dy?** Confirmed. No dy or screenshot pixel assertion on `reproPlacePos`.
9. **Fix solves render dy only, while bug is placement?** No — placement remap is correct (live trace verdict is `guard_matched_expected_side_placement`). The bug is purely visual dy not firing.
10. **Two bugs to separate?** No — placement is fine; this is a single dy bug. But proof needs a separate fix (assert dy).

## 6. Failing layer

**Production code (model/outline/raycast triad dy)** AND **proof gap (no dy assertion)**.

Specifically:
- Production: the `isBottomSlab(below)` precondition is too strict. The realistic geometry for a lowered-side placement is a single lowered block whose support slab does NOT extend laterally. The rule must trigger from the horizontal-neighbour signal alone.
- Proof: `runLoweredSideSlabPlacementRepro` must assert `getYOffset(world, reproPlacePos, placedState) == -0.5` so a future regression is caught automatically.

## 7. Recommended next slice

**Option E (rollback) + then proof-first re-approach.**

### Step A — Roll back the candidate fix
Branch: `revert/adjacent-side-slab-dy-inherit`
Tag: `save/adjacent-side-slab-dy-inherit-revert`

The current rule cannot fire in realistic geometry. Keeping it is dead code that obscures future audits. Revert via `git revert 8dbda59` on a new branch off `8dbda59`.

### Step B — Add proof gate FIRST
Branch: `test/lowered-side-adjacent-slab-dy-assertion`
Tag: `save/lowered-side-adjacent-slab-dy-assertion`

Add to `runLoweredSideSlabPlacementRepro` (after both first-click and repeat-click assertions):

```java
double dy = SlabSupport.getYOffset(mc.world, reproPlacePos,
        mc.world.getBlockState(reproPlacePos));
if (dy != -0.5) {
    throw new RuntimeException(
        "adjacent side slab visual dy expected -0.5, got " + dy
        + " at " + reproPlacePos.toShortString());
}
```

This must FAIL on `8dbda59` (and on the reverted base) to prove the assertion is real and not green-by-accident. Only then is the proof gate trustworthy.

### Step C — Re-design and re-implement dy rule
Branch: `fix/adjacent-side-slab-dy-inherit-v2`
Tag: `save/adjacent-side-slab-dy-inherit-v2`

Drop the `isBottomSlab(below)` precondition. New rule candidate (proof-driven, not theory-driven):

> A `SlabType.BOTTOM` slab returns dy=-0.5 when:
> - At least one horizontal neighbour at the same Y is a non-slab solid with `getYOffset == -0.5`
> - AND the slab itself does not already qualify for any other dy path
> - AND `pos.up()` is not occupied by a non-slab block (avoid double-shift cascading)

Risks remaining (must be explicitly tested):
- a player builds a normal floor next to a decorative lowered block and any same-Y bottom slab they place will offset
- if the lowered neighbour is later broken, a neighbour-update revalidation is needed to refresh dy

This is a deliberately broader rule than the design note proposed. The previous narrow rule failed live. The next rule must accept this trade-off; otherwise the visual will never align in single-pillar geometry, which is the dominant real-world case.

## 8. Recommended slice classification

**A + D + E** — screenshot/proof expansion, proof assertion correction, AND rollback the candidate fix.

Order:
1. E first (revert dead code)
2. D second (add dy assertion, watch it fail on the reverted base — proves the gate)
3. C third (new dy rule passes the new gate AND live retest)

Do NOT skip step D. The current proof bundle cannot tell us whether any future "fix" actually works in live geometry. Without a real dy assertion, the next attempt risks a second false-green.

## 9. Stop conditions reached?

No. Audit is conclusive:
- Live trace authoritatively shows `dyPlace = 0.0`.
- Code inspection shows precondition cannot be satisfied in live geometry.
- Gametest inspection shows assertions cannot detect dy at the placed slab.

The contradiction (auto green vs live red) is fully explained.
