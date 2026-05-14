# Beta 3.5 Hitbox Aperture Contact RED Proof

Diagnostic correction slice after `63a0e32` / `save/beta35-slab-height-hit-acceptance-red`.

Evidence folder: `tmp/beta35-hitbox-aperture-contact-red-63a0e32/`.

## Why

Julia's live retest showed the `63a0e32` slab-height hit acceptance matrix was a false green. The tracer logged accepted targets but did not fail nonzero contact metrics, and it did not measure aim aperture. The visible issue is player feel: hitboxes are finicky and require weird aim around some objects.

Concrete live false green: `minecraft:acacia_button[face=floor]` on `minecraft:stone_slab[type=top]` reported `supportDy=-1.000000`, `objectDy=-0.500000`, `contactGap=0.500000`, `classification=HIT_ACCEPTANCE_GREEN`, `failureLayer=NONE`.

## Added

- Corrected metric-first classification in the slab-height tracer.
- Metric type field: `FLOOR_CONTACT`, `SIDE_FACE_CONTACT`, `AXIS_CONTACT`, `OWNER_ONLY`, or `SUPPORT_TARGET`.
- Corrected proof flag: `-Dslabbed.beta35HitboxApertureContactRed=true`.
- Row marker: `JULIA_BETA35_HITBOX_APERTURE_CONTACT_RED_ROW`.
- Summary marker: `JULIA_BETA35_HITBOX_APERTURE_CONTACT_SUMMARY`.
- Aim zones: `VISIBLE_BODY`, `VISIBLE_TOP`, `EDGE`, `SUPPORT_SEAM`, `EMPTY_OVERHANG`, `NEAR_MISS`.

## Result

Corrected local proof:

`JULIA_BETA35_HITBOX_APERTURE_CONTACT_SUMMARY outcome=RED rows=8 green=2 red=6 buttonContactGapRows=1 chainMetricGapRows=1 supportMetricNoiseRows=0 apertureTooNarrowRows=4 fixtureMismatchRows=0 firstFailureLayer=BUTTON_FLOOR_CONTACT_DY_MISSING nextRecommendedFix=MIXED`

Proven layers:

- `BUTTON_FLOOR_CONTACT_DY_MISSING`: floor acacia button has `contactGap=0.500000` at `supportDy=-1.000000`.
- `CHAIN_AXIS_CONTACT_METRIC_MISSING`: chain needs an axis/contact metric before it can be classified green.
- `HITBOX_AIM_APERTURE_TOO_NARROW`: visible body, top, edge, and support seam rows miss in the replay fixture.

## Follow-up Aperture Fix

Follow-up savepoint: `save/beta35-hitbox-aperture-fix`.

Focused proof flag: `-Dslabbed.beta35HitboxApertureFix=true`.

The aperture slice fixes only `HITBOX_AIM_APERTURE_TOO_NARROW`. The owner path now lets the existing object-shape owner-preserve scan consider a lowered non-slab visible object over a direct `supportDy=-1.0` slab support, and only when the ray intersects the visible outline/raycast shape. Empty overhang remains non-owning.

Focused aperture result:

`JULIA_BETA35_HITBOX_APERTURE_FIX_SUMMARY outcome=GREEN rows=6 apertureTooNarrowRowsBefore=4 apertureTooNarrowRowsAfter=0 emptyOverhangStealRows=0 visibleBodyGreen=yes visibleTopGreen=yes edgeClassification=GREEN supportSeamClassification=GREEN buttonContactGapStillDeferred=yes chainAxisMetricStillDeferred=yes fixtureMismatchRows=0 failureLayer=NONE`

Corrected RED proof after the aperture fix:

`JULIA_BETA35_HITBOX_APERTURE_CONTACT_SUMMARY outcome=RED rows=8 green=6 red=2 buttonContactGapRows=1 chainMetricGapRows=1 supportMetricNoiseRows=0 apertureTooNarrowRows=0 fixtureMismatchRows=0 firstFailureLayer=BUTTON_FLOOR_CONTACT_DY_MISSING nextRecommendedFix=MIXED buttonContactGapStillDeferred=yes chainAxisMetricStillDeferred=yes apertureFixApplied=true`

Deferred known REDs remain:

- `BUTTON_FLOOR_CONTACT_DY_MISSING`: the floating floor button contact gap is not fixed here.
- `CHAIN_AXIS_CONTACT_METRIC_MISSING`: chain axis/contact metric work is not fixed here.

## Scope

The original `48550c7` slice was diagnostics-only. The follow-up aperture slice changes only visible-target owner acceptance for the proven slab-height aperture fixture. No button contact fix was implemented. No chain axis metric fix was implemented. No global hit tolerance was widened. No server accept bypass was added. No global collision, solidity, or sturdy-face behavior was changed. No broad all-item support claim was made. No release audit was run, and no release tag was moved.

## Continuation: Visible Owner Stability

Continuation base: `05f1582` / `save/beta35-hitbox-aperture-fix`.

The stopped WIP after `05f1582` retained the floor-button contact fix and added the next live-blocker proof: visible-object owner stability for trapdoor and vertical-chain rows where the slab/support target previously stole ownership.

Current corrected RED proof after the continuation:

`JULIA_BETA35_HITBOX_APERTURE_CONTACT_SUMMARY outcome=NOT_REPRODUCED rows=8 green=8 red=0 buttonContactGapRows=0 chainMetricGapRows=0 apertureTooNarrowRows=0 firstFailureLayer=NONE buttonContactGapStillDeferred=no chainAxisMetricStillDeferred=no apertureFixApplied=true floorButtonContactFixApplied=true releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false`

Visible-owner proof:

`JULIA_BETA35_VISIBLE_OBJECT_OWNER_STABILITY_SUMMARY outcome=GREEN trapdoorSupportStealRowsAfter=0 chainSupportStealRowsAfter=0 chainOwnerGreenRows=2 chainMetricDeferredRows=2 slabJumpRowsAfter=0 serverRejectRows=0 failureLayer=NONE`

Chain axis metric is now owner-green/deferred bookkeeping for the reproduced rows, not a support-steal owner failure.

This continuation still does not run a release audit, move release tags, or claim all-item support. Julia live acceptance is still required.
