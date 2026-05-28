# SLABBED_SPINE.md - Slabbed 26.1.2 Port Spine

This is the active operating spine for the dedicated Slabbed MC 26.1.2 port checkout. It is local to this tree and is not the phase19 Slabbed spine.

Use it to know the current root, branch, HEAD, base tag, port blocker, proof state, and next safe step.

## Read Order

1. `AGENTS.md`
2. `SLABBED_SPINE.md`
3. Relevant `docs/porting/*` notes for the active blocker

## Canonical Port Root

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Do not update other Slabbed checkouts from this file. The phase19 checkout remains a separate source/release line unless Julia explicitly asks to sync something back.

## Current Git Truth

- Branch: `port/mc-26.1.2`
- HEAD / tag at HEAD: verify live with `git rev-parse --short HEAD` and `git tag --points-at HEAD`.
- Base release provenance: `release/0.2.0-beta.4` / `f9014fbfcb15af2716f090d038762fd8d3d460de`
- Current tracked tree after the release savepoint commit should be clean.
- Current untracked evidence is expected under `tmp/` and is not release payload.

Do not rely on this file alone for release proof; Git commands, proof logs, the annotated save tag, and pushed refs are authoritative.

## Current Port Status

Fresh dy1.5-2.0 live-recorder closure proof on 2026-05-28:

- Savepoint intent: `save/port-26.1.2-dy0-slab-shape-dispatch`.
- Active resolved blocker: the live-recorder RED at `4,-60,30` where unnamed vanilla dy0 `minecraft:stone_slab[type=bottom]` had outline/collision bounds but empty interaction/raycast shape.
- Mechanism: `SlabSupportStateMixin` now lets unnamed dy0 vanilla slabs with empty native interaction shape expose vanilla-compatible slab bounds from collision shape, falling back to outline shape. The row remains dy0, unnamed, non-persistent, and non-compound.
- Exact branch-client recorder proof: `tmp/dy15-20-dy0-slab-shape-dispatch-65f9ce54/live-validation-160618-clean-control/exact-pose-player365` has `outlineRaycastSplitRows=0`, `ghostSurfaceRows=0`, `hiddenOwnerRows=0`, `6513` exact target rows for `4,-60,30`, and `LIVE_GREEN_CURSOR_TRIAD`.
- Focused dy15 proof stayed green for the required counts: `liveFailUnauthorizedDyRows=0`, `renderBridgeMismatchRows=0`, `targetBlockNoSurfaceReplayRows=0`, `hiddenOwnerPlacementRows=0`, `loweredSideSlabPlacementVanillaDyRows=0`, `legalLoweredSlabSurfaceMismatchRows=0`.
- Lowered side-slab placement remains green: live recorder action evidence under `tmp/dy15-20-dy0-slab-shape-dispatch-65f9ce54/live-validation-152432/routes/lowered-carrier-placement-window/window-actions.tsv` has `LIVE_GREEN_PLACEMENT_AUTHORING` with `afterDy=-0.500000` and `persistent_lowered_slab_carrier`.
- Fresh gates in `tmp/dy15-20-dy0-slab-shape-dispatch-65f9ce54/final-savepoint-audit-162706/`: compile passed, runner3 passed with `RUNNER3_SUMMARY rows=260 placeRows=260 traceMismatches=44 placeMismatches=44 mismatches=88`, hitbox gate passed, culling visual passed with screenshots, default `runClientGameTest` passed, live-cursor recorder contract passed, and `git diff --check` passed.
- Tracked residual: focused dy15 proof still reports `legalObjectTriadMismatchRows=2` for `dy15_oak_fence_object` and `dy15_cobblestone_wall_object`. This is classified as separate from the `4,-60,30` dy0 slab dispatch regression because those are legal dynamic fence/wall object rows, not unnamed dy0 slabs; hitbox gate separately proves their raycast/collision co-location.

The 26.1.2 port has reached a placement/culling closure candidate for the slab-held visual-target / placement-intent fault plus the follow-up placement review fallout. Work so far has focused on release-base provenance, mapping/tooling/classpath proof, Java 25 / Gradle / Loom compatibility, source-set wiring, narrow source API probes, the slab-held visual-target failure, the reviewed placement/retargeting guards, and the post-cull visible-face / partial-collision closure.

Resolved blocker: Julia's 2026-05-23 slab-held recording showed the selection outline/target floating above the visible stone body. The port restores the missing 26.1.2 visible-shape path, preserves the visual triad, applies the final-target-unknown placement-intent fix through the validated visible lane, prevents slab-held compound-visible retargeting from stealing nearer vanilla block hits, rejects non-placeable final-target-unknown visible-lane contexts before APPLY, and narrows lowered-side comfort retargeting so border samples outside the visible face do not remain sticky. Temporary `runner3RowId` / provenance audit instrumentation was removed before release proof.

Fresh placement review fallout proof on 2026-05-26:

- Runner3: `tmp/port-26-1-2-placement-review-fallout-proof-98bc0629/clientGameTest-runner3-provenance.log`
- Runner3 metrics with the synced local runner3 harness: `NO_PLACE_BUT_SHOULD_PLACE=19`, `PLACED_RELATIVE_TO_RETARGETED_OWNER=6`, `PLACED_ABOVE_VISIBLE_TARGET=21`, `PLACED_BUT_SHOULD_NOT=16`, `heldStoneVisibleOwnerRemapRegressionRiskRows=4`
- Proof environment note: the local shell default Java was 21, so proof was rerun with `JAVA_HOME=$(/usr/libexec/java_home -v 25)` to match the known 26.1.2 classfile/runtime family.
- Apples-to-apples regression check: removing the two production review-fallout edits under the same synced local runner3 harness produced the same mismatch profile, so the secondary class-count shape is harness-baseline drift rather than a production regression from this patch.
- Hitbox checkerboard scan: `RUNNER3_HITBOX_SCAN_GREEN specs=20 faces=86 samples=4214 anomalies=0`; prior clipping evidence showed `BORDER_FINAL_STICKY_INTENDED_OWNER=60` before the comfort-retarget narrowing.
- Runtime smoke: `tmp/port-26-1-2-placement-review-fallout-proof-98bc0629/runClient-smoke.log`
- Visual check: Julia supplied a live recording showing large/sticky selection clipping. The checkerboard scan reproduced the border-sticky target anomaly and proved it green after the comfort-retarget narrowing; no additional manual visual route was available while she was away.
- Release hygiene: `./gradlew --no-daemon clean build` passed; jar scans found no runner3/gametest/probe/trace/debug/provenance/tmp artifacts.

Fresh placement/culling closure proof on 2026-05-27:

- Compile: `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew --no-daemon compileJava compileClientJava --console plain` passed.
- Runner3: `tmp/port-26-1-2-wip-closure-savepoint-c36611d/final-runner3.log`
- Runner3 metrics: `mismatches=88`, `NO_PLACE_BUT_SHOULD_PLACE=19`, `PLACED_RELATIVE_TO_RETARGETED_OWNER=6`, `PLACED_ABOVE_VISIBLE_TARGET=3`.
- Culling proof marker: `classification=CULLING_VISUAL_REPRO_FIXED_CURRENT_ONLY`, not a literal restored-face marker. Existing proof is in `tmp/port-26-1-2-culling-clipping-fix-c36611d/quad-cullface-visual-ab-summary.txt`: `current_vs_baseline_diff_pixels=88200`, `pre_quadfix_current_vs_baseline_diff_pixels=0`, and the mechanism states that quadfix current renders the missing gold face.
- Collision proof: `tmp/port-26-1-2-wip-closure-savepoint-c36611d/final-hitboxgate.log` has both `oak_fence` and `cobblestone_wall` object rows at `expectedDy=-1.500000` with `raycastCoLocated=yes` and `collisionCoLocated=yes`.
- Runtime forbidden marker scan across runner3, hitboxgate, and culling visual logs found no `Invalid player data`, `InvalidInjectionException`, `MixinApplyError`, `updateCrosshairTarget`, `onPlayerInteractBlock`, or `Vec3d.ofCenter` markers.
- Release hygiene: `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew --no-daemon clean build --console plain` passed; `build/libs/*.jar` leakage scan found no runner3/gametest/probe/trace/debug/provenance/tmp artifacts; `git diff --check` passed.

The preserved blocker note is:

```text
docs/porting/mc-26.1.2-mapping-blocker.md
```

That note records the original mapping-provider/tooling provenance stop. Later local port forensics indicated the live `compileJava` classpath is Mojang-style rather than Yarn-style, so current source migration should be treated as Mojang-style API drift unless fresh evidence proves otherwise.

## Current Operating Rules

- Keep work local to `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2`.
- Do not edit `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate` from this port slice.
- Preserve unrelated dirty source migration work.
- Use grep/classpath/compiler evidence before changing code.
- Prefer one-file mechanical probes for source API drift.
- Run `compileJava` only when the slice calls for it, and interpret success narrowly.
- Do not treat `buildEnvironment` success as compile success.
- Do not make gameplay, release, or broad compatibility claims from this port tree.

## Known Port Evidence

- The dedicated port branch was bootstrapped from the released base `release/0.2.0-beta.4`.
- Java 25 is the known runtime family used for 26.1.2 port proof.
- Gradle 9.4.x and Fabric Loom experiments have been part of the tooling path.
- Broad cache searches are not authoritative for namespace decisions; exact task classpath proof is preferred.
- Optional compatibility families may be deferred when they are not core port scope, but the defer must be explicit and narrow.
- For one-file source probes, success means the target file leaves the compile error stream after one compile gate, not that the full project is ported.

## Current Stop Conditions

Stop and report if:

- the root is not `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2`
- the requested slice would edit another Slabbed checkout
- two focused attempts fail
- compile evidence points to a different dominant blocker family
- a proposed change widens from mechanical API migration into gameplay behavior
- a savepoint/release claim is being made without commit, annotated tag, branch push, tag push, and clean tracked tree

## Next Safe Action

For documentation-only work, keep edits limited to port-local docs.

For code migration work, classify the current dominant compile/source blocker first, then patch exactly the requested file or the smallest proven mechanism. If the result remains unclear after one compile gate, stop with tried/observed/proven/unproven/next-smallest-audit.

After the dy0 slab shape-dispatch savepoint, the next safe technical slice is to classify whether `legalObjectTriadMismatchRows=2` is accepted fence/wall visual-vs-collision overhang or a real dy1.5 legal-object triad bug. Do not reopen the `4,-60,30` unnamed dy0 slab dispatch path unless a fresh recorder RED reproduces it.
