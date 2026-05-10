# Beta4 Compound Slab Merge/Remap Grammar

## Problem

Beta 4 gates passed, but the live/product feel is paused.

The compound ordinary full-block lane at `dy=-1.0` is proven and useful. Slab-held attempts currently reject cleanly by design, which is safe but now feels too restrictive for a mod called Slabbed. The question is not "can we place a slab somehow?" but "what legal stable state should slab placement normalize into?"

Terminology note: `Row 3` in this document means the internal proof-row name in the focused gametest harness. It does not refer to Julia's in-world sign labels `ROW 1` / `ROW 2` / `ROW 3` / `ROW 4` from the manual screenshot.

## Contract sentence

Compound slab placement should resolve into the nearest legal stable slab result.

## Legal target states

Beta 4 may resolve compound slab interaction into the following legal results only:

- vanilla slab states at `dy=0`
- existing lowered slab lane states at `dy=-0.5`
  - `BOTTOM` `dy=-0.5`
  - `TOP` `dy=-0.5`
  - `DOUBLE` `dy=-0.5`
  - compatible side-lane continuation at `dy=-0.5`
  - merge/completion into `DOUBLE` `dy=-0.5` when proof-covered
- compound ordinary full-block lane at `dy=-1.0`
- ordinary full blocks may remain/use compound `dy=-1.0` lane
- named compound visible slab lane states at `dy=-1.0`, only when tied to an
  authored/persistent compound full-block owner

## Non-legal states for Beta 4

Beta 4 does not allow:

- `dy=-1.0` slab states are illegal unless they are one of the named compound
  visible slab lane states.
- no `dy<-1.0`
- no normal-lane `dy=0` slab produced from valid lowered-lane interaction
- no slab state that only works because rescue/retarget fixes it later
- no broad solidity or hitbox lies

## Product law correction: compound visible slab lane

Manual delayed trace at `d7ef534` proves the earlier Beta4 slab law is
insufficient for Julia's live expectation. A compound full block at block
position Y with `dy=-1.0` visually spans Y-1.0 to Y. A side slab authored as
`stone_slab[type=bottom]` at the side candidate with `dy=-0.5` spans only
Y-0.5 to Y, so it represents the upper half of the visible full block. It
cannot be the lower-half side placement Julia expects.

The earlier owner-top result is also visually wrong: `stone_slab[type=bottom]`
at `source.up()` with `dy=0.0` spans Y+1.0 to Y+1.5 in block coordinates for
that candidate, leaving it visually floating above the `dy=-1.0` source instead
of sitting on the source's visible top at Y.

The corrected product law promotes one narrow legal family:
`COMPOUND_VISIBLE_SLAB_LANE`. It is bounded and source-owned. These states are
legal only when the clicked/source owner is an authored or persistent compound
ordinary full block at `dy=-1.0`:

| Legal state | Candidate | Final state | Final dy | Meaning |
| --- | --- | --- | --- | --- |
| `COMPOUND_VISIBLE_SIDE_LOWER_SLAB` | `source.offset(horizontalFace)` | `stone_slab[type=bottom]` | `-1.0` | spans Y-1.0 to Y-0.5, aligned to the owner's lower visible half |
| `COMPOUND_VISIBLE_SIDE_UPPER_SLAB` | `source.offset(horizontalFace)` | `stone_slab[type=top]` | `-1.0` | spans Y-0.5 to Y, aligned to the owner's upper visible half |
| `COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB` | same side cell | `stone_slab[type=double]` | `-1.0` | produced only by merging compatible lower/upper compound visible side slabs |
| `COMPOUND_VISIBLE_OWNER_TOP_SLAB` | `source.up()` | `stone_slab[type=bottom]` | `-1.0` | spans Y to Y+0.5, sitting on the owner's visible top |

This supersedes the earlier assumption that `dy=-1.0` slab states are
categorically illegal. The replacement rule is: `dy=-1.0` slab states are
illegal unless they are one of the named compound visible slab lane states.

Explicitly non-legal:

- recursive `dy<-1.0`
- arbitrary freeform `dy=-1.0` slab chains
- `dy=-1.0` slabs not tied to a compound full-block owner/source
- using rescue or retarget to fake legality
- global slab solidity lies
- replacing the existing `dy=-0.5` lowered slab lane law globally

## Player-facing behavior table

| Player action | Required Beta 4 resolution |
| --- | --- |
| full block side-click on compound full block | keep current legal compound full-block behavior, `dy=-1.0` |
| full block top-click on compound full block | same compound lane `dy=-1.0`, not `dy=-1.5` |
| slab lower-half side-click on authored/persistent compound full block at `dy=-1.0` | author `COMPOUND_VISIBLE_SIDE_LOWER_SLAB` at the immediate side candidate as `stone_slab[type=bottom]`, `dy=-1.0` |
| slab upper-half side-click on authored/persistent compound full block at `dy=-1.0` | author `COMPOUND_VISIBLE_SIDE_UPPER_SLAB` at the immediate side candidate as `stone_slab[type=top]`, `dy=-1.0` |
| second compatible side slab click in the same compound visible side cell | merge into `COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB`, `stone_slab[type=double]`, `dy=-1.0` |
| slab side/top-click on persistent visible compound owner after direct below support is missing | still legal only through the same source-owned compound visible slab lane states while the source remains anchored, compound, visible, and `dy=-1.0` |
| slab side-click where existing legal `dy=-0.5` lowered slab lane can be continued | remap into that `dy=-0.5` legal lane |
| second slab click against compatible lowered slab | merge `BOTTOM`/`TOP` into `DOUBLE` `dy=-0.5` when proof-covered |
| slab top-click on persistent visible compound owner | author `COMPOUND_VISIBLE_OWNER_TOP_SLAB`: candidate `source.up()`, `stone_slab[type=bottom]`, `dy=-1.0`, tied to the source owner |
| source/support break | no jump, no ghost, no silent renormalization |
| reload/rejoin | legal states survive; illegal/unproven states must not appear |

Priority rule: an existing legal horizontal `dy=-0.5` continuation lane wins
before compound visible side classification. The source-owned `dy=-1.0` visible
slab lane applies only when no eligible horizontal lowered slab lane should own
the click.

## Product decision: compound below-lane side slab placement

Superseded by the compound visible slab lane correction above for authored or
persistent compound full-block owners at `dy=-1.0`. The historical
below-lane side law remains useful context for the old A-prime path, but it no
longer satisfies Julia's live side-lower, side-upper, repeat, or top-face
expectations for the visible compound owner.

The discriminator audit proved that below-lane-only is unsafe as a screenshot-only discriminator: Julia's screenshot side-shape and Rows 1/2 have the same placement facts available to current authority. The product contract therefore promotes that whole class into a named legal placement instead of trying to split it with a nonexistent discriminator.

If a compound `dy=-1.0` ordinary full-block source has a legal lowered slab lane/support directly below it, a held-slab side click may author the immediate side candidate into the existing legal `dy=-0.5` lowered slab grammar. The candidate side slab may become `BOTTOM` or `TOP` at `dy=-0.5` according to that existing lowered slab grammar.

If that direct below support is later missing, the same side placement law may still apply only for a persistent visible compound owner: the clicked source itself must remain an anchored ordinary full block, carry the compound full-block sidecar, and still report `dy=-1.0`. This legal class is `COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB`; it does not make unsupported air a source and does not allow slab authoring from a missing or non-compound owner.

This is allowed because the result is not a compound slab lane. It is a remap into existing lowered slab grammar at `dy=-0.5`.

## Product decision: compound visible owner top slab placement

Superseded by the compound visible slab lane correction above. The named result
remains `COMPOUND_VISIBLE_OWNER_TOP_SLAB`, but the corrected legal dy is
`-1.0`, not `0.0`.

If a held-slab click hits the UP face of a persistent visible compound owner, the legal result is named `COMPOUND_VISIBLE_OWNER_TOP_SLAB`. The source must remain an anchored ordinary full block carrying the compound full-block sidecar, and the candidate is exactly `source.up()`.

The authored candidate is vanilla-height slab law, not lowered side-lane law:

- final state: `stone_slab[type=bottom]`
- final dy: `-1.0` under corrected compound visible slab lane law
- `persistentLoweredSlabCarrier=false`
- durable distinguisher: the candidate carries
  `SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE`, written only after
  the pre-placement source truth names `COMPOUND_VISIBLE_OWNER_TOP_SLAB`

The dynamic dy path that previously broke this law was `SlabSupport.getYOffsetInner(...)`'s bottom-slab lowered branch through `hasLoweredCarrierBelow(world, pos)`: a bottom slab directly above an anchored compound full block was being treated as a lowered carrier continuation and resolved to `dy=-0.5`. `COMPOUND_VISIBLE_OWNER_TOP_SLAB` now returns `dy=-1.0` only through its persisted/synced owner-top marker, while unmarked bottom slabs remain under the older lowered-lane rules.

The forbidden results remain unchanged:

- no slab state at `dy=-1.0` unless it is a named compound visible slab lane state
- no slab state at `dy<-1.0`
- no `dy=0` slab from a lowered-lane interaction
- no rescue/retarget workaround
- no broad solidity lie

Rows 1/2 and Julia's screenshot side-shape are now intentionally unified as the same legal class: compound below-lane side slab placement. Rows 1/2 are expected RED/PENDING until implementation; their previous safe-reject GREEN status is superseded by this product decision.

## Owner/targeting policy

- If no legal slab placement result exists, compound full block selection should remain preferred over rescue-created slab intent.
- If a legal visible `dy=-0.5` slab lane exists and the player is clearly clicking that lane, the slab lane may win.
- Retargeting must not invent placement legality.
- Targeting, outline, model, placement, survival, and live feel must agree.

## RED proof matrix before implementation

| # | Proof row | Expected initial RED behavior | Intended GREEN result | Forbidden false-green |
| --- | --- | --- | --- | --- |
| 1 | `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_LOWER_RED]` lower side click on compound `dy=-1.0` full block | current implementation authors `stone_slab[type=bottom]` at `dy=-0.5` or misses the exact lower lane | candidate side cell becomes `stone_slab[type=bottom]`, `dy=-1.0`, visually aligned to the source lower half, with no `dy<-1.0` | `dy=-0.5` upper-half surrogate, rescue-created legality, or freeform chain |
| 2 | `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_UPPER_RED]` upper side click | current implementation can author the upper-looking `dy=-0.5` result but not the corrected source-owned lane | candidate side cell becomes `stone_slab[type=top]`, `dy=-1.0`, visually aligned to the source upper half | hidden retarget workaround or global slab solidity lie |
| 3 | `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MERGE_RED]` lower + upper in same side cell | repeat behavior is still a symptom of the wrong lane model | compatible lower/upper compound visible side slabs merge to `stone_slab[type=double]`, `dy=-1.0`, durable server/client final state, no ghost | client-only prediction, `dy=-0.5` same-cell merge standing in for the visible lane |
| 4 | `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TOP_RED]` top-face click on compound owner | previous `dy=0.0` owner-top result is visually floating/invalid | `source.up()` becomes `stone_slab[type=bottom]`, `dy=-1.0`, visually sitting on top of source, no floating dy=0 ghost | `dy=0.0`, `dy=-0.5`, or top-face ghost |
| 5 | `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SUPPORT_MISSING_RED]` support removed but compound full block persists | current side/top support-missing confidence used the old lane law | side/top placement still works through compound owner truth and produces only the same legal named lane states | support under air as a source, jump rescue, or ownerless slab lane |
| 6 | `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRIAD_RED]` model/outline/raycast parity | prior automation/goblin proofs are superseded for release confidence | model, outline, and raycast agree for each named `dy=-1.0` slab result | rescue-only correctness or model-only false-green |
| 7 | `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_RED]` reload/rejoin or explicit save/reload | illegal or unproven state may reappear | named states persist correctly after reload/rejoin or explicit save/reload proof | save/load hiding an illegal or ownerless lane |

## Proof status

Current implementation has the first four named corrected states implemented:
`COMPOUND_VISIBLE_SIDE_LOWER_SLAB`, `COMPOUND_VISIBLE_SIDE_UPPER_SLAB`,
`COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB`, and `COMPOUND_VISIBLE_OWNER_TOP_SLAB`.
They are backed by
`SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE` and
`SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE`, plus
`SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE` for the merge
state and `SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE` for the
top state. The sidecar markers are persisted/synced and written only for the
immediate side candidate of an authored or persistent compound full-block owner
at `dy=-1.0`. Lower and upper require final state
`stone_slab[type=bottom]` or `stone_slab[type=top]`; double requires an existing
marked lower/upper visible side candidate in that same source-owned side cell
and final state `stone_slab[type=double]`. Owner top requires the exact
`source.up()` candidate and final state `stone_slab[type=bottom]`.
`SlabSupport.getYOffsetInner(...)` returns `dy=-1.0` for those marked slabs only,
before the legacy `dy=-0.5` lowered-lane rules. The old `dy=0.0` owner-top ghost
result is not accepted as green.
The historical proof rows below remain evidence for the older law, but they are
superseded for release confidence by the marker matrix above.

The marker matrix is executable as an opt-in client gametest diagnostic:

```bash
JAVA_TOOL_OPTIONS="-Dslabbed.beta4CompoundVisibleSlabLaneRed=true -Dfabric.client.gametest.disableNetworkSynchronizer=true" ./gradlew --no-daemon runClientGameTest --console plain
```

The run must first emit
`[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_FIXTURE_GREEN]`. The expected current
summary is `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SUMMARY] fixtureTruth=GREEN
lower=GREEN upper=GREEN merge=GREEN top=GREEN supportMissing=GREEN triad=PARTIAL
modelAuthority=GREEN reload=GREEN releaseBlockers=JuliaLiveRetest` once model
dy authority is proven. Any unmarked `dy=-0.5` or `dy=0.0` slab result is
logged as observed state, not accepted as green.

Latest triad proof outcome:
`[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRIAD_CASE]` now records lower, upper,
merge, and top separately. All four named states prove marker/type truth,
`dy=-1.0`, shifted outline bounds, owner raycast/target ownership, and model dy
authority at the visible body. The model authority proof uses the same
`SlabSupport.getYOffset(...)` path as `OffsetBlockStateModel.emitQuads(...)`
through a render-region-style non-`World` `BlockView`, so model, outline, and
raycast share the same marker source. It is still not a Julia manual visual
claim, so the triad remains `PARTIAL` until visual alignment is confirmed.

Latest lower/upper/double proof result:
`[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_LOWER_GREEN]` reports the candidate at
`source.offset(horizontalFace)` as `stone_slab[type=bottom]`, `dy=-1.0`, with
the clicked source still a compound full block at `dy=-1.0` and no recursive
`dy<-1.0`. `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_UPPER_GREEN]` reports the
same source-owned candidate relation as `stone_slab[type=top]`, `dy=-1.0`,
backed by `COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE`.
`[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MERGE_GREEN]` reports the same side
candidate merging from a marked lower/upper visible side slab to
`stone_slab[type=double]`, `dy=-1.0`, backed by
`COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE`. The source remains the compound full
block at `dy=-1.0`, no `dy<-1.0` result is accepted, and the old `dy=-0.5`
lowered same-cell merge remains a separate `LOWERED_SAME_CELL_SLAB_MERGE`
class. `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TOP_GREEN]` reports
`source.up()` as `stone_slab[type=bottom]`, `dy=-1.0`, backed by
`COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE`; both server and client report
`compoundVisibleOwnerTopSlab=true`. Support-missing turned GREEN naturally
through the same bounded source-owned states.

Support-missing proof now explicitly builds all four named states, removes the
direct support under the authored compound full block, and requires the source
to remain `stone`, `dy=-1.0`, and `compoundFullBlockAnchor=true` with no
jump/pop. The lower side bottom, upper side top, side double, and owner-top
bottom states must still resolve to `dy=-1.0`, and no checked state may report
`dy<-1.0`.

Triad is `PARTIAL`, not release-green: the harness proves dy, outline minY,
owner target/raycast ownership, and model authority for the four named states,
but Julia's manual visual retest is still required. The live triad harness now
logs `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MODEL_AUTHORITY_GREEN]` with
lower, upper, double, and top `modelDy=-1.0` and
`proofMethod=renderRegionStyleBlockViewBridge`; per-state
`[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MODEL_AUTHORITY_CASE]` markers record
the same dy and marker truth from the non-`World` render-view bridge. Reload is
now `GREEN` through
`TestWorldSave.open()`: `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_BEFORE]`
captures lower, upper, double, and owner-top as marked `dy=-1.0` slab states
before close/save, `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_AFTER]`
captures the same state, marker, and dy after open, and
`[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_GREEN]` reports
`markerSourceTruthSurvived=true`, `namedStatesSurvived=true`,
`noCollapseToDyZeroOrMinusHalf=true`, and `noDyBelowMinusOne=true`. The source
owner remains `stone`, `dy=-1.0`, and `compoundFullBlockAnchor=true`.
Immediate placement plus reload green is still not enough for release because
model/manual visual proof and Julia manual live retest remain blocking.

## Immediate render refresh requirement

Manual live after `e5492d0` proved that compound-visible marker truth and
model dy authority can exist while the client chunk model remains visually stale
until a support block replacement forces a rebuild. This is classified as
`D. RERENDER_NOT_SCHEDULED`, not a placement-law or legal-dy failure.

For every `COMPOUND_VISIBLE_*` marker add/remove/change, the client must refresh
the immediate marker position and nearby affected positions without relying on a
support-toggle workaround. The visible slab body, outline, raycast/target owner,
and model must all agree immediately after placement: lower, upper, double, and
top remain source-owned `dy=-1.0` states, and unmarked `dy=-0.5`/`dy=0.0`
fallbacks are not accepted as green for this lane.

Current render-refresh slice keeps the existing marker sync/render-view bridge
and adds a forced one-block-neighborhood client `scheduleBlockRenders(...)`
refresh for the four compound-visible attachment types. Gated trace
`-Dslabbed.beta4CompoundVisibleRenderTrace=true` emits marker set, client sync,
model dy, rerender, support update, and summary records; the focused proof
requires `clientMarker=true`, `modelDy=-1.0`, and rerender scheduled for the
candidate/neighborhood. Release remains blocked until Julia manually confirms
that the model and outline align immediately in live play.

Diagnostic conflict classification after the render-refresh WIP is
`A. STALE_DIAGNOSTIC_EXPECTATION`: the old live-shape lower-after-first and
repeat-placement sequence expected `stone_slab[type=bottom/double] dy=-0.5`.
Those markers are historical evidence for the pre-lane model and are superseded
where the current `COMPOUND_VISIBLE_SLAB_LANE` proof is green. The current
release blocker for this lane remains the visible model/manual retest gate, not
the old lowered-same-cell sequence.

## Compatibility audit: old Row 1

Decision: **A, old Row 1 superseded**.

| Fact | Old Row 1 | Visible upper proof | Same case? |
| --- | --- | --- | --- |
| Source | ordinary `stone`, authored/persistent compound full-block owner, `dy=-1.0` | same source kind, `dy=-1.0` | yes |
| Face | horizontal side (`EAST`) | horizontal side | yes |
| Hit band | hit Y `sourceY - 0.25`; visible local Y `0.75` because source dy is `-1.0` | visible local Y `0.75` | yes |
| Candidate | `source.offset(horizontalFace)` | `source.offset(horizontalFace)` | yes |
| Old expectation | `stone_slab[type=bottom] dy=-0.5` | rejected by corrected law as upper-half surrogate | superseded |
| Corrected expectation | `COMPOUND_VISIBLE_SIDE_UPPER_SLAB`, `stone_slab[type=top] dy=-1.0` | same | yes |
| Proof action | emit `[JULIA_BETA4_COMPOUND_OLD_ROW_VISIBLE_UPPER_SUPERSEDED_GREEN]` | keep `[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_UPPER_GREEN]` | yes |

The old Row 1 label was stale: it described a block-coordinate lower hit under
the old lowered-lane model, not a lower-band hit in the source's corrected
visible body. This does not erase the `dy=-0.5` lowered slab lane; rows whose
hit lies outside the named compound visible slab bands still use the old
below-lane grammar unless a separate proof supersedes them.

Follow-up validation kept old Row 3 as a separate legal continuation case. Row 3
has exactly one horizontal legal `dy=-0.5` slab lane in the clicked direction
(`legalLaneCount=1`) and expects continuation at `source.offset(face).offset(face)`.
`SlabSupport.findLegalCompoundSlabRemap(...)` now gives that
`COMPOUND_HORIZONTAL_CONTINUATION_LANE` decision priority before compound visible
side classification, so Row 3 remains `stone_slab[type=bottom] dy=-0.5` without
weakening the source-owned `dy=-1.0` bounds.

These proof rows exist in the focused harness slice under the superseded law:

- Row 1: GREEN-implemented/proven for compound below-lane side slab placement; lower-half side click authors `stone_slab[type=bottom]` at `dy=-0.5`.
- Row 2: GREEN-implemented/proven for compound below-lane side slab placement; upper-half side click authors `stone_slab[type=top]` at `dy=-0.5`.
- Internal proof Row 3: GREEN-implemented/proven for the narrow artificial same-Y remap topology; automated/focused proof passed and runtime/live-launch logs emitted GREEN.
- Julia screenshot side-shape upper-half side slab: GREEN/proven after Julia's `6d0d525` live retest; the side candidate becomes `stone_slab[type=top]` at `dy=-0.5`.
- Julia screenshot side-shape lower-half side slab: RED after Julia's `6d0d525` live retest; lower-band side click flickers/fails or does not author the expected legal `stone_slab[type=bottom]` at `dy=-0.5`.
- Julia screenshot/goblin top-face: RED after Julia's c956fa3 live retest; the prior automated clean-reject/preserve GREEN is superseded because the fixture did not prove the actual slab/full-block live structure.
- Row 4: TODO.
- Row 5: GREEN for clean reject/preserve on the canonical live-shape top-face path; no legal authored top-face slab result is defined.
- Row 6: TODO.

Harness/source-truth repair note:

- The failed Row 3 implementation attempt exposed a proof topology gap: Row 1 could report against an authored `dy=-0.5` lowered slab at the side lane instead of proving the clicked source was the compound ordinary full block.
- Before any slab click/remap assertion, Rows 1-3 now prove the clicked source is ordinary stone, `compoundFullBlockAnchor=true`, and `dy=-1.0`.
- Rows 1-2 require zero neighboring horizontal legal `dy=-0.5` slab lanes, and their legal lowered support directly below the compound source now remaps the immediate side candidate into the existing `dy=-0.5` lowered slab grammar.
- Row 3 requires exactly one legal adjacent `dy=-0.5` slab lane in the intended remap direction. The implemented path now remaps to the continuation cell beyond that lane and authors a legal lowered slab lane at `dy=-0.5`.
- The Row 1/2 and Row 3 implementations keep the clicked source as ordinary stone at compound `dy=-1.0` and do not legalize slab type + `dy=-1.0`.

Current proof markers emitted by the gated gametest slice:

- `[JULIA_BETA4_COMPOUND_BELOW_LANE_SIDE_SLAB_PENDING]`
- `[JULIA_BETA4_COMPOUND_BELOW_LANE_SIDE_SLAB_GREEN]`
- `[JULIA_BETA4_COMPOUND_SLAB_HARNESS_SOURCE_GREEN]`
- `[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_GREEN]`
- `[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_PENDING]`
- `[JULIA_BETA4_COMPOUND_SLAB_DOUBLE_MERGE_PENDING]`
- `[JULIA_BETA4_COMPOUND_SLAB_HARNESS_FAIL]`

Superseded history marker: `[JULIA_BETA4_COMPOUND_SLAB_NO_LEGAL_LANE_GREEN]`
used to describe Rows 1/2 as release-safe rejection. It no longer implies
release-safe behavior for that class.

Rows 4-6 remain pending/TODO in this focused grammar note. Row 4 DOUBLE merge and Row 5 top-click are not implemented by the internal Row 3 remap slice. Julia's screenshot-shape RED is now a separate required proof/fix path, not a contradiction of the internal Row 3 GREEN.

Screenshot-shape proof markers added by `-Dslabbed.beta4LiveScreenshotShapeRed=true`:

- `[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_UPPER_GREEN]`
- `[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_LOWER_RED]`
- `[JULIA_BETA4_LIVE_SCREENSHOT_TOP_FACE_GHOST_RED]`
- `[JULIA_BETA4_LIVE_SCREENSHOT_BAND_SPLIT_HARNESS_GREEN]`
- `[JULIA_BETA4_LIVE_SCREENSHOT_BAND_SPLIT_HARNESS_FAIL]`
- `[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_GREEN]`
- `[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_FAIL]`

## Automated canonical live-shape goblin harness

The opt-in client gametest `-Dslabbed.beta4LiveShapeGoblin=true` automates the live checks Julia has been doing manually. It is diagnostic only, is not registered as a default client gametest entrypoint, and does not change placement, retarget, support, or release behavior. The exact gated route is:

`JAVA_TOOL_OPTIONS="-Dslabbed.beta4LiveShapeGoblin=true -Dfabric.client.gametest.disableNetworkSynchronizer=true" ./gradlew --no-daemon runClientGameTest --console plain`

The harness builds and proves this exact canonical structure before any click:

- Layer A: two bottom `stone_slab[type=bottom]` supports.
- Layer B: two ordinary full stone blocks bridging over those supports.
- Layer C: two `stone_slab[type=bottom]` slabs on top of the bridge.
- Layer D: one ordinary full stone block on top of one Layer C slab.

After `[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_GREEN]`, it tests support-present lower/upper side placement, support-present top-face placement, repeat placement after the first upper-half side slab, and the missing-under-slab side/top-face variants from the visible upper full block. `[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID]` fails the harness if any required slab is a full block, any required full block is a slab, the upper full block is not on the named top slab, or the missing-under-slab variant is not explicitly named. The final `[JULIA_BETA4_LIVE_GOBLIN_SUMMARY]` reports `structure`, `fixtureTruth`, `lowerExact`, `upperFirst`, `repeatPlacement`, `topFaceExact`, `supportPresent.upperSide`, `supportPresent.lowerSide`, `supportPresent.topFace`, `supportMissing.side`, `supportMissing.topFace`, `hitbox`, `ghost`, `jump`, `wrongOwner`, and `releaseBlockers`.

Exact-candidate proof is mandatory. "Some slab placed" is not sufficient under
the corrected law: lower-band side clicks must resolve to the expected side
candidate as `stone_slab[type=bottom]` at `dy=-1.0`; upper-band side clicks must
resolve to the same side candidate as `stone_slab[type=top]` at `dy=-1.0`;
repeat placement must merge the same side cell into `stone_slab[type=double]`
at `dy=-1.0`; top-face clicks must resolve to `source.up()` as
`stone_slab[type=bottom]` at `dy=-1.0`.

Repeat merge seam finding:

- Diagnostic-only trace property: `-Dslabbed.beta4RepeatMergeTrace=true`.
- Markers: `[JULIA_BETA4_REPEAT_SEAM_START]`, `[JULIA_BETA4_REPEAT_SEAM_CLIENT_BEFORE]`, `[JULIA_BETA4_REPEAT_SEAM_CLIENT_PREDICT]`, `[JULIA_BETA4_REPEAT_SEAM_CLIENT_RESULT]`, `[JULIA_BETA4_REPEAT_SEAM_SERVER_TOLERANCE]`, `[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]`, `[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]`, `[JULIA_BETA4_REPEAT_SEAM_SERVER_TICK]`, `[JULIA_BETA4_REPEAT_SEAM_CLIENT_TICK]`, and `[JULIA_BETA4_REPEAT_SEAM_SUMMARY]`.
- Fixed classification: `FIXED_GREEN`.
- Legal repeat/merge class: `LOWERED_SAME_CELL_SLAB_MERGE`.
- Exact result: compatible lowered same-cell slab `BOTTOM`/`TOP` at `dy=-0.5` merges into `DOUBLE` at `dy=-0.5`.
- The traced canonical result is `stone_slab[type=bottom] dy=-0.5 -> stone_slab[type=double] dy=-0.5`, with `setBlockState=YES` and server/client ticks 1, 5, and 20 staying `DOUBLE`.
- Client prediction is still not proof by itself; the release-relevant proof is the durable server final state.
- arbitrary `dy=-1.0` slab lanes remain illegal. Only the named compound visible slab lane states are legal candidates, and top-face proof must be rebuilt against the corrected `dy=-1.0` owner-top law before release confidence can return.

Goblin live parity is now mandatory before any goblin GREEN can be treated as
release evidence. The harness must aim the camera, read the actual
`MinecraftClient.crosshairTarget`, prove the target owner/face/localY/band, and
click that real `BlockHitResult`. A synthetic `BlockHitResult`, or an isolated
fresh-fixture candidate success, is only a diagnostic and is not release proof.
The sequence proof must avoid rebuilding between the first side, lower-after-
first, repeat, and top-face clicks, and each step must emit a bounded changed-
block delta scan. Exact candidate state and exact changed-block delta are both
required; either one alone is insufficient.

Targeting parity is also a precondition for placement proof. Before placement,
the diagnostic must show the camera ray is actually aimed at the intended
visible owner, face, and dy-adjusted local band. Mismatched aim, an invalid
visible-local hit point, or a physically occluded point is harness evidence only,
not release proof. The lower-after-first sequence now uses a named player-
realistic corridor, and the top-face step uses a separate top-face eye so the
real crosshair proves the visible compound owner `UP` face before placement.

The corridor scout requirement makes that explicit: lower-half proof must first
scan plausible player eye positions around the canonical structure and select a
real `TARGET_OK` crosshair corridor. If every player-realistic corridor is
occluded, the result is `NO_CORRIDOR` and not gameplay proof unless beta4 product
law intentionally adds comfort placement for occluded lower bands. Latest scout:
both the unmutated lower route and the sequence lower-after-first route found
real corridors; the sequence lower click still wrong-deltaed after a real
crosshair hit, so the remaining RED is no longer explained by the old occluded
ray.

Superseded after the `d7ef534` manual delayed trace: the beta4 law now allows
only the named, source-owned compound visible slab lane states at `dy=-1.0`.
Arbitrary `dy=-1.0` slab lanes remain illegal, but successful visible compound
side/top results no longer have to normalize into `dy=-0.5` or `dy=0.0`.

Triad targeting follow-up: lower, upper, merge, and owner-top visible slab lane
states now share one narrow owner rule. If a real ray intersects the shifted
visible outline body of one of the named compound visible slab lane states, the
client retargets ownership to that named slab body instead of the neighboring
support/visible cell that vanilla DDA may visit first. This is not a generic
lowered-slab rescue: arbitrary `dy=-1.0` slabs remain untargetable unless they
carry one of the named compound visible lane markers, and the old `dy=-0.5`
continuation lane remains separate.

## Screenshot side-shape discriminator audit

Audit status at `08cb004`: no safe screenshot-only discriminator has been
found in the current placement facts. The tempting rule "a lowered bottom slab
exists directly below the clicked compound source" is insufficient because Row
1 of the no-legal-lane proof has the same semantic shape as Julia's side-slab
screenshot case: an ordinary compound full-block source at `dy=-1.0`, a lowered
bottom slab directly below it at `dy=-0.5`, a lower/side-band horizontal slab
click, and an immediate side candidate that is air.

The only proven implementation predicate that preserves Rows 1/2 is still the
existing Row 3 horizontal-lane rule: exactly one legal `dy=-0.5` slab lane must
already exist horizontally adjacent to the clicked source in the intended
direction, and placement continues beyond that lane. Julia's screenshot side
shape does not currently satisfy that rule, so implementing it safely requires
either a new semantic fact not present in Rows 1/2 or a product decision to
change Rows 1/2 semantics.

| Case | Clicked source | Face / band | Below source | Horizontal lane in clicked direction | Current helper `legalLaneCount` | Candidate relation | Candidate has horizontal `dy=-0.5` lane neighbor | Source has vertical below `dy=-0.5` support only | Expected behavior | Current behavior |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Old Row 1 compound below-lane side slab placement | ordinary stone compound full block, `dy=-1.0` | horizontal side; old block-coordinate lower label, but corrected visible local Y `0.75` | bottom `stone_slab`, `dy=-0.5` | air at immediate side cell | `0` | immediate side cell | no | yes | superseded by `COMPOUND_VISIBLE_SIDE_UPPER_SLAB`, `stone_slab[type=top]`, `dy=-1.0` | GREEN via `COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE` and row compatibility proof |
| Old Row 2 compound below-lane side slab placement | ordinary stone compound full block, `dy=-1.0` | horizontal side; hit is outside the source visible body under corrected local-Y math | bottom `stone_slab`, `dy=-0.5` | air at immediate side cell | `0` | immediate side cell | no | yes | remains old below-lane expectation unless separately superseded | GREEN/PENDING under old below-lane proof |
| Internal artificial Row 3 legal remap | ordinary stone compound full block, `dy=-1.0` | horizontal side; same visible local Y as Row 1, but existing legal horizontal lane is present | bottom `stone_slab`, `dy=-0.5` | existing legal bottom `stone_slab`, `dy=-0.5` | `1` | continuation cell beyond existing lane | yes | no, because horizontal lane exists | author `stone_slab[type=bottom]` at `dy=-0.5` | GREEN via horizontal continuation priority before visible classification |
| Julia screenshot upper-half side-shape | upper ordinary stone compound full block, `dy=-1.0` | horizontal side; upper band | bottom `stone_slab`, `dy=-0.5` | air at immediate side cell | `0` | immediate side cell | no | yes | `COMPOUND_VISIBLE_SIDE_UPPER_SLAB`, `stone_slab[type=top]`, `dy=-1.0` | GREEN via `COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE` |
| Julia screenshot lower-half side-shape | upper ordinary stone compound full block, `dy=-1.0` | horizontal side; lower/below-source band | bottom `stone_slab`, `dy=-0.5` | air at immediate side cell | `0` | immediate side cell | no | yes | `COMPOUND_VISIBLE_SIDE_LOWER_SLAB`, `stone_slab[type=bottom]`, `dy=-1.0` | GREEN via `COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE` |

Discriminator candidates evaluated:

| Candidate | Distinguishes screenshot from Rows 1/2 | Preserves artificial Row 3 | Risks broadening slabs | Recommended implementation predicate |
| --- | --- | --- | --- | --- |
| A. Existing horizontal lane neighbor required | no; it rejects screenshot and Rows 1/2, accepts Row 3 | yes | no | yes, but already implemented and does not solve screenshot |
| B. Visible side-band click required | no; Row 1 and screenshot both use the lower side band | yes | yes if used alone | no |
| C. Upper full-block stack source required | no; Rows 1/2 and screenshot both use a compound source above a lowered bottom slab carrier | yes | yes if used alone | no |
| D. Candidate position relation | no; screenshot and Rows 1/2 both target the immediate side cell if below-lane-only were accepted | yes | yes if used alone | no |
| E. Existing visible lane ownership | no proven screenshot-only owner exists; Row 3 has a horizontal visible lane, screenshot and Rows 1/2 do not | yes | no if defined as Row 3's horizontal lane | no for screenshot |

Conclusion superseded after `d7ef534`: below-lane-only was rejected as a
discriminator and is no longer sufficient as the product-law class. The
screenshot side-shape and Rows 1/2 still share a placement surface, but the
corrected result must be the bounded compound visible slab lane at `dy=-1.0`
when the source is an authored/persistent compound full-block owner.

Julia live retest correction at `6d0d525`: the old screenshot side-shape status
was too coarse. Upper-half side placement is GREEN, lower-half side placement
was RED at that point, and top-face ghost/skip was RED. The proof now keeps
those bands separate instead of treating side placement as one status.

Canonical live-shape correction status after `d7ef534`: the previous lower-side
and top-face goblin successes are superseded as release evidence. They accepted
the old lane model (`dy=-0.5` side slabs and `dy=0.0` owner-top slab), while the
manual delayed trace proves the corrected law needs named `dy=-1.0` visible
lane results for lower side, upper side, same-cell merge, and owner top. Correct
future goblin output must report non-empty blockers until those exact states are
proven through real crosshair targeting, durable server/client final state,
bounded changed-block deltas, and model/outline/raycast parity.

After the named `COMPOUND_VISIBLE_SLAB_LANE` proof became green, the legacy
lower-after-first/repeat live-shape markers no longer define current-law
release blockers. `[JULIA_BETA4_LIVE_GOBLIN_LEGACY_SEQUENCE_STALE]` records the
old `dy=-0.5` expectation, while the visible-lane summary owns the current
`dy=-1.0` proof and the remaining `JuliaLiveRetest` blocker.

Manual-live parity rule after `b92887b`: automation does not count as release
proof when Julia's real `runClient` interaction disagrees. The live-goblin
summary may be useful as a harness diagnostic, but manual `runClient` RED wins
until the actual click stream is captured and explained. Parity proof must use
real `MinecraftClient.crosshairTarget` clicks, not synthetic `BlockHitResult`
success, and must include target pos/state/dy, face, hit vector, local hit
coordinates, expected candidate, changed-block deltas, `ActionResult`, placement
intent, server tolerance, and `SlabSupport` decision markers for the same manual
sequence. The capture command is:

`JAVA_TOOL_OPTIONS="-Dslabbed.inspect=true -Dslabbed.target.trace=true -Dslabbed.beta4ManualLiveTrace=true" ./gradlew --no-daemon runClient --console plain`

Delayed-final proof rule after `4e6dae9`: immediate client delta is not proof
of durable placement. Any manual `runClient` parity claim must inspect
`[JULIA_BETA4_MANUAL_LIVE_DELAYED_FINAL]` and
`[JULIA_BETA4_MANUAL_LIVE_DELAYED_SUMMARY]` at client ticks 1, 5, 20, and 40.
The delayed proof must say whether the immediate candidate survived server
reconciliation, reverted/vanished as a ghost, remained a clean no-op after
`Pass[]`, or mismatched the expected candidate/state/dy. Gameplay work should
not start from an immediate `Success` alone.

## Manual visual acceptance after 78c0f01

Julia reported manual visual acceptance for `COMPOUND_VISIBLE_SLAB_LANE` after
placement settle.

- lower / upper / repeat-merge / top / support-missing behavior is accepted.
- `dy=-1.0` slab lane remains bounded and source-owned.
- remaining issue is immediate render snap: slab can briefly appear at old/top-half
  visual position before settling to intended `dy=-1.0`.
- delayed trace includes legacy `dy=-0.5` expectation checks; stale `delayed_candidate_mismatch` lines with `ghost=true` are caveat-only while the current `dy=-1.0` law is in force.
- no final Bug Blaster yet; release remains blocked until snap is audited or explicitly deferred.

## Implementation slices after design

1. Add/rename focused RED/PENDING proof markers for compound below-lane side slab placement.
2. Add authority-level classifier for compound below-lane slab remap result.
3. Remap only into existing legal `dy=-0.5` lowered slab grammar.
4. Preserve/reject when no legal stable slab result exists.
5. Add merge completion into `DOUBLE` `dy=-0.5`.
6. Triad and survival proof.
7. Julia live retest.
8. commit/tag/push only after one live-confirmed win.

## Authority placement

The decision must live in Slabbed authority/semantic helper, not scattered inline mixin predicates.

Mixins may gather context and call authority. Authority should return an enum-like decision:

- `KEEP_COMPOUND_OWNER`
- `REMAP_TO_EXISTING_LOWERED_SLAB_LANE`
- `MERGE_TO_LOWERED_DOUBLE`
- `VANILLA_SLAB_RESULT`
- `REJECT_OR_DEFER`

Do not implement this enum in this slice; document only.

## Release impact

Beta 4 artifact/gates may be technically ready.

Public release confidence remains paused because live slab feel is RED, not accepted. Do not move the release tag until Julia explicitly approves.
