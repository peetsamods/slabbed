# Beta 3.5 Floor Torch Live Acceptance (226cc6c)

- Date: 2026-05-11
- Scope: `floor_torch_only`
- Base savepoint: `save/beta35-floor-torch-plain-bottom-contact` / `226cc6c`
- Evidence folder: `tmp/beta35-floor-torch-live-acceptance-savepoint-226cc6c`

## Julia live acceptance result

- `tmp/julia-live-torch-acceptance-226cc6c` trace
- Dual tracer enabled: `true`
- 8 live placement attempts, `PLACEMENT_ATTEMPT_OK` (8/8)
- `PLACEMENT_REJECTED = 0`
- `COMFORT_NO_BOX_INTERSECTION = 0`
- `WRONG_TARGET_OWNER = 0`
- `PLACED_CONTACT_GREEN = 1407`
- `PLACED_CONTACT_GAP = 0`
- `max concrete floor_torch contactGap = 0.000000`

## Supported floor_torch rows observed

- lowered bottom slab support (`supportDy=-1.0`) → GREEN
- `COMPOUND_VISIBLE_SIDE_LOWER_SLAB` and `COMPOUND_VISIBLE_SIDE_UPPER_SLAB`
- plain bottom support (`supportDy=-0.5`) with `contactGap=0.000000`
- placement rows include stone and double-slab contexts
- duplicate/occupied target traces no longer block as release failures

## Scope guard

- `wall_torch`, `lantern`, `signs`, `chains`: `NOT_COVERED`
- legacy `JULIA_BETA35_LIVE_TORCH_CAPTURE` wall-torch air-support rows are
  out of scope for Beta 3.5 floor torch acceptance and do not block this savepoint

## Release status

- `Beta 3.5 floor_torch_only` is live accepted and documented as a docs-only
  savepoint.
- No gameplay code changes in this slice.
- No release tag movement in this slice.
- Next recommended slice: Beta 3.5 release-readiness audit from
  `226cc6c` / this live acceptance savepoint.
