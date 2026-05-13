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

The tracer/proof did not prove a gameplay mechanism yet. It therefore does not justify a generic fix or a category-specific fix.

## Scope

No gameplay fix was implemented. No global hit tolerance was widened. No server accept bypass was added. No global solidity or sturdy-face behavior was changed. No broad all-item support was attempted. No release audit was run, and no release tag was moved.
