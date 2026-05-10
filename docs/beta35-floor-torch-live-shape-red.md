# Beta 3.5 Floor Torch Live-Shape Fixture Parity Audit

## Status

Julia's manual visual verdict remains **NOT ACCEPTED**.

This slice adds a screenshot-style live-shape fixture parity proof for
`floor_torch` only. It does **not** implement a production gameplay fix. Beta
3.5 release prep remains **PAUSED**.

A new gated live recorder was added for Julia's manual floor-torch mismatch repro:

- Gate: `-Dslabbed.beta35LiveTorchCapture=true`
- Marker: `[JULIA_BETA35_LIVE_TORCH_CAPTURE]`
- Classification: `LIVE_CAPTURE_OK`, `NO_TORCH_TARGET`,
  `TORCH_FOUND_NEAR_TARGET`, `CONTACT_GAP`, `UNKNOWN`

## Gate and markers

Gate:

```
-Dslabbed.beta35FloorTorchLiveShapeRed=true
```

Primary markers:

```
[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_FIXTURE_GREEN]
[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_CONTACT_MEASURED]
[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_SUMMARY]
```

Status marker emitted per run:

- `[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_RED]`
- `[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_PENDING]`
- `[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_GREEN]`

## Fixture intent

The fixture mirrors the screenshot family more closely than the simple
controlled contact fixture by seeding a multi-level slab/full structure and
placing floor torches around upper/flanking positions, then measuring one
player-placed floor torch on a slab-supported lane.

Scope remains `floor_torch_only`.

- `wall_torch`: NOT_COVERED
- `lantern`: NOT_COVERED
- `signs`: NOT_COVERED
- `chains`: NOT_COVERED

## Measured result

Focused run emitted:

- `expectedTorchPos=67,202,0`
- `actualTorchPos=67,202,0`
- `supportPos=67,201,0`
- `supportState=stone_slab[type=bottom]`
- `supportDy=-0.500`
- `supportVisibleTopY=201.000000`
- `torchDy=-1.000`
- `torchModelBottomY=201.000000`
- `torchModelTopY=201.625000`
- `contactGap=0.000000`
- `triad=GREEN`
- `liveShapeProofStatus=GREEN`
- `failureLayer=NONE`

## Failure-layer contract

The proof classifies into:

- `LIVE_SHAPE_CONTACT_GAP`
- `LIVE_SHAPE_WRONG_SUPPORT_OWNER`
- `LIVE_SHAPE_WRONG_DY`
- `LIVE_SHAPE_PROOF_GAP`
- `NONE`

Current run landed at `NONE`, which means this parity fixture still does not
reproduce the manual complaint directly.

## Interpretation

Both the controlled fixture and this closer live-shape fixture currently measure
zero model/support gap for the tested floor torch placement. That does **not**
authorize release prep and does **not** imply Julia's manual concern is invalid.
It indicates that a more specific live coordinate/face/owner capture is needed
for the exact manual complaint path.

- Beta 3.5 release prep remains **PAUSED**.
- Do not claim `floor_torch_only` release scope accepted.
- No production gameplay fix was implemented in this slice.
- No release tag was moved by this proof itself.
