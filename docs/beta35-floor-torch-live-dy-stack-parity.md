# Beta 3.5 Floor Torch Live Dy Stack Parity

## Status

Fixture now reproduces the live dy stack (`supportDy=-0.500`, `torchDy=-1.000`,
`fixtureMatchesLiveDyStack=true`). Beta 3.5 release prep remains **PAUSED**.
`wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.
No production gameplay fix implemented. No release tag moved.

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

## Outcome: B — dy stack matches, contactGap becomes 0

The fixture reproduces the live dy stack correctly but `contactGap=0.000000` rather than
the live `-1.500000`. Classification: `LIVE_DY_STACK_MATCH_NO_GAP`.

### Live capture values (from `beta35-live-floor-torch-contact-gap-red.md`)

```
supportVisibleTopY=-55.500000
torchModelBottomY=-57.000000
contactGap=-1.500000
```

### Discrepancy analysis

`supportVisibleTopY = supportCandidatePos.getY() + supportDy + getSupportYOffset(state)`

In live: `-56 + (-0.5) + getSupportYOffset = -55.5` → `getSupportYOffset = 1.0`
In fixture: `-56 + (-0.5) + getSupportYOffset = -56.0` → `getSupportYOffset = 0.5`

`torchModelBottomY = torchPos.getY() + outline.minY_relative`

In live: `-55 + outline.minY_relative = -57.0` → `outline.minY_relative = -2.0`
In fixture: `-55 + outline.minY_relative = -56.0` → `outline.minY_relative = -1.0`

The live capture implies `getSupportYOffset=1.0` for a bottom slab and a torch outline
starting at -2.0 relative to the torch block. The fixture measures `getSupportYOffset=0.5`
and torch outline at -1.0 relative. The source of this 0.5-block discrepancy in
`getSupportYOffset` and 1.0-block discrepancy in the torch outline requires a separate
recorder/contact-math audit.

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
