# Beta 3.5 Floor Torch Live Capture Recorder

## Status

Recorder contact math corrected in `save/beta35-live-torch-recorder-contact-math-audit`
(v2 formula). The previously reported `contactGap=-1.500000` was a measurement artifact.
With the corrected recorder, `contactGap=0` — the torch sits correctly on the lowered slab.
No gameplay fix is implemented. Beta 3.5 release prep remains PAUSED pending Julia’s
live re-verification with the corrected recorder. See
`docs/beta35-live-torch-recorder-contact-math-audit.md` for the full analysis.

## Gate and marker

Enable capture with:

```
-Dslabbed.beta35LiveTorchCapture=true
```

Recorder lines emit `[JULIA_BETA35_LIVE_TORCH_CAPTURE]` with:

- `classification`
- `targetType`, `targetPos`, `targetFace`, `targetState`
- `torchPos` when targeted torch is found directly
- `nearestTorchPos` and `searchRadius` when the target is nearby but not torch
- `torchState`
- `supportCandidatePos`, `supportCandidateState`
- `torchDy`, `supportDy`, `supportVisibleTopY`, `torchModelBottomY`
- `contactGap`, `outlineMinY`, `outlineMaxY`, `raycastMinY`, `raycastMaxY`
- `heldItem`
- `playerPos`, `playerLookYaw`, `playerLookPitch`, `cameraPos`, `worldCoords`

## Notes

- Logger output is evidence-only and does not alter gameplay.
- Beta 3.5 release remains paused pending Julia live capture match.
- wall_torch, lantern, signs, and chains remain `NOT_COVERED` for the current
  floor-torch contact-gap RED slice.

## Floor-torch contact-gap follow-up proof

The in-scope floor-torch-only live mismatch pattern is now captured with
`-Dslabbed.beta35LiveFloorTorchContactGapRed=true`, emitting:

- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]`
- `[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]`

Observed in-scope failure pattern includes
`supportCandidateState=Block{minecraft:stone_slab}[type=bottom,...]`,
`torchDy=-1.000000`, `supportDy=-0.500000`,
`supportVisibleTopY`, `torchModelBottomY`, and `contactGap` from
`CONTACT_GAP` captures.
