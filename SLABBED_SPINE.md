# SLABBED_SPINE

This file is the current repo-local truth for Codex. Keep it short. Update it after every proof-confirmed or live-confirmed savepoint.

## Current active root

```text
/Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest
```

Stop if the actual root does not match the intended current root.

## Current branch

```text
compat/mc1211-terrain-slabs-named-surface-support
```

## Current known-good savepoint

Commit:

```text
908a3ea3
```

Tag:

```text
(untagged — local commits on compat/mc1211-terrain-slabs-named-surface-support, ahead 18)
```

Pushed branch: yes (origin/compat/mc1211-terrain-slabs-named-surface-support, 18 commits ahead)
Pushed tag: n/a

Live-confirmed at 908a3ea3: TS compat compound stacking, BUG 1 fix (vanilla TOP slab
capping TS slab → -1.0), decorative hanger follow-down, cull window fix. See HANDOFF.md
for WIP state and what is not yet committed.

## Current objective

Finish live inspection of the upgraded 1.21.11 placement parity fixes, then commit the
accumulated WIP as clean slices. Veg fix determined NOT applicable (TS handles
vegetation natively on 1.21.11). TS side-lane parity and VB+VS/VS+VB isolation are
headless-green; the render-region world-load crash guard is live-green. The suspected
merge regression was rechecked live by Julia and is green again. See HANDOFF.md.

## Current blocker

Placement parity/crash-guard WIP is green but uncommitted. Do not commit before final
diff review and Julia's explicit savepoint instruction.

Deferred lane: inappropriate shadows on shifted opaque full-cube quads. The
all-face `cullFace`/AO metadata experiment was automation-green but not
visual-accepted, so it is parked outside the commit candidate. Do not continue
shadow work unless Julia reopens it; next gate is one fresh branch-local visual
repro/triage, not a Sodium/Indigo renderer mixin.

## Do not touch boundaries

- Do not touch culling unless fresh RED says culling.
- Do not continue deferred shadow/lighting work unless Julia explicitly reopens it.
- Do not promote Terrain Slabs into generic Slabbed support.
- Do not broaden rescue without RED proof.
- Do not move release tags unless explicitly running release correction.
- Do not use dirty/archive roots unless recovery is explicitly requested.
- Do not edit multiple layers in one slice.
