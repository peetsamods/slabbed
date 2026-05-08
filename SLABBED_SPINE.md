# SLABBED_SPINE.md -- Active Project Spine

This is the active operating spine for Slabbed. It is not the Git working tree, not a full archive, and not a replacement for source doctrine.

Use it to know the current root, branch, HEAD, savepoint, active slice, invariants, proof gates, and next safe step.

## Read order

1. `00_SLABBED_SOURCE_INDEX.md`
2. `01_SLABBED_CANONICAL_DOCTRINE.md`
3. `02_SLABBED_ACTIVE_STATUS.md`
4. `SLABBED_SPINE.md`

## Canonical root

`/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`

The older `/Users/joolmac/CascadeProjects/Slabbed` checkout is archive/recovery only unless Julia explicitly says otherwise.

## Current branch / HEAD / tag

- Branch: `integrate/phase19-into-side-slab-top-support`
- Current operating base HEAD: `e787bf1`
- Current operating base tag: `save/beta4-live-retarget-source-recorder`
- Prior live-first classifier base: `767f735` / `save/beta4-live-first-seam-owner-classifier`
- Reverted failed classifier: `763434e` / `save/beta4-seam-owner-classifier`
- Stale, provenance-confusing release tag for this issue: `release/0.2.0-beta.4`

Do not move, delete, overwrite, or reuse `save/beta4-seam-owner-classifier`; it remains historical failed evidence.

## Current tracked tree state

Tracked tree is clean.
`tmp/` may remain intentionally untracked and must not be staged.

## Current product goal

Classify beta4 reload/chunk-jump persistence before any further retarget or owner-rule work.

## Current proof note

Release remains blocked. The failed shared seam-owner classifier `763434e` was reverted by `b3a09db`; the live-first classifier at `767f735` passed harness proof but failed Julia live side-face validation. The side-face MISS fix at `9ed44e3` is automation-green (`[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_GREEN]`) but Julia's live retest also failed: live evidence shows the BLOCK/UP anchored-UP preservation path stealing the visible upper lowered slab (2275 `SLAB_HELD_UP_GUARD_SIDE_OWNER_CLASSIFY`, 2267 `classification=anchoredUpPreserve`, 0 `visibleUpperSideFaceOwner`, 0 `miss-no-rescue-candidate`), so the automation-green MISS fix targeted the wrong branch. See `docs/beta4-live-fail-anchored-up-audit.md`. Next RED proof needed: `[BETA4_SEAM_VISIBLE_UPPER_ANCHORED_UP_STEAL_RED]` (future GREEN). Do not upload beta4, do not move `release/0.2.0-beta.4`, and do not call the seam issue fixed.

Recorder status: gated live retarget recorder added behind `-Dslabbed.beta4LiveRetargetRecorder=true`; it emits `[BETA4_LIVE_RETARGET_RECORDER_START]`, `[BETA4_LIVE_RETARGET_RECORDER]`, and `[BETA4_LIVE_RETARGET_SOURCE_TRUTH]` lines for Julia's live seam capture. This is evidence-only and does not unblock release.

Reload persistence blocker: Julia observed that an ordinary lowered block/log jumps up to vanilla height after load back in or chunk reload. Current local evidence proves pre-save source truth for ordinary `minecraft:stone` full-block anchors and the lowered bottom slab carrier, while the clean save/close/open proof route stayed GREEN. A later fresh ordinary placement harness against a dynamic non-persistent lowered bottom slab source also stayed GREEN and was archived as unfaithful fixture evidence; no gameplay fix was made from it. A default-off placement-authoring recorder now runs behind `-Dslabbed.beta4PlacementAuthorRecorder=true` and emits `[BETA4_PLACEMENT_AUTHOR_RECORDER_START]`, `[BETA4_PLACEMENT_AUTHOR_RECORDER]`, and `[BETA4_PLACEMENT_AUTHOR_AFTER_TICK]` for Julia's exact break/re-place/reload sequence. The existing live timing recorder runs behind `-Dslabbed.beta4ReloadJumpRecorder=true`; the model/render-view recorder runs behind `-Dslabbed.beta4ModelDyRecorder=true`; the outline/crosshair recorder runs behind `-Dslabbed.beta4OutlineRecorder=true`. The raycast-shape parity layer is locally GREEN, but release remains blocked pending a live placement-authoring capture and Julia retest. See `docs/beta4-reload-jump-persistence-audit.md` and `docs/beta4-live-placement-authoring-proof-gap.md`.

Compound lowered full-block height (named legal state): A freshly placed ordinary full block directly above a legal lowered bottom slab carrier must use compound `dy=-1.0`, not the generic anchored full-block `dy=-0.5`. Formula: `fullBlockDy = sourceBottomSlabDy - 0.5`. The live placement-author recorder evidence at `9bf3bdc` (`tmp/beta4-placement-authoring-recorder-9bf3bdc`) showed `placedDy=-1.0` at `place-return` collapsing to `placedDy=-0.5` after `Block.onPlaced` recorded the persistent full-block anchor. The fix lives in `SlabSupport.getYOffsetInner` and folds the existing `isAdjacentSideSlabLowered` compound predicate into the anchor branch, so an anchored ordinary full block above a lowered bottom slab carrier returns `-1.0`. Opt-in proof property: `-Dslabbed.beta4CompoundLoweredFullBlockCollapseRedOnly=true`; markers `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_RED]` / `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_GREEN]`. See `docs/beta4-compound-lowered-fullblock-height.md`. Release remains blocked pending Julia live retest.

Compound lowered full-block contract audit (live-fail baseline at `06724fb` / `save/beta4-compound-placement-popoff-fix`): the recent compound stack (`9bf3bdc` collapse fix, `5a5c3bd` owner fix, `6e0bd10` placement-popoff fix, `06724fb` integrated savepoint) is **automation-green but live-failed**. Julia's live retest at `06724fb` reproduced bottom-half side placement flicker / pop-off, top-half upward placement, and source-slab-break upward jump; live evidence is harvested in `tmp/beta4-compound-live-fail-contract-audit-06724fb/`. The compound `dy=-1.0` lane is per-column-only (depth recomputed each query from the `persistentLoweredSlabCarrier` directly below) and the boolean `persistentFullBlockAnchor` cannot encode whether an anchor was authored at `dy=-0.5` or `dy=-1.0`. A-prime is now documented in `docs/beta4-compound-source-mode-design.md`: explicit authored lane/depth source mode for compound ordinary full blocks, with no `dy=-1.0` slab lane in beta4. Release remains **blocked**. Next slice is RED proof for richer authored compound anchor depth storage.

Compound contract matrix complete at `205ec36` / `save/beta4-compound-fullblock-contract-matrix` (gated proof only, no gameplay fix). Property `-Dslabbed.beta4CompoundContractMatrixRedOnly=true` runs `SlabbedLabBeta4CompoundContractMatrixClientGameTest` and emits `[BETA4_COMPOUND_CONTRACT_MATRIX] row=<NN_NAME> ...` per row plus `[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` (or future `_GREEN`). The matrix reports `rows=12 red=4 undecided=4 green=3 notImplemented=1`. RED rows: side-lower-half stone placement (4), side-lower-half slab placement (6), source-slab-break compound jump (9), neighbor-update-after-source-break compound jump (10). Formerly UNDECIDED rows now have intended beta4 outcomes under A-prime: slab-held select keeps the compound selected unless a legal slab face exists (3), upper-half stone placement uses the compound full-block lane (5), upper-half slab placement rejects cleanly or preserves selection (7), and top-of-compound ordinary full-block placement stays at `dy=-1.0` rather than recursing to `dy=-1.5` (8). GREEN rows remain empty-hand select (1), stone-held select (2), and save+reload via `TestWorldSave.open()` (11; automation-only, live remains final). NOT_IMPLEMENTED: chunk unload+reload (12; helper absent in `fabric-client-gametest-api-v1` 4.3.5). Release remains **blocked**; next slice is RED proof for authored compound anchor depth storage, not release prep. See `docs/beta4-compound-lowered-fullblock-contract-audit.md`, `docs/beta4-compound-source-mode-design.md`, and `tmp/beta4-compound-contract-matrix-effd6ee/`.

Authored compound anchor depth sidecar implementation: the RED proof at `84bbb81` was flipped to GREEN by adding a beta4-narrow `COMPOUND_FULL_BLOCK_ANCHOR_TYPE` sidecar attachment on `SlabAnchorAttachment` (additive, not a replacement of `ANCHOR_TYPE`). The sidecar is an `AttachmentType<LongOpenHashSet>` of packed positions persisted and synced via the existing data-attachment plumbing. Authoring fires inside `addAnchorUnchecked` whenever the placed position satisfies `qualifiesForCompoundFullBlockAnchor` (ordinary full block above a `SlabSupport.isLoweredCompoundSourceSlab`); cleared together with `removeAnchor` when the compound block itself is broken/replaced. `SlabSupport.getYOffsetInner` consults the sidecar inside the anchored branch *before* the existing dynamic compound predicate so authored `dy=-1.0` survives source slab removal. Client/non-World render views see the sidecar via `clientCompoundFullBlockAnchorLookup`, wired in `SlabAnchorClientSync` together with chunk-load rerender and `onAttachedSet` rerender listeners. Opt-in proof property `-Dslabbed.beta4AuthoredCompoundAnchorDepthRedOnly=true` now emits `[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_GREEN]` (`postCompoundFullBlockAnchor=true`, `actualDy=-1.000`, `sidecarCanExposeCompoundLane=true`); contract matrix at `-Dslabbed.beta4CompoundContractMatrixRedOnly=true` now reports `rows=12 red=2 undecided=4 green=5 notImplemented=1` (rows 9 `SOURCE_SLAB_BREAK` and 10 `NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK` flipped to GREEN). Beta4 narrow scope preserved: no `dy=-1.0` slab lane, no slab side/half authoring from compound full blocks, no recursion below `dy=-1.0`. Release remains **blocked** pending matrix rows 4/6 (side-half placement), the undecided rows (3/5/7/8), and Julia live retest. Next slice likely targets rows 4/6 placement grammar or the slab-rejection contract.

Row 4 packet/hit-validity RED audit at `43b1214` / `save/beta4-compound-sidecar-anchor-fix`: Row 4 `PLACE_STONE_SIDE_LOWER_HALF` is blocked before server-side placement finalization. The client accepts/predicts stone placement from the compound WEST lower-half side hit (`support pos=8,203,8`, `dy=-1.0`, `compoundFullBlockAnchor=true`, hit `(8.0, 202.25, 8.5)`), then the server rejects the `UseItemOnPacket` as too far from native hit block `BlockPos{x=8, y=203, z=8}` and the side slot returns to air. Opt-in proof property `-Dslabbed.beta4CompoundRow4HitValidityRedOnly=true` emits `[BETA4_COMPOUND_ROW4_HIT_VALIDITY_RED]`; the full matrix remains `rows=12 red=2 undecided=4 green=5 notImplemented=1`. No gameplay fix was made. Release remains **blocked**; next implementation slice is a narrow server hit-validity bridge for compound full-block visual bounds, not anchor authoring, retarget, model/shape, or placement inheritance.

Row 4 packet/hit-validity bridge implementation: a narrow server-side `ServerInteractBlockHitToleranceMixin` shifts the vanilla `ServerPlayNetworkHandler#onPlayerInteractBlock` hit-center reference only for ordinary full-block compound anchors at `dy=-1.0`, horizontal visual-bound hits, and non-slab full-block held items. It does not change retarget, model, outline, raycast, release files, dy below `-1.0`, or beta4-illegal slab-side compound lanes. Opt-in proof `-Dslabbed.beta4CompoundRow4HitValidityRedOnly=true` now emits `[BETA4_COMPOUND_ROW4_HIT_VALIDITY_GREEN]` with `finalizationServer=observed_after_packet_acceptance`; the packet rejection is gone. Matrix now reports `rows=12 red=1 undecided=5 green=5 notImplemented=1`: row 4 moved to UNDECIDED because placement survives as ordinary anchored `dy=-0.5`, while row 6 remains RED. Release remains **blocked**; next slice is either downstream Row 4 compound-lane finalization/authoring or Row 6 clean rejection.

Row 4 lane-authoring implementation: `SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor` now preserves the ordinary anchor and additionally writes `COMPOUND_FULL_BLOCK_ANCHOR_TYPE` only when an ordinary full block is placed side-adjacent, same-Y, one horizontal step from an ordinary compound full-block source at `dy=-1.0`. Row 4 `PLACE_STONE_SIDE_LOWER_HALF` flips GREEN with the placed stone side slot authored at `dy=-1.0` and `compoundFullBlockAnchor=true`. Row 6 remains RED and Row 7 remains UNDECIDED; beta4 slab-side compound lanes remain illegal, and release remains **blocked**. Next likely slice: Row 6 clean slab-side rejection, or Row 5/8 full-block upper/top.

Row 6 slab-side clean rejection: slab-held lower-half side placement from an authored compound full block at `dy=-1.0` now cleanly returns pass before slab remap can author a lowered slab lane. Matrix Row 6 `PLACE_SLAB_SIDE_LOWER_HALF` flips GREEN with `cleanReject=true`: side slot stays air immediately and after tick, support remains `compoundFullBlockAnchor=true` at `dy=-1.0`, Row 4 remains GREEN, and the full matrix reports `rows=12 red=0 undecided=4 green=7 notImplemented=1`. No beta4 `dy=-1.0` slab lane or lower-half `dy=-0.5` ghost slab is legalized. Release remains **blocked** pending Rows 3/5/7/8 decisions and Julia live retest.

Authored compound anchor depth RED proof captured at `84bbb81` / `save/beta4-authored-compound-anchor-depth-red-proof` (gated proof only, no gameplay fix). Property `-Dslabbed.beta4AuthoredCompoundAnchorDepthRedOnly=true` runs `SlabbedLabBeta4AuthoredCompoundAnchorDepthClientGameTest`, seeds the matrix row 9/10 fixture, removes the lower `persistentLoweredBottomSlabCarrier` plus an explicit `world.updateNeighborsAlways` pulse, and emits `[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_FACTS]`, `[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_PHASE]` (pre/post), `[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_SUMMARY]`, and `[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_RED]` (future `_GREEN`). Captured facts at `84bbb81`: `phase=preSourceRemoval placedDy=-1.000 placedPersistentFullBlockAnchor=true sourceDy=-0.500 sourcePersistentLoweredSlabCarrier=true expectedAuthoredDy=-1.000 | phase=postSourceRemoval placedPersistentFullBlockAnchor=true actualDy=-0.500 expectedAuthoredDy=-1.000 authoredDepthMissing=true currentAnchorCanExposeDepth=false classification=RED`. The proof confirms `SlabAnchorAttachment.ANCHOR_TYPE` is `AttachmentType<LongOpenHashSet>` with no per-position payload and that no public `dy`/`depth`/`lane` accessor exists on `SlabAnchorAttachment` (reflective probe `none`); the boolean `persistentFullBlockAnchor` therefore structurally cannot encode the authored compound `dy=-1.0`. Default `runClientGameTest` remains `BUILD SUCCESSFUL` (no behavior change). Next implementation slice: add `COMPOUND_FULL_BLOCK_ANCHOR_TYPE` sidecar attachment for beta4 rather than replacing `ANCHOR_TYPE`. Release remains **blocked**. See `docs/beta4-compound-source-mode-design.md` ("Authored compound anchor depth RED proof") and `tmp/beta4-authored-compound-anchor-depth-red-84bbb81/`.

Proof status: gated RED proof `-Dslabbed.beta4SeamVisibleUpperAngleGeneralRedOnly=true` captures the angle-general BLOCK/UP failure where `topInterior=true`, `edgeLike=false`, a closer visible-upper lowered slab candidate exists, and `anchoredUpPreserve` still keeps the anchored owner. This is proof-only and does not unblock release.

Proof gap: Julia's stone-held live retarget trace proves `heldIsSlab=false`, initial ordinary stone at `16,-59,-1`, final lowered `stone_slab` at `14,-58,0`, and `sideSlabRetargetFired=true`, but two harness attempts plus one coordinate replay attempt did not produce a faithful saveable RED. See `docs/beta4-stone-held-side-slab-retarget-proof-gap.md`; release remains blocked.

Implementation status: retarget and owner-rule work is paused until reload/chunk-jump persistence is classified. Release remains blocked.

Current beta4 seam proof classes:

- Anchored-UP preservation remains GREEN for centered valid `UP` hits on an anchored lowered full block: `[BETA4_ANCHORED_UP_PRESERVE_GREEN]`.
- Screenshot-intent visible upper owner is GREEN for the top/above harness path only: `[BETA4_SEAM_VISIBLE_UPPER_SLAB_GREEN]`.
- Live side-face aim on the visible upper lowered slab is GREEN in the opt-in proof after the MISS-side fix: `[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_GREEN]`.
- Adjacent visible target preservation remains GREEN: `[BETA4_ADJACENT_VISIBLE_SEAM_GREEN]`.
- Slab-held air/behind/miss no-rescue boundary is GREEN: `[BETA4_SEAM_NO_RESCUE_GREEN]`.

Contract note: `docs/beta4-seam-ownership-contract.md`.

## Non-negotiable invariants

- Preserve the current phase19 branch/HEAD/tag truth.
- Do not drift into broad chaos by default.
- Do not chase Phase20 or Ultra2 unless a fresh live failure points there.
- Treat the spine as current context, but verify against Git before edits.
- Do not upload beta4, move `release/0.2.0-beta.4`, broaden rescue, change `SlabSupport` lane grammar, or add a final Bug Blaster for the current seam issue yet.

## Latest fixed Bug Blaster

Title: Slab-Held Anchored UP-Hit Rescue Guard
Invariant: A valid `UP` hit on an anchored lowered full block must keep ownership while holding a slab unless a proven equal-or-closer legal side-placement owner should actually win.
Root cause: In the first `0.2.0-beta.4` release candidate, slab-held side-slab rescue was too eager. Vanilla targeting correctly hit the anchored lowered stone full block at `24,201,0` face `up`, but Slabbed's slab-held retarget path classified the nearby lowered `DOUBLE` slab at `24,202,0` face `west` as `sideOwnerWouldWin`, stealing the cyan outline/target from the block Julia was actually aiming at. This recreated the live hitbox-targeting bug even though compile, gametest, clean build, jar scan, and `jdeps` had all passed.
Fix: Narrowed the slab-held anchored-lowered-full-block `UP` branch in `GameRendererCrosshairRetargetMixin.java` so the competing side-slab candidate is classified as `anchoredUpPreserve` instead of `sideOwnerWouldWin`. This preserves valid anchored lowered full-block top ownership and prevents slab-held rescue from jumping the outline to the upper lowered double slab.
Proof: Added focused gated proof in `SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java` using `-Dslabbed.juliaBeta4TargetingRedOnly=true`. Before the fix, proof emitted `[JULIA_BETA4_TARGETING_RED]`: vanilla target `24,201,0/up`, final target stolen by `24,202,0/west`, `sideOwnerWouldWin=true`. After the fix, proof emitted `[JULIA_BETA4_TARGETING_GREEN]`: final target stayed `24,201,0 face=up`, `classification=anchoredUpPreserve`. `compileJava compileGametestJava`, focused proof, full `runClientGameTest --console plain`, `clean build`, jar contents scan, and `jdeps` hard-reference scan all passed.
Savepoint: `ad4f78e` / `save/beta4-anchored-up-hit-rescue-guard`; branch pushed yes; tag pushed yes. Release tag `release/0.2.0-beta.4` was corrected from bad commit `c79c5f6` to fixed audited commit `ad4f78e`.
Status: Fixed.

Title: Slab-held retarget parity improvements
Invariant: Holding a slab must not globally suppress valid lowered owner targeting. Slab-held protection may preserve true top/slab-placement intent only when no proven same-ray visible owner should win.
Root cause: Slab-held targeting preserved old lowered slab / UP / MISS paths before comparing a proven visible lowered owner, so `stone_slab` held could suppress the same owner even when normal block-held targeting could reach scan-side-slab-fired or anchored full-block rescue.
Fix: Added proof-backed `sideOwnerWouldWin` / anchored full-block owner comparisons so slab-held targeting can let the same visible owner win while preserving Phase19 true-top protection.
Proof: `compileJava`, `compileGametestJava`, and `runClientGameTest` passed; Julia live-tested and confirmed targeting was a lot better / major mismatch fixed before savepoint.
Savepoint: `571ba89` / `save/slab-held-retarget-parity-improvements`
Status: Fixed / saved.

Title: Lowered slab face placement inheritance
Invariant: Visible lowered slab face placement must author the placed object into the correct legal lowered state. Placement success is not enough; placed dy/anchor truth, preview/placement agreement, orphan teardown, and live feel must agree.
Root cause: Targeting correctly selected a visible lowered slab face, but placement authored adjacent blocks in vanilla height. For slabs, placed slab initially became `dy=0.0` / ghost-face mismatch. For ordinary blocks/logs/grass, placement also authored `dy=0.0` until source qualifier was corrected. A false-green proof had made the source slab persistent; live source was non-persistent but legally lowered by a carrier below.
Fix: Slab placement against lowered slab face now inherits the dynamic lowered lane without making the slab persistent. Ordinary full blocks placed against a legal non-persistent lowered bottom slab source now use the established anchored full-block path. Post-placement slab-held targeting now respects the newly placed anchored full-block owner instead of preserving the old lowered slab face.
Proof: `LIVE_CLICK_PAIR_BOTTOM_SLAB_LANE_INHERITANCE` green with placed slab `dy=-0.5` and `persistentLoweredSlabCarrier=false`; `LIVE_CLICK_PAIR_FULL_BLOCK_LANE_INHERITANCE` green with source `persistentLoweredSlabCarrier=false`, placed full block `dy=-0.5`, `anchored=true`; `runOrphanedLoweredLaneSupportRemovalCase` green; `runClientGameTest` passed; Julia live-tested and said "Perfect! Save."
Savepoint: `04744e1` / `save/lowered-slab-face-placement-inheritance`
Status: Fixed / saved.

## Recent relevant savepoints

- `save/lowered-slab-face-placement-inheritance` (`04744e1`): current saved state for lowered slab face placement inheritance, full-block lane inheritance, and post-place anchored-owner targeting.
- `save/slab-held-retarget-parity-improvements` (`571ba89`): prior slab-held retarget parity savepoint, now documented above as the upstream targeting fix.
- Pending savepoint: debug helper classpath closure. Packaging/classpath blocker fixed by removing or bridging production/runtime hard-links to excluded debug helpers. `compileJava compileGametestJava`, `runClientGameTest`, `clean build`, release jar leakage scan, `jdeps` hard-reference scan, and source direct-import scan passed. No gameplay behavior was intentionally changed.
- `save/real-lowered-bottom-slab-under-placement-persistence`
- `save/real-placed-lowered-bottom-slab-persistence` is historical only and should not be treated as final truth.

## Current next action

After the classifier savepoint, the next required gate is Julia live validation of the beta4 seam table before any release candidate decision. Do not upload beta4 or move `release/0.2.0-beta.4`.

## Suggested live run command

`./gradlew runClientGameTest --console plain`

## Suggested log extractor

`rg -n "under-placement|8:16 PM|\\[SLABBED\\]|\\[GAME_TEST\\]"`

## Live-note format

Capture the exact setup, the observed placement result, the first failure point if any, and whether the run stayed on the current phase19 slice.

## Do not chase by default

- Do not widen into unrelated gameplay cleanup.
- Do not treat old branches or old savepoints as current just because they are nearby.
- Do not chase Phase20 or Ultra2 without a fresh live contradiction.

## Proof required before declaring fixed

- Clean root and correct branch/HEAD/tag.
- Live micro-test result for the 8:16 PM under-placement setup.
- Diff check or equivalent proof that no unrelated files changed.

## Savepoint rule

If the slice changes, update the source pack and spine together so the current operating state stays explicit.

## Notion/source update targets

- `00_SLABBED_SOURCE_INDEX.md`
- `01_SLABBED_CANONICAL_DOCTRINE.md`
- `02_SLABBED_ACTIVE_STATUS.md`
- `SLABBED_SPINE.md`
