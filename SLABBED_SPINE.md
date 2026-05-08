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

Reload persistence blocker: Julia observed that an ordinary lowered block/log jumps up to vanilla height after load back in or chunk reload. Current local evidence proves pre-save source truth for ordinary `minecraft:stone` full-block anchors and the lowered bottom slab carrier, while the clean save/close/open proof route stayed GREEN. A default-off live timing recorder now runs behind `-Dslabbed.beta4ReloadJumpRecorder=true` and emits `[BETA4_RELOAD_JUMP_RECORDER_START]`, `[BETA4_RELOAD_JUMP_RECORDER]`, and `[BETA4_RELOAD_JUMP_SYNC]`; the follow-up model/render-view recorder runs behind `-Dslabbed.beta4ModelDyRecorder=true` and emits `[BETA4_MODEL_DY_RECORDER_START]`, `[BETA4_MODEL_DY_RECORDER]`, and `[BETA4_MODEL_DY_RERENDER]`; the outline/crosshair recorder runs behind `-Dslabbed.beta4OutlineRecorder=true` and emits `[BETA4_OUTLINE_RECORDER_START]` and `[BETA4_OUTLINE_RECORDER]`. The raycast-shape parity layer is now locally GREEN: `slabbed.beta4OutlineHitRaycastMissRedOnly` emits `[BETA4_OUTLINE_HIT_RAYCAST_MISS_GREEN] classification=RAYCAST_SHAPE_GREEN` for lowered anchored `minecraft:stone` at `14,-57,0`, with matching lowered outline/raycast bounds and `raycastHit=true`; the harness still logs `crosshairMissReproduced=false`. See `docs/beta4-reload-jump-persistence-audit.md`. Required next gate: Julia live retest of the visible outline/crosshair feel before any beta4 release prep.

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
