# Beta4 Live Fail Anchored-UP Audit

## Savepoint

- Commit: `9ed44e3`
- Tag: `save/beta4-visible-slab-side-face-miss-fix`
- Branch: `integrate/phase19-into-side-slab-top-support`

## Live result

Failed. Julia reports no meaningful difference from before `9ed44e3`. Visible upper lowered slab still does not target correctly in live side/body aiming.

## Key live facts

From `tmp/beta4-live-retest-9ed44e3/run_logs_latest_log.copy.log`:

- `[SLAB_HELD_UP_GUARD_SIDE_OWNER_CLASSIFY]` occurrences: `2275`.
- `classification=anchoredUpPreserve` occurrences: `2267` (dominant).
- `visibleUpperSideFaceOwner` occurrences in live: `0`.
- `miss-no-rescue-candidate` occurrences in live: `0`.
- `SLAB_HELD_MISS_SIDE_RESCUE*` occurrences in live: `0`.

Canonical live line facts (first exemplar, `run_logs_latest_log.copy.log:979`):

- `initial`: `hit=true pos=14,-59,-1 side=up`
- `initialState`: `Block{minecraft:stone}`
- `initialDy`: `-0.500`, `initialAnchored`: `true`, `initialLowered`: `true`, `initialFullBlock`: `true`
- `localHit`: `0.318,0.500,0.032`
- `topHit`: `true`, `edgeLike`: `true`, `topInterior`: `false`
- `sideScanCandidateExists`: `true`, `sideScanCandidateReason`: `accepted`
- `sideScanCandidate`: `hit=true pos=14,-58,-1 side=west`
- `sideScanCandidateFacts`: `state=Block{minecraft:stone_slab}[type=bottom,waterlogged=false] dy=-0.500 slabType=bottom anchored=false lowered=true`
- `initialDist2`: `22.370483`, `candidateDist2`: `17.929824`, `candidateMinusInitialDist2`: `-4.440659` (candidate is closer)
- `wouldProduceScanSideSlabFired`: `true`
- `classification`: `anchoredUpPreserve`
- `initialTarget`: preserved as `pos=14,-59,-1 side=up`

## Mechanism hypothesis

The `9ed44e3` proof-green MISS fix targeted the wrong branch. The live failure is BLOCK/UP anchored-UP preservation stealing the visible upper candidate, not MISS side-face rescue.

- Live enters `slabbed$traceSlabHeldUpGuardSideOwnerClassification` because vanilla's raycast already hits a BLOCK (anchored lowered full block at `14,-59,-1` face `up`). The MISS path (`slabbed$traceSlabHeldMissSideRescueClassification`, where `visibleUpperSideFaceOwner` lives) never runs in this case.
- Within the UP-guard classifier the branch condition is `topHit && initialAnchored && initialLowered && initialFullBlock`, which selects `anchoredUpPreserve` and returns `null` (preserve initial). It does **not** check `topInterior` or `edgeLike`, and does **not** compare `candidateDist2` against `initialDist2`. Live shows `edgeLike=true`, `topInterior=false`, and `candidateMinusInitialDist2=-4.440659` — a closer visible upper lowered bottom slab — yet preservation still wins.
- The visible upper lowered slab (type=`bottom`, dy=`-0.5`, lowered=`true`) is a legitimate closer owner at a seam/edge UP hit on the anchored lowered full block. The current UP-guard classifier cannot distinguish a safe top-interior anchored-UP hit from a seam/edge UP hit that coexists with a visible upper side-face candidate, so it preserves in both cases.

## Proof gap

- Automated `[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_GREEN]` (`runBeta4SeamVisibleUpperSideFaceRedCase` in `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java`) asserts `vanillaType=MISS` and exercises only the MISS-rescue path. It does not reproduce the live BLOCK/UP anchored preservation steal.
- `[BETA4_ANCHORED_UP_PRESERVE_GREEN]` treats all anchored-UP preservation as a single correct class; it does not split true top-interior UP hits from seam/native-cell edge UP hits.
- No current proof reproduces: initial BLOCK/UP on anchored lowered full block + `edgeLike=true`/`topInterior=false` + visible upper lowered bottom slab candidate closer than initial.

## Next RED proof

New marker: `[BETA4_SEAM_VISIBLE_UPPER_ANCHORED_UP_STEAL_RED]`, future `[BETA4_SEAM_VISIBLE_UPPER_ANCHORED_UP_STEAL_GREEN]`.

- Scenario: slab-held, vanilla `BLOCK` hit face=`up` on anchored lowered full block; visible upper lowered bottom slab present immediately above-adjacent; aim region is the seam/edge producing `edgeLike=true`, `topInterior=false`, matching the live `localHit` geometry.
- Expected owner class: `VISIBLE_UPPER_LOWERED_SLAB`.
- Actual (pre-fix) owner class: `ANCHORED_FULL_BLOCK` with `classification=anchoredUpPreserve`.
- Required asserts:
  - `sideScanCandidateExists=true`
  - candidate state is `stone_slab[type=bottom]`, `lowered=true`, `dy=-0.500`
  - `candidateDist2 < initialDist2` (i.e. `candidateMinusInitialDist2 < 0`)
  - `classification=anchoredUpPreserve`
  - `finalOwner` equals initial anchored full block pos (proves the steal)
  - `visibleOwnerWon=false`
- Must use the BLOCK/UP path tracer `SLAB_HELD_UP_GUARD_SIDE_OWNER_CLASSIFY`, not the MISS path.

## Audit answers

1. Live enters `SLAB_HELD_UP_GUARD_SIDE_OWNER_CLASSIFY` because vanilla's outline raycast already hits a BLOCK (anchored lowered stone at `14,-59,-1` face `up`). The MISS path where `visibleUpperSideFaceOwner` lives is only reached when the initial target is `MISS`/no-BLOCK, which is never the case here.
2. `anchoredUpPreserve` wins because its branch is `topHit && initialAnchored && initialLowered && initialFullBlock`. Live matches all four regardless of `topInterior`, `edgeLike`, and `candidateDist2`. The classifier does not compare distances and does not split interior vs seam, so a closer visible upper lowered bottom slab is ignored and initial is preserved.
3. No. The current automated side-face proof reproduces only `MISS` + `visibleUpperSideFaceOwner` rescue. It does not reproduce BLOCK/UP anchored preservation stealing a closer visible upper lowered slab.
4. `[BETA4_SEAM_VISIBLE_UPPER_ANCHORED_UP_STEAL_RED]` (future GREEN) covering the BLOCK/UP anchored-preservation path with `edgeLike=true`, `topInterior=false`, closer visible upper lowered bottom slab candidate, asserting the visible upper lowered slab owns the final target and `anchoredUpPreserve` no longer steals at the seam.
5. Anchored-UP preservation proof is too broad. It needs to split (a) true top-interior UP hit (`topInterior=true`, `edgeLike=false`) where preservation must stay GREEN, versus (b) seam / native-cell edge UP hit (`topInterior=false`, `edgeLike=true`) with a closer visible upper lowered slab candidate where preservation must be classified as a steal and fail RED until fixed.

## Non-negotiables

- No gameplay patch before the RED proof is in place and failing.
- No release prep, no `release/0.2.0-beta.4` move, no beta4 upload.
- Preserve `[BETA4_ANCHORED_UP_PRESERVE_GREEN]` for true top-interior anchored-UP cases.
- Preserve `[BETA4_ADJACENT_VISIBLE_SEAM_GREEN]` and the adjacent visible proof.
- Preserve `[BETA4_SEAM_NO_RESCUE_GREEN]` and the no-rescue boundary.
- Do not weaken or delete the existing MISS-rescue `[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_GREEN]` proof; it remains correct for its branch.
