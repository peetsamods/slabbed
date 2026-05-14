# Beta 3.5 Lowered Trapdoor Server Validation Fix

Slice base: `22ec3f2` / `save/beta35-visible-object-owner-stability`.

Evidence folder: `tmp/beta35-trapdoor-server-slab-jump-fix-22ec3f2/`.

## Why

Julia's 10:47 live source truth after the visible-owner savepoint showed trapdoor selection was stable client-side, but some lowered trapdoors above one slab height would not stay open. The visible interaction appeared to succeed, then the trapdoor repeatedly closed or snapped back.

The live rows identify the split: trapdoor owner and hit acceptance are mostly green, `SERVER_HIT_TOO_FAR=0`, but some legal `targetDy=-1.000000` bottom-trapdoor hits reached server validation with `shiftedValidationCenter=null`.

Primary failure layer:

`LOWERED_TRAPDOOR_SERVER_SHIFTED_VALIDATION_GAP`

This is not a visible-owner support steal, not a button contact gap, not a chain owner failure, and not a fence/wall regression.

## Change

- `SlabSupport` now has a narrow server-validation helper for lowered bottom trapdoor targets with finite negative dy.
- `ServerInteractBlockHitToleranceMixin` uses that helper for the existing shifted-center validation path.
- `Beta35SlabHeightHitAcceptanceRecorder` reports the trapdoor-specific failure layer when a lowered bottom-trapdoor target is legal but missing shifted validation.
- Trapdoor-family shifted dy is kept on the server-validation path; the general contact-dy path remains unchanged so prior visible-owner/client proof behavior is preserved.

The shifted hit still has to pass the existing vanilla component tolerance after applying target dy. No global hit tolerance was widened. No server accept bypass was added. No global collision lowering or sturdy-face lie was added.

## Proof Contract

Focused proof flag:

`-Dslabbed.beta35TrapdoorServerValidationFix=true -Dslabbed.beta35SlabHeightHitAcceptance=true`

Required markers:

- `JULIA_BETA35_TRAPDOOR_SERVER_VALIDATION_RED`
- `JULIA_BETA35_TRAPDOOR_SERVER_VALIDATION_GREEN`
- `TRAPDOOR_SERVER_NEGATIVE_GREEN`
- `JULIA_BETA35_TRAPDOOR_OPEN_STATE_GREEN`
- `JULIA_BETA35_TRAPDOOR_SERVER_VALIDATION_SUMMARY`

Expected after rows:

- `minecraft:oak_trapdoor`, held `minecraft:stone_slab`
- `minecraft:mangrove_trapdoor`, held `minecraft:mangrove_trapdoor`
- `minecraft:birch_trapdoor`, held `minecraft:torch`
- `minecraft:oak_trapdoor`, held `minecraft:acacia_button`

Each green row must report `targetDy=-1.000000`, a present `shiftedValidationCenterY=validationCenterY + targetDy`, shifted delta inside vanilla component tolerance, `classification=SERVER_SHIFTED_HIT_GREEN`, and `failureLayer=NONE`.

The negative boundary must remain rejected:

`classification=TRAPDOOR_SERVER_NEGATIVE_GREEN accepted=false failureLayer=HIT_ACCEPTANCE_SERVER_REJECT_NON_OWNABLE_DY_RELATION`

The open-state proof must report `serverAccepted=true` and `finalStateOpen=true`. Julia live acceptance is still required after this automated proof.

## Scope

This is an automated-proof green target only. It does not run a release audit, move a release tag, or claim all-item gameplay support.
