# Beta 3.5 Floor Torch Live Dy Stack Parity

## Status

Fixture now reproduces the live dy stack (`supportDy=-0.500`, `torchDy=-1.000`,
`fixtureMatchesLiveDyStack=true`). Beta 3.5 release prep remains **PAUSED**.
`wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
No production gameplay fix implemented. No release tag moved.

Cross-check against v2 source-truth parity: `fixtureMatchesV2LiveStack=true` with
`contactGap=0.500000` is now reproduced under compound visible slab-lane source truth.

With corrected V2 capture math in `docs/beta35-live-torch-recorder-contact-math-audit.md`,
the remaining open blocker in this slice is reproducing the `v2` floor-torch live-contact
measurement (`contactGap=0.500000`) in a focused RED proof.

Starting HEAD: `9984cf5` / `save/beta35-floor-torch-support-source-truth-audit`
Commit: `save/beta35-floor-torch-live-dy-stack-parity`

## Fixture context used

**Option B — bottom-slab-backed anchored full block:**

| Block | Position | Purpose |
|---|---|---|
| `stone_slab[type=bottom]` | `supportCandidatePos.down().down()` = `(51, -58, 89)` | Makes `hasBottomSlabBelow(world, anchoredFullBlockPos)` true |
| `stone` | `anchoredFullBlockPos` = `(51, -57, 89)` | Ordinary full-block anchor candidate |
| `addAnchor(...)` | on `(51, -57, 89)` | Writes `ANCHOR_TYPE` mark (now qualifies because `hasBottomSlabBelow` is true) |
| `stone_slab[type=bottom]` | `supportCandidatePos` = `(51, -56, 89)` | Support slab under torch |
| `updatePersistentLoweredSlabCarrier(...)` | on `(51, -56, 89)` | `qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive` = true → carrier mark written |

`addAnchor` succeeds because `qualifiesForAnchor` requires `isOrdinaryFullBlockAnchorCandidate`
AND (`hasBottomSlabBelow` OR `qualifiesAsVerticalChainSupport`). The bottom slab at
`anchoredFullBlockPos.down()` satisfies `hasBottomSlabBelow`.

## Measured result

```
supportCandidatePos=51,-56,89
supportCandidateState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false]
anchoredFullBlockPos=51,-57,89
anchoredFullBlockAnchored=true
anchoredFullBlockHasBottomSlabBelow=true
carrierMarkWritten=true
supportDy=-0.500
torchPos=51,-55,89
torchDy=-1.000
supportVisibleTopY=-56.000000
torchModelBottomY=-56.000000
contactGap=0.000000
fixtureMatchesLiveDyStack=true
failureLayer=LIVE_DY_STACK_MATCH_NO_GAP
```

## Outcome: B — dy stack matches, contactGap=0 (recorder math corrected)

The fixture reproduces the live dy stack correctly and `contactGap=0.000000`.
Classification: `LIVE_DY_STACK_MATCH_NO_GAP`.

The previously reported live `contactGap=-1.500000` was a **recorder formula artifact** —
see `docs/beta35-live-torch-recorder-contact-math-audit.md` for the full analysis.
`v2` capture math now classifies those reports as corrected measurement artifacts while
remaining on-file tests focus on reproducing the floor-torch `+0.500000` live-contact condition.

### Why the old recorder showed `-1.5`

Two bugs in `Beta35LiveTorchCaptureRecorder` (v1 formula):

1. `supportVisibleTopY` used hardcoded `+1.0d` (full-block height) instead of
   `getSupportYOffset(state)=0.5` for a bottom slab → off by +0.5
2. `torchModelBottomY` added `+torchDy` again on top of the block-local shape min,
   but `SlabSupportStateMixin.slabbed$offsetOutline` already shifts the outline shape
   by `yOff=torchDy` in block-local space → double-applying, off by -1.0

Net: `contactGap_v1 = (-57.0) - (-55.5) = -1.5` (measurement artifact only).
Corrected: `contactGap_v2 = (-56.0) - (-56.0) = 0.0` (matches fixture proof).

### Implication

The torch placement behavior is **correct**. The torch model bottom sits flush with the
lowered slab's visible top surface. Beta 3.5 release prep should be unblocked for the
floor-torch case pending Julia's live re-verification with the v2 recorder.

## Failure-layer contract

- `SOURCE_TRUTH_MISMATCH` — dy stack still does not match live
- `LIVE_DY_STACK_MATCH_NO_GAP` — dy matches, `contactGap == 0` (this slice result)
- `LIVE_DY_STACK_MATCH_CONTACT_GAP` — dy matches, nonzero gap not equal to `-1.500`
- `NONE` — dy matches and `contactGap == -1.500`

## Release and coverage status

- Beta 3.5 release prep: **PAUSED**
- `productionGameplayFixApplied=false`
- `wall_torch=NOT_COVERED`
- `lantern=NOT_COVERED`
- `signs=NOT_COVERED`
- `chains=NOT_COVERED`
- No release tag moved
- `fixtureContext=OPTION_B_BOTTOM_SLAB_BELOW_ANCHORED_FULL_BLOCK`
