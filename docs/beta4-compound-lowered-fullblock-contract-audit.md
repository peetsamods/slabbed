# Beta4 Compound Lowered Full Block Contract Audit

Audit-only. No gameplay edits in this slice. Release remains blocked.

Decision cross-reference: `docs/beta4-compound-source-mode-design.md` adopts
A-prime, explicit authored lane/depth source mode for compound ordinary full
blocks. Matrix rows below now have intended beta4 outcomes; implementation is
still blocked behind proof and source-mode work.

The earlier slab reject rows were technically intentional and safe, but Julia's
live review shows that safety alone is not enough for product feel. The new
slab merge/remap grammar lives in
`docs/beta4-compound-slab-merge-grammar.md`. This audit remains a design/proof
record; it does not mark implementation complete.

Update at `fae6d25`: Julia's screenshot-shape manual live test found an
uncovered topology after the internal compound slab Row 3 proof went GREEN.
The internal proof row covers a narrow artificial same-Y remap path; it does
not cover Julia's in-world sign-labeled upper full-block shape with a lowered
slab directly below. Manual live acceptance is not claimed.

Product-law correction at `d96ba01`: the discriminator audit proved Julia's
screenshot side-shape and Rows 1/2 are equivalent under current facts
(`sourceDy=-1.0`, `belowDy=-0.5`, immediate side candidate air,
`legalLaneCount=0`, `sourceBelowOnlyLoweredLane=true`). Rows 1/2 are no longer
safe-reject/pass cases. They are promoted into the named legal class
**compound below-lane side slab placement**: expected legal side placement at
`dy=-0.5` through existing lowered slab grammar. Implemented/proven at
`save/beta4-compound-below-lane-side-slab-fix`: Rows 1/2 author the immediate
side candidate at `dy=-0.5`, and the screenshot side-shape side proof emits
GREEN. This does not legalize any `dy=-1.0` slab lane.

Live band-split correction after `6d0d525`: Julia's no-jump live retest showed
the previous screenshot side-shape proof was too coarse. Upper-half side click
is GREEN, lower-half side click is RED because it flickers/fails or does not
author the expected legal `dy=-0.5` slab, and top-face click still creates the
ghost/skip slab path. The proof markers for this split are
`[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_UPPER_GREEN]`,
`[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_LOWER_RED]`,
`[JULIA_BETA4_LIVE_SCREENSHOT_TOP_FACE_GHOST_RED]`,
`[JULIA_BETA4_LIVE_SCREENSHOT_BAND_SPLIT_HARNESS_GREEN]`, and
`[JULIA_BETA4_LIVE_SCREENSHOT_BAND_SPLIT_HARNESS_FAIL]`.

Canonical lower-side fix result: lower-half side A/B now place legal lowered
bottom slabs at `dy=-0.5` from the canonical live-shape structure. The failure
layer was server packet hit validation after client placement remap, not
crosshair targeting and not slab remap classification. The narrow bridge accepts
held-slab packets only when the existing legal compound slab remap predicate is
true; it does not legalize any beta4 `dy=-1.0` slab lane or `dy<-1.0` lane.

Goblin fixture correction at `c956fa3`: Julia's manual live observation
supersedes the prior automated top-face GREEN. The old harness summary was not
release-valid because the fixture/build shape did not prove the required
composition: two bottom slabs, full-block bridge, two top slabs, and an upper
full block on one top slab. The corrected support-missing side proof is now
GREEN when the upper full block remains visible, persistent, compound, and
`dy=-1.0`; it authors the side candidate as `stone_slab[type=top]` at
`dy=-0.5`. Corrected summary: `structure=GREEN fixtureTruth=GREEN
supportPresent.upperSide=GREEN supportPresent.lowerSide=GREEN
supportPresent.topFace=RED supportMissing.side=GREEN supportMissing.topFace=RED
hitbox=RED ghost=false jump=false wrongOwner=true
releaseBlockers=topFace,supportMissingTopFace`. Release remains blocked.

Compound visible-owner top slab fix: `COMPOUND_VISIBLE_OWNER_TOP_SLAB` is now the
named legal result for held-slab UP clicks on the persistent visible compound
owner. It authors the exact candidate `source.up()` as
`stone_slab[type=bottom]`, final `dy=0.0`, and
`persistentLoweredSlabCarrier=false`. The remaining RED was not placement
routing; the packet reached the intended candidate, but
`SlabSupport.getYOffsetInner(...)`'s bottom-slab dynamic branch treated a bottom
slab directly above an anchored compound full block as a lowered continuation via
`hasLoweredCarrierBelow(world, pos)` and returned `dy=-0.5`. The fix uses the
existing durable compound full-block source sidecar to exclude only this named
top-face result from dynamic lowered classification and persistent lowered
carrier fallback. Latest corrected goblin summary: `structure=GREEN
fixtureTruth=GREEN supportPresent.upperSide=GREEN supportPresent.lowerSide=GREEN
supportPresent.topFace=GREEN supportMissing.side=GREEN
supportMissing.topFace=GREEN hitbox=RED ghost=false jump=false wrongOwner=true
releaseBlockers=none`. Side placement remains GREEN, fixtureTruth remains
GREEN, and no beta4 `dy=-1.0` slab lane or `dy<-1.0` lane is legalized. Release
is ready for Julia manual live retest but remains blocked until Julia passes or
waives that live retest.

Manual live false-green audit after `278513b`: Julia's live run invalidates the
`releaseBlockers=none` summary above as too weak. The harness must prove exact
candidate position, slab half/type, repeated placement/extension after the first
side slab, and exact top-face `source.up()` / `stone_slab[type=bottom]` /
`dy=0.0`. Manual live observed lower-half aim producing the upper/top result,
only one accepted top-half slab, repeat placement rejection, and a top-face
floating/ghost/wrong result. New explicit markers are
`[JULIA_BETA4_LIVE_GOBLIN_SIDE_LOWER_WRONG_RESULT_RED]`,
`[JULIA_BETA4_LIVE_GOBLIN_SIDE_UPPER_FIRST_GREEN]` / `_RED`,
`[JULIA_BETA4_LIVE_GOBLIN_REPEAT_PLACEMENT_RED]` / `_GREEN`, and
`[JULIA_BETA4_LIVE_GOBLIN_TOP_FACE_WRONG_RESULT_RED]`. Release remains blocked.

Goblin live-parity scout replaces the dirty exact-candidate WIP as the current
proof direction. The WIP used synthetic `BlockHitResult` interactions and fresh
fixture resets, so its lower/top GREEN markers are not release proof. New marker
set:
`[JULIA_BETA4_LIVE_GOBLIN_PARITY_START]`,
`[JULIA_BETA4_LIVE_GOBLIN_AIM_LOWER_REAL_TARGET]`,
`[JULIA_BETA4_LIVE_GOBLIN_AIM_UPPER_REAL_TARGET]`,
`[JULIA_BETA4_LIVE_GOBLIN_AIM_TOP_REAL_TARGET]`,
`[JULIA_BETA4_LIVE_GOBLIN_AIM_PARITY_FAIL]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_DIAG_START]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_LOWER_DIAG]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_UPPER_DIAG]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_TOP_DIAG]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SEQUENCE_FIRST_DIAG]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SEQUENCE_LOWER_AFTER_FIRST_DIAG]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SEQUENCE_TOP_DIAG]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_HARNESS_AIM_FAIL]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_OWNER_FAIL]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_OCCLUSION_EXPECTED]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SEQUENCE_STATE_MISMATCH]`,
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SUMMARY]`,
`[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_START]`,
`[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_CANDIDATE]`,
`[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_SELECTED]`,
`[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_NONE]`,
`[JULIA_BETA4_LIVE_GOBLIN_LOWER_CORRIDOR_SEQUENCE]`,
`[JULIA_BETA4_LIVE_GOBLIN_CORRIDOR_SUMMARY]`,
`[JULIA_BETA4_LIVE_GOBLIN_DELTA_SCAN]`,
`[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE_FIRST_SIDE]`,
`[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE_LOWER_AFTER_FIRST]`,
`[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE_REPEAT]`,
`[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE_TOP_FACE]`, and
`[JULIA_BETA4_LIVE_GOBLIN_PARITY_SUMMARY]`. Latest manual/live mismatch remains:
manual lower-half routes to upper/top, first upper side accepts one slab, repeat
rejects, top-face ghosts/wrongs; release remains blocked.

Latest targeting classification: the first targeting diagnostic pass proved a
gametest harness bug, not gameplay behavior, because the camera eye/visible
local point did not match the intended ray. The gametest-only harness fix pins
the player pose, uses the intended side eye, and computes side/top points in
dy-adjusted visible local coordinates. The rerun classifies upper side, sequence
first, top face, and sequence top as `TARGET_OK`; lower-after-first is
`OCCLUSION_EXPECTED` because the first placed side slab is physically closer
than the intended lower-side owner point. Summary:
`[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SUMMARY] lower=OCCLUSION_EXPECTED upper=TARGET_OK top=TARGET_OK sequenceFirst=TARGET_OK sequenceLowerAfterFirst=OCCLUSION_EXPECTED sequenceTop=TARGET_OK harnessAimFailures=0 ownerFailures=0 occlusionCases=2 nextAction=choosePlayerRealisticAimPoint`.
No owner/retarget failure is proven by this run, and release remains blocked.

Latest corridor scout: lower-half player-realistic scanning is now part of the
goblin proof. The clean lower case selected `same_side_straight` at
`eye=(37.9,203.2,8.5)`. After the first upper side slab, the sequence selected
`same_side_low` at `eye=(37.9,202.7,8.5)`, with real crosshair target
`pos=40,203,8 face=west` and visual local Y around `0.25`. The subsequent
lower-after-first click changed unexpected `38,203,8`, so it remains RED by
delta, not by occlusion. Latest summary:
`[JULIA_BETA4_LIVE_GOBLIN_CORRIDOR_SUMMARY] lowerCorridor=FOUND lowerAfterFirstCorridor=FOUND sequenceLowerResult=RED repeatPlacement=RED topFace=RED ghost=true wrongDelta=true releaseBlockers=lowerAfterFirst,repeatPlacement,topFace`.
Release remains blocked.

### Repeat Merge Server Seam Audit

Diagnostic-only markers added behind `-Dslabbed.beta4RepeatMergeTrace=true`:

- `[JULIA_BETA4_REPEAT_SEAM_START]`
- `[JULIA_BETA4_REPEAT_SEAM_CLIENT_BEFORE]`
- `[JULIA_BETA4_REPEAT_SEAM_CLIENT_PREDICT]`
- `[JULIA_BETA4_REPEAT_SEAM_CLIENT_RESULT]`
- `[JULIA_BETA4_REPEAT_SEAM_SERVER_TOLERANCE]`
- `[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]`
- `[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]`
- `[JULIA_BETA4_REPEAT_SEAM_SERVER_TICK]`
- `[JULIA_BETA4_REPEAT_SEAM_CLIENT_TICK]`
- `[JULIA_BETA4_REPEAT_SEAM_SUMMARY]`

Current classification: `SERVER_TOLERANCE_REJECT`.

The repeat target before click is `39,203,8` as
`stone_slab[type=bottom] dy=-0.5` on face `UP`. The client predicts and briefly
authors `stone_slab[type=double] dy=-0.5`, and the client action returns
`Success`. Server-side tolerance sees `legalLoweredSameCellMerge=true` but leaves
the native packet center unchanged; no durable server `DOUBLE` follows. Server
and client ticks 1, 5, and 20 all report the target still as
`stone_slab[type=bottom] dy=-0.5`. Client prediction is not server/final proof.
No gameplay implementation is included; `repeatPlacement=RED`, `topFace=RED`,
and release remains blocked.

Automated canonical live-shape goblin harness marker set:
`[JULIA_BETA4_LIVE_GOBLIN_START]`,
`[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_GREEN]`,
`[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID]`,
`[JULIA_BETA4_LIVE_GOBLIN_BASELINE]`,
`[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_PRESENT_SIDE_UPPER_GREEN]`,
`[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_PRESENT_SIDE_LOWER_GREEN]`,
`[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_PRESENT_TOP_FACE_RED]`,
`[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_MISSING_SIDE_GREEN]`,
`[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_MISSING_TOP_FACE_RED]`,
`[JULIA_BETA4_LIVE_GOBLIN_SIDE_LOWER_WRONG_RESULT_RED]`,
`[JULIA_BETA4_LIVE_GOBLIN_SIDE_UPPER_FIRST_GREEN]`,
`[JULIA_BETA4_LIVE_GOBLIN_SIDE_UPPER_FIRST_RED]`,
`[JULIA_BETA4_LIVE_GOBLIN_REPEAT_PLACEMENT_GREEN]`,
`[JULIA_BETA4_LIVE_GOBLIN_REPEAT_PLACEMENT_RED]`,
`[JULIA_BETA4_LIVE_GOBLIN_TOP_FACE_WRONG_RESULT_RED]`,
`[JULIA_BETA4_LIVE_GOBLIN_HITBOX_GREEN]`,
`[JULIA_BETA4_LIVE_GOBLIN_HITBOX_RED]`,
`[JULIA_BETA4_LIVE_GOBLIN_SUMMARY]`, and
`[JULIA_BETA4_LIVE_GOBLIN_DONE]`. Decision use: this harness replaces manual
recreation as the canonical diagnostic for Julia's live shape; it does not
approve release by itself. Any summary with `lowerExact`, `repeatPlacement`,
`topFaceExact`, `supportMissing.side`, or `supportMissing.topFace` not GREEN
keeps release blocked unless Julia explicitly defers that blocker.

Gating note: the harness is opt-in only and is routed from the already-registered
`SlabbedLabLoweredSidePlacementLiveReproClientGameTest` path when
`-Dslabbed.beta4LiveShapeGoblin=true` is present. Default `runClientGameTest`
must not execute the goblin proof.

## Current savepoint

- HEAD: `06724fb`
- Tag: `save/beta4-compound-placement-popoff-fix`
- Branch: `integrate/phase19-into-side-slab-top-support`

## Live result

Total fail. Not release-saveable.

Julia live retest at `06724fb` (evidence harvested in
`tmp/beta4-compound-live-fail-contract-audit-06724fb/`) shows the compound
state is recognized in isolation but is not legal across the broader
placement / survival / support-removal surface.

### Player symptoms (live)

- Aiming at the **bottom half** of the compound `dy=-1.0` full block while
  attempting side placement: flicker / pop-off after the queued tick.
- Aiming at the **top half** of the same compound full block: placement
  goes upward into the wrong column / wrong dy lane.
- **Breaking the lower source slab** under the compound full block: the
  compound full block jumps upward (collapses to `dy=-0.5` or vanilla
  `dy=0`).
- Slab placement adjacent to the compound full block: same flicker / wrong
  column behavior.
- Live feel: not release-saveable.

## What automation proved at `06724fb`

The recent fix stack contributed real, narrow GREEN proofs:

- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_GREEN]` — compound `dy=-1.0`
  is preserved through the anchor finalization for the seeded triad
  (`9bf3bdc`).
- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_TRIAD_*]` — outline / raycast / model
  shape parity for the compound `dy=-1.0` cell.
- `[BETA4_COMPOUND_PLACEMENT_POPOFF_GREEN]` — a single side-adjacent
  placement against the seeded compound source survives the queued tick in
  the lowered lane (`6e0bd10` / `06724fb`).

These are valid proofs of three isolated slices. They do not prove a full
contract.

## Why automation is insufficient

Automation seeds a perfectly legal triad in a fixed orientation and tests
one face / one half / one neighbor at a time. Live play exposes:

1. **Half / face grammar gap**. The compound `dy=-1.0` outline visually
   spans world cells `y=-58` (bottom half) and `y=-57` (top half). The
   block exists at `y=-57`. Side hits at `y=-58` vs `y=-57` are completely
   different placement targets, and the compound state has no documented
   rule for which column / dy lane each half should produce.
2. **Source-truth not self-describing**. Live evidence
   (`run_logs_latest_log.extract.txt` lines around tick 36127–36128 in
   `tmp/beta4-live-retest-06724fb`) shows the compound block as
   `state=Block{minecraft:stone} dy=-1.000000 anchored=true
   persistentFullBlockAnchor=true sourceMode=normal`, with `dy=-1.0`
   re-derived each lookup from the slab below
   (`pos=14,-58,0 ... persistentLoweredSlabCarrier=true`). The boolean
   `persistentFullBlockAnchor` cannot tell apart "I am a `dy=-0.5` anchor"
   from "I am a `dy=-1.0` anchor over a lowered carrier slab". The depth
   is not stored, it is recomputed.
3. **Per-column lane, not per-row lane**. Compound `dy=-1.0` only exists
   for a column that has a `persistentLoweredSlabCarrier` directly below.
   Lateral placement into an adjacent column with empty space below
   cannot legally inherit `dy=-1.0` because no support source exists in
   that column.
4. **Source-removal recompute hazard**. `SlabSupport.getYOffsetInner`
   re-runs `isAdjacentSideSlabLowered(world, pos.down(), belowSlab)` on
   every query. Breaking / replacing the slab below makes the compound
   query fail and the dy collapses to `-0.5` (anchor branch) or `0`
   (vanilla). This is the live "jump" Julia observes.
5. **Neighbor-update / `canPlaceAt` recompute hazard**. The same
   recompute is what `getStateForNeighborUpdate` and `canPlaceAt` rely
   on. Any neighbor change that breaks the predicate signal flips the
   compound block out of the `dy=-1.0` lane.

The current 06724fb state has no single contract that names what should
happen for each face × half × support-removal × reload combination.

## Legal-state decision table

Decision required for each row before any further implementation. Status
columns:

- **Current observed**: what the live build at `06724fb` does.
- **Intended**: TBD by design owner.
- **Required source truth**: what the state law must store / derive to
  guarantee that intended outcome.
- **Proof status**: which automation case (if any) covers this.

| # | Player action | Current observed | Intended | Required state / source truth | Proof status |
| - | ------------- | ---------------- | -------- | ----------------------------- | ------------ |
| 1 | Select / look at compound `dy=-1.0` block | Outline + raycast at `dy=-1.0` for the seeded triad | (decide) | Triad shape parity | TRIAD_GREEN at `06724fb`, only seeded fixture |
| 2 | Place ordinary stone on TOP of compound | (mostly intercepted by intent mixin; not in primary failure surface) | (decide) | Top-of-compound = next compound `dy=-1.0`? Vanilla `dy=0`? | No live proof |
| 3 | Place full block on SIDE LOWER HALF (hit `y=-58` band of the compound) | Flicker / pop-off | (decide: place at compound column `dy=-1.0`? at adjacent column `dy=-1.0`? at adjacent column `dy=-0.5`? vanilla?) | Half→column mapping rule; compound depth in adjacent column requires its own slab carrier | Seeded GREEN only for one face / one column |
| 4 | Place full block on SIDE UPPER HALF (hit `y=-57` band of the compound) | Upward placement / wrong dy | (decide) | Half→column mapping rule | None |
| 5 | Place slab on SIDE LOWER HALF | Flicker / wrong column | (decide: lowered bottom carrier? lowered top? double? vanilla?) | Slab grammar against compound side | None |
| 6 | Place slab on SIDE UPPER HALF | Wrong column / wrong half | (decide) | Slab grammar against compound side | None |
| 7 | Break compound block itself | (decide if it should drop normal item, drop nothing, leave anchor ghost) | (decide) | Anchor cleanup contract | Existing `removeAnchor` paths, not validated for compound |
| 8 | Break the lower source slab (the `persistentLoweredSlabCarrier` directly under the compound) | Compound block jumps up to `dy=-0.5` or vanilla `dy=0` | (decide A / B / C below) | If A: compound must self-describe its depth; if B: re-normalize rule; if C: explicit pop with item drop | None |
| 9 | Neighbor update on a side neighbor without breaking the source slab | (compound stays for now; not directly observed) | Compound stays at `dy=-1.0` | `getStateForNeighborUpdate` must not call into the compound recompute when the column source is unchanged | None |
| 10 | Save / reload / chunk reload of the compound column | `BETA4_RELOAD_JUMP_SYNC` events fire with `oldCount=0 newCount=0`; compound dy depends on slab carrier still being persisted | Compound stays at `dy=-1.0` | Either both blocks rehydrate from disk before the recompute, or the compound block stores its own depth | Reload jump sync is RECORDER ONLY, no GREEN |
| 11 | Chunk unload then reload only the compound column without the slab column loaded | (untested; possible mismatch window) | (decide) | Cross-chunk source-truth ordering | None |
| 12 | Live triad: source slab broken then re-placed quickly | (untested) | (decide) | Compound recovery rule | None |

Cases 3, 4, 5, 6, 8 are the live failures Julia reproduced. Cases 9–12 are
not yet observed but follow from the same source-truth ambiguity.

The later screenshot-shape failure at `fae6d25` keeps this audit release
blocking: slab side placement off the visible upper full-block area and the
top-face skip/ghost path now require explicit RED/GREEN coverage before manual
live acceptance can be claimed.

## Is `dy=-1.0` viable?

**Risky / undecided.** It is viable as a per-column visual height **only
when** every system that reads dy understands "compound depth comes from
the column source slab right below". Today that is true for
`getYOffsetInner` (centrally fixed), torch/non-anchor compound, and
outline/raycast/model parity for the seeded triad.

It is **not yet viable** as a horizontally extensible lane:

- Lateral side placement assumes the new block can also be `dy=-1.0`. That
  only holds if the new block's column also has a
  `persistentLoweredSlabCarrier` directly below. Otherwise the new block
  must fall back to `dy=-0.5` (lowered single carrier) or `dy=0` (vanilla),
  and the half→column mapping for the click must be defined.
- Source-removal collapses dy because the depth is recomputed, not stored.
  The current `persistentFullBlockAnchor` boolean cannot encode "this
  anchor was authored at depth `-1.0` over a lowered carrier and must
  not silently re-normalize to `-0.5`".
- Save / reload survival of `dy=-1.0` is only correct when the slab below
  rehydrates before any block in the compound column is asked for its
  offset. Cross-chunk ordering is not proven.

The `dy=-1.0` value itself is fine. The viability gap is in the **state
law** that places, supports, and persists it.

## Anchor representation risk

`SlabAnchorAttachment.isAnchored` is a boolean stored per-position. The
current code derives the depth from this boolean plus a live query against
the block below:

- `getYOffsetInner` anchor branch (lines around 651, 674–680 in
  `src/main/java/com/slabbed/util/SlabSupport.java`) returns `-1.0` only
  when `isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world,
  belowPos, belowSlab)` evaluates true on every call.
- The "non-anchor" compound branch (lines 691–696) does the same.
- `sourceMode=` strings in the live recorder logs (e.g.
  `sourceMode=dynamicLoweredOrAnchored`, `sourceMode=normal`,
  `sourceMode=persistentLoweredSlabCarrier`) are derived labels, not stored
  attachments.

**Risk**: a single boolean cannot encode the difference between an anchor
authored at `dy=-0.5` over a vanilla / dynamic lowered source and an
anchor authored at `dy=-1.0` over a lowered bottom-slab carrier. Any time
the source-below predicate flips (slab broken, slab replaced, chunk loaded
out of order, neighbor update triggers a re-derivation), the depth flips
with it.

**Richer representation options** (not implemented, design only):

- Add a `compoundDepth` byte / enum to `SlabAnchorAttachment` per anchor
  pos, so an anchor authored at `dy=-1.0` is stored as
  `compoundDepth=COMPOUND_LOWERED` and not re-derived from neighbors.
- Or replace the boolean with a `sourceMode` enum
  (`NORMAL_ANCHOR | LOWERED_CARRIER | COMPOUND_LOWERED_OVER_CARRIER`) and
  let `getYOffsetInner` switch on it without querying the block below.
- Either approach must define explicit rules for what happens when the
  recorded source is invalidated (case 8 in the table). Persistent
  storage by itself does not decide whether the compound should jump,
  pop, re-normalize, or persist.

This is exactly what the doctrine warns against fixing with one more
boolean: the next implementation must commit to a richer representation
**only after** the decision table above is filled in.

## Which proofs prove a slice but not the live contract

- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_GREEN]`: proves the seeded
  triad keeps `dy=-1.0` after `Block.onPlaced` syncs the anchor. Does
  not exercise side placement, support removal, neighbor update, or
  reload.
- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_TRIAD_*]`: proves outline / raycast
  / model shape for the seeded triad cell only.
- `[BETA4_COMPOUND_PLACEMENT_POPOFF_GREEN]`: proves one side-adjacent
  placement against one face of the seeded compound source survives one
  queued tick. Does not exercise both halves, both face axes, slab-held
  adjacency, support-removal, or save/reload.
- `[BETA4_RELOAD_JUMP_RECORDER]` / `[BETA4_OUTLINE_RECORDER]` /
  `[BETA4_PLACEMENT_AUTHOR_RECORDER]`: recorder-only, not proofs.

No proof exists for: side lower-half × side upper-half × slab-held ×
ordinary-held × source-slab-break × neighbor-update × save/reload as a
matrix.

## Recommended next slice

**A. Add a comprehensive RED proof matrix for compound `dy=-1.0` faces /
halves / support removal, before any further implementation.**

Rationale: every prior slice in the recent stack was a single-case fix
followed by a single-case GREEN. The doctrine states a Slabbed state
must be named **and** proven across placement, collision, survival,
neighbor update, reload, triad, and live feel. The compound `dy=-1.0`
state is named but the proof matrix is empty for at least seven of the
twelve decision-table cases.

The matrix must enumerate, with a RED-only opt-in proof per case:

- side LOWER half × N/E/S/W × ordinary-held vs slab-held
- side UPPER half × N/E/S/W × ordinary-held vs slab-held
- TOP-face × ordinary-held vs slab-held
- break source slab below × neighbor update × `getStateForNeighborUpdate`
  recompute
- save / reload / chunk reload (cross-column ordering)
- compound block broken first × source slab broken first

Only after that matrix exists and Julia signs off on the **intended**
column for each case can the team responsibly choose between B (richer
anchor / source mode) and C (revert / narrow `dy=-1.0`). A blind jump to
B risks coding around the wrong cases; a blind jump to C loses a feature
Julia may want.

## Non-negotiables

- No release prep until the matrix is filled, the decisions are made,
  and Julia live-confirms the matrix.
- No retarget / rescue workarounds to compensate for state-law gaps.
- No broad solidity / shape lies. Compound `dy=-1.0` must remain a named,
  documented state.
- No more single-case patches until the contract decision table above
  has explicit "Intended" answers from the design owner.
- Julia live retest remains the final gate. Automation GREEN does not
  unblock release.
- `release/0.2.0-beta.4` stays where it is. Do not move, delete, or
  reassign.

## Matrix proof status (added at `effd6ee`)

A comprehensive opt-in RED proof matrix for the compound `dy=-1.0` lane is
now available. It does not implement gameplay fixes; it enumerates the
current observable behavior of the canonical compound topology
(`STONE` over a `persistentLoweredBottomSlabCarrier` over an anchored
`STONE` over a vanilla bottom slab) across the legal-state decision table
above so a design choice between A / B / C / D can be made on evidence.

- File: `src/gametest/java/com/slabbed/test/SlabbedLabBeta4CompoundContractMatrixClientGameTest.java`
- Property: `-Dslabbed.beta4CompoundContractMatrixRedOnly=true`
- Per-row marker: `[BETA4_COMPOUND_CONTRACT_MATRIX] row=<NN_NAME> ...`
- Final markers:
  - `[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` — at least one row is RED,
    UNDECIDED, or NOT_IMPLEMENTED. Expected current state.
  - `[BETA4_COMPOUND_CONTRACT_MATRIX_GREEN]` — every row GREEN. Do not
    emit until the design table is filled in and every row is decided
    plus implemented.
- Matrix is a no-op when the property is not set. Default
  `runClientGameTest` is unaffected.
- Evidence harvest at `effd6ee`:
  `tmp/beta4-compound-contract-matrix-effd6ee/`.

### Row summary (observed at `effd6ee`, intended by A-prime)

| Row | Name | Observed classification | Live status | Intended beta4 outcome |
| --- | ---- | ----------------------- | ----------- | ---------------------- |
| 1 | `SELECT_EMPTY_HAND_COMPOUND_BODY` | GREEN | automation-only | Compound block owns selection. |
| 2 | `SELECT_STONE_HELD_COMPOUND_BODY` | GREEN | automation-only | Compound block owns selection. |
| 3 | `SELECT_SLAB_HELD_COMPOUND_BODY` | GREEN | automation-only | Compound block owns unless a legal slab-placement face exists; no redirect to beta4-illegal `dy=-1.0` slab placement. |
| 4 | `PLACE_STONE_SIDE_LOWER_HALF` | GREEN | live-confirmed-fail | Packet validity is fixed and downstream side-lane authoring now marks the placed ordinary stone with the compound sidecar at `dy=-1.0`. |
| 5 | `PLACE_STONE_SIDE_UPPER_HALF` | GREEN | live-confirmed-fail | Same as row 4 for full blocks; side slot is ordinary stone with compound sidecar at `dy=-1.0`; no upward/vanilla ghost placement. |
| 6 | `PLACE_SLAB_SIDE_LOWER_HALF` | GREEN | live-confirmed-fail | Clean reject/pass: side slot stays air immediately and after tick; no `dy=-1.0` or ghost `dy=-0.5` slab lane. |
| 7 | `PLACE_SLAB_SIDE_UPPER_HALF` | GREEN | live-confirmed-fail | Clean reject/pass: side slot stays air immediately and after tick; no `dy=-1.0` or ghost `dy=-0.5` slab lane. |
| 8 | `PLACE_BLOCK_ON_TOP` | GREEN | not-yet-live-tested | Place ordinary full block above in same compound lane `dy=-1.0`; do not create `dy=-1.5`. |
| 9 | `SOURCE_SLAB_BREAK` | GREEN (post-sidecar) | live-confirmed-fail (pre-sidecar) | Persistent compound anchor preserves authored `dy=-1.0`; no silent jump to `dy=-0.5`. Sidecar `COMPOUND_FULL_BLOCK_ANCHOR_TYPE` flipped this from RED to GREEN; live retest pending. |
| 10 | `NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK` | GREEN (post-sidecar) | not-yet-live-tested | Same as row 9. Sidecar flipped this from RED to GREEN; live retest pending. |
| 11 | `SAVE_RELOAD_AFTER_COMPOUND` | GREEN | live-confirmed-fail | Preserve authored `dy=-1.0`; should remain green, with live still final. |
| 12 | `CHUNK_UNLOAD_RELOAD_IF_HELPER_EXISTS` | NOT_IMPLEMENTED | not-yet-live-tested | Still not implemented in gametest; live remains final. |

Final marker emitted at `effd6ee`:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED] rows=12 red=4 undecided=4 green=3 notImplemented=1`.

After the beta4 compound full-block anchor sidecar
(`COMPOUND_FULL_BLOCK_ANCHOR_TYPE` on `SlabAnchorAttachment`) lands:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED] rows=12 red=2 undecided=4 green=5 notImplemented=1`.
Rows 9 (`SOURCE_SLAB_BREAK`) and 10 (`NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK`)
flipped from RED to GREEN; the row classifiers in
`SlabbedLabBeta4CompoundContractMatrixClientGameTest` were updated to
emit GREEN when post-break compound `dy` stays at `-1.0` (per A-prime in
`docs/beta4-compound-source-mode-design.md`). Rows 4/6 stay RED
(side-half placement is the next slice). Rows 3/5/7/8 stay UNDECIDED
pending Julia intent. Row 11 stays GREEN (automation only, live still
final). Row 12 stays NOT_IMPLEMENTED. Live retest remains the final
gate; the matrix flip is automation-only evidence.

After the Row 4 packet/hit-validity bridge lands:
`[BETA4_COMPOUND_ROW4_HIT_VALIDITY_GREEN]` emits with
`reason=server_hit_validity_bridge_accepted_compound_visual_hit` and
`finalizationServer=observed_after_packet_acceptance`. The full matrix now
reports `[BETA4_COMPOUND_CONTRACT_MATRIX_RED] rows=12 red=1 undecided=5 green=5
notImplemented=1`. Row 4 moved from RED to UNDECIDED because the packet reaches
server placement and the side slot survives, but it lands as ordinary anchored
`dy=-0.5`; downstream Row 4 finalization/authoring is not complete. Row 6 stays
RED, so the beta4-illegal slab-side `dy=-1.0` lane was not legalized.

After the Row 4 lane-authoring fix lands:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` reports `rows=12 red=1 undecided=4 green=6
notImplemented=1`. Row 4 is GREEN because ordinary stone side placement from
the compound source inherits the compound full-block sidecar and stays
`dy=-1.0`; Row 6 stays RED and Row 7 stays UNDECIDED, so beta4 slab-side
compound lanes remain illegal.

After the Row 6 clean-reject fix lands:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` reports `rows=12 red=0 undecided=4 green=7
notImplemented=1`. Row 6 is GREEN because slab-held lower-half side placement
on the compound source returns `Pass[]` with `accepted=false`, `cleanReject=true`,
the side slot air immediately and after tick, and the support still
`compoundFullBlockAnchor=true` at `dy=-1.0`. This does not legalize a `dy=-1.0`
slab lane or the upper-half Row 7 path.

After the compound matrix closure lands:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` reports `rows=12 red=0 undecided=0 green=11
notImplemented=1`. Rows 3/5/7/8 are GREEN under A-prime: slab-held selection
keeps the compound owner, upper-half ordinary stone side placement stays in the
compound `dy=-1.0` lane, slab-held upper-half side placement cleanly rejects, and
top-of-compound ordinary stone placement is authored in the same `dy=-1.0` lane.
Row 12 remains NOT_IMPLEMENTED because the chunk-only unload/reload helper is
still unavailable in the current client gametest API. This closure does not
legalize any beta4 `dy=-1.0` slab lane or any `dy<-1.0` recursion.

The focused compound slab merge/remap proof slice now lives in
`src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java`
and emits `[JULIA_BETA4_COMPOUND_BELOW_LANE_SIDE_SLAB_PENDING]`,
`[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_GREEN]`,
`[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_PENDING]`, and
`[JULIA_BETA4_COMPOUND_SLAB_DOUBLE_MERGE_PENDING]`. Those markers keep the
old no-lane rejection as superseded/pending while proving that the artificial
Row 3 legal `dy=-0.5` remap path already exists. The row-3 proof record is in
`tmp/beta4-compound-slab-row3-live-742a839/`; Rows 4 and 5 remain pending for
later beta4 work and are not treated as blockers by this automated/focused
proof, but Julia manual live-feel test is still pending. This is not a
generalized rescue/retarget fix.

Superseded history marker: `[JULIA_BETA4_COMPOUND_SLAB_NO_LEGAL_LANE_GREEN]`
used to mark Rows 1/2 as safe rejection. It no longer implies release-safe
behavior after the compound below-lane product decision.

Harness audit note: Row 3 proof must start from proven compound dy=-1.0 ordinary
full-block source; dy=-0.5 source hits are invalid for this row. The corrected
focused harness proves `compoundFullBlockAnchor=true`, clicked source
`dy=-1.0`, and exactly one adjacent legal `dy=-0.5` remap lane before emitting
the Row 3 GREEN marker. Rows 1/2 still require `legalLaneCount=0`; their
direct below-lane support now makes them implemented/proven legal `dy=-0.5`
side placements. Row 1 authors `stone_slab[type=bottom]`; Row 2 authors
`stone_slab[type=top]`. The Row 3 implementation remaps only into the
continuation cell beyond the one existing legal lowered slab lane and keeps
beta4 `dy=-1.0` slab lanes illegal.

Screenshot side-shape discriminator audit at `08cb004`: diagnostic-only harness
markers now compare Rows 1/2, the internal artificial Row 3, and Julia's
screenshot side-shape without changing placement behavior:
`[JULIA_BETA4_NO_LEGAL_LANE_DISCRIMINATOR]`,
`[JULIA_BETA4_INTERNAL_ROW3_DISCRIMINATOR]`, and
`[JULIA_BETA4_LIVE_SCREENSHOT_DISCRIMINATOR]`.

| Case | Clicked source / dy | Below source | Horizontal lane | Helper lane count | Candidate relation | Expected / current |
| --- | --- | --- | --- | --- | --- | --- |
| Rows 1/2 compound below-lane side slab placement | ordinary compound stone, `dy=-1.0` | bottom slab, `dy=-0.5` | air in intended direction | `0` | immediate side cell | GREEN; immediate side slab at `dy=-0.5` |
| Internal Row 3 legal remap | ordinary compound stone, `dy=-1.0` | bottom slab, `dy=-0.5` | legal bottom slab, `dy=-0.5` | `1` | continuation beyond horizontal lane | author lowered slab; GREEN |
| Julia screenshot side shape | upper ordinary compound stone, `dy=-1.0` | bottom slab, `dy=-0.5` | air in intended direction | `0` | immediate side cell for side, `source.up()` for top | lower/upper side slab GREEN; top-face GREEN as `COMPOUND_VISIBLE_OWNER_TOP_SLAB` at `dy=0.0` |

The audit rejects "lowered bottom slab directly below the source" as a safe
predicate: it is shared by Julia's screenshot side-shape and Row 1 no-legal-lane
safe rejection. Product law now intentionally accepts that shared surface rather
than trying to keep Rows 1/2 rejected while allowing the screenshot shape. The
existing horizontal legal-lane continuation used by Row 3 remains a separate
GREEN artificial proof. The top-face ghost/skip path remains a separate
RED/PENDING issue. No full manual live acceptance is claimed.

### Compound matrix closure

- **Row 3** `SELECT_SLAB_HELD_COMPOUND_BODY`: GREEN; slab-held selection keeps
  the compound full block selected because there is no legal beta4 slab lane.
- **Row 4** `PLACE_STONE_SIDE_LOWER_HALF`: GREEN after lane authoring; the
  packet/hit-validity bridge accepts the visual lower-half WEST-face hit
  `(8.0, 202.25, 8.5)` and the side slot survives as compound `dy=-1.0`.
- **Row 5** `PLACE_STONE_SIDE_UPPER_HALF`: GREEN; the upper-half WEST-face
  ordinary stone placement uses the same compound sidecar lane at `dy=-1.0`.
- **Row 6** `PLACE_SLAB_SIDE_LOWER_HALF`: GREEN after clean rejection; aiming
  the visual lower half of the compound's EAST face with slab-held leaves the
  side slot air immediately and after tick, with no slab lane authored.
- **Row 7** `PLACE_SLAB_SIDE_UPPER_HALF`: GREEN after clean rejection; aiming
  the visual upper half of the compound's EAST face with slab-held also leaves
  the side slot air immediately and after tick, with no slab lane authored.
- **Row 8** `PLACE_BLOCK_ON_TOP`: GREEN after top-of-compound authoring; the
  top ordinary stone stays in the compound sidecar lane at `dy=-1.0`, with no
  `dy=-1.5`.

### Rows formerly RED, now GREEN after sidecar

- **Row 9** `SOURCE_SLAB_BREAK`: with the
  `COMPOUND_FULL_BLOCK_ANCHOR_TYPE` sidecar, breaking the lower
  `persistentLoweredBottomSlabCarrier` no longer collapses the compound
  block. Authored `dy=-1.0` is preserved; the matrix classifies GREEN
  (`post-break` compound `dy=-1.0`). Live retest still pending.
- **Row 10** `NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK`: the explicit
  `world.updateNeighborsAlways` pulse after source-break also no longer
  collapses the compound block; the sidecar fast-path in
  `SlabSupport.getYOffsetInner` returns `dy=-1.0` directly. Matrix
  classifies GREEN. Live retest still pending.

### Rows classified UNDECIDED

None after compound matrix closure. Row 12 remains NOT_IMPLEMENTED, not
UNDECIDED.

### Rows classified GREEN

- **Row 1** `SELECT_EMPTY_HAND_COMPOUND_BODY`: outline / raycast /
  selected target all own the compound BlockPos (matches the existing
  `BETA4_COMPOUND_LOWERED_FULL_BLOCK_TRIAD` proof).
- **Row 2** `SELECT_STONE_HELD_COMPOUND_BODY`: same triad ownership while
  holding `minecraft:stone`. The slab-held retarget guard does not
  engage for non-slab held items.
- **Row 11** `SAVE_RELOAD_AFTER_COMPOUND`: in this clean automation
  harness, `TestSingleplayerContext.getWorldSave().open()` reload
  preserves both the compound `dy=-1.0` and the carrier slab's
  `persistentLoweredBottomSlabCarrier` truth. **This is automation-only
  evidence**; Julia's live observation of compound jumping after reload
  remains live-confirmed-fail. The matrix records the harness disagreement
  rather than declaring the issue resolved. The audit row 10 hazard
  ("compound dy depends on slab carrier still being persisted") is not
  closed by this row alone.

### Rows NOT_IMPLEMENTED

- **Row 12** `CHUNK_UNLOAD_RELOAD_IF_HELPER_EXISTS`: the current Fabric
  client gametest API (`fabric-client-gametest-api-v1` `4.3.5`) does not
  expose a chunk-only unload/reload primitive. `ChainSurvivalReproTest`
  documents the same caveat. Recorded as helper-absent.

## Recommendation

Do not implement gameplay fixes until Julia decides the intended outcome
for each UNDECIDED row (3, 5, 7, 8) and the design owner picks among
A / B / C / D for the RED rows (4, 6, 9, 10). The matrix is the contract
surface; it is the input to the design decision, not the output. Release
remains blocked.

## Cross-references

- `docs/beta4-compound-lowered-fullblock-height.md` — height fix,
  collapse / placement-popoff proofs.
- `docs/beta4-reload-jump-persistence-audit.md` — prior reload jump
  classifier work.
- `docs/beta4-live-placement-authoring-proof-gap.md` — why placement
  authoring needed a recorder-first path.
- `docs/beta4-seam-ownership-contract.md` — current seam ownership
  contract; not changed by this audit.
- `docs/beta4-compound-source-mode-design.md` — A-prime decision and the
  authored-depth sidecar plan; "Authored compound anchor depth RED proof"
  section captures the row 9/10 structural argument as
  `[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_RED]` at `84bbb81`.
- Live evidence harvest:
  `tmp/beta4-compound-live-fail-contract-audit-06724fb/`.
- Matrix evidence harvest at `effd6ee`:
  `tmp/beta4-compound-contract-matrix-effd6ee/`.
- Authored-compound-anchor-depth RED proof evidence at `84bbb81`:
  `tmp/beta4-authored-compound-anchor-depth-red-84bbb81/`.
