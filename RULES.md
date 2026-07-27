# Slabbed — Rules (Development Guardrails)

These rules are intentionally strict. Slabbed must remain predictable, reversible, visually correct, and aligned with the actual product intent. The current canonical intent is global slab support: ordinary full blocks anchoring on slabs is intended product behavior, and past selective-only framing caused regressions and project drift. :contentReference[oaicite:0]{index=0} :contentReference[oaicite:1]{index=1}

## 1) Global slab support is the product intent
- Ordinary full blocks anchoring on slabs is intended product behavior.
- This is not a side feature, not an experiment, and not a future optional expansion.
- Selective-only notes that imply ordinary full blocks should not anchor to slabs are stale and must not be treated as project law.
- Special handling may still exist for specific categories, but those are implementation details inside a globally slab-supporting mod, not evidence of a selective-only product policy. :contentReference[oaicite:2]{index=2} :contentReference[oaicite:3]{index=3}

## 2) No global redirects without proof
- Do not use broad `@Redirect` hooks on shared helpers (shape, solid, support checks) unless you can demonstrate no behavior change outside the intended slab context.
- Default to narrow `@Inject` hooks with tight conditions and early returns.
- Do not reintroduce broad “global lies” about solidity or support just to force behavior. The ghost-face history already proved how expensive that is. :contentReference[oaicite:4]{index=4}

## 3) Baseline lane is sacred
- The full-block lane must remain correct relative to the intended product.
- For Slabbed, that means ordinary full blocks anchoring on slabs must continue to work.
- If a change regresses ordinary full-block anchoring on slabs, stop and treat it as a real product regression, not as an optional carve-out.
- If a change alters unrelated vanilla baseline behavior outside intended slab semantics, stop and revert to the last known good tag. :contentReference[oaicite:5]{index=5}

## 4) Slab support logic is single-source
- All slab support semantics must route through `SlabSupport`.
- Do not duplicate slab checks inside mixins beyond “call helper and act”.
- Do not create competing local definitions of what counts as slab support.
- If product intent changes, update `SlabSupport`-based rules centrally instead of scattering policy across mixins. :contentReference[oaicite:6]{index=6}

## 5) Never expand non-slab partial-block semantics silently
- Global slab support does **not** mean global support for every partial block family.
- Do not broaden support to stairs, fences, walls, trapdoors, panes, or other partial blocks without:
  - an explicit decision
  - a dedicated branch
  - a dedicated test sweep
  - a dedicated tag
- Keep the scope broad for slabs, but explicit for non-slab partial blocks. :contentReference[oaicite:7]{index=7} :contentReference[oaicite:8]{index=8}

## 6) The visual triad is non-negotiable
For any slab-lowered or slab-shifted object, these three must agree:
1. Model
2. Outline
3. Raycast

- If only one or two are updated, the feature is broken even if placement appears to work.
- There must be exactly one shared dy authority.
- No duplicated dy logic.
- No shared mutable “current dy” state.
- Any change that updates only one or two triad surfaces must stop immediately. :contentReference[oaicite:9]{index=9}

## 7) Visual audit gate is mandatory
- No `slabbed-<category>-pass` tag unless the category passes visual alignment audit.
- “Functionally correct but visually wrong” is still wrong.
- “Looks right but clicks wrong” is still wrong.
- “Works once but drifts after updates/reload” is still wrong. :contentReference[oaicite:10]{index=10}

## 8) Shared hooks require regression proof
- If you choose a shared hook over targeted mixins, you must run a regression sweep of all previously passing categories and core broad-anchoring behavior.
- If any regression occurs, revert and move to targeted mixins or reduce scope.
- Shared hooks are allowed only when their blast radius is understood and defended. :contentReference[oaicite:11]{index=11} :contentReference[oaicite:12]{index=12}

## 9) Placement and survival are separate
- Placement success does not prove survival success.
- A block that places and then pops off later is still a failure.
- Every relevant slice must explicitly reason about:
  - placement predicates
  - survival predicates
  - neighbor updates
  - reload/relog stability
- Do not declare success until all applicable paths are proven. :contentReference[oaicite:13]{index=13}

## 10) Manual live verification outranks automated proof for feel bugs
- Automated proof is necessary but not sufficient for interaction-feel bugs.
- If headless tests, screenshots, or representative gametests say “pass” but live play still feels wrong, trust the live report.
- This especially applies to:
  - lower-half interaction feel
  - rescue/crosshair targeting feel
  - “it still doesn’t feel like it’s targeting what I’m looking at”
- For those bug classes, manual live verification is the final gate. :contentReference[oaicite:14]{index=14}

## 11) Rescue boundary discipline
- There must be exactly one post-vanilla crosshair rewrite site unless a later slice proves otherwise.
- Lowered visuals are not automatically valid rescue targets.
- A non-BE class may only get rescue logic if it has a class-owned targeting or ownership signal, not merely participation in shared slab-support lowering.
- Proven rescue targets so far: lowered block entities, torch family, bed family.
- Audited no-go targets so far: chain, crafting table.
- Do not broaden rescue from generic shared support checks alone.
- Do not add packet/interact rewrite logic as a substitute for hit-ownership proof. :contentReference[oaicite:15]{index=15}

## 12) One slice, one category, one branch when category work is involved
- Never mix multiple category slices in one branch.
- Branch naming: `feat/<category>-on-slabs`
- Tag naming: `slabbed-<category>-pass`
- If the work is a core regression fix rather than a new category, keep the scope to one subsystem and one failure mode per pass.
- Do not stack fixes just because they seem nearby. :contentReference[oaicite:16]{index=16} :contentReference[oaicite:17]{index=17}

## 13) Savepoint discipline is not optional
- One change per commit.
- Build must pass before commit.
- Tag milestones and first-working behaviors.
- Commit only intended files.
- Keep the project recoverable.
- If you cannot explain what changed and why in one slice, the slice is probably too broad. :contentReference[oaicite:18]{index=18} :contentReference[oaicite:19]{index=19}

## 14) If the same failure happens twice, document it
- When a failure mode repeats, add a short note to:
  - the relevant skill, or
  - `INSTRUCTIONS.md`, or
  - this rules file if it is truly constitutional
- This is how the project learns and avoids paying for the same mistake again. :contentReference[oaicite:20]{index=20}

## 15) Research first for novel architecture or unfamiliar slices
- Before proposing a new architecture, novel feature family, or unfamiliar support pattern, perform prior-art research.
- Identify:
  - existing mods attempting similar behavior
  - patterns they use
  - what they explicitly avoid
  - known failure modes
- If no prior art exists, say so plainly and treat the slice as novel exploration.
- Do not speculate past the evidence. :contentReference[oaicite:21]{index=21} :contentReference[oaicite:22]{index=22}

## 16) Protected historical invariant: carpet + global model dy coexistence
- Do not break the “perfect hotfix.1b” coexistence rule casually.
- Global model shift remains in the quad pipeline.
- Carpets are a special model-dy override case inside that same path.
- Carpet outline recursion protections must remain intact.
- Do not add a second competing global model-translate path without proof.
- If carpet or global offset regressions return, check this invariant first. 

## 17) Hard stop conditions
Stop immediately and do not continue pushing forward if:
- build fails and the cause is unclear
- any mixin reports missing targets or mapping mismatch
- visuals fail (floating, clipping, wrong outline box)
- only part of the triad has been updated
- ordinary full-block anchoring on slabs regresses
- unrelated baseline vanilla behavior changes
- the same live symptom persists after two serious attempts
- the work becomes speculative instead of evidence-based

When stuck in a loop, stop implementation and run a static audit pass before proposing another patch:
- confirm the fix is actually present in the branch
- validate all `*.mixins.json` files
- confirm `fabric.mod.json` registers the relevant mixin config(s)
- build and inspect the produced jar
- confirm Loom/source-set wiring if split environments are involved

Only after the audit identifies a concrete mismatch may a new patch be proposed. :contentReference[oaicite:24]{index=24}

## 18) Definition of done
A slice is only done when all relevant lanes and behaviors pass.

Where applicable, that includes:
- full blocks lane
- bottom slabs lane
- top slabs lane
- placement
- survival after neighbor update
- survival after reload/relog
- model alignment
- outline alignment
- raycast/interact alignment
- live manual sanity check for feel/targeting bugs

Not done:
- build passes
- screenshots look close enough
- one representative block worked
- automated proof said pass
- the behavior is “probably fine”

Done means the behavior is actually correct. :contentReference[oaicite:25]{index=25} :contentReference[oaicite:26]{index=26}