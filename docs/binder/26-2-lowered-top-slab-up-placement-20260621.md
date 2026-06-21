# 26.2 lowered top-slab UP-face placement

## Symptom

Julia found a WYSIWYG violation in live 26.2: placing a slab on the visible UP face of a lowered, side-placed TOP slab
created/merged the new slab underneath the clicked slab instead of stacking it on the visible top surface.

## Fix

`BlockItemPlacementIntentMixin` now skips the synthetic TOP-slab `UP` -> `DOWN` merge rewrite when the clicked slab is
already lowered. The same WYSIWYG follow marker used by lowered side-face placement now also marks `UP` clicks on lowered
slabs, so `freezeLoweredOnPlace` anchors the predicted above-cell slab at `dy=-0.5`.

## Proof

- `tmp/runGameTest-wysiwyg-lowered-top-slab.log`
- Result: 131/131 required tests passed.
- Key row: `USEON-FP | wysiwyg_slab_on_lowered_top_slab_up_face | dy=-0.500 | type=bottom`

## Status

Headless fixed, and Julia later confirmed the specific stacking behavior works live. This note is now superseded by the
separate slab-held targeting / WYSIWYG blocker recorded in `26-2-slab-held-target-owner-identity-20260621.md`.
