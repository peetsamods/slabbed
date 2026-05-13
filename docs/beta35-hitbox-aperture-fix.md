# Beta 3.5 Visible Hit Aperture Fix

Slice base: `48550c7` / `save/beta35-hitbox-aperture-contact-red`.

Evidence folder: `tmp/beta35-hitbox-aperture-fix-48550c7/`.

## Why

The corrected `48550c7` proof found three buckets:

- `BUTTON_FLOOR_CONTACT_DY_MISSING`
- `CHAIN_AXIS_CONTACT_METRIC_MISSING`
- `HITBOX_AIM_APERTURE_TOO_NARROW`

This slice fixes only `HITBOX_AIM_APERTURE_TOO_NARROW`, which maps directly to Julia's player-feel complaint that hitboxes are finicky and require weird aim.

## Change

`GameRendererCrosshairRetargetMixin` extends the existing Beta 3.5 object-shape owner-preserve scan to include a lowered non-slab visible object only when:

- the visible object itself resolves lowered,
- the direct support below it is a slab,
- that support resolves to `supportDy=-1.0`, and
- the ray intersects the visible object's outline/raycast shape.

This is an owner/selection aperture fix only. Empty overhang remains non-owning, and the fix does not use collision overhang as the visual target.

## Proof

Focused flag: `-Dslabbed.beta35HitboxApertureFix=true`.

Summary:

`JULIA_BETA35_HITBOX_APERTURE_FIX_SUMMARY outcome=GREEN rows=6 apertureTooNarrowRowsBefore=4 apertureTooNarrowRowsAfter=0 emptyOverhangStealRows=0 visibleBodyGreen=yes visibleTopGreen=yes edgeClassification=GREEN supportSeamClassification=GREEN buttonContactGapStillDeferred=yes chainAxisMetricStillDeferred=yes fixtureMismatchRows=0 failureLayer=NONE productionBehaviorChanged=true releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false`

Corrected RED proof after the fix:

`JULIA_BETA35_HITBOX_APERTURE_CONTACT_SUMMARY outcome=RED rows=8 green=6 red=2 buttonContactGapRows=1 chainMetricGapRows=1 supportMetricNoiseRows=0 apertureTooNarrowRows=0 fixtureMismatchRows=0 firstFailureLayer=BUTTON_FLOOR_CONTACT_DY_MISSING nextRecommendedFix=MIXED buttonContactGapStillDeferred=yes chainAxisMetricStillDeferred=yes apertureFixApplied=true productionBehaviorChanged=aperture_owner_scan_only releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false`

## Deferred

Button floor contact remains a known RED:

`BUTTON_FLOOR_CONTACT_DY_MISSING`

Chain axis metric remains a known RED:

`CHAIN_AXIS_CONTACT_METRIC_MISSING`

Those buckets were not fixed in this slice.

## Scope

No button contact fix. No chain axis metric fix. No global hit tolerance widening. No server accept bypass. No global collision lowering. No solidity or sturdy-face lies. No broad all-item gameplay claim. No release audit run. No release tag moved.
