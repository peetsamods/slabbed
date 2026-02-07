---
name: slabbed-compat-safety-contract
description: Define Slabbed’s compatibility guardrails to avoid breaking vanilla behavior or modded blocks while adding slab-top support.
---

# Slabbed — Compatibility Safety Contract

## Goal
Keep Slabbed safe and predictable in modpacks:
- Do not break vanilla placement/survival rules outside slab-top support
- Do not assume all partial blocks behave like slabs
- Avoid global hooks that change unrelated behavior

## Non-goals
- Full “compat with every mod” promise
- Special handling for stairs/fences/walls/trapdoors unless explicitly planned

## Scope of support
Slabbed extends “top support” only for:
- Vanilla `SlabBlock` bottom/top/double variants
- Explicitly whitelisted slab-like blocks (only if added later with clear criteria)

Slabbed does NOT automatically treat as support:
- stairs
- fences
- walls
- trapdoors
- panes
- carpet-like custom shapes
unless explicitly added later.

## Hook strategy rules
1) Prefer the narrowest hook that fixes the category.
2) Prefer per-block/category hooks over global redirects if the global change impacts unrelated blocks.
3) If using a shared helper predicate, it must be limited to slab semantics only.
4) All slab semantics must route through `SlabSupport` (single source of truth).

## Modded block safety
- Never assume a modded slab extends vanilla `SlabBlock`.
- If handling modded slabs later, gate by:
  - interface/marker (preferred), or
  - explicit registry whitelist, or
  - shape predicate proven equivalent to slab top face
- Do not attempt heuristic “any half height block counts” logic.

## Vanilla behavior preservation (mandatory checks)
After any new hook:
- Baseline full-block lane must behave exactly like vanilla
- Existing categories must not regress
- No new placement allowed on air/liquids that vanilla forbids
- No changes to collision behavior for slabs themselves

## Failure protocol
If a hook causes unintended changes:
1) Revert to last known good tag
2) Reduce scope (targeted mixin)
3) Add regression test notes to the test matrix
4) Re-apply in smaller pieces

## Output report format
When invoking this contract during review, report:
- Hook type: shared vs targeted
- Potential blast radius (1–2 lines)
- Vanilla behavior checks performed
- Modded block assumptions made (must be “none” unless explicitly justified)

## Stop conditions
- If you cannot explain why a hook is safe in 2 sentences, it’s too broad
- If a shared hook alters behavior outside slab contexts, revert and target
