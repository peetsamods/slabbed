# Beta 3.5 Floor Button Contact Fix

Slice base: stopped dirty WIP on `05f1582` / `save/beta35-hitbox-aperture-fix`.

Evidence folder: `tmp/beta35-floor-button-contact-fix-05f1582/visible-owner-stability-opus/`.

## Result

The floor-button WIP is retained inside the visible-object owner stability savepoint.

Focused proof:

`JULIA_BETA35_FLOOR_BUTTON_CONTACT_SUMMARY outcome=GREEN rows=4 green=4 red=0 supportDyNegOneObjectDy=-1.000000 supportDyNegOneContactGap=0.000000 supportDyNegHalfObjectDy=-1.000000 supportDyNegHalfContactGap=0.000000 normalSupportObjectDy=0.000000 normalSupportContactGap=0.000000 unsupportedStatus=UNSUPPORTED_EXPECTED wallCeilingButtons=NOT_COVERED failureLayer=NONE`

## Scope

This fixes floor-button contact for the proven floor-button rows only. Wall and ceiling buttons remain `NOT_COVERED`.

No broad button family claim. No global collision lowering. No global hit tolerance widening. No release audit run. No release tag moved.
