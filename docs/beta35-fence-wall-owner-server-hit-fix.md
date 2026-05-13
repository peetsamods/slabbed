# Beta 3.5 Fence/Wall Owner Server Hit Fix

Implementation slice after `fbbbd68` / `save/beta35-fence-wall-live-reject-tracer`.

Evidence folder: `tmp/beta35-fence-wall-owner-server-hit-fix-fbbbd68/`.

## Why

The `fbbbd68` live tracer proved the contact-height fix was real but incomplete:

- `LIVE_CONTACT_GREEN=2058`
- `LIVE_CONTACT_GAP=0`
- `LIVE_TRIAD_MISMATCH=0`
- `LIVE_OWNER_GAP=1495`
- `SERVER_HIT_TOO_FAR=2`

Previous failure layer: `LIVE_OWNER_GAP_PLUS_SERVER_SHIFTED_HIT_TOLERANCE`.

The live owner rows showed green contact and co-located model/outline/raycast/collision bounds, but final targeting could fall through to `scan-side-slab-fired`. The server rows showed legal lowered hits compared against an unshifted vanilla center; shifting the center by the legal Slabbed target dy put the hit vector back inside vanilla component tolerance.

## Fix

Client owner priority:

- `GameRendererCrosshairRetargetMixin` keeps the existing side-slab scan for normal cases.
- If the current-distance-limited owner scan finds no visible object owner, it performs a narrow Beta 3.5 owner scan along the actual ray.
- That scan is gated to the proven lowered object-owner set only: `FenceBlock` / `WallBlock` family through `SlabSupport.isBeta35FenceWallVariantContactObject(...)`, plus exact `minecraft:anvil`.
- When the corrected visible object shape is intersected, final decision becomes `object-shape-owner-preserve` instead of `scan-side-slab-fired`.

Server shifted-hit validation:

- `ServerInteractBlockHitToleranceMixin` keeps vanilla component tolerance.
- For legal Slabbed-lowered targets with negative dy and a fence/wall/anvil target, above-object, or held object context, it compares the packet hit vector against the shifted Slabbed center.
- The shifted center is used only when the hit is within vanilla component tolerance after applying the Slabbed dy.
- No global tolerance widening or accept bypass was added.

Contact dy from `57d651a` was not rewritten.

## Result

Focused proof:

- Gate: `-Dslabbed.beta35FenceWallOwnerServerHit=true -Dslabbed.beta35FenceWallLiveInspect=true`
- Summary: `JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_SUMMARY outcome=GREEN previousFailureLayer=LIVE_OWNER_GAP_PLUS_SERVER_SHIFTED_HIT_TOLERANCE failureLayer=NONE liveOwnerGapAfter=0 serverHitTooFarAfter=0 contact=LIVE_CONTACT_GREEN triad=LIVE_TRIAD_GREEN contactDyRewritten=false globalHitToleranceWidened=false releaseAudit=NOT_RUN releaseTagMoved=false`
- Green marker: `JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_GREEN owner=LIVE_OWNER_GREEN finalDecision=object-shape-owner-preserve server=SERVER_SHIFTED_HIT_GREEN failureLayer=NONE releaseAudit=NOT_RUN releaseTagMoved=false`

Reproduced server row:

- Before: `validationCenter=(1220.500,-54.500,188.500)`, `validationDelta=(-0.415,-1.159,0.500)`, `SERVER_HIT_TOO_FAR`.
- After: `shiftedValidationCenter=(1220.500,-55.500,188.500)`, `shiftedValidationDelta=(-0.415,-0.159,0.500)`, `SERVER_SHIFTED_HIT_GREEN`.

Contact and triad remain green: `contactGap=0.000000`, `triadCoLocated=yes`.

## Regression Gates

- Compile: `./gradlew --no-daemon compileJava compileGametestJava` -> `BUILD SUCCESSFUL`
- Owner/server-hit proof: `BUILD SUCCESSFUL`
- Fence/wall contact-hitbox proof: `BUILD SUCCESSFUL`
- Live-hitbox owner proof for wall/fence/anvil: `BUILD SUCCESSFUL`
- Live-hitbox gate proof: `BUILD SUCCESSFUL`
- Fence/wall family proof: `BUILD SUCCESSFUL`
- Fence-gate family proof: `BUILD SUCCESSFUL`
- Common-object matrix: `BUILD SUCCESSFUL`
- Floor torch, candle, flower pot contact/survival regressions: `BUILD SUCCESSFUL`
- Default client gametest: `BUILD SUCCESSFUL`
- runClient live-inspect smoke emitted `[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] enabled=true`.
- `git diff --check` -> clean

Release audit remains paused pending Julia live acceptance. No release tag was moved. Scope remains fence/wall/anvil owner/server-hit behavior plus existing floor_torch/candle/flower_pot regression coverage; there is no all-item claim.

## Follow-Up: Stack Contact

Julia's live acceptance after `aa66efd` kept server validation green (`SERVER_HIT_TOO_FAR=0`) but exposed one concrete contact bucket: a wall stacked on lowered wall-family support with `objectDy=-0.500000`, `supportDy=-1.000000`, and `contactGap=0.500000`.

The follow-up stack-contact slice fixes only that fence/wall-family stack dy path. The focused proof now reports `JULIA_BETA35_FENCE_WALL_STACK_CONTACT_SUMMARY outcome=GREEN ... failureLayer=NONE`, with wall-on-wall and fence stack equivalents green.

No server hit tolerance rewrite, global tolerance widening, release audit, release tag movement, or all-item expansion is included.
