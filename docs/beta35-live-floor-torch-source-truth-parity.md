# Beta 3.5 Floor Torch Source Truth Parity Proof

## Status

This proof-only slice classifies whether the replay fixture can reproduce Julia’s live
floor-torch dy stack for the same in-scope pattern:

- `supportCandidateState=Block{minecraft:stone_slab}[type=bottom]`
- `supportDy=-0.500000`
- `torchDy=-1.000000`

Beta 3.5 release prep remains **PAUSED**.
`wall_torch`, `lantern`, `signs`, and `chains` are still `NOT_COVERED`.

## Gate

Enable with:

```bash
-Dslabbed.beta35LiveFloorTorchSourceTruthParity=true
```

## Markers

- `[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_GREEN]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_FAIL]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]`

## Required logged fields

- `torchPos`
- `torchState`
- `supportCandidatePos`
- `supportCandidateState`
- `supportDy`
- `torchDy`
- `supportVisibleTopY`
- `torchModelBottomY`
- `contactGap`
- `outlineMinY`
- `outlineMaxY`
- `raycastMinY`
- `raycastMaxY`
- `targetFace`
- `targetType`
- `targetPos`
- `targetHitX/targetHitY/targetHitZ`
- `placementResult`
- `placementAccepted`
- `fixtureMatchesLiveDyStack=true/false`
- `failureLayer`

## Failure-layer contract (updated for live dy stack parity slice)

- `SOURCE_TRUTH_MISMATCH` — dy stack did not match live (`supportDy != -0.500` or `torchDy != -1.000`)
- `LIVE_DY_STACK_MATCH_NO_GAP` — dy stack matches but `contactGap == 0` (unexpected; needs audit)
- `LIVE_DY_STACK_MATCH_CONTACT_GAP` — dy stack matches, nonzero gap but not matching `-1.500`
- `NONE` — dy stack matches and `contactGap` matches live `-1.500000`

`fixtureMatchesLiveDyStack` is computed as `supportDy == -0.500` and `torchDy == -1.000`.
If this is `false`, the proof reports `SOURCE_TRUTH_MISMATCH` and stops.

## Root cause of mismatch (audit 9ac16f2 / save/beta35-floor-torch-source-truth-mismatch)

`supportDy=-0.500` for a `stone_slab[type=bottom]` requires that
`SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive` returns `true`.
This is true when the `LOWERED_SLAB_CARRIER_TYPE` chunk attachment contains the position
(written by `updatePersistentLoweredSlabCarrier` at placement time), OR when the structural
fallbacks find an anchored / `hasBottomSlabBelow`-backed full block at `pos.down()` or an
adjacent lowered bridge support.

The fixture places the slab in isolation, then calls `updatePersistentLoweredSlabCarrier`.
Since there is no qualifying surrounding context (no anchored full block below, no bottom slab
below that full block, no adjacent lowered lane), `qualifiesForPersistentLoweredSlabCarrier`
returns `false` → carrier mark is NOT written to the chunk attachment → client chunk attachment
is empty → structural fallbacks also return false → `supportDy=0.000`.

In the live world the mark was written during real in-game play when that block position had the
qualifying surrounding context. The mark persists via `.persistent(SET_CODEC)` and syncs to
all clients via `.syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())`.

There is no client/server dy split: both live capture and replay measure from `mc.world`
(ClientWorld, a World subclass), which reads the synced chunk attachment directly.

**Missing source truth**: the fixture did not place an anchored full block (or `hasBottomSlabBelow`-
backed full block) at `supportCandidatePos.down()`.

**Fix applied** (`save/beta35-floor-torch-live-dy-stack-parity`): the fixture now places:
1. `Blocks.STONE_SLAB[type=bottom]` at `supportCandidatePos.down().down()` — bottom slab so
   `hasBottomSlabBelow(world, anchoredFullBlockPos)` is true.
2. `Blocks.STONE` at `supportCandidatePos.down()` — ordinary full block anchor candidate.
3. `addAnchor(world, anchoredFullBlockPos, stoneState)` — writes `ANCHOR_TYPE` mark (succeeds
   because `hasBottomSlabBelow` is now true for the stone block).
4. `Blocks.STONE_SLAB[type=bottom]` at `supportCandidatePos` — support slab.
5. `updatePersistentLoweredSlabCarrier(world, supportCandidatePos, state)` — now qualifies via
   `qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive` (anchored full block
   below) → carrier mark written → `supportDy=-0.500` ✓.

## Live dy stack parity result (save/beta35-floor-torch-live-dy-stack-parity)

```
fixtureMatchesLiveDyStack=true
supportDy=-0.500
torchDy=-1.000
contactGap=0.000000
failureLayer=LIVE_DY_STACK_MATCH_NO_GAP
anchoredFullBlockAnchored=true
anchoredFullBlockHasBottomSlabBelow=true
carrierMarkWritten=true
```

**Outcome B**: dy stack matches live but `contactGap=0` instead of `-1.500`. The torch model
bottom Y equals the support visible top Y in the fixture. This diverges from the live capture
(`contactGap=-1.500000`, `supportVisibleTopY=-55.500000`, `torchModelBottomY=-57.000000`).

**Next audit**: the `getSupportYOffset` / torch outline relative-Y discrepancy between live capture
and fixture needs investigation. The live `supportVisibleTopY=-55.5` implies
`getSupportYOffset=1.0` at capture time; the fixture measures `getSupportYOffset=0.5`.
This is classified as `LIVE_DY_STACK_MATCH_NO_GAP` — recorder/contact-math audit required.
