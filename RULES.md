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

### Current slab-sit product policy
- Current slab-sit categories are selective, not broad full-block anchoring.
- Allowed examples: chest, barrel, furnace, jukebox, crafting table, carpet, chain.
- Excluded examples: stone, dirt, sand, planks, cobble, terracotta.
- Broad support for ordinary solid full blocks anchoring on slab tops would be a separate scoped feature, not a bugfix.

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

## 10) Rescue boundary discipline
- There must be exactly one post-vanilla crosshair rewrite site unless a later slice proves otherwise.
- Lowered visuals are not automatically valid rescue targets.
- A non-BE class may only get rescue logic if it has a class-owned targeting or ownership signal, not merely participation in shared slab-support lowering.
- Proven rescue targets so far: lowered block entities, torch family, bed family.
- Audited no-go targets so far: chain, crafting table.
- Do not broaden rescue from generic `isSlabSitCandidate(...)`, generic ceiling-attached logic, or generic shared support checks alone.
- Do not add packet/interact rewrite logic as a substitute for hit-ownership proof.

## 10) Stop conditions
Stop immediately and do not continue “pushing forward” if:
- build fails and the cause isn’t clear
- any mixin reports missing targets / mapping mismatch
- visuals fail (floating, clipping, wrong outline box)
- baseline lane behavior changes
