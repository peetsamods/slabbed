# Beta 3.5 Floor Torch V2 Contact Gap RED

## Status

V2 contact-gap proof now reproduces the live +0.5 condition for `top_slab_support` and
`bottom_slab_support` with exact coordinates and source truth:
- `supportDy=-1.000` in both cases.
- `torchDy=-0.500` (top) and `torchDy=-1.000` (bottom).
- `contactGap=0.500000` in both cases.
- `fixtureMatchesV2LiveStack=true`.

Current blocker:
- in-scope `floor_torch` contact gap remains `+0.500` and must be fixed in production code
  (`floor_torch` contact-height correction slice).

A previous blocker state `SOURCE_TRUTH_MISMATCH` is superseded by the new source-truth
parity slice; this doc now reflects the `save/beta35-floor-torch-v2-source-truth-parity`
savepoint evidence.

## Coverage

- `wall_torch=NOT_COVERED`
- `lantern`, `signs`, `chains` remain `NOT_COVERED`.
- No production gameplay fix applied.
- No release tag moved.
