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
- **The diff tripwire** (`tools/hooks/commit-msg`): an added line in `src/main/**` containing
  `geometric | merge | follow | inherit | cantilever | recompute | isAdjacent.*Lowered` is presumed
  a LAW 1 violation and blocks the commit without a logged `LAW-SIGNOFF:` and a new invariance row.
- **Preflight trailer:** every `src/main/**` commit answers `LAW-PREFLIGHT: n` — "no, this does not
  let a neighbour move a placed block" — or `y` with Maintainer's sign-off.
- **Suite-count check:** a green run is not proof unless the reported test count matches the
  expected total (see `HANDOFF.md`). A stale run dir silently under-reports and reads as clean.

---

## ⚠️ THIS LINE DOES NOT YET OBEY LAW 1 — and that is the point of Phase 2

Stated plainly so no future reader mistakes aspiration for reality: **on 1.21.11 today, height is
recomputed live on every read.** Anchors and frozen-flat markers are partial patches over that
design, not the design itself. S-2 is therefore **RED on this architecture by construction**, and
lands first as a *characterization* run whose RED inventory goes to Maintainer before any row is
re-specced.

**Known LAW 1 violation lanes (the S-2 RED inventory, 2026-08-06 — each renders lowered with NO
anchor, so each is a pop waiting for the right neighbour change):**

| # | Lane | Player likelihood |
|---|---|---|
| A | Command / rig / worldgen-authored cells — `onPlaced` never fires, so no lane anchors | Certain (this is what the rig fix addresses) |
| B | Cantilever adjacency renders on "is lowered", but the anchor twin demands `dy == -0.5` exactly — a −1.0 neighbour renders lowered and refuses to anchor | High (any TS or mixed-slab world) |
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
