# Slabbed — Rules (Development Guardrails)

These rules are intentionally strict. Slabbed must remain predictable, reversible, and visually correct.

## 1) No global redirects without proof
- Do not use broad `@Redirect` hooks on shared helpers (shape/solid/support checks) unless you can demonstrate no behavior change outside slab contexts.
- Default to narrow `@Inject` with tight conditions and early returns.

## 2) One category per branch, always
- Never mix categories in one branch.
- Branch naming: `feat/<category>-on-slabs` 
- Tag naming: `slabbed-<category>-pass` 

## 3) Baseline lane is sacred
- The full-block lane must behave exactly like vanilla.
- If a change alters baseline behavior, stop and revert to the last known good tag.

## 4) Slab support logic is single-source
- All slab support semantics must route through `SlabSupport`.
- Do not duplicate slab checks inside mixins beyond “call helper and act”.

## 5) Never expand slab semantics silently
- Do not broaden support to stairs, fences, walls, trapdoors, panes, or other partial blocks without:
  - an explicit decision,
  - a dedicated branch,
  - and a dedicated tag.

## 6) Visual audit gate is mandatory
- No `slabbed-<category>-pass` tag unless the category passes `slabbed-visual-alignment-audit`.

## 7) Shared hooks require regression proof
- If you choose a shared hook over targeted mixins, you must run a regression sweep of all previously passing categories.
- If any regression occurs, revert and move to targeted mixins.

## 8) If the same failure happens twice, document it
- When a failure mode repeats, add a short note to either:
  - the relevant skill, or
  - `INSTRUCTIONS.md` 
- This is how the project “learns” and avoids repeated pain.

## 9) Savepoint discipline is not optional
- One change per commit.
- Build must pass before commit.
- Tag milestones and first-working behaviors.

## 10) Stop conditions
Stop immediately and do not continue “pushing forward” if:
- build fails and the cause isn’t clear
- any mixin reports missing targets / mapping mismatch
- visuals fail (floating, clipping, wrong outline box)
- baseline lane behavior changes
