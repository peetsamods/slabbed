# Slabbed Y-System — should we "overhaul to half-integers"? (design note)

_Written overnight 2026-06-08 for Julia. Headless-validated where claimed; no live runs._

## TL;DR

- **Do NOT replace the grid with a true half-integer block system.** It isn't buildable on
  Minecraft's architecture and would throw away the vanilla/mod compatibility that is the whole
  point of Slabbed.
- **The system already speaks half-integers** — `getYOffset` returns `-0.5` right there in the
  `/slabdy` overlay. The recurring bugs (DODO, fence split, hanger clip, the TOP_LIKE float) are
  not because Slabbed "doesn't understand halves." They're because the half is a **render
  illusion painted on an integer grid**, and every integer-assuming subsystem has to be taught to
  compensate — separately, by hand, in its own mixin.
- **The overhaul worth doing is consolidation, not grid-replacement:** (1) one authoritative
  offset resolver every consumer derives from, and (2) a declarative per-block "surface/sit
  contract" so new surfaces and mods are *data, not new predicate code*. That is also the thing
  that most improves **modding** Slabbed.

---

## 1. Why a true half-integer grid is not on the table

Minecraft's world is, at its foundation, a 3-D array of block states at **integer** coordinates.
The following all assume integer Y and cannot be made fractional without replacing them wholesale:

- chunk palette storage & world save
- the lighting engine
- collision / `VoxelShape` math
- pathfinding & mob AI
- block-update / neighbor propagation
- networking (block change packets are `BlockPos`)

To put a block at `Y=113.5` you would need a parallel store (display entities or a custom
voxel layer) and re-implement all of the above against it. The instant you do, you stop being a
block — you lose redstone, pistons, mob interaction, and **compatibility with Terrain Slabs and
every other mod**, which is the reason Slabbed exists. Cost: enormous. Payoff: negative.

So the offset stays a **presentation** concern over an integer grid. The question is only *how
cleanly* we manage that presentation.

---

## 2. The actual root cause of the recurring bugs

The offset (`dy ∈ {-1.0, -0.5, 0.0, +0.5}`) is produced by `SlabSupport.getYOffset` and consumed
by a **fan-out of subsystems that each re-derive or re-interpret it**:

| Consumer | File | How it uses the offset |
|---|---|---|
| Chunk/entity render | `OffsetBlockStateModel`, `BlockModelDyTranslateMixin` | translates quads by `getYOffset` |
| Crosshair raycast | `SlabbedOffsetRaycast` + `GameRendererPickOffsetRaycastMixin` | shifts hit by `getYOffset` |
| **Placement target** | `BlockItemPlacementIntentMixin` | **re-implements its own height logic** |
| Server hit validation | `ServerInteractBlockHitToleranceMixin` | shifts validation center by hard-coded dy lanes |
| Fence/wall/pane joins | `FencePaneSlabConnectionMixin`, `WallSlabConnectionMixin` | compares `connectingBlockVisualDy` |
| Hangers | `SlabSupport.loweredFullBlockUndersideSupportDy` | bespoke underside walk |

Every one of these is a place the **visual** and the **logic** can disagree. The bug pattern is
always the same: *a consumer didn't get the memo about the offset, or got a stale/partial copy of
it.*

- **Fence "split":** the connection-state consumer hadn't been taught about the step. (Fixed.)
- **DODO / "placing too high":** `BlockItemPlacementIntentMixin` re-implements height logic and
  **bailed on the `UP` face** (`"face_not_horizontal"`), ignoring the `-0.5` that `getYOffset`
  already knew. The raycast was correct; the placement consumer diverged.
- **TOP_LIKE float (the Packed Mud Slab):** see §3 — a surface kind nobody wrote a lowering rule
  for, in a consumer that special-cases per kind.

These are not different bugs. They are the **same structural bug** (offset known centrally,
applied inconsistently) wearing different hats.

---

## 3. The TOP_LIKE float, decoded (the case from the screenshots)

`/slabdy` readings:
- Floating block: `Packed Mud Slab · TERRAIN(TOP_LIKE) · dy=0.000 flush`
- Neighbour column: `Stripped Oak Log · VANILLA · dy=-0.500 LOWERED`

Confirmed by harness (`tsTopSlabOnLoweredSupportStaysFlush_KNOWN_GAP` + control):

- A **Terrain Slabs block is a support surface but is itself never lowered.**
  `TerrainSlabsCompat.shouldSkipOffset` returns `true` for *every* `terrain_slabs` block, so
  `getYOffset` is forced to `0` for it.
- A vanilla block on the *same* lowered support lowers `-0.5`. So the gap is **specifically the
  TS skip-offset exclusion**, not the support failing.

This is the **skip-offset asymmetry** already flagged "DEFERRED for beta 5" in memory. It is a
genuine **design decision**, not a code bug:

> Should a Terrain Slabs block, when it sits on a lowering support, inherit the lowering like a
> vanilla block does — or does Terrain Slabs own its block's vertical placement and Slabbed must
> keep its hands off (to avoid double-offset / fighting TS's own rendering)?

I did **not** change `shouldSkipOffset` overnight, because:
1. it's a deliberate exclusion with broad blast radius (it also governs how TS slabs combine as
   supports), and
2. the "correct" answer is a product decision about who owns TS block geometry — yours, not mine,
   and it needs a live look (does removing the skip double-offset TS slabs in the common case?).

It is now **pinned by a green tripwire test** so that whenever we flip it, the change is explicit
and any regression is visible.

---

## 4. Proposed architecture (the consolidation, not a grid change)

### 4a. One authoritative offset resolver

Make `getYOffset` (or a thin `SlabOffset.resolve(world,pos,state) -> OffsetResult`) the **single
source of truth**, and delete every consumer's private height logic. Concretely the highest-value
target is `BlockItemPlacementIntentMixin`: instead of its own `isCompoundTopHit` /
`targetSupportsTopMerge` / `face_not_horizontal` cascade, it should ask the resolver "what is the
visual top face of the block I hit, and which cell does a slab placed there belong in?" and remap
the hit from that — the same number the render and raycast already use. The DODO is a direct
consequence of this not being centralized.

A richer resolver could return not just `dy` but the **visual AABB** (`[yLo, yHi]`), so placement,
selection, and server validation all consume one geometry instead of three hand-fitted ones.

### 4b. Declarative per-block surface/sit contract

Today, teaching Slabbed about a surface means writing branches in `SlabSupport`
(`customSlabSurfaceKind`, `slabColumnYOffset`, `getDirectObjectSupportTopOffset`, …). TOP_LIKE
floated because it's a kind with no lowering rule written for the *object-on-lowered-support*
case.

Replace the scattered predicates with a small declared contract per block (or block tag),
resolved once:

```
SurfaceContract {
  SupportKind support;     // NONE | BOTTOM_LIKE | TOP_LIKE | DOUBLE_LIKE | FULL
  double      sitOffset;   // how much an object resting on me is lowered (e.g. -0.5 for bottom-like)
  boolean     inheritsLowering; // do I, as an object, follow a lowered support? (the §3 decision)
}
```

- Vanilla bottom slab → `{BOTTOM_LIKE, -0.5, inherits=true}`
- Vanilla top/double slab → `{TOP_LIKE/DOUBLE_LIKE, 0.0, inherits=true}`
- Terrain Slabs slab → today `{kind, …, inherits=FALSE}` (the asymmetry); flip `inherits` to fix §3.
- A future mod's slab → **register a contract**; no new code in `SlabSupport`.

The engine (resolver) reads the contract; consumers read the resolver. Adding a slab-like block
from another mod becomes **registration, not a patch** — which is the concrete answer to *"would
this help modding Slabbed?"* **Yes — far more than half-integers would**, because the recurring
cost isn't the arithmetic, it's the combinatorial *surface-kind × consumer* matrix you keep
hand-coding.

---

## 5. Recommendation & sequencing

1. **Ship a cut first.** You're ~58–60% to shippable with a lot of hard-won edge-case fixes
   embedded in the scattered mixins. Don't big-bang-refactor on top of that.
2. **Resolver-first, incrementally.** Introduce the single resolver and migrate the *worst*
   diverging consumer (`BlockItemPlacementIntentMixin`) to it. That alone kills the DODO/"too
   high" class and is testable headlessly.
3. **Then the contract.** Convert `customSlabSurfaceKind` + the offset predicates into the
   declared `SurfaceContract`, keeping the current values 1:1 so it's a pure refactor with green
   tests, then expose registration for mods.
4. **Decide the skip-offset asymmetry (§3) deliberately**, with a live look, and flip the
   `inherits` flag + the KNOWN_GAP tripwire together.

None of this requires the grid to change. It makes the half-integer presentation a *closed,
declarative system* instead of whack-a-mole.

---

## 6. What I did overnight to support this

- Confirmed the game client is stopped.
- Closed the TOP_LIKE testing blind spot: added characterization tripwires
  (`shimTopAndDoubleClassify`, `fullBlockOnTsTopLike/DoubleLikeNotLowered`,
  `tsTopSlabOnLoweredSupportStaysFlush_KNOWN_GAP`, `vanillaBlockOnSameLoweredSupportDoesLower`).
  **55/55 harness tests pass.** Commit `9ed019dc`.
- Root-caused the screenshot DODO to the **skip-offset asymmetry** (`shouldSkipOffset` excludes
  all `terrain_slabs` blocks from lowering), not a missing arithmetic case.
- Wrote this note.

**Open for your morning call:** (a) flip the skip-offset asymmetry for TS blocks (fix the
TOP_LIKE float) — needs a live double-offset check; (b) start the resolver-first consolidation;
(c) keep shipping edge-case fixes and defer the refactor. My vote: (a) as a scoped fix once you
confirm it doesn't double-offset live, then (c) to a shippable cut, then (b).
