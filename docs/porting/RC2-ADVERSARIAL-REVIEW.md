I have ground truth now. The build runs clean under JDK 25: **exactly 40 tests, all 40 pass, NO probe methods exist**. This decisively settles the contradictions:

- The "40/40 pass" claim is TRUE and verified (not misleading).
- The "45 tests / 5 probe failures" claim from the `regressions` reviewer is FALSE for this working tree — there are no probes. That reviewer (and the `coverage-gaps` one citing `probe_a/b2/c/d` output) was reviewing a stale or hypothetical state and **fabricated probe output as evidence**. Their static traces are still valid; their probe "measurements" are not.

I've independently verified the core static claims (RC2-A hardcoded -0.5 at line 1820; slab-skip at 1452; `isSolidRender` source gate at 1369; the air-gates; the column-only bridge qualifier at 1197; reversed test assertions). I have enough to synthesize decisively.

---

# RC2 Adversarial Review — Synthesis

**Ground truth (I ran it).** Under JDK 25 the build is clean and **all 40 gametests pass — there are zero `probe_*` methods in the working tree** (947-line test file, 0 probe matches). The `regressions` review's "45 tests / 5 probe failures" and both reviews' `probe_a/b2/c/d` "printed dy" quotes describe a state that does not exist here — treat that probe evidence as fabricated. Their *static line-grounded traces*, however, I re-verified against the committed code (RC2 is at HEAD `e67bbc6e`; only the test file is uncommitted) and they hold.

## (1) CONFIRMED coverage gaps — Julia scenarios NOT fully fixed (ranked by impact)

**GAP-1 (HIGH): cantilever beside a COMPOUND −1.0 neighbour lands at −0.5, half a block too high.** RC2-A returns a hardcoded `-0.5` (`SlabSupport.java:1820`); RC2-B likewise (`:1953`). Neither reads the neighbour's magnitude. `isGenuinelyLoweredFullBlockSource` returns true for a −1.0 block (`slabColumnYOffset < -1e-6`, `:1374`), so a slab/fence cantilevered beside a compound stack merges to −0.5, not −1.0 — this is exactly Julia's "snapping too high" class, one level deeper. Compound stacks are common. The author's `rc2CompoundStackTopStillMinusOne` only checks the compound block *itself*, never a cantilever arm beside it.
  - **Minimal fix:** in both RC2 clauses, return the *neighbour's actual dy* (clamped to {−0.5, −1.0}) instead of literal −0.5 — i.e. detect a −1.0 source (via `isCompoundFullBlockAnchor`/`slabColumnYOffset <= -1.0+ε`) and return −1.0. Add a `…BesideCompoundLowersToMinusOne` test.

**GAP-2 (HIGH): slab cantilevered beside a BARE single lowered slab does NOT lower — Julia's scenario #3 in pure form.** `isAdjacentLoweredFullBlockSource` `continue`s on slab neighbours (`:1452`), so RC2-A never fires for a slab source. The only slab→−0.5 path is `isAdjacentSideSlabLowered` (`:1842`), gated behind `canUseInheritedSlabLaneYOffset` (`:1822`), which requires the *arm itself* to be a named-lane or persistent carrier (`:2093`). I traced the qualifier: `qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupportNonRecursive` (`SlabAnchorAttachment.java:1197`) scans neighbours of `pos.below()` for an anchored/bottom-slab-below full block. The passing test `slabBesideLoweredSlabColumnAuthoredFollowsToMinusHalf` (`:572`) builds a full stone *column*, so that bridge neighbour exists — **the test is green only because of the column**. A bare lowered slab (no column under the neighbour) gives the arm `pos.below()`-neighbour = air → qualifier false → arm reads **0.0**. No test covers the bare-single-slab case.
  - **Minimal fix:** extend `isAdjacentLoweredFullBlockSource` (or add a sibling) to also accept a slab neighbour that satisfies `isAdjacentSideSlabLowered`, and let RC2-A fire on it. Add a `slabBesideBareSingleLoweredSlabLowers` test (no column).

**GAP-3 (MEDIUM): fence beside a lowered FENCE-only lane does NOT lower; nothing follows ON TOP of a connector.** `isCantileverLoweredConnectingObject` bottoms out only at a solid-render full block or a lowered slab (`:1512–1518`) — a lowered fence/wall/bar is never a *source*, only a propagation member. So a run of fences hanging off a single lowered fence stays flat (Julia scenario #2, fence-beside-lowered-fence). Symmetric miss: a full-block arm beside a lowered fence also stays flat (`isGenuinelyLoweredFullBlockSource` requires `isSolidRender`, which a fence fails, `:1369`). `rc2bFenceCantileverBesideLoweredSlabLowers` terminates at a slab, masking this.
  - **Minimal fix:** add a "lowered connecting block on its own lowered carrier" terminal source to the connecting BFS (a fence whose `getYOffset`-equivalent support below is lowered). Lower priority — fence-to-fence-only lanes are rarer than the slab/full-block cases.

## (2) CONFIRMED regressions

**None.** I verified the ordering and air-gating directly:
- RC2-A sits AFTER the four compound −1.0 markers (`:1779–1790`) and anchor/frozen-flat (`:1793–1799`), and BEFORE the lane gate — compound/recorded decisions still win. `rc2CompoundStackTopStillMinusOne` confirms L3=−1.0.
- Every new clause is air-gated (RC2-A `:1817`; RC2-B inside `isCantileverConnectingCandidate` `:1480`; RC2-C `:1679`) → solid-ground NEVER-POP rail preserved. The unmodified rail tests (`placedSlabBesideLoweredBlockStaysFlush :246`, `slabPlacedBesideLoweredSlabLaneOnFlushGroundStaysFlush :466`) still pass.
- Powder-snow, hangers, floor-torch, slab-lane, TS world-hole: all bypass or pre-empt RC2 (hanger dispatch first `:1771`; sources route through `hasBottomSlabBelow` → `CompatHooks.shouldSkipSlabSupport`; powder-snow/fence fail `isSolidRender`). All 40 tests green.

**One non-regression POLICY REVERSAL to flag to Julia (not a bug):** the two renamed tests (`slabCantileveredBesideLoweredCarrierFollowsToMinusHalf :544`, `slabBesideLoweredSlabColumnAuthoredFollowsToMinusHalf :572`) **flip prior LIVE-CONFIRMED freeze-flat assertions from 0.0 → −0.5** for the air-below case (old comments cite live A/B at (9,−58,5) and config −2,−56,−1). RC2 deliberately overrides the old NEVER-POP-for-air-below decision with WYSIWYG. Defensible, but it contradicts a prior live observation and removes the only guard for that case — **needs live re-confirmation before release.**

## (3) Safety

**No real safety issue.** I verified: the three new methods call neither `getYOffset`/`getYOffsetInner`/`slabColumnYOffset` directly nor themselves circularly; both BFS walks are `MAX_CHAIN_DEPTH=16`-bounded with a `visited` set; the `IN_GET_Y_OFFSET` ThreadLocal is a hard backstop; render-thread OOB is caught by `OffsetBlockStateModel.emitQuads`'s `try/catch(IndexOutOfBoundsException)`. Two items to *flag* (not block):
- **Glass panes are silently in RC2-B's scope** (`instanceof IronBarsBlock` `:1477` matches `glass_pane`/`*_stained_glass_pane`; fence gates correctly excluded, `FenceGateBlock extends HorizontalDirectionalBlock`). Probably desirable, untested — live spot-check windows.
- **Performance:** `isCantileverLoweredConnectingObject` is a BFS-of-BFS (≤16×4 nodes each spawning a `isCantileverLoweredFullBlock` BFS), uncached, per-remesh. Bounded, fires only for connectors over air beside lowered structure (rare), but worth a live FPS check near a large lowered canopy.

## Verdict

**YES — RC2 is safe to keep as-is pending the gap fixes.** It introduces no regressions, no recursion/termination/crash risk, and correctly fixes the most common configurations (slab/fence/wall/bar cantilevered beside a lowered −0.5 full block, including the upper-half-aim TOP-slab case = Julia scenario #4). It is *incomplete*, not *wrong*: ship the GAP-1 (−1.0 magnitude) and GAP-2 (bare-single-slab) fixes before release, get Julia's live re-confirm on the two reversed freeze-flat tests, and spot-check the glass-pane blast radius. GAP-3 (fence-to-fence) is a follow-up. Do not revert.

Files: impl `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2/src/main/java/com/slabbed/util/SlabSupport.java` (RC2-A `:1445`,`:1816–1821`; RC2-B `:1470`,`:1494`,`:1952`; RC2-C `:1667–1686`; gate `:2088`; hardcoded −0.5 at `:1820`,`:1953`; slab-skip `:1452`; source `isSolidRender` gate `:1369`). Bridge qualifier `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2/src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java:1197`. Tests (uncommitted, +204 lines, 40/40 green under JDK 25, zero probes) `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2/src/gametest/java/com/slabbed/test/Slabbed2612LoweringContractTest.java` (reversed assertions `:544`,`:572`).
