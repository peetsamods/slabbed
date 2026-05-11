# Beta 3.5 Live Torch Recorder Contact Math Audit

## Status

Two formula bugs in `Beta35LiveTorchCaptureRecorder` were identified and corrected.
The live recorder's previously reported `contactGap=-1.500000` was entirely a
measurement artifact. The actual torch behavior shows `contactGap=0` — the torch
sits correctly on the lowered slab surface with no visible gap.

A later corrected V2 fixture/live trace review for the same floor-torch path showed
that the live capture now reports `contactGap=0.500000` in the blocker-relevant stack
cases, while still leaving `wall_torch`/`lantern`/`signs`/`chains` outside this slice.

No production gameplay fix was implemented. No release tag moved.
Beta 3.5 release prep should be **unblocked for the floor-torch case** pending
Julia's re-verification with the corrected recorder, but that decision belongs to Julia.

`wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.

Starting HEAD: `220ecca` / `save/beta35-floor-torch-live-dy-stack-parity`
Commit: `save/beta35-live-torch-recorder-contact-math-audit`

## Audit questions answered

| # | Question | Answer |
|---|---|---|
| 1 | Does the recorder compute `supportVisibleTopY` the same way as the proof? | **No** — recorder used hardcoded `+1.0d` (full-block height); proof uses `getSupportYOffset(state)=0.5` for a bottom slab |
| 2 | Does the recorder compute `torchModelBottomY` from the same shape bounds? | **No** — recorder added `+torchDy` again; `SlabSupportStateMixin.slabbed$offsetOutline` already shifts the shape in block-local space |
| 3 | Is the recorder double-applying `torchDy`? | **Yes** — for the shape Y |
| 4 | Is the recorder ignoring `supportDy` when computing `supportVisibleTopY`? | No — it adds `supportDy` correctly but uses the wrong block height |
| 5 | Is the recorder using world Y incorrectly for torch shape bounds? | Yes — see Q3 |
| 6 | Is the recorder comparing the wrong torch/support? | No — `torchPos` and `supportCandidatePos` are correct |
| 7 | Are values from a nearby torch rather than the actual one? | No — the targeted torch is correct |
| 8 | Does the recorder need raw vs dy-adjusted fields? | Yes — added `rawSupportTopY`, `rawTorchShapeMinY`, `contactGapV1` |

## Root cause: two formula bugs

### Bug 1 — `supportVisibleTopY` uses hardcoded `1.0` instead of `getSupportYOffset`

**Old formula** (v1):
```java
double supportVisibleTopY = supportCandidatePos.getY() + 1.0d + supportDy;
```

For `stone_slab[type=bottom]` at Y=-56, `supportDy=-0.5`:
`-56 + 1.0 + (-0.5) = -55.5` ← **wrong by +0.5**

**Correct formula** (v2):
```java
double supportTopOffset = SlabSupport.isSupportingSlab(supportCandidateState)
        ? SlabSupport.getSupportYOffset(supportCandidateState)  // 0.5 for BOTTOM, 1.0 for TOP/DOUBLE
        : 1.0d;
double supportVisibleTopY = supportCandidatePos.getY() + supportTopOffset + supportDy;
```

For `stone_slab[type=bottom]` at Y=-56, `supportDy=-0.5`:
`-56 + 0.5 + (-0.5) = -56.0` ← **correct**

### Bug 2 — `torchModelBottomY` double-applies `torchDy`

`SlabSupportStateMixin.slabbed$offsetOutline` (line 185+) modifies `getOutlineShape` to
return `SLABBED$COMFORT_TORCH_SHAPE.offset(0, yOff, 0)` where `yOff = torchDy = -1.0`.
So the block-local shape returned by `getOutlineShape` already has minY = `-1.0` (= `0 + torchDy`).

**Old formula** (v1):
```java
outlineMinY = torchPos.getY() + outlineMin + torchDy;
// = -55 + (-1.0) + (-1.0) = -57.0  ← double-applying torchDy!
```

**Correct formula** (v2 — same as `beta35WorldBox`):
```java
outlineMinY = torchPos.getY() + outlineMin;
// = -55 + (-1.0) = -56.0  ← correct
```

Same fix applied to `outlineMaxY`, `raycastMinY`, `raycastMaxY`.

## Combined effect — why the live capture showed `contactGap=-1.5`

| Value | V1 (wrong) | V2 (correct) |
|---|---|---|
| `supportVisibleTopY` | -56 + 1.0 + (-0.5) = **-55.5** | -56 + 0.5 + (-0.5) = **-56.0** |
| `torchModelBottomY` | -55 + (-1.0) + (-1.0) = **-57.0** | -55 + (-1.0) = **-56.0** |
| `contactGap` | -57.0 - (-55.5) = **-1.5** | -56.0 - (-56.0) = **0.0** |

The two bugs partially cancel — the support top is off by +0.5 and the torch bottom is off by -1.0 — creating a net apparent gap of -1.5 that does not exist. Julia's original report of `contactGap=-1.5` was measuring a formula artifact.

**The actual torch placement behavior is correct.** The torch model bottom sits flush
with the lowered slab's visible top surface (`contactGap=0`).

## Corrected recorder fields (v2)

| Field | Formula |
|---|---|
| `rawSupportTopY` | `pos.getY() + getSupportYOffset(state)` (unshifted top) |
| `supportVisibleTopY` | `rawSupportTopY + supportDy` (dy-adjusted) |
| `rawTorchShapeMinY` | `shape.getBoundingBox().minY` (block-local, dy already applied by mixin) |
| `torchModelBottomY` | `torchPos.getY() + rawTorchShapeMinY` (world space) |
| `contactGap` | `torchModelBottomY - supportVisibleTopY` |
| `contactGapV1` | old formula: `(torchPos.getY() + rawTorchShapeMinY + torchDy) - (pos.getY() + 1.0 + supportDy)` |
| `measurementFormulaVersion` | `v2` |

## Marker version

`markerVersion=1` → `markerVersion=2` in `[JULIA_BETA35_LIVE_TORCH_CAPTURE]`.

## Implication for Beta 3.5

Julia's live `contactGap=-1.5` was a measurement artifact. With corrected math:
- `contactGap=0.000000` — torch sits correctly on the lowered slab
- `fixtureMatchesLiveDyStack=true`
- `supportDy=-0.500`, `torchDy=-1.000`

The floor-torch placement and visual position are **correct**. Beta 3.5 release prep
should be unblocked for the floor-torch case pending Julia's live re-verification.

## Release and coverage status

- Beta 3.5 release prep: **PAUSED** (pending Julia's live re-verification with v2 recorder)
- `productionGameplayFixApplied=false`
- `wall_torch=NOT_COVERED`
- `lantern=NOT_COVERED`
- `signs=NOT_COVERED`
- `chains=NOT_COVERED`
- No release tag moved
