# 26.2 slab-held target owner identity

## Symptom

After the lowered top-slab UP-face stacking fix, Julia live-confirmed stacking works but found a separate targeting
violation: holding a slab can outline/select a different owner than the block visually under the crosshair, while holding
another item targets the visible block.

## Product Law

This is not an intentional deep-compound stacking mechanic. Deep stacking may constrain whether a slab placement is legal
or which lane a slab can occupy, but it must not lie about owner identity. The crosshair-visible block, selection outline,
raycast owner, and interaction owner must agree.

## Likely Area

- `src/client/java/com/slabbed/mixin/client/GameRendererCrosshairRetargetMixin.java`
- `src/client/java/com/slabbed/client/runtime/LoweredSideSlabRetargeter.java`
- Prior doctrine: `docs/beta4-seam-ownership-contract.md`
- Prior safe-candidate commits listed in `docs/beta35-salvage-audit.md`: slab-held top-hit preservation and side-slab
  retarget-steal fixes.

## Rejected Fix Candidate

`GameRendererCrosshairRetargetMixin` briefly preserved owner parity before slab-held placement-intent guards by accepting
the same ray's non-slab lowered-side-slab owner. Julia live-rejected this immediately: when holding a slab, the outline
grabbed a lowered `UPPER` side slab at the seam and drew an incoherent split/diagonal selector.

Evidence: `tmp/live-slab-held-targeting-parity-regression-20260621.png`.

That rejected source patch has now been removed from active source, and the profile jar is restaged to the rollback
build.

## Proof

- Red: Julia live screenshots show non-slab targeting the crosshair-visible slab owner while slab-held targeting kept a
  different support/lower owner.
- Focused red paths: the client harness reproduced both slab-held offset-only owner lies:
  - ordinary lowered bottom-slab lane (`candidate=17, -58, -1`, `candidateCompoundLane=false`, `actualRawVanilla=miss`)
  - compound-visible upper slab lane (`candidate=27, -58, -1`, `candidateCompoundLane=true`, `rawVanilla=miss`)
- Rejected candidate compile/build: `tmp/compileClientJava-slab-held-visible-owner-parity.log`,
  `tmp/build-slab-held-visible-owner-parity.log`; rejected jar SHA-256
  `a6c8a71e5f9d3756f54729b6a50d95e37e81a29a5885d7c67bb5963d9f7da916`.
- Rollback compile/build: `tmp/compileClientJava-slab-held-parity-rollback.log`,
  `tmp/build-slab-held-parity-rollback.log`; staged rollback jar SHA-256
  `ba19ebe6f1f8a75ce3de123bcd7179fbf5b062233c01cc4a49de865684e182db`.
- Green focused client proof: `tmp/runClientGameTest-slab-held-owner-guard.log` passed. The proof rows keep both
  dangerous candidates directly hittable through Slabbed's shifted outline while their raw vanilla slab shapes miss.
  Final `Minecraft.pick` lands on honest owners instead: `18, -59, 0` for the ordinary lowered lane case and
  `28, -59, 0` for the compound-visible upper slab lane case.
- Positive control: `tmp/runGameTest-slab-held-owner-guard.log` passed all 131 required server gametests, including the
  lowered top-slab UP-face stacking row `wysiwyg_slab_on_lowered_top_slab_up_face | dy=-0.500 | type=bottom`.
- Build proof: `tmp/build-slab-held-owner-guard.log` passed and produced
  `build/libs/slabbed-0.4.2-beta.1+26.2.jar`.
- Profile staging: `SLABBED-MC 26.2` briefly had local candidate jar SHA-256
  `27a03f1f9a07ce16ad68b6b59b256e453d45113e4af0a087653933f75111391e`. The previous candidate jar SHA-256
  `675acd71b1098d8ec632c118dc70dc30e70f9c48afac51d8b29fd0918ca9d8fd` is backed up as
  `_codex-backups/before-slab-held-target-owner-ordinary-guard-20260621-104316.jar`; the earlier rollback jar SHA-256
  `ba19ebe6f1f8a75ce3de123bcd7179fbf5b062233c01cc4a49de865684e182db` remains backed up as
  `_codex-backups/before-slab-held-owner-guard-20260621-1017.jar`.
- Live rejection: Julia restarted/tested the `27a03f...` candidate and found targeting was worse in general. Fresh
  evidence:
  - `/var/folders/qd/dqqdc7fd5pndvbpcrbqkshdh0000gn/T/codex-clipboard-94402963-32f4-4c0e-bd53-c75a5c95c8d4.png`
    shows slab-held `Oak Slab`, `dy=-0.500 LOWERED`, `side=up`, `half=UPPER`, `sro=ANCHORED`, with selector/owner
    displaced from the visible crosshair target.
  - `/var/folders/qd/dqqdc7fd5pndvbpcrbqkshdh0000gn/T/codex-clipboard-3d11eb71-13d7-4614-8b5e-d21f959e2c1b.png`
    shows the same area falling to `[slabdy] target: none`.
- Rollback: the profile jar is now restored to SHA-256
  `ba19ebe6f1f8a75ce3de123bcd7179fbf5b062233c01cc4a49de865684e182db`; the rejected candidate is preserved as
  `mods/_codex-backups/slabbed-0.4.2-beta.1+26.2.bad-targeting-27a03f-20260621-120417.jar`.
- Source rollback alignment: `tmp/compile-source-aligned-targeting-rollback.log` passed after removing the rejected
  owner-guard logic from `GameRendererCrosshairRetargetMixin`, removing the companion guard from
  `LoweredSideSlabRetargeter`, and unregistering the focused client gametest from `build.gradle` and
  `src/gametest/resources/fabric.mod.json`.
- Updated blocker framing: the earlier raw-owner-override suspicion was useful for classifying the rejected candidate,
  but it is no longer the current active source state. The current blocker is the rollback-baseline live red itself:
  prove where visible model, selectable outline, and final `Minecraft.pick` / placement target disagree while slab-held
  placement is active.

## Status

**RESOLVED + SHIPPED (`0.4.2-beta.1+26.2`, 2026-06-21).** The earlier targeted slab-held owner-guard patch
(`27a03f...`) was a live-rejected false green — patching the legacy post-hoc retargeter was the wrong approach.
The fix that landed instead replaced the targeting ARCHITECTURE with the offset-aware nearest-hit raycast
(`SlabbedOffsetRaycast` + `LocalPlayerPickOffsetRaycastMixin`); Julia LIVE-CONFIRMED. Released jar `5140fb50…`.
The `27a03f...` jar stays archived under `_codex-backups/` — do not restage it. (Historical detail above is
retained for the false-green lesson; the release is no longer blocked.)
