# Slabbed — Verification Protocol (mandatory)

**Why this exists.** The recurring failure on this project is not wrong code — it is
**incomplete verification stated with overconfidence**. The concrete pattern, observed
repeatedly: a fix is applied to ONE call site / ONE input category, a test exercises that ONE
case, and it is declared "done / verified / conforms" — while sibling call sites and sibling
inputs stay broken behind a green test. (Worked example: the TS-ceiling smoosh guard was applied
to 1 of 3 walk sites; lanterns/chains/dripstone stayed broken while the hanging-roots test passed.)

This protocol turns the missing steps into **hard gates**. No change is "done" until every
applicable gate is answered **in writing, with evidence**. If a gate cannot be answered, the work
is not done — it is *in progress with a named gap*.

---

## The gates (answer in writing before any "done" claim)

### G1 — Domain enumeration
State the **complete input domain** of the change. Not "it works for X" — the full set X belongs to:
- every **input category** the change must handle (all block families, all `SlabType`s, vanilla vs
  TS, floor vs wall, every neighbor-change kind, every relevant property value);
- with **counts**. "Fences" is not a domain; "fence/wall/pane/gate = 4 families × {bottom,top,TS}
  slab × {model,outline,raycast} = 36 cells" is.

**Coverage = covered / total, with the uncovered items named.** Anything below 100% is a gap that
must be stated, not omitted.

### G2 — Call-site sweep (for any change to a shared function/predicate/helper)
`grep` **every** caller of each function you touched. For each caller, classify and justify:
- **must-change** (fixed here), **must-NOT-change** (and why the change does not affect it), or
- **verified-unaffected** (and how).

A guard added inside a helper, or at one caller, is not trusted until every other caller is
accounted for. *This is the exact step whose absence caused the smoosh half-fix.*

### G3 — Mutation / RED gate
A green test proves nothing until it has been seen **RED for the right reason**.
- **Fixes:** RED-first — temporarily revert the fix, run, confirm the test fails with the expected
  wrong value, restore. Cover the **domain** (G1), not one representative.
- **Characterization tests** (pinning existing behavior): apply a mutation to the code under test
  and confirm the test fails. A test that never goes red under any relevant mutation is **vacuous**
  and must be labelled so or strengthened.

### G4 — Adversarial pass (default, not on request)
Before "done" on any substantive change, actively **seek failure**:
- small change → a written self-attack: "how is this incomplete? which sibling did I skip? would
  this test pass over broken code?"
- large change → fan out adversarial reviewers (see the saved `rigor-audit` / `adversarial-review`
  workflow scripts). Assume the fix is partial and the tests vacuous until proven otherwise.

The user must never have to ask for rigor. It is the default final step.

### G5 — Claim calibration (no over-claiming, ever)
Every claim carries an **evidence tag**. Bare superlatives are **banned** unless G1 shows 100%:
> ~~"done", "verified", "complete", "conforms", "all X", "proven", "invariant", "fixed"~~

Use instead, precisely:
- **`EXHAUSTIVE`** — full domain (G1) enumerated, covered, and mutation-proven (G3).
- **`PARTIAL[scope]`** — a named subset is covered; the rest is explicitly listed as uncovered.
- **`HEADLESS-ONLY`** — gametest-proven; render / particle / live NOT proven.
- **`UNVERIFIED`** — reasoned from code, not executed.

Default to **understatement**. State what is **NOT** covered every time. "The suite is green" means
"the pinned rows and their tests pass", never "the behavior is correct".

### G6 — Completeness critic (final, written)
End every substantive change with: **"What did I NOT check? What would a skeptic attack next?"**
The answer is logged as a known gap (in the PR/commit/HANDOFF), never silently dropped. If the list
is empty, justify why the domain (G1) is closed.

---

## Enforcement

- These gates are `RULES.md` §21. A change that skips them is not review-ready.
- The two saved workflow scripts (`adversarial-review-*`, `rigor-audit-*` under the session
  `workflows/scripts/`) are the reusable G4 harness — run one before declaring substantive work done.
- Cross-reference: [`DY_SPEC.md`](DY_SPEC.md) (what dy must be), [`PORT_FIX_MATRIX.md`](PORT_FIX_MATRIX.md)
  (which branch has which fix), [`RELEASE_REGRESSION_TRIGGERS.md`](RELEASE_REGRESSION_TRIGGERS.md)
  (the recurring bug classes). Those say *what* to verify; this says *how thoroughly* and *how honestly*.

## The one-line test
Before you write "done", ask: **could a hostile reviewer name one input, one call site, or one
triad member I did not check?** If yes, you are not done — you have a `PARTIAL[…]`. Say so.
