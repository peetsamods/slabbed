# 14 — Zoom-Out Discipline

This is a general rule, not specific to mixins. It exists because the
mistake in `docs/codex/13-mixin-layer-wiring-audit.md` was not really a
mixin mistake — it was a failure to ever ask the bigger question across many
sessions of otherwise-correct narrow-slice work.

## The cost, stated plainly

The Forge 1.20.1 WYSIWYG/targeting work cost Julia multiple sessions —
hours and hours — of narrow patch slices, each individually disciplined
(preflight, RED proof, smallest file set, savepoint), each individually a
plausible-looking partial improvement, none of them fixing the actual
problem. The actual problem was found and fixed in well under an hour once
someone asked one zoomed-out question: "is the proven fix for this even
running at all?" It wasn't. The whole mixin layer was disabled at the build
level. Every narrow slice before that question was patching downstream
symptoms of one disabled subsystem.

Narrow-slice discipline (`04-slice-contracts.md`, `05-preflight-savepoint.md`)
is correct and stays. The failure was that nothing in the process forced a
zoomed-out check across slices, only within a slice. A slice can be perfectly
executed — correct preflight, correct RED, smallest file set, clean
savepoint — and still be the wrong slice, because the question "is this the
right layer to be working in at all" was never asked above the level of the
individual patch.

## The rule

**Track the symptom family, not just the individual RED, across sessions.**
Before starting any patch slice, name the symptom family it belongs to (e.g.
"crosshair targeting disagrees with visible geometry," "a placed block
changes height after an unrelated world edit," "Book IV Visual Triad
proof"). Check `HANDOFF.md` / `SLABBED_SPINE.md` history for that family.

**If this is the 2nd+ recorded slice against the same symptom family with no
full resolution, the next slice MUST be a zoomed-out audit, not another
narrow patch — even if each prior slice individually proved its own narrow
RED green.** This is stricter than the existing "two failed fixes" trigger
in `10-troubleshooting-when-stuck.md`: that trigger fires on failure; this
one also fires on a string of partial, narrowly-proven "successes" that
never closes the family out. A narrow slice that goes green but the family
stays open is itself the warning sign — it usually means the slices are
treating symptoms of one shared cause as if they were independent bugs.

A zoomed-out audit asks, in this order, before any new Java:

1. **Is there one shared cause behind this whole symptom family?** Don't
   list five REDs and patch them one at a time — ask what single layer,
   if broken or unwired, would produce all five. (See
   `docs/codex/13-mixin-layer-wiring-audit.md` for the wiring-specific
   version of this question; the question itself is general.)
2. **Does the proven fix for this family already exist somewhere** — in this
   repo's own dormant code, in the donor branch, in a sibling port, in
   `SLABBED_SPINE.md`/memory history? Read it before writing anything new.
   A new narrow patch next to an already-correct-but-disconnected
   implementation is wasted work twice over: once now, and again later when
   someone has to reconcile the two.
3. **What would make the whole family pass at once, not just this one
   instance of it?** If the honest answer is "nothing, each block
   family/case genuinely needs its own code," narrow slicing was correct
   and you should resume it. If the honest answer is "fixing the shared
   layer would close most or all of these REDs in one move," stop slicing
   and fix the shared layer.

## What this looks like in the savepoint log

A zoomed-out audit slice produces a different kind of artifact than a narrow
patch slice: not a savepoint tag for one fixed case, but a short note in
`HANDOFF.md`/`SLABBED_SPINE.md` naming the shared cause and the minimal set
of fixes that close the family, so the NEXT session (or the next agent)
implements the real fix instead of slice N+1 of the same symptom.

```text
Symptom family: <name>
Prior narrow slices against this family: <list, with what each proved>
Shared cause found: <yes/no — if no, say why slicing is still correct>
If yes: <the one fix that closes the family, and which prior narrow
         slices/files become obsolete once it lands>
```

## Self-check before starting any patch slice

1. What symptom family does this belong to?
2. Has this family already absorbed 2+ slices?
3. If yes, have I actually asked "is there one shared cause" — not just
   reread the family's history, but checked whether a dormant fix already
   exists or whether the architecture itself is unwired? (Run
   `docs/codex/13-mixin-layer-wiring-audit.md` if the family is
   targeting/triad/survival-shaped.)
4. Only patch the individual case once (3) has a real answer, not an
   assumed one.
