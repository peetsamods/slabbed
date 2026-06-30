# 00 — Authority Order

Codex must rank Slabbed information by authority. When two sources disagree, use the higher tier.

## Tier 0 — Repo-local operating law

- `AGENTS.md`
- `SLABBED_SPINE.md`

These override old chats, old Notion notes, and stale source files.

## Tier 1 — Codex handbook docs

- `docs/codex/01-canon-law-purpose.md`
- `docs/codex/02-legal-state-grammar.md`
- `docs/codex/03-visual-triad.md`
- `docs/codex/04-slice-contracts.md`
- `docs/codex/05-preflight-savepoint.md`
- `docs/codex/06-bug-blaster-case-law.md`
- `docs/codex/07-live-test-log-recipes.md`
- `docs/codex/08-compat-contracts.md`
- `docs/codex/09-release-gate.md`
- `docs/codex/10-troubleshooting-when-stuck.md`
- `docs/codex/11-model-thread-policy.md`
- `docs/codex/12-goal-mode.md`
- `docs/codex/13-mixin-layer-wiring-audit.md`
- `docs/codex/14-zoom-out-discipline.md`

Read only the narrow relevant file. Avoid broad rereads.

## Tier 2 — Source pack / Notion exports

Useful for history, but not automatically current unless `SLABBED_SPINE.md` says so.

## Tier 3 — `tmp/` evidence

`tmp/` contains evidence, logs, and proof artifacts. It is not doctrine. It may prove a failure or a fix, but it does not define product intent.

## Tier 4 — Chat memory and pasted handoffs

Use only as supporting context. Never let chat memory override repo-local law.

## Superseded interpretations

Treat these as stale unless a current doc explicitly revives them:

- selective-only slab support
- category expansion as default next work
- debug tools as product behavior
- rescue expansion before RED proof
- placement success as survival proof
- jar purity as contents-only scan
- proof-pending Bug Blasters as fixed
- a 2nd+ narrow patch slice against the same unresolved symptom family,
  taken without first running the zoomed-out audit in
  `docs/codex/14-zoom-out-discipline.md`
