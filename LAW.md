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
- **✅ S-2 is BLOCKING BY DEFAULT (flipped 2026-08-06 by Maintainer).** `./gradlew build runGameTest` —
  no flag — enforces the law directly. `-Dslabbed.lawGate=false` is the escape hatch for a session
  deliberately introducing a new RED subject and needing the inventory without failing the build
  while it's fixed forward; it is never a way to land a known violation quietly.
  **The full matrix executes on every run in either mode** — every subject built, every mutation
  applied, every violation collected — because the RED inventory is the entire value of the test.
  Only the verdict differs: violations are thrown by default, or **logged instead** when disabled,
  one greppable `[LAW-GATE]` line per subject (`CLEAN` when none moved, so silence can never be
  mistaken for a run that did not happen). **S-2 is never skipped and never short-circuited; a
  green-because-skipped law gate would be the worst false green this project could ship.**
  (An earlier revision of this bullet read "characterization by default; a blocking S-2 could not
  be honest while this line does not obey LAW 1". That was true when written and is kept in the
  history below rather than pretended away — the line now passes.)
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

## ✅ PHASE 2 EXIT CRITERION MET (2026-08-06) — S-2 passes enforcing, default flipped

**This section's title used to be "THIS LINE DOES NOT YET OBEY LAW 1". Kept below, unedited, as
the record of the state that title described — not because it is still true.** As of `c51ec869`,
S-2 passes with 9 of 9 subjects CLEAN under enforcement, and the default flag flip (this commit)
makes that the standing state of `./gradlew build runGameTest`, not an opt-in check.

> **AMENDED 2026-08-06 (night, second): 10 of 10 CLEAN, and the tenth subject exists because a
> PLAYER found what the gate could not.** Maintainer's live pass caught followers floating above a
> `−1.0` fence while S-2 read 9/9 CLEAN, because no subject had ever rested on a support that was
> neither a slab nor a solid cube. The gate was not wrong about what it tested; it had never been
> asked. Read the paragraph below with that as the live example of what "precisely" means: a green
> S-2 certifies its own scenes and nothing outside them.
>
> **AMENDED AGAIN 2026-08-06 (night, third): 11 of 11 CLEAN, and the eleventh subject exists for
> the SAME reason, on the opposite side of the same boundary.** The fence fix taught
> `supportSeatDy` to ask a top-FACE question instead of a volume question — but its full-height arm
> still opened with `state.getBlock() instanceof SlabBlock`, so the new predicate was never asked
> about a TOP or DOUBLE slab, both of which pass it. Recorder run `f37a3b2b` (actions a38/a39): a
> `smooth_stone_slab[type=double]` support at `−1.0`, a `stripped_jungle_log` placed on it captured
> `−0.5`, while the same session's fence, chain and BOTTOM-slab supports were all correct. Two
> consecutive coverage-boundary bugs, both found by a player, both a class test standing in for a
> shape question. **The lesson is not "add another subject"; it is that a class test inside a
> geometry predicate is where to look first.**
>
> **AMENDED A FOURTH TIME 2026-08-06 (night, fourth): still 11 of 11, and DELIBERATELY so — this
> one is not an S-2 subject.** Maintainer's next live pass confirmed the fix above server-side and then
> reported a *visible* snap: the block is drawn at `−0.5` for a moment before dropping to `−1.0`.
> Recorder `e9eb0932` (a8/a10/a15) shows CLIENT `−0.5` / SERVER `−1.0` for the same cell, while the
> CLIENT's own snapshot in that frame already read the support as `dy=−1.0000 anchored=true` — so
> the client was not missing the fact. The LIVE lane (`slabColumnYOffset`) answered
> `isBottomSlab(cur) ? -1.0 : -0.5`: a class test plus two flat constants, correct only while the
> slab sits at `−0.5`. **The SERVER's own pre-placement read was `−0.5` too**, which is what proves
> the lane is side-independent and headlessly reachable all along — the store simply overwrote its
> answer a moment later, so the defect could only ever be seen as a frame, never as a stored number.
> **No S-2 subject was added, on purpose:** S-2 asks whether a PROTECTED cell survives a neighbour
> edit, and this defect lives in cells that hold no stored height by definition. A subject built
> that way would be RED by construction and would prove nothing about LAW 1. Three new rows in
> `AnchoredFollowerSupportDyTest` assert the resolver directly instead. **This is the fifth
> flat-constant mirror and, counting `isBottomSlab` here, exclude-by-classname number TEN.**

**What this claim covers, precisely, so it is not over-read a second time (LAW.md was wrong about
this once already, in the "8 of 8" revision below):** it covers the 11 subjects and 10 mutations
this matrix actually builds and applies — measured reachable, per the audit at `49691609` and the
same measurement repeated for subjects #10 and #11. It does
**not** cover: any geometry outside those 11 scenes; any lane this matrix's mutations cannot reach
(6 of 10 mutations still have zero live cells anywhere — see the reachability table below); or
anything requiring Terrain Slabs, which cannot load in this headless environment at all. **This
line obeys LAW 1 for what S-2 tests. Whether it obeys LAW 1 everywhere is a live-testing question,
not a headless one**, and the LIVE_LEDGER entry dated 2026-08-06 (night) records six specific rows
Maintainer's next pass must cover before this milestone is trusted beyond the gate itself.

The frozen-height architecture is still not the general design — the placement-dy store closes the
cases this matrix found, not every live-recompute lane in `SlabSupport`. Anchors and frozen-flat
markers remain what they were: mechanisms layered onto a live-recompute engine, not a replacement
for one. The original framing below is preserved for that reason.

---

### (historical) THIS LINE DOES NOT YET OBEY LAW 1 — and that was the point of Phase 2

Stated plainly so no future reader mistakes aspiration for reality: **on 1.21.11 [as of
2026-08-06, before the fixes above], height is recomputed live on every read.** Anchors and
frozen-flat markers are partial patches over that design, not the design itself. S-2 is therefore
**RED on this architecture by construction**, and lands first as a *characterization* run whose RED
inventory goes to Maintainer before any row is re-specced.

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
> **UPDATE 2 (2026-08-06, later still): both violations closed, default flipped.** `7756d152`
> (candle — `freezeLoweredOnPlace`'s block-type allow-list replaced with a behavioural
> `hangsFromTheCellAbove` exclusion) and `c51ec869` (lane B — the placement-dy store records a
> height for lowered placements no anchor lane claims, without granting an anchor; the rejected
> alternative was measured, not argued, to feed the open `KNOWN_INCOMPLETE` L11-broader
> anchor-widening leak). S-2 is 9/9 CLEAN enforcing. Maintainer flipped the default the same night. See
> the section above for what this milestone does and does not cover.

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

### ✅ AUDIT COMPLETE (`49691609`, 2026-08-06): 9 of 9 subjects provably reachable — now 11 of 11

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
| 10 | `full_block_on_minus_one_fence_support` | `break_directly_below` `−1.0→0.0` | CLEAN — store, **discriminated** |
| 11 | `full_block_on_minus_one_double_slab_support` | `break_directly_below` `−0.5→0.0` | CLEAN — store, **discriminated** |

**Subject #11 added 2026-08-06 (night, third), by the same protection-stripping method.** Measured:
fully protected `−1.0→−1.0` (subject survives the break); anchor+frozen+store stripped `−0.5→0.0`,
so the mutation provably reaches the resolver; **store cleared with the anchor kept `−1.0→−0.5`**,
so like #3, #5, #6 and #10 it discriminates the stored NUMBER from both the anchor boolean and the
fallback floor. The other nine mutations were measured inert against it (`−1.0→−1.0` in all three
protection modes), which is why only `break_directly_below` is named on the row. Its support is a
real vanilla slab-COMBINE (two clicks on one cell), which is how the live double slab arose.

**Subject #10 added 2026-08-06 (night, second) — it exists because this table's coverage boundary
was found by a player, not by the gate.** Every one of subjects #1–#9 rests on a SLAB or a SOLID
CUBE. Nothing here had ever rested a block on a fence, so S-2 stayed 9/9 CLEAN through a live bug
in which a sign, a lantern and a log all floated half a block above a `birch_fence` at `−1.0`
(`supportSeatDy` classified a seat by `isSolidBlock`, a volume test, so a fence matched no arm and
its followers fell to the `−0.5` floor — invisible for as long as `−0.5` was also the right
answer). Measured by the same protection-stripping method as the rest of the table: fully protected
`−1.0→−1.0`; bare `−1.0→0.0`; **store cleared with the anchor kept `−1.0→−0.5`**, so like #3, #5
and #6 it discriminates the stored NUMBER from both the anchor boolean and the fallback floor.

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
| D | Full block on an unanchored adjacency-lowered TOP/DOUBLE slab. **Narrowed 2026-08-06 (night, third):** an ANCHORED or STORED lowered TOP/DOUBLE slab support is no longer in this lane — `supportSeatDy` now reads it, S-2 subject #11 pins it. **Narrowed again (night, fourth):** the SUBJECT no longer needs a stored height either, provided the SUPPORT is anchored or stored — `slabColumnYOffset` reads the seat instead of a constant, which is precisely the number the client draws before sync arrives. What remains is the case where the SUPPORT ITSELF holds neither fact (a pre-store world or an authored cell): the column walk cannot claim it and it still takes the floor. | Old worlds, authored cells |
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
