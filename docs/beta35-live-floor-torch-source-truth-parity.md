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

## Failure-layer contract

- `SOURCE_TRUTH_MISMATCH`
- `LIVE_FLOOR_TORCH_WRONG_SUPPORT_OWNER`
- `LIVE_FLOOR_TORCH_WRONG_DY`
- `LIVE_FLOOR_TORCH_CONTACT_GAP`
- `NONE`

`fixtureMatchesLiveDyStack` is computed as `supportDy == -0.500` and `torchDy == -1.000`.
If this is `false`, the proof should report `SOURCE_TRUTH_MISMATCH` and stop.

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

**Missing source truth**: the fixture does not place an anchored full block (or `hasBottomSlabBelow`-
backed full block) at `supportCandidatePos.down()`.

**Next slice**: better RED fixture that places the surrounding carrier context so
`fixtureMatchesLiveDyStack=true`. See `docs/beta35-floor-torch-support-source-truth-audit.md`.
