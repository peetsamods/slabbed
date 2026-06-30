# 04 — Codex Slice Contracts

Use these contracts to keep work bounded. One slice, one layer, one proof goal.

## Before any slice: symptom-family check

Before choosing a slice type below, name the symptom family the work belongs
to (e.g. "crosshair targeting disagrees with visible geometry," "placed
block changes height after unrelated edit") and check whether 2+ prior
slices already targeted that family without closing it — proven-green
slices count too, not just failed ones. If so, the contract for THIS slice
is the zoomed-out audit in `docs/codex/14-zoom-out-discipline.md`, not the
implementation patch slice below, regardless of how small or well-evidenced
the next individual fix looks. Skipping this check is how a single disabled
subsystem turns into a dozen separately-savepointed narrow patches that
never close the family — see `docs/codex/13-mixin-layer-wiring-audit.md`
for the concrete incident this rule exists to prevent.

## Audit-only slice

Use when root cause or failing layer is unclear.

Forbidden:

- Java edits
- gametest edits unless explicitly requested
- commits/tags
- release work

Required:

- preflight
- minimal grep first
- evidence folder under `tmp/<issue>-audit-<HEAD-short>`
- exact suspected layer
- exact files likely involved
- next patch slice only if proof supports it

Final report:

```text
Verdict:
Root:
Branch:
HEAD:
Layer suspected:
Files inspected:
Evidence written:
What is proven:
What remains unproven:
Next smallest slice:
```

## Implementation patch slice

Use only after failing layer and legal state are known.

Required:

- preflight
- visible symptom
- failing layer
- protected invariant
- RED proof or stated proof gap
- smallest possible file set
- compile/proof
- live test if visual/targeting/feel bug

Forbidden:

- touching unrelated files
- broad rescue expansion
- broad solidity/support lies
- category expansion
- release prep

## Savepoint slice

Use after proof/live win.

Required:

- preflight
- verify intended dirty files only
- run required proof if not fresh
- stage intended files only
- commit
- annotated tag
- push branch
- push tag
- verify final tree

Never call Bug Blaster fixed before savepoint closure.

## Failed live test slice

Use when automation is green but Julia live testing is red.

Forbidden:

- patching first
- rescue broadening
- speculative guard edits

Required:

- preserve logs and live details
- extract relevant markers
- identify why automation missed it
- write RED proof matching live failure
- only then patch

## Release gate slice

Use before release or release-tag movement.

Required:

- clean build
- client/server gametest as applicable
- jar contents scan
- bytecode hard-reference scan
- private launch/smoke if applicable
- release tag movement only after proof

Forbidden:

- behavior edits unless release gate identifies a concrete blocker
- debug/proof class leakage
- changing release tags without final gate proof
