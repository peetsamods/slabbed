# Ensemble Coherence — Design (2026-07-07, Maintainer-approved lane)

## The problem (video-proven, 2026-07-07 recording, frames synced to recorder rows)

Every dy in Slabbed is computed and defended **per block** (anchor, freeze-flat, carrier, geometric
lanes). Neighbors never negotiate. Three visible sub-classes when neighbors disagree:

1. **Interpenetration** (frame t=130s): flush FROZEN-FLAT hopper under an ANCHORED −0.5 chest — the
   chest's visual box sinks half a block into the hopper bowl. Pairwise overlap = `dyUpper − dyLower`
   when > 0 (block above cell y+1 renders `[y+1+dyU, y+2+dyU]`, block below renders top at `y+1+dyL`).
2. **Gaps** (the CHANGELOG-deferred "mixed-offset stacks" known issue): `dyLower − dyUpper > 0` leaves
   an open seam mid-stack (−1.0 under −0.5 → half-block air band).
3. **Occupancy invisibility** (frame t=98s): a slab at cell C renders level with the LOWERED top of the
   block below — cell C *looks* like free space above a surface. Maintainer clicked a trapdoor into it 5×;
   vanilla refused 5× (cell occupied). WYSIWYG corollary: the logical occupancy of a cell is not
   visually apparent around mixed-dy geometry.

**Why the proof system was silent:** recorder + sentinel verify each block against *its own* truth
(placement dy, baked-vs-logical, never-pop). Nothing verifies blocks against *each other*. Each block
in Maintainer's tower was individually lawful — 30 breaks, zero pops, sentinel green with liveness proof —
while the ensemble read as "everything falling apart."

## Laws in tension (both Maintainer's, both standing)

- **NEVER-POP:** a placed block keeps its height, no matter what happens to neighbors. (Wins — ruled.)
- **WYSIWYG:** what you see is what is there; placements land where aimed.

In a stack these conflict: if a support's dy differs from a rider's, either the rider moves (violates
NEVER-POP) or the ensemble diverges (violates WYSIWYG-as-perception). Since existing blocks must not
move, coherence must come from (a) **new placements joining stacks coherently**, (b) **measurement**
that names every remaining clash, and eventually (c) render-side tiling (long-term).

## The lane (this order, proof-first)

### Phase 1 — Measurement gate: `SlabEnsembleCoherence` + `ENSEMBLE_CLASH` recorder rows
Pure, headless-testable classifier over a vertical pair (states + dys + support shapes):
`classify(lowerState, dyLower, upperState, dyUpper)` → `COHERENT | INTERPENETRATION(depth) |
GAP(depth) | OCCLUDED_OCCUPANCY`. Only face-touching solid tops/bottoms count (use shape max/min Y at
the shared face, not naive full-cube math — slabs/hoppers have partial shapes; a bottom slab's visual
top is `y+0.5+dy`). Wire into the EXISTING placement-arm sweep (`SlabModelStaleSentinel.armPlacement`
already walks the radius-2 neighborhood pre- and the sampler post-placement): after each placement
settles (first sample pass), classify each vertical pair in the armed neighborhood; non-coherent →
`ENSEMBLE_CLASH` row (pair positions, states, dys, kind, depth) + summary counter + rate-limited chat
ping. Recorder-gated like everything else. **This converts the row-silent class into named rows** — the
gate the audit's whole method demands: measure before designing the fix.

### Phase 2 — Placement-time coherence (logic-side, headless-provable)
When a NEW block is placed such that it forms a vertical face-contact pair with an existing block, the
dy-assignment path (`freezeLoweredOnPlace` / the anchor lanes in `SlabAnchorAttachment`) must prefer a
dy that makes the new pair COHERENT when one is available within the lawful candidate set (the dys the
existing lanes would already permit: {0, support-follow, carrier-inherit}). Never moves an existing
block; never invents a dy outside existing lanes; ties break toward WYSIWYG (meet the clicked face).
The chest-under/over-hopper and slab-into-log-cell cases from the video become coherent at placement.
Churn (breaking mid-stack) can still create clashes — those remain measured by Phase 1 rows.
RED-first: gametests build the video's exact scenes via real `useOn` (hopper flush → place chest above
lowered context → assert pair coherent; slab-level-with-lowered-log → assert placement refused cells
are visually distinguishable is Phase 3's problem — Phase 2 only asserts dy assignment).

### Phase 2a RESULT (2026-07-07, empirical): already coherent — pinned, re-scoped
The RED-first suite for placement-onto-flush-support interpenetration came back GREEN unfixed: the
existing freeze-flat rails already assign the coherent dy in every reconstructed shape (2 permanent
pin tests: EnsemblePlacementCoherenceTest). Therefore the remaining placement-sourced clash inventory
is: (a) OCCLUDED_OCCUPANCY — the block is WYSIWYG-correct but invisible in its own cell; fixable only
by **Phase 2b: occluded-surface placement remap** (clicking the visible flush top of an occluded slab
places the new block in the cell ABOVE the logical target with the deep-follow dy, filling the
apparent space — converts the 5x-refused-clicks case into a WYSIWYG placement); and (b) new-LOWER
build-order inversions (placing flush under an existing lowered block), which have NO lawful coherent
candidate (the new block would have to sink below its aimed cell) — Phase 3 by law. Phase 2b touches
the placement-remap mixin (S11 danger territory) and gets its own carefully-scoped lane.
**Phase 2b LANDED (2026-07-07, 274/274):** slabbed$remapClickOnOccludedSurface — up-face click, vanilla-
would-fail only, occluded occupant (TS-guarded), air above; remapped placement deep-follows via the
existing compound lanes. RED-first + surgical-scope pin + occluded-condition mutation RED.

### Phase 3 — GREENLIT 2026-07-07; decomposition by measured harm
3a **GAP-FILL BANDS (LANDED, 275/275)**: additive side-texture band spanning [top, top+d] of the lower
member of a GAP pair, emitted through the dy-shifted emitter (chain-bridge philosophy generalized).
Plan = classifier GAP depth, pure + pinned + mutation-proven; emission client-side, NEEDS-LIVE (TEST
(9) A/B). 3b interpenetration trim (vertex clipping — hard, deferred). 3c BER pipeline (chests — the
band covers static models only today). 3d occupancy cue (largely obsoleted by Phase 2b remap).

### Phase 3 original scoping (retained) — Render tiling + occupancy visibility
Model stretching/trimming so residual mixed-dy pairs tile seamlessly (the chain-bridge approach
generalized), and a placement-preview cue when the expectedPlace cell is occupied-but-displaced.
Chest is a BlockEntityRenderer (separate dy path from `OffsetBlockStateModel`) — any render-side work
must cover both pipelines. Fed by Phase 1 row statistics (which pairs actually occur live).

## Invariants (test-pinned)
- No existing block's dy changes as a result of any Phase 2 rule (NEVER-POP absolute).
- Phase 2 never assigns a dy outside the lawful lane set for that block/context.
- Phase 1 classifier: zero rows on an all-flush scene; zero on a uniformly-lowered coherent stack;
  exact kind+depth on the three video scenes (reconstructed as gametest fixtures).
- Perf: classifier runs only inside recorder sessions, only on armed neighborhoods (the sentinel's
  existing gate order; zero cost recorder-off).
- TS guard: classification/assignment must not touch TS-owned blocks (`shouldSkipOffset` family —
  failure-mode-4 sweep required before merge).
