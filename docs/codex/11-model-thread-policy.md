# 11 — Model and Thread Policy

Use the cheapest model that safely completes the task. Expensive models are for uncertainty, fragility, and root-cause reasoning, not clerical repo work.

## Recommended model choices

| Work type | Model | Reasoning |
|---|---|---|
| docs, grep, status, logs, commit/tag/push | gpt-5.4-mini | Low |
| routine implementation patch | gpt-5.3-codex | Medium |
| unclear root cause, regression, interacting systems | gpt-5.5 | High |
| repeated failed attempts, broad architecture, release-critical judgment | gpt-5.5-pro | Extra High |

## Thread choice

Start a new thread for:

- new reasoning context
- new gameplay logic
- failed attempt recovery
- model escalation
- broad diagnostics

Continue existing thread for:

- same-context savepoint
- log extraction
- build rerun
- small docs/status reconciliation
- commit/tag/push hygiene

## Escalation report required

Before escalating, report:

```text
What was tried:
What was observed:
What is still unknown:
Why the cheaper model is insufficient:
Recommended stronger model:
Next smallest slice:
```

## Token discipline

- grep before reading
- read minimal windows
- avoid rereading broad history
- do not perform broad audit unless stuck
- use `SLABBED_SPINE.md` for current truth
- stop instead of wandering
