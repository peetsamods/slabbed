# Handoff Package Template

A **handoff package** is the unit the Architect hands to a worker (developer or
test-writer). It must be **self-contained**: a worker with zero prior context
should be able to execute it cold and know exactly when it's done.

The Architect writes one package per modular unit of work to
`handoffs/packages/<short-slug>.md`. Copy the block below and fill every field.
Delete a section only if it genuinely doesn't apply, and say why.

---

## Definition of Ready (the architect must pass this BEFORE routing)

A cheaper worker model only saves tokens if the package is unambiguous. Hand it a
fuzzy spec and it will confidently head down the wrong path and burn more than you
saved. So a package may not go to a worker until ALL of these are true:

- [ ] Every file the worker will touch is named (or a clear search path is given).
- [ ] Acceptance criteria are concrete and testable (not "works").
- [ ] The out-of-scope list is filled in.
- [ ] The test command + expected result are specified.
- [ ] There are **no open questions**. If a real decision is unresolved, do NOT
      route it — set `Status: blocked-needs-decision`, surface the question to the
      human, and hold the package until it's answered.

If you can't satisfy this, the task isn't ready to delegate — tighten the package
or escalate the question first.

---

## Copy-paste template (Markdown)

```markdown
# Handoff: <short imperative title>

- **Package ID:** PKG-<yyyymmdd>-<slug>
- **Status:** ready | blocked-needs-decision   # only "ready" packages get routed
- **Tier:** developer | test-writer
- **Model:** opus | sonnet         # matches the tier's agent
- **Depends on:** <other package IDs, or "none">
- **Blast radius:** none | low | high — note anything risky (payments, auth, data, external calls)

## Goal
<1–2 sentences. What outcome this package produces.>

## Context the worker needs
<Only what's required to do THIS task. Link files/symbols, don't paste them.>
- Relevant files: `path/one.py`, `path/two.tsx`
- Relevant docs/conventions: <e.g. CONTRIBUTING.md, an architecture note>
- Prior decisions: <anything the architect already settled>

## Scope
### In scope
- <bullet the exact changes>
### Out of scope
- <what NOT to touch — prevents scope creep>

## Implementation notes
<Approach, gotchas, patterns to follow.>

## Acceptance criteria
<Checklist the worker self-verifies against before reporting done.>
- [ ] <criterion 1>
- [ ] <criterion 2>

## Tests
- Command: `<your test runner, e.g. npm test / pytest -k ...>`
- Expected: <what green looks like; new tests to add>

## Report back
Synthesized summary only (files changed one-line each, test pass/fail, deviations,
deferrals). No raw logs.
```

---

## Optional: JSON variant (for programmatic routing)

If you'd rather emit machine-routable packages, use this shape instead. The
fields mirror the Markdown template.

```json
{
  "package_id": "PKG-20260101-validate-inputs",
  "title": "Add input validation to the intake endpoint",
  "tier": "developer",
  "model": "opus",
  "depends_on": [],
  "blast_radius": "high",
  "goal": "Reject malformed payloads before they reach the data layer.",
  "context": {
    "files": ["api/intake.py", "schemas/record.py"],
    "docs": ["CONTRIBUTING.md", "docs/architecture.md"],
    "prior_decisions": ["Use the framework's validators, not manual checks."]
  },
  "scope": {
    "in": ["Add field validators", "Return 422 on invalid input"],
    "out": ["Do not change the schema", "Do not touch the payment flow"]
  },
  "implementation_notes": "Follow the validator pattern in schemas/user.py.",
  "acceptance_criteria": [
    "Malformed input returns 422 with a clear error",
    "Valid input unchanged in behavior"
  ],
  "tests": {
    "command": "pytest -k intake",
    "expected": "New tests for each invalid case pass; existing tests stay green."
  }
}
```

## Rules of thumb for good packages

- **One concern per package.** If it needs two test suites or two subsystems, split it.
- **Name files, link docs, don't paste bodies.** Keeps the architect's context clean.
- **Acceptance criteria are testable.** "Works" is not a criterion; "returns 422
  on empty id" is.
- **State the out-of-scope list.** It's the cheapest way to prevent a worker from
  ballooning the diff.
- **Match the tier to the token profile**, not prestige: repetitive/well-specified
  → test-writer; novel/logic-heavy → developer.
