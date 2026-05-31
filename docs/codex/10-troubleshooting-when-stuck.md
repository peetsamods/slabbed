# 10 — Troubleshooting When Stuck

Use when a bug persists, two fixes fail, or automation and live play disagree.

## First principle

Stop patching. Convert uncertainty into evidence.

## After two failed fixes

The next slice is audit-only. No Java edits.

Audit checklist:

- correct root
- correct branch
- correct HEAD/tag
- fix actually exists in current branch
- intended mixin JSON registered
- `fabric.mod.json` source-set wiring correct
- produced jar contains expected resources/classes
- dev/debug/test/proof classes excluded from release jar
- production bytecode does not hard-link excluded classes
- proof fixture matches live source truth
- live failure layer named correctly

## If automation green but live red

Likely causes:

- proof measured existence but not contact gap
- proof manually promoted persistent state not present live
- proof used World lookup while live render used non-World render view
- proof clicked center while live failure is seam/edge/held-item-specific
- proof verified placement but not survival/update/reload
- proof verified owner but not server validation

## If targeting flickers at seams

Do not add another inline rescue guard first. Audit owner priority:

```text
anchored lowered full block
visible lowered slab owner
no rescue / keep initial
slab-held placement intent
```

Create a small owner-classification table before patching.

## If faces disappear

Do not assume culling is the only layer. Classify:

- model dy mismatch
- cull-context state leakage
- custom compat source treated as generic support
- render-view lookup mismatch
- YOffsetEmitter delegate state leakage

## If block jumps after support break

Classify as source-truth / persistence / survival before patching. Placement success is irrelevant.

Required proof:

```text
pre-break dy=
post-break dy=
persistent anchor/carrier=
source state before=
source state after=
neighbor update observed=
reload/relog status=
```
