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
- Current operating base before compound visible slab lane law slice: `d7ef534`
- Current operating base tag before compound visible slab lane law slice: `save/beta4-manual-live-delayed-final-trace`
- Prior live-first classifier base: `767f735` / `save/beta4-live-first-seam-owner-classifier`
- Reverted failed classifier: `763434e` / `save/beta4-seam-owner-classifier`
- Stale, provenance-confusing release tag for this issue: `release/0.2.0-beta.4`

Do not move, delete, overwrite, or reuse `save/beta4-seam-owner-classifier`; it remains historical failed evidence.

## Current tracked tree state

Tracked tree is clean.
`tmp/` may remain intentionally untracked and must not be staged.

## Current Beta 3.5 fence/wall owner/server-hit status

Operating base before this owner/server-hit slice: `fbbbd68` / `save/beta35-fence-wall-live-reject-tracer` on `integrate/phase19-into-side-slab-top-support`.

The `fbbbd68` live tracer worked. Julia's live capture proved fence/wall contact and triad are green (`LIVE_CONTACT_GREEN=2058`, `LIVE_CONTACT_GAP=0`, `LIVE_TRIAD_MISMATCH=0`) but still showed `LIVE_OWNER_GAP=1495` and two captured `SERVER_HIT_TOO_FAR` rows. Current fixed classification for the focused reproduction is `failureLayer=NONE`; previous failure layer was `LIVE_OWNER_GAP_PLUS_SERVER_SHIFTED_HIT_TOLERANCE`.

Narrow production change: `GameRendererCrosshairRetargetMixin` now lets the proven Beta 3.5 visible object owner set (`FenceBlock` / `WallBlock` family plus exact anvil) win over side-slab scan when the corrected visible object shape is intersected. `ServerInteractBlockHitToleranceMixin` validates legal Slabbed-lowered fence/wall/anvil target hits against the shifted Slabbed center when the shifted center is still inside vanilla component tolerance. This does not globally widen hit tolerance.

Focused proof flag: `-Dslabbed.beta35FenceWallOwnerServerHit=true -Dslabbed.beta35FenceWallLiveInspect=true`. Required green marker: `JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_GREEN`. The reproduced row now reports `finalDecision=object-shape-owner-preserve`, `ownerClassification=LIVE_OWNER_GREEN`, `SERVER_SHIFTED_HIT_GREEN`, `contactGap=0.000000`, `triadCoLocated=yes`, and `failureLayer=NONE`.

Julia live command:

```bash
JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallLiveInspect=true" ./gradlew --no-daemon runClient --console plain
```

No contact dy rewrite was made in this slice. No release audit was run, no release tag was moved, and scope remains fence/wall/anvil owner/server-hit behavior plus existing floor_torch/candle/flower_pot regression status. Standing signs, lanterns, chains, redstone, rails, buttons/levers, wall/hanging signs, panes, doors, and trapdoors remain out of scope. Savepoint target: `save/beta35-fence-wall-owner-server-hit`.

## Current product goal

Classify beta4 reload/chunk-jump persistence before any further retarget or owner-rule work.

Current Beta 4 status:

- `33f7db6` / `save/beta4-render-snap-audit` was audit-passing but manually rejected by Julia.
- `release/0.2.0-beta.4` was moved to `33f7db6` but is now invalid/retracted and not publishable.
- Julia manual video review after the audit rejected the candidate: render/model snap and slab placement jank remain unacceptable.
- Known failure is no longer minor deferred polish; it is release-blocking.
- Current release state: BLOCKED. Current candidate status: rejected release candidate.
- top, side, and repeat placement now require the bounded compound visible slab lane design
- release remains blocked
- candidate note superseded by product law: authored/persistent compound full-block owners at `dy=-1.0` may require named, source-owned compound visible slab lane states rather than forcing all slab results into `dy=0.0` or `dy=-0.5`.
- failed Row 3 WIP patch preserved under `tmp/beta4-compound-slab-harness-audit-7c45fc0/failed-row3-wip-full.patch`

Manual visual acceptance run at 78c0f01:

- Julia accepts COMPOUND_VISIBLE_SLAB_LANE functionally after placement.
- lower / upper / repeat-merge / top / support-missing behavior accepted after visual settle.
- wrong-owner, jump, reload-ish, and ghost-feel are accepted from this manual run.
- render snap audit at `fa3fc03` classifies the brief post-placement visual snap as `C. CLIENT_PREDICTION_UNMARKED_BLOCKSTATE`: the placed slab blockstate can render before the synced compound-visible client marker arrives, then `SlabAnchorClientSync` rebuilds the candidate/source neighborhood and the model settles to legal `dy=-1.0`.
- tiny fix applied: no. A same-tick temporary client marker would need a prediction/rollback path that does not exist in this slice and could risk false visuals/ghosts; defer as a known minor visual issue unless Julia objects.
- evidence captured under `tmp/beta4-final-manual-visual-78c0f01/`.
- render snap audit evidence captured under `tmp/beta4-render-snap-audit-fa3fc03/`.
- delayed manual trace line caveat: some `delayed_candidate_mismatch` entries with `ghost=true` reflect stale `dy=-0.5` expectations and must not override current `dy=-1.0` COMPOUND_VISIBLE_SLAB_LANE product law.
- with the snap explicitly deferred, release remains blocked pending final release audit.
- no final Bug Blaster yet.

Next action:

- treat `release/0.2.0-beta.4` as rejected and hold as status-only until architecture/product direction or render/placement snap redesign is complete; do not call final release audit in this rejected state.

Row 3 savepoint summary: slab-held side-clicks on a proven compound `dy=-1.0`; automated/focused proof passed and runtime/live-launch logs emitted GREEN with evidence in `tmp/beta4-compound-slab-row3-live-742a839/`; Julia manual live-feel test pending.
Rows 1/2 are no longer release-safe no-lane rejection cases; they are expected
compound below-lane side slab placements into existing `dy=-0.5` lowered slab
grammar. Implementation proof at `save/beta4-compound-below-lane-side-slab-fix`
emits `[JULIA_BETA4_COMPOUND_BELOW_LANE_SIDE_SLAB_GREEN]`: Row 1 lower-half
authors `stone_slab[type=bottom]` at `dy=-0.5`, and Row 2 upper-half authors
`stone_slab[type=top]` at `dy=-0.5`. The artificial Row 3 path remains separate
and remaps only when exactly one adjacent legal `dy=-0.5` slab lane exists in
the intended continuation direction. Focused proof still emits
`[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_GREEN]` with source `0,201,0`
remaining ordinary stone at `dy=-1.0`, legal lane `1,201,0` at `dy=-0.5`, and
candidate `2,201,0` authored as `stone_slab[type=bottom]` at `dy=-0.5`. No
beta4 `dy=-1.0` slab lane is legalized. No final Bug Blaster yet.

Julia screenshot-shape live failure at `fae6d25`: manual live test rejected the current release feel. The internal proof-row name `ROW3` is not Julia's in-world sign label `ROW 3`; the automated Row 3 proof covers a narrow artificial same-Y remap topology, while Julia's screenshot/log shape targets the upper visible full-block area with a lowered slab directly below it. Gated proof `-Dslabbed.beta4LiveScreenshotShapeRed=true` now emits `[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_SLAB_GREEN]` for the side slab at `dy=-0.5`; `[JULIA_BETA4_LIVE_SCREENSHOT_TOP_FACE_GHOST_RED]` remains separate RED/PENDING. Rows 4/5 may still be release-blocking if the screenshot top-face ghost proves the Row 5 top-click gap. Release remains blocked pending Julia live retest and top-face ghost decision.

Screenshot side-shape discriminator audit at `08cb004`: diagnostic-only markers now compare `[JULIA_BETA4_NO_LEGAL_LANE_DISCRIMINATOR]`, `[JULIA_BETA4_INTERNAL_ROW3_DISCRIMINATOR]`, and `[JULIA_BETA4_LIVE_SCREENSHOT_DISCRIMINATOR]`. Current finding: "lowered bottom slab directly below the clicked compound source" is not a safe discriminator because Row 1 and Julia's screenshot side shape both have source `dy=-1.0`, below source `stone_slab[type=bottom]` at `dy=-0.5`, lower/side-band horizontal slab click, no horizontal legal lane, and an immediate side candidate air. Product decision: Rows 1/2 and Julia's screenshot side-shape are intentionally the same legal class now, named compound below-lane side slab placement. The implementation must remap into existing legal `dy=-0.5` lowered slab grammar; no `dy=-1.0` slab lane is legalized. Release remains blocked until implementation plus Julia live retest.

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

Compound matrix closure: Rows 3/5/7/8 are now resolved under A-prime. Row 3 slab-held selection keeps the compound full-block owner; Row 5 upper-half ordinary stone side placement lands in the same compound `dy=-1.0` sidecar lane; Row 7 slab-held upper-half side placement cleanly returns pass with no immediate or after-tick slab mutation; Row 8 top-of-compound ordinary stone placement is authored as `compoundFullBlockAnchor=true` at `dy=-1.0` with no `dy=-1.5` recursion. Matrix proof reports `[BETA4_COMPOUND_CONTRACT_MATRIX_RED] rows=12 red=0 undecided=0 green=11 notImplemented=1`; the marker remains RED only because Row 12 chunk-only reload is NOT_IMPLEMENTED. No beta4 `dy=-1.0` slab lane or `dy<-1.0` lane is legalized. Release remains **blocked** pending Julia live retest and release readiness audit.

Boundary timeout triage: the default `runClientGameTest` timeout did not reproduce on rerun. The earlier failure is archived in `tmp/beta4-boundary-timeout-ecb1c10/` and stopped during world-load/session creation inside `ClientGameTestImpl.waitForWorldLoad` before any `COMPATIBLE_LOWERED_SLAB_LANE_PREDICATE_BOUNDARY` marker emitted; the log showed a stale `session.lock` collision while `CreateWorldScreen` was creating the world. Row 3 remains proven at `ecb1c10`, the timeout does not implicate the compound slab remap, and the live-feel gate can resume once the default suite stays green.

Compound live-failure audit at `26540ff` / `save/beta4-compound-matrix-closure`: Julia's live retest is **live-red despite automation-green matrix closure**. Live recorder evidence from `tmp/beta4-compound-live-fail-audit-26540ff/` shows placement author/reload/outline recorders enabled at `gitHead=26540ff`; lower side placement from `clickedPos=14,-57,0` (`dy=-1.0`, `persistentFullBlockAnchor=true`) finalizes side slots as `placedDy=-0.5`, live slab-held side placement creates bottom/top slabs instead of cleanly rejecting, and the server still emits `Rejecting UseItemOnPacket ... too far away from hit block BlockPos{x=14, y=-57, z=0}` on the lower route. Release remains **blocked**. Next slice is targeted live recorder/proof correction with explicit compound sidecar, hit-validity bridge, finalization, and source-break relation fields; no gameplay fix was made in the audit.

Compound live-path recorder added after `146f368` / `save/beta4-compound-live-fail-audit-26540ff` (recorder-only, no gameplay fix). Default-off `-Dslabbed.beta4CompoundLivePathRecorder=true` works with the existing placement-author recorder and emits `[BETA4_COMPOUND_LIVE_PATH]`, `[BETA4_COMPOUND_HIT_VALIDITY_BRIDGE]`, and `[BETA4_COMPOUND_FINALIZATION_PATH]` with explicit compound sidecar, source/support, final placed dy/source mode, hit-validity accept/reject, and branch fields. Release remains **blocked**. Next step is Julia's live recorder run against the exact failing setup, then log pull into `tmp/beta4-compound-live-path-recorder-<newHEAD>/`.

Compound live-pass audit after `3cef02c` / `save/beta4-compound-live-path-recorder`: Julia live-tested the beta4 compound contract as PASS and reported "Everything worked"; slab placement rejected cleanly with no flash. The slab rejection is intended A-prime beta4 behavior, not a failure: ordinary full-block compound side/top routes may land in the `dy=-1.0` compound lane, while slab-held compound side/half placement must reject cleanly with no ghost slab, no flicker, no `dy=-0.5` slab lane, and no `dy=-1.0` slab lane. Harvested live-path evidence shows stone side placement from `clickedPos=14,-57,0` / `clickedFace=west` finalizing `finalPlacedPos=13,-57,0`, `finalPlacedState=Block{minecraft:stone}`, `finalPlacedDy=-1.0`, and `finalPlacedCompoundFullBlockAnchor=true`; slab-held attempts report `bridgeAccepted=false` / `rejectionReason=held_item_slab`. Matrix baseline remains `rows=12 red=0 undecided=0 green=11 notImplemented=1` at `26540ff`, with Row 12 still NOT_IMPLEMENTED. Release artifact closure now passes the sensitive jar and `jdeps` hard-reference scans after moving proof/recorder/debug links behind release-safe runtime surfaces; the exact broad `lab` regex remains a known false-positive scan pattern because it matches `com/slabbed`. No upload was performed and `release/0.2.0-beta.4` was not moved. Next slice is explicit version/changelog/release tag/upload decision only if Julia authorizes.

Julia live retest after `6d0d525` / `save/beta4-compound-below-lane-side-slab-fix`: no jumping was reported, upper-half side placement works, lower-half side placement flickers/fails, and top-face placement still creates the ghost/skip slab above/on top of the source block. The screenshot side proof is now split into explicit band markers: `[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_UPPER_GREEN]`, `[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_LOWER_RED]`, `[JULIA_BETA4_LIVE_SCREENSHOT_TOP_FACE_GHOST_RED]`, and `[JULIA_BETA4_LIVE_SCREENSHOT_BAND_SPLIT_HARNESS_GREEN]` / `_FAIL`. Release remains **blocked**. Next implementation should target lower-half side placement first, then top-face ghost.

Automated live-shape goblin harness correction at `c956fa3`: Julia invalidated the prior gated `topFace=GREEN` because the fixture/build shape did not prove the required slab/full-block composition. The superseded c956fa3 automation is not release-valid; manual live at c956fa3 remains RED, including top-face placement and missing-under-slab side placement. The corrected gated harness is still opt-in behind `-Dslabbed.beta4LiveShapeGoblin=true`, but `[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_GREEN]` now requires explicit `stone_slab` bottom supports, full stone bridge blocks, explicit top slabs, the upper full block on the named top slab, candidate positions, and the missing-under-slab variant support position. `[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID]` fails the harness when fixture truth is not proven. The summary now reports `fixtureTruth`, `supportPresent.upperSide`, `supportPresent.lowerSide`, `supportPresent.topFace`, `supportMissing.side`, `supportMissing.topFace`, `ghost`, `jump`, `wrongOwner`, and `releaseBlockers`. Release remains blocked; do not call the goblin top-face issue fixed or release-ready from the superseded c956fa3 green.

Support-missing visible-owner side placement: the corrected goblin proof now accepts side placement from the still-visible upper full block after its direct top-slab support is removed. `SlabSupport.findLegalCompoundSlabRemap(...)` names the legal class `COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB`: source must still be an anchored ordinary full-block compound owner at `dy=-1.0`, the held item must be a slab, the face must be horizontal, and the result is the existing legal side slab grammar at `dy=-0.5`. Corrected goblin summary: `structure=GREEN fixtureTruth=GREEN supportPresent.upperSide=GREEN supportPresent.lowerSide=GREEN supportPresent.topFace=RED supportMissing.side=GREEN supportMissing.topFace=RED hitbox=RED ghost=false jump=false wrongOwner=true releaseBlockers=topFace,supportMissingTopFace`. No `dy=-1.0` slab lane, `dy<-1.0`, or `dy=0` lowered-interaction slab is legalized. Release remains blocked on topFace/supportMissingTopFace unless Julia explicitly defers those blockers.

Compound visible-owner top slab placement at `278513b` is now superseded by the `d7ef534` manual delayed trace. The old legal result was candidate `source.up()`, `stone_slab[type=bottom]`, `dy=0.0`, but Julia's manual trace proved that result is visually floating/invalid for a compound source spanning Y-1.0 to Y. Treat the old goblin `releaseBlockers=none` as historical false-green evidence only.

Julia manual live RED after `278513b` / `save/beta4-compound-visible-owner-top-slab-dy-fix`: the prior goblin `releaseBlockers=none` was false-green because it did not assert exact candidate position, exact slab half/type, repeat placement after the first side slab, or exact top-face result tightly enough. Manual live shows lower-half aiming produces the upper/top result, upper-half accepts only the first slab, repeat placement rejects, and top-face placement still creates a floating/ghost/wrong result. Release remains **blocked**. The proof-only harness now has to report `lowerExact`, `upperFirst`, `repeatPlacement`, `topFaceExact`, `supportPresent.side`, `supportMissing.side`, `supportMissing.topFace`, `ghost`, `jump`, `wrongOwner`, and non-empty `releaseBlockers` for this live RED. New blockers are lower-half exact result, repeat placement after first side slab, and top-face exact result/ghost.

Goblin live-parity reset: the dirty exact-candidate WIP proved the old goblin still used synthetic `BlockHitResult` clicks and isolated fresh fixtures, so its lower/top GREEN markers are not release proof. The gated `-Dslabbed.beta4LiveShapeGoblin=true` route must now prove real `MinecraftClient.crosshairTarget` parity, sequence-state behavior without rebuilding between clicks, and a bounded changed-block delta scan after every click. Synthetic candidate success is diagnostic only. Manual live after `278513b` remains RED and release remains **blocked** until the real-crosshair sequence either matches Julia or explains the exact target/face/localY/delta mismatch.

Beta4 live goblin targeting parity at `5db72f9`: the harness now emits `[JULIA_BETA4_LIVE_GOBLIN_TARGETING_*]` diagnostics for intended owner, dy-adjusted visible local hit, camera eye/yaw/pitch/look vector, real crosshair target, raw/visual local coordinates, and classification. The first diagnostic pass proved harness camera aim was bad (wrong side eye plus unpinned camera pose); the gametest-only harness fix corrected the aim without gameplay changes. Latest targeted run: upper side `TARGET_OK`, top face `TARGET_OK`, sequence first `TARGET_OK`, sequence top `TARGET_OK`; lower-after-first is `OCCLUSION_EXPECTED` because the first placed side slab physically intersects before the intended lower-side point, so that sequence aim point is invalid and needs a different player-realistic angle. `harnessAimFailures=0`, `ownerFailures=0`, `occlusionCases=2`, `nextAction=choosePlayerRealisticAimPoint`; manual live remains RED and release remains **blocked**.

Beta4 live goblin player aim corridor scout: the harness now scans named player-realistic lower-half camera lanes before clicking. The unmutated lower route finds `same_side_straight` (`eye=(37.9,203.2,8.5)`, intended hit `(40.0,202.25,8.5)`); after the first upper side slab, the sequence route finds `same_side_low` (`eye=(37.9,202.7,8.5)`) with real crosshair `TARGET_OK` on the upper full block west face, visual local Y `0.250...`. With that corridor, lower-after-first no longer stops as occlusion: it clicks the real target and still goes RED, changing unexpected `38,203,8` instead of expected `39,203,8`. Repeat placement and top face remain RED, `ghost=true`, `wrongDelta=true`, and release remains **blocked**. Latest summary: `[JULIA_BETA4_LIVE_GOBLIN_CORRIDOR_SUMMARY] lowerCorridor=FOUND lowerAfterFirstCorridor=FOUND sequenceLowerResult=RED repeatPlacement=RED topFace=RED ghost=true wrongDelta=true releaseBlockers=lowerAfterFirst,repeatPlacement,topFace`.

Repeat slab merge server seam audit at `371fa59`: diagnostic-only markers behind `-Dslabbed.beta4RepeatMergeTrace=true` now prove the repeat click targets `39,203,8` as `stone_slab[type=bottom] dy=-0.5` on face `UP`. The client predicts `stone_slab[type=double] dy=-0.5` and reports `Success`, but the server tolerance marker classifies the same packet as `legalLoweredSameCellMerge=true` while leaving the packet center unchanged; no server placement context or durable server `DOUBLE` follows. Server/client ticks 1, 5, and 20 remain `stone_slab[type=bottom] dy=-0.5`. Seam classification: `SERVER_TOLERANCE_REJECT`. Latest summary: `[JULIA_BETA4_REPEAT_SEAM_SUMMARY] clientPredict=DOUBLE serverTolerance=LEGAL_CANDIDATE_NOT_REWRITTEN placementContext=CLIENT_ONLY_FACE_NOT_HORIZONTAL setBlockState=NO ... seam=SERVER_TOLERANCE_REJECT`. The current live-goblin corridor summary is `sequenceLowerResult=GREEN repeatPlacement=RED topFace=RED`; release remains **blocked**. No gameplay implementation was added.

Repeat lowered same-cell slab merge fixed at `fae943b` follow-up is historical under the old lane model: `LOWERED_SAME_CELL_SLAB_MERGE` at `dy=-0.5` remains valid for the lowered slab lane, but it no longer proves Julia's compound visible slab lane expectation. Corrected compound-visible repeat proof must merge lower/upper side results into `stone_slab[type=double] dy=-1.0` while remaining source-owned and bounded. Release remains **blocked**.

Compound visible-owner top-face corridor fixed after the lowered same-cell repeat merge is also superseded for release confidence. It proved a real-crosshair path to the old `stone_slab[type=bottom] dy=0.0` result, which the `d7ef534` manual delayed trace now classifies as visually invalid. Corrected owner-top proof must land `source.up()` as `stone_slab[type=bottom] dy=-1.0` through the bounded compound visible slab lane. Release remains **blocked**.

Manual-live parity capture after `b92887b` / `save/beta4-compound-visible-owner-topface-fix`: automation says `sequenceLowerResult=GREEN`, `repeatPlacement=GREEN`, `topFace=GREEN`, and `releaseBlockers=none`, but Julia's real runClient remains RED: lower side rejected, upper side accepted, repeat/merge rejected, top face ghost/wrong, and visible flicker/pop. Manual live wins. Diagnostics-only trace mode `-Dslabbed.beta4ManualLiveTrace=true` now captures Julia's actual right-click path with markers `[JULIA_BETA4_MANUAL_LIVE_CLICK_START]`, `[JULIA_BETA4_MANUAL_LIVE_TARGET]`, `[JULIA_BETA4_MANUAL_LIVE_PLACEMENT_INTENT]`, `[JULIA_BETA4_MANUAL_LIVE_SERVER_TOLERANCE]`, `[JULIA_BETA4_MANUAL_LIVE_SLAB_SUPPORT_DECISION]`, `[JULIA_BETA4_MANUAL_LIVE_DELTA]`, `[JULIA_BETA4_MANUAL_LIVE_FINAL]`, and `[JULIA_BETA4_MANUAL_LIVE_SUMMARY]`. Julia command: `JAVA_TOOL_OPTIONS="-Dslabbed.inspect=true -Dslabbed.target.trace=true -Dslabbed.beta4ManualLiveTrace=true" ./gradlew --no-daemon runClient --console plain`. Next action after this savepoint: Julia runs manual live with that flag, captures the real click target/face/hit/candidate/delta path, and release stays blocked until those logs are interpreted and the manual RED is resolved or explicitly waived.

Manual-live delayed final trace after `d7ef534` / `save/beta4-manual-live-delayed-final-trace`: Julia's manual run proves the current Beta4 slab law is insufficient. Lower/upper side clicks on the compound `dy=-1.0` full block author side slabs as `stone_slab[type=bottom] dy=-0.5`, which visually represents only the upper half of the visible source. The top-face path using `source.up()` as `stone_slab[type=bottom] dy=0.0` is visually floating above the `dy=-1.0` source. Repeat behavior and wrongDelta are symptoms of the same wrong lane model. Corrected product direction is the bounded, source-owned `COMPOUND_VISIBLE_SLAB_LANE`: lower side `BOTTOM dy=-1.0`, upper side `TOP dy=-1.0`, side merge `DOUBLE dy=-1.0`, and owner top `BOTTOM dy=-1.0`. Arbitrary or recursive `dy=-1.0` slab chains remain illegal. Release remains **blocked**.

Next slice: add RED proofs for `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_LOWER_RED]`, `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_UPPER_RED]`, `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MERGE_RED]`, `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TOP_RED]`, `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SUPPORT_MISSING_RED]`, `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRIAD_RED]`, and `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_RED]`. Do not implement the lane until that RED proof matrix exists.

Compound visible slab lane RED proof matrix is now executable behind `-Dslabbed.beta4CompoundVisibleSlabLaneRed=true`. It first emits `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_FIXTURE_GREEN]` for the canonical bottom-slab / bridge-full-block / top-slab / compound upper-full-block fixture, then emits the LOWER, UPPER, MERGE, TOP, SUPPORT_MISSING, TRIAD, RELOAD, and SUMMARY markers against the corrected source-owned `dy=-1.0` lane expectations. Current implementation is expected RED because old `dy=-0.5` side slabs and `dy=0.0` top slabs are not legal green results for this product law. No gameplay implementation, support-law, placement-mixin, server-tolerance, or retarget/rescue behavior changes are part of this proof slice. Release remains **blocked**; next slice is implement the first named state only after the RED proof exists.

Compound visible side lower slab implementation: the first named state `COMPOUND_VISIBLE_SIDE_LOWER_SLAB` is now implemented through the persisted/synced `COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE` sidecar. `SlabSupport.findLegalCompoundSlabRemap(...)` names only lower-band horizontal hits on an authored/persistent compound full-block owner at `dy=-1.0`; `BlockItemPlacementIntentMixin` finalization marks only the immediate side candidate when the final state is `stone_slab[type=bottom]`; `SlabSupport.getYOffsetInner(...)` returns `dy=-1.0` only for that marked slab before the old `dy=-0.5` lane rules. Gated proof now reports `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_LOWER_GREEN]` with server/client candidate `stone_slab[type=bottom] dy=-1.0`, source still `compoundFullBlockAnchor=true dy=-1.0`, and no `dy<-1.0`; summary remains `fixtureTruth=GREEN lower=GREEN upper=RED merge=RED top=RED supportMissing=RED triad=RED reload=RED releaseBlockers=compoundVisibleSlabLane`. The `dy=-1.0` slab lane remains bounded/source-owned only; release remains **blocked**.

Compound visible side upper slab implementation: the second named state `COMPOUND_VISIBLE_SIDE_UPPER_SLAB` is now implemented through the persisted/synced `COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE` sidecar. It mirrors lower narrowly: `SlabSupport.findLegalCompoundSlabRemap(...)` names only upper-band horizontal hits on an authored/persistent compound full-block owner at `dy=-1.0`; `BlockItemPlacementIntentMixin` finalization marks only the immediate side candidate when the final state is `stone_slab[type=top]`; `SlabSupport.getYOffsetInner(...)` returns `dy=-1.0` only for the marked upper/lower visible side slab states before the old `dy=-0.5` lane rules. Gated proof reports `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_UPPER_GREEN]` with server/client candidate `stone_slab[type=top] dy=-1.0`, source still `compoundFullBlockAnchor=true dy=-1.0`, lower remains GREEN, and no `dy<-1.0`; summary is `fixtureTruth=GREEN lower=GREEN upper=GREEN merge=RED top=RED supportMissing=RED triad=RED reload=RED releaseBlockers=compoundVisibleSlabLane`. The `dy=-1.0` slab lane remains bounded/source-owned only; release remains **blocked**.

Compound visible side double slab implementation: the third named state `COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB` is now implemented through the persisted/synced `COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE` sidecar. It is only reachable when the same immediate side candidate is already a marked `COMPOUND_VISIBLE_SIDE_LOWER_SLAB` or `COMPOUND_VISIBLE_SIDE_UPPER_SLAB` beside the same authored/persistent compound full-block owner at `dy=-1.0`; compatible repeat side placement merges that candidate to `stone_slab[type=double]` and replaces the lower/upper marker with the double marker. `SlabSupport.getYOffsetInner(...)` returns `dy=-1.0` for the marked double before the legacy `dy=-0.5` lane rules. Gated proof reports `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MERGE_GREEN]` with server/client candidate `stone_slab[type=double] dy=-1.0`, clicked source still `compoundFullBlockAnchor=true dy=-1.0`, and no `dy<-1.0`; summary is `fixtureTruth=GREEN lower=GREEN upper=GREEN merge=GREEN top=RED supportMissing=RED triad=RED reload=RED releaseBlockers=compoundVisibleSlabLane`. Old Row 3 remains GREEN as `COMPOUND_HORIZONTAL_CONTINUATION_LANE` at `dy=-0.5`, and the old lowered same-cell repeat merge remains GREEN at `dy=-0.5`. The `dy=-1.0` slab lane remains bounded/source-owned only; release remains **blocked**.

Compound visible owner top slab implementation: the fourth named state `COMPOUND_VISIBLE_OWNER_TOP_SLAB` is now implemented through the persisted/synced `COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE` sidecar. A held slab on the UP face of an authored/persistent compound full-block owner at `dy=-1.0` captures the pre-placement intent, lets vanilla place exactly `source.up()`, then finalization marks only that bottom slab when the source remains the compound owner. `SlabSupport.getYOffsetInner(...)` returns `dy=-1.0` for the marked owner-top slab before legacy `dy=-0.5` carrier rules, so the old `dy=0.0` floating top result is not accepted. Gated proof reports `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TOP_GREEN]` with server/client candidate `stone_slab[type=bottom] dy=-1.0`, `compoundVisibleOwnerTopSlab=true`, clicked source still `compoundFullBlockAnchor=true dy=-1.0`, and no `dy<-1.0`; summary is `fixtureTruth=GREEN lower=GREEN upper=GREEN merge=GREEN top=GREEN supportMissing=GREEN triad=PENDING reload=PENDING releaseBlockers=compoundVisibleSlabLane`. Support-missing turned GREEN naturally through the same bounded source-owned marker; triad/reload remain pending and release remains **blocked**.

Compound visible slab lane triad targeting fix: the named-state ownership rule now retargets only the four `COMPOUND_VISIBLE_SLAB_LANE` slab results (`COMPOUND_VISIBLE_SIDE_LOWER_SLAB`, `COMPOUND_VISIBLE_SIDE_UPPER_SLAB`, `COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB`, and `COMPOUND_VISIBLE_OWNER_TOP_SLAB`) when the ray intersects their shifted visible outline body at `dy=-1.0`. Raw vanilla `ShapeType.OUTLINE` still reports the historical neighboring/support cells for lower/upper/merge, but the bounded owner target now resolves to the named slab body for lower, upper, merge, and top. No generic `dy=-1.0` slab rescue was added, and old `dy=-0.5` lane proofs remain GREEN.
Compound visible slab lane reload proof: the opt-in visible-lane gametest now emits `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_START]`, per-state `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_BEFORE]`, per-state `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_AFTER]`, and `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_GREEN]` using `TestWorldSave.open()`. Lower, upper, double, and owner-top markers all survive reload; final states stay `stone_slab[type=bottom/top/double/bottom]`, all dy stay `-1.0`, source remains `stone`, `sourceBeforeAnchor=true`, `sourceAfterAnchor=true`, and no checked state collapses to `dy=0.0` or `dy=-0.5`.
Compound visible slab lane model dy bridge: Julia's manual visual retest after `d39928d` proved the remaining failure was render-model vertical placement, not placement, outline/hitbox, reload, wrong-owner, jump/source-break, or ghost behavior. The failure layer was the model render view: `OffsetBlockStateModel.emitQuads(...)` calls the shared `SlabSupport.getYOffset(...)`, but chunk render paths may pass a non-`World` render view, and the four `COMPOUND_VISIBLE_SLAB_LANE` marker predicates did not have the client-world fallback bridge that existing anchor/lowered-carrier model paths already used. `SlabAnchorAttachment` and `SlabAnchorClientSync` now bridge only `COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE`, `COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE`, `COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE`, and `COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE` for non-`World` model views and rerender scheduling. The opt-in proof now emits `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MODEL_AUTHORITY_GREEN]` with lower, upper, double, and top `modelDy=-1.0` through a render-region-style `BlockView`. Latest summary is expected as `fixtureTruth=GREEN lower=GREEN upper=GREEN merge=GREEN top=GREEN supportMissing=GREEN triad=PARTIAL modelAuthority=GREEN reload=GREEN releaseBlockers=JuliaLiveRetest`. This is model authority GREEN, not a full manual visual release claim; release remains **blocked** until Julia manually confirms visual model alignment.

Compound visible slab lane immediate render refresh: manual live after `e5492d0` / `save/beta4-compound-visible-lane-model-dy-fix` is still RED because the placed slab can initially render too high while outline/marker truth is already lower, then support replacement forces the model to catch up. Failure layer classification is **D. RERENDER_NOT_SCHEDULED**: marker truth, client marker visibility, non-`World` render-view bridge, and model dy authority all exist, but the synced attachment refresh only used same-state block rerender and could leave the chunk model stale. `SlabAnchorClientSync` now forces a one-block-neighborhood `scheduleBlockRenders(...)` refresh for the four compound-visible marker attachment changes, and gated `-Dslabbed.beta4CompoundVisibleRenderTrace=true` markers report marker set, client sync, model dy, rerender, support update, and summary. Focused proof emits `[JULIA_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_SUMMARY] classification=RERENDER_NOT_SCHEDULED ... clientMarker=true modelDy=-1.0 candidateRerenderScheduled=true neighborRerenderScheduled=true` plus `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MODEL_AUTHORITY_GREEN]`. No placement law, legal `dy=-1.0` state definition, or retarget/rescue boundary changed. Diagnostic conflict classification is **A. STALE_DIAGNOSTIC_EXPECTATION**: the old live-shape `lowerAfterFirst`/`repeatPlacement` sequence expected `dy=-0.5` lowered-same-cell results and is now superseded by the `COMPOUND_VISIBLE_SLAB_LANE` proof at `dy=-1.0`; `[JULIA_BETA4_LIVE_GOBLIN_LEGACY_SEQUENCE_STALE]` keeps that history visible while release blockers defer to `JuliaLiveRetest`. Release remains **blocked** pending Julia manual visual retest.

Compound visible slab lane render snap audit: manual live after `78c0f01` / `fa3fc03` accepted lower, upper, double merge, top, support-missing, wrong-owner, jump, reload-ish, and ghost-feel after visual settle, with one known brief snap immediately after placement. Current classification is **C. CLIENT_PREDICTION_UNMARKED_BLOCKSTATE**. The four compound-visible marker writers remain server-truth only, so the client can briefly display the predicted slab blockstate without a compound-visible marker; once attachment sync arrives, `SlabAnchorClientSync` schedules the candidate/source neighborhood and the model path reports `clientMarker=true` and `modelDy=-1.0`. No tiny fix was applied because an immediate temporary client marker would require a new prediction/rollback path and could create false transient visuals. Known minor snap is explicitly deferred unless Julia objects; release remains **blocked** pending final release audit, and no final Bug Blaster has been run.

Visible upper vs old Row 1 compatibility audit: old beta4 compound merge Row 1 clicked `FULL_POS` (`stone`, authored/persistent compound full-block owner, `dy=-1.0`) on horizontal `EAST` with hit Y `sourceY - 0.25`. Under old `dy=-0.5` row naming this was called lower-half, but the corrected visible-body math is `visibleLocalY = 200.75 - (201 + -1.0) = 0.75`, so it is an upper-band side hit on the compound owner. Decision A: old Row 1's `stone_slab[type=bottom] dy=-0.5` expectation is superseded by `COMPOUND_VISIBLE_SIDE_UPPER_SLAB`, `stone_slab[type=top] dy=-1.0`; the proof now emits `[JULIA_BETA4_COMPOUND_OLD_ROW_VISIBLE_UPPER_SUPERSEDED_GREEN]` for that row. Row 3 remains the distinct continuation case: when an existing horizontal legal `dy=-0.5` lane is present in the clicked direction (`legalLaneCount=1`), `SlabSupport.findLegalCompoundSlabRemap(...)` gives `COMPOUND_HORIZONTAL_CONTINUATION_LANE` priority before compound visible side classification. Proof now emits `[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_GREEN]` with the Row 3 candidate at `2,201,0` finalized as `stone_slab[type=bottom] dy=-0.5`. This does not reclassify arbitrary below-lane rows, does not legalize ownerless `dy=-1.0` slabs, and release remains **blocked**.

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

Title: Beta 3.5 Floor Torch Lowered Bottom Slab Source Truth
Invariant: A floor torch on slab-supported geometry must place through the player path, survive, and visually contact the live support surface with model, outline, and raycast co-located.
Root cause: Early proofs for floor torch control fixtures were false-green and did not cover Julia’s live SBSBS-style slab-supported structure. The live failure split into two contact layers: lowered bottom slab placement needing `torchDy=-1.5` and plain bottom support needing `torchDy=-1.0` to close contact.
Fix: Added narrow floor-torch-only source-truth support handling in `SlabSupport`, separated placement-vs-contact tracing in the live dual tracer, and treated duplicate occupied torch clicks as `OCCUPIED_TORCH_TARGET` instead of real placement failures.
Proof savepoints:
- `66ca74a` / `save/beta35-floor-torch-lowered-slab-placement`: lowered bottom-slab placement GREEN.
- `226cc6c` / `save/beta35-floor-torch-plain-bottom-contact`: plain bottom support contact GREEN, `supportDy=-0.5`, `torchDy=-1.0`, `contactGap=0.000000`.
- `a9c2882` / `save/beta35-floor-torch-live-acceptance`: Julia live acceptance at 8/8 `PLACEMENT_ATTEMPT_OK`, `PLACEMENT_REJECTED=0`, `PLACED_CONTACT_GREEN=1407`, `PLACED_CONTACT_GAP=0`, max concrete floor-torch contactGap=0.000000.
Status: Fixed for `floor_torch_only`; `wall_torch`, `lantern`, `signs`, `chains` remain `NOT_COVERED`; tag moved: no.

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

After the classifier savepoint, the next required action is Julia and architecture/product direction on render/placement snap redesign. `release/0.2.0-beta.4` remains not publishable while blocked; do not upload beta4 and do not call it release-ready.

Current Beta 3.5 floor-torch slice state (2026-05-10): add `-Dslabbed.beta35LiveTorchCapture=true` gated live recorder in `src/client/java/com/slabbed/mixin/client/Beta35LiveTorchCaptureMixin.java` and `src/main/java/com/slabbed/util/Beta35LiveTorchCaptureRecorder.java` as evidence-only capture. Wall torch, lantern, signs, and chains remain explicitly `NOT_COVERED`.

Current v2 floor-torch contact-gap proof slice (2026-05-11): add
`-Dslabbed.beta35FloorTorchV2ContactGapRed=true` in
`SlabbedLabLoweredSidePlacementLiveReproClientGameTest` and use
`docs/beta35-floor-torch-v2-contact-gap-red.md`.
Required markers:
`[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_MEASURED]`,
`[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_RED]`,
`[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_SUMMARY]`.

Target blocker shifted from the v1 recorder artifact to corrected live source-truth:
top/bottom support fixtures should reproduce
`supportDy=-1.000`, `torchDy=-0.500 or -1.000`, and `contactGap=0.500000`
under `fixtureMatchesV2LiveStack=true`.
If both attempts fail to match source truth, this slice should classify
`failureLayer=V2_SOURCE_TRUTH_MISMATCH`.
Wall torch remains separate `NOT_COVERED`; Beta 3.5 release remains blocked.
No gameplay fix and no release tag movement in this slice.

Beta 3.5 floor_torch_only live acceptance status (2026-05-11):

- Julia live trace at `226cc6c` (`save/beta35-floor-torch-plain-bottom-contact`) is accepted for the `floor_torch_only` scope.
- Dual live tracer report: tracer enabled, 8/8 placement attempts `PLACEMENT_ATTEMPT_OK` with zero
  `PLACEMENT_REJECTED`, zero `COMFORT_NO_BOX_INTERSECTION`, and zero `WRONG_TARGET_OWNER`.
- dual floor-torch contact probe reports `PLACED_CONTACT_GREEN=1407`, `PLACED_CONTACT_GAP=0`,
  `max concrete floor-torch contactGap=0.000000`.
- floor torch placements are green for lowered bottom slab, top slab, double slab, stone, and plain bottom support:
  - `COMPOUND_VISIBLE_SIDE_LOWER_SLAB` support with `supportDy=-1.0` → GREEN
  - `COMPOUND_VISIBLE_SIDE_UPPER_SLAB` support with `supportDy=-0.500` supportDy path GREEN
  - `PLAIN_STATE` plain bottom support with `supportDy=-0.500` and `contactGap=0.000000`.
  - `supportDy=-0.500` plain bottom contact is live GREEN; `supportDy=-1.0` lowered bottom slab support is green.
  - `OCCUPIED_TORCH_TARGET` / duplicate-occupied noise is present but not treated as release-blocking.
  - old `JULIA_BETA35_LIVE_TORCH_CAPTURE` rows for `minecraft:wall_torch` air-support contact-gap remain outside this `floor_torch_only` acceptance slice and do not block this savepoint.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
- Bug Blaster `Beta 3.5 Floor Torch Lowered Bottom Slab Source Truth` is finalized.
- Release prep may proceed to a release-readiness audit from this savepoint; this slice moved no release tag.
- Next action: keep this slice docs-only; do not start the release-readiness audit in this slice.
- Next safe action: run the Beta 3.5 release-readiness audit with `floor_torch_only` scope and no additional gameplay edits in this slice.

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

## Beta 3.5 salvage audit status
- Beta4 compound visible slab lane release candidate is rejected and remains disqualified.
- `docs/beta35-salvage-audit.md` created with candidate `INCLUDE/EXCLUDE/NEEDS PROOF` classification for a `0.2.0-beta.3.5` stability salvage path.
- Current goal is a smaller release excluding `COMPOUND_VISIBLE_SLAB_LANE` and `dy=-1` visible-lane architecture.
- Beta 3.5 object/slab ownership status: `c96e674` / `save/beta35-object-slab-ownership-fix` was partial and false-green under Julia's screenshot evidence because it proved only simple owner routing. Target law A is now selected for the screenshot-faithful floor-torch case: the object inherits the existing support-derived dy for model, outline, and raycast, while the lowered floor-torch outline/raycast basis matches the visible torch post body instead of a taller comfort proxy. Gated `-Dslabbed.beta35ObjectSlabOwnershipRed=true` now proves torch target `GREEN`, slab target `GREEN`, survival `GREEN`, model/outline `GREEN`, raycast `GREEN`, with no rejected `COMPOUND_VISIBLE_SLAB_LANE` revival and no arbitrary broad `dy=-1` torch/object behavior. Expected markers are `[JULIA_BETA35_OBJECT_SLAB_TRIAD_OWNER_ROUTE_GREEN]`, `[JULIA_BETA35_OBJECT_SLAB_TRIAD_MODEL_OUTLINE_GREEN]`, `[JULIA_BETA35_OBJECT_SLAB_TRIAD_RAYCAST_GREEN]`, `[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SUMMARY]`, and `[JULIA_BETA35_OBJECT_SLAB_TRIAD_SUMMARY]`; failure layer is `NONE`. Beta 3.5 include status is `INCLUDE` for this narrow object/slab triad fix after proof.

- Beta 3.5 object/slab triad dry-run stopped on first cherry-pick conflicts for `c96e674` into old `release/0.2.0-beta.2` baseline; failure layer is **beta35 inclusion mismatch**, not a functional code bug. Integration branch proof at HEAD `9c90c23` / `save/beta35-object-triad-dryrun` is fully GREEN: all triad markers (`TORCH_TARGET_GREEN`, `SLAB_TARGET_GREEN`, `MODEL_OUTLINE_GREEN`, `RAYCAST_GREEN`, `SURVIVAL_GREEN`, `screenshotFaithfulTriad=GREEN`, `failureLayer=NONE`, `beta35IncludeStatus=INCLUDE`). Compile: `BUILD SUCCESSFUL`. Focused proof: `BUILD SUCCESSFUL in 31s`. dy values: objectDy=-1.000, slabDy=-0.500, supportDy=0.000; model/outline/raycast bounds identical. Evidence: `tmp/support-anchor-object-triad-audit-9c90c23/`. Next slice: resolve cherry-pick conflicts manually or create a fresh beta3.5 branch that includes prerequisite commits before applying c96e674+6cb28bb+274b286. No release tag moved; no gameplay fix implemented.

- Beta 3.5 object triad is **triad-include-ready** on the integration lineage (`save/beta35-object-triad-classification`, `failureLayer=NONE`, `beta35IncludeStatus=INCLUDE`); old beta2 cherry-pick path (c96e674 onto d1417ff) is invalid due to diverged context, not a code defect; no object-triad gameplay work remains. Inclusion strategy: `docs/beta35-inclusion-strategy.md`.

- **Beta 3.5 release prep is PAUSED.** Julia's manual live test (MC 1.21.11) at HEAD `4f63abe` / `save/beta35-object-triad-inclusion-strategy` showed that the object-triad proof did not cover player-facing floor torch placement onto slab-supported geometry. The object-triad proof (`beta35IncludeStatus=INCLUDE`) proved owner-route targeting and model/outline/raycast co-location for a pre-placed fixture only (`proofScope=OWNER_ROUTE_ONLY_SIMPLE_ROUTING`, `screenshotFaithfulTriad=NOT_PROVEN`). It did **not** prove player-initiated floor torch placement or survival through real neighbor-update pulses.

- Beta 3.5 floor torch player-placement RED/proof-gap was added at HEAD `4f63abe`, gated by `-Dslabbed.beta35LiveItemAnchoringRed=true`. Markers: `[JULIA_BETA35_LIVE_ITEM_ANCHORING_FIXTURE_GREEN]`, `[JULIA_BETA35_LIVE_ITEM_ANCHORING_PLACEMENT_GREEN]`, `[JULIA_BETA35_LIVE_ITEM_ANCHORING_SURVIVAL_GREEN]`, `[JULIA_BETA35_LIVE_ITEM_ANCHORING_SUMMARY]`, `[JULIA_BETA35_LIVE_ITEM_ANCHORING_RED]`. Controlled fixture passed (`canPlaceAt=true`, `torchDy=-1.000`, survival=GREEN), but `juliaLiveResult=RED failureLayer=PROOF_GAP` because the player item-use path was not covered. This historical note is superseded by the floor-torch-only GREEN proof below. Evidence: `tmp/beta35-live-item-anchoring-red-4f63abe/`. See `docs/beta35-live-item-anchoring-red.md`.

- Beta 3.5 floor torch player-like placement proof is GREEN at `0f08624` / `save/beta35-floor-torch-player-placement`, but Julia's manual live visual verdict after that savepoint is NOT ACCEPTED ("I'm not seeing how this is better"). The blocker remains floor torch visual/support acceptance, not placement permission. Controlled fixture gate `-Dslabbed.beta35FloorTorchVisualContactRed=true` still measures `supportVisibleTopY=201.000000`, `torchModelBottomY=201.000000`, `contactGap=0.000000`, `torchDy=-1.000`, `supportDy=-0.500`, `triad=GREEN`, with `visualContactProofStatus=PENDING` and `failureLayer=FIXTURE_MISMATCH`.

- Live-shape fixture parity gate `-Dslabbed.beta35FloorTorchLiveShapeRed=true` is now added to mirror Julia's screenshot-style multi-level slab/full structure using player-like floor torch placement. Current measured parity result: `expectedTorchPos=67,202,0`, `actualTorchPos=67,202,0`, `supportPos=67,201,0`, `supportState=stone_slab[type=bottom]`, `supportDy=-0.500`, `supportVisibleTopY=201.000000`, `torchDy=-1.000`, `torchModelBottomY=201.000000`, `contactGap=0.000000`, `triad=GREEN`, `liveShapeProofStatus=GREEN`, `failureLayer=NONE`. This closer fixture still does not reproduce Julia's manual complaint directly; next safe path is tighter live coordinate/face/owner capture. `wall_torch`, `lantern`, `signs`, and `chains` remain NOT_COVERED and are not the current blocker. Beta 3.5 release prep remains PAUSED; do not claim `floor_torch_only` release scope accepted. No release tag moved and no production gameplay fix was implemented.

- **Beta 3.5 floor-torch support source-truth audit** at HEAD `9ac16f2` / `save/beta35-floor-torch-source-truth-mismatch` → new savepoint `save/beta35-floor-torch-support-source-truth-audit`. The parity proof (`-Dslabbed.beta35LiveFloorTorchSourceTruthParity=true`) reported `fixtureMatchesLiveDyStack=false` and `failureLayer=LIVE_FLOOR_TORCH_WRONG_SUPPORT_OWNER` because the fixture slab resolved as `supportDy=0.000` while the live slab resolved as `supportDy=-0.500`. **Root cause (docs-only, no production fix):** `supportDy=-0.500` for a `stone_slab[type=bottom]` requires `SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive=true`, which is satisfied by either (a) the `LOWERED_SLAB_CARRIER_TYPE` chunk attachment containing that position (written at real in-game placement time when surrounding context qualified), or (b) structural fallbacks: an anchored / `hasBottomSlabBelow`-backed ordinary full block at `pos.down()`, or adjacent lowered bridge support. The fixture calls `updatePersistentLoweredSlabCarrier` in isolation (slab only, no surrounding blocks), so `qualifiesForPersistentLoweredSlabCarrier=false`, no mark is written, the client chunk attachment is empty, structural fallbacks also fail → `supportDy=0.000`. In the live world the mark was written during Julia's actual gameplay session and persists across saves (`.persistent(SET_CODEC)`, synced via `AttachmentSyncPredicate.all()`). There is **no client/server dy split**: both live capture and replay measure from `mc.world` (ClientWorld), which reads the synced chunk attachment directly via `WorldChunk.getAttached(LOWERED_SLAB_CARRIER_TYPE)`. **Missing source truth**: the fixture omits the anchored (or `hasBottomSlabBelow`-backed) full block at `supportCandidatePos.down()` that would satisfy `qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive`. Diagnostic markers added to the parity proof: `[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_AUDIT]`, `[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_MISSING]`, `[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_SUMMARY]`. **Next slice**: better RED fixture that places an anchored full block at `supportCandidatePos.down()` so `fixtureMatchesLiveDyStack=true`. No production gameplay fix implemented. No release tag moved. See `docs/beta35-floor-torch-support-source-truth-audit.md`.

- **Beta 3.5 floor-torch live dy stack parity** at HEAD `9984cf5` / `save/beta35-floor-torch-support-source-truth-audit` → new savepoint `save/beta35-floor-torch-live-dy-stack-parity`. Fixture now reproduces the live dy stack (`fixtureMatchesLiveDyStack=true`, `supportDy=-0.500`, `torchDy=-1.000`) using **Option B — bottom-slab-backed anchored full block**: `stone_slab[type=bottom]` at `supportCandidatePos.down().down()` makes `hasBottomSlabBelow(world, anchoredFullBlockPos)` true; `Blocks.STONE` at `supportCandidatePos.down()` + `addAnchor(...)` writes the `ANCHOR_TYPE` mark; `updatePersistentLoweredSlabCarrier(world, supportCandidatePos, state)` now succeeds via `qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive` (anchored full block below) → `carrierMarkWritten=true` → `supportDy=-0.500`. **Outcome B** (from task spec): `contactGap=0.000000` rather than live `-1.500000` — `LIVE_DY_STACK_MATCH_NO_GAP`. Discrepancy: live capture implies `getSupportYOffset=1.0` and torch outline relative minY=-2.0; fixture measures `getSupportYOffset=0.5` and torch outline relative minY=-1.0. This 0.5-block `getSupportYOffset` and 1.0-block torch-outline discrepancy is classified as a **recorder/contact-math audit** item, not a production gameplay fix. `failureLayer=LIVE_DY_STACK_MATCH_NO_GAP`. Compile: `BUILD SUCCESSFUL`. Parity proof: `BUILD SUCCESSFUL`. Contact-gap proof: `BUILD SUCCESSFUL`. Default suite: `BUILD SUCCESSFUL in 1m 45s`. No production gameplay fix implemented. No release tag moved. `wall_torch=NOT_COVERED`. See `docs/beta35-floor-torch-live-dy-stack-parity.md`.

- **Beta 3.5 live torch recorder contact math audit** at HEAD `220ecca` / `save/beta35-floor-torch-live-dy-stack-parity` → new savepoint `save/beta35-live-torch-recorder-contact-math-audit`. Two formula bugs in `Beta35LiveTorchCaptureRecorder` were identified and corrected (recorder-only, no production code changed). **Bug 1 — `supportVisibleTopY`**: recorder used hardcoded `+1.0d` (full-block height) instead of `getSupportYOffset(state)=0.5` for a `stone_slab[type=bottom]` → wrong by +0.5 → recorded `-55.5` instead of correct `-56.0`. **Bug 2 — `torchModelBottomY` double-applying `torchDy`**: recorder formula `torchPos.getY() + shapeMin + torchDy` adds `torchDy` again, but `SlabSupportStateMixin.slabbed$offsetOutline` already applies `yOff=torchDy` in block-local space when it returns `SLABBED$COMFORT_TORCH_SHAPE.offset(0, torchDy, 0)` → local shape minY = `-1.0` (not `0`), recorder got `-55 + (-1.0) + (-1.0) = -57.0` instead of correct `-56.0`. **Combined effect**: `contactGap_v1 = (-57.0) - (-55.5) = -1.5` — entirely a measurement artifact. Corrected: `contactGap_v2 = (-56.0) - (-56.0) = 0.0`. Julia's original `contactGap=-1.500000` was measuring the recorder's formula error, not a real visual gap. **The torch placement is correct**: model bottom sits flush with the lowered slab's visible top surface. Recorder updated to `markerVersion=2`, `measurementFormulaVersion=v2` with new diagnostic fields: `rawSupportTopY`, `rawTorchShapeMinY`, `contactGapV1`. Parity proof: `BUILD SUCCESSFUL` (`fixtureMatchesLiveDyStack=true`, `contactGap=0.000000`, `failureLayer=LIVE_DY_STACK_MATCH_NO_GAP`). Default suite: `BUILD SUCCESSFUL in 1m 42s`. No production gameplay fix implemented. No release tag moved. `wall_torch=NOT_COVERED`. Beta 3.5 release prep: **PAUSED** pending Julia's live re-verification with the v2 recorder. See `docs/beta35-live-torch-recorder-contact-math-audit.md`.

## Beta 3.5 floor torch v2 source-truth parity status (2026-05-11)

- Beta 3.5 floor torch v2 contact fix is implemented narrowly for `floor_torch`.
- `FLOOR_TORCH_COMPOUND_VISIBLE_TOP_SLAB_SUPPORT` is legal and now aligns at `torchDy=-1.000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `triad=GREEN`.
- `FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT` is rejected/deferred by law because same-position contact would require illegal `dy<-1.0`; proof reports `placementResult=Fail[]`, `survival=REJECTED_BY_LAW`, `triad=REJECTED_BY_LAW`.
- Focused proof `-Dslabbed.beta35FloorTorchV2ContactGapRed=true` is GREEN with `coordinateParity=true`, `fixtureMatchesFixedLegalStack=true`, `failureLayer=NONE`.
- Regression gates passed: `compileJava compileGametestJava`, previous live item anchoring proof, previous object triad proof, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-floor-torch-v2-contact-fix-883b204`.
- Beta 3.5 release remains paused / blocked pending Julia live re-test.
- wall_torch remains `NOT_COVERED`.
- Next safe action: savepoint commit/tag/push for this narrow floor_torch contact fix, then Julia live re-test.
- production gameplay fix has been applied in this slice.
- no release tag moved.

## Beta 3.5 floor torch SBSBS red proof (2026-05-11)

- **Julia live after `04ace65`**: "SBSBS+item = floating item in vanilla position. (torch, in this case.)" This is a distinct remaining `floor_torch` failure separate from the support-finalization stale-torch fix.
- SBSBS fixture = 5-level alternating slab/full-block vertical tower (bottom→top): base slab (S) → lower anchored full block (B) → middle carrier slab (S) → upper anchored full block (B) → support slab (S); torch placed on top.
- **Controlled fixture result at HEAD `04ace65`**: `[JULIA_BETA35_FLOOR_TORCH_SBSBS_FIXTURE_GREEN]` → `fixtureResult=GREEN`; `[JULIA_BETA35_FLOOR_TORCH_SBSBS_MEASURED]` → `supportDy=-0.500`, `torchDy=-1.000`, `contactGap=0.000000`, `isVanillaPosition=false`, `failureLayer=NONE`; `[JULIA_BETA35_FLOOR_TORCH_SBSBS_SUMMARY]` → `redProofResult=GREEN`.
- Root cause of GREEN: `hasBottomSlabBelow(world, upperAnchorBlock)` is `true` (middleCarrierSlab is `stone_slab[type=bottom]`) → `isPersistentLoweredBottomSlabCarrierNonRecursive(supportPos)=true` → `supportDy=-0.5`; `isAdjacentSideSlabLowered(world, supportPos)=true` → `getYOffsetInner` returns `-1.0` for the torch via the compound slab case.
- **Gap**: controlled fixture is GREEN, but Julia's live test shows vanilla position. Failure is in Julia's specific gameplay-path arrangement, not in the plain SBSBS dy model/chain. Possible causes: different slab type, interaction with adjacent compound-visible marks, or chunk-reload timing issue.
- Gate: `-Dslabbed.beta35FloorTorchSbsbsRed=true`. Regression proofs (`SupportFinalizationRed`, `V2ContactGapRed`) and default suite all pass GREEN.
- **No production gameplay fix implemented. No release tag moved. Beta 3.5 release remains PAUSED.**
- Next slice: investigate Julia's exact live structure (block types, adjacent compound marks, placement order) to find the failure path that produces vanilla-position torch.
- `wall_torch=NOT_COVERED`, `lantern=NOT_COVERED`, `signs=NOT_COVERED`, `chains=NOT_COVERED`.
- Evidence: `tmp/beta35-floor-torch-sbsbs-red-04ace65/`. See `docs/beta35-floor-torch-sbsbs-red.md`.

- 2026-05-11: Added SBSBS floor-torch source-truth capture branch (proof-only) in `SlabbedLabLoweredSidePlacementLiveReproClientGameTest`.
- 2026-05-11: Added source-truth recorder emission (`Beta35LiveTorchCaptureRecorder`) for contact/anchor/carrier state under gate `slabbed.beta35FloorTorchSbsbsSourceTruthRed`.
- 2026-05-11: No production gameplay fix in this slice; no tag/tag movement performed yet.

## Beta 3.5 floor torch SBSBS source-truth fix (2026-05-11)

- Operating base for this slice: `4bca184` / `save/beta35-floor-torch-sbsbs-source-truth-red` on `integrate/phase19-into-side-slab-top-support`.
- Root remains `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`; tracked tree was clean before edits; `tmp/` remains untracked evidence only.
- Focused RED was reproduced before the fix: live/player-like SBSBS emitted `failureLayer=SBSBS_SUPPORT_SOURCE_TRUTH_MISMATCH`, `supportDy=-1.000000`, `torchDy=-0.500000`, `contactGap=1.000000`.
- Root cause is now isolated as authored source-truth class mismatch, not client sync: lower anchor, middle carrier, and upper anchor sync correctly; the live top support slab is authored as `COMPOUND_VISIBLE_OWNER_TOP_SLAB` (`supportDy=-1.000000`) instead of the controlled fixture's persistent lowered carrier (`supportDy=-0.500000`).
- Narrow production fix: `SlabSupport.isRejectedFloorTorchTopFace(...)` rejects `floor_torch` on a bottom slab marked `COMPOUND_VISIBLE_OWNER_TOP_SLAB`, and `SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(...)` now revalidates the block above on first marker authoring so stale torches are removed through `TorchBlockMixin`.
- Focused gate `-Dslabbed.beta35FloorTorchSbsbsSourceTruthRed=true` is GREEN after the fix with `sourceTruth=SBSBS_OWNER_TOP_SUPPORT_REJECTED_BY_LAW`, `failureLayer=NONE`, `supportDy=-1.000000`, `torchDy=N/A`, `contactGap=N/A`, and `redProofResult=GREEN`.
- Controlled SBSBS remains GREEN with `contactGap=0.000000`; support-finalization, V2 contact, fullblock contact, default client gametest, build gate, and `git diff --check` all passed.
- Evidence folder: `tmp/beta35-floor-torch-sbsbs-source-truth-fix-4bca184`.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`; Beta 3.5 remains paused pending Julia live retest; no release tag moved.
- Next safe action after savepoint: Julia live retests SBSBS floor-torch behavior from the savepoint tag; do not broaden into other item categories or release prep without explicit instruction.

## Beta 3.5 floor torch lowered bottom-slab contact fix (2026-05-11)

- Operating base for this slice: `3212d88` / `save/beta35-floor-torch-sbsbs-source-truth-fix` on `integrate/phase19-into-side-slab-top-support`.
- Root remains `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`; tracked tree was clean before edits; `tmp/` remains untracked evidence only.
- Julia live evidence after `3212d88` proved the remaining bad layer was `floor_torch` dy/contact over lowered bottom-slab support, not placement, survival, or model/outline/raycast triad split.
- Previous bad lowered bottom-slab cases:
  - `torchPos=43,-56,88`, `supportCandidateState=stone_slab[type=bottom]`, `supportDy=-1.000000`, previous `torchDy=-1.000000`, previous `contactGap=0.500000`.
  - `torchPos=43,-55,79`, `supportCandidateState=stone_slab[type=bottom]`, `supportDy=-1.000000`, previous `torchDy=-0.500000`/live capture and local reproduction `torchDy=-1.000000`, previous `contactGap=1.000000`/`0.500000`.
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now returns `torchDy=-1.500000` only for `floor_torch` above a bottom slab marked `COMPOUND_VISIBLE_SIDE_LOWER_SLAB`. This aligns torch model/outline/raycast bottom with the slab's visible top. `COMPOUND_VISIBLE_OWNER_TOP_SLAB` remains rejected by law.
- Focused gate `-Dslabbed.beta35FloorTorchLiveShapeRed=true` is GREEN after the fix. Retested lowered bottom-slab cases report `supportDy=-1.000`, `torchDy=-1.500`, `contactGap=0.000000`, `triadCoLocated=true`, `survival=SURVIVAL_GREEN`, and `failureLayer=NONE`.
- Regression gates passed: `compileJava compileGametestJava`, `-Dslabbed.beta35FloorTorchVisualContactRed=true`, `-Dslabbed.beta35LiveItemAnchoringRed=true`, `-Dslabbed.beta35ObjectSlabOwnershipRed=true`, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-floor-torch-lowered-slab-contact-fix-3212d88`.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`; scope remains `floor_torch_only`.
- Beta 3.5 release prep remains paused pending Julia live acceptance; no release tag moved.
- Next safe action after savepoint: Julia live-tests the lowered bottom-slab floor-torch contact from the savepoint tag; do not broaden into other item categories or release prep without explicit instruction.

## Beta 3.5 live floor torch dual tracer (2026-05-11)

- Operating base for this proof-only tracer slice: `b149996` / `save/beta35-floor-torch-lowered-slab-contact` on `integrate/phase19-into-side-slab-top-support`.
- Julia's live video after `b149996` means `b149996` is **not live-accepted**: floor torches sometimes place but visibly float, and sometimes floor torch placement attempts do not resolve to the intended support/position.
- The prior `b149996` proof fixed only the narrow measured lowered-bottom-slab contact case (`COMPOUND_VISIBLE_SIDE_LOWER_SLAB`, `torchDy=-1.500`, `contactGap=0.000000`). It did not cover every live source path or player targeting/intent path.
- New gated manual-live tracer flag: `-Dslabbed.beta35LiveTorchDualTrace=true`. It emits startup marker `[JULIA_BETA35_LIVE_TORCH_DUAL_TRACE] enabled=true`, placement attempts with `[JULIA_BETA35_LIVE_TORCH_PLACEMENT_ATTEMPT]`, existing nearby floor-torch contact with `[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT]`, and `[JULIA_BETA35_LIVE_TORCH_DUAL_SUMMARY]`.
- The tracer separates placement/targeting/intent classifications from visual contact/source-truth/dy classifications. It is debug-gated and proof-only; no production behavior fix is implemented in this slice.
- Beta 3.5 release prep remains **PAUSED** pending Julia live trace. Scope remains `floor_torch_only`; `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`. No release tag moved.

## Beta 3.5 floor torch lowered bottom-slab placement fix (2026-05-11)

- Operating base for this slice: `fe7677a` / `save/beta35-live-torch-dual-tracer` on `integrate/phase19-into-side-slab-top-support`.
- The dual tracer proved the current failure layer was placement on lowered bottom-slab support, not visual contact: failing live attempts had `heldItem=minecraft:torch`, `intendedSupportCandidateState=stone_slab[type=bottom]`, `intendedSupportSourceType=PLAIN_STATE`, `intendedSupportDy=-1.000000`, `finalInteractResult=Fail[]`, `torchBlockAppearedAfterAttempt=false`, and `classification=PLACEMENT_RESULT_UNKNOWN`.
- Narrow production fix: `SlabSupport.isLegalFloorTorchLoweredBottomSlabSupport(...)` now authorizes only `floor_torch` on named lowered bottom-slab supports with `supportDy=-1.0`; `TorchBlockMixin` continues to consult SlabSupport authority. The newly legal path reuses the existing contact law and resolves the floor torch to `torchDy=-1.500000` so `contactGap=0.000000`.
- Focused gate `-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchLoweredSlabPlacement=true` is GREEN: `[JULIA_BETA35_LIVE_TORCH_PLACEMENT_ATTEMPT] classification=PLACEMENT_ATTEMPT_OK`, `[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_GREEN]`, and `[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_SUMMARY] failureLayer=NONE`.
- Fixed values: `intendedSupportDy=-1.000000`, `finalInteractResult=Success[...]`, `torchBlockAppearedAfterAttempt=true`, `finalTorchState=Block{minecraft:torch}`, `torchDy=-1.500000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`.
- Regression gates passed: `compileJava compileGametestJava`, focused live-shape/contact, visual contact, player-like placement, object triad, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-floor-torch-lowered-slab-placement-fix-fe7677a`. See `docs/beta35-floor-torch-lowered-slab-placement-fix.md`.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`; scope remains `floor_torch_only`.
- Beta 3.5 release prep remains paused pending Julia live acceptance; no release tag moved.

## Beta 3.5 floor torch plain bottom contact fix (2026-05-11)

- Operating base for this slice: `66ca74a` / `save/beta35-floor-torch-lowered-slab-placement` on `integrate/phase19-into-side-slab-top-support`.
- Julia's live acceptance trace proved the `supportDy=-1.0` lowered bottom-slab placement bug was fixed. The remaining concrete contact gap was a different `floor_torch` row: `torchPos=32,-54,80`, `supportCandidateState=stone_slab[type=bottom]`, `supportSourceType=PLAIN_STATE`, `supportDy=-0.500000`, previous `torchDy=-0.500000`, and previous `contactGap=0.500000`.
- Narrow production fix: `SlabSupport` now computes floor-torch bottom-slab contact dy from existing lowered support truth, so a plain bottom slab at `supportDy=-0.5` gives `torchDy=-1.0`. This is not a global slab solidity/sturdy-face change and does not broaden all item or attachable placement.
- Tracer hygiene: duplicate/occupied target clicks where the intended torch position already contains `minecraft:torch` now classify as `OCCUPIED_TORCH_TARGET` rather than a true empty-target placement failure. Sequence 16 / `PERSISTENT_LOWERED_SLAB_CARRIER` comfort-scan behavior was deferred and not changed in this slice.
- Focused gate `-Dslabbed.beta35LiveTorchDualTrace=true -Dslabbed.beta35FloorTorchPlainBottomContact=true` is GREEN: `[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT] classification=PLACED_CONTACT_GREEN` with `supportSourceType=PLAIN_STATE`, `[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_GREEN]`, and `[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_SUMMARY] failureLayer=NONE`.
- Fixed values: `supportDy=-0.500000`, `torchDy=-1.000000`, `supportVisibleTopY=-55.000000`, `torchModelBottomY=-55.000000`, `contactGap=0.000000`, `triadCoLocated=yes`, `survival=SURVIVAL_GREEN`, duplicate click `classification=OCCUPIED_TORCH_TARGET`.
- Regression gates passed: `compileJava compileGametestJava`, lowered `supportDy=-1.0` placement regression, live-shape/contact, visual contact, player-like placement, object triad, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-floor-torch-plain-bottom-contact-fix-66ca74a`. See `docs/beta35-floor-torch-plain-bottom-contact-fix.md`.
- `wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`; scope remains `floor_torch_only`.
- Beta 3.5 release prep remains paused pending Julia live acceptance; no release tag moved.

## Beta 3.5 floor/top object family audit (2026-05-11)

- Operating base for this proof/docs slice: `8a03902` / `save/beta35-floor-torch-bug-blaster` on `integrate/phase19-into-side-slab-top-support`.
- Floor torch remains the live-accepted reference control: player-like placement, survival, contact, and model/outline/raycast co-location are GREEN on both lowered bottom support (`supportDy=-1.0`) and plain bottom support (`supportDy=-0.5`).
- New gated audit: `-Dslabbed.beta35FloorTopObjectFamilyAudit=true`, emitting `JULIA_BETA35_FLOOR_TOP_OBJECT_MATRIX_START`, `JULIA_BETA35_FLOOR_TOP_OBJECT_ROW`, and `JULIA_BETA35_FLOOR_TOP_OBJECT_SUMMARY`.
- Matrix result: `minecraft:torch=GREEN_ALREADY_INHERITS`; `minecraft:candle=CONTACT_GAP`; `minecraft:flower_pot=SURVIVAL_FAILURE`; standing-only `minecraft:oak_sign=CONTACT_GAP` with `rendererPath=BLOCK_ENTITY_OR_SPECIAL`.
- Shared failure layer: non-torch floor/top objects do not inherit the floor-torch contact dy law. Candle and standing sign place/survive but stay at `objectDy=-0.5`, producing `contactGap=1.000000` on lowered bottom support and `contactGap=0.500000` on plain bottom support. Flower pot also has an unsupported survival-law mismatch and should not be bundled with the candle contact fix.
- Validation passed: `compileJava compileGametestJava`, focused floor/top object audit, floor torch regression, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-floor-top-object-family-audit-8a03902`. See `docs/beta35-floor-top-object-family-audit.md`.
- Release remains paused until this one-more-family decision is resolved. Recommended next slice: single-representative `minecraft:candle` contact/dy law proof and fix, not all-item support.
- `wall_torch`, lanterns, chains, wall signs, and hanging signs remain `NOT_COVERED`; redstone/rails remain audit-only/out of scope.
- No production behavior fix implemented. No release audit run. No release tag moved.

## Beta 3.5 candle floor/top contact fix (2026-05-11)

- Operating base for this implementation slice: `08198ef` / `save/beta35-floor-top-object-family-audit` on `integrate/phase19-into-side-slab-top-support`.
- Previous candle failure layer: `CONTACT_GAP`; candle placed and survived, but `objectDy=-0.500000` left `contactGap=1.000000` on lowered bottom support (`supportDy=-1.0`) and `contactGap=0.500000` on plain bottom support (`supportDy=-0.5`).
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now has a `Blocks.CANDLE`-only floor/top contact branch using the same bottom-slab support dy calculation as floor torch. `SlabSupportStateMixin` also gives lowered `minecraft:candle` an outline-backed raycast basis when vanilla raycast is empty, keeping model/outline/raycast co-located.
- Focused gate `-Dslabbed.beta35CandleFloorTopContact=true` is GREEN: lowered bottom support reports `objectDy=-1.500000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `triadCoLocated=yes`; plain bottom support reports `objectDy=-1.000000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `triadCoLocated=yes`; summary `failureLayer=NONE`.
- Updated floor/top matrix: `minecraft:torch=GREEN_ALREADY_INHERITS`; `minecraft:candle=GREEN_ALREADY_INHERITS`; `minecraft:flower_pot=SURVIVAL_FAILURE`; standing-only `minecraft:oak_sign=CONTACT_GAP` with `rendererPath=BLOCK_ENTITY_OR_SPECIAL`.
- Validation passed: `compileJava compileGametestJava`, focused candle proof, focused floor/top family audit, floor torch regression, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-candle-floor-top-contact-fix-08198ef`. See `docs/beta35-candle-floor-top-contact-fix.md`.
- Scope is floor torch plus `minecraft:candle` only. No flower pot survival fix, standing sign fix, wall/ceiling/hanging category, redstone/rail work, all-object support, or global sturdy-face/solidity change was made.
- Release audit remains paused until Julia decides whether candle is enough for this one-more-family pass or whether flower pot/sign need separate slices. No release audit run. No release tag moved.

## Beta 3.5 flower pot floor/top survival fix (2026-05-11)

- Operating base for this implementation slice: `b76643d` / `save/beta35-candle-floor-top-contact` on `integrate/phase19-into-side-slab-top-support`.
- Previous flower pot failure layer: `SURVIVAL_FAILURE`; `minecraft:flower_pot` placed on the valid slab-supported rows, but the unsupported control remained valid.
- Narrow production fix: `SlabSupportStateMixin` now has a `Blocks.FLOWER_POT`-only `canPlaceAt` branch. It accepts survival on vanilla full-square top support or Slabbed legal slab top support, and rejects unsupported cases. This is not broad floor-object support and does not change global solidity or sturdy-face truth.
- Focused gate `-Dslabbed.beta35FlowerPotFloorTopSurvival=true` is GREEN for survival: lowered bottom support reports `supportDy=-1.000000`, placement GREEN, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`; plain bottom support reports `supportDy=-0.500000`, placement GREEN, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`; summary `failureLayer=NONE`.
- Updated floor/top matrix: `minecraft:torch=GREEN_ALREADY_INHERITS`; `minecraft:candle=GREEN_ALREADY_INHERITS`; `minecraft:flower_pot` survival is GREEN with secondary `CONTACT_GAP`; standing-only `minecraft:oak_sign=CONTACT_GAP` with `rendererPath=BLOCK_ENTITY_OR_SPECIAL`.
- Flower pot still has a separate visual/contact layer: `contactGap=1.000000` on lowered bottom support and `contactGap=0.500000` on plain bottom support, with `triadCoLocated=no`. Standing sign remains unchanged/separate.
- Validation passed: `compileJava compileGametestJava`, focused flower pot proof, focused floor/top family audit, floor torch regression, focused candle regression, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-flower-pot-floor-top-survival-fix-b76643d`. See `docs/beta35-flower-pot-floor-top-survival-fix.md`.
- Scope is floor torch plus `minecraft:candle` plus `minecraft:flower_pot` survival only. No standing sign fix, wall/ceiling/hanging category, redstone/rail work, all-object support, or global sturdy-face/solidity change was made.
- Release audit remains paused until Julia decides whether this is enough for the one-more-family pass or whether flower pot contact and/or standing sign need separate slices. No release audit run. No release tag moved.

## Beta 3.5 flower pot floor/top contact fix (2026-05-11)

- Operating base for this implementation slice: `9ce0211` / `save/beta35-flower-pot-floor-top-survival` on `integrate/phase19-into-side-slab-top-support`.
- Previous flower pot failure layer: `CONTACT_GAP`; survival was already GREEN, but flower pot stayed at generic `objectDy=-0.500000`, leaving `contactGap=1.000000` on lowered bottom support and `contactGap=0.500000` on plain bottom support.
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now has an explicit Beta 3.5 floor/top contact object branch for `Blocks.CANDLE` plus `Blocks.FLOWER_POT` only. `SlabSupportStateMixin` applies the existing lowered floor/top contact raycast fallback to flower pot as well as candle, so model/outline/raycast stay co-located.
- Focused gate `-Dslabbed.beta35FlowerPotFloorTopContact=true` is GREEN: lowered bottom support reports `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`; plain bottom support reports `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`; summary `failureLayer=NONE`.
- Updated floor/top matrix: `minecraft:torch=GREEN_ALREADY_INHERITS`; `minecraft:candle=GREEN_ALREADY_INHERITS`; `minecraft:flower_pot=GREEN_ALREADY_INHERITS`; standing-only `minecraft:oak_sign=CONTACT_GAP` with `rendererPath=BLOCK_ENTITY_OR_SPECIAL`.
- Validation passed: `compileJava compileGametestJava`, focused flower pot contact proof, focused flower pot survival regression, focused candle regression, focused floor/top family audit, floor torch regression, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-flower-pot-floor-top-contact-fix-9ce0211`. See `docs/beta35-flower-pot-floor-top-contact-fix.md`.
- Scope is floor torch plus `minecraft:candle` plus `minecraft:flower_pot` only. No standing sign fix, wall/ceiling/hanging category, redstone/rail work, all-object support, or global sturdy-face/solidity change was made.
- Release audit remains paused until Julia decides whether floor torch plus candle plus flower pot is enough for Beta 3.5, or whether standing sign must also be handled. No release audit run. No release tag moved.


- 2026-05-11: Added Beta 3.5 release-readiness audit PASS record at HEAD `f9d2987` / `save/beta35-flower-pot-floor-top-contact` for scoped candidate `floor_torch + candle + flower_pot`.
- 2026-05-11: Marked the scoped release-readiness audit as superseded by Julia's expanded common-object scope request (`trapdoors`, `doors`, `crafting_table`, `fences`, etc.); it is not release authorization for that expanded scope.
- `wall_torch`, `lantern`, `chains`, signs, hanging signs, redstone, and rails remain `NOT_COVERED` for Beta 3.5 scoped scope.

## Beta 3.5 common object compatibility audit (2026-05-11)

- Audit-only common-object matrix added behind `-Dslabbed.beta35CommonObjectCompatibilityAudit=true`; no production behavior fix, release audit, release tag movement, version metadata, changelog, or all-item support change.
- Operating base for the audit slice: `e8f5fdb` / `save/beta35-scoped-release-readiness-audit`.
- Required representatives audited: `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, `minecraft:oak_trapdoor`, `minecraft:oak_door`, and standing `minecraft:oak_sign`.
- Current green set remains `floor_torch + candle + flower_pot`; all three are `GREEN_ALREADY_INHERITS` on plain bottom and lowered bottom slab-supported rows.
- Expanded common objects are not release-green: `crafting_table`, `furnace`, `oak_fence`, `oak_trapdoor`, `oak_door`, and standing `oak_sign` all place and survive on slab-supported rows but show contact gaps (`0.500000` on plain bottom support, `1.000000` on lowered bottom support).
- Additional category risks remain: `oak_fence=COLLISION_SHAPE_RISK`, `oak_trapdoor=NEEDS_CATEGORY_SLICE`, `oak_door=MULTIPART_RISK`, standing `oak_sign=RENDERER_SPECIAL_CASE`. `lantern`, `chain`, `redstone_wire`, and `rail` were not audited in this slice.
- Validation passed: `compileJava compileGametestJava`, focused common-object matrix, floor/top family regression, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-common-object-compatibility-audit-e8f5fdb`. See `docs/beta35-common-object-compatibility-audit.md`.
- Release remains paused pending Julia decision. Recommended next slice, if fixing one more family before release: single-representative ordinary full block contact/dy proof and fix starting with `minecraft:crafting_table`, not fences/trapdoors/doors/signs/all-item support.
- Release finalization is paused; next action is a common-object compatibility matrix from a clean savepoint.

## Beta 3.5 crafting table contact fix (2026-05-11)

- Operating base for this implementation slice: `9e1348c` / `save/beta35-common-object-compatibility-audit` on `integrate/phase19-into-side-slab-top-support`.
- Previous `minecraft:crafting_table` failure layer: `CONTACT_GAP`; placement and survival were already GREEN, but slab-supported rows stayed at `objectDy=-0.500000`, leaving `contactGap=0.500000` on plain bottom slab support and `contactGap=1.000000` on lowered bottom slab support.
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now has a constrained Beta 3.5 ordinary full-block contact dy path for `CraftingTableBlock` and solid full-block `BlockEntityProvider` blocks. It applies only over bottom slab supports with lowered Slabbed support truth and computes `objectDy = supportDy - 0.5`. It is not a fence, trapdoor, door, sign, hanging, redstone, rail, all-item, or global sturdy-face/solidity change.
- Focused gate `-Dslabbed.beta35CraftingTableContact=true` is GREEN: vanilla row `contactGap=0.000000`; plain bottom support reports `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`; lowered bottom support reports `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`; summary `failureLayer=NONE`.
- Updated common-object matrix: `minecraft:crafting_table=GREEN_ALREADY_INHERITS`; `minecraft:furnace` inherited contact (`contactGap=0.000000`) but remains `TRIAD_MISMATCH` on slab-supported rows; `minecraft:oak_fence` remains `CONTACT_GAP` plus `COLLISION_SHAPE_RISK`; `minecraft:oak_trapdoor` remains `CONTACT_GAP` plus `NEEDS_CATEGORY_SLICE`; `minecraft:oak_door` remains `CONTACT_GAP` plus `MULTIPART_RISK`; standing `minecraft:oak_sign` remains `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`.
- Floor/top family regression remains GREEN for `minecraft:torch`, `minecraft:candle`, and `minecraft:flower_pot`; standing `minecraft:oak_sign` remains separate `CONTACT_GAP`.
- Validation passed: `compileJava compileGametestJava`, focused crafting table contact proof, focused common-object matrix, focused floor/top family audit, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-crafting-table-contact-fix-9e1348c`. See `docs/beta35-crafting-table-contact-fix.md`.
- Release remains paused pending Julia decision on whether `crafting_table` plus the existing floor/top green set is enough, or whether furnace triad, fence, trapdoor, door, and/or sign categories must be handled before Beta 3.5. No release audit run. No release tag moved.

## Beta 3.5 furnace triad fix (2026-05-11)

- Operating base for this implementation slice: `3712a37` / `save/beta35-crafting-table-contact` on `integrate/phase19-into-side-slab-top-support`.
- Previous `minecraft:furnace` failure layer: `TRIAD_MISMATCH`; contact had already inherited from the ordinary full-block dy helper (`contactGap=0.000000`), but slab-supported rows did not have model/outline/raycast co-location.
- Narrow production fix: `SlabSupportStateMixin` now gives `minecraft:furnace` the lowered full-block raycast basis when its resolved dy is negative and the native raycast shape is empty. The dy still comes from `SlabSupport`; this is not a fence, trapdoor, door, sign, hanging, rail, redstone, all-object, or global sturdy-face/solidity change.
- Focused gate `-Dslabbed.beta35FurnaceTriad=true` is GREEN: vanilla row `contactGap=0.000000`; plain bottom support reports `supportDy=-0.500000`, `objectDy=-1.000000`, `contactGap=0.000000`, matching model/outline/raycast bounds, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`; lowered bottom support reports `supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, matching model/outline/raycast bounds, `survival=SURVIVAL_GREEN`, `unsupported=UNSUPPORTED_FAILS`, `triadCoLocated=yes`; summary `failureLayer=NONE`.
- Updated common-object matrix: `minecraft:crafting_table=GREEN_ALREADY_INHERITS`; `minecraft:furnace=GREEN_ALREADY_INHERITS`; `minecraft:oak_fence` remains `CONTACT_GAP` plus `COLLISION_SHAPE_RISK`; `minecraft:oak_trapdoor` remains `CONTACT_GAP` plus `NEEDS_CATEGORY_SLICE`; `minecraft:oak_door` remains `CONTACT_GAP` plus `MULTIPART_RISK`; standing `minecraft:oak_sign` remains `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`.
- Floor/top family regression remains GREEN for `minecraft:torch`, `minecraft:candle`, and `minecraft:flower_pot`; standing `minecraft:oak_sign` remains separate `CONTACT_GAP`.
- Validation passed: `compileJava compileGametestJava`, focused furnace triad proof, focused common-object matrix, focused crafting table contact regression, focused floor/top family audit, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-furnace-triad-fix-3712a37`. See `docs/beta35-furnace-triad-fix.md`.
- Release remains paused pending Julia decision on whether ordinary full blocks plus the existing floor/top green set are enough, or whether fence, trapdoor, door, sign, and/or other categories must be handled before Beta 3.5. No release audit run. No release tag moved.

## Beta 3.5 oak fence partial-collision contact fix (2026-05-11)

- Safety worktree for this slice: `/Users/joolmac/CascadeProjects/Slabbed-beta35-object-compat-worktree` on `work/beta35-common-object-compat`, created from `a6400ca` / `save/beta35-furnace-triad`. Original canonical repo stayed tracked/staged-clean.
- Previous `minecraft:oak_fence` failure layer: `CONTACT_GAP` plus `COLLISION_SHAPE_RISK`; placement and survival were already GREEN, but slab-supported rows had `contactGap=0.500000` on plain bottom support and `contactGap=1.000000` on lowered bottom support.
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now has an `minecraft:oak_fence`-only contact dy path over lowered bottom slab support truth, and `SlabSupportStateMixin` aligns lowered oak-fence outline/raycast/collision shape to the fence collision body. This is not all fences, walls, panes, trapdoors, doors, signs, all partial blocks, or a global sturdy-face/solidity change.
- Focused gate `-Dslabbed.beta35OakFenceContact=true` is GREEN: vanilla, plain bottom, and lowered bottom rows all place/survive; slab-supported rows report `objectDy=-1.000000` or `-1.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes`, and summary `failureLayer=NONE`.
- Updated common-object matrix: `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, and `minecraft:oak_fence` are GREEN. `minecraft:oak_trapdoor` remains `CONTACT_GAP` plus `NEEDS_CATEGORY_SLICE`; `minecraft:oak_door` remains `CONTACT_GAP` plus `MULTIPART_RISK`; standing `minecraft:oak_sign` remains `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`.
- Validation passed: `compileJava compileGametestJava`, focused oak-fence proof, focused common-object matrix, focused floor/top family audit, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-oak-fence-contact-risk-a6400ca`. See `docs/beta35-oak-fence-contact-risk.md`.
- Release remains paused. No release audit run. No release tag moved. Trapdoor, door, and sign were not touched.

## Beta 3.5 trapdoor / door category audit (2026-05-11)

- Operating base for this proof/docs slice: `f88afb7` / `save/beta35-oak-fence-contact-integrated` on `integrate/phase19-into-side-slab-top-support`.
- New gated audit: `-Dslabbed.beta35TrapdoorDoorAudit=true`; markers `JULIA_BETA35_TRAPDOOR_DOOR_MATRIX_START`, `JULIA_BETA35_TRAPDOOR_DOOR_ROW`, and `JULIA_BETA35_TRAPDOOR_DOOR_SUMMARY`.
- Current green set remains `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, and `minecraft:oak_fence`.
- `minecraft:oak_trapdoor` places and survives on slab-supported rows; open/close was exercised successfully, but slab-supported rows remain `CONTACT_GAP` (`0.500000` plain bottom, `1.000000` lowered bottom). Exact failure layer: `TRAPDOOR_CONTACT_GAP`. Next safe implementation slice is trapdoor-only contact/open-close handling.
- `minecraft:oak_door` places both halves and both halves survive, with matching `bottomDy`/`topDy` on audited rows; slab-supported rows remain `CONTACT_GAP` (`0.500000` plain bottom, `1.000000` lowered bottom) and the category remains multipart-risky. Exact failure layer: `DOOR_MULTIPART_CONTACT_GAP`. Recommended status: defer door unless Julia authorizes a separate multipart slice.
- Validation passed: `compileJava compileGametestJava`, focused trapdoor/door audit, focused common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-trapdoor-door-category-audit-f88afb7`. See `docs/beta35-trapdoor-door-category-audit.md`.
- Release remains paused. No release audit run. No release tag moved. Signs, lanterns, chains, redstone, and rails were not touched.

## Beta 3.5 oak trapdoor contact fix (2026-05-11)

- Operating base for this implementation slice: `2300229` / `save/beta35-trapdoor-door-category-audit` on `integrate/phase19-into-side-slab-top-support`.
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now has an `minecraft:oak_trapdoor` bottom-half-only contact dy path over valid lowered bottom slab support truth, and `SlabSupportStateMixin` gives lowered bottom-half oak trapdoors an outline-backed raycast basis when vanilla raycast is empty. This is not an oak door, sign, lantern, chain, redstone, rail, all-trapdoor, all-object, or global sturdy-face/solidity change.
- Focused proof gate: `-Dslabbed.beta35OakTrapdoorContact=true`; markers `JULIA_BETA35_OAK_TRAPDOOR_CONTACT_GREEN` and `JULIA_BETA35_OAK_TRAPDOOR_CONTACT_SUMMARY failureLayer=NONE`.
- `minecraft:oak_trapdoor` is now GREEN for the audited bottom-half interactive hinge representative on valid slab-supported rows: plain bottom support and lowered bottom support both report `contactGap=0.000000`, `triadCoLocated=yes`, collision bounds co-located, and `openCloseResult=Success->Success`.
- Updated common-object matrix: `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, and `minecraft:oak_trapdoor` are GREEN. `minecraft:oak_door` remains `CONTACT_GAP` plus `MULTIPART_RISK`; standing `minecraft:oak_sign` remains `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`; `lantern`, `chain`, `redstone_wire`, and `rail` remain not covered.
- Validation passed: `compileJava compileGametestJava`, focused oak-trapdoor proof, focused trapdoor/door audit, focused common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-oak-trapdoor-contact-fix-2300229`. See `docs/beta35-oak-trapdoor-contact-fix.md`.

## Beta 3.5 oak door multipart contact fix (2026-05-12)

- Operating base for this implementation slice: `d6c10d8` / `save/beta35-oak-trapdoor-contact` on `integrate/phase19-into-side-slab-top-support`.
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now has a `minecraft:oak_door`-only multipart contact dy path for lower and upper door halves over valid lowered bottom slab support truth, and `SlabSupportStateMixin` gives lowered oak-door halves an outline-backed raycast basis when vanilla raycast is empty. This is not signs, lanterns, chains, redstone, rails, all doors, all multipart blocks, all objects, or global sturdy-face/solidity.
- `minecraft:oak_door` is now GREEN for the audited oak-door multipart representative on valid slab-supported rows: bottom and top halves appear, `bottomDy` and `topDy` remain coherent, `bottomContactGap=0.000000`, `topAlignment=GREEN`, model/outline/raycast/collision are co-located, survival is GREEN, and `openCloseResult=Success->Success`.
- Updated common-object matrix: `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, `minecraft:oak_trapdoor`, and `minecraft:oak_door` are GREEN. Standing `minecraft:oak_sign` remains `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`; `lantern`, `chain`, `redstone_wire`, and `rail` remain not covered.
- Release remains paused pending Julia decision on whether standing signs, hanging objects, rails, redstone, or any broader category are required before Beta 3.5. No release audit run. No release tag moved.
- Evidence folder: `tmp/beta35-oak-door-multipart-contact-fix-d6c10d8`. See `docs/beta35-oak-door-multipart-contact-fix.md`.
- Release remains paused pending Julia decision on whether door is required before release. No release audit run. No release tag moved. Oak door, signs, lanterns, chains, redstone, and rails were not touched.

## Beta 3.5 standing oak sign contact fix (2026-05-12)

- Operating base for this implementation slice: `dc1076c` / `save/beta35-oak-door-contact` on `integrate/phase19-into-side-slab-top-support`.
- Previous `minecraft:oak_sign` status: standing sign representative reported `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE` in the common-object matrix. First focused attempt proved the contact dy path was narrow but exposed `STANDING_OAK_SIGN_TRIAD_MISMATCH` because slab-supported standing sign raycast bounds were empty while outline/render proxy bounds were lowered.
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now has an exact `minecraft:oak_sign` contact dy path over valid lowered bottom slab support truth, and `SlabSupportStateMixin` gives lowered standing oak signs an outline-backed raycast basis when vanilla raycast is empty. This is not wall signs, hanging signs, lanterns, chains, redstone, rails, all signs, all block entities, all objects, or global sturdy-face/solidity.
- Focused gate `-Dslabbed.beta35StandingOakSignContact=true` is GREEN with `failureLayer=NONE`: placement and survival are GREEN, `blockEntityPresent=true`, slab-supported rows report `objectDy=-1.000000` / `-1.500000`, `contactGap=0.000000`, and model/render proxy, outline, and raycast bounds are co-located for the standing sign representative. Renderer path remains the existing `BlockEntityOffsetMixin`.
- Updated common-object matrix: all 27 representative rows are GREEN (`greenAlreadyInherits=27`, `contactGap=0`, `triadMismatch=0`, `rendererSpecialCase=0`). Current green set is `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, `minecraft:oak_trapdoor`, `minecraft:oak_door`, and standing `minecraft:oak_sign`. `lantern`, `chain`, `redstone_wire`, and `rail` remain not audited / not covered.
- Regression gates passed: `compileJava compileGametestJava`, `-Dslabbed.beta35StandingOakSignContact=true`, `-Dslabbed.beta35CommonObjectCompatibilityAudit=true`, `-Dslabbed.beta35TrapdoorDoorAudit=true`, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-standing-oak-sign-contact-fix-dc1076c`. See `docs/beta35-standing-oak-sign-contact-fix.md`.
- Release remains paused pending Julia decision on lantern/chain/redstone/rail or any broader category. No release audit run. No release tag moved. Wall signs, hanging signs, lanterns, chains, redstone, and rails were not touched.
## Beta 3.5 special fullblock compatibility audit (2026-05-12)

- Safety worktree for this proof/docs slice: `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`, created from `d6c10d8` / `save/beta35-oak-trapdoor-contact`. Canonical checkout was not modified.
- New gated audit: `-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true`; markers `JULIA_BETA35_SPECIAL_FULLBLOCK_MATRIX_START`, `JULIA_BETA35_SPECIAL_FULLBLOCK_ROW`, and `JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY`.
- Required representatives audited: `minecraft:bookshelf`, `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:barrel`, `minecraft:chest`, plus controls `minecraft:crafting_table` and `minecraft:furnace`. Optional cheap rows audited: `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil`.
- Summary: `rows=30 greenAlreadyInherits=7 placementFailure=0 survivalFailure=0 contactGap=14 triadMismatch=2 blockEntityRisk=2 specialRendererRisk=2 needsCategorySlice=3 outOfScopeForBeta35=0`.
- Controls remain GREEN: `minecraft:crafting_table` and `minecraft:furnace`. Current green set remains `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, and `minecraft:oak_trapdoor`.
- `minecraft:bookshelf` is the nearest ordinary-full-block sibling and currently places/survives but has slab-supported contact gaps (`0.500000` plain bottom, `1.000000` lowered bottom). Recommended next implementation slice, if Julia authorizes one: bookshelf-only ordinary-full-block contact/dy proof and fix.
- Block-entity/special-renderer/special-shape rows are category slices: `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:barrel`, `minecraft:chest`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil`.
- Validation passed: `compileJava compileGametestJava`, focused special-fullblock audit, focused common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-special-fullblock-compat-audit-d6c10d8`. See `docs/beta35-special-fullblock-compatibility-audit.md`.
- No production behavior fix implemented. No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched.

## Beta 3.5 bookshelf contact fix (2026-05-12)

- Operating base for this implementation slice: `e0da848` / `save/beta35-special-fullblock-compatibility-audit` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`. Canonical checkout was not modified.
- Previous `minecraft:bookshelf` failure layer: `CONTACT_GAP`; placement and survival were already GREEN, but slab-supported rows had `contactGap=0.500000` on plain bottom support and `contactGap=1.000000` on lowered bottom support.
- Narrow production fix: `SlabSupport.getYOffsetInner(...)` now lets exact `Blocks.BOOKSHELF` use the existing Beta 3.5 ordinary-full-block contact dy helper over valid lowered bottom slab support truth. This is not all full blocks, not all special fullblocks, not block entities, and not a global sturdy-face/solidity change.
- Focused gate `-Dslabbed.beta35BookshelfContact=true` is GREEN: vanilla, plain bottom, and lowered bottom rows all place/survive; slab-supported rows report `objectDy=-1.000000` or `-1.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, and model/outline/raycast/collision bounds co-located. Summary marker: `JULIA_BETA35_BOOKSHELF_CONTACT_SUMMARY failureLayer=NONE`.
- Updated special-fullblock matrix: `minecraft:bookshelf`, `minecraft:crafting_table`, and `minecraft:furnace` are GREEN ordinary-full-block representatives. Matrix summary now reports `rows=30 greenAlreadyInherits=9 placementFailure=0 survivalFailure=0 contactGap=12 triadMismatch=2 blockEntityRisk=2 specialRendererRisk=2 needsCategorySlice=3 outOfScopeForBeta35=0`.
- Remaining special-fullblock statuses are unchanged and honest: `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:chest`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain slab-supported `CONTACT_GAP`; `minecraft:barrel` remains `TRIAD_MISMATCH` on slab-supported rows. They were not fixed in this slice.
- Common-object matrix still passes for this worktree and keeps its pre-existing door/sign classifications: `greenAlreadyInherits=21 contactGap=4 multipartRisk=1 rendererSpecialCase=1`.
- Validation passed: `compileJava compileGametestJava`, focused bookshelf proof, focused special-fullblock audit, focused common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-bookshelf-contact-fix-e0da848`. See `docs/beta35-bookshelf-contact-fix.md`.
- Release remains paused. No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched. Canonical checkout was not modified.

## Beta 3.5 chest contact fix (2026-05-12)

- Operating base for this implementation slice: `baf09f0` / `save/beta35-bookshelf-contact` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`. Canonical checkout was not modified.
- Previous `minecraft:chest` failure layer: `CONTACT_GAP`; placement and survival were already GREEN, but slab-supported rows had `contactGap=0.500000` on plain bottom support and `contactGap=1.000000` on lowered bottom support. First focused implementation attempt fixed contact and exposed `CHEST_TRIAD_MISMATCH` because vanilla chest raycast was empty on lowered rows.
- New `minecraft:chest` failure layer: `NONE`. The fix is exact `Blocks.CHEST` contact/dy in `SlabSupport` plus a chest-only lowered raycast fallback in `SlabSupportStateMixin`. Existing `BlockEntityOffsetMixin` keeps chest block-entity rendering on the same `SlabSupport` dy; no renderer rewrite was needed.
- Focused proof `-Dslabbed.beta35ChestContact=true` passes with `JULIA_BETA35_CHEST_CONTACT_SUMMARY failureLayer=NONE`; slab-supported rows report `placementResult=Success`, `blockEntityPresent=true`, `survivalResult=SURVIVAL_GREEN`, `contactGap=0.000000`, `triadCoLocated=yes`, and co-located model/outline/raycast/collision bounds.
- Updated special-fullblock matrix: `minecraft:chest`, `minecraft:bookshelf`, `minecraft:crafting_table`, and `minecraft:furnace` are GREEN representatives. Matrix summary now reports `rows=30 greenAlreadyInherits=12 placementFailure=0 survivalFailure=0 contactGap=10 triadMismatch=2 blockEntityRisk=2 specialRendererRisk=1 needsCategorySlice=3 outOfScopeForBeta35=0`.
- Remaining special-fullblock statuses are unchanged and honest: `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain slab-supported `CONTACT_GAP`; `minecraft:barrel` remains `TRIAD_MISMATCH` on slab-supported rows. They were not fixed in this slice.
- Common-object matrix still passes for this worktree and keeps its pre-existing door/sign classifications: `greenAlreadyInherits=21 contactGap=4 multipartRisk=1 rendererSpecialCase=1`.
- Validation passed: `compileJava compileGametestJava`, focused chest proof, focused special-fullblock audit, focused common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-chest-contact-fix-baf09f0`. See `docs/beta35-chest-contact-fix.md`.
- Release remains paused. No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched. Canonical checkout was not modified.

## Beta 3.5 barrel triad fix (2026-05-12)

- Operating base for this implementation slice: `0ee0ab3` / `save/beta35-chest-contact` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`. Canonical checkout was not modified.
- Previous `minecraft:barrel` failure layer: `TRIAD_MISMATCH`; placement, survival, and contact were already GREEN, with slab-supported rows at `contactGap=0.000000`. The exact mismatch was raycast-only: model, outline, and collision were co-located, while vanilla barrel raycast was empty.
- New `minecraft:barrel` failure layer: `NONE`. The fix is a barrel-only lowered raycast fallback in `SlabSupportStateMixin` that uses the lowered outline basis when native barrel raycast is empty. No broad block-entity/fullblock fallback and no renderer rewrite were added.
- Focused proof `-Dslabbed.beta35BarrelTriad=true` passes with `JULIA_BETA35_BARREL_TRIAD_SUMMARY failureLayer=NONE`; slab-supported rows report `placementResult=Success`, `blockEntityPresent=true`, `survivalResult=SURVIVAL_GREEN`, `contactGap=0.000000`, `triadCoLocated=yes`, and co-located model/outline/raycast/collision bounds.
- Updated special-fullblock matrix: `minecraft:barrel`, `minecraft:chest`, `minecraft:bookshelf`, `minecraft:crafting_table`, and `minecraft:furnace` are GREEN representatives. Matrix summary now reports `rows=30 greenAlreadyInherits=15 placementFailure=0 survivalFailure=0 contactGap=10 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=1 needsCategorySlice=3 outOfScopeForBeta35=0`.
- Remaining special-fullblock statuses are unchanged and honest: `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain slab-supported `CONTACT_GAP`. They were not fixed in this slice.
- Common-object matrix still passes for this worktree and keeps its pre-existing door/sign classifications: `greenAlreadyInherits=21 contactGap=4 multipartRisk=1 rendererSpecialCase=1`.
- Validation passed: `compileJava compileGametestJava`, focused barrel proof, focused special-fullblock audit, focused common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-barrel-triad-fix-0ee0ab3`. See `docs/beta35-barrel-triad-fix.md`.
- Release remains paused. No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched. Canonical checkout was not modified.

## Beta 3.5 enchanting table contact fix (2026-05-12)

- Operating base for this implementation slice: `e46cd26` / `save/beta35-barrel-triad` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`. Canonical checkout was not modified.
- Previous `minecraft:enchanting_table` failure layer: `CONTACT_GAP`; placement and survival were already GREEN, but slab-supported rows had `contactGap=0.500000` on plain bottom support and `contactGap=1.000000` on lowered bottom support. The exact mechanism was too-shallow dy plus empty native raycast.
- New `minecraft:enchanting_table` failure layer: `NONE`. The fix is exact `Blocks.ENCHANTING_TABLE` contact/dy in `SlabSupport` plus an enchanting-table-only lowered raycast fallback in `SlabSupportStateMixin`. Existing `BlockEntityOffsetMixin` keeps enchanting-table block-entity rendering on the same `SlabSupport` dy; no renderer rewrite was added.
- Focused proof `-Dslabbed.beta35EnchantingTableContact=true` passes with `JULIA_BETA35_ENCHANTING_TABLE_CONTACT_SUMMARY failureLayer=NONE`; slab-supported rows report `placementResult=Success`, `blockEntityPresent=true`, `survivalResult=SURVIVAL_GREEN`, `contactGap=0.000000`, `triadCoLocated=yes`, and co-located model/outline/raycast/collision bounds.
- Updated special-fullblock matrix: `minecraft:enchanting_table`, `minecraft:barrel`, `minecraft:chest`, `minecraft:bookshelf`, `minecraft:crafting_table`, and `minecraft:furnace` are GREEN representatives. Matrix summary now reports `rows=30 greenAlreadyInherits=18 placementFailure=0 survivalFailure=0 contactGap=8 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=3 outOfScopeForBeta35=0`.
- Remaining special-fullblock statuses are unchanged and honest: `minecraft:lectern` remains an interactive block-entity contact slice; `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain special-shape category slices. They were not fixed in this slice.
- Common-object matrix still passes for this worktree and keeps its pre-existing door/sign classifications: `greenAlreadyInherits=21 contactGap=4 multipartRisk=1 rendererSpecialCase=1`.
- Validation passed: `compileJava compileGametestJava`, focused enchanting-table proof, focused special-fullblock audit, focused common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-enchanting-table-contact-fix-e46cd26`. See `docs/beta35-enchanting-table-contact-fix.md`.
- Release remains paused. No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched. Canonical checkout was not modified.

## Beta 3.5 stonecutter contact fix (2026-05-12)

- Operating base for this implementation slice: `99b03ed` / `save/beta35-enchanting-table-contact` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`. Canonical checkout was not modified.
- Previous `minecraft:stonecutter` failure layer: `CONTACT_GAP`; placement and survival were already GREEN, but slab-supported rows had `contactGap=0.500000` on plain bottom support and `contactGap=1.000000` on lowered bottom support. The exact mechanism was too-shallow dy plus empty native raycast.
- New `minecraft:stonecutter` failure layer: `NONE`. The fix is exact `Blocks.STONECUTTER` contact/dy in `SlabSupport` plus a stonecutter-only lowered raycast fallback in `SlabSupportStateMixin`. No broad special-fullblock, stone-like utility block, or global sturdy-face/solidity change was added.
- Focused proof `-Dslabbed.beta35StonecutterContact=true` passes with `JULIA_BETA35_STONECUTTER_CONTACT_SUMMARY failureLayer=NONE`; slab-supported rows report `placementResult=Success`, `survivalResult=SURVIVAL_GREEN`, `contactGap=0.000000`, `triadCoLocated=yes`, and co-located model/outline/raycast/collision bounds.
- Updated special-fullblock matrix: `minecraft:stonecutter`, `minecraft:enchanting_table`, `minecraft:barrel`, `minecraft:chest`, `minecraft:bookshelf`, `minecraft:crafting_table`, and `minecraft:furnace` are GREEN representatives. Matrix summary now reports `rows=30 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=6 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=2 outOfScopeForBeta35=0`.
- Remaining special-fullblock statuses are unchanged and honest: `minecraft:lectern` remains an interactive block-entity contact slice; `minecraft:grindstone` and `minecraft:anvil` remain special-shape category slices. They were not fixed in this slice.
- Validation passed: `compileJava compileGametestJava`, focused stonecutter proof, focused special-fullblock audit, focused common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-stonecutter-contact-fix-99b03ed`. See `docs/beta35-stonecutter-contact-fix.md`.
- Release remains paused. No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched. Canonical checkout was not modified.

## Beta 3.5 special fullblock helper consolidation (2026-05-12)

- Operating base for this consolidation slice: `d854e2b` / `save/beta35-stonecutter-contact` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`. Canonical checkout was not modified.
- Consolidation performed: yes. `SlabSupport` now names the already-proven Beta 3.5 special-fullblock contact allowlist as `isBeta35SpecialFullblockContactObject(...)` and routes contact dy through `beta35SpecialFullblockContactDy(...)`. `SlabSupportStateMixin` now names the separate empty-native-raycast fallback allowlist as `slabbed$isBeta35SpecialFullblockRaycastFallbackObject(...)`.
- The contact allowlist is explicit and limited to already-green representatives: `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, and `minecraft:stonecutter`. The raycast fallback allowlist is separately limited to `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, and `minecraft:stonecutter`.
- No new object support was implemented. `minecraft:lectern`, `minecraft:grindstone`, and `minecraft:anvil` remain open category slices. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched.
- Focused special-fullblock matrix remains GREEN with unchanged summary: `rows=30 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=6 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=2 outOfScopeForBeta35=0`.
- Focused common-object matrix remains GREEN with unchanged summary: `rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0`. Default `runClientGameTest` and `git diff --check` passed.
- No release audit run. No release tag moved. Next recommended implementation slice remains `minecraft:grindstone` or `minecraft:anvil`; do not bundle either with lectern.

## Beta 3.5 anvil contact fix (2026-05-12)

- Operating base for this implementation slice: `9f3bacf` / `save/beta35-special-fullblock-helper-consolidation` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`. Canonical checkout was not modified.
- Previous `minecraft:anvil` failure layer: `CONTACT_GAP`; placement and survival were already present on the audited rows, but slab-supported rows had `contactGap=0.500000` on plain bottom support and `contactGap=1.000000` on lowered bottom support, with triad not co-located.
- New `minecraft:anvil` failure layer: `NONE`. The fix adds exact `Blocks.ANVIL` to the consolidated Beta 3.5 special-fullblock contact helper and to the separate lowered empty-raycast fallback helper. No all-special-fullblock, all-falling-block, grindstone, lectern, or global sturdy-face/solidity change was added.
- Focused proof `-Dslabbed.beta35AnvilContact=true` passes with `JULIA_BETA35_ANVIL_CONTACT_SUMMARY failureLayer=NONE`; slab-supported rows report `placementResult=Success`, `survivalResult=SURVIVAL_GREEN`, `objectDy=-1.000000` or `-1.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, `fallingBehavior=STABLE_ON_VALID_SUPPORT`, and co-located model/outline/raycast/collision bounds.
- Updated special-fullblock matrix: `minecraft:anvil`, `minecraft:stonecutter`, `minecraft:enchanting_table`, `minecraft:barrel`, `minecraft:chest`, `minecraft:bookshelf`, `minecraft:crafting_table`, and `minecraft:furnace` are GREEN representatives. Matrix summary now reports `rows=30 greenAlreadyInherits=24 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=1 outOfScopeForBeta35=0`.
- Remaining special-fullblock statuses are unchanged and honest: `minecraft:lectern` remains an interactive block-entity slice; `minecraft:grindstone` remains a special-shape contact slice. They were not fixed in this slice.
- Common-object matrix remains GREEN with unchanged summary: `rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0`.
- Validation passed: `compileJava compileGametestJava`, focused anvil proof, focused special-fullblock audit, focused common-object matrix, and default `runClientGameTest`. `git diff --check` pending final savepoint gate in this slice.
- Evidence folder: `tmp/beta35-anvil-contact-fix-9f3bacf`. See `docs/beta35-anvil-contact-fix.md`.
- Release remains paused. No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched. Canonical checkout was not modified.

## Beta 3.5 grindstone contact fix (2026-05-12)

- Operating base for this implementation slice: `805b070` / `save/beta35-anvil-contact` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on `work/beta35-special-fullblock-compat`. Canonical checkout was not modified.
- Previous `minecraft:grindstone` failure layer: `CONTACT_GAP`; placement and survival were already GREEN, but slab-supported floor-oriented rows had `contactGap=0.500000` on plain bottom support and `contactGap=1.000000` on lowered bottom support, with empty native raycast.
- New `minecraft:grindstone` failure layer: `NONE`. The fix adds exact `Blocks.GRINDSTONE` to the consolidated Beta 3.5 special-fullblock contact helper, the separate lowered empty-raycast fallback helper, and a grindstone-only collision fallback so the tested oriented shape stays co-located. No all-special-fullblock, all-oriented-block, lectern, or global sturdy-face/solidity change was added.
- Focused proof `-Dslabbed.beta35GrindstoneContact=true` passes with `JULIA_BETA35_GRINDSTONE_CONTACT_SUMMARY failureLayer=NONE`; tested rows keep `finalBlockState=Block{minecraft:grindstone}[face=floor,facing=south]`, and slab-supported rows report `placementResult=Success`, `survivalResult=SURVIVAL_GREEN`, `objectDy=-1.000000` or `-1.500000`, `contactGap=0.000000`, `triadCoLocated=yes`, and co-located model/outline/raycast/collision bounds.
- Updated special-fullblock matrix: `minecraft:grindstone`, `minecraft:anvil`, `minecraft:stonecutter`, `minecraft:enchanting_table`, `minecraft:barrel`, `minecraft:chest`, `minecraft:bookshelf`, `minecraft:crafting_table`, and `minecraft:furnace` are GREEN representatives. Matrix summary now reports `rows=30 greenAlreadyInherits=27 placementFailure=0 survivalFailure=0 contactGap=2 triadMismatch=0 blockEntityRisk=1 specialRendererRisk=0 needsCategorySlice=0 outOfScopeForBeta35=0`.
- Remaining special-fullblock status is unchanged and honest: `minecraft:lectern` remains an interactive block-entity contact slice. It was not fixed in this slice.
- Common-object matrix remains GREEN with unchanged summary: `rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0`.
- Evidence folder: `tmp/beta35-grindstone-contact-fix-805b070`. See `docs/beta35-grindstone-contact-fix.md`.
- Release remains paused. No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched. Canonical checkout was not modified.

## Beta 3.5 special fullblock compatibility integrated (2026-05-12)

- Branch `work/beta35-special-fullblock-compat` has been merged into canonical integration through `348ac65` / `save/beta35-grindstone-contact`.
- Current special-fullblock GREEN set: `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, `minecraft:stonecutter`, `minecraft:anvil`, and `minecraft:grindstone`.
- `minecraft:lectern` remains open / not fixed as an interactive block-entity contact slice.
- No release audit was run in this merge. No release tag was moved.

## Beta 3.5 fence family live RED (2026-05-12)

- Operating base for this proof/classification slice: `0ccbc7f` / `save/beta35-special-fullblock-compat-integrated` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-fence-family-worktree` on `work/beta35-fence-family-live-red`. Canonical checkout was not modified after worktree creation.
- New gated proof: `-Dslabbed.beta35FenceFamilyLiveRed=true`, markers `JULIA_BETA35_FENCE_FAMILY_LIVE_RED`, `JULIA_BETA35_FENCE_FAMILY_ROW`, and `JULIA_BETA35_FENCE_FAMILY_SUMMARY`.
- Result: RED, first failure layer `VARIANT_COVERAGE_GAP`. Summary: `rows=7 greenSimplifiedOnly=1 greenLiveLike=3 variantCoverageGap=3 oakFenceClassification=GREEN_LIVE_LIKE`.
- `minecraft:oak_fence` remains GREEN in the tested live-like configurations: isolated, one-neighbor, two-neighbor, and beside-lowered-fullblock; connected rows report `contactGap=0.000000`, correct connection properties, and co-located model/outline/raycast/collision bounds.
- `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall` place and survive but fail as `VARIANT_COVERAGE_GAP` with `contactGap=1.500000`, empty raycast bounds, and `triadCoLocated=no`.
- Previous `minecraft:oak_fence` green is valid simplified-only / live-superseded, not a fence-family release claim. No production behavior fix implemented. No release audit run. No release tag moved.
- Validation passed: `compileJava compileGametestJava`, focused fence-family live-red proof, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-fence-family-live-red-0ccbc7f`. See `docs/beta35-fence-family-live-red.md`.

## Beta 3.5 fence wall variant coverage fix (2026-05-12)

- Operating base for this implementation slice: `c570299` / `save/beta35-fence-family-live-red` in `/Users/joolmac/CascadeProjects/Slabbed-beta35-fence-family-worktree` on `work/beta35-fence-family-live-red`. Canonical checkout was not modified.
- Previous failure layer: `VARIANT_COVERAGE_GAP`. `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall` placed and survived but had `contactGap=1.500000`, empty raycast bounds, and `triadCoLocated=no`.
- New failure layer: `NONE`. The fix extends the existing `minecraft:oak_fence` lowered contact/raycast/collision treatment through an explicit named allowlist to `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall` only. It does not add pane support, all-fence/all-wall support, or a global solidity/sturdy-face change.
- Focused proof `-Dslabbed.beta35FenceWallVariantCoverage=true` is GREEN: `rows=16 greenLiveLike=15 greenSimplifiedOnly=1 contactGap=0 triadMismatch=0 collisionShapeRisk=0 connectionShapeRisk=0 placementFailure=0 survivalFailure=0 variantCoverageGap=0 failureLayer=NONE`.
- Tested configurations: `isolated`, `one_neighbor`, `two_neighbor`, and `beside_lowered_fullblock` for `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall`.
- Current green fence/wall set: `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall`. `minecraft:glass_pane` and panes remain out of scope / not covered.
- Fence-family live proof rerun is GREEN with no remaining variant coverage gap. Common-object matrix and default `runClientGameTest` passed.
- No release audit run. No release tag moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched.

## Beta 3.5 fence wall variant coverage integrated (2026-05-12)

- Canonical integration merged the proven fence-family branch from `/Users/joolmac/CascadeProjects/Slabbed-beta35-fence-family-worktree` at `f9995b6d91ace2d04d522c8c3850798e242adf27` / `save/beta35-fence-wall-variant-coverage` into `integrate/phase19-into-side-slab-top-support` at merge commit `7a5ab91`.
- The canonical integration result preserves the proven fence/wall coverage slice only: `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall` are GREEN; `minecraft:glass_pane` remains `NOT_COVERED`.
- Validation passed after merge: `compileJava compileGametestJava`, focused fence/wall variant proof, fence-family live proof, common-object matrix, default `runClientGameTest`, and `git diff --check`.
- Evidence folder: `tmp/beta35-fence-wall-variant-integration-merge-f9995b6`.
- No release audit was run. No release tag was moved. No pane support was implemented.

## Beta 3.5 fence false-green Opus audit (2026-05-12)

- Operating base: HEAD `8545b84` / `save/beta35-fence-wall-variant-coverage-integrated` on `integrate/phase19-into-side-slab-top-support`. Audit/classification slice only; no production gameplay code changed, no release audit run, no release tag moved.
- Contradiction: focused proof `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN ... contactGap=0 failureLayer=NONE`, but Julia's live/headless visual reads fences as "in no way shape or form fixed at all."
- Failure layer classification: `OBJECT_MODEL_BOTTOM_PROXY_GAP` in the proof harness, masking an underlying `MODEL_RENDER_GAP` in production. The fence/wall variant coverage GREEN claim is rescinded as a release artifact.
- False-green mechanism: `runBeta35FenceFamilyAuditRow` derives `objectModelBottomY` from `collisionShape` (or `outlineShape`), both forcibly offset by `SlabSupportStateMixin` for the four allowlisted variants. The visible client model bottom is sourced from `OffsetBlockStateModel.emitQuads`, which explicitly sets `dy = 0.0f` for every `FenceBlock | WallBlock | PaneBlock` (`src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java:117-127`). Shape proxy and rendered model are decoupled by design; the proof has no probe that fails when they diverge.
- Net visible behavior: collision/hitbox/raycast drop ~1.5 blocks for the four allowlisted variants; visible model stays at the original un-shifted Y because the Fabric `emitQuads` exclusion zeros dy for fences/walls/panes.
- Next RED proof: add a render-quad dy probe to `runBeta35FenceFamilyAuditRow` using existing `OffsetBlockStateModel.resetModelDyOwnerTrace` / `snapshotModelDyOwnerTrace` (or equivalent direct dy-policy assertion) and require `totalAppliedDy != 0` whenever `SlabSupport.getYOffset != 0`. This will go RED on current HEAD for `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall`.
- Recommendation: A then B — proof harness fix first (render-quad dy probe), then a model/render path fix that relaxes the blanket `FenceBlock | WallBlock | PaneBlock` exclusion in `OffsetBlockStateModel.emitQuads` for the allowlisted lowered fence/wall variants. Recommendations C, D, E are not the cheapest path.
- Release status: remains blocked. Fence/wall variant coverage must be re-proven via the new RED-first probe before re-entering a release slice. `minecraft:glass_pane` remains `NOT_COVERED`.
- Evidence folder: `tmp/beta35-fence-false-green-opus-audit-8545b84`. See `docs/beta35-fence-false-green-opus-audit.md`.

## Beta 3.5 fence model render RED (2026-05-12)

- Operating base: HEAD `a576fa1` / `save/beta35-fence-false-green-opus-audit` on `integrate/phase19-into-side-slab-top-support`. Proof-only slice; no production gameplay or render fix implemented, no release audit run, no release tag moved, pane support not added, `OffsetBlockStateModel` production behavior unchanged.
- New gated proof: `-Dslabbed.beta35FenceModelRenderRed=true`, markers `JULIA_BETA35_FENCE_MODEL_RENDER_RED`, `JULIA_BETA35_FENCE_MODEL_RENDER_ROW`, and `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY`. Tests `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, and `minecraft:cobblestone_wall` in the `isolated` configuration. `minecraft:glass_pane` remains `NOT_COVERED`.
- Probe mechanism: places the variant on the lowered bottom slab fixture, then calls `OffsetBlockStateModel.resetModelDyOwnerTrace(objectPos)` + `mc.worldRenderer.scheduleBlockRenders(...)`, waits ticks plus `waitForChunksRender`, and snapshots `OffsetBlockStateModel.snapshotModelDyOwnerTrace()`. The same row also captures the legacy `shapeContactGap` / `shapeTriadCoLocated` so the divergence is visible in one line.
- Result: RED. `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=RED rows=4 modelRenderGap=4 greenRenderDyApplied=0 modelDyTraceNotObserved=0 placementFailure=0 supportDyZero=0 oakFenceClassification=MODEL_RENDER_GAP spruceFenceClassification=MODEL_RENDER_GAP netherBrickFenceClassification=MODEL_RENDER_GAP cobblestoneWallClassification=MODEL_RENDER_GAP previousFailureLayer=OBJECT_MODEL_BOTTOM_PROXY_GAP failureLayer=MODEL_RENDER_GAP productionFixImplemented=false releaseAudit=NOT_RUN releaseTagMoved=false canonicalCheckoutModified=false`.
- All four rows report `supportDy=-1.000000`, `objectDy=-1.500000`, `expectedModelDy=-1.500000`, `emitCalls=6`, `appliedCalls=0`, `totalAppliedDy=0.000000`, `renderDyApplied=no`, `shapeContactGap=0.000000`, `shapeTriadCoLocated=yes` — the same false-green the shape proxy sees, alongside the new RED render-dy signal that the visible model never shifts.
- The prior `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN` is rescinded as a release artifact for the four allowlisted fence/wall variants. The shape-triad proof is necessary but not sufficient; it must be paired with the render-quad dy probe for any fence/wall/pane variant release claim.
- Next implementation slice: production render fix in `OffsetBlockStateModel.emitQuads` only. Replace the blanket `FenceBlock | WallBlock | PaneBlock` dy=0 exclusion with a conditional that re-applies dy when `SlabSupport.isBeta35FenceWallVariantContactObject(state)` and `SlabSupport.getYOffset(view, pos, state) != 0`; panes remain `NOT_COVERED`. No `SlabSupport.java` or `SlabSupportStateMixin.java` change is needed.
- Validation: `compileJava compileGametestJava` BUILD SUCCESSFUL; `-Dslabbed.beta35FenceModelRenderRed=true ./gradlew runClientGameTest` BUILD SUCCESSFUL with the RED markers emitted; `git diff --check` clean. Default suite not rerun (proof-only gated slice).
- Evidence folder: `tmp/beta35-fence-model-render-red-a576fa1`. See `docs/beta35-fence-model-render-red.md`.

## Beta 3.5 fence model render fix (2026-05-12)

- Operating base: HEAD `67ba365` / `save/beta35-fence-model-render-red` on `integrate/phase19-into-side-slab-top-support`. Production render fix implemented; no release audit run, no release tag moved, pane support not added.
- Fix: `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java` `emitQuads`. The blanket `FenceBlock | WallBlock | PaneBlock => dy = 0.0f` exclusion now has an inner guard `if (!SlabSupport.isBeta35FenceWallVariantContactObject(state))` so the four proven variants pass through their `SlabSupport.getYOffset` dy. All other `FenceBlock`, `WallBlock`, and `PaneBlock` instances remain at `dy = 0.0f`.
- Proof updated: `runBeta35FenceModelRenderRedProof` now emits `JULIA_BETA35_FENCE_MODEL_RENDER_GREEN` when outcome is GREEN, and records `productionFixImplemented=true` in the summary.
- Result: GREEN. `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=GREEN rows=4 modelRenderGap=0 greenRenderDyApplied=4 modelDyTraceNotObserved=0 placementFailure=0 supportDyZero=0 oakFenceClassification=GREEN_RENDER_DY_APPLIED spruceFenceClassification=GREEN_RENDER_DY_APPLIED netherBrickFenceClassification=GREEN_RENDER_DY_APPLIED cobblestoneWallClassification=GREEN_RENDER_DY_APPLIED glassPane=NOT_COVERED failureLayer=NONE productionFixImplemented=true releaseAudit=NOT_RUN`. All four rows: `emitCalls=6`, `appliedCalls=6`, `totalAppliedDy=-9.000000`, `actualModelAppliedDy=-1.500000`, `renderDyApplied=yes`.
- Validation: `compileJava compileGametestJava` BUILD SUCCESSFUL; `-Dslabbed.beta35FenceModelRenderRed=true` GREEN; `-Dslabbed.beta35FenceWallVariantCoverage=true` GREEN (`rows=16, variantCoverageGap=0`); `-Dslabbed.beta35FenceFamilyLiveRed=true` GREEN (`rows=7, variantCoverageGap=0`); `-Dslabbed.beta35CommonObjectCompatibilityAudit=true` GREEN (`rows=27, greenAlreadyInherits=27`); default suite BUILD SUCCESSFUL; `git diff --check` clean.
- Current proven fence/wall set (both shape-triad AND render-quad dy): `minecraft:oak_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, `minecraft:cobblestone_wall`. `minecraft:glass_pane` remains `NOT_COVERED`.
- See `docs/beta35-fence-model-render-fix.md`.

## Beta 3.5 live object coverage false-green (2026-05-12)

- Operating base: HEAD `a891ba6` / `save/beta35-fence-wall-model-render-fix` on `integrate/phase19-into-side-slab-top-support`. Docs-only audit; no production gameplay code changed; no release audit run; no release tag moved.
- Julia's live runClient at `gitHead=a891ba6` (single `[SLABBED-INSPECT][SESSION] startedAt=2026-05-12T18:49:25Z` marker) showed that holding `minecraft:birch_fence`, `minecraft:birch_trapdoor`, `minecraft:spruce_door`, `minecraft:birch_sign`, and `minecraft:anvil` still produces player-facing object-compatibility failure on lowered bottom-slab supports. All five held items recorded `warning=BOTTOM_SLAB_UNEXPECTED_DY` on slab targets; four of the five also recorded `warning=TOP_SLAB_WITH_UNSUPPORTED_NEGATIVE_DY`.
- Root cause classification (from `SlabSupport` allowlists and `OffsetBlockStateModel.emitQuads`):
  - `birch_fence` → `EXACT_BLOCK_ALLOWLIST_GAP` + `VARIANT_FAMILY_COVERAGE_GAP` + `FENCE_RENDER_VARIANT_GAP`. `isBeta35FenceWallVariantContactObject` covers only `OAK_FENCE`, `SPRUCE_FENCE`, `NETHER_BRICK_FENCE`, `COBBLESTONE_WALL`; render-quad gate forces `dy=0.0f` for non-allowlisted fence/wall/pane.
  - `birch_trapdoor` → `TRAPDOOR_VARIANT_GAP`. `isBeta35OakTrapdoorContactObject` covers only `OAK_TRAPDOOR[BLOCK_HALF=BOTTOM]`.
  - `spruce_door` → `DOOR_MULTIPART_VARIANT_GAP`. `isBeta35OakDoorContactObject` covers only `OAK_DOOR`.
  - `birch_sign` → `SIGN_RENDERER_GAP` + `VARIANT_FAMILY_COVERAGE_GAP`. `isBeta35StandingOakSignContactObject` covers only `OAK_SIGN`.
  - `anvil` → in `isBeta35SpecialFullblockContactObject`; possible `LIVE_PLACEMENT_TARGETING_GAP` (side-slab retarget fires regardless of held-item placement intent). Inspect-only evidence; needs a follow-up placement-event capture before classifying.
- Overall mechanism: `EXACT_BLOCK_ALLOWLIST_GAP` + `PROOF_HARNESS_UNDER_SCOPE`. The `a891ba6` fence model render fix is **valid for its declared four-variant allowlist** but the proof harness iterates only category representatives, so wood-variant red cases are never tested. The fix is not rescinded; its scope claim is.
- **Beta 3.5 release remains BLOCKED.** Current object compatibility proofs are under-scoped for live play. The next safe action is a sequence of single-variant RED proofs (`birch_fence`, then `birch_trapdoor`, then `spruce_door`, then `birch_sign`, then anvil placement-event capture), each followed by an allowlist expansion only after its own RED is in place. Do not bundle.
- Evidence folder: `tmp/beta35-live-object-coverage-false-green-a891ba6/`. See `docs/beta35-live-object-coverage-false-green.md`.

## Beta 3.5 birch_fence variant RED proof (2026-05-12)

- Operating base: HEAD `4f09773` / `save/beta35-live-object-coverage-gap` on `integrate/phase19-into-side-slab-top-support`. Proof/docs only; no production gameplay code changed; no release audit run; no release tag moved.
- New gated proof: `-Dslabbed.beta35BirchFenceVariantRed=true`, markers `JULIA_BETA35_BIRCH_FENCE_VARIANT_RED`, `JULIA_BETA35_BIRCH_FENCE_VARIANT_ROW`, `JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY`. Two rows: `minecraft:oak_fence` (control) and `minecraft:birch_fence` (live-tested variant).
- Result: RED. `JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=RED rows=2 greenAllowlisted=1 variantCoverageGap=1 modelRenderGap=0 placementFailure=0 oakFenceClassification=GREEN_ALLOWLISTED birchFenceClassification=VARIANT_FAMILY_COVERAGE_GAP failureLayer=VARIANT_FAMILY_COVERAGE_GAP productionFixImplemented=false releaseAudit=NOT_RUN releaseTagMoved=false canonicalCheckoutModified=false`.
- oak_fence control: `inFenceWallAllowlist=yes`, `supportDy=-1.000000`, `objectDy=-1.500000`, `expectedModelDy=-1.500000`, `actualModelAppliedDy=-1.500000`, `totalAppliedDy=-9.000000`, `renderDyApplied=yes`, `shapeContactGap=0.000000`, `shapeTriadCoLocated=yes`, `classification=GREEN_ALLOWLISTED`, `failureLayer=NONE`.
- birch_fence live variant: `inFenceWallAllowlist=no`, `supportDy=-1.000000`, `objectDy=-0.500000` (basic -0.5 offset only; no fence-allowlist -1.5), `expectedModelDy=-0.500000`, `actualModelAppliedDy=0.000000`, `totalAppliedDy=0.000000`, `renderDyApplied=no`, `shapeContactGap=1.500000`, `shapeTriadCoLocated=no`, `placementResult=Success`, `survivalResult=survived`, `classification=VARIANT_FAMILY_COVERAGE_GAP`, `failureLayer=VARIANT_FAMILY_COVERAGE_GAP`.
- Failure mechanism: `isBeta35FenceWallVariantContactObject` does not include `BIRCH_FENCE`; `beta35FenceWallVariantContactDy` returns NaN for birch_fence; `OffsetBlockStateModel.emitQuads` inner guard `!isBeta35FenceWallVariantContactObject(state)` is true → `dy = 0.0f` for rendered quads.
- `minecraft:birch_fence` places and survives (vanilla placement allowed) but renders and shape-behaves as if the lowered support does not exist, producing `shapeContactGap=1.5` and `renderDyApplied=no`. This is the live release-blocking gap Julia observed.
- The allowlist at `isBeta35FenceWallVariantContactObject` is too exact for wood fence families. The current four-variant set (`oak_fence`, `spruce_fence`, `nether_brick_fence`, `cobblestone_wall`) covers only those representatives; the wood-fence family requires at minimum the birch variant.
- **Beta 3.5 release remains BLOCKED.** No production fix implemented in this slice. No release tag moved. No release audit run.
- Next implementation slice: expand `isBeta35FenceWallVariantContactObject` to include `minecraft:birch_fence` (narrowly, not all wood fences) only after the RED proof is in place (this commit). See `docs/beta35-birch-fence-variant-red.md`.

## Beta 3.5 birch_fence variant fix (2026-05-12)

- Operating base: HEAD `77c11e0` / `save/beta35-birch-fence-variant-red` on `integrate/phase19-into-side-slab-top-support`.
- Production fix: `src/main/java/com/slabbed/util/SlabSupport.java` `isBeta35FenceWallVariantContactObject` — added `state.isOf(Blocks.BIRCH_FENCE)` to the explicit named allowlist alongside the existing four variants (`OAK_FENCE`, `SPRUCE_FENCE`, `NETHER_BRICK_FENCE`, `COBBLESTONE_WALL`). No other fence/wall/pane variants added. No all-wood-fence, all-FenceBlock, all-WallBlock, all-PaneBlock, or global solidity/sturdy-face changes.
- `OffsetBlockStateModel.emitQuads` was **not modified**; it already uses `SlabSupport.isBeta35FenceWallVariantContactObject(state)` as its render-quad guard — adding `BIRCH_FENCE` to the allowlist automatically passes through dy for `birch_fence` rendered quads.
- `SlabSupportStateMixin` was **not modified**; it already uses the same helper for outline/raycast/collision shape offset.
- Proof updated: `-Dslabbed.beta35BirchFenceVariantRed=true` now emits `JULIA_BETA35_BIRCH_FENCE_VARIANT_GREEN` when outcome is GREEN and records `productionFixImplemented=true`.
- Result: GREEN. `JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=GREEN rows=2 greenAllowlisted=2 variantCoverageGap=0 modelRenderGap=0 placementFailure=0 oakFenceClassification=GREEN_ALLOWLISTED birchFenceClassification=GREEN_ALLOWLISTED failureLayer=NONE productionFixImplemented=true releaseAudit=NOT_RUN releaseTagMoved=false canonicalCheckoutModified=false`.
- birch_fence row: `inFenceWallAllowlist=yes`, `supportDy=-1.000000`, `objectDy=-1.500000`, `expectedModelDy=-1.500000`, `actualModelAppliedDy=-1.500000`, `totalAppliedDy=-9.000000`, `renderDyApplied=yes`, `shapeContactGap=0.000000`, `shapeTriadCoLocated=yes`, `classification=GREEN_ALLOWLISTED`, `failureLayer=NONE`.
- Prior proofs unaffected: `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=GREEN`; `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN`; `JULIA_BETA35_FENCE_FAMILY_SUMMARY outcome=GREEN`; `JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=27 contactGap=0`.
- Current green fence/wall set (shape-triad + render-quad + birch_fence variant): `minecraft:oak_fence`, `minecraft:birch_fence`, `minecraft:spruce_fence`, `minecraft:nether_brick_fence`, `minecraft:cobblestone_wall`. `minecraft:glass_pane` remains `NOT_COVERED`. Other wood fences not yet covered.
- **Beta 3.5 release remains BLOCKED.** Remaining live failures from Julia's session: `birch_trapdoor`, `spruce_door`, `birch_sign`, and anvil placement-event capture. No release audit was run. No release tag was moved.
- Evidence folder: `tmp/beta35-birch-fence-variant-fix-77c11e0/`. See `docs/beta35-birch-fence-variant-fix.md`.

## Beta 3.5 fence/wall family fix (2026-05-12)

- Operating base: HEAD `25a6ef6` / `save/beta35-birch-fence-variant-fix` on `integrate/phase19-into-side-slab-top-support`.
- Production fix: `src/main/java/com/slabbed/util/SlabSupport.java` `isBeta35FenceWallVariantContactObject` — replaced exact-block named allowlist with `state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock`. `PaneBlock` remains explicitly excluded (returns false).
- `OffsetBlockStateModel.emitQuads` was **not modified**; the existing `!SlabSupport.isBeta35FenceWallVariantContactObject(state)` guard within the `FenceBlock | WallBlock | PaneBlock` branch automatically propagates the wider family rule to the render path.
- `SlabSupportStateMixin` was **not modified**; it already delegates to the same helper for collision/outline/raycast offset.
- New proof: `-Dslabbed.beta35FenceWallFamilyFix=true` — `runBeta35FenceWallFamilyFixProof` — 21 rows: 11 FenceBlock + 9 WallBlock + 1 PaneBlock control.
- Result: GREEN. `JULIA_BETA35_FENCE_WALL_FAMILY_SUMMARY outcome=GREEN rows=21 greenFamily=20 notCovered=1 modelRenderGap=0 shapeContactGap=0 placementFailure=0 glassPaneControl=NOT_COVERED failureLayer=NONE productionFixImplemented=true`.
- All FenceBlock variants GREEN_FAMILY: `oak_fence`, `birch_fence`, `jungle_fence`, `acacia_fence`, `dark_oak_fence`, `mangrove_fence`, `cherry_fence`, `bamboo_fence`, `crimson_fence`, `warped_fence`, `nether_brick_fence` — all `objectDy=-1.500000`, `renderDyApplied=yes`, `shapeContactGap=0.000000`, `shapeTriadCoLocated=yes`.
- All WallBlock variants GREEN_FAMILY: `cobblestone_wall`, `mossy_cobblestone_wall`, `stone_brick_wall`, `brick_wall`, `andesite_wall`, `granite_wall`, `diorite_wall`, `cobbled_deepslate_wall`, `polished_blackstone_brick_wall` — all `objectDy=-1.500000`, `renderDyApplied=yes`, `shapeContactGap=0.000000`, `shapeTriadCoLocated=yes`.
- `glass_pane` control: `inFenceWallFamily=no`, `renderDyApplied=no`, `shapeContactGap=1.500000`, `classification=NOT_COVERED`. Pane support not added.
- Prior proofs unaffected: `JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY outcome=GREEN`; `JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY outcome=GREEN`; `JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY outcome=GREEN`; `JULIA_BETA35_FENCE_FAMILY_SUMMARY outcome=GREEN`; `JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=27 contactGap=0`.
- **Beta 3.5 release remains BLOCKED.** Remaining live failures: `birch_trapdoor`, `spruce_door`, `birch_sign`, and anvil placement-event capture. No release audit was run. No release tag was moved.
- Evidence folder: `tmp/beta35-fence-wall-family-fix-25a6ef6/`. See `docs/beta35-fence-wall-family-fix.md`.

## Beta 3.5 live hitbox/gate RED audit (2026-05-12)

- Operating base: HEAD `edbba27` / `save/beta35-fence-wall-family-fix` on `integrate/phase19-into-side-slab-top-support`. Proof/docs only; no production gameplay/render fix implemented; no release audit run; no release tag moved.
- New gated audit: `-Dslabbed.beta35LiveHitboxGateRed=true`; markers `JULIA_BETA35_LIVE_HITBOX_GATE_MATRIX_START`, `JULIA_BETA35_LIVE_HITBOX_GATE_ROW`, and `JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY`.
- Result: RED. `JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY outcome=RED rows=5 red=2 pending=3 green=0 fenceHitboxFailureLayer=PROOF_HARNESS_GAP wallHitboxFailureLayer=PROOF_HARNESS_GAP anvilHitboxFailureLayer=PROOF_HARNESS_GAP fenceGateClosedFailureLayer=FENCE_GATE_CONTACT_GAP fenceGateOpenFailureLayer=FENCE_GATE_CONTACT_GAP`.
- Connected `cherry_fence` and `stone_brick_wall` rows stayed shape/math co-located (`supportDy=-1.000000`, `objectDy=-1.500000`, `contactGap=0.000000`, `collisionCoLocated=yes`, `triadCoLocated=yes`) but Julia's live hitbox/collision feel remains unclosed: `PENDING` / `PROOF_HARNESS_GAP`.
- `minecraft:anvil` stayed contact/triad/collision co-located in the bounded proof (`objectDy=-1.500000`, `contactGap=0.000000`, `collisionCoLocated=yes`, `triadCoLocated=yes`) but this does not close live hitbox suspicion: `PENDING` / `PROOF_HARNESS_GAP`.
- `minecraft:cherry_fence_gate` is a separate category, not fixed by `FenceBlock`/`WallBlock` family law. Closed and open rows place/survive, but both have `objectDy=-0.500000` over `supportDy=-1.000000`, expected `-1.500000`, `contactGap=1.000000`: `FENCE_GATE_CONTACT_GAP`.
- **Beta 3.5 release remains BLOCKED.** Next safe implementation slice: fence-gate-specific contact/support law with closed/open proof. Keep fence/wall and anvil live-hitbox reproduction as a separate proof-harness slice unless Julia supplies sharper live evidence.
- Evidence folder: `tmp/beta35-live-hitbox-gate-red-edbba27/`. See `docs/beta35-live-hitbox-gate-red.md`.

## Beta 3.5 cherry fence gate contact fix (2026-05-12)

- Operating base: HEAD `0916b36` / `save/beta35-live-hitbox-gate-red` on `integrate/phase19-into-side-slab-top-support`. Implementation/proof/docs slice only for `minecraft:cherry_fence_gate`; no global `FenceGateBlock` family support, pane support, fence/wall/anvil hitbox fix, door/trapdoor/sign/lantern/chain/end-rod/redstone/rail work, release audit, release metadata edit, or release tag movement.
- Production behavior change: `SlabSupport` now has a cherry-fence-gate-only contact path that uses the fence/wall contact dy math on lowered bottom slab support (`supportDy=-1.000000` -> `objectDy=-1.500000`). `SlabSupportStateMixin` includes the same cherry-gate predicate for lowered shape offset and empty-raycast fallback. `OffsetBlockStateModel` was not modified; render dy was already applied once the object dy became correct.
- Focused proof: `-Dslabbed.beta35FenceGateContact=true`; markers `JULIA_BETA35_FENCE_GATE_CONTACT_GREEN`, `JULIA_BETA35_FENCE_GATE_CONTACT_ROW`, and `JULIA_BETA35_FENCE_GATE_CONTACT_SUMMARY`.
- Result: GREEN. `JULIA_BETA35_FENCE_GATE_CONTACT_SUMMARY outcome=GREEN failureLayer=NONE rows=2 closedClassification=GREEN closedFailureLayer=NONE openClassification=GREEN openFailureLayer=NONE scope=cherry_fence_gate_only fenceWallHitboxRows=UNCHANGED_PENDING anvilHitboxRows=UNCHANGED_PENDING panes=NOT_COVERED releaseAudit=NOT_RUN releaseTagMoved=false`.
- Closed row: `supportDy=-1.000000`, `objectDy=-1.500000`, `expectedContactDy=-1.500000`, `contactGap=0.000000`, `collisionCoLocated=category_valid_closed_tall`, `triadCoLocated=yes`, `placementResult=PLACEMENT_GREEN`, `survivalResult=SURVIVAL_GREEN`, `classification=GREEN`, `failureLayer=NONE`.
- Open row: `supportDy=-1.000000`, `objectDy=-1.500000`, `expectedContactDy=-1.500000`, `contactGap=0.000000`, `collisionCoLocated=category_valid_open_empty`, `triadCoLocated=yes`, `placementResult=PLACEMENT_GREEN`, `survivalResult=SURVIVAL_GREEN`, `interactionResult=Success[...]`, `classification=GREEN`, `failureLayer=NONE`.
- Live-hitbox/gate audit after the fix: `JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY outcome=PENDING rows=5 red=0 pending=3 green=2 ... fenceGateClosedClassification=GREEN ... fenceGateOpenClassification=GREEN ...`. Fence/wall/anvil hitbox rows remain `PENDING` / `PROOF_HARNESS_GAP`; they were not fixed in this slice.
- Validation passed: `compileJava compileGametestJava`, focused cherry fence gate contact proof, live-hitbox/gate audit, fence/wall family proof, common-object matrix, default `runClientGameTest`, and `git diff --check`.
- **Beta 3.5 release remains BLOCKED.** Next safe action: fence/wall/anvil true hitbox/collision proof-harness slice. Evidence folder: `tmp/beta35-fence-gate-contact-fix-0916b36/`. See `docs/beta35-fence-gate-contact-fix.md`.

## Beta 3.5 fence gate family fix (2026-05-12)

- Operating base: HEAD `57e6c95` / `save/beta35-cherry-fence-gate-contact` on `integrate/phase19-into-side-slab-top-support`. Implementation/proof/docs slice only for vanilla `FenceGateBlock` family support; no door/trapdoor/sign/pane support, fence/wall/anvil hitbox fix, release audit, release metadata edit, or release tag movement.
- Production behavior change: `SlabSupport` now treats `state.getBlock() instanceof FenceGateBlock` as the Beta 3.5 fence-gate contact object. `SlabSupportStateMixin` delegates to the same family helper for lowered shape/raycast/collision handling. `OffsetBlockStateModel` was not modified.
- Harness repair: removed the brittle post-row render wait that timed out in the 22-row matrix; the model trace still performs targeted render scheduling/wait. Gate proof rows now stabilize the proof player before placement and interaction to prevent long-matrix drift.
- Focused proof: `-Dslabbed.beta35FenceGateFamilyFix=true`; markers `JULIA_BETA35_FENCE_GATE_FAMILY_GREEN`, `JULIA_BETA35_FENCE_GATE_FAMILY_ROW`, and `JULIA_BETA35_FENCE_GATE_FAMILY_SUMMARY`.
- Result: GREEN. `JULIA_BETA35_FENCE_GATE_FAMILY_SUMMARY outcome=GREEN failureLayer=NONE variants=11 rows=22 greenRows=22 closedGreen=11 openGreen=11 interactionRepresentatives=3 interactionRepresentativeGreen=3 scope=FenceGateBlock_family fenceWallHitboxRows=UNCHANGED_PENDING anvilHitboxRows=UNCHANGED_PENDING panes=NOT_COVERED releaseAudit=NOT_RUN releaseTagMoved=false failedRows=none`.
- Tested variants: `oak_fence_gate`, `spruce_fence_gate`, `birch_fence_gate`, `jungle_fence_gate`, `acacia_fence_gate`, `dark_oak_fence_gate`, `mangrove_fence_gate`, `cherry_fence_gate`, `bamboo_fence_gate`, `crimson_fence_gate`, and `warped_fence_gate`.
- Closed/open rows: all GREEN with `contactGap=0.000000`, `triadCoLocated=yes`; closed collision is `category_valid_closed_tall`, open collision is `category_valid_open_empty`. Open/close interaction representatives `oak_fence_gate`, `cherry_fence_gate`, and `crimson_fence_gate` are GREEN.
- Validation passed: `compileJava compileGametestJava`, focused fence gate family proof, cherry gate regression proof, live-hitbox/gate audit, fence/wall family proof, common-object matrix, default `runClientGameTest`, and `git diff --check`.
- **Beta 3.5 release remains BLOCKED.** Fence/wall/anvil hitbox rows remain `PENDING` / `PROOF_HARNESS_GAP`; panes remain `NOT_COVERED`. Evidence folder: `tmp/beta35-fence-gate-family-fix-57e6c95/`. See `docs/beta35-fence-gate-family-fix.md`.
