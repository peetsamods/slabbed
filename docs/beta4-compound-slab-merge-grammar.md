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

## Non-legal states for Beta 4

Beta 4 does not allow:

- no freeform slab type + `dy=-1.0`
- no `dy<-1.0`
- no normal-lane `dy=0` slab produced from valid lowered-lane interaction
- no slab state that only works because rescue/retarget fixes it later
- no broad solidity or hitbox lies

## Player-facing behavior table

| Player action | Required Beta 4 resolution |
| --- | --- |
| full block side-click on compound full block | keep current legal compound full-block behavior, `dy=-1.0` |
| full block top-click on compound full block | same compound lane `dy=-1.0`, not `dy=-1.5` |
| slab side-click on compound full block whose legal lowered slab lane/support is directly below the source | remap the immediate side candidate into the existing lowered slab grammar at `dy=-0.5`; do not create `dy=-1.0` slab |
| slab side-click on persistent visible compound owner after direct below support is missing | remap the immediate side candidate into the existing lowered slab grammar at `dy=-0.5` only while the source remains anchored, compound, visible, and `dy=-1.0` |
| slab side-click where existing legal `dy=-0.5` lowered slab lane can be continued | remap into that `dy=-0.5` legal lane |
| second slab click against compatible lowered slab | merge `BOTTOM`/`TOP` into `DOUBLE` `dy=-0.5` when proof-covered |
| slab top-click on persistent visible compound owner | author `COMPOUND_VISIBLE_OWNER_TOP_SLAB`: candidate `source.up()`, `stone_slab[type=bottom]`, `dy=0.0`, `persistentLoweredSlabCarrier=false` |
| source/support break | no jump, no ghost, no silent renormalization |
| reload/rejoin | legal states survive; illegal/unproven states must not appear |

## Product decision: compound below-lane side slab placement

The discriminator audit proved that below-lane-only is unsafe as a screenshot-only discriminator: Julia's screenshot side-shape and Rows 1/2 have the same placement facts available to current authority. The product contract therefore promotes that whole class into a named legal placement instead of trying to split it with a nonexistent discriminator.

If a compound `dy=-1.0` ordinary full-block source has a legal lowered slab lane/support directly below it, a held-slab side click may author the immediate side candidate into the existing legal `dy=-0.5` lowered slab grammar. The candidate side slab may become `BOTTOM` or `TOP` at `dy=-0.5` according to that existing lowered slab grammar.

If that direct below support is later missing, the same side placement law may still apply only for a persistent visible compound owner: the clicked source itself must remain an anchored ordinary full block, carry the compound full-block sidecar, and still report `dy=-1.0`. This legal class is `COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB`; it does not make unsupported air a source and does not allow slab authoring from a missing or non-compound owner.

This is allowed because the result is not a compound slab lane. It is a remap into existing lowered slab grammar at `dy=-0.5`.

## Product decision: compound visible owner top slab placement

If a held-slab click hits the UP face of a persistent visible compound owner, the legal result is named `COMPOUND_VISIBLE_OWNER_TOP_SLAB`. The source must remain an anchored ordinary full block carrying the compound full-block sidecar, and the candidate is exactly `source.up()`.

The authored candidate is vanilla-height slab law, not lowered side-lane law:

- final state: `stone_slab[type=bottom]`
- final dy: `0.0`
- `persistentLoweredSlabCarrier=false`
- durable distinguisher: the source below carries existing persisted/synced compound full-block owner truth; no new slab lane marker is required

The dynamic dy path that previously broke this law was `SlabSupport.getYOffsetInner(...)`'s bottom-slab lowered branch through `hasLoweredCarrierBelow(world, pos)`: a bottom slab directly above an anchored compound full block was being treated as a lowered carrier continuation and resolved to `dy=-0.5`. `COMPOUND_VISIBLE_OWNER_TOP_SLAB` is now narrowly excluded from that branch by checking the durable source-owner sidecar, while side placements continue to use the existing `dy=-0.5` lowered slab grammar.

The forbidden results remain unchanged:

- no slab state at `dy=-1.0`
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
| 1 | slab lower-half side click on compound full block with legal lowered slab support directly below and no horizontal lane | currently preserves/rejects and authors no slab | author the immediate side candidate as legal lowered slab grammar at `dy=-0.5` | any `dy=-1.0` slab, `dy<-1.0`, `dy=0` lowered-lane fallback, or rescue-created slab lane |
| 2 | slab upper-half side click on compound full block with legal lowered slab support directly below and no horizontal lane | currently preserves/rejects and authors no slab | author the immediate side candidate as legal lowered slab grammar at `dy=-0.5` | any `dy=-1.0` slab, `dy<-1.0`, `dy=0` lowered-lane fallback, or hidden retarget workaround |
| 3 | slab side click when adjacent legal `dy=-0.5` lowered slab lane exists | still rejects or misroutes until grammar exists | remap into the adjacent legal lowered lane | `dy=0` fallback or compound lane invention |
| 4 | second slab click merging `BOTTOM`/`TOP` -> `DOUBLE` `dy=-0.5` | merge path not yet proven | merge into `DOUBLE` `dy=-0.5` only when proof-covered | `DOUBLE` at `dy=-1.0` or silent recursion |
| 5 | slab top click on persistent visible compound owner | top-face ghost/skip was possible on the live shape | author `COMPOUND_VISIBLE_OWNER_TOP_SLAB` at `source.up()`, `stone_slab[type=bottom]`, `dy=0.0`, `persistentLoweredSlabCarrier=false` | freeform slab at `dy=-1.0`, `dy<-1.0`, or `dy=-0.5` top-face ghost |
| 6 | no ghost/flicker after tick | visible instability or mismatch may remain | stable post-tick result with no ghost/flicker | visual success that only exists for one frame |
| 7 | source/support break | jump or collapse risk remains | no jump, no ghost, no silent renormalization | survival only because of delayed rescue |
| 8 | reload/rejoin | illegal or unproven state may reappear | only legal states survive reload/rejoin | save/load hiding an illegal slab lane |
| 9 | triad: model/outline/raycast | shape/owner disagreement may remain | model, outline, and raycast agree on the legal result | model-only or outline-only false-green |
| 10 | held slab selection preserves compound owner when no legal slab result exists | selection may drift toward a fake slab lane | keep compound owner until a legal slab result exists | retarget-created slab legality |

## Proof status

These proof rows now exist in the focused harness slice:

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

Exact-candidate proof is mandatory. "Some slab placed" is not sufficient: lower-band side clicks must resolve to the expected side candidate as `stone_slab[type=bottom]` at `dy=-0.5`; upper-band side clicks must resolve to the same side candidate as `stone_slab[type=top]` at `dy=-0.5`; repeat placement must legally extend/combine according to product law; top-face clicks must resolve to `source.up()` as `stone_slab[type=bottom]` at `dy=0.0`.

Goblin live parity is now mandatory before any goblin GREEN can be treated as
release evidence. The harness must aim the camera, read the actual
`MinecraftClient.crosshairTarget`, prove the target owner/face/localY/band, and
click that real `BlockHitResult`. A synthetic `BlockHitResult`, or an isolated
fresh-fixture candidate success, is only a diagnostic and is not release proof.
The sequence proof must avoid rebuilding between the first side, lower-after-
first, repeat, and top-face clicks, and each step must emit a bounded changed-
block delta scan. Exact candidate state and exact changed-block delta are both
required; either one alone is insufficient.

The beta4 law remains unchanged: slab `dy=-1.0` lanes are illegal, any successful slab side result must normalize into the existing legal `dy=-0.5` lowered slab grammar, and the named top-face result normalizes into vanilla `dy=0.0` slab law.

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
| Rows 1/2 compound below-lane side slab placement | ordinary stone compound full block, `dy=-1.0` | horizontal side; Row 1 lower band, Row 2 upper band | bottom `stone_slab`, `dy=-0.5` | air at immediate side cell | `0` | immediate side cell | no | yes | author legal lowered side slab at `dy=-0.5` | RED/PENDING; old safe reject superseded |
| Internal artificial Row 3 legal remap | ordinary stone compound full block, `dy=-1.0` | horizontal side; lower band | bottom `stone_slab`, `dy=-0.5` | existing legal bottom `stone_slab`, `dy=-0.5` | `1` | continuation cell beyond existing lane | yes | no, because horizontal lane exists | author `stone_slab[type=bottom]` at `dy=-0.5` | GREEN legal remap |
| Julia screenshot upper-half side-shape | upper ordinary stone compound full block, `dy=-1.0` | horizontal side; upper band | bottom `stone_slab`, `dy=-0.5` | air at immediate side cell | `0` | immediate side cell | no | yes | author legal lowered side slab at `dy=-0.5` | GREEN after `6d0d525` live retest/proof split |
| Julia screenshot lower-half side-shape | upper ordinary stone compound full block, `dy=-1.0` | horizontal side; lower/below-source band | bottom `stone_slab`, `dy=-0.5` | air at immediate side cell | `0` | immediate side cell | no | yes | author legal lowered side slab at `dy=-0.5` | GREEN via held-slab legal-remap hit-validity bridge |

Discriminator candidates evaluated:

| Candidate | Distinguishes screenshot from Rows 1/2 | Preserves artificial Row 3 | Risks broadening slabs | Recommended implementation predicate |
| --- | --- | --- | --- | --- |
| A. Existing horizontal lane neighbor required | no; it rejects screenshot and Rows 1/2, accepts Row 3 | yes | no | yes, but already implemented and does not solve screenshot |
| B. Visible side-band click required | no; Row 1 and screenshot both use the lower side band | yes | yes if used alone | no |
| C. Upper full-block stack source required | no; Rows 1/2 and screenshot both use a compound source above a lowered bottom slab carrier | yes | yes if used alone | no |
| D. Candidate position relation | no; screenshot and Rows 1/2 both target the immediate side cell if below-lane-only were accepted | yes | yes if used alone | no |
| E. Existing visible lane ownership | no proven screenshot-only owner exists; Row 3 has a horizontal visible lane, screenshot and Rows 1/2 do not | yes | no if defined as Row 3's horizontal lane | no for screenshot |

Conclusion: below-lane-only is rejected as a discriminator but promoted as a
product-law class. The screenshot side-shape and Rows 1/2 are the same legal
placement surface now: compound below-lane side slab placement. The future
implementation must remap that surface into existing legal lowered slab grammar
at `dy=-0.5`; the `dy=-1.0` slab lane remains illegal.

Julia live retest correction at `6d0d525`: the old screenshot side-shape status
was too coarse. Upper-half side placement is GREEN, lower-half side placement
was RED at that point, and top-face ghost/skip was RED. The proof now keeps
those bands separate instead of treating side placement as one status.

Canonical live-shape lower-side and top-face correction: the lower-half A/B side
route uses the same compound below-lane side slab grammar and survives server
packet hit validation when `SlabSupport.findLegalCompoundSlabRemap(...)`
classifies the held-slab click as a legal remap. The top-face route is now the
named `COMPOUND_VISIBLE_OWNER_TOP_SLAB` result: held-slab `UP` clicks on the
persistent visible compound owner author `stone_slab[type=bottom]` at
`source.up()` with final `dy=0.0` and `persistentLoweredSlabCarrier=false`.
The c956fa3 gated goblin proof that reported `topFace=GREEN` remains superseded
as release evidence because Julia found the harness/build fixture did not prove
the actual slab/full-block composition. The later 278513b goblin summary that
reported `releaseBlockers=none` is also superseded by Julia manual live RED:
it accepted too weak a condition and did not prove exact lower/upper candidates,
repeat placement, or exact top-face `dy=0.0`. Corrected goblin output must keep
side-authored slabs as legal `dy=-0.5` lowered slabs, keep the beta4 `dy=-1.0`
slab lane illegal, and report non-empty blockers when lower exact placement,
repeat placement, or top-face exact placement fails. Release remains blocked until
Julia passes or
waives that live retest.

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
