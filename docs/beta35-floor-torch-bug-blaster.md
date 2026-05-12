# Beta 3.5 Floor Torch Lowered Bottom Slab Source Truth

## Invariant

A floor torch on slab-supported geometry is not fixed merely because it can exist in a controlled fixture. It must place through the player path, survive, and visually contact the actual live support surface with model, outline, and raycast co-located.

## Root cause

The original Beta 3.5 object/torch proofs were false-green because they proved controlled fixture behavior, not Julia’s live SBSBS-style slab-supported structure. Once live tracing was added, the bug split into two layers: floor torch placement failed on lowered bottom slab support with `intendedSupportDy=-1.000000`, and existing torches could float because torch `dy` was computed too shallow for bottom-slab support source truth. The dy/contact failures appeared in two support paths: supportDy=-1.0 needed `torchDy=-1.5`, and plain lowered bottom slab support `supportDy=-0.5` needed `torchDy=-1.0`.

## Fix

Added narrow floor-torch-only `SlabSupport` authority for lowered bottom slab support, including `SlabSupport.isLegalFloorTorchLoweredBottomSlabSupport(...)`, reused by the existing torch placement/survival path. Updated floor-torch contact `dy` so lowered bottom slab support computes from the visible slab support surface. Added the live dual tracer to separate placement failure from visual contact failure, and classified duplicate occupied torch clicks as `OCCUPIED_TORCH_TARGET` instead of treating them as real placement failures.

## Proof

- `66ca74a` / `save/beta35-floor-torch-lowered-slab-placement`:
  - placement GREEN with `supportDy=-1.0`
  - `finalInteractResult=Success[...]`
  - `torchBlockAppearedAfterAttempt=true`
  - `torchDy=-1.500000`
  - `contactGap=0.000000`
  - `survival=GREEN`
- `226cc6c` / `save/beta35-floor-torch-plain-bottom-contact`:
  - plain bottom slab support `supportDy=-0.5` contact GREEN
  - `torchDy=-1.000000`
  - `supportVisibleTopY == torchModelBottomY`
  - `contactGap=0.000000`
  - `survival=GREEN`
- `a9c2882` / `save/beta35-floor-torch-live-acceptance`:
  - Julia live acceptance recorded
  - `8/8` placement attempts `PLACEMENT_ATTEMPT_OK`
  - `PLACEMENT_REJECTED=0`
  - `COMFORT_NO_BOX_INTERSECTION=0`
  - `WRONG_TARGET_OWNER=0`
  - `PLACED_CONTACT_GREEN=1407`
  - `PLACED_CONTACT_GAP=0`
  - `max concrete floor-torch contactGap=0.000000`

## Savepoint

- Final docs savepoint: `a9c2882`
- Tag: `save/beta35-floor-torch-live-acceptance`
- Branch pushed: yes
- Tag pushed: yes

## Status

- Fixed for `floor_torch_only`.
- Out of scope / not covered: `wall_torch`, `lanterns`, `signs`, `chains`.
- No release tag moved yet.
- `wall_torch` air-support contact-gap rows are out of Beta 3.5 scope and do not block.
