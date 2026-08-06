# Slabbed — Rules (Development Guardrails)

> **See [`LAW.md`](LAW.md) — this document does not redefine the law.** LAW.md is supreme; where this file conflicts with it, LAW.md wins and this file is wrong.

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
- ~~Carpets are a special model-dy override case inside that same path.~~ **SUPERSEDED
  2026-08-06 (Maintainer's ruling, L19).** The carpet model-dy override WAS the bug: `ClientDy.dyFor`
  held its own anchor-blind carpet check, so the model and the outline drew carpets flush while
  the shared authority said they were lowered. **Carpets now take the same `getVisualYOffset` as
  every other block, and `ClientDy.dyFor` is a pure delegate to it.** Do not re-introduce a
  per-class dy opinion on the client — `ClientCarpetDyAuthorityTest` fails immediately if you do.
  Maintainer's binding law: *"everything should be able to lower; no exceptions."*
- **Carpet outline recursion / double-offset protections must remain intact — this half is NOT
  superseded and is the live half of this rule.** Exactly ONE layer may offset a carpet's outline:
  `CarpetDyShapeMixin` (client). `SlabSupportStateMixin.slabbed$offsetOutline` (common) must keep
  skipping carpets, or the shape is offset twice. The client owns it because `CarpetBlock` does not
  override `getCollisionShape`, so a carpet's collision box IS its outline and offsetting it on the
  common side would move SERVER physics. Guarded by
  `SlabbedLabFixtureTest#carpetOutlineNotDoubled`.
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

## 19) Standard regression triggers are tested before every release and every port
The same bug classes regress on every loader/version fork — snaps and never-pop, the visual
triad, smoosh/double-offset under Terrain Slabs, world-hole DODOs, particles that don't follow
the model, state-change jitter. They are enumerated permanently in
[`RELEASE_REGRESSION_TRIGGERS.md`](RELEASE_REGRESSION_TRIGGERS.md).

- Before any build is called release-ready — and before any port is called "ported" — every
  **AUTO** row in that checklist must be green (`./gradlew runGameTest`) and every **LIVE** row
  must have a fresh in-game confirmation.
- When you fix one of these, add or greenlight its AUTO gametest so the next port catches the
  regression for free. Do not rely on remembering to eyeball it.
- Cross-branch status of every row lives in [`PORT_FIX_MATRIX.md`](PORT_FIX_MATRIX.md) — check
  it before assuming a fix on `main` also applies to another loader/version line, and before
  assuming another line hasn't already solved something `main` still has open. A survey step
  must check what's actually **compiled and registered** (build.gradle include-lists, mixin
  json registration), not just what exists under `src/` — see that file's "written but never
  wired / excluded from the build" failure modes.
- The **`dy` values themselves are specified**, not folklore: [`DY_SPEC.md`](DY_SPEC.md) is the
  version-invariant oracle (each (block-role × config) → required dy + its law), enforced by
  `DySpecificationTest`. Prefer proving a behavior against the spec headlessly over discovering
  it live. When you fix a dy behavior, add/append its spec row so it becomes a permanent,
  portable assertion — the spec + its test are what a new port runs to know what to fix BEFORE
  launching the game. A dy value is "version-specific" only if it's a real product decision
  (then update the spec and every port) — otherwise a divergence is a port bug, not an exception.
- A fix "tested" only on the model, or only on the outline, or only headless, is NOT done — see
  §6 (the triad is non-negotiable) and §10 (live verification outranks automated proof for feel
  bugs). Headless-green and live-confirmed are DIFFERENT claims; never conflate them in a commit
  body or a doc.

## 20) Debug tooling ships in every jar, off-switchable — never compiled out
`/slabdy` (and any successor debug tooling) is **present in every build** and never stripped at
compile time. It exists so a live tester can read the truth on screen instead of sending a video.

- The passive target-dy overlay defaults ON on test/debug builds (a player should not have to
  invoke a command to see it). A clean release cut turns it off by build flag
  (`-Dslabbed.targetDyOverlay=false`), NOT by deleting the code.
- Keep it ambient-cost-free: the overlay only draws when enabled; `record`/`row`/`use` only run
  when invoked. Gated trace machinery stays off by default.
- Do not re-introduce a "release-stripped, no tooling" build. If a past doc says main is
  stripped of `/slabdy`, it is superseded by this rule.

## 21) Verification is complete and calibrated, or it is not done — [`VERIFICATION_PROTOCOL.md`](VERIFICATION_PROTOCOL.md)
The recurring failure here is INCOMPLETE verification stated with OVERCONFIDENCE: a fix applied to
one call site / one input, tested on one case, declared "done" while siblings stay broken behind a
green test. This rule makes the missing steps mandatory gates. Before any change is called done:
- **G1 enumerate the full input domain** (every call site + every input category, with counts) and
  state coverage as covered/total with the uncovered items NAMED. "Fences" is not a domain.
- **G2 sweep EVERY call site** of any shared function/predicate you touched; classify each
  must-change / must-not-change / unaffected. (The absence of this step caused the TS-smoosh half-fix.)
- **G3 no green is trusted until seen RED** for the right reason (RED-first for fixes; mutation for
  characterization). Cover the domain, not one representative.
- **G4 run the adversarial/seek-failure pass by DEFAULT** (self-attack for small; fan-out workflow
  for large) — the user must never have to ask for rigor.
- **G5 OVER-CLAIMING IS BANNED.** No bare "done / verified / complete / conforms / all / proven /
  invariant" unless G1 is 100%. Tag every claim: `EXHAUSTIVE` / `PARTIAL[scope]` / `HEADLESS-ONLY`
  / `UNVERIFIED`. Default to understatement; state what is NOT covered every time. "Suite green" ≠
  "behavior correct".
- **G6 end with the completeness critic**: "what did I NOT check?" — logged as a known gap in
  [`KNOWN_INCOMPLETE.md`](KNOWN_INCOMPLETE.md), never omitted. That ledger is the canonical home for
  real-but-unfixed / unproven / live-only gaps; a category is not "done" while its rows are open.

The one-line test: could a hostile reviewer name one input, one call site, or one triad member I
did not check? If yes, it is a `PARTIAL[…]`, not "done".