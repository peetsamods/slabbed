# 12 — Goal Mode

Use `/goal` only when the objective is known and Codex should pursue it through multiple bounded internal loops.

## Goal-mode rules

- Start with preflight.
- Reconcile current repo state before assumptions.
- Identify failing layer before patching.
- Add/identify RED proof before behavior patch.
- After two failed patches, stop patching and switch to audit-only.
- Commit/tag/push only after proof gates pass.
- Do not claim release readiness without release gates.

## Minimal goal skeleton

```text
/goal
Repo Root: <path>
Branch: <branch>
Expected HEAD/tag: <hash/tag or unknown; reconcile first>

Objective:
<player-visible objective>

Protected invariant:
<Slabbed law>

Failing layer:
state authority / placement / collision / survival / model / outline / raycast / rescue / proof gap / release hygiene

Allowed files:
<list>

Forbidden files:
<list>

Required preflight:
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD

Required proof gates:
1. produce or identify RED proof
2. patch smallest layer only
3. compile
4. focused proof
5. full relevant proof
6. live test if visual/targeting/feel
7. savepoint only after proof/live green

Stop if:
- wrong root/branch/HEAD
- dirty staging
- unrelated dirty files
- build failure unclear
- proof ambiguity
- automation green but live red
- scope expands
- two failed patches

Final report:
root, branch, old/new HEAD, tag, files touched, files untouched, proof produced, what remains unproven, final tree, next smallest slice.
```
