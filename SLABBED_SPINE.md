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

## 2026-06-11 (Claude, autonomous)

HEAD `b231debe` == origin, tree clean (only untracked `tmp/`). The HANDOFF "uncommitted WIP"
inventory is STALE — that WIP is all committed+pushed at `b231debe`. Build green; headless
gametests **37/37** (`./gradlew runGameTest`, terrainslabs loaded). Added `a7c20bc7`:
`tsCanopyRowAllLowerNoMiddlePop` — pins the canopy steady-state invariant (3- and 5-wide rows of
objects each on their own TS bottom slab all read getYOffset=−0.5, no middle deviation). This
proves the "middle pops up" symptom is a render-region/chunk-mesh desync, NOT a dy-logic gap.
Decisive next step needs Julia: `/slabdy` on the popped middle — `−0.500` while popped ⇒ render
desync; `0.000` ⇒ logic edge (`hasNonLoweredFullBlockSupportBelow`). Remaining to finish: live
visual accept of placement-parity + MODEL_PATH step-cull, version-line decision, release re-cut —
all human-gated. See HANDOFF.md status banner.

## 2026-06-12 (Claude, autonomous adversarial pass) — TWO real bugs found

HEAD `13e42ae3` (clean, NOT pushed). Same adversarial gametest probes that proved 1.21.1 robust found two real 1.21.11 bugs:
1. **FIXED `3a3f57e7` — ghost-window cull was Terrain-Slabs-ONLY.** `isSlabHeightStepFace` keyed on `isDirectCustomSlabSupportedObject` (only TS BOTTOM_LIKE support), so a VANILLA-slab-lowered cube beside a flat cube returned false → both cull paths (BlockRenderInfoCullMixin + YOffsetEmitter model path) left a see-through seam. Fixed → dy-difference `|getYOffset(self)−getYOffset(neighbour)|>ε` (covers vanilla + compound + cantilever; mirrors 1.21.1; only flips cull→draw; kill switch `-Dslabbed.disableStepCull`). Proven by gametest `advVanillaSlabStepMustUnCull`. 40/40 green. ⚠️ RENDER change — live A/B visual confirm still pending (the same mechanism was live-confirmed working under Sodium on 1.21.1, so strong indirect backing).
2. **FOUND + DEFERRED `13e42ae3` — vanilla VERTICAL compound stack FLOATS.** `slab/stone/slab/stone` → top stone reads −0.5 not −1.0 (floats 0.5 above the L2 slab). 1.21.1 gets this right. Root: compound −1.0 only via `isAdjacentSideSlabLowered` (side-adjacency, ~775); a vertically-lowered vanilla slab doesn't qualify. Core dy change + needs visual confirm → not landed unsupervised. Characterized by `advVanillaCompoundStackFloatsKnownBug` (asserts current −0.5; FLIP to −1.0 when fixed). Reference fix = the 1.21.1 `getYOffsetInner` vertical-compound branch. **This is the top release-blocker to fix next.** See HANDOFF.md bug banner.
