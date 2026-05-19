## MC 1.21.1 Wall/Lantern Lower-Mid Targeting Decision Gate

1. Title
MC 1.21.1 Wall/Lantern Lower-Mid Targeting Decision Gate

2. Verdict
The lower/mid wall/lantern/slab targeting complaint is not proven as a Beta4 parity regression. Same-ray Beta4 also resolves to anchored full block.

3. Evidence chain
- MC1211 RED live complaint confirmed (live feel mismatch still reproducible).
- Wall model/outline/raycast coherent; wall is lawfully missed by ray/collision geometry.
- Lantern/support candidate exists but is also missed by the low/mid ray and is not a lawful visible owner under current authority.
- Beta4 same-ray replay also starts anchored, rejects supportBehind as initial-not-support-surface, misses wall/lantern, and finishes anchored.
- Old Beta4 useful row used a different initial-hit route and is not same-ray evidence for this row.

4. Closed hypothesis
Close “restore Beta4 behavior for this lower/mid wall/lantern/slab ray” as unsupported.

5. Open product decision
If Julia wants this feel anyway, it must be a new named product behavior, not a Beta4 parity restoration.

Possible name: `WALL_LANTERN_COMFORT_TARGETING`

6. Required red proof before future patch
- exact fixture
- held item
- camera ray
- initial target
- intended final target
- why the intended target is lawful despite wall/lantern outline miss
- why this does not become generic lowered visual rescue
- why Phase19 true-top remains protected
- why no-rescue boundary remains protected
- why side-slab scan-cap remains protected

7. Decision options
- Option A: accept current law for beta/port.
  - No patch.
  - Remove/retire temporary trace-only Beta4 instrumentation later.
  - Continue MC1211 port readiness elsewhere.
- Option B: define new comfort targeting behavior.
  - Write a red proof first.
  - Patch only after predicate is named and accepted.
  - Treat as new product behavior, not Beta4 parity restoration.

8. Non-negotiables
- no rescue from generic lowered visuals
- no generic anchored full-block rescue broadening
- no fake wall outline ownership
- no SlabSupport/ClientDy changes for this unless a future red proof proves a state-law issue
- no release-ready claim until trace WIP is reconciled and required regressions pass

9. Still unproven regressions for any future patch
- Phase19 true-top
- no-rescue boundary
- side-slab scan-cap
