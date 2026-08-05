# AGENTS.md - Slabbed 26.2 Port Rules

These rules apply to the dedicated MC 26.2 port checkout:

```text
$HOME/CascadeProjects/Slabbed-port-26.2
```

Do not apply this file to any other Slabbed tree.

## LAW — read first, above everything

**`LAW.md` is required reading #1 and is supreme over every other doc, including this one.** If anything
below conflicts with LAW.md, LAW.md wins. The one law: *where a block is placed is where it goes and
STAYS; height is frozen at placement and a neighbor update never changes it.* Before you edit
`src/main/**`, you must be able to answer **"does this change let a neighbor edit move an already-placed
block?"** — and record the answer in the commit trailer `LAW-PREFLIGHT: n` (or `y` + Maintainer's sign-off).
The `commit-msg` hook enforces this; install it once with `git config core.hooksPath tools/hooks`.

## Required First Reads

Before any Slabbed 26.2 port work, read:

1. **`LAW.md`** — the one law (supreme). Nothing you do may violate it.
2. `docs/audits/WYSIWYG-LAW-VIOLATION-POSTMORTEM.md` — how the law was violated for months, and the guardrails.
3. `HANDOFF.md`
4. `SLABBED_SPINE.md`
5. `docs/process/LIVE_LEDGER.md` — the live-verification gate (no stacking behavior changes unseen).
6. Relevant `docs/**` notes for the active task.

Treat `SLABBED_SPINE.md` as the current operating context, but verify it against Git before edits.

### Maintainer-authorized protected-document exception

An active Maintainer instruction or monitor packet may declare exact documentation paths
`protected_document_paths` and provide an exact `allowed_read_paths` manifest. In that case:

- the named protected paths are **path/status only**, even when they appear in the required-first-read
  list above;
- do not open, print, diff, hash, grep, ripgrep, search, summarize, or pass those paths to a subagent;
- do not run repository-wide content searches, `rg .`, `rg docs`, or any search whose roots can contain
  a protected path;
- use only the exact `allowed_read_paths`, preferably through
  `$HOME/.codex/skills/slabbed-preflight/scripts/safe_rg.py`;
- treat the architect's task-owned handoff/proof capsule as the substitute for unavailable operating
  context; if it is insufficient, stop and request a narrower packet instead of opening a protected
  document;
- every delegated worker inherits the same read allowlist and protected-path prohibition.

`LAW.md` remains required before `src/main/**` mutation unless Maintainer explicitly names it as protected.
If it is protected, source mutation is blocked until Maintainer supplies an approved law capsule. This
exception never authorizes weakening the law, modifying protected dirt, or skipping Git preflight.

## Required Preflight

Before edits, builds, tests, commits, or savepoints, run:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD
```

Hard stop unless the root is:

```text
$HOME/CascadeProjects/Slabbed-port-26.2
```

If the tree is dirty, inspect only the files relevant to the intended slice before editing. Do not auto-stash, clean, reset, or revert unrelated work.

## Tiered Agent Workflow (the Fable tiered workflow, adapted for Slabbed)

Slabbed runs the **Fable tiered model workflow**: the right model does the right job, the expensive
context-sensitive reasoning stays at the two ends (plan + evaluate), and the high-volume trial-and-error
middle routes to cheaper models. It operates **entirely under LAW.md and the `tools/hooks` guardrails** —
the workflow never overrides the law, the S-2 gate, or the commit rules below. Reference specs live in
`docs/workflow/` (`TIERED-AGENTS.md`, `HANDOFF-TEMPLATE.md`); the run log is `handoffs/RUN-STATE.md`;
agent definitions are in `.claude/agents/`.

### Roles and model tiers

| Tier | Agent | Model | Role | Writes code? |
|------|-------|-------|------|--------------|
| Plan / evaluate | **architect** | Fable (`claude-fable-5`) | Restate goal, explore read-only, decompose into handoff packages, gate the Definition of Ready, route by tier, do the final eval. | No (Write only to emit packages) |
| Implement | **developer** | Opus (`claude-opus-5`) | The heavy lifting: real logic, cross-module changes, the build/fix/retry loop. | Yes |
| Assist | **test-writer** | Sonnet (`claude-sonnet-5`) | Boilerplate tests, mocks, fixtures, smoke checks, well-specified repetitive work. | Yes (tests/fixtures) |
| Review | **reviewer** | Fable (`claude-fable-5`) | Read the diff + worker reports **against LAW.md, RULES.md, the plan docs, and the acceptance criteria**, then return a structured verdict and **decide if the commit is ready**. | No (read-only) |
| Adversarial sweep | **sweeper** (panel) | tiered by task | Independent hostile review — try to break the change. Panel intelligence matches the task (see below). | No (read-only) |

**You (the main session) are ALWAYS the project architect.** You hold the architect role in the main
context — you plan, decompose, route, and do the final eval; you do not drop into the trial-and-error
loop yourself. Delegate implementation to **developer**, repetitive tests to **test-writer**, and the
diff-depth review to **reviewer**. Keep a Fable-clean head at the top: don't ingest raw build logs or
churn — workers report a synthesized result and log it to `handoffs/RUN-STATE.md`.

### Assign the most appropriate model/intelligence to every subagent

Match the tier to the token/intelligence profile, not prestige:
- Novel logic, cross-module, law-sensitive height/placement work → **developer (Opus)**.
- Scaffolding, mocks, fixtures, added gametest rows, smoke checks → **test-writer (Sonnet)**.
- Planning, routing, final eval, and the commit-gating review → **architect / reviewer (Fable)**.
- **Adversarial sweeper panel** (the standing Slabbed pattern): tier each sweeper to its lens —
  - **high** (architecture parity, LAW.md compliance, donor-vs-port divergence, multiple dy authorities, lifecycle/state) → Fable or Opus (`inherit` the main model when unsure);
  - **medium** (test adequacy, false-green risk, RED-matches-symptom, mutation coverage) → Sonnet;
  - **clerical** (callers/imports/registration/wiring/docs, no architecture judgment) → Haiku (`claude-haiku-4-5`).
  Run them in parallel (one message, multiple Agent calls). **You verify/reject every sweeper finding
  locally before acting on it** — never relay a finding unverified.

### The loop (per unit of work)

1. **Architect (you)** — restate the goal; explore read-only; write one self-contained handoff package
   per modular unit to `handoffs/packages/<slug>.md` (use `docs/workflow/HANDOFF-TEMPLATE.md`); gate each
   on the Definition of Ready (files named, acceptance criteria testable, scope bounded, test command
   given, **LAW-PREFLIGHT answered**, no open questions). If a real decision is unresolved, mark it
   `Status: blocked-needs-decision` and surface it to Maintainer — do NOT route. Reset `handoffs/RUN-STATE.md`.
2. **Developer / test-writer** — execute the packages under their pinned models; own the build loop;
   report a synthesized result (files changed one-line each, test pass/fail, deviations) to RUN-STATE.
3. **Adversarial sweeper panel** — hostile review, tiered as above; findings verified by you.
4. **Reviewer (Fable)** — read the diff + reports **against LAW.md / RULES.md / the plan / acceptance
   criteria**, adjudicate sweeper + (optional) Codex findings, and return the verdict + a
   **commit-ready: yes/no** decision. Route required changes back down; the reviewer never edits code.
5. **Architect (you)** — final eval. **On a successful pass, COMMIT** (see the commit rule below), then
   advance to the next unit. Anything failing routes back to the right worker.

Optional cross-model second opinion: `scripts/codex-review.sh` (OpenAI Codex, a different model family).
If the CLI is absent it exits cleanly and the workflow proceeds on the sweeper + reviewer verdict.

### Commit after every successful pass (Maintainer's rule)

**A commit is made after each successful pass** — reviewer says commit-ready and the gates below are
green. Do not batch multiple units into one commit; one reviewed unit → one commit. Every commit still
goes through the `tools/hooks` guardrails:
- **LAW-PREFLIGHT** trailer on any `src/main` change (S-4).
- The **S-3 tripwire** (no new neighbor-recompute vocabulary without `LAW-SIGNOFF` + an invariance row).
- **S-2** (`NeighborUpdateInvarianceTest`) — the law gate; a behavior change that would let a neighbor
  move a placed block cannot be called a successful pass.
- **S-5** — no second behavior-changing commit stacks without a recorded live pass in
  `docs/process/LIVE_LEDGER.md`. Behavior changes therefore stop for Maintainer's live A/B between passes.

Keep the work one unit at a time; name files, don't paste bodies; stage only intended files; never
invent commits, tags, pushes, tests, or proof.

## 26.2 Port Defaults

- Branch is expected to be `port/mc-26.2-0.4.1-beta.1` unless Git proves otherwise.
- Treat this checkout as an experimental port workspace until a clean savepoint proves otherwise.
- Java 25 / Gradle 9.4.x / Fabric Loom / mappings decisions must be proven from the active checkout or a verified donor, not guessed from broad cache scans.
- A green `buildEnvironment` is not proof that `compileJava` is green.

## SLABBED_SPINE.md Update Discipline

Update `SLABBED_SPINE.md` when current operating truth changes:

- root / branch / HEAD / tag changes
- active port blocker changes
- next safe action changes
- compile frontier changes
- mapping/provider/classpath proof changes
- a stop condition is hit and the next agent needs the reason
- a stale or dangerous interpretation is discovered

Do not use `SLABBED_SPINE.md` as a scratchpad, full running log, commit index, or research notebook.

At every savepoint, either update `SLABBED_SPINE.md` or explicitly report:

```text
SLABBED_SPINE.md unchanged because current operating truth did not change.
```

## Savepoint Discipline

A savepoint is not complete until:

1. intended files only are staged
2. validation passed
3. commit created
4. annotated `save/...` tag created
5. branch pushed
6. tag pushed
7. final tracked tree verified
8. `SLABBED_SPINE.md` is updated or explicitly declared unchanged

If any of those are missing, report the state as WIP, not a savepoint.

## When Blocked

Stop after two failed attempts or when evidence becomes unclear. Report:

- tried
- observed
- proven
- unproven
- next smallest audit

Do not continue wandering through adjacent source families.

## Compact Report Format

For repo work, use:

```text
Root:
Branch:
HEAD:
Tree:
Task:
Files inspected:
Files changed:
Finding:
Proof:
Status:
Next slice:
```
