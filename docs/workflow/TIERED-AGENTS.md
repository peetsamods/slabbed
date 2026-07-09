# Tiered Agent Workflow

A model-tiered coding workflow: the right model does the right job, so the
expensive, context-sensitive reasoning stays at the top and the bulk of the
token volume flows to cheaper models. Built on Claude Code **subagents** — each
agent pins its own model and tool set.

## The hierarchy

| Tier | Agent | Model | Role |
|------|-------|-------|------|
| Plan / Evaluate | `architect` | **Fable** | Ingest goal, explore read-only, decompose into handoff packages, route work, do the high-level final eval. Never writes code. |
| Review | `reviewer` | **Fable** | Read diffs + worker reports against the plan and conventions. Read-only verdict. |
| Implement | `developer` | **Opus** | The heavy lifting: real implementation code, owns the build/fix/retry loop. |
| Assist | `test-writer` | **Sonnet** | Boilerplate unit tests, mocks, fixtures, smoke checks — repetitive, well-specified work. |
| 2nd opinion / troubleshoot | Codex | **OpenAI Codex** (CLI) | Independent review of the diff (feeds the reviewer's verdict) and on-demand troubleshooting when a worker is stuck. An external CLI, not a Claude subagent — optional but recommended. |

Why it works: the architect/reviewer (Fable) never ingest massive log output or
raw code churn, so their context stays compact. The trial-and-error loop — where
most tokens are burned — lives in Opus/Sonnet workers. And because **Codex** is a
different model family, running it as a second reviewer catches bugs a single
model's blind spots would let through — the Fable reviewer then adjudicates its
findings rather than accepting them blindly.

## Two things that make or break the savings

This pattern only pays off if two boring disciplines hold. Both are baked into
the agents and templates, but they're worth understanding:

1. **Explicit handoffs, or the cheap tier wanders.** A worker on a cheaper model
   only saves tokens when its package is unambiguous. Give it a fuzzy spec and it
   will confidently head down the wrong path and burn more than you saved. So the
   architect must pass a **Definition of Ready** before routing (files named,
   acceptance criteria testable, scope bounded, no open questions — see
   `HANDOFF-TEMPLATE.md`), and every worker **pre-flights the package and STOPS on
   ambiguity** instead of guessing. Unresolved decisions become
   `Status: blocked-needs-decision` and go back to the human, not to a worker.
2. **A boring shared state file, or the reviewer reconstructs everything.**
   Without durable run state, the reviewer has to rebuild the whole run from chat
   history and worker narratives — which burns the savings you just made. So every
   tier updates `handoffs/RUN-STATE.md`: what changed, what tests ran and their
   result, what's still broken, what's next. The reviewer (and the architect's
   final eval) reads that file instead of reconstructing. Subagents run in
   isolated context and can't see each other's chat, so a file on disk is the
   right shared-memory mechanism.

## The loop

```
        ┌──────────────────────────────────────────────┐
Goal ──▶ │  ARCHITECT (Fable)                            │
        │  restate goal → explore read-only → plan →    │
        │  write handoff packages → route by tier       │
        └───────────────┬──────────────────────────────┘
                        │  handoff packages (handoffs/packages/*.md)
          ┌─────────────┴───────────────┐
          ▼                             ▼
   DEVELOPER (Opus)              TEST-WRITER (Sonnet)
   implementation               tests / mocks / fixtures
   + build loop                 smoke checks
          │                             │
          └─────────────┬───────────────┘
                        │  synthesized reports (no raw logs)
                        │        ┌──────────────────────────┐
                        │        │  CODEX (CLI)             │
                        ├───────▶│  scripts/codex-review.sh │
                        │        │  → P1/P2/P3 findings     │
   (stuck worker ┄┄┄┄┄┄┄┘        └────────────┬─────────────┘
    troubleshoot via                          │  findings
    codex exec)                               ▼
        ┌──────────────────────────────────────────────┐
        │  REVIEWER (Fable) → adjudicate + verdict       │
        │  (its own read + Codex's findings)             │
        │  approve / changes-required (routed back down) │
        └───────────────┬──────────────────────────────┘
                        ▼
              ARCHITECT final eval → commit
```

## How to run it

You (the human) talk to your top-level Claude Code session. Two ways to drive
the tiers:

**1. Let the orchestrator delegate.** Just describe the goal and ask it to use
the workflow — the easiest way is the bundled `/tier` slash command:

> `/tier add rate-limiting to the public API endpoints`

Or spell it out:

> Use the tiered workflow: have the **architect** plan this, then run the
> packages on the **developer** and **test-writer**, then get a **reviewer**
> verdict before we commit.

Claude Code routes each phase to the matching subagent (by the `name:` in the
agent file), and each subagent runs under its pinned model with its own clean
context window.

**2. Invoke a tier directly** when you already know what you want:

> Ask the **test-writer** to scaffold unit tests for `services/rate_quote.py`
> following `handoffs/packages/PKG-...-rate-tests.md`.

### Typical sequence

1. **Architect** → produces the plan + `handoffs/packages/*.md`, one per unit,
   each tagged with a tier.
2. **Developer** → implements the packages, owns the build/test loop. If stuck,
   consults **Codex** (`codex exec`) for troubleshooting.
3. **Test-writer** → scaffolds/fills the test coverage the packages call for.
4. **Codex review** → `bash scripts/codex-review.sh` for an independent second
   opinion (P1/P2/P3 findings).
5. **Reviewer** (Fable) → reads `handoffs/RUN-STATE.md` + the diff + Codex's
   findings, adjudicates, returns a structured verdict (recorded back to RUN-STATE).
6. **Architect** → final eval; anything failing routes back to the right worker.
7. Commit.

## Notes / knobs

- **The main session is the orchestrator, not Fable.** Whatever model you launch
  Claude Code with is what the top-level chat runs on. You keep Fable at the top
  by *delegating* planning/review to the `architect` and `reviewer` subagents —
  which is exactly what `/tier` does. Don't let the main chat do the planning
  itself.
- **Model aliases.** Agent files use `model: fable | opus | sonnet`. If your
  Claude Code build doesn't resolve the `fable` alias, replace it with the full
  identifier `claude-fable-5` in `architect.md` and `reviewer.md`. `opus` and
  `sonnet` resolve to the current Opus / Sonnet. Use `inherit` to make an agent
  match the main session's model.
- **Tools are least-privilege.** The architect and reviewer have no
  `Write`/`Edit` on code (architect gets `Write` only to emit handoff packages);
  workers get the full file+Bash set. Tighten or loosen in each file's `tools:`
  line.
- **Where things live.** Agents: `.claude/agents/*.md`. Slash command:
  `.claude/commands/tier.md`. Packages the architect emits: `handoffs/packages/`.
  Shared run log: `handoffs/RUN-STATE.md`. This doc + the template:
  `docs/workflow/`.
- **Cost intuition.** Keep Fable at the two ends (plan + evaluate) and route the
  voluminous middle to Opus/Sonnet. If a task is small, skip the ceremony and
  just ask the developer directly — the tiering pays off on multi-step work.
- **Codex is optional but recommended.** The review gate and troubleshooting use
  the OpenAI Codex CLI (`codex exec`). Install it
  (https://github.com/openai/codex) and run `codex login` once. Without it,
  `scripts/codex-review.sh` exits cleanly and the Fable reviewer proceeds on its
  own — you just lose the cross-model second opinion. Codex reviews are saved to
  `reviews/` for traceability.
- **Adapt to your repo.** The agents reference generic "project conventions /
  invariants docs." Point them at your actual files (e.g. `CONTRIBUTING.md`, a
  `CLAUDE.md`, an architecture doc) for best results.
