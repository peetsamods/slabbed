# LAW.md — the law of Slabbed (supreme)

This file is the constitution. **No other document may redefine the law below. Where any file —
including any `*RULES*`, `*WYSIWYG*`, `*AUDIT*`, `*SPEC*`, `HANDOFF`, `SPINE`, or memory note —
conflicts with LAW.md, LAW.md wins and the other document is wrong.** A doc that "improves" or
"clarifies" the law into something a neighbor can change is not a clarification; it is a violation and
must be reverted.

## LAW 1 — Placement is permanent (maintainer ruling, verbatim)

> **Where I put it is where it goes and STAYS.** This is the core of WYSIWYG. I put a block in place A,
> I expect it to stay there no matter what. It should not change states according to a neighbor update
> (unless there is a specific vanilla mechanism as part of gameplay). If I want to place something at
> dy -0.5, that's where it goes. **No exceptions.**

## What that means for the implementation (the part that keeps getting inverted)

1. **Height (`dy`) is COMPUTED ONCE, at placement, from where the player aimed — and then FROZEN.**
   The interpretation of the aim (which cell, which half, which face) is the *only* legitimate time the
   surrounding geometry is consulted.

2. **Every later read returns the stored value verbatim. Nothing recomputes `dy` from neighbors, ever.**
   A block placed at dy = X reads dy = X for its entire life, no matter what is built, broken, or changed
   around it.

3. **A neighbor edit changing a placed block's height is a BUG — always.** "Geometric merge",
   "consistent merge", "cantilever lowering", "side inheritance", "follow the support", "recompute on
   structure change" — every one of these is a **restatement of the violation**, not a feature. If a
   change makes a placed block's height depend on a neighbor's current state, it breaks the law.

4. **The only thing that may remove or change a placed block is a genuine vanilla gameplay mechanic**
   (e.g. vanilla itself breaking a block that lost its support). That is the *block* being removed by
   vanilla — never Slabbed silently re-deriving a different height for a block that is still there.

## LAW 2 — Everything can lower (maintainer ruling, 2026-08-06, verbatim)

> **Everything should be able to lower; no exceptions.**

Eligibility to lower follows **geometry** — whether a block's support actually presents a lowered top
face — and never a block-class allow-list, a namespace string, or membership in an anchor set. "This
block type is excluded" is not a reason; it is the bug. Where a genuine hazard requires protection,
that protection is expressed **by behaviour, not by classname**, per the standing exclude-by-behaviour
rule.

The two laws are one law seen from two sides: **LAW 1 says a placed height must never change; LAW 2
says every block is entitled to the right height in the first place.**

## How this is enforced (do not rely on memory or good intentions)

- **`NeighborUpdateInvarianceTest`** (the S-2 gate): place via the real `useOn` path, record the height
  as an exact value, mutate every class of neighbor without touching the block, assert the height is
  byte-identical. This test *is* LAW 1. It is a blocking release gate. It goes green only when the
  implementation obeys the law.
- **⚠️ VACUITY CHECK — a green S-2 row is proof ONLY if at least one mutation provably reaches that
  subject's resolver.** Anchored subjects resolve from the `pos.down()` chain alone, and no mutation
  writes *below* a subject, so their other rows can be inert by construction. **Every new subject must
  name, in a comment, the mutation that would move it.** A law gate full of unreachable rows is worse
  than no gate, because it reads as proof.
- **The diff tripwire (S-3) is RETIRED** (maintainer ruling, 2026-08-07). With S-2 blocking, the
  executable test enforces LAW 1 directly, and the added-line vocabulary regex had become redundant
  and fired on comments. **LAW 1's sole enforcement is S-2. Do not reintroduce S-3.**
- **Suite-count check:** a green run is not proof unless the reported count matches
  `tools/expected-gametest-count.py`. A stale run dir silently under-reports and reads as clean.
- **Session-start reading:** this file is required reading #1 (see `AGENTS.md`). Before editing
  `src/main/**` you answer, in the commit trailer, `LAW-PREFLIGHT: n` — "no, this change does not let a
  neighbor move a placed block" — or `y` with a recorded maintainer sign-off.

## History (why this file exists)

The law was documented for months (NEVER-POP / WYSIWYG in RULES, HANDOFF, memory) yet the code violated
it continuously, because (a) the law lived nowhere as *supreme*, (b) a later audit doc redefined it as
"geometric continuity", and (c) no test ever asserted it, so a fully-broken build stayed green. Commit
`472c7b70` deleted the frozen model and installed lanes that *"recompute on every structure change"* —
calling the law-correct behavior a "stale anchor" bug. See
`docs/audits/WYSIWYG-LAW-VIOLATION-POSTMORTEM.md`. This file + the S-2 gate exist so that cannot recur.
