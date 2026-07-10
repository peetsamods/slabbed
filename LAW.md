# LAW.md — the one law of Slabbed (supreme)

This file is the constitution. **No other document may redefine the law below. Where any file —
including any `*RULES*`, `*WYSIWYG*`, `*AUDIT*`, `*SPEC*`, `HANDOFF`, `SPINE`, or memory note —
conflicts with LAW.md, LAW.md wins and the other document is wrong.** A doc that "improves" or
"clarifies" the law into something a neighbor can change is not a clarification; it is a violation and
must be reverted.

## The law (the maintainer, verbatim)

> **Where I put it is where it goes and STAYS.** This is the core of WYSIWYG. I put a block in place A,
> I expect it to stay there no matter what. It should not change states according to a neighbor update
> (unless there is a specific vanilla mechanism as part of gameplay). If I want to place something at
> dy 0.5, that's where it goes. **No exceptions.**

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

## How this is enforced (do not rely on memory or good intentions)

- **`NeighborUpdateInvarianceTest`** (the S-2 gate): place via the real `useOn` path, record the height
  as an exact value, mutate every class of neighbor without touching the block, assert the height is
  byte-identical. This test *is* the law. It is a blocking release gate. It goes green only when the
  implementation obeys the law.
- **The diff tripwire** (implemented inline in `tools/hooks/commit-msg`): any added line in `src/main/**` containing
  `geometric | merge | follow | inherit | cantilever | recompute | isAdjacent.*Lowered` is presumed a
  violation and blocks the commit unless it carries a logged `LAW-SIGNOFF:` and adds an invariance row.
- **Session-start reading:** this file is required reading #1 (see `AGENTS.md`). Before editing
  `src/main/**` you answer, in the commit trailer, `LAW-PREFLIGHT: n` — "no, this change does not let a
  neighbor move a placed block" — or `y` with the maintainer's sign-off.

## History (why this file exists)

The law was documented for months (NEVER-POP / WYSIWYG in RULES, HANDOFF, memory) yet the code violated
it continuously, because (a) the law lived nowhere as *supreme*, (b) a later audit doc redefined it as
"geometric continuity", and (c) no test ever asserted it, so a fully-broken build stayed green. Commit
`472c7b70` deleted the frozen model and installed lanes that *"recompute on every structure change"* —
calling the law-correct behavior a "stale anchor" bug. See
`docs/audits/WYSIWYG-LAW-VIOLATION-POSTMORTEM.md`. This file + the S-2 gate exist so that cannot recur.
