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
> cell floors at `-0.5` once its seat becomes air. Lane G was therefore 1 of 2, not 2 of 2. This is the sharpest demonstration yet
of the project's own thesis: **an anchor is a boolean fact, not a stored number**, so it cannot
protect a value that depends on a neighbour which no longer exists. Every lane A–F below is "no
anchor at all"; lane G is "anchored, and still not enough" — the harder half of the problem, and
the one only a real stored `dy` (not a marker set) can close.

### 🚨 S-2's GREEN COLUMN PROVES NOTHING TODAY (audit, 2026-08-06)

**Both "contradictions" resolved as FIXTURE DEFECTS, not behaviour — and the audit that resolved
them found something worse.** Neither result contradicted a prediction:

- `slab_on_lowered_bottom_slab` never builds its stated geometry. Its `-0.25` NORTH nudge mints a
  **TOP** seat via the intent mixin's half-split at `targetPos.getY()` (cross-confirmed by
  `UseOnCombineVsExtendPlacementTest`, which asserts TOP at `+0.4`), so the subject sits at −0.5,
  not the −1.0 its own comment claims. Proof independent of any trace: were it at −1.0,
  `break_directly_below` would resolve NaN → the −0.5 floor and the row would be RED. It is green,
  therefore it was never at −1.0. Fix: nudge `-0.75`, plus a `SlabType.BOTTOM` premise assert so it
  cannot silently drift again. Expected to flip **RED at −1.0 → −0.5 — a third lane-G row.**
- `candle_placed_flat_then_neighbored` is **vacuous**. It has *no* protection at all (no anchor —
  `isOrdinaryAnchorCandidate` rejects non-solid non-connecting; no FROZEN_FLAT — `freezeLoweredOnPlace`'s
  structural gate rejects it too), yet **0 of 10 mutations can reach its resolver.** A candle's only
  live door reads the neighbours of its SUPPORT, and every mutation writes at the subject's own
  level or above. It asserts "a flat block stayed flat", which `flat_full_block_control` already
  covers.

**The headline finding: 6 of 8 subjects cannot be moved by ANY mutation.** The only two that can
are the two that are RED. So "only 2/8 RED" is **not** evidence this architecture is closer to
LAW 1 than the A–F table claims — it is the signature of a matrix whose subjects mostly sit outside
the reach of its own mutations. **Planning Phase 2B off that number would be planning off a wrong
map.** Structural causes:

- **Anchored subjects are structurally unfalsifiable here.** The anchored branches read only the
  `pos.down()` chain, and no mutation writes below a subject — the only one that touches it
  *removes* it. For every anchored subject, 9 of 10 rows are provably inert.
- **Four of ten mutations are literal no-ops on every current fixture** — `break_{north,east,west,south}`
  all break air, because every builder leaves the subject's horizontal ring empty. The matrix
  reports 80 cells; ~20 never touch the world at all.
- `carpet_on_minus_one_owner` is vacuous too: its only live mutation destroys the carpet and hits
  the legitimate vanilla carve-out, contributing zero invariance assertions.

**Lane B stands as written, and is untested.** It is real and unfixed (its predicates were last
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
| B | Cantilever adjacency renders on "is lowered" (booleans, no magnitude), but the anchor twin demands `dy == -0.5` **exactly** (`SlabAnchorAttachment.qualifiesForAdjacentLoweredFullBlockAnchor`) — a −1.0 neighbour renders lowered and refuses to anchor, so the block gets neither an anchor nor a frozen-flat marker. **Scope:** reachable only by ordinary full blocks and connecting structurals — the qualifier is gated on `isOrdinaryAnchorCandidate`, which rejects slabs, carpets, block entities and decorations before the equality is evaluated. **UNTESTED: no S-2 subject enters this lane** (audit 2026-08-06). | High (any TS or mixed-slab world) |
| C | Object-follows-support-below, denied an anchor by `isCeilingAttached`'s **classname list** — floor lever/button, Y-chain, **TOP-half trapdoor** (needs no support, so the real-click repro) | Moderate, real-click reachable |
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
