# 03 — Visual Triad Law

For any slab-lowered or slab-shifted object, three surfaces must agree:

1. Model — rendered mesh.
2. Outline — selection wireframe.
3. Raycast — targeting / interaction result.

If only one or two are updated, the feature is broken even if placement appears to work.

## Authority

`ClientDy.dyFor(world, pos, state)` is the authoritative client dy source where client dy logic is needed. Slab semantics route through `SlabSupport` or an explicitly designated Slabbed authority.

Rules:

- all three surfaces must derive from the same dy decision
- no duplicate dy logic
- no shared mutable “current dy”
- use scoped state only if necessary and clear it safely
- stop if model, outline, and raycast disagree

## Required triad proof shape

For a dy/lowered bug, proof should include:

```text
pos=
state=
support source=
worldDy=
modelDy=
outlineDy=
targetDy=
vanilla target=
final target=
held item=
face=
hit vector=
live expected owner=
actual owner=
```

## Common triad failures

| Symptom | Likely layer |
|---|---|
| model correct, outline floats | outline dy not using shared authority |
| outline correct, click hits wrong block | raycast/retarget ownership gap |
| block looks lowered until reload | persistence/client mirror gap |
| model collapses only during rendering | non-World render-view bridge gap |
| face disappears next to custom slabs | culling/compat/source-truth leak |
| selection jumps while holding slab | rescue or placement-intent priority bug |

## Live-feel rule

For ghosting, weird hitboxes, targeting theft, moving-up behavior, and “it still feels wrong,” Julia live testing outranks green automation. If automation is green but live is red, stop patching and write a RED proof that fails for the live reason.
