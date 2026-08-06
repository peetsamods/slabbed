# LAW.md — the law of Slabbed (supreme)

This file is the constitution. **No other document may redefine the laws below. Where any file —
`RULES`, `DY_SPEC`, `KNOWN_INCOMPLETE`, `HANDOFF`, `SPINE`, a test's javadoc, or a memory note —
conflicts with LAW.md, LAW.md wins and the other document is wrong.** A doc that "improves" or
"clarifies" a law into something a neighbour can change is not a clarification; it is a violation
and must be reverted.

Ported to the Fabric 1.21.11 parity line 2026-08-06 from the 26.2 donor, plus Maintainer's second law.

---

## LAW 1 — Placement is permanent (Maintainer, verbatim)

> **Where I put it is where it goes and STAYS.** This is the core of WYSIWYG. I put a block in
> place A, I expect it to stay there no matter what. It should not change states according to a
> neighbor update (unless there is a specific vanilla mechanism as part of gameplay). If I want to
> place something at dy 0.5, that's where it goes. **No exceptions.**

1. **Height (`dy`) is COMPUTED ONCE, at placement, from where the player aimed — and then FROZEN.**
   Interpreting the aim is the *only* legitimate time surrounding geometry is consulted.
2. **Every later read returns the stored value verbatim.** A block placed at dy = X reads dy = X
   for its entire life, whatever is built, broken, or changed around it.
3. **A neighbour edit changing a placed block's height is a BUG — always.** "Geometric merge",
   "cantilever lowering", "side inheritance", "follow the support", "recompute on structure change"
   are restatements of the violation, not features.
4. **Only a genuine vanilla gameplay mechanic may remove or change a placed block** (e.g. vanilla
   breaking a block that lost its support). That is the *block* being removed by vanilla — never
   Slabbed silently re-deriving a different height for a block that is still there.

## LAW 2 — Everything can lower (Maintainer, 2026-08-06, verbatim)

> **Everything should be able to lower; no exceptions.**

Eligibility to lower follows **geometry** — whether a block's support actually presents a lowered
top face — and never a block-class allow-list, a namespace string, or membership in an anchor set.
"This block type is excluded" is not a reason; it is the bug. Where a genuine hazard requires
protection (e.g. weather-deposited terrain fill tearing a step through unplaced terrain), that
protection must be expressed **by behaviour, not by classname** — the project's standing
exclude-by-behaviour rule.

The two laws are one law seen from two sides: **LAW 1 says a placed height must never change; LAW 2
says every block is entitled to the right height in the first place.**

---

## Enforcement (do not rely on memory or good intentions)

- **`NeighborUpdateInvarianceTest`** (the S-2 gate): place via the real `useOn` path, record the
  height exactly, mutate every class of neighbour without touching the block, assert the height is
  byte-identical. **This test *is* LAW 1.**
- **S-2's VERDICT is characterization by default; the RUN never is.** Earlier revisions of this
  section read as though S-2 already blocks. It does not, and could not honestly: the section below
  says this line does not yet obey LAW 1, so a blocking S-2 would make CI permanently red for a
  state this constitution declares expected — and a permanently red CI is a CI nobody reads.
  **The full matrix executes on every run either way** — every subject built, every mutation
  applied, every violation collected — because the RED inventory is the entire value of the test.
  Only the verdict differs: by default violations are **logged, not thrown**, one greppable
  `[LAW-GATE]` line per subject (`CHARACTERIZATION` when a subject moved, `CLEAN` when none did, so
  silence can never be mistaken for a run that did not happen). **S-2 is never skipped and never
  short-circuited; a green-because-skipped law gate would be the worst false green this project
  could ship.**
- **`-Dslabbed.lawGate=true` makes S-2 blocking** (`./gradlew build runGameTest -Dslabbed.lawGate=true`
  — see `HANDOFF.md`), throwing the identical violation message. **Flipping that default to ON is
  Phase 2's exit criterion:** Phase 2 is done when, and only when, this line passes S-2 with the
  flag removed.
- **The diff tripwire** (`tools/hooks/commit-msg`): an added line in `src/main/**` containing
  `geometric | merge | follow | inherit | cantilever | recompute | isAdjacent.*Lowered` is presumed
  a LAW 1 violation and blocks the commit without a logged `LAW-SIGNOFF:` and a new invariance row.
- **Preflight trailer:** every `src/main/**` commit answers `LAW-PREFLIGHT: n` — "no, this does not
  let a neighbour move a placed block" — or `y` with Maintainer's sign-off.
- **Suite-count check:** a green run is not proof unless the reported test count matches the
  expected total (see `HANDOFF.md`). A stale run dir silently under-reports and reads as clean.
- **⚠️ VACUITY CHECK — a green S-2 row is proof ONLY if at least one mutation provably reaches the
  subject's resolver.** The count check catches under-reporting; it does not catch a row that runs
  and asserts nothing. Anchored subjects resolve from the `pos.down()` chain alone, and no mutation
  writes *below* a subject, so their non-`break_directly_below` rows are inert by construction.
  **Every new subject must name, in a comment, the mutation that would move it.** A law gate full
  of unreachable rows is worse than no gate, because it reads as proof. (Established 2026-08-06,
  when an audit found 6 of 8 subjects unreachable — see below.)

---

## ⚠️ THIS LINE DOES NOT YET OBEY LAW 1 — and that is the point of Phase 2

Stated plainly so no future reader mistakes aspiration for reality: **on 1.21.11 today, height is
recomputed live on every read.** Anchors and frozen-flat markers are partial patches over that
design, not the design itself. S-2 is therefore **RED on this architecture by construction**, and
lands first as a *characterization* run whose RED inventory goes to Maintainer before any row is
re-specced.

**S-2 actually ran (`89792d44`, 2026-08-06): 2 of 8 subjects RED, both on `break_directly_below`
only.** This is a smaller RED surface than the A–F table below predicted, and it exposed a lane the
table did not name:

> **UPDATE (2026-08-06, end of day — supersedes two earlier revisions of this box).** Lane G was
> closed by the placement-dy store (`d4f38510`) and lane C by the ceiling ROLE predicate
> (`3a7c17c0`). The matrix was then repaired (`e5704f50`) and fully audited (`49691609`), so the
> honest state is now:
>
> **9 of 9 subjects are provably reachable — measured, not argued — and 2 are RED.**
> `candle_placed_flat_then_neighbored` (`0.0 → −0.5` on `add_lowered_stack_east`, a decoration with
> no protection at all) and `cantilever_full_block_beside_minus_one` (`−0.5 → 0.0` on
> `break_south_neighbor`, lane B: the adjacent-anchor qualifier demands `dy == -0.5` **exactly**, so
> a −1.0 neighbour is denied both an anchor and a frozen-flat marker). **Both are genuine open
> product bugs and the Phase 2 punch-list.**
>
> **The default must still not be flipped**, but the reason has changed. It is no longer "the matrix
> proves nothing" — it now proves plenty. It is simply that **two real violations are open**. Phase
> 2's exit criterion is unchanged and now actually measurable: this line passes S-2 enforcing, with
> those two closed, and the default flips. That remains Maintainer's call.

**Lane G — support-removal-driven magnitude re-derivation (confirmed, S-2-proven, and CLOSED
2026-08-06 by the placement-dy store).** The RED subject `full_block_on_anchored_minus_one_support`
(−1.0→−0.5) WAS anchored — presence protection held — but its height still moved, because the
anchor records only *that* the cell is lowered, not *how far*, and the magnitude re-derives live
from a support that `break_directly_below` just destroyed.

> **CORRECTION (2026-08-06).** An earlier revision of this file claimed **both** S-2 REDs were
> anchored. That was wrong, and it was my error, not the test's.
> `chain_on_lowered_support_ceiling_scenery` is **never anchored** — probed directly:
> `chainAnchored=false`. The Y-axis chain is rejected by every anchor lane and explicitly excluded
> by `isCeilingAttached`. **It is a lane C cell, not lane G.** The RED message corroborates this on
> its face: it falls to *exactly* `0.0`, which an anchored cell can never produce — an anchored
> cell floors at `-0.5` once its seat becomes air. Lane G was therefore 1 of 2, not 2 of 2.

This is the sharpest demonstration yet of the project's own thesis: **an anchor is a boolean fact,
not a stored number**, so it cannot protect a value that depends on a neighbour which no longer
exists. Every lane A–F below is "no anchor at all"; lane G is "anchored, and still not enough" —
the harder half of the problem, and the one only a real stored `dy` (not a marker set) can close.

### 🚨 S-2's GREEN COLUMN PROVED NOTHING (audit, 2026-08-06) — PARTIALLY REPAIRED (`e5704f50`, same day)

**Both "contradictions" resolved as FIXTURE DEFECTS, not behaviour — and the audit that resolved
them found something worse.** Neither result contradicted a prediction:

- `slab_on_lowered_bottom_slab` never built its stated geometry. Its `-0.25` NORTH nudge minted a
  **TOP** seat via the intent mixin's half-split at `targetPos.getY()` (cross-confirmed by
  `UseOnCombineVsExtendPlacementTest`, which asserts TOP at `+0.4`), so the subject sat at −0.5,
  not the −1.0 its own comment claimed. Proof independent of any trace: were it at −1.0,
  `break_directly_below` would resolve NaN → the −0.5 floor and the row would be RED. It was green,
  therefore it was never at −1.0. **Fixed:** nudge `-0.75` + a `SlabType.BOTTOM` premise assert.
  **Result diverged from the primary prediction, reported rather than smoothed over:** it is now
  genuinely at −1.0 *and still CLEAN* — the placement-dy store (`d4f38510`) already covers this
  case, so the subject now tests real geometry and passes for a real reason.
- `candle_placed_flat_then_neighbored` was **vacuous**. No protection at all (no anchor —
  `isOrdinaryAnchorCandidate` rejects non-solid non-connecting; no FROZEN_FLAT —
  `freezeLoweredOnPlace`'s structural gate rejects it too), yet 0 of 10 mutations could reach its
  resolver. **Fixed:** planted a dormant lowering source so `add_lowered_stack_east` actually bites.
  **Now genuinely RED:** `0.0 → -0.5`, a real lane E/F instance.
- **Lane B had no subject at all.** **Fixed:** added `cantilever_full_block_beside_minus_one` —
  an ordinary full block beside a −1.0 owner, verified empirically (not assumed) to land via plain
  vanilla side-placement. **RED:** `break_south_neighbor: -0.5 → 0.0`.

### ✅ AUDIT COMPLETE (`49691609`, 2026-08-06): 9 of 9 subjects provably reachable

The audit was finished by **measurement, not reasoning**: probe gametests built every subject with
the real builders, **stripped its protection** (`removeAnchor` clears anchor + frozen-flat + stored
dy together), applied all ten mutations, and recorded whether the resolver's answer moved — a
90-cell measured matrix, plus anchor-kept/store-cleared counterfactuals to separate *"the store
holds this"* from *"the floor happens to equal it"*. Probes were removed afterward.

| # | Subject | Live mutation (measured, bare) | Verdict |
|---|---|---|---|
| 1 | `flat_full_block_control` | `add_full_block_north` `0.0→−0.5` | CLEAN — frozen-flat holds |
| 2 | `flat_slab_control` | `add_lowered_stack_east` `0.0→−0.5` | CLEAN — frozen-flat holds |
| 3 | `full_block_on_anchored_minus_one_support` | `break_directly_below` `−1.0→0.0` | CLEAN — store, **discriminated** |
| 4 | `cantilever_slab_beside_lowered_block` | `break_south_neighbor` `−0.5→0.0` | CLEAN — anchor; store NOT discriminated |
| 5 | `slab_on_lowered_bottom_slab` | `break_directly_below` `−1.0→0.0` | CLEAN — store, **discriminated** |
| 6 | `carpet_on_laterally_lowered_slab_support` | `add_lowered_stack_east` `−0.5→−1.0` | CLEAN — store, **discriminated** |
| 7 | `candle_placed_flat_then_neighbored` | `add_lowered_stack_east` `0.0→−0.5` | **RED** — no protection |
| 8 | `chain_on_lowered_support_ceiling_scenery` | `break_directly_below` `−0.5→0.0` | CLEAN — anchor; store NOT discriminated |
| 9 | `cantilever_full_block_beside_minus_one` | `break_south_neighbor` `−0.5→0.0` | **RED** — no protection |

Subject #6 was renamed from `carpet_on_minus_one_owner` when it was rebuilt: the old name described
geometry it no longer has, and a lying subject name is precisely the defect that made #5 test the
wrong thing for weeks. It is now **the strongest row in the matrix** — anchor kept with the store
cleared still moves, fully protected holds, so it asserts lane G's thesis (the stored *number*, not
the anchor boolean, is what holds a cell) rather than arguing it.

**Honest limits of this table, stated rather than smoothed:**

- **#4 and #8 do not discriminate the store.** Their anchors are provably load-bearing (stripping
  the anchor reproduces the pre-fix RED byte-identically), but with the store cleared and the anchor
  kept they still read `−0.5`, because the floor equals the stored value there. Those rows cannot
  tell the two mechanisms apart, and their comments say so.
- **6 of the 10 mutations still have zero live cells anywhere** (`add_slab_north`, `add_slab_east`,
  `add_full_block_above`, `break_north/east/west_neighbor`). The nominal 90-cell matrix is carried
  by 4 mutations. A future pass should either give them reachable geometry or retire them.
- Two structural facts found while building the controls, worth keeping: **on solid ground NO
  mutation can reach a full block at all** (gap-fill is gated on air-below, the column walk stops at
  the opaque support, opaque cubes are pinned flat), and **`add_lowered_stack_east` can never move
  any full block**, because it plants a *slab* at the subject's level and the adjacency check skips
  slab neighbours.

**Lane B is now tested and RED** (was: real and unfixed, but untested). Its predicates were last
touched 2026-06/07; none of today's `9e4dffb5` / `76454c6d` / `182952d7` go near them — those live
in the support-below resolver). But **no S-2 subject enters it**, because
`qualifiesForAdjacentLoweredFullBlockAnchor` is gated on `isOrdinaryAnchorCandidate`, which rejects
slabs, carpets, block entities and decorations before the `== -0.5d` equality is ever evaluated —
so the lane is reachable only by **ordinary full blocks and connecting structurals**.

**Consequence for lane G's true size:** once the fixture defects are repaired, lane G accounts for
**4 of 8** subjects, not 2. The lane G entry above is understated, not wrong.

**Known LAW 1 violation lanes (the S-2 RED inventory, 2026-08-06 — each renders lowered with NO
anchor, so each is a pop waiting for the right neighbour change; lane G above is the anchored
counterpart, confirmed live by S-2 itself):**

| # | Lane | Player likelihood |
|---|---|---|
| A | Command / rig / worldgen-authored cells — `onPlaced` never fires, so no lane anchors | Certain (this is what the rig fix addresses) |
| B | Cantilever adjacency renders on "is lowered" (booleans, no magnitude), but the anchor twin demands `dy == -0.5` **exactly** (`SlabAnchorAttachment.qualifiesForAdjacentLoweredFullBlockAnchor`) — a −1.0 neighbour renders lowered and refuses to anchor, so the block gets neither an anchor nor a frozen-flat marker. **Scope:** reachable only by ordinary full blocks and connecting structurals — the qualifier is gated on `isOrdinaryAnchorCandidate`, which rejects slabs, carpets, block entities and decorations before the equality is evaluated. **TESTED and OPEN**: S-2 subject `cantilever_full_block_beside_minus_one` (`e5704f50`) confirms it live —
RED at `break_south_neighbor: -0.5 → 0.0`, enforcing mode. | High (any TS or mixed-slab world) |
| C | ~~Object-follows-support-below, denied an anchor by `isCeilingAttached`'s **classname list** — floor lever/button, Y-chain, **TOP-half trapdoor** (needs no support, so the real-click repro)~~ **CLOSED 2026-08-06** — `isCeilingAttached` now asks the ROLE (does this block, in this state, actually hang from above?) instead of the block TYPE: lever/button by `BLOCK_FACE`, bell by `ATTACHMENT`, dripstone by `VERTICAL_DIRECTION`, and the two families vanilla gives no property (Y-chain, TOP-half trapdoor) by a world query for something above to hang from. Floor-mounted subjects now reach `qualifiesForDecorativeObjectAnchor` and lock. Intrinsic hangers (lantern `HANGING=true`, hanging sign, cave vines, spore blossom, hanging roots) are untouched by construction. | Moderate, real-click reachable |
| D | Full block on an unanchored adjacency-lowered TOP/DOUBLE slab | Old worlds, authored cells |
| E | Standing-object probe vs a column walk that stops at air | Low |
| F | Gap-fill under an anchored lowered block entity | Low |

Natural-terrain flush behaviour (the world-hole guard) is **not** on this list — it is by design.

**Every one of A–F stops existing under frozen height by construction**, because none of them can
run at read time once the stored value is authoritative. That is why the correct response to a new
pop report is Phase 2, not another anchor-widening — anchor-widening has already caused a
self-inflicted Terrain Slabs over-lowering regression on this line, twice.

## History (why this file exists)

On the donor line the law was documented for months while the code violated it continuously,
because it lived nowhere as *supreme*, an audit doc redefined it as "geometric continuity", and no
test asserted it — so a fully-broken build stayed green. This file plus the S-2 gate exist so that
cannot recur. On this line the same shape has now appeared five times in one day as
**boolean-where-magnitude-is-needed**: an anchor flag, a seat check, a cull test, a column probe,
and a pot's identity each stood in for an actual height. When two lowered things disagree, suspect
a boolean that should be a number.
