# AGENTS.md - SLABBED

## Source of truth

Use repo docs and code as the default source of truth. Do not rely on chat memory when repo docs are available.

Read order before non-trivial work:

1. `00_SLABBED_SOURCE_INDEX.md`
2. `01_SLABBED_CANONICAL_DOCTRINE.md`
3. `02_SLABBED_ACTIVE_STATUS.md`
4. Relevant workflow, skill, research note, Bug Blaster, issue, PR, or handoff named in the active prompt.

If docs conflict, cite exact file paths and sections. Follow documented superseding rules. If no superseding rule exists, stop and report the conflict before editing.

Current active Slabbed source pack:

- `00_SLABBED_SOURCE_INDEX.md`
- `01_SLABBED_CANONICAL_DOCTRINE.md`
- `02_SLABBED_ACTIVE_STATUS.md`
- `03_SLABBED_BUG_BLASTERS.md`
- `04_SLABBED_WORKFLOWS.md`
- `05_SLABBED_SKILLS_AND_COMMANDS.md`
- `06_SLABBED_RESEARCH.md`
- `07_SLABBED_ARCHIVE_AND_PRUNE_MAP.md`

Old duplicated source files are historical only unless explicitly named by the active prompt.

## Canonical repo and preflight

Canonical active root:

`/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`

Do not use `/Users/joolmac/CascadeProjects/Slabbed` for new coding unless the prompt explicitly says this is archive or recovery work.

Before edits, builds, proofs, commits, tags, pushes, or release work, run:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD
```

Stop immediately if:

* root is not `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`
* branch is unexpected for the active prompt
* staging is dirty
* tracked dirty files are unrelated or cannot be isolated
* HEAD/tag does not match the active handoff when one is specified
* the task lacks proof context and is not explicitly audit-only

Never auto-stash, reset, discard, rewrite, or stage unrelated files.

## Core product law

Slabbed’s intent is global slab support through named, legal Slabbed state grammar.

Ordinary full blocks should be able to anchor to slabs. This is core product behavior, not an experiment.

Slabbed must not implement support by broad lies. Forbidden unless explicitly proven and regression-defended:

* global solidity lies
* broad sturdy-face lies
* broad shape/support redirects outside slab context
* generic rescue from “looks lowered”
* packet/interact rewrites as substitute for legal state
* unnamed hybrid states

Every slab-supported placement must normalize into one legal state before completion.

A placement hook must do exactly one of these:

1. produce a canonical legal Slabbed state
2. preserve a canonical vanilla state
3. reject or defer placement

Do not create states that only appear to work because model, outline, raycast, collision, survival, or rescue compensate afterward.

## Visual Triad

For any slab-lowered or slab-shifted object, these must agree:

1. model
2. outline
3. raycast / targeting

Client offset authority:

`ClientDy.dyFor(world, pos, state)`

Slab semantics authority:

`SlabSupport`

Rules:

* no duplicate dy logic
* no static mutable current dy
* ThreadLocal only if necessary and cleared in `try/finally`
* no model-only fix
* no outline-only fix
* no raycast rescue pretending to fix visual drift
* stop if only one or two triad surfaces are updated

## Legal state discipline

Known legal vanilla slab states:

* bottom slab, `dy=0`
* top slab, `dy=0`
* double slab, `dy=0`

Known legal Slabbed lowered full-block states:

* ordinary full block anchored on a bottom slab with `dy=-0.5`
* ordinary full block in a proven lowered vertical chain with `dy=-0.5`
* ordinary full block preserved by a valid persistent anchor after original support changes
* ordinary full block side-adjacent to a valid anchored/lowered full-block neighbor when authoring creates the legal lowered state

Known legal lowered slab lane states:

* side-lane `TOP` slab with `dy=-0.5`
* side-lane `BOTTOM` slab with `dy=-0.5`
* side-lane `DOUBLE` slab with `dy=-0.5`
* lowered `DOUBLE` side target lower-half placement producing `BOTTOM dy=-0.5`
* lowered `DOUBLE` side target upper-half placement producing `TOP dy=-0.5`
* lowered `DOUBLE` side lane merge producing `DOUBLE dy=-0.5`
* lowered `TOP` up-click merge producing `DOUBLE dy=-0.5` only when explicitly covered by proof
* real-placed legal lowered `BOTTOM` slab above anchored/lowered ordinary full block only when persistent carrier truth is explicitly proven

Illegal or suspect unless promoted by a future architecture slice:

* any slab type + dy pairing not listed above
* slab type whose vanilla vertical meaning conflicts with assigned dy outside lowered lane grammar
* lowered air as support truth
* lowered state inferred only from neighboring visuals without canonical support relationship
* normal-lane `dy=0` slab produced from valid lowered-lane interaction
* any state that exists only because rescue rewrites targeting later

## Layer selection

Every implementation or debugging slice must name exactly one primary failing layer:

* state authority
* placement
* collision
* survival
* model
* outline
* raycast
* rescue
* proof gap

If the failing layer cannot be identified from evidence, the slice is audit-only. No Java patch.

Do not bundle unrelated fixes. No “while here” changes.

## Standard slice workflow

For current core-contract defects:

1. name the visible player-facing symptom
2. name the failing layer
3. state the legal invariant being protected
4. write or identify a red proof when possible
5. patch only that layer
6. run compile and relevant gametest
7. live-test if the bug involves feel, targeting, ghosting, or moving-up behavior
8. commit, annotated tag, push branch, push tag
9. verify final tree

After two failed fixes, stop patching. Next slice is audit-only.

## Proof rules

Placement success is not survival proof.

For legal Slabbed states, reason about:

* placement predicate
* collision acceptance
* survival predicate
* neighbor update behavior
* reload/relog stability
* model/outline/raycast alignment
* live feel

Live play is final authority for:

* lower-half interaction feel
* targeting theft
* ghost blocks
* weird hitboxes
* moving-up behavior
* “no meaningful live difference” after a supposed fix

If automation passes but live play fails, stop implementation and create a red proof that fails for the same reason live failed before patching again.

Proof fixtures must mirror live source truth. Do not manually add persistent anchor/carrier truth unless live has that truth.

## Required validation commands

Minimum compile gate:

```bash
./gradlew --no-daemon compileJava compileGametestJava
```

Default proof gate:

```bash
./gradlew --no-daemon runClientGameTest --console plain
```

Style gate:

```bash
git diff --check
git diff --cached --check
```

Release gate:

```bash
./gradlew --no-daemon clean build
./gradlew --no-daemon runClientGameTest --console plain
jar tf build/libs/slabbed-*.jar | rg "debug|dev|audit|gametest|test|proof|fixture|lab"
jdeps -recursive -verbose:class build/libs/slabbed-*.jar | rg "com\\.slabbed\\.(debug|dev)|SlabbedDebug|slabbed\\.debug\\.mixins"
```

A release jar is not clean merely because debug files are absent. Packaged runtime bytecode must not hard-link excluded dev/debug classes.

## Goblin / live testing

Use this loop when live play shows ghosting, odd hitboxes, targeting theft, moving-up behavior, or no meaningful difference after a green proof:

1. record exact shape, held item, aim location, and wrong behavior
2. extract one repeated illegal state or contradiction
3. add or identify red proof for that one mechanism
4. fix one layer only
5. run compile/gametest
6. re-goblin same shape
7. savepoint immediately after one confirmed live win

Always provide both the run command and log pull command.

Goblin run:

```bash
./gradlew --no-daemon runClientGameTest --console plain
```

Log pull:

```bash
rg -n "GREEN|RED|FAIL|ERROR|SLABBED|LOWERED|PHASE|GOBLIN|RETARGET|dy=|owner|target|MISS|StackOverflow" build/run/clientGameTest/logs/latest.log build/run/clientGameTest/logs/*.log
```

Adjust log path only if the current Gradle run writes elsewhere.

## Savepoint discipline

One proven or live-confirmed win means:

1. commit intended files only
2. create annotated tag
3. push branch
4. push tag
5. verify final status
6. only then continue

Do not stack multiple proven fixes in one dirty tree.

Never call a Bug Blaster fixed/final until:

* mechanism known
* invariant named
* fix implemented
* proof obtained
* commit created
* annotated tag created
* branch pushed if required
* tag pushed if required
* final tree verified

Bug Blaster may be described only as candidate, proof pending, or savepoint pending before closure.

## Git rules

Only commit changes made for the current task.

Do not include:

* pre-existing dirty files
* unrelated changes
* scratch files
* logs
* build artifacts
* secrets
* `tmp/` unless explicitly requested

Use concise imperative commit messages.

Do not push, tag, move release tags, close issues, update Project state, or mutate GitHub unless the prompt explicitly authorizes it.

For savepoint tasks, push branch and tag only after validation passes.

## Research-first rule

Before novel architecture, new category family, refactor that changes state law, or unfamiliar support behavior, perform prior-art research first.

Required sources when research is needed:

* Modrinth
* CurseForge
* GitHub source
* Fabric/Yarn/Mojang docs where relevant
* known reference mods

Required output:

* prior art
* solution patterns used
* what those projects avoid
* known constraints/failure modes
* decision gate: direct precedent, adjacent precedent, or novel exploration

Do not propose hooks or code before the research summary is complete.

Category expansion is paused until the Slabbed Core Building Contract is stable.

## Superpowers plugin workflow

If the Superpowers plugin is available, invoke `superpowers:using-superpowers` before task-specific skills.

Resolve Superpowers skills through the active registry or tool output. Do not hard-code plugin cache paths.

Superpowers defaults are lower priority than:

1. direct user instructions
2. this `AGENTS.md`
3. active repo docs
4. active issue/task/handoff

Do not use Superpowers workflows to bypass Slabbed preflight, root discipline, savepoint discipline, or proof requirements.

## Scope control

Before non-trivial work, produce a doc-and-code-grounded task map:

* concrete slices
* goal
* expected files/modules
* required tests/proofs
* main regression risks
* authorized now / blocked / later-out-of-scope status

A slice is authorized now only if:

* active prompt or authoritative plan supports it
* no unresolved decision blocks it
* it does not broaden scope
* it preserves Slabbed doctrine

If no single active issue, handoff, task, or prompt is authoritative, stop after the task map and present candidate directions with rationale.

## Edit discipline

Keep unrelated files untouched.

Search before broad reads.

Prefer `rg` and narrow file windows before opening large files.

Avoid creating or expanding mega files without a clear responsibility-based reason.

When moving boundaries, update imports, types, contracts, and tests in the same change.

Do not edit Java/gametest files during docs-only slices.

Do not edit release/version/changelog files unless release work is explicitly requested.

## Debug and tooling boundaries

Debug tools are evidence, not product intent.

Before committing any debug overlay, trace, renderer, fixture, probe, or visual helper, verify:

* explicit flag or dev-only gate
* default state off
* no hardcoded normal Gradle run args
* no gameplay behavior change
* deliberate enable path
* disabling leaves selection, targeting, placement, and rendering intact

Normal play should show the mod, not scaffolding.

## Execution checkpoints

For implementation work, report concise checkpoints:

1. after initial scan: intended files and next step
2. after first meaningful patch
3. before validation: exact commands
4. after validation: pass/fail and blockers

Do not equate passing validation with full completion. State whether the scoped task is:

* fully complete
* complete for scoped slice
* not complete

For review/acceptance, separate:

* blockers
* non-blockers
* missing proofs/tests
* acceptance-criteria coverage gaps

Run a findings-first review pass after diff is stable before claiming implementation work is done.

## Stop conditions

Stop immediately if:

* non-canonical root
* dirty staging
* unrelated dirty files cannot be isolated
* wrong branch/HEAD/tag for prompt
* build fails and cause is unclear
* mixin target/signature mismatch appears
* baseline lane changes unexpectedly
* only part of visual triad is updated
* collision and placement disagree
* survival and placement disagree
* live play contradicts claimed fix after two serious attempts
* illegal or unnamed hybrid state is created
* legal state cannot be stated before implementation
* speculative architecture begins without research
* category scope expands mid-slice
* debug/inspect/release tooling leaks into production behavior
* release jar excludes debug classes but packaged bytecode hard-links them
* proof is obtained but savepoint is not completed and someone tries to call the fix final

## Final response contract

Final response must include:

* verdict: fully complete / complete for scoped slice / not complete
* root
* branch
* old HEAD
* new HEAD if changed
* tag if created
* files touched
* files intentionally untouched
* proof commands run
* proof results
* pushed branch yes/no when relevant
* pushed tag yes/no when relevant
* final tree status
* what remains unproven
* next smallest slice

If no files changed, say so directly.

If scoped work is complete but broader stage remains, say that distinction directly.

## Slabbed shorthand support

`/cc` means produce a concise copy-pasteable handoff with:

* project
* active workflow
* canonical root
* current branch
* current objective
* what happened in session
* current proven state
* important commits/tags/push state
* exact files/subsystems touched
* known remaining local files
* locked findings
* known risks/open questions
* recommended next slice
* do-not-touch boundaries
* Slabbed doctrine/invariants note
* Bug Blaster/source update instructions
* Notion update instructions

`/claude` means produce a comprehensive single-pass Claude Opus handoff with:

* done / partial / not started
* invariants and non-negotiables
* remaining work as ordered slices
* exact systems/files where known
* Windsurf-ready plan
* skills, branches, tags, tests, stop conditions
* token discipline: grep first, avoid rereading, no speculative broad audits

`/hmh` means beginner-friendly, one step at a time, exact commands, explicit stop conditions.

`/storm` means strategy brainstorming only; no repo edits.

`/brutal` and `/scalpel` mean direct critique mode. Prioritize technical truth and risks.

```
```
