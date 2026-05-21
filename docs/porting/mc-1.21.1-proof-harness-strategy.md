# MC 1.21.1 Proof Harness Strategy

## Decision

Chosen strategy: Option C - replace the unavailable Fabric client-gametest route with an explicit local dev runtime proof harness for MC 1.21.1, while preserving the server-compatible subset under the available 1.21.1 server gametest API.

Status: documented strategy only. No production gameplay code was patched in this slice. No gametest classes were deleted, disabled, or edited.

Release status: blocked. The port is not release-ready until the deferred client proof coverage has an explicit runnable replacement and the compile/proof gate is green.

## Dependency Evidence

Active dependency line:

- `minecraft_version=1.21.1`
- `yarn_mappings=1.21.1+build.3`
- `loader_version=0.17.3`
- `fabric_version=0.115.6+1.21.1`

Local evidence captured under `tmp/mc1211-proof-harness-strategy-f9014fb/`:

- `gametestCompileClasspath-dependencies-offline.txt` resolves `fabric-api:0.115.6+1.21.1`.
- The resolved active line includes `fabric-gametest-api-v1:2.0.5+6fc22b9919`.
- The resolved active line does not include `fabric-client-gametest-api-v1`.
- The cached `fabric-api-0.115.6+1.21.1.pom` lists `fabric-gametest-api-v1` and does not list `fabric-client-gametest-api-v1`.
- Cached `fabric-client-gametest-api-v1` jars exist, and they contain the old client symbols, but local POM evidence ties the cached versions to other Fabric API lines, not the active MC 1.21.1 line.

Decision consequence: Option A is not verified. Adding a cached client-gametest coordinate to the 1.21.1 port would be a guess, not a verified dependency correction.

## Harness Inventory

Evidence files:

- `tmp/mc1211-proof-harness-strategy-f9014fb/gametest-harness-inventory.txt`
- `tmp/mc1211-proof-harness-strategy-f9014fb/gametest-classification-signals.txt`
- `tmp/mc1211-proof-harness-strategy-f9014fb/client-only-signal-lines.txt`
- `tmp/mc1211-proof-harness-strategy-f9014fb/gametest-proof-classification.md`

Classification:

- 2 server-compatible proof classes:
  - `src/gametest/java/com/slabbed/test/ChainSurvivalReproTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabFixtureTest.java`
- 16 client proof classes implementing `FabricClientGameTest` and using `ClientGameTestContext`, `TestSingleplayerContext`, and sometimes `TestWorldSave`.
- 1 client helper:
  - `src/gametest/java/com/slabbed/client/runtime/SlabbedRetargetTestHooks.java`

The client proof suite contains singleplayer, crosshair, render, client-world sync, and reload semantics. These are proof semantics, not cosmetic harness details.

## Options

### Option A - Verified client gametest dependency correction

Rejected for this slice.

Why:

- No `fabric-client-gametest-api-v1` module is resolved by `fabric-api:0.115.6+1.21.1`.
- Local cached client-gametest jars exist, but they are not verified as compatible with the active 1.21.1 dependency line.
- The active Fabric API POM and offline Gradle report support server gametest only.

Allowed later only if a verified MC 1.21.1-compatible client gametest coordinate is found from local or primary metadata and compile proof confirms the API shape.

### Option B - Split proof harness

Partially safe, but incomplete by itself.

Safe part:

- Migrate the 2 server-compatible proof classes to the available 1.21.1 server gametest API in a later narrow implementation slice.
- Keep those proofs compiling under `fabric-gametest-api-v1:2.0.5+6fc22b9919`.

Unsafe part:

- Treating the 16 client proofs as server GameTest equivalents would weaken coverage.
- Deleting, commenting out, or unregistering client proofs just to pass compile would hide the proof gap.

### Option C - Local dev runtime proof harness

Chosen.

Required meaning:

- Preserve the client proof classes and markers as proof obligations.
- Replace the missing compile-time Fabric client-gametest API with a dev-gated runtime proof route that can launch the client, build or enter a controlled world, drive the same actions, and extract the same GREEN/RED markers.
- Keep the proof gap visible until this exists and passes.
- Do not claim release readiness from compile-only success.

Minimum next implementation shape:

- First slice: server-compatible gametest adaptation only, limited to the 2 server-compatible classes and gametest metadata/build wiring if required.
- Second slice: design the dev runtime proof launcher/log extraction path for the client-only proof obligations, preserving marker names and pass/fail semantics.
- Third slice: migrate one high-value client proof through the runtime harness, verify the same marker semantics, then repeat by bucket.

### Option D - Stop

Not chosen as final strategy, because a safe path exists. The safe path is not a compile shortcut; it is an explicit proof-harness replacement plan with release still blocked.

## Non-Changes

- No gameplay behavior changed.
- No `SlabSupport` change.
- No `ClientDy` change.
- No rescue, placement, collision, or survival change.
- No proof markers removed.
- No tests deleted or disabled.
- No release, changelog, or Modrinth files changed.

## Release Gate

The MC 1.21.1 port remains blocked until:

- `compileJava` passes.
- `compileClientJava` passes.
- server-compatible gametest proofs compile/run under the verified 1.21.1 server gametest API.
- client-only proof obligations have a verified client runtime proof route or a verified compatible Fabric client-gametest dependency.
- deferred proofs are documented as deferred and are not counted as green.

## 2026-05-15 server-compatible migration + deferred-client split (f9014fb)

- Chosen action: B (explicit deferred-client-proof source-set split + mechanical server-class migration).
- Server-compatible classes migrated to verified server API:
  - `src/gametest/java/com/slabbed/test/ChainSurvivalReproTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabFixtureTest.java`
- Mechanical migration details:
  - `GameTest` import changed to `net.minecraft.test.GameTest`.
  - `@GameTest(structure=...)` changed to `@GameTest(templateName=...)` using `fabric-gametest-api-v1:empty`.
- Deferred client proof classes (still in-tree, not deleted):
  - `src/gametest/java/com/slabbed/test/SlabbedLabBeta4AuthoredCompoundAnchorDepthClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabBsFbAdjacentPlacementProofClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabModelDySingleOwnerAuditClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabCompatibleLoweredSlabLanePredicateBoundaryClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSideSlabPlacementClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabBeta4CompoundContractMatrixClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabSbMixedStackBreakClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabB2UpperHalfGhostWindowClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabBeta4CompoundLoweredFullBlockCollapseClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabRenderRegionLoweredCarrierBridgeClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabBeta4LiveShapeGoblinClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabVanillaIllegalSlabToSlabPlacementClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabUltraGoblin2StressClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabVerticalChainHitboxOwnershipClientGameTest.java`
- Deferred helper preserved:
  - `src/gametest/java/com/slabbed/client/runtime/SlabbedRetargetTestHooks.java`
- Source-set split policy:
  - `build.gradle` now explicitly compiles only the 2 server-compatible gametest classes in `sourceSets.gametest.java`.
  - This split is explicit and reversible, and is named/documented as deferred client proof harness treatment.
- Coverage statement:
  - `compileGametestJava` passing under this split does **not** mean client proof coverage is complete.
  - Client runtime proof route remains unimplemented in this line.
- Release gate status:
  - Release remains blocked on deferred client proof obligations.

## 2026-05-15 deterministic runtime visual parity route (5887009)

- Candidate replacement route added for one deferred client-proof bucket: Beta 4 lowered-stack visual parity.
- Route type:
  - Dev/runtime client diagnostic, default off.
  - Enabled with `-Dslabbed.mc1211.visualParityProof=true`.
  - Optional fixed target: `-Dslabbed.mc1211.visualParityProofWatch=x,y,z`.
- Markers:
  - `[MC1211_VISUAL_PARITY_START]`
  - `[MC1211_VISUAL_PARITY_ROW]`
  - `[MC1211_VISUAL_PARITY_SUMMARY]`
- Coverage intent:
  - one row ties the same client position to `clientDy`, observed `modelDy`, `outlineDy`, `targetDy`, target owner classification, client anchor/carrier fields, and non-World render-view observation.
- Smoke result:
  - Compile gate passed.
  - Runtime route emitted rows and observed `ChunkRendererRegion` as the model trace view.
  - The run did not exercise Julia's lowered-stack screenshot fixture; captured rows were normal terrain, so screenshot parity remains `INCONCLUSIVE`.
- Replacement status:
  - Candidate route exists, but it does not yet replace deferred FabricClientGameTest coverage.
  - It becomes a useful replacement only after enough Beta 4 lowered-stack proof cases are captured with nonzero-dy rows and stable GREEN/RED classification.
- Release gate status:
  - Still release-blocking.

## 2026-05-15 lowered-stack proof outcome (5887009)

- Outcome:
  - `INCONCLUSIVE`
- Reason:
  - the route produced valid parity rows, but not the suspicious lowered-stack target row from Julia's screenshot.
- Diagnostic usefulness:
  - useful only if it can capture a nonzero-dy target row for the suspicious fixture or a watched equivalent.
- Next state:
  - client proof route remains incomplete until enough Beta 4 cases are covered, including the exact lowered-stack screenshot condition.

## 2026-05-15 object-family coverage audit (6b37119)

- Outcome: **GREEN_BLOCK_MODEL_ONLY**
- Evidence: `tmp/mc1211-cursed-object-family-coverage-6b37119/`

### objectFamily classification field

Added `objectFamily=BLOCK_MODEL/BLOCK_ENTITY/UNKNOWN` to `[MC1211_VISUAL_PARITY_ROW]`.
This lets the proof harness distinguish block-model rows (provable via model trace)
from block-entity rows (always INCONCLUSIVE — modelDy=NaN) from air/unknown rows.

### Remaining proof obligations

| Family | Proof route | Status |
|---|---|---|
| BLOCK_MODEL | `OffsetBlockStateModel` model trace in ROW | GREEN (572 rows, Julia fixture) |
| BLOCK_ENTITY | None — modelDy=NaN, cannot classify GREEN/RED via model trace | UNPROVEN |
| ENTITY_RENDERER (item frame, minecart) | None — `crosshairPos()` ignores `EntityHitResult` | UNPROVEN |
| ENTITY_RENDERER (painting, armor stand) | No mixin coverage at all | UNPROVEN + uncovered |

### Diagnostic route sufficiency

The current `[MC1211_VISUAL_PARITY_ROW]` route is sufficient to prove block-model
parity for any crosshair-reachable block.
It is **not sufficient** to prove block-entity or entity-renderer parity:
- Block entity rows will always be INCONCLUSIVE (no model trace, modelDy=NaN).
- Entity targets are never captured (only `BlockHitResult` is handled).

To prove block-entity parity, a separate route is needed (visual inspection + log
from `BlockEntityOffsetMixin` translate calls, or a dedicated block-entity proof row
that checks dy vs translate value).

### Latent bugs identified (static analysis)

- `ItemFrameRenderOffsetMixin`: fixed -0.5 offset, wrong for dy=-1.0 compound-lowered item frames.
- `MinecartRenderOffsetMixin`: fixed -0.5 offset, wrong for dy=-1.0 compound-lowered minecarts.
- Painting / armor stand: no renderer mixin at all.

These are not yet confirmed as the source of Julia's video symptom, but are release blockers
if compound-lowered item frames or minecarts appear in expected scenes.

## 2026-05-17 normalized outline comparison and manual action markers

- Requirement update:
  - Lowered-slab visual parity rows must compare the normalized outline interval,
    not only raw `outlineDy=minY`.
  - The proof row should retain `outlineMinY`, `outlineMaxY`, `expectedMinY`,
    `expectedMaxY`, and `normalizedOutlineMatch` so the recorder can distinguish
    a shifted TOP slab from a real outline mismatch.
- Marker update:
  - Julia's exact video sequence needs explicit, default-off action markers in the log.
  - Use the `MC1211_ACTION_MARKER` helper format to record the manual sequence and
    coordinates while the crosshair is held still around each action.
- Status update:
  - This is proof-harness only and does not change gameplay semantics.
  - The port remains blocked until a fresh live rerun proves whether the previous
    outline RED was a false-positive or a genuine mismatch.

## 2026-05-17 coordinate-space normalization follow-up

- Rule update:
  - The recorder must compare equivalent coordinate spaces.
  - For slabs, `outlineLocal*` should be compared to the block's local shape
    interval, while `outlineWorld*` should be compared to the client/world-shifted
    interval used by `clientDy`, `targetDy`, and model trace rows.
- Current proof obligation after normalization:
  - Lowered TOP and BOTTOM slab rows are no longer proof blockers when local and
    world intervals agree.
  - Remaining RED rows need separate classification before they are treated as
    gameplay failures.
- Status:
  - Diagnostic-only; no gameplay semantics change.

## 2026-05-17 humanRedCapture marker route

- Requirement update:
  - Julia-visible wrongness must be capturable as an explicit human-marked current
    target row, not inferred from unrelated GREEN rows.
  - Enable with `-Dslabbed.mc1211.humanRedCapture=true` together with
    `-Dslabbed.mc1211.visualParityProof=true`.
- Marker contract:
  - `[MC1211_HUMAN_RED_TARGET]` logs `humanRedCandidate=true`, held item, hit
    type, raw crosshair target type, target pos/state/face, object family,
    block-entity presence, `clientDy`, `modelDy`, `targetDy`, outline interval
    fields, normalized outline status, target owner, anchor/carrier booleans,
    support/below state, and model-trace linkage.
  - `[MC1211_HUMAN_RED_SUMMARY]` records row count and keeps
    `releaseReady=false diagnosticsOnly=true semanticsChanged=false`.
- Current status:
  - Route is implemented and compile-green.
  - First live run captured the stone/wall/lantern/slab setup and exited cleanly.
  - Classification is `GREEN_BUT_VISUALLY_UNEXPLAINED`: marked targets agree on
    `clientDy`, `modelDy`, and `targetDy`, but the route does not yet measure
    object visual attachment point, semantic expectation versus Beta 4, or
    wall-to-lantern neighbor relation as a first-class verdict.
- Release gate status:
  - Still blocked. Normalized slab-row GREEN does not prove live parity is fixed.
  - Next proof should be a side-by-side Beta 4 comparison for the exact marked
    targets before any gameplay patch.

## 2026-05-17 visually disputed side-by-side rule

- Requirement update:
  - For visually disputed cases, dy agreement inside the MC 1.21.1 port is not
    enough to classify parity.
  - The proof must compare the same relative fixture against Beta 4 before any
    gameplay patch, and must record whether Beta 4's visual behavior was accepted
    by Julia or captured by screenshot/video.
- Wall/lantern/slab case status:
  - Evidence dir:
    `tmp/mc1211-beta4-side-by-side-wall-lantern-parity-6b37119/`.
  - Beta 4 baseline `f9014fb` / `release/0.2.0-beta.4` compiled and launched.
  - Clean exit was not confirmed; the Beta 4 run was interrupted after repeated
    diagnostic rows were captured.
  - Diagnostic comparison proves a `TARGETING_DELTA`: Beta 4 uses
    `scan-side-slab-fired` or `object-shape-owner-preserve` for the comparable
    wall/lantern/side-slab relation, while the 1.21.1 human RED stone row remains
    `anchoredFullBlock`.
  - Visual attachment parity remains unclosed until a Beta 4 visual verdict or
    screenshot/video is captured.
- Status:
  - Proof-harness/docs only; no gameplay semantics changed.
  - The port remains blocked and not release-ready.

## 2026-05-17 wall/lantern targeting delta audit

- Evidence dir:
  - `tmp/mc1211-retarget-parity-delta-6b37119/`
- Proof status:
  - Retarget mixin source is mechanically equivalent to Beta 4 except for the
    1.21.1 `BlockHitResult` constructor signature.
  - The current proven delta is target-entry data:
    - Beta 4 visible-wall row: `initial=MISS`, `object-shape-owner-preserve`,
      final target wall visible owner.
    - 1.21.1 human RED row: `initial=BLOCK` on anchored stone,
      `scan-no-rescue-candidate;legacy-above-target-not-lowered-owner`,
      final owner `anchoredFullBlock`.
- Required regression cases before release:
  - Same-coordinate or fixture-relative Beta 4 vs 1.21.1 retarget trace for the
    wall/lantern/slab setup.
  - Phase19 true-top preservation row, especially anchored full-block `face=UP`
    with no proven visible object owner.
  - No-rescue boundary row where a farther/behind visible object must not steal
    from an unrelated anchored target.
  - Side-slab candidate row proving `scan-side-slab-fired` only fires when the
    side owner is outline/raycast eligible.

## 2026-05-17 retarget branch trace requirement

- Evidence dir:
  - `tmp/mc1211-retarget-matched-trace-6b37119/`
- Trace status:
  - Default-off `[MC1211_RETARGET_BRANCH_TRACE]` was added behind
    `-Dslabbed.mc1211.retargetBranchTrace=true`.
  - The trace records the required branch fields for the wall/lantern/slab
    retarget case without changing gameplay semantics.
- Current result:
  - 1.21.1 RED coordinate row: initial target is anchored stone at
    `-18,-59,2`, support-behind path is not eligible, cap distance is
    `1.907423`, side candidate `-18,-58,2` has outline miss, and
    object-shape-owner preserve is not eligible.
  - Beta 4 fixture-relative rows reach `object-shape-owner-preserve` for the
    visible wall owner and `scan-side-slab-fired` where the side owner is
    eligible.
- Required regression cases before behavior patch savepoint:
  - Wall/lantern RED row flips from anchored stone to the intended visible owner
    or owner-prepass result.
  - Phase19 true-top preservation remains unchanged for anchored full-block
    `face=UP`.
  - No-rescue boundary row remains unchanged.
  - Generic anchored rescue does not widen.
  - Side-slab outline-miss row does not become a false `scan-side-slab-fired`.
- Status:
  - Trace/proof/docs only; no behavior semantics changed.
  - The port remains blocked and not release-ready.
- Patch gate:
  - Do not behavior-patch this path until the same-coordinate trace proves the
    exact predicate to restore without broadening generic rescue.

## 2026-05-18 retarget branch trace rerun

- Evidence dir:
  - `tmp/mc1211-matched-retarget-branch-trace-576b09b/`
- Trace status:
  - `[MC1211_RETARGET_BRANCH_TRACE]` emitted in the current 1.21.1 WIP with no
    trace fix needed.
  - The rerun captured the human RED target with `held=minecraft:lantern` at
    `-15,-59,10`.
- Current result:
  - 1.21.1 still enters retargeting on anchored stone:
    `initialType=BLOCK initialOwner=ANCHORED_FULL_BLOCK`.
  - Support-behind remains skipped:
    `supportBehindSkipReason=initial-not-support-surface`.
  - Object-shape owner preserve remains ineligible:
    `self=not-object-owner;above=outline-miss;supportBehind=initial-not-support-surface;scan=none-before-cap`.
  - Final owner remains `ANCHORED_FULL_BLOCK`.
- Required regression checks before behavior patch savepoint:
  - Matched wall/lantern row must flip from anchored stone to the intended
    visible-owner/prepass result.
  - Phase19 true-top preservation must remain unchanged for anchored full-block
    `face=UP`.
  - No-rescue boundary row must remain unchanged.
  - Generic anchored rescue must not widen.
  - Side-slab outline-miss row must not become a false `scan-side-slab-fired`.
- Status:
  - Trace/proof/docs only; no gameplay semantics changed.
  - The port remains blocked and not release-ready.

### Release gate additions

Port release remains blocked until:
- block-entity renderer parity proven (place chest/sign above lowered slab, confirm visual + log)
- item frame and minecart fixed-offset latent bugs assessed (dy=-1.0 item frame visual check)
- painting / armor stand coverage gap assessed and decided (add mixin or document as out-of-scope).

## 2026-05-15 big-dig runtime blocker (5887009)

- Evidence dir: `tmp/mc1211-big-dig-visual-and-quit-5887009`.
- Runtime A/B result:
  - baseline no-proof-flags run reproduced a stuck/hanging client process.
  - diagnostic proof-flags run also reproduced a stuck/hanging client process and stalled during loading/spawn-prep.
  - result classification: `BOTH_HANG`.
- Runtime proof-route status:
  - no nearby scan/watch candidate mode was added in this slice.
  - no nonzero-dy candidate row was captured.
  - no block-model / block-entity / entity-renderer classification was possible.
  - `[MC1211_VISUAL_PARITY_START]` was not observed in the proof-flag run, so the current route did not prove world-ready observation in this big-dig pass.
- Strategy update:
  - Runtime proof route may still use scan/watch candidate mode later, but only after the launch/loading/shutdown blocker is isolated.
  - Client runtime proof remains release-blocking until Beta 4 lowered-stack cases are covered and the quit/loading hang is resolved.

## 2026-05-19 - Wall/Lantern lower-mid proof gate

- Next proof gate before any patch:
  - Before any gameplay patch, write a red proof for `WALL_LANTERN_COMFORT_TARGETING`.
  - Or explicitly accept current targeting law and close this as a non-parity complaint.
- Rule:
  - If the behavior slice is proposed, it must include the exact fixture, held item, camera ray, initial target, intended final target, and lawfulness arguments before any predicate broadening.
- Reminder:
  - No placement/collision/survival/SlabSupport/ClientDy changes until this proof gate is passed.

Route check:
- client-gametest route for this proof is unavailable on this branch, because the
  existing MC1211 gametest source-set does not run this class in active build
  output.
- Source-set blocker found on `port/mc-1.21.1`: `build.gradle`
  intentionally limits `sourceSets.gametest.java` to
  `ChainSurvivalReproTest.java` and `SlabbedLabFixtureTest.java`. The
  `fabric-client-gametest` entrypoint still names
  `SlabbedLabLoweredSidePlacementLiveReproClientGameTest`, but that class and
  `SlabbedRetargetTestHooks` are not compiled into the active gametest output.
- Classpath blocker found on `port/mc-1.21.1`: adding the client harness back
  to the active gametest compile route is not a proof-harness-only fix because
  the active dependency line still does not resolve a verified
  `fabric-client-gametest-api-v1` module. The prior compile failure with
  missing `net.fabricmc.fabric.api.client.gametest.v1` symbols is therefore a
  real source-set/classpath gap, not evidence about wall/lantern comfort
  behavior.
- Runtime route added on 2026-05-19:
  - Default-off dev/client launcher:
    `-Dslabbed.wallLanternComfortRuntimeProof=true`.
  - Exact command:
    `JAVA_TOOL_OPTIONS="-Dslabbed.wallLanternComfortRuntimeProof=true" ./gradlew --no-daemon runClient --console plain`.
  - Route emits one `[WALL_LANTERN_COMFORT_TARGETING_RED]` marker after
    client world/player availability, then requests client shutdown.
  - Proof route: `proofRoute=runtime-client`.
  - Marker quality: `PRODUCT_DECISION_MARKER_ONLY`; this is an
    evidence-backed/manual-runtime product-decision marker, not an automated
    geometric RED proof.
  - The marker records `currentOwner=ANCHORED_FULL_BLOCK`,
    `desiredBehavior=WALL_LANTERN_COMFORT_TARGETING`, `wallHit=false`,
    `lanternHit=false`, `beta4Parity=false`, `productBehavior=true`, and
    `patchRequired=true`.
- Current status: client-gametest route remains unavailable/broken for this
  proof on MC1211. Runtime route can collect the marker but is classified as
  `PRODUCT_DECISION_MARKER_ONLY` and not an automated RED.
- No real automated geometry RED or manual-runtime RED was collected.
- Beta4 parity claim for this path is closed as unsupported.
- `WALL_LANTERN_COMFORT_TARGETING` is not implemented and not justified as a
  port-feature from current evidence.
- Current law is accepted for port-readiness unless Julia explicitly reopens it as
  a new product feature.
- If reopened, future work requires a real manual-runtime RED or automated geometry
  RED before any behavior patch.

## 2026-05-20 deterministic overlap reload phase (b1a2d71)

- Status: proof-only extension to the existing default-off MC1211 deterministic
  overlap fixture matrix. No gameplay behavior changes.
- Baseline matrix: `tmp/mc1211-deterministic-overlap-fixture-matrix-b1a2d71/`
  (36 GREEN / 0 RED / 0 INCONCLUSIVE across 9 scenarios x 4 tick phases).
- Reload-phase evidence: `tmp/mc1211-deterministic-overlap-reload-b1a2d71/`.
- Mode: two-run protocol with persisted signature file. Run A authors or verifies
  the fixture and writes a signature file with all preReload measurements.
  User saves and quits to title. Run B reads the signature, lets the chunk warm,
  and emits postReload + delta rows comparing every scenario.
- Property gates (default off):
  - `slabbed.mc1211.fullBlockSlabOverlapProof=true`
  - `slabbed.mc1211.exactFixtureOverlapProof=true`
  - `slabbed.mc1211.deterministicOverlapFixtureMatrix=true`
  - `slabbed.mc1211.deterministicOverlapReloadProof=true`
  - `slabbed.mc1211.deterministicOverlapReloadPhase=preReload|postReload`
  - `slabbed.mc1211.overlapFixtureOrigin=0,-48,33` (pinned)
  - `slabbed.mc1211.deterministicOverlapReloadSignaturePath=<absolute path>`
- Run A command:
  - `JAVA_TOOL_OPTIONS="-Dslabbed.inspect=true -Dslabbed.target.trace=true -Dslabbed.bsfb.live.trace=true -Dslabbed.mc1211.fullBlockSlabOverlapProof=true -Dslabbed.mc1211.exactFixtureOverlapProof=true -Dslabbed.mc1211.deterministicOverlapFixtureMatrix=true -Dslabbed.mc1211.deterministicOverlapReloadProof=true -Dslabbed.mc1211.deterministicOverlapReloadPhase=preReload -Dslabbed.mc1211.overlapFixtureOrigin=0,-48,33 -Dslabbed.mc1211.deterministicOverlapReloadSignaturePath=/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate/tmp/mc1211-deterministic-overlap-reload-b1a2d71/fixture-signature.txt" ./gradlew --no-daemon runClient --console plain`
- Run B command (same flags, phase swapped):
  - `JAVA_TOOL_OPTIONS="-Dslabbed.inspect=true -Dslabbed.target.trace=true -Dslabbed.bsfb.live.trace=true -Dslabbed.mc1211.fullBlockSlabOverlapProof=true -Dslabbed.mc1211.exactFixtureOverlapProof=true -Dslabbed.mc1211.deterministicOverlapFixtureMatrix=true -Dslabbed.mc1211.deterministicOverlapReloadProof=true -Dslabbed.mc1211.deterministicOverlapReloadPhase=postReload -Dslabbed.mc1211.overlapFixtureOrigin=0,-48,33 -Dslabbed.mc1211.deterministicOverlapReloadSignaturePath=/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate/tmp/mc1211-deterministic-overlap-reload-b1a2d71/fixture-signature.txt" ./gradlew --no-daemon runClient --console plain`
- Markers added (default off):
  - `[MC1211_DETERMINISTIC_OVERLAP_RELOAD_START]`
  - `[MC1211_DETERMINISTIC_OVERLAP_RELOAD_ROW] phase=preReload|postReload|delta`
  - `[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SIGNATURE_WRITTEN]`
  - `[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SIGNATURE_READ]`
  - `[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SAVE_AND_QUIT_INSTRUCTION]`
  - `[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SUMMARY]`
- Verdict rules (delta classification):
  - RED on lane name change, lane legality flip, dy / supportTopWorldY /
    objectVisualBottomWorldY delta exceeding 1e-6 (when both finite),
    overlap becoming finite-positive, or persistence-truth disappearance
    (anchorClient / loweredCarrierClient / compoundFullBlockAnchorClient /
    compoundVisibleSideUpperSlabClient pre=true && post=false), or postReload
    row red on its own merits.
  - INCONCLUSIVE for NaN scalar fields, missing baseline, missing signature,
    missing fixture, chunk-load budget exceeded, or render-view bridge flip
    (treated as INCONCLUSIVE per safer interpretation since the harness cannot
    confirm both phases had identical render observation opportunity).
  - GREEN otherwise.
- Release status:
  - The reload extension is proof-only; no release tag moves on this slice.
  - The MC1211 port remains release-blocked.

## 2026-05-20 MC1211 goblin route repair (cdb7fe3)

- Status: harness-route repair only. No gameplay behavior change.
- Problem: `runClientGameTest` could emit the overlap-route canary and
  server-state overlap rows without proving that the intended goblin route
  executed, because the legacy
  `SlabbedLabUltraGoblin2StressClientGameTest.java` class remains a deferred
  `FabricClientGameTest` obligation outside the active compiled MC1211
  gametest set.
- Repair:
  - Added `Mc1211GoblinRouteClientEntrypoint` as an explicit gametest-side
    `client` entrypoint gated by `slabbed.mc1211.goblinOnly=true`.
  - Added server-compatible `Mc1211GoblinRouteCanaryGameTest` to the active
    `fabric-gametest` route so the replacement route also compiles/registers
    inside the active MC1211 gametest source set.
  - The executed goblin replacement route emits:
    - `[MC1211_GOBLIN_ROUTE_CANARY]`
    - `[MC1211_GOBLIN_START]`
    - `[MC1211_GOBLIN_ROW]`
    - `[MC1211_GOBLIN_SUMMARY]`
    - `[MC1211_GOBLIN_GREEN]`
  - The row payload names the legacy UltraGoblin phase and proof ids so the
    replacement route proves execution reached the intended goblin body under
    `runClientGameTest` even though the old Fabric client-gametest API remains
    deferred on MC1211.
  - `SlabbedLabFixtureTest.mc1211ServerStateOverlapMatrix` now skips only when
    `slabbed.mc1211.goblinOnly=true` and `slabbed.mc1211.overlapMatrixOnly` is
    not also true, preserving the overlap matrix by default.
- Goblin-only command:
  - `JAVA_TOOL_OPTIONS="-Dslabbed.mc1211.goblinOnly=true" ./gradlew --no-daemon runClientGameTest --console plain`
- Overlap-only command:
  - `JAVA_TOOL_OPTIONS="-Dslabbed.mc1211.overlapMatrixOnly=true" ./gradlew --no-daemon runClientGameTest --console plain`
- Legacy class status:
  - `SlabbedLabUltraGoblin2StressClientGameTest.java` remains in-tree as the
    deferred client-only proof obligation.
  - The active MC1211 executed replacement route is the gametest-side client
    bootstrap canary, not a claim that the old Fabric client-gametest API is
    healthy on 1.21.1.

## 2026-05-21 overlap deferred-row policy alignment (358dbb7)

- Scope: proof/docs expectation alignment only. No gameplay behavior changes.
- Row reclassification:
  - `LOWERED_TOP_SLAB_SIDE_LANE_STACK` is now treated as
    `LOWERED_TOP_SLAB_SIDE_LANE_STACK_DEFERRED`.
  - Lane status for this row is `deferred / illegal-for-current-release`.
  - Policy reason: compound `dy=-1.0` top-slab side-lane behavior is not
    legalized for current MC1211 release scope.
- Diagnostic preservation:
  - The row remains in the overlap matrix output and still logs overlap facts,
    including `serverOverlap=0.500000` when observed.
  - The matrix summary now distinguishes legal blocking RED rows from DEFERRED
    rows.
- Release-readiness interpretation for this row:
  - This deferred row is not counted as a legal-lane GREEN and is not a
    release-blocking RED under current policy.
  - Reopening requires fresh RED proof, named legal lane grammar, and explicit
    owner/triad/collision predicate.
