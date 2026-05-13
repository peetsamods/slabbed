# Beta 3.5 Slab-Height Hit Acceptance RED Tracer

Diagnostic slice after `eec3bc0` / `save/beta35-fence-wall-visual-hitbox-stack-aim`.

Evidence folder: `tmp/beta35-slab-height-hit-acceptance-red-eec3bc0/`.

## Why

Julia's live verdict after `eec3bc0` was near-acceptance for fence/wall, but one slab height still showed trouble. Lantern, chain, and button are examples from the video, not scope boundaries.

The active question is generic: at the problematic slab height/lane, does the visible target keep hit acceptance and ownership for arbitrary held items that should be allowed to target it?

## Added

- Gated live tracer: `-Dslabbed.beta35SlabHeightHitAcceptance=true`
- Startup marker: `[JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE] enabled=true`
- Focused matrix proof: `-Dslabbed.beta35SlabHeightHitAcceptanceRed=true`
- Row marker: `JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE_RED_ROW`
- Summary marker: `JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE_SUMMARY`

The matrix covers support `dy=-1.0`, support `dy=-0.5`, lowered top/double support variants, compound lowered full-block support, and held stone/slab/stairs/trapdoor/lantern/iron_chain/button/torch/candle/flower_pot, plus an anvil visible-owner baseline.

## Result

Focused local matrix:

`JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE_SUMMARY outcome=NOT_REPRODUCED rows=51 green=51 red=0 ownerGap=0 miss=0 supportSteal=0 sideAttachmentGap=0 survivalGap=0 fixtureMismatch=0 exactProblematicSlabHeight=NOT_REPRODUCED_IN_MATRIX heldItemIndependent=NO categorySpecific=NO failureLayer=HIT_ACCEPTANCE_FIXTURE_MISMATCH nextRecommendedFix=live_fixture_capture_or_Julia_exact_height_replay`

That matrix is superseded as proof evidence after Julia's live retest. It was a false green because it let target ownership dominate `HIT_ACCEPTANCE_GREEN` before checking nonzero contact metrics, and it sampled accepted hits without measuring visible-body/top/edge/seam/overhang aim aperture.

Concrete false-green live row: `minecraft:acacia_button[face=floor]` on `minecraft:stone_slab[type=top]` had `supportDy=-1.000000`, `objectDy=-0.500000`, `contactGap=0.500000`, and was reported as `HIT_ACCEPTANCE_GREEN`. Correct classification is `BUTTON_CONTACT_GAP` with failure layer `BUTTON_FLOOR_CONTACT_DY_MISSING` or the generic slab-height contact dy layer.

Corrected diagnostic proof: `-Dslabbed.beta35HitboxApertureContactRed=true`. Latest summary: `JULIA_BETA35_HITBOX_APERTURE_CONTACT_SUMMARY outcome=RED rows=8 green=2 red=6 buttonContactGapRows=1 chainMetricGapRows=1 supportMetricNoiseRows=0 apertureTooNarrowRows=4 fixtureMismatchRows=0 firstFailureLayer=BUTTON_FLOOR_CONTACT_DY_MISSING nextRecommendedFix=MIXED`.

That corrected proof named three layers: `BUTTON_FLOOR_CONTACT_DY_MISSING`, `CHAIN_AXIS_CONTACT_METRIC_MISSING`, and `HITBOX_AIM_APERTURE_TOO_NARROW`.

Follow-up aperture fix: `-Dslabbed.beta35HitboxApertureFix=true` / `save/beta35-hitbox-aperture-fix`.

Focused aperture result: `JULIA_BETA35_HITBOX_APERTURE_FIX_SUMMARY outcome=GREEN rows=6 apertureTooNarrowRowsBefore=4 apertureTooNarrowRowsAfter=0 emptyOverhangStealRows=0 visibleBodyGreen=yes visibleTopGreen=yes edgeClassification=GREEN supportSeamClassification=GREEN buttonContactGapStillDeferred=yes chainAxisMetricStillDeferred=yes fixtureMismatchRows=0 failureLayer=NONE`.

Corrected RED proof after the aperture fix: `JULIA_BETA35_HITBOX_APERTURE_CONTACT_SUMMARY outcome=RED rows=8 green=6 red=2 buttonContactGapRows=1 chainMetricGapRows=1 apertureTooNarrowRows=0 firstFailureLayer=BUTTON_FLOOR_CONTACT_DY_MISSING nextRecommendedFix=MIXED buttonContactGapStillDeferred=yes chainAxisMetricStillDeferred=yes apertureFixApplied=true`.

The current gameplay change is limited to the visible-target aim aperture: visible body/top/edge/support-seam rows now preserve the intended visible object when the ray intersects the object's visible outline/raycast shape, while empty overhang and near-miss rows do not steal owner. Button floor contact dy and chain axis metrics remain deferred known REDs.

## Scope

No button contact fix was implemented. No chain axis metric fix was implemented. No global hit tolerance was widened. No server accept bypass was added. No global solidity or sturdy-face behavior was changed. No broad all-item support claim was made. No release audit was run, and no release tag was moved.
